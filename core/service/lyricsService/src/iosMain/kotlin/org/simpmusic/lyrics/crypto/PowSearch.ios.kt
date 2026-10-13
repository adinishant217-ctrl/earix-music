package org.simpmusic.lyrics.crypto

actual fun powHash(
    prefix: ByteArray,
    nonceAscii: ByteArray,
    nonceLen: Int,
): ByteArray = sha256PureKotlin(prefix + nonceAscii.copyOf(nonceLen))

actual fun powCpuCount(): Int = 2
