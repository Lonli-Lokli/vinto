package game.vinto.shapes

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * FIPS 180-4 vectors plus the boundary cases a hand-written implementation gets wrong:
 * the 55/56/64-byte inputs around the padding block split.
 */
class Sha256Test {

    @Test
    fun matchesPublishedVectors() {
        val vectors = listOf(
            "" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "abc" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" to
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
        )

        for ((input, expected) in vectors) {
            assertEquals(expected, Sha256.hex(input), "digest mismatch for '$input'")
        }
    }

    @Test
    fun handlesThePaddingBlockBoundary() {
        // 55 bytes fits the length in the same block; 56 forces a second; 64 is exact.
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            Sha256.hex("a".repeat(55)),
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            Sha256.hex("a".repeat(56)),
        )
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            Sha256.hex("a".repeat(64)),
        )
    }

    @Test
    fun hashesMultibyteTextAsUtf8() {
        // The canonical string may contain non-ASCII (nicknames), so the digest must be
        // over UTF-8 bytes, not UTF-16 code units.
        assertEquals(
            "53a5f3d7f9b1b2d4b0ee3e2e9e35e6c3ff0cbb9b3e58f9a2b0e8e5b06c9d8a4f".length,
            Sha256.hex("Zoë").length,
        )
        assertEquals(Sha256.hex("Zoë".encodeToByteArray()), Sha256.hex("Zoë"))
    }
}
