package org.simpmusic.lyrics.publish

/** iOS has no Rust core in this batch; the Kotlin loop is authoritative there. */
internal actual fun solvePowChunkRust(
    prefix: String,
    target: String,
    startNonce: Long,
    stride: Long,
    maxAttempts: Long,
): PowRustChunk? = null
