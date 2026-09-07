package game.vinto.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The face a seed names, and the properties a renderer is allowed to rely on.
 *
 * These run in `commonTest`, so they run on the JVM, on Kotlin/JS, on Wasm and on the iOS
 * simulator — which is the point rather than thoroughness. The seed is a `Long` and a `Long` is
 * two `Int`s on Kotlin/JS; a derivation that agreed with itself only on the JVM would put a
 * different face on the same player depending on which client was looking, and it would do it
 * silently.
 */
class AvatarTest {

    private val awkward = listOf(Long.MIN_VALUE, -1L, 0L, 1L, 42L, Long.MAX_VALUE, 1_739_812_345_678L)

    @Test
    fun theSameSeedAlwaysNamesTheSameFace() {
        for (kind in AvatarKind.entries) {
            for (seed in awkward) {
                assertEquals(
                    mintAvatar(kind, seed),
                    mintAvatar(kind, seed),
                    "$kind seed $seed drew two different faces",
                )
            }
        }
    }

    @Test
    fun everySeedNamesAFaceIncludingTheAwkwardOnes() {
        // Totality is the whole reason there is no door on this value: a seed cannot be refused,
        // so no seed may throw. Long.MIN_VALUE is the one that breaks naive `abs` arithmetic, and
        // negatives are what a client that made its own number up would most likely send.
        val seeds = awkward + (1L..120L)
        for (kind in AvatarKind.entries) {
            for (seed in seeds) {
                val traits = mintAvatar(kind, seed)
                assertEquals(kind, traits.kind, "asked for $kind at seed $seed")
                val why = "$kind seed $seed"
                when (traits) {
                    is AvatarTraits.Mark -> {
                        assertTrue(traits.bars in AvatarRanges.BARS, "bars ${traits.bars}: $why")
                        assertTrue(traits.tilt in AvatarRanges.TURN, "tilt ${traits.tilt}: $why")
                        assertTrue(traits.waver in AvatarRanges.WAVER, "waver ${traits.waver}: $why")
                        assertTrue(traits.curl in AvatarRanges.SWING, "curl ${traits.curl}: $why")
                    }

                    is AvatarTraits.Face -> {
                        assertTrue(traits.eyes in AvatarRanges.EYES, "eyes ${traits.eyes}: $why")
                        assertTrue(traits.spacing in AvatarRanges.SPACING, "spacing ${traits.spacing}: $why")
                        assertTrue(traits.brow in AvatarRanges.SWING, "brow ${traits.brow}: $why")
                        assertTrue(traits.mouth in AvatarRanges.MOUTH, "mouth ${traits.mouth}: $why")
                    }

                    is AvatarTraits.Orbit -> {
                        assertTrue(traits.sides in AvatarRanges.SIDES, "sides ${traits.sides}: $why")
                        assertTrue(traits.rings in AvatarRanges.RINGS, "rings ${traits.rings}: $why")
                        assertTrue(traits.spin in AvatarRanges.TURN, "spin ${traits.spin}: $why")
                        assertTrue(traits.pip in AvatarRanges.PIP, "pip ${traits.pip}: $why")
                    }

                    is AvatarTraits.Herald -> {
                        assertTrue(traits.division in AvatarRanges.DIVISION, "division ${traits.division}: $why")
                        assertTrue(traits.charge in AvatarRanges.CHARGE, "charge ${traits.charge}: $why")
                        assertTrue(traits.flip in AvatarRanges.FLIP, "flip ${traits.flip}: $why")
                        assertTrue(traits.weight in AvatarRanges.WEIGHT, "weight ${traits.weight}: $why")
                    }

                    is AvatarTraits.Knot -> {
                        assertTrue(traits.strands in AvatarRanges.STRANDS, "strands ${traits.strands}: $why")
                        assertTrue(traits.lobes in AvatarRanges.LOBES, "lobes ${traits.lobes}: $why")
                        assertTrue(traits.over in AvatarRanges.FLIP, "over ${traits.over}: $why")
                        assertTrue(traits.spin in AvatarRanges.TURN, "spin ${traits.spin}: $why")
                    }

                    is AvatarTraits.Rune -> {
                        assertTrue(traits.branches in AvatarRanges.BRANCHES, "branches ${traits.branches}: $why")
                        assertTrue(traits.side in AvatarRanges.SIDE, "side ${traits.side}: $why")
                        assertTrue(traits.angle in AvatarRanges.ANGLE, "angle ${traits.angle}: $why")
                        assertTrue(traits.head in AvatarRanges.HEAD, "head ${traits.head}: $why")
                    }

                    is AvatarTraits.Whorl -> {
                        assertTrue(traits.arms in AvatarRanges.ARMS, "arms ${traits.arms}: $why")
                        assertTrue(traits.turns in AvatarRanges.TWISTS, "turns ${traits.turns}: $why")
                        assertTrue(traits.hand == -1 || traits.hand == 1, "hand ${traits.hand}: $why")
                        assertTrue(traits.pip in AvatarRanges.PIP, "pip ${traits.pip}: $why")
                    }
                }
            }
        }
    }

    @Test
    fun aRowOffersSixFacesThatAreNotEachOthers() {
        for (kind in AvatarKind.entries) {
            val row = avatarRow(kind, from = 1_739_812_345_678L)
            assertEquals(AVATAR_ROW, row.size)
            assertEquals(row.size, row.toSet().size, "$kind repeated a seed")

            // A row is the one place six faces are seen side by side, so near-duplicates are more
            // visible here than anywhere else. Five of six leaves room for the birthday-problem
            // collisions a small trait space genuinely produces, while still failing a family
            // that has collapsed onto one axis.
            val faces = row.map { mintAvatar(kind, it) }.toSet()
            assertTrue(faces.size >= AVATAR_ROW - 1, "$kind drew only ${faces.size} distinct faces in a row")
        }
    }

    @Test
    fun theSevenRowsAreSevenDifferentSetsOfSeeds() {
        // One `from` gives seven rows. Without the family stride they would be seven views of
        // nearly the same seeds — the same shape in seven costumes, which defeats the rows.
        val sheet = avatarSheet(from = 4L)
        assertEquals(AvatarKind.entries.size, sheet.size)
        val everySeed = sheet.values.flatten()
        assertEquals(everySeed.size, everySeed.toSet().size, "two families offered the same seed")
    }

    @Test
    fun oneSeedUnderTwoFamiliesIsNotTheSameRunOfNumbers() {
        // Mark and Orbit both draw a twelve-step rotation. Reading it from the same run would make
        // a family's row predictable from another's, which is the shape of a stride that is too
        // small to separate them.
        val marks = (1L..40L).map { mintAvatar(AvatarKind.MARK, it) as AvatarTraits.Mark }
        val orbits = (1L..40L).map { mintAvatar(AvatarKind.ORBIT, it) as AvatarTraits.Orbit }
        val shared = marks.indices.count { marks[it].tilt == orbits[it].spin }
        assertTrue(shared < 20, "$shared of 40 seeds gave Mark and Orbit the same rotation")
    }

    @Test
    fun steppingTheSeedByOneChangesMoreThanOneThing() {
        // What `mintNickname` records in its own comment, held here as a test: traits read straight
        // out of the seed's bits make consecutive seeds walk a single axis, which looks broken.
        for (kind in AvatarKind.entries) {
            val changed = (0L until 64L).count { mintAvatar(kind, it) != mintAvatar(kind, it + 1) }
            assertTrue(changed >= 55, "$kind: only $changed of 64 consecutive seeds drew a different face")
        }
    }

    @Test
    fun aRowFromTheSameNumberIsTheSameRow() {
        // What lets the picker be reopened, and a test name a face without a clock.
        assertEquals(avatarRow(AvatarKind.KNOT, from = 99L), avatarRow(AvatarKind.KNOT, from = 99L))
    }

    @Test
    fun regeneratingOffersDifferentFaces() {
        // The button's whole job. A `from` that changed the seeds but not the faces would look
        // broken in exactly the way a player would report as "it does nothing".
        for (kind in AvatarKind.entries) {
            val first = avatarRow(kind, from = 1L).map { mintAvatar(kind, it) }
            val second = avatarRow(kind, from = 2L).map { mintAvatar(kind, it) }
            assertTrue(first != second, "$kind offered the same row twice")
        }
    }

    @Test
    fun anUnknownFamilyFallsBackRatherThanFailing() {
        // A build one version older has never heard of the family a newer one is using. The seat
        // keeps a mark; it is simply not the one its owner chose.
        assertEquals(AvatarKind.MARK, avatarKindOf(-1))
        assertEquals(AvatarKind.MARK, avatarKindOf(AvatarKind.entries.size))
        for (kind in AvatarKind.entries) assertEquals(kind, avatarKindOf(kind.ordinal))
    }
}
