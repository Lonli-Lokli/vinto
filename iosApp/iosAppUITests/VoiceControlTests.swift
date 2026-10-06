import XCTest

/// Voice Control: every control a person can operate has a name they can say.
///
/// A Voice Control user says "tap Settings", and iOS matches the words to the control's
/// accessibility label. So a control with no label cannot be reached by name at all, a label with a
/// glyph in it ("Rules, ?") is not something anybody can say, the same name twice ("Plan, PLAN") is
/// not a name, and two different controls with one name ("On", three times on the settings screen)
/// leave the user asking for numbers. This walks every main screen and checks each enabled button,
/// switch, link and field for all of it, and writes the whole inventory to `A11Y_OUT`.
///
/// Separate from the audit on purpose: `performAccessibilityAudit` reports a missing description
/// only where its own heuristics expect one, and never checks that a name can be spoken.
final class VoiceControlTests: RestoresAppearance {
    /// Ten screens, one launch each, the coalition scene among them.
    override class var allowance: TimeInterval { 480 }

    /// Found by this test, real, and outside what this suite changes: each reports as an expected
    /// failure naming where it lives, and fails the test the day it stops reproducing.
    private static let known: [(screen: Screen, label: String, reason: String)] = [
        (.teach, "Four players, five cards each, Part 1 of 14",
         "the lesson's coach panel is one button named by its title and all fourteen chapter dots "
             + "(TeachScreen.kt); too long to say"),
        (.plan, "Plan, PLAN",
         "the plan switch is named and then reads its own word (TableScreen.kt PlanSwitch; the "
             + "coalition tests find it by that word)"),
        (.plan, "+ and then…, + and then…",
         "a plan word is named and then reads its own text (PlanFelt.kt Word)"),
        (.plan, "+ throw in…, + throw in…",
         "a plan word is named and then reads its own text (PlanFelt.kt Word)")
    ]

    func testEveryControlOnEveryMainScreenHasASayableName() {
        var problems: [String] = []
        var inventory: [String] = []
        var knownSeen: Set<String> = []
        for screen in Screen.allCases {
            let app = screen.open(appearance: .light, size: .standard)
            let controls = Harness.snapshot(app).filter { node in
                [.button, .switch, .link, .textField, .secureTextField].contains(node.type) && node.enabled
                    && !node.frame.isEmpty
            }
            inventory.append("## \(screen.rawValue): \(controls.count) controls")
            inventory += controls.map { "  \($0.type.rawValue) \"\($0.label)\"\($0.value.map { " = \($0)" } ?? "")" }
            for control in controls {
                guard let problem = Self.problem(with: control.label) else { continue }
                if let known = Self.known.first(where: { $0.screen == screen && control.label.hasPrefix($0.label) }) {
                    knownSeen.insert(known.label)
                    XCTExpectFailure("Known: \(known.reason)") {
                        XCTFail("\(screen.rawValue): \(problem): \"\(control.label)\"")
                    }
                    continue
                }
                problems.append("\(screen.rawValue): \(problem): \"\(control.label)\" at \(control.frame.integral)")
            }
            // One name, one control. The sheet's ✕ and the dark behind it are both "Close" and both
            // close the sheet, which is the one place two controls may share a name.
            let names = controls.map { $0.label.lowercased() }.filter { $0 != "close" }
            let repeated = Set(names.filter { name in names.filter { $0 == name }.count > 1 })
            if !repeated.isEmpty {
                problems.append("\(screen.rawValue): several controls answer to \(repeated.sorted())")
            }
            app.terminate()
        }
        Harness.write(inventory.joined(separator: "\n"), name: "voice-control-inventory.txt")
        for known in Self.known where !knownSeen.contains(known.label) {
            XCTFail("No longer reproduces, remove it from the known list: \(known.label)")
        }
        XCTAssertTrue(
            problems.isEmpty,
            "Controls Voice Control cannot address by name:\n" + problems.joined(separator: "\n")
        )
    }

    /// What is wrong with a label as a spoken name, or nil if nothing is.
    private static func problem(with label: String) -> String? {
        let name = label.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty { return "no name" }
        if name.rangeOfCharacter(from: .letters) == nil, name.rangeOfCharacter(from: .decimalDigits) == nil {
            return "nothing in the name can be said"
        }
        // iOS joins a control's merged parts with ", ". A part that is a lone glyph or letter is a
        // picture that leaked into the name: "Back, ‹", "More about Sound, i".
        let parts = name.components(separatedBy: ", ")
        if let glyph = parts.dropFirst().first(where: { part in
            // A rank is a letter too: a card turned over reads "Tide, card 2, K, worth 13".
            part.count == 1 && part.rangeOfCharacter(from: .decimalDigits) == nil && !"JQKA".contains(part)
        }) {
            return "a glyph in the name (\"\(glyph)\")"
        }
        if parts.contains(where: { $0.unicodeScalars.contains { $0.properties.isEmojiPresentation } }) {
            return "an emoji in the name"
        }
        // The name read again, as the words drawn on the control: "Plan, PLAN".
        if parts.count > 1, parts.dropFirst().contains(where: { $0.caseInsensitiveCompare(parts[0]) == .orderedSame }) {
            return "the name said twice"
        }
        if name.split(separator: " ").count > 16 { return "too long to say" }
        return nil
    }
}
