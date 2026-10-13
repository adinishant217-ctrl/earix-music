package org.simpmusic.lyrics.crypto

/**
 * Pure-Kotlin SHA-256 (FIPS 180-4), dependency-free.
 *
 * Used by the iOS actual of [sha256Hex]. JVM/Android use MessageDigest instead.
 * Test vectors: sha256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855,
 * sha256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.
 */
internal fun sha256PureKotlin(input: ByteArray): ByteArray {
    val k =
        intArrayOf(
            0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
            0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
            0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
            0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
            0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
            0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
            0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
            0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )
    var h0 = 0x6a09e667.toInt()
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372.toInt()
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f.toInt()
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab.toInt()
    var h7 = 0x5be0cd19.toInt()

    val bitLen = input.size.toLong() * 8L
    val paddedLen = ((input.size + 8) / 64 + 1) * 64
    val msg = ByteArray(paddedLen)
    input.copyInto(msg)
    msg[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
        msg[paddedLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
    }

    val w = IntArray(64)
    var off = 0
    while (off < paddedLen) {
        for (i in 0 until 16) {
            w[i] =
                ((msg[off + i * 4].toInt() and 0xFF) shl 24) or
                ((msg[off + i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((msg[off + i * 4 + 2].toInt() and 0xFF) shl 8) or
                (msg[off + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + s1 + ch + k[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
        off += 64
    }

    fun Int.toBytes(out: ByteArray, o: Int) {
        out[o] = (this ushr 24).toByte()
        out[o + 1] = (this ushr 16).toByte()
        out[o + 2] = (this ushr 8).toByte()
        out[o + 3] = this.toByte()
    }
    return ByteArray(32).also {
        h0.toBytes(it, 0)
        h1.toBytes(it, 4)
        h2.toBytes(it, 8)
        h3.toBytes(it, 12)
        h4.toBytes(it, 16)
        h5.toBytes(it, 20)
        h6.toBytes(it, 24)
        h7.toBytes(it, 28)
    }
}

private fun Int.rotateRight(n: Int): Int = (this ushr n) or (this shl (32 - n))

internal fun ByteArray.toHex(): String {
    val hex = CharArray(size * 2)
    val digits = "0123456789abcdef"
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        hex[i * 2] = digits[v ushr 4]
        hex[i * 2 + 1] = digits[v and 0x0F]
    }
    return hex.concatToString()
}
