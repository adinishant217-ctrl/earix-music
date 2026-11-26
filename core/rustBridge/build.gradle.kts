import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xmulti-dollar-interpolation")
        freeCompilerArgs.add("-Xwhen-guards")
    }
}

/**
 * Earix Rust core bridge (Android only).
 *
 * Holds the UniFFI-generated Kotlin bindings plus a thin, failure-tolerant wrapper. The
 * native library `libearix_core.so` is compiled by [CargoBuildAndroidTask] from `rust/` and
 * packaged through `jniLibs`. Desktop and iOS deliberately do NOT depend on this module:
 * they keep the Kotlin implementation, so nothing breaks when no .so exists there.
 *
 * Scope note: this module contains pure lyrics text parsing. It has no relationship to
 * Media3/ExoPlayer, the playback service, the queue or the audio pipeline.
 */

/**
 * Pinned NDK. CI installs exactly this one; it is also what `ndkVersion` reports to AGP so a
 * developer cannot silently link the Rust objects against a different toolchain than AGP
 * expects.
 */
val rustNdkVersion = "27.0.12077973"

/** ABI -> Rust target triple. Mirrors `androidApp`'s `abiFilters` exactly. */
val rustAbis =
    mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "armv7-linux-androideabi",
        "x86_64" to "x86_64-linux-android",
    )

/**
 * Compiles `rust/` for every Android ABI and stages the `.so` files for `jniLibs`.
 *
 * Deliberately does NOT use `cargo-ndk`: setting `CARGO_TARGET_<TRIPLE>_LINKER` to the NDK's
 * clang wrapper is all rustc needs, and it keeps CI to a plain `rustup` install.
 */
abstract class CargoBuildAndroidTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustWorkspace: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:Input
    abstract val ndkDirectory: Property<String>

    @get:Input
    abstract val apiLevel: Property<Int>

    @get:Input
    abstract val abiToTriple: MapProperty<String, String>

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun build() {
        val ndk = File(ndkDirectory.get())
        check(ndk.isDirectory) {
            "Android NDK not found at '$ndk'. Install NDK $rustNdkVersion (sdkmanager \"ndk;$rustNdkVersion\") " +
                "or set ANDROID_NDK_HOME. To build the app without the Rust core, pass -PskipRustBuild=true " +
                "(the Kotlin lyrics parser is then used at runtime)."
        }

        // Resolve the host toolchain folder instead of guessing from os.name: NDK still ships
        // `darwin-x86_64` on Apple Silicon, so the folder name is not derivable from the arch.
        val prebuilt =
            File(ndk, "toolchains/llvm/prebuilt")
                .listFiles()
                ?.filter { it.isDirectory }
                ?.minByOrNull { it.name }
                ?: error("No LLVM toolchain under $ndk/toolchains/llvm/prebuilt")
        val binDir = File(prebuilt, "bin")

        val workspace = rustWorkspace.get().asFile
        val outDir = jniLibsDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        abiToTriple.get().forEach { (abi, triple) ->
            val clang = File(binDir, "${clangPrefix(triple)}${apiLevel.get()}-clang")
            check(clang.isFile) { "Missing NDK linker for $abi: expected $clang" }

            execOps.exec {
                workingDir = workspace
                commandLine(
                    "cargo",
                    "build",
                    "--release",
                    "--locked",
                    "--package",
                    "earix-core",
                    "--target",
                    triple,
                )
                environment(
                    "CARGO_TARGET_${triple.uppercase().replace('-', '_')}_LINKER",
                    clang.absolutePath,
                )
            }

            val built = File(workspace, "target/$triple/release/libearix_core.so")
            check(built.isFile) { "cargo did not produce $built" }

            val dest = File(outDir, "$abi/libearix_core.so")
            dest.parentFile.mkdirs()
            built.copyTo(dest, overwrite = true)
            logger.lifecycle("Earix Rust: staged $abi (${dest.length()} bytes)")
        }
    }

    /** The API-level-specific clang wrapper name differs from the triple for 32-bit ARM. */
    private fun clangPrefix(triple: String): String =
        if (triple == "armv7-linux-androideabi") "armv7a-linux-androideabi" else triple
}

val rustWorkspaceDir = rootProject.layout.projectDirectory.dir("rust")
val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs")

android {
    namespace = "com.maxrave.earixrust"
    compileSdk = 37
    ndkVersion = rustNdkVersion

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets.getByName("main").jniLibs.srcDir(rustJniLibsDir)
}

// Resolved from the environment only: `sdkDirectory` is not part of AGP's stable public DSL, and
// an env var is what CI and every Android dev setup already exports. The task reports a clear
// error when none of these resolve to a real NDK.
val ndkPathProvider =
    providers
        .environmentVariable("ANDROID_NDK_HOME")
        .orElse(providers.environmentVariable("ANDROID_NDK_ROOT"))
        .orElse(
            providers
                .environmentVariable("ANDROID_SDK_ROOT")
                .orElse(providers.environmentVariable("ANDROID_HOME"))
                .map { "$it/ndk/$rustNdkVersion" },
        )

val cargoBuildAndroid =
    tasks.register<CargoBuildAndroidTask>("cargoBuildAndroidJniLibs") {
        description = "Builds the Earix Rust core for every packaged Android ABI."
        group = "rust"
        rustWorkspace.set(rustWorkspaceDir)
        jniLibsDir.set(rustJniLibsDir)
        ndkDirectory.set(ndkPathProvider)
        apiLevel.set(26)
        abiToTriple.set(rustAbis)

        // Cargo is incremental on its own; these declarations just let Gradle skip the task
        // entirely when no Rust source changed.
        inputs
            .files(
                rustWorkspaceDir.asFileTree.matching {
                    include("**/*.rs", "**/Cargo.toml", "**/Cargo.lock", "**/uniffi.toml")
                    exclude("target/**")
                },
            ).withPathSensitivity(PathSensitivity.RELATIVE)
    }

// Fail loudly if the toolchain is missing, rather than shipping an APK silently missing the
// native parser. Developers without Rust can opt out explicitly.
val skipRustBuild =
    providers
        .gradleProperty("skipRustBuild")
        .map { it.toBoolean() }
        .orElse(false)

cargoBuildAndroid.configure {
    onlyIf("Rust build enabled (-PskipRustBuild is not true)") { !skipRustBuild.get() }
}

tasks.named("preBuild") {
    dependsOn(cargoBuildAndroid)
}

dependencies {
    // UniFFI's generated Kotlin bindings use JNA. The @aar artifact carries the Android
    // `libjnidispatch.so` slices; the plain jar only has desktop natives.
    implementation("net.java.dev.jna:jna:5.19.1@aar")

    testImplementation(libs.junit)
}
