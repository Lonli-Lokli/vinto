package game.vinto.shapes

/**
 * SHA-256, written out rather than delegated to a platform API.
 *
 * One implementation for every target is the point: `java.security.MessageDigest`,
 * WebCrypto and CommonCrypto would each need an `expect`/`actual` and each is a place the
 * state hash could quietly differ. This is the same reasoning the TypeScript side uses in
 * choosing WebCrypto over `node:crypto` — one code path, no per-platform surprises.
 *
 * Verified against the published FIPS 180-4 vectors in `Sha256Test`.
 */
object Sha256 {

    private val K = intArrayOf(
        0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(),
        0x3956c25bu.toInt(), 0x59f111f1u.toInt(), 0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(),
        0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(),
        0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(),
        0xe49b69c1u.toInt(), 0xefbe4786u.toInt(), 0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(),
        0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
        0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(),
        0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(), 0x06ca6351u.toInt(), 0x14292967u.toInt(),
        0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(),
        0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(),
        0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(), 0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(),
        0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
        0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(),
        0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(), 0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(),
        0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(),
        0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt(),
    )

    private val INITIAL = intArrayOf(
        0x6a09e667,
        0xbb67ae85u.toInt(),
        0x3c6ef372,
        0xa54ff53au.toInt(),
        0x510e527f,
        0x9b05688cu.toInt(),
        0x1f83d9ab,
        0x5be0cd19,
    )

    /** Lowercase hex digest of [message]'s UTF-8 bytes. */
    fun hex(message: String): String = hex(message.encodeToByteArray())

    fun hex(message: ByteArray): String =
        digest(message).joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            value.toString(16).padStart(2, '0')
        }

    fun digest(message: ByteArray): ByteArray {
        val hash = INITIAL.copyOf()
        val padded = pad(message)
        val block = IntArray(64)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val base = offset + i * 4
                block[i] = ((padded[base].toInt() and 0xFF) shl 24) or
                    ((padded[base + 1].toInt() and 0xFF) shl 16) or
                    ((padded[base + 2].toInt() and 0xFF) shl 8) or
                    (padded[base + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(block[i - 15], 7) xor rotr(block[i - 15], 18) xor (block[i - 15] ushr 3)
                val s1 = rotr(block[i - 2], 17) xor rotr(block[i - 2], 19) xor (block[i - 2] ushr 10)
                block[i] = block[i - 16] + s0 + block[i - 7] + s1
            }

            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]

            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[i] + block[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h

            offset += 64
        }

        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (hash[i] ushr 24).toByte()
            out[i * 4 + 1] = (hash[i] ushr 16).toByte()
            out[i * 4 + 2] = (hash[i] ushr 8).toByte()
            out[i * 4 + 3] = hash[i].toByte()
        }
        return out
    }

    private fun rotr(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))

    /** Append 0x80, pad with zeros to 56 mod 64, then the bit length as a big-endian long. */
    private fun pad(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        var paddedSize = message.size + 1
        while (paddedSize % 64 != 56) paddedSize++

        val padded = ByteArray(paddedSize + 8)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()

        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }
        return padded
    }
}
