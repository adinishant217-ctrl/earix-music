package org.simpmusic.lyrics.crypto

actual class PowHasher {
    actual fun hash(
        prefix: ByteArray,
        nonceAscii: ByteArray,
        nonceLen: Int,
    ): ByteArray = sha256PureKotlin(prefix + nonceAscii.copyOf(nonceLen))
}

actual fun powCpuCount(): Int = 2
