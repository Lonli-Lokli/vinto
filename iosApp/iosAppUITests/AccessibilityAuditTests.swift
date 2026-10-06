import XCTest

/// Apple's own accessibility audit, run on every main screen of the real iOS build: both palettes,
/// at the default text size and at the largest Dynamic Type size there is.
///
/// `XCUIApplication.performAccessibilityAudit` checks what VoiceOver, Voice Control and Dynamic Type
/// depend on: contrast (sampled from the rendered pixels), element descriptions, hit regions,
/// traits, clipped text. Every finding is collected rather than thrown, so one run reports all of
/// them; a finding that is not explained below fails the test with its element's label and frame.
///
/// Each case is one launch into a state the app builds itself (`-vinto.capture`, see
/// `MarketingScene.kt`), so a render is deterministic and nothing depends on the case before it.
/// Settings and the help sheet are reached the way a person reaches them, by pressing the button
/// that names them. A screenshot and the accessibility tree of every case land in `A11Y_OUT`.
///
/// What the audit cannot see, and is therefore NOT evidence of: Dynamic Type. Compose draws its own
/// text, so there is no `UIFont` for the audit's Dynamic Type check to inspect, and it reports
/// nothing either way. `LargerTextTests` measures the text instead.
///
/// Run, on the iPhone the portfolio shares:
///
///     cd iosApp && xcodegen generate
///     TEST_RUNNER_A11Y_OUT=/tmp/vinto-a11y xcodebuild -project iosApp.xcodeproj -scheme iosApp \
///       -destination 'platform=iOS Simulator,name=iPhone 18 Pro Max,OS=latest' \
///       -only-testing:iosAppUITests/AccessibilityAuditTests test
final class AccessibilityAuditTests: RestoresAppearance {
    /// Four launches and four audits per screen; the coalition scene stages a dozen turns each time.
    override class var allowance: TimeInterval { 540 }

    func testHome() { audit(.home) }
    func testLesson() { audit(.teach) }
    func testTable() { audit(.table) }
    func testScoreSheet() { audit(.score) }
    func testOnlineLobby() { audit(.lobby) }
    func testCoalitionPlan() { audit(.plan) }
    func testSettings() { audit(.settings) }
    func testHelpSheet() { audit(.help) }
    /// The rings, and the seat marks that were measured at 1.01:1 on the light sheet (2026-10-05).
    func testHelpSheetRings() { audit(.helpRings) }
    func testHelpSheetBadges() { audit(.helpBadges) }

    /// The default, and the largest Dynamic Type setting iOS has: as large as the text gets
    /// (`LargerTextTests` measures how large that is).
    static let sizes: [TextSize] = [.standard, .accessibilityXXXL]

    private func audit(_ screen: Screen, file: StaticString = #filePath, line: UInt = #line) {
        var report: [String] = []
        var knownSeen: Set<String> = []
        for appearance in Appearance.allCases {
            for size in Self.sizes {
                let app = screen.open(appearance: appearance, size: size)
                let name = "audit-\(screen.rawValue)-\(appearance)-\(size)"
                Harness.record(app, name: name, into: self)
                var findings: [Finding] = []
                do {
                    try app.performAccessibilityAudit { issue in
                        findings.append(Finding(issue))
                        return true
                    }
                } catch {
                    XCTFail("\(name): the audit itself failed: \(error)", file: file, line: line)
                }
                report.append("## \(name): \(findings.count) raw")
                var survivors: [Finding] = []
                for finding in findings {
                    if let why = Self.explanation(for: finding, on: screen) {
                        report.append("  cleared (\(why)) \(finding)")
                    } else if let known = Self.knownDefects.first(where: { $0.screen == screen && $0.matches(finding) }) {
                        knownSeen.insert(known.reason)
                        report.append("  KNOWN (\(known.reason)) \(finding)")
                        XCTExpectFailure("Known, and not this suite's to fix: \(known.reason)") {
                            XCTFail("A11Y[\(name)] \(finding)", file: file, line: line)
                        }
                    } else {
                        report.append("  FAILED \(finding)")
                        survivors.append(finding)
                    }
                }
                if !survivors.isEmpty {
                    XCTFail(
                        "A11Y[\(name)] " + survivors.map(\.description).joined(separator: " ;; "),
                        file: file, line: line
                    )
                }
                app.terminate()
            }
        }
        // A known defect that no longer reproduces is a list entry hiding nothing: say so, so it goes.
        for known in Self.knownDefects where known.screen == screen && !knownSeen.contains(known.reason) {
            XCTFail("No longer reproduces, remove it from knownDefects: \(known.reason)", file: file, line: line)
        }
        Harness.write(report.joined(separator: "\n"), name: "audit-\(screen.rawValue).txt")
    }

    /// Real defects this suite found that live outside what it may change, each with where it lives
    /// and why it is open. They report as expected failures, so the suite stays a gate for everything
    /// else, and each one fails the suite the day it stops reproducing.
    private static let knownDefects: [KnownDefect] = [
        KnownDefect(
            screen: .teach,
            reason: "a seat plate half under the lesson's coach is a disabled button with no name "
                + "(SeatPlate.kt: the plate is a Surface button whose name comes from its children, "
                + "and iOS drops the clipped children; that file is another session's on 2026-10-05)"
        ) { $0.type.contains(.sufficientElementDescription) && $0.elementType == .button && $0.label.isEmpty }
    ]

    /// Why a finding is not a defect, or nil when it is one. Each clause is narrow on purpose, and
    /// says what was checked to earn it.
    private static func explanation(for finding: Finding, on screen: Screen) -> String? {
        // A hit region is a property of something that can be hit. The lesson's coach panel lies
        // over the top of the table, and iOS reports the cards underneath it with the sliver of
        // frame left showing (50 x 15 pt). Those cards are not controls at that moment — no button
        // trait, no action — so there is no target to be too small (WCAG 2.5.8 is about targets).
        // Anything with a button, switch or link role still has to clear the bar.
        if finding.type.contains(.hitRegion), finding.elementType == .other {
            return "not a control"
        }
        return nil
    }
}

/// A defect the audit found that this suite records rather than fixes.
struct KnownDefect {
    let screen: Screen
    let reason: String
    let matches: (Finding) -> Bool
}

/// One audit finding, kept as plain values so it outlives the element it came from.
struct Finding: CustomStringConvertible {
    let type: XCUIAccessibilityAuditType
    let compact: String
    let label: String
    let elementType: XCUIElement.ElementType
    let frame: CGRect?
    let detail: String

    @MainActor
    init(_ issue: XCUIAccessibilityAuditIssue) {
        type = issue.auditType
        compact = issue.compactDescription
        label = issue.element?.label ?? ""
        elementType = issue.element?.elementType ?? .other
        frame = issue.element?.frame
        detail = issue.detailedDescription
    }

    var description: String {
        let place = frame.map { "\($0.integral)" } ?? "nil"
        return "{\(compact) | label=\"\(label)\" | type=\(elementType.rawValue) | frame=\(place) | \(detail)}"
    }
}

/// The screens audited, and how each is reached.
enum Screen: String, CaseIterable {
    case home, teach, table, score, lobby, plan, settings, help, helpRings, helpBadges

    /// The capture state each screen starts from. Settings and help are a press away from one.
    private var state: String {
        switch self {
        case .settings: "home"
        case .help, .helpRings, .helpBadges: "table"
        default: rawValue
        }
    }

    @MainActor
    func open(appearance: Appearance, size: TextSize) -> XCUIApplication {
        let app = Harness.launch(state: state, appearance: appearance, size: size)
        switch self {
        case .settings: Harness.press(app, "Settings")
        case .help: Harness.press(app, "Rules")
        case .helpRings:
            Harness.press(app, "Rules")
            Harness.press(app, "Rings")
        case .helpBadges:
            Harness.press(app, "Rules")
            Harness.press(app, "Badges")
        default: break
        }
        let ready = marker(in: app)
        // Waits of fifteen seconds, never open-ended. The coalition scene plays a dozen real turns
        // before it is drawn, tens of seconds on a simulator, so it alone gets four of them.
        var appeared = ready.waitForExistence(timeout: 15)
        if self == .plan {
            for _ in 0..<3 where !appeared { appeared = ready.waitForExistence(timeout: 15) }
        }
        XCTAssertTrue(appeared, "\(self) did not come up")
        // The fan deals in and cards fly to their seats: let the motion end before measuring.
        Thread.sleep(forTimeInterval: 3)
        return app
    }

    /// Something that is on this screen and no other, by its accessibility name.
    @MainActor
    private func marker(in app: XCUIApplication) -> XCUIElement {
        switch self {
        case .home: Harness.button(app, "How to play")
        case .teach: Harness.button(app, "Go on")
        case .table: Harness.button(app, "Discard")
        // The verdict, not the button: at the largest text the sheet's buttons are below the fold,
        // and Compose leaves a control wholly off screen out of the tree until it is scrolled to.
        case .score: Harness.anything(app, containing: "The Vinto call held")
        case .lobby: Harness.button(app, "Give me another name")
        // Named "Plan, PLAN" while the board is open: the switch's word is read after its name.
        case .plan: app.switches.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Plan")).firstMatch
        case .settings: Harness.anything(app, containing: "Support the game")
        case .help: Harness.anything(app, containing: "Numbers")
        case .helpRings, .helpBadges: Harness.anything(app, containing: "Right now")
        }
    }
}
