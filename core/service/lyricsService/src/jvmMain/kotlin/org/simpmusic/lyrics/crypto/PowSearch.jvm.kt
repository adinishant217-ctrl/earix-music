package org.simpmusic.lyrics.crypto

import java.security.MessageDigest

private val threadLocalMd: ThreadLocal<MessageDigest> =
    ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

actual fun powHash(
    prefix: ByteArray,
    nonceAscii: ByteArray,
    nonceLen: Int,
): ByteArray {
    val md = threadLocalMd.get()
    md.reset()
    md.update(prefix)
    md.update(nonceAscii, 0, nonceLen)
    return md.digest()
}

actual fun powCpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
