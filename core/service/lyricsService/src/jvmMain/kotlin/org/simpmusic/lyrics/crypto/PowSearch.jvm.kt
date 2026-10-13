package org.simpmusic.lyrics.crypto

import java.security.MessageDigest

actual class PowHasher {
    private val md: MessageDigest = MessageDigest.getInstance("SHA-256")

    actual fun hash(
        prefix: ByteArray,
        nonceAscii: ByteArray,
        nonceLen: Int,
    ): ByteArray {
        md.reset()
        md.update(prefix)
        md.update(nonceAscii, 0, nonceLen)
        return md.digest()
    }
}

actual fun powCpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
