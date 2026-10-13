package org.simpmusic.lyrics.crypto

/**
 * Hex-encoded SHA-256 digest (lowercase) of [input] (UTF-8 bytes).
 *
 * Multiplatform: JVM/Android use `java.security.MessageDigest`, iOS uses the
 * self-contained [sha256PureKotlin] below. Same bytes on every platform.
 */
expect fun sha256Hex(input: String): String
