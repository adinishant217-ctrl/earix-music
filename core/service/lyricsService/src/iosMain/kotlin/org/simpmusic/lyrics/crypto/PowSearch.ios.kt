package org.simpmusic.lyrics.crypto

actual fun powHash(
    prefix: ByteArray,
    nonceAscii: ByteArray,
    nonceLen: Int,
    out: ByteArray,
): Int {
    val full = sha256PureKotlin(prefix + nonceAscii.copyOf(nonceLen))
    full.copyInto(out)
    return 32
}

actual fun powCpuCount(): Int = 2
