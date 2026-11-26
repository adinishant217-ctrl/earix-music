package org.simpmusic.lyrics.publish

/**
 * One slice of the proof-of-work search, produced by the Rust core.
 *
 * `nonce == null` means "this slice found nothing, keep going"; the field is nullable rather than
 * sentinel-valued because `0` is a legitimate winning nonce.
 */
internal data class PowRustChunk(
    val nonce: Long?,
    val attempts: Long,
)

/**
 * Tests up to [maxAttempts] nonces in `startNonce, startNonce + stride, ...` with the Rust core.
 *
 * Returns `null` when the native path cannot be used — no library on this platform, a failed
 * SHA-256 self-test, or any error inside the bridge. Callers then run the Kotlin loop, which is the
 * pre-migration behaviour and stays the source of truth.
 *
 * Deliberately NOT a "solve everything" call: the caller keeps its own coroutines, its own striding
 * and its own cancellation, and Rust owns only the arithmetic in between. That is what stops this
 * migration from introducing native threads that Kotlin could not cancel.
 */
internal expect fun solvePowChunkRust(
    prefix: String,
    target: String,
    startNonce: Long,
    stride: Long,
    maxAttempts: Long,
): PowRustChunk?
