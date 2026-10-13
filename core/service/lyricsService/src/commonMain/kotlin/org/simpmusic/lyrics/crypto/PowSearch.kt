package org.simpmusic.lyrics.crypto

/**
 * SHA-256 over `prefix + nonceAscii[0 until nonceLen]`, written into [out]
 * (which must hold 32 bytes). Returns bytes written.
 *
 * Deliberately functions, not a class: Android/JVM back this with a
 * per-thread `MessageDigest` (reset per attempt — MessageDigest is NOT
 * thread-safe, one instance per worker thread gives reuse with zero
 * contention). The reusable output buffer avoids one 32B allocation per
 * attempt. iOS hashes via [sha256PureKotlin]; same bytes everywhere.
 */
expect fun powHash(
    prefix: ByteArray,
    nonceAscii: ByteArray,
    nonceLen: Int,
    out: ByteArray,
): Int

/** PoW worker count: cores capped at 8 (Exynos 1330 class). */
expect fun powCpuCount(): Int
