package org.simpmusic.lyrics.publish

/**
 * Desktop keeps the Kotlin loop. The Rust core is built for Android ABIs only in this batch, and
 * `powHash` on the JVM already uses the JCE's hardware-accelerated SHA-256.
 */
internal actual fun solvePowChunkRust(
    prefix: String,
    target: String,
    startNonce: Long,
    stride: Long,
    maxAttempts: Long,
): PowRustChunk? = null
