package com.maxrave.earixrust

import android.util.Log

/**
 * Failure-tolerant entry point to the Rust core.
 *
 * Every call is wrapped so that a missing library, a broken ABI, an `UnsatisfiedLinkError` or a
 * panic converted into an exception by UniFFI all resolve to `null`. The caller then keeps
 * using the Kotlin implementation, which is exactly the pre-migration behaviour.
 *
 * Diagnostics deliberately contain no lyric text and no user data — only the failure type.
 */
object RustLyricsBridge {
    private const val TAG = "EarixRust"

    /** `null` while healthy, otherwise the reason the native core is unusable. */
    private val loadFailure: String? by lazy {
        try {
            // Proves the .so is present, mapped and callable before any parsing is attempted.
            val version = coreVersion()
            Log.i(TAG, "Rust core loaded (earix-core $version)")
            null
        } catch (t: Throwable) {
            val reason = "${t::class.simpleName}: ${t.message ?: "no message"}"
            Log.w(TAG, "Rust core unavailable, falling back to the Kotlin parser ($reason)")
            reason
        }
    }

    /** True when the native core loaded successfully. Never throws. */
    fun isAvailable(): Boolean = loadFailure == null

    /** The load failure reason, for diagnostics. Never contains user data. */
    fun unavailableReason(): String? = loadFailure

    /**
     * Parses an LRC document. Returns `null` when Rust cannot be used (load failure or a call
     * failure), so the caller can fall back. An empty list is a *valid* result meaning "no
     * timed lines", not a failure.
     */
    fun parseLrc(data: String): List<LrcLine>? {
        if (loadFailure != null) return null
        return try {
            parseLrcLines(data)
        } catch (t: Throwable) {
            Log.w(TAG, "Rust LRC parse failed, falling back to Kotlin (${t::class.simpleName})")
            null
        }
    }
}
