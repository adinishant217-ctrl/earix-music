package org.simpmusic.lyrics.crypto

/**
 * One-shot SHA-256 over `prefix + nonceAscii[0 until nonceLen]`.
 *
 * Deliberately functions, not a class: Android/JVM back this with a
 * per-thread `MessageDigest` (reset per attempt — MessageDigest is NOT
 * thread-safe, and one instance per worker thread gives reuse with zero
 * contention). iOS hashes via [sha256PureKotlin]; same bytes everywhere.
 */
expect fun powHash(
    prefix: ByteArray,
    nonceAscii: ByteArray,
    nonceLen: Int,
): ByteArray

/** PoW worker count: cores capped at 8 (Exynos 1330 class). */
expect fun powCpuCount(): Int
