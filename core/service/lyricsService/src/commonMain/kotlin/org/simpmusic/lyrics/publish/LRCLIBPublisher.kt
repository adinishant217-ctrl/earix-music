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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.simpmusic.lyrics.crypto.powCpuCount
import org.simpmusic.lyrics.crypto.powHash
import org.simpmusic.lyrics.crypto.sha256Hex
import org.simpmusic.lyrics.crypto.toHex

private const val TAG = "LRCLIBPublisher"
private const val CHALLENGE_URL = "https://lrclib.net/api/request-challenge"
private const val PUBLISH_URL = "https://lrclib.net/api/publish"
private const val USER_AGENT = "Earix/1.0.14 (https://github.com/adinishant217-ctrl/earix-music)"

/** Upper bound for the proof-of-work nonce search (30x margin over ~17M avg). */
const val MAX_NONCE = 500_000_000L

/** PoW backstop: users should never wait forever. */
private const val POW_TIMEOUT_MS = 60_000L

/** Progress callback granularity (attempts) — coarse enough to avoid UI churn. */
private const val PROGRESS_EVERY = 50_000L

/** Solved-token reuse window (LRCLIB tokens live ~4 minutes). */
private val TOKEN_TTL = 240_000L.milliseconds

/** Solved nonce plus the attempts it took (for hash-rate logging). */
data class PowSolution(
    val nonce: Long,
    val attempts: Long,
)

private data class CachedToken(
    val token: String,
    val issuedAt: TimeMark,
)

private fun CachedToken.isAlive(): Boolean = issuedAt.elapsedNow() < TOKEN_TTL

/** Solved-token reuse (LRCLIB tokens live ~4 minutes). Cleared on any failure. */
private var cachedToken: CachedToken? = null

/** Writes [value] as ASCII digits into [buf]; returns the digit count. No allocation. */
internal fun writeLongAscii(value: Long, buf: ByteArray): Int {
    if (value == 0L) {
        buf[0] = '0'.code.toByte()
        return 1
    }
    var n = value
    var len = 0
    while (n > 0) {
        buf[len++] = ('0'.code + (n % 10)).toByte()
        n /= 10
    }
    var i = 0
    var j = len - 1
    while (i < j) {
        val t = buf[i]
        buf[i] = buf[j]
        buf[j] = t
        i++
        j--
    }
    return len
}

private fun hexVal(c: Char): Int =
    when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("bad hex char '$c'")
    }

/**
 * Bitcoin-style difficulty check: is `hash` (big-endian unsigned) numerically
 * below the target?
 *
 * The server sends full-length (64-char) targets like
 * `000000FF0000...0000`, where a hex-prefix match would demand an exact
 * 256-bit collision (impossible). Integer comparison gives the intended
 * ~2^-24 odds (~16.7M attempts).
 *
 * Short targets unify too: right-padding `"0000"` with `F` to 64 chars yields
 * `0x0000FF..FF`, and `hash < that` is exactly "hex starts with 0000".
 */
private fun hashLessThanTarget(hash: ByteArray, targetHex: String): Boolean {
    val padded = targetHex.padEnd(64, 'F')
    for (i in 0 until 32) {
        val h = hash[i].toInt() and 0xFF
        val t = ((hexVal(padded[i * 2]) shl 4) or hexVal(padded[i * 2 + 1]))
        if (h != t) return h < t
    }
    return false
}

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

    data object TimedOut : TokenResult
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

    suspend fun getPublishTokenDetailed(onProgress: (Long) -> Unit = {}): TokenResult =
        withContext(Dispatchers.IO) {
            try {
                cachedToken?.takeIf { it.isAlive() }?.let {
                    Logger.d(TAG, "reusing cached token")
                    return@withContext TokenResult.Ok(it.token)
                }
                cachedToken = null
                Logger.d(TAG, "POST $CHALLENGE_URL")
                val fetchMark = TimeSource.Monotonic.markNow()
                val raw: HttpResponse =
                    client.post(CHALLENGE_URL) {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.UserAgent, USER_AGENT)
                    }
                val fetchMs = fetchMark.elapsedNow().inWholeMilliseconds
                val rawBody = raw.bodyAsText()
                Logger.d(TAG, "challenge status=${raw.status.value} fetchMs=$fetchMs body=${rawBody.take(500)}")
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
                val solveMark = TimeSource.Monotonic.markNow()
                val solution =
                    try {
                        withTimeout(POW_TIMEOUT_MS) {
                            solvePowFast(challenge.prefix, challenge.target, onProgress)
                        }
                    } catch (e: TimeoutCancellationException) {
                        Logger.w(TAG, "PoW timed out after ${POW_TIMEOUT_MS}ms")
                        return@withContext TokenResult.TimedOut
                    } ?: return@withContext TokenResult.PowUnsolved
                val solveMs = solveMark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1)
                val rate = solution.attempts * 1000L / solveMs
                Logger.d(TAG, "PoW solved nonce=${solution.nonce} attempts=${solution.attempts} solveMs=$solveMs rate=$rate H/s")
                val token = "${challenge.prefix}:${solution.nonce}"
                cachedToken = CachedToken(token, TimeSource.Monotonic.markNow())
                TokenResult.Ok(token)
            } catch (e: CancellationException) {
                throw e
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
                val publishMark = TimeSource.Monotonic.markNow()
                val result =
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
                Logger.d(TAG, "publishMs=${publishMark.elapsedNow().inWholeMilliseconds}")
                if (result is PublishResult.Error) cachedToken = null
                result
            } catch (e: Exception) {
                PublishResult.Error(-1, e.message ?: e.toString())
            }
        }

    /** Parallel strided PoW: workers own a MessageDigest each, first match wins. */
    suspend fun solvePowFast(
        prefix: String,
        target: String,
        onProgress: (Long) -> Unit = {},
    ): PowSolution? =
        coroutineScope {
            // Self-test the exact hot path (platform hasher): SHA256("hello").
            val selfTestBuf = ByteArray(32)
            powHash("hello".toByteArray(), ByteArray(0), 0, selfTestBuf)
            val selfTest =
                selfTestBuf.toHex() ==
                    "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
            Logger.d(TAG, "SHA-256 self-test passed=$selfTest")
            if (!selfTest) return@coroutineScope null
            require(target.isNotEmpty() && target.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "bad challenge target"
            }
            val workers = powCpuCount().coerceIn(1, 8)
            val prefixBytes = prefix.toByteArray()
            Logger.d(TAG, "PoW start workers=$workers prefix='$prefix' target='$target'")
            val mutex = Mutex()
            var total = 0L
            suspend fun report(delta: Long) {
                mutex.withLock {
                    total += delta
                    onProgress(total)
                }
            }
            val jobs =
                (0 until workers).map { id ->
                    async(Dispatchers.Default) {
                        val buf = ByteArray(20)
                        val hashBuf = ByteArray(32)
                        var nonce = id.toLong()
                        var local = 0L
                        var reported = 0L
                        var checks = 0
                        suspend fun flush() {
                            val delta = local - reported
                            if (delta > 0) {
                                reported = local
                                report(delta)
                            }
                        }
                        while (nonce < MAX_NONCE) {
                            if (++checks >= 1000) {
                                ensureActive()
                                checks = 0
                            }
                            val len = writeLongAscii(nonce, buf)
                            powHash(prefixBytes, buf, len, hashBuf)
                            if (nonce < 5) {
                                val hashHex = hashBuf.toHex()
                                Logger.d(TAG, "attempt $nonce: input='$prefix$nonce' hash=$hashHex first4=${hashHex.take(4)} target=$target")
                            }
                            if (hashLessThanTarget(hashBuf, target)) {
                                flush()
                                return@async nonce
                            }
                            nonce += workers
                            local++
                            if (local - reported >= PROGRESS_EVERY) flush()
                        }
                        flush()
                        null
                    }
                }
            val pending = jobs.toMutableList()
            var winner: Long? = null
            var winnerAttempts = 0L
            while (winner == null && pending.isNotEmpty()) {
                val done = select<Deferred<Long?>> { pending.forEach { d -> d.onAwait { d } } }
                pending.remove(done)
                if (done.isCompleted) {
                    val v = done.await()
                    if (v != null) {
                        winner = v
                        winnerAttempts = total
                    }
                }
            }
            coroutineContext[Job]?.cancelChildren()
            if (winner == null) {
                // Exhausted: dump forensic samples using the SAME path as the loop
                // (prefix + decimal nonce). The old dump hashed the bare prefix —
                // that mismatch is why its hash never equaled attempt 0's.
                val zeroBuf = ByteArray(32)
                powHash(prefixBytes, "0".toByteArray(), 1, zeroBuf)
                Logger.w(TAG, "PoW exhausted prefix='$prefix' target='$target' hash(prefix+\"0\")=${zeroBuf.toHex()}")
                for (n in 0L..4L) {
                    val ns = n.toString()
                    val sampleBuf = ByteArray(32)
                    powHash(prefixBytes, ns.toByteArray(), ns.length, sampleBuf)
                    val sample = sampleBuf.toHex()
                    Logger.w(TAG, "sample nonce=$n first4=${sample.take(4)} target=$target")
                }
            }
            winner?.let { PowSolution(it, winnerAttempts) }
        }

    /** Lowest nonce whose hex SHA-256 starts with [target] (legacy sequential path). */
    internal fun solvePow(prefix: String, target: String): Long? {
        for (nonce in 0 until MAX_NONCE) {
            if (sha256Hex(prefix + nonce).startsWith(target)) return nonce
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
