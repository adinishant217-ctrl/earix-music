package org.simpmusic.lyrics.crypto

actual fun sha256Hex(input: String): String =
    sha256PureKotlin(input.encodeToByteArray()).toHex()
