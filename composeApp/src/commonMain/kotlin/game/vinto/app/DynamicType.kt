package game.vinto.app

/**
 * Apple's Dynamic Type text sizes, smallest first, each with the point size iOS gives body text
 * there. Large, 17 pt, is the default. AccessibilityMedium to AccessibilityExtraExtraExtraLarge are
 * the five sizes Settings only offers behind Larger Accessibility Sizes, often called AX1 to AX5.
 *
 * A copy of games-core's `games.core.a11y.DynamicTypeSize`, which Vinto does not use. Keep the two
 * the same.
 */
enum class DynamicTypeSize(val bodyPoints: Int) {
    ExtraSmall(14),
    Small(15),
    Medium(16),
    Large(17),
    ExtraLarge(19),
    ExtraExtraLarge(21),
    ExtraExtraExtraLarge(23),
    AccessibilityMedium(28),
    AccessibilityLarge(33),
    AccessibilityExtraLarge(40),
    AccessibilityExtraExtraLarge(47),
    AccessibilityExtraExtraExtraLarge(53),
}

/**
 * The `fontScale` Vinto draws with on iOS, from the phone's Dynamic Type size. `GameUIViewController`
 * provides it to the composition in place of Compose's own. A copy of games-core's
 * `games.core.a11y.DynamicType`, which Vinto does not use; keep the two the same.
 *
 * Why (owner, 2026-10-05): Apple's Larger Text accessibility label asks for text that grows to at
 * least 200%. Compose Multiplatform maps the sizes to `fontScale` in straight-line steps capped at
 * 1.8; measured that day on the iPhone 18 Pro Max simulator (iOS 27, Compose Multiplatform 1.12.0),
 * text grew 1.41, 1.51, 1.61, 1.69 and 1.80 times at the five accessibility sizes, so no game could
 * claim the label. Here every size scales as Apple's own body text does (its points over Large's
 * 17), and nothing goes past [MAX_FONT_SCALE].
 *
 * | Size                              | Body pt | fontScale |
 * |-----------------------------------|---------|-----------|
 * | ExtraSmall                        | 14      | 0.82      |
 * | Small                             | 15      | 0.88      |
 * | Medium                            | 16      | 0.94      |
 * | Large (the default)               | 17      | 1.0       |
 * | ExtraLarge                        | 19      | 1.12      |
 * | ExtraExtraLarge                   | 21      | 1.24      |
 * | ExtraExtraExtraLarge              | 23      | 1.35      |
 * | AccessibilityMedium (AX1)         | 28      | 1.65      |
 * | AccessibilityLarge (AX2)          | 33      | 1.94      |
 * | AccessibilityExtraLarge (AX3)     | 40      | 2.35      |
 * | AccessibilityExtraExtraLarge      | 47      | 2.35, not 2.76 |
 * | AccessibilityExtraExtraExtraLarge | 53      | 2.35, not 3.12 |
 *
 * The cap is the owner's call: 2.35 is AX3's own ratio, past the 200% the label asks for, so AX4
 * and AX5 draw as AX3 and every layout is re-checked at that one size. An unknown size (UIKit's
 * "unspecified", or one added after this table) draws at 1.0.
 */
object DynamicType {

    /** Body text at [DynamicTypeSize.Large], the default size, in points. */
    const val DEFAULT_BODY_POINTS = 17

    /** The largest `fontScale` any size gets (owner, 2026-10-05). */
    const val MAX_FONT_SCALE = 2.35f

    /** The `fontScale` for [size]: its body points over the default's, at most [MAX_FONT_SCALE]. */
    fun fontScale(size: DynamicTypeSize?): Float =
        if (size == null) 1f else minOf(size.bodyPoints.toFloat() / DEFAULT_BODY_POINTS, MAX_FONT_SCALE)
}
