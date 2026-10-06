package game.vinto.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the Dynamic Type table Vinto's iOS text follows ([DynamicType]). The App Store's Larger Text
 * label rests on it: Compose's own table stopped at 1.8, and this one has to reach 2.0 and stop at
 * the owner's 2.35. A copy of games-core's `DynamicTypeTest`, as the table is a copy of its table.
 */
class DynamicTypeTest {

    @Test
    fun everySizeScalesAsApplesBodyText() {
        // Apple's body text points over Large's 17, to two places, as the owner's table gives them.
        val expected = mapOf(
            DynamicTypeSize.ExtraSmall to 0.82f,
            DynamicTypeSize.Small to 0.88f,
            DynamicTypeSize.Medium to 0.94f,
            DynamicTypeSize.Large to 1.0f,
            DynamicTypeSize.ExtraLarge to 1.12f,
            DynamicTypeSize.ExtraExtraLarge to 1.24f,
            DynamicTypeSize.ExtraExtraExtraLarge to 1.35f,
            DynamicTypeSize.AccessibilityMedium to 1.65f,
            DynamicTypeSize.AccessibilityLarge to 1.94f,
            DynamicTypeSize.AccessibilityExtraLarge to 2.35f,
            DynamicTypeSize.AccessibilityExtraExtraLarge to 2.35f,
            DynamicTypeSize.AccessibilityExtraExtraExtraLarge to 2.35f,
        )
        assertEquals(DynamicTypeSize.entries.toSet(), expected.keys, "a size has no row in the table")
        for ((size, scale) in expected) {
            assertEquals(scale, DynamicType.fontScale(size), TWO_PLACES, "$size (${size.bodyPoints} pt)")
        }
    }

    @Test
    fun theDefaultSizeIsExactlyOne() {
        assertEquals(1f, DynamicType.fontScale(DynamicTypeSize.Large))
    }

    @Test
    fun anUnknownSizeIsOne() {
        assertEquals(1f, DynamicType.fontScale(null))
    }

    @Test
    fun nothingGoesPastTheCap() {
        assertEquals(2.35f, DynamicType.MAX_FONT_SCALE)
        for (size in DynamicTypeSize.entries) {
            assertTrue(DynamicType.fontScale(size) <= DynamicType.MAX_FONT_SCALE, "$size is past the cap")
        }
        // The two largest are held, not merely shrunk: they draw exactly as the cap.
        val held = listOf(
            DynamicTypeSize.AccessibilityExtraExtraLarge,
            DynamicTypeSize.AccessibilityExtraExtraExtraLarge,
        )
        for (size in held) assertEquals(DynamicType.MAX_FONT_SCALE, DynamicType.fontScale(size), "$size")
    }

    @Test
    fun theLargerTextLabelIsReachedAtAccessibilityExtraLarge() {
        // Apple's bar is 200%. The two sizes below it stay below it, as Apple's own text does.
        assertTrue(DynamicType.fontScale(DynamicTypeSize.AccessibilityLarge) < 2f)
        for (size in DynamicTypeSize.entries.filter { it >= DynamicTypeSize.AccessibilityExtraLarge }) {
            val scale = DynamicType.fontScale(size)
            assertTrue(scale >= 2f, "$size draws at $scale, under 200%")
        }
    }

    @Test
    fun theScaleNeverShrinksAsTheSizeGrows() {
        val scales = DynamicTypeSize.entries.map(DynamicType::fontScale)
        assertEquals(scales.sorted(), scales, "the table is out of order: $scales")
    }

    private companion object {
        const val TWO_PLACES = 0.005f
    }
}
