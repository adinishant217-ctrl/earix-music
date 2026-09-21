package com.maxrave.simpmusic.update

import com.maxrave.simpmusic.utils.VersionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val UPDATE_REPO_OWNER = "adinishant217-ctrl"
const val UPDATE_REPO_NAME = "earix-music"
const val UPDATE_RELEASES_URL =
    "https://api.github.com/repos/adinishant217-ctrl/earix-music/releases?per_page=10"

/** A newer release with a downloadable APK attached. */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val fileName: String,
    val releaseNotes: String,
    val releaseUrl: String,
)

sealed interface UpdateCheckResult {
    /** Installed version is current (or newer than anything published). */
    data object UpToDate : UpdateCheckResult

    /** A newer release with an APK attached. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** No usable release found (empty repo, no APK asset, …). */
    data object NoUpdateFound : UpdateCheckResult

    /** Network / parse failure. Message is technical; UI shows its own text. */
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Queries the GitHub releases API and returns the first non-prerelease,
 * non-draft release NEWER than the installed version that carries an APK.
 * Never throws — every failure maps to [UpdateCheckResult.Error].
 */
suspend fun fetchLatestUpdate(): UpdateCheckResult {
    val current = VersionManager.getVersionName()
    if (current.isBlank()) return UpdateCheckResult.Error("unknown current version")
    val client = HttpClient(CIO)
    try {
        val body =
            client
                .get(UPDATE_RELEASES_URL) {
                    header("Accept", "application/vnd.github+json")
                    header("User-Agent", "Earix-App")
                    header("X-GitHub-Api-Version", "2022-11-28")
                }.bodyAsText()
        val releases = Json.parseToJsonElement(body).jsonArray
        for (release in releases) {
            val obj = release.jsonObject
            if (obj["draft"]?.jsonPrimitive?.content == "true") continue
            if (obj["prerelease"]?.jsonPrimitive?.content == "true") continue
            val tag = obj["tag_name"]?.jsonPrimitive?.content.orEmpty()
            val version = tag.trim().removePrefix("v").trim()
            if (version.isEmpty() || !isNewerVersion(version, current)) continue
            val assets = obj["assets"]?.jsonArray.orEmpty()
            val apk =
                assets
                    .mapNotNull { asset ->
                        val name = asset.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                        val url = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                            name to url
                        } else {
                            null
                        }
                    }.firstOrNull { (name, _) -> name.contains("universal", ignoreCase = true) }
                    ?: assets
                        .mapNotNull { asset ->
                            val name = asset.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                            val url = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
                            if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                                name to url
                            } else {
                                null
                            }
                        }.firstOrNull()
                    ?: continue
            return UpdateCheckResult.Available(
                UpdateInfo(
                    version = version,
                    apkUrl = apk.second,
                    fileName = apk.first.ifBlank { "earix-$version.apk" },
                    releaseNotes = obj["body"]?.jsonPrimitive?.content.orEmpty(),
                    releaseUrl = obj["html_url"]?.jsonPrimitive?.content.orEmpty(),
                ),
            )
        }
        return UpdateCheckResult.NoUpdateFound
    } catch (e: Exception) {
        return UpdateCheckResult.Error(e.message.orEmpty())
    } finally {
        client.close()
    }
}

/** True when [latest] is a strictly newer dotted version than [current]. */
fun isNewerVersion(
    latest: String,
    current: String,
): Boolean {
    val latestParts = latest.trim().removePrefix("v").trim().split(".", "-", "+")
    val currentParts = current.trim().removePrefix("v").trim().split(".", "-", "+")
    val size = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until size) {
        val left = latestParts.getOrNull(i).orEmpty()
        val right = currentParts.getOrNull(i).orEmpty()
        val leftNum = left.toIntOrNull()
        val rightNum = right.toIntOrNull()
        if (leftNum != null && rightNum != null) {
            if (leftNum != rightNum) return leftNum > rightNum
        } else if (left != right) {
            return left > right
        }
    }
    return false
}

/**
 * Hands an APK URL to the platform downloader. Android enqueues it with the
 * system DownloadManager (notification on completion; tapping installs).
 * Other platforms: no-op — callers offer "open releases page" instead.
 */
expect fun downloadUpdateApk(
    url: String,
    fileName: String,
)
