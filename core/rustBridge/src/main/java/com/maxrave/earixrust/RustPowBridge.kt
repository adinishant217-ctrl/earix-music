package com.maxrave.earixrust

import android.util.Log

/**
 * Failure-tolerant entry point to the Rust proof-of-work search.
 *
 * Same contract as [RustLyricsBridge]: a missing library, a broken ABI, a failed self-test or a
 * panic converted into an exception by UniFFI all resolve to `null`, and the caller keeps using the
 * Kotlin loop. Nothing here can throw past this object.
 *
 * The self-test is not ceremony. Kotlin's own path verifies `SHA256("hello")` before trusting the
 * platform provider; the native core gets the same check, because a `.so` built for the wrong ABI
 * or with a broken hash backend would otherwise silently hand the server nonces it will reject —
 * the failure mode that looks like "publishing is broken" rather than "the native library is wrong".
 */
object RustPowBridge {
    private const val TAG = "EarixRust"

    /** One chunk's result, mirroring the Rust record without leaking the binding type upward. */
    data class Chunk(
        val nonce: Long?,
        val attempts: Long,
    )

    /** `null` while healthy, otherwise the reason the native PoW path is unusable. */
    private val loadFailure: String? by lazy {
        try {
            if (powSelfTest()) {
                Log.i(TAG, "Rust PoW self-test passed")
                null
            } else {
                Log.w(TAG, "Rust PoW self-test FAILED, using the Kotlin loop")
                "sha256 self-test failed"
            }
        } catch (t: Throwable) {
            val reason = "${t::class.simpleName}: ${t.message ?: "no message"}"
            Log.w(TAG, "Rust PoW unavailable, using the Kotlin loop ($reason)")
            reason
        }
    }

    /** True when the native core loaded and its SHA-256 passed the self-test. Never throws. */
    fun isAvailable(): Boolean = loadFailure == null

    /** The unavailability reason, for diagnostics. Never contains user data. */
    fun unavailableReason(): String? = loadFailure

    /**
     * Tests one chunk of the nonce space. Returns `null` when Rust cannot be used, so the caller
     * falls back to Kotlin from the same nonce it asked for — no nonce is ever skipped.
     */
    fun solveChunk(
        prefix: String,
        target: String,
        startNonce: Long,
        stride: Long,
        maxAttempts: Long,
    ): Chunk? {
        if (loadFailure != null) return null
        return try {
            val chunk = solvePowChunk(prefix, target, startNonce, stride, maxAttempts)
            Chunk(chunk.nonce, chunk.attempts)
        } catch (t: Throwable) {
            // A rejected challenge target or a bad stride is a programming error, not a reason to
            // keep hammering the native path: report it and let Kotlin take over.
            Log.w(TAG, "Rust PoW chunk failed, falling back to Kotlin (${t::class.simpleName})")
            null
        }
    }
}
