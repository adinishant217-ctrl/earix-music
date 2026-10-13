package org.simpmusic.lyrics.publish

import com.maxrave.ktorext.getEngine
import com.maxrave.logger.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.simpmusic.lyrics.crypto.sha256Hex

private const val TAG = "LRCLIBPublisher"
private const val CHALLENGE_URL = "https://lrclib.net/api/request-challenge"
private const val PUBLISH_URL = "https://lrclib.net/api/publish"
private const val USER_AGENT = "Earix/1.0.8 (https://github.com/adinishant217-ctrl/earix-music)"

/** Upper bound for the proof-of-work nonce search. */
private const val MAX_NONCE = 10_000_000

/** Outcome of a publish attempt. */
sealed class PublishResult {
    data object Success : PublishResult()

    data object AlreadyExists : PublishResult()

    data class Error(
        val code: Int,
        val message: String,
    ) : PublishResult()
}

@Serializable
private data class ChallengeResponse(
    val prefix: String = "",
    val target: String = "",
)

@Serializable
private data class PublishRequest(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Int,
    val instrumental: Boolean = false,
    val plainLyrics: String,
    val syncedLyrics: String,
)

/** Detailed token outcome (public: consumed by the app's SyncLyricsViewModel). */
sealed interface TokenResult {
    data class Ok(val token: String) : TokenResult

    data object NetworkError : TokenResult

    data object PowUnsolved : TokenResult
}

/**
 * Publishes user-synced lyrics to LRCLIB (free, keyless, anonymous).
 *
 * LRCLIB gates publishing behind a small proof-of-work challenge so that all
 * network + hashing work stays on [Dispatchers.IO], never the main thread.
 */
object LRCLIBPublisher {
    private val client =
        HttpClient(getEngine()) {
            expectSuccess = false
            followRedirects = true
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        explicitNulls = false
                    },
                )
            }
        }

    /**
     * Fetches a challenge and solves it. Returns `"{prefix}:{nonce}"`, or null
     * on network/parse failure or an unsolved challenge.
     */
    suspend fun getPublishToken(): String? =
        when (val r = getPublishTokenDetailed()) {
            is TokenResult.Ok -> r.token
            else -> null
        }

    suspend fun getPublishTokenDetailed(): TokenResult =
        withContext(Dispatchers.IO) {
            try {
                Logger.d(TAG, "POST $CHALLENGE_URL")
                val raw: HttpResponse =
                    client.post(CHALLENGE_URL) {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.UserAgent, USER_AGENT)
                    }
                val rawBody = raw.bodyAsText()
                Logger.d(TAG, "challenge status=${raw.status.value} body=${rawBody.take(500)}")
                if (raw.status.value != 200) return@withContext TokenResult.NetworkError
                val challenge: ChallengeResponse =
                    try {
                        Json.decodeFromString(rawBody)
                    } catch (e: Exception) {
                        Logger.w(TAG, "challenge parse failed: ${e.message}")
                        return@withContext TokenResult.NetworkError
                    }
                Logger.d(TAG, "prefix=${challenge.prefix} target=${challenge.target}")
                if (challenge.prefix.isBlank() || challenge.target.isBlank()) {
                    return@withContext TokenResult.NetworkError
                }
                val nonce = solvePow(challenge.prefix, challenge.target)
                    ?: return@withContext TokenResult.PowUnsolved
                Logger.d(TAG, "PoW solved nonce=$nonce hash=${sha256Hex(challenge.prefix + nonce).take(16)}...")
                TokenResult.Ok("${challenge.prefix}:$nonce")
            } catch (e: Exception) {
                Logger.w(TAG, "challenge request failed: ${e.message}")
                TokenResult.NetworkError
            }
        }

    suspend fun publishLyrics(
        token: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Int,
        syncedLyrics: String,
        plainLyrics: String,
    ): PublishResult =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.post(PUBLISH_URL) {
                        header("X-Publish-Token", token)
                        header(HttpHeaders.UserAgent, USER_AGENT)
                        contentType(ContentType.Application.Json)
                        setBody(
                            PublishRequest(
                                trackName = trackName,
                                artistName = artistName,
                                albumName = albumName,
                                duration = durationSeconds,
                                plainLyrics = plainLyrics,
                                syncedLyrics = syncedLyrics,
                            ),
                        )
                    }
                Logger.d(TAG, "publish status=${response.status.value}")
                when (response.status) {
                    HttpStatusCode.Created -> PublishResult.Success
                    HttpStatusCode.Conflict -> PublishResult.AlreadyExists
                    HttpStatusCode.TooManyRequests -> {
                        val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
                        val msg =
                            if (retryAfter != null) {
                                "Too many requests. Try again in $retryAfter seconds."
                            } else {
                                "Too many requests. Try again later."
                            }
                        PublishResult.Error(response.status.value, msg)
                    }
                    else -> PublishResult.Error(response.status.value, response.bodyAsText().take(300))
                }
            } catch (e: Exception) {
                PublishResult.Error(-1, e.message ?: e.toString())
            }
        }

    /** Lowest nonce whose hex SHA-256 starts with [target], or null past [MAX_NONCE]. */
    internal fun solvePow(prefix: String, target: String): Long? {
        for (nonce in 0 until MAX_NONCE) {
            if (sha256Hex(prefix + nonce).startsWith(target)) return nonce.toLong()
        }
        return null
    }

    /** `(timestamp_ms, text)` pairs → `"[MM:SS.mm] text\n"` (centiseconds, 2 digits). */
    fun buildLRC(lines: List<Pair<Long, String>>): String =
        buildString {
            for ((ms, text) in lines) {
                // Manual zero-padding: String.format is JVM-only, this is commonMain.
                val minutes = (ms / 60000).toString().padStart(2, '0')
                val seconds = ((ms % 60000) / 1000).toString().padStart(2, '0')
                val centis = ((ms % 1000) / 10).toString().padStart(2, '0')
                append("[$minutes:$seconds.$centis] $text\n")
            }
        }

    /** Plain text, one line each, no timestamps. */
    fun buildPlainLyrics(lines: List<Pair<Long, String>>): String =
        lines.joinToString("\n") { it.second }
}
