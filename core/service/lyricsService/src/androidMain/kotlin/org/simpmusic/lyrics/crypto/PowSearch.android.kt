package org.simpmusic.lyrics.crypto

import com.maxrave.logger.Logger
import java.security.MessageDigest

private const val TAG = "PowSearch"

private val threadLocalMd: ThreadLocal<MessageDigest> =
    ThreadLocal.withInitial {
        val md =
            try {
                MessageDigest.getInstance("SHA-256", "Conscrypt")
            } catch (e: Exception) {
                MessageDigest.getInstance("SHA-256")
            }
        Logger.d(TAG, "SHA-256 provider=${md.provider.name} info=${md.provider.info}")
        md
    }

actual fun powHash(
    prefix: ByteArray,
    nonceAscii: ByteArray,
    nonceLen: Int,
    out: ByteArray,
): Int {
    val md = threadLocalMd.get()
    md.reset()
    md.update(prefix)
    md.update(nonceAscii, 0, nonceLen)
    return md.digest(out, 0, out.size)
}

actual fun powCpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
