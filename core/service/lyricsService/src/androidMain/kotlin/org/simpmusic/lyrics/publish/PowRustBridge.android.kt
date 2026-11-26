package org.simpmusic.lyrics.publish

import com.maxrave.earixrust.RustPowBridge

/**
 * Android is the only target that ships the Rust core. The bridge already turns a missing library,
 * a failed self-test or a converted panic into `null`, which sends the caller back to Kotlin.
 */
internal actual fun solvePowChunkRust(
    prefix: String,
    target: String,
    startNonce: Long,
    stride: Long,
    maxAttempts: Long,
): PowRustChunk? =
    RustPowBridge
        .solveChunk(prefix, target, startNonce, stride, maxAttempts)
        ?.let { PowRustChunk(it.nonce, it.attempts) }
