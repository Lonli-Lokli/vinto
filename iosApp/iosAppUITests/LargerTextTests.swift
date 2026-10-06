import XCTest

/// Larger Text, measured on the real build: how big the words get when the reader asks iOS for
/// bigger ones.
///
/// Apple's bar for the Larger Text label is text that grows to at least 200% through Dynamic Type.
/// The audit cannot measure that here — Compose draws its own glyphs, so there is no `UIFont` to
/// inspect — so this measures the rendered words: the width of one line of text at the default
/// size against the same line at the accessibility sizes. A single line's width is proportional to
/// its font size, so the ratio IS the scale.
///
/// History, because the number moved on 2026-10-05. Compose Multiplatform maps the size categories
/// to a linear font scale that stops at 1.8 (measured: AX-L 1.50x, AX-XXXL 1.80x), which can never
/// reach the label. `DynamicType.kt` / `SystemBars.ios.kt` (owner, 2026-10-05) replaced it with
/// Apple's own body-text table, capped at AX3's 2.35x. This test is what says which of the two the
/// build on the simulator actually draws. Whether the layouts survive that size is the audit's and
/// the common task's to show (`AccessibilityAuditTests`, `CommonTaskTests`, both run at AX-XXXL).
final class LargerTextTests: RestoresAppearance {
    /// One line, never wrapped at any size on this phone, drawn in the app's ordinary type.
    private static let sample = "Hold less. Know more."

    private func width(at size: TextSize) -> CGFloat {
        let app = Harness.launch(state: "home", appearance: .light, size: size)
        let line = app.staticTexts[Self.sample]
        XCTAssertTrue(line.waitForExistence(timeout: 15), "the tagline is not on Home")
        Thread.sleep(forTimeInterval: 1)
        Harness.record(app, name: "larger-text-home-\(size)", into: self)
        let width = line.frame.width
        app.terminate()
        return width
    }

    /// The text follows Dynamic Type, and at the largest size it is at least twice the default.
    func testTextGrowsToAtLeastTwiceItsSizeThroughDynamicType() {
        let standard = width(at: .standard)
        let large = width(at: .accessibilityL)
        let largest = width(at: .accessibilityXXXL)
        let scaleL = large / standard
        let scaleXXXL = largest / standard
        Harness.write(
            String(format: "default %.1f pt, AX-L %.1f pt (%.2fx), AX-XXXL %.1f pt (%.2fx)\n",
                   standard, large, scaleL, largest, scaleXXXL),
            name: "larger-text-scale.txt"
        )
        XCTAssertGreaterThan(scaleL, 1.3, "the text did not follow Dynamic Type: AX-L drew it at \(scaleL)x")
        XCTAssertGreaterThanOrEqual(scaleXXXL, scaleL, "AX-XXXL drew the text smaller than AX-L")
        XCTAssertGreaterThanOrEqual(
            scaleXXXL, 2.0,
            "the largest text is \(scaleXXXL)x the default; Apple's Larger Text bar is 2x"
        )
    }
}
