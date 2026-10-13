package org.simpmusic.lyrics.crypto

/**
 * One-shot SHA-256 over `prefix + nonceAscii[0 until nonceLen]`.
 *
 * Android/JVM hold a single `MessageDigest` (reset per attempt — NOT thread-safe,
 * so every PoW worker owns its own [PowHasher]). iOS hashes the concatenated
 * copy with [sha256PureKotlin]; same bytes everywhere.
 */
expect class PowHasher {
    fun hash(
        prefix: ByteArray,
        nonceAscii: ByteArray,
        nonceLen: Int,
    ): ByteArray
}

/** PoW worker count: cores capped at 8 (Exynos 1330 class). */
expect fun powCpuCount(): Int
