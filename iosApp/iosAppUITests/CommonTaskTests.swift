import XCTest

/// The common task, done the way VoiceOver and Voice Control do it: start a game against the bots,
/// take turns (draw; swap or discard; use a card's power; call Vinto) and reach the round's score
/// sheet. Then the help sheet, the settings and the online lobby.
///
/// Every element is found by its accessibility label and activated through XCUI, which presses the
/// element the accessibility tree reports. There is no coordinate tap anywhere in this file: if a
/// step can only be reached by touching an unlabelled patch of screen, that is the finding, and the
/// test fails saying which step.
///
/// The deal is a fresh one each run (Home's "New game" seeds from the clock, as it does for a
/// player), so the turn loop reads the table's question and the buttons on offer and answers them,
/// as a person would, rather than replaying a script. It plays until it has swapped a card, used a
/// card's power and called Vinto, and deals another round if a bot calls first. It gives up after
/// `maxSteps`, so a table that stops offering anything fails instead of hanging.
///
/// It also checks the one thing a memory game cannot do without: a card the player turns over says
/// what it is. The setup peeks are read back from the tree.
final class CommonTaskTests: RestoresAppearance {
    /// A whole round against brisk bots is minutes of play; the walks through help and settings are not.
    override class var allowance: TimeInterval { 600 }

    /// The bots at their brisk pace, so a round takes a minute rather than five. Pace changes only
    /// how long a bot waits between moves, never what the screen offers.
    private static let settings = #"{"version":1,"pace":"brisk","difficulty":"easy"}"#

    private static let maxSteps = 500

    func testARoundAgainstTheBotsThroughTheAccessibilityTree() {
        playARound(size: .standard, name: "common-task")
    }

    /// The same round with the largest Dynamic Type setting: every control the task needs has to be
    /// there and pressable when the words are at their biggest.
    func testARoundAgainstTheBotsAtTheLargestTextSize() {
        playARound(size: .accessibilityXXXL, name: "common-task-ax")
    }

    // MARK: The round

    private func playARound(size: TextSize, name: String) {
        let app = Harness.launch(state: nil, appearance: .light, size: size, settings: Self.settings)
        var log: [String] = []
        defer { Harness.write(log.joined(separator: "\n"), name: "\(name).log") }

        let newGame = Harness.button(app, "New game")
        let play = Harness.button(app, "Play")
        XCTAssertTrue(
            newGame.waitForExistence(timeout: 15) || play.exists,
            "Home offers no way to start a game"
        )
        XCTAssertTrue(
            Harness.anything(app, containing: "Easy").exists,
            "the settings launch argument did not reach the app: the difficulty is not Easy"
        )
        (newGame.exists ? newGame : play).tap()
        log.append("home: started a game")

        var progress = Progress()
        for step in 0..<Self.maxSteps where !progress.done {
            take(step, app, &progress, &log, name: name)
        }
        Harness.record(app, name: "\(name)-end", into: self)

        XCTAssertTrue(progress.peekedFace, "a card turned over in the setup peek never said what it is")
        XCTAssertTrue(progress.swapped, "never swapped a card")
        XCTAssertTrue(progress.usedPower, "never used a card's power")
        XCTAssertTrue(progress.called, "never called Vinto")
        XCTAssertTrue(progress.reachedScore, "never reached the score sheet")
    }

    /// What the round has shown so far.
    private struct Progress {
        var peekedFace = false
        var swapped = false
        var usedPower = false
        var called = false
        var reachedScore = false
        var rounds = 1
        var recorded: Set<String> = []
        /// Set when a step cannot be done at all, so the loop ends on the failure it has reported.
        var gaveUp = false
        var done: Bool { reachedScore && called || gaveUp }
    }

    /// How the score sheet opens, whichever way the round went (`score_caller_won` and its siblings).
    private static let verdicts = [
        "The Vinto call held", "Level. The call held", "The others beat the call", "Nobody called"
    ]

    /// The questions that are answered by touching a card, as the table asks them.
    private static let touchACard = [
        "Look at two of your cards", "One more card to look at", "Which card does it replace?",
        "Look at one of your own cards", "Look at one card of another player", "Choose any card",
        "Choose two cards, from two different players", "Look at two cards, from two different players",
        "Who draws a card?"
    ]

    // One branch per question the table can ask; splitting it would scatter the round's rules.
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func take(_ step: Int, _ app: XCUIApplication, _ progress: inout Progress, _ log: inout [String], name: String) {
        let nodes = Harness.snapshot(app)
        let buttons = nodes.filter { $0.type == .button && $0.enabled && !$0.label.isEmpty }.map(\.label)
        let labels = nodes.map(\.label)
        let has = { (label: String) in buttons.contains { $0.caseInsensitiveCompare(label) == .orderedSame } }
        let asked = { (prompt: String) in labels.contains { $0.localizedCaseInsensitiveContains(prompt) } }
        let firstTime = { (what: String, progress: inout Progress) -> Bool in progress.recorded.insert(what).inserted }

        // The setup peeks: once a card of ours is turned over, its label must carry its face.
        let ownFaces = nodes.filter { $0.label.hasPrefix("You, card ") && $0.label.contains("worth") }
        if !ownFaces.isEmpty, !progress.peekedFace {
            progress.peekedFace = true
            log.append("\(step): a turned card says \(ownFaces.map(\.label))")
            Harness.record(app, name: "\(name)-peek", into: self)
        }

        // The score sheet, known by its verdict as well as by its button: the sheet scrolls, and
        // at the largest text its buttons are below the fold, where Compose leaves an element out of
        // the tree until it is scrolled to. So the verdict says the sheet is up, and the button is
        // then scrolled to as a person would scroll to it ("scroll down" to Voice Control, a
        // three-finger swipe to VoiceOver), and pressed by name.
        let onTheSheet = Self.verdicts.contains { verdict in labels.contains { $0 == verdict } }
        if onTheSheet, !has("Deal the next round") {
            let deal = Harness.button(app, "Deal the next round")
            var swipes = 0
            while !(deal.exists && deal.isHittable), swipes < 4 {
                app.swipeUp()
                swipes += 1
            }
            log.append("\(step): the score sheet is up; its buttons took \(swipes) scroll(s) to reach")
            if !(deal.exists && deal.isHittable) {
                Harness.record(app, name: "\(name)-score-unreachable", into: self)
                XCTFail("the score sheet is up, and Deal the next round cannot be reached even by scrolling")
                progress.gaveUp = true
            }
            return
        }
        if has("Deal the next round") {
            progress.reachedScore = true
            log.append("\(step): the score sheet is up (round \(progress.rounds))")
            if firstTime("score", &progress) { Harness.record(app, name: "\(name)-score", into: self) }
            // A bot called before we could: play the next round rather than stop short.
            if !progress.called, progress.rounds < 3 {
                press(app, "Deal the next round", &log, step)
                progress.reachedScore = false
                progress.rounds += 1
            }
            return
        }
        if has("See the score") { return press(app, "See the score", &log, step) }
        if has("Start the round") { return press(app, "Start the round", &log, step) }

        // A power: the drawn card's, or one waiting on the discard pile.
        if !progress.usedPower, has("Use Action") {
            progress.usedPower = true
            if firstTime("power", &progress) { Harness.record(app, name: "\(name)-power", into: self) }
            return press(app, "Use Action", &log, step)
        }
        if !progress.usedPower, let pile = buttons.first(where: { $0.hasPrefix("Use ") }) {
            progress.usedPower = true
            return press(app, pile, &log, step)
        }
        if has("Done") { return press(app, "Done", &log, step) }
        if has("Leave them") { return press(app, "Leave them", &log, step) }
        if has("Just Swap") {
            progress.swapped = true
            return press(app, "Just Swap", &log, step)
        }
        // A King's rank list: putting the card down is always on offer, and always legal.
        if asked("Say what it is"), has("Put it down") { return press(app, "Put it down", &log, step) }

        // The drawn card: kept once (a swap), discarded after that.
        if has("Swap Cards"), has("Discard") {
            return press(app, progress.swapped ? "Discard" : "Swap Cards", &log, step)
        }

        // A question answered by touching a card. Only these: in a toss-in window our cards are
        // buttons too, and touching one there is a throw that costs a penalty if it is wrong.
        if Self.touchACard.contains(where: asked) {
            let cards = buttons.filter { $0.contains(", card ") }
            let own = cards.filter { $0.hasPrefix("You, card ") && !$0.contains("worth") }
            if let card = own.first ?? cards.first {
                if firstTime("aim", &progress) { Harness.record(app, name: "\(name)-aim", into: self) }
                return press(app, card, &log, step)
            }
        }

        // The end of our own turn: call once the swap and the power are behind us.
        if has("Call Vinto"), progress.swapped, progress.usedPower, !progress.called {
            progress.called = true
            Harness.record(app, name: "\(name)-call", into: self)
            return press(app, "Call Vinto", &log, step)
        }
        if has("Continue") { return press(app, "Continue", &log, step) }
        // A bot called first: the coalition confers before its last turns.
        if has("Share what I know") { return press(app, "Share what I know", &log, step) }
        if has("That’s all I know") { return press(app, "That’s all I know", &log, step) }
        if has("Draw Card") { return press(app, "Draw Card", &log, step) }

        // Nothing for us to do: the bots are playing.
        Thread.sleep(forTimeInterval: 0.5)
    }

    private func press(_ app: XCUIApplication, _ label: String, _ log: inout [String], _ step: Int) {
        let button = Harness.button(app, label)
        guard button.waitForExistence(timeout: 5) else {
            log.append("\(step): \(label) went away before it could be pressed")
            return
        }
        guard button.isHittable else {
            XCTFail("\"\(label)\" is in the tree but cannot be pressed (off screen or covered)")
            log.append("\(step): \(label) is not hittable at \(button.frame)")
            return
        }
        button.tap()
        log.append("\(step): pressed \(label)")
    }

    // MARK: Help, settings, the lobby

    /// The help sheet opens from the table by its name, every tab answers to its name, and the
    /// sheet closes by the name of its own button.
    func testTheHelpSheetOpensAndClosesByName() {
        let app = Harness.launch(state: "table", appearance: .light, size: .standard)
        Harness.press(app, "Rules")
        let cards = Harness.anything(app, containing: "Numbers")
        XCTAssertTrue(cards.waitForExistence(timeout: 10), "the help sheet did not open on its first tab")
        for tab in ["Rings", "Badges", "More", "Cards"] {
            Harness.press(app, tab, timeout: 5)
            XCTAssertTrue(Harness.button(app, tab).isSelected, "the \(tab) tab does not say it is the one open")
        }
        Harness.record(app, name: "task-help", into: self)
        // Two ways out, both called Close: the sheet's own ✕ and the dark behind it. XCUI presses
        // the middle of an element, which for the full-screen dark is the sheet lying over it, so
        // the ✕ is the one pressed here. VoiceOver activates either through its action instead.
        let closes = app.buttons.matching(NSPredicate(format: "label ==[c] %@", "Close")).allElementsBoundByIndex
        let cross = closes.min { $0.frame.width * $0.frame.height < $1.frame.width * $1.frame.height }
        XCTAssertNotNil(cross, "the help sheet has no control named Close")
        cross?.tap()
        XCTAssertTrue(
            Harness.button(app, "Discard").waitForExistence(timeout: 10),
            "closing help did not return to the table"
        )
    }

    /// The settings open from Home, every switch is a switch named for what it switches and says
    /// where it stands, the Game page opens by name, and Back goes back each time.
    func testTheSettingsAreReachableAndTheirSwitchesSayWhatTheySwitch() {
        let app = Harness.launch(state: "home", appearance: .light, size: .standard)
        Harness.press(app, "Settings")
        for name in ["Sound", "Haptics", "Keep the screen on"] {
            XCTAssertTrue(Harness.toggle(app, name).waitForExistence(timeout: 10), "no switch named \(name)")
        }
        let sound = Harness.toggle(app, "Sound")
        let before = (sound.isSelected, sound.value as? String)
        sound.tap()
        let after = (sound.isSelected, sound.value as? String)
        Harness.write("Sound before: \(before), after: \(after)\n", name: "task-settings-switch.txt")
        XCTAssertNotEqual(before.0, after.0, "the Sound switch did not report its new state")
        sound.tap()
        Harness.press(app, "Game")
        XCTAssertTrue(Harness.anything(app, containing: "More about Pace").waitForExistence(timeout: 10), "Game did not open")
        Harness.record(app, name: "task-settings-game", into: self)
        Harness.press(app, "Back")
        XCTAssertTrue(Harness.toggle(app, "Sound").waitForExistence(timeout: 10), "Back did not return to the settings")
        Harness.record(app, name: "task-settings-back", into: self)
        Harness.press(app, "Back")
        XCTAssertTrue(Harness.button(app, "How to play").waitForExistence(timeout: 10), "Back did not return home")
    }

    /// The online lobby: the face opens its picker and closes it again, the name re-rolls, and the
    /// doors lead somewhere and back, all by name. No room is opened: that is the network's.
    func testTheOnlineLobbyIsReachableByName() {
        let app = Harness.launch(state: "home", appearance: .light, size: .standard)
        Harness.press(app, "Play online")
        Harness.press(app, "Choose a different face")
        Harness.record(app, name: "task-lobby-faces", into: self)
        // Known and reported, not fixed here: the faces and colours in the picker are buttons with
        // no names. Naming them needs new words in all twenty-one languages (IdentityControl.kt).
        let unnamed = app.buttons.allElementsBoundByIndex.filter { $0.label.isEmpty && $0.isEnabled }
        XCTExpectFailure("the face picker's faces and colours have no names (IdentityControl.kt, AvatarPicker)") {
            XCTAssertTrue(unnamed.isEmpty, "\(unnamed.count) unnamed buttons in the face picker")
        }
        Harness.press(app, "Choose a different face")
        Harness.press(app, "Give me another name")
        let door = app.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Join with a code")).firstMatch
        XCTAssertTrue(door.waitForExistence(timeout: 10), "no button named Join with a code")
        door.tap()
        Harness.record(app, name: "task-lobby-join", into: self)
        Harness.press(app, "Back")
        XCTAssertTrue(Harness.button(app, "Give me another name").waitForExistence(timeout: 10), "Back did not return to the lobby")
        Harness.press(app, "Back")
        XCTAssertTrue(Harness.button(app, "How to play").waitForExistence(timeout: 10), "Back did not return home")
    }

    /// At the largest text the settings run past the screen, and Compose leaves a control that is
    /// wholly off screen out of the accessibility tree until it is scrolled to. About is one: it
    /// is reached here as a Voice Control user reaches it, by scrolling ("scroll down") and then
    /// saying its name. VoiceOver's own three-finger scroll is not something XCUITest can make.
    func testAtTheLargestTextTheLastSettingIsReachedByScrolling() {
        let app = Harness.launch(state: "home", appearance: .light, size: .accessibilityXXXL)
        Harness.press(app, "Settings")
        let about = Harness.button(app, "About")
        var swipes = 0
        while !(about.exists && about.isHittable), swipes < 4 {
            app.swipeUp()
            swipes += 1
        }
        Harness.write("About reached after \(swipes) scroll(s)\n", name: "task-settings-ax-scroll.txt")
        Harness.record(app, name: "task-settings-ax-scrolled", into: self)
        XCTAssertTrue(about.isHittable, "About could not be scrolled to at the largest text")
        about.tap()
        XCTAssertTrue(Harness.button(app, "Back").waitForExistence(timeout: 10), "About did not open")
    }
}
