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
 * Holds the UniFFI-generated Kotlin bindings plus a thin, failure-tolerant wrapper. The native
 * library `libearix_core.so` is compiled from `rust/` and packaged through the module's default
 * `src/main/jniLibs` root. Desktop and iOS deliberately do NOT depend on this module: they keep
 * the Kotlin implementation, so nothing breaks when no .so exists there.
 *
 * Scope note: this module contains lyrics text parsing and the LRCLIB proof-of-work nonce search.
 * Both are pure computation — no audio, no Media3/ExoPlayer, no playback service, no queue and no
 * audio pipeline. Every entry point is failure-tolerant (`RustLyricsBridge`, `RustPowBridge`) and
 * returns `null` instead of throwing, which sends the caller back to the Kotlin implementation.
 *
 * Regenerating the bindings after changing the Rust API — the DEBUG library on purpose, because
 * `strip = "symbols"` in the release profile removes the UniFFI metadata the generator reads
 * (`print-repr` printing `[]` is the symptom):
 *   cargo build -p earix-core --lib
 *   cargo run -p earix-core --features uniffi-bindgen --bin uniffi-bindgen -- \
 *     generate --library rust/target/debug/libearix_core.so --language kotlin \
 *     --out-dir <tmp> --config rust/earix-core/uniffi.toml --no-format
 * then copy the generated `earix_core.kt` into `src/main/java/com/maxrave/earixrust/`.
 *
 * Equivalence and benchmarking: `scripts/rust/verify-lrc-parity.sh` (Phase 1) and
 * `scripts/rust/verify-stage2-parity.sh` (Phase 2) run the real Kotlin implementations against the
 * real Rust ones through these same bindings, on the host.
 */

/** Pinned NDK; CI installs exactly this version and `ndkVersion` below reports it to AGP. */
val rustNdkVersion = "27.0.12077973"

/** ABI -> Rust target triple. Mirrors `androidApp`'s `abiFilters` exactly. */
val rustAbis =
    mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "armv7-linux-androideabi",
        "x86_64" to "x86_64-linux-android",
    )

val rustApiLevel = 26

val rustWorkspaceDir = rootProject.layout.projectDirectory.dir("rust")

android {
    namespace = "com.maxrave.earixrust"
    compileSdk = 37
    ndkVersion = rustNdkVersion

    defaultConfig {
        minSdk = rustApiLevel
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// --- Rust native build -----------------------------------------------------------------------
//
// Deliberately built from plain Exec/Copy tasks rather than a custom Task subclass: a class
// declared inside a .gradle.kts is compiled as a non-static inner class, which Gradle refuses to
// instantiate ("Class Build_gradle.X is a non-static inner class"). Exec also keeps the build
// configuration-cache compatible.
//
// `cargo-ndk` is not used: pointing CARGO_TARGET_<TRIPLE>_LINKER at the NDK's clang wrapper is all
// rustc needs, which keeps CI to a plain rustup install.

/**
 * NDK location. Resolved from the environment only; the default matches the GitHub runner image so
 * a missing variable is not by itself a configuration failure. A missing NDK is reported by the
 * build task with an actionable message, and can be skipped entirely with -PskipRustBuild=true.
 */
val ndkRoot: String =
    System.getenv("ANDROID_NDK_HOME")
        ?: System.getenv("ANDROID_NDK_ROOT")
        ?: System.getenv("ANDROID_SDK_ROOT")?.let { "$it/ndk/$rustNdkVersion" }
        ?: System.getenv("ANDROID_HOME")?.let { "$it/ndk/$rustNdkVersion" }
        ?: "/usr/local/lib/android/sdk/ndk/$rustNdkVersion"

/** NDK still ships `darwin-x86_64` on Apple Silicon, so this is keyed on the host OS only. */
val ndkHostTag: String =
    when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows-x86_64"
        System.getProperty("os.name").lowercase().contains("mac") -> "darwin-x86_64"
        else -> "linux-x86_64"
    }

fun clangName(
    triple: String,
    apiLevel: Int,
): String {
    // The API-level clang wrapper is spelled `armv7a-...` for 32-bit ARM only.
    val prefix = if (triple == "armv7-linux-androideabi") "armv7a-linux-androideabi" else triple
    return "$prefix$apiLevel-clang"
}

fun taskSuffix(abi: String): String =
    abi.split('-', '_').joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }

// Resolved eagerly at configuration time on purpose: `onlyIf { }` / `doFirst { }` lambdas written
// in a .kts are compiled as inner classes that reference the build script, and the configuration
// cache refuses to serialize those ("cannot serialize Gradle script object references"). A plain
// boolean applied to `enabled` has no such problem.
val skipRustBuild =
    providers
        .gradleProperty("skipRustBuild")
        .map { it.toBoolean() }
        .getOrElse(false)

val rustStageTasks =
    rustAbis.map { (abi, triple) ->
        val suffix = taskSuffix(abi)
        val linker = "$ndkRoot/toolchains/llvm/prebuilt/$ndkHostTag/bin/${clangName(triple, rustApiLevel)}"
        val builtLibrary = rustWorkspaceDir.file("target/$triple/release/libearix_core.so")
        val stagedDir = layout.projectDirectory.dir("src/main/jniLibs/$abi")

        val cargoTask =
            tasks.register<Exec>("cargoBuild${suffix}") {
                group = "rust"
                description = "Compiles the Earix Rust core for $abi ($triple)."
                workingDir = rustWorkspaceDir.asFile
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
                environment("CARGO_TARGET_${triple.uppercase().replace('-', '_')}_LINKER", linker)

                inputs
                    .dir(rustWorkspaceDir.dir("earix-core/src"))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                inputs
                    .dir(rustWorkspaceDir.dir("earix-core/tests"))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                inputs.files(
                    rustWorkspaceDir.file("Cargo.toml"),
                    rustWorkspaceDir.file("Cargo.lock"),
                    rustWorkspaceDir.file("earix-core/Cargo.toml"),
                    rustWorkspaceDir.file("earix-core/uniffi.toml"),
                )
                outputs.file(builtLibrary)

                // -PskipRustBuild=true keeps the module buildable without a Rust/NDK toolchain; the
                // app then simply uses the Kotlin parser at runtime.
                enabled = !skipRustBuild
            }

        tasks.register<Copy>("stageRust${suffix}") {
            group = "rust"
            description = "Copies the $abi Rust library into the module's jniLibs root."
            dependsOn(cargoTask)
            from(builtLibrary)
            into(stagedDir)
            enabled = !skipRustBuild
        }
    }

val cargoBuildAndroid =
    tasks.register("cargoBuildAndroidJniLibs") {
        group = "rust"
        description = "Builds the Earix Rust core for every packaged Android ABI."
        dependsOn(rustStageTasks)
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
