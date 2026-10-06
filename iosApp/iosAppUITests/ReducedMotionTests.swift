import XCTest
import UIKit

/// Reduced Motion, measured in pixels on the real build with the SYSTEM setting.
///
/// On the table the seat being waited on wears a thinking cloud that moves and a ring that breathes;
/// both read `LocalReducedMotion`, which `Motion.ios.kt` fills from
/// `UIAccessibilityIsReduceMotionEnabled`. So two screenshots of that seat taken 0.45 s apart
/// differ while motion is on and are identical while it is off. That is the whole chain, from the
/// iOS setting to the drawing, with nothing in between faked.
///
/// Run twice. Inside the suite, with Reduce Motion OFF: the frames MUST differ, the control without
/// which "identical" would prove nothing. Then with the simulator's Reduce Motion ON and
/// `TEST_RUNNER_A11Y_EXPECT_REDUCE_MOTION=1`:
///
///     xcrun simctl spawn <device> defaults write com.apple.Accessibility ReduceMotionEnabled -bool true
///     TEST_RUNNER_A11Y_EXPECT_REDUCE_MOTION=1 xcodebuild test-without-building … \
///       -only-testing:iosAppUITests/ReducedMotionTests
///     xcrun simctl spawn <device> defaults write com.apple.Accessibility ReduceMotionEnabled -bool false
final class ReducedMotionTests: RestoresAppearance {
    func testTheWaitingSeatHoldsStillUnderReduceMotion() {
        let expectReduced = ProcessInfo.processInfo.environment["A11Y_EXPECT_REDUCE_MOTION"] == "1"
        XCTAssertEqual(
            UIAccessibility.isReduceMotionEnabled, expectReduced,
            "the simulator's Reduce Motion setting is not what this run expects"
        )
        let app = Harness.launch(state: "table", appearance: .light, size: .standard)
        XCTAssertTrue(Harness.button(app, "Discard").waitForExistence(timeout: 15), "no table")
        Thread.sleep(forTimeInterval: 3)
        let seat = Harness.anything(app, containing: "the table is waiting on this seat")
        XCTAssertTrue(seat.exists, "no seat is being waited on")
        let frame = seat.frame
        let first = app.screenshot()
        Thread.sleep(forTimeInterval: 0.45)
        let second = app.screenshot()
        let tag = expectReduced ? "reduce-motion-on" : "reduce-motion-off"
        Harness.record(app, name: tag, into: self)
        guard let one = Pixels(first), let two = Pixels(second) else {
            XCTFail("could not decode the screenshots")
            return
        }
        let changed = one.changed(comparedTo: two, in: frame)
        Harness.write("seat \(frame.integral): \(changed) pixels changed in 0.45 s\n", name: "\(tag).txt")
        if expectReduced {
            XCTAssertEqual(changed, 0, "with Reduce Motion on, the waiting seat still moved (\(changed) pixels)")
        } else {
            XCTAssertGreaterThan(changed, 0, "with Reduce Motion off, the waiting seat did not move at all")
        }
    }
}

/// A screenshot as RGBA bytes, for counting the pixels that differ between two of them.
private struct Pixels {
    let bytes: [UInt8]
    let width: Int
    let height: Int
    let scale: CGFloat

    @MainActor
    init?(_ shot: XCUIScreenshot) {
        guard let image = shot.image.cgImage else { return nil }
        // Locals, so the drawing closure captures nothing of a value still being built.
        let w = image.width, h = image.height
        var buffer = [UInt8](repeating: 0, count: w * h * 4)
        let drawn = buffer.withUnsafeMutableBytes { raw -> Bool in
            guard let context = CGContext(
                data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8,
                bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drawn else { return nil }
        bytes = buffer
        width = w
        height = h
        scale = CGFloat(w) / shot.image.size.width
    }

    /// Pixels inside `frame` (in points) that are not the same in `other`.
    func changed(comparedTo other: Pixels, in frame: CGRect) -> Int {
        let x0 = max(0, Int(frame.minX * scale)), x1 = min(width, Int(frame.maxX * scale))
        let y0 = max(0, Int(frame.minY * scale)), y1 = min(height, Int(frame.maxY * scale))
        guard x1 > x0, y1 > y0, other.width == width, other.height == height else { return -1 }
        var count = 0
        for y in y0..<y1 {
            for x in x0..<x1 {
                let i = (y * width + x) * 4
                if bytes[i] != other.bytes[i] || bytes[i + 1] != other.bytes[i + 1] || bytes[i + 2] != other.bytes[i + 2] {
                    count += 1
                }
            }
        }
        return count
    }
}
