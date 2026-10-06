import XCTest

/// What every UI test here launches the app with, and where it leaves its evidence.
///
/// The app is driven through its own handle and the system's settings, nothing else:
/// `-vinto.capture <state>` (the store-capture handle, `MarketingScene.kt`) puts it in a
/// deterministic state, the appearance is the simulator's own (`XCUIDevice.appearance`), and the
/// text size is the launch-argument override of the system's Dynamic Type setting. Nothing reaches
/// into the app's internals, so what these tests see is the tree VoiceOver and Voice Control read.
enum Appearance: String, CaseIterable, CustomStringConvertible {
    case light, dark

    var description: String { rawValue }

    var device: XCUIDevice.Appearance { self == .dark ? .dark : .light }
}

/// A Dynamic Type setting, by the name iOS gives it in the launch-argument domain.
///
/// The app turns the category into a font scale itself on iOS (`DynamicType.kt`): Apple's body-text
/// table, capped at AX3's 2.35x, in place of Compose's own linear scale that stopped at 1.8x.
/// `LargerTextTests` measures which one the build draws.
enum TextSize: String, CustomStringConvertible {
    /// The iOS default, "Large".
    case standard = "UICTContentSizeCategoryL"
    case accessibilityL = "UICTContentSizeCategoryAccessibilityL"
    /// The largest Dynamic Type setting there is, and the largest text the app draws.
    case accessibilityXXXL = "UICTContentSizeCategoryAccessibilityXXXL"

    var description: String {
        switch self {
        case .standard: "default"
        case .accessibilityL: "AX-L"
        case .accessibilityXXXL: "AX-XXXL"
        }
    }
}

@MainActor
enum Harness {
    /// Launch arguments for one case. `state` is a `MarketingScene` id, or nil for a real launch.
    static func arguments(state: String?, size: TextSize, settings: String? = nil) -> [String] {
        var args: [String] = []
        if let state { args += ["-vinto.capture", state] }
        args += ["-UIPreferredContentSizeCategoryName", size.rawValue]
        if let settings {
            // The vault is NSUserDefaults, and the argument domain outranks what the app saved, so
            // this is the settings file for this launch only. Quoted as a plist string so the JSON
            // reaches `stringForKey` as written instead of being parsed as a property list.
            let quoted = settings.replacingOccurrences(of: "\"", with: "\\\"")
            args += ["-vinto.settings", "\"\(quoted)\""]
        }
        return args
    }

    /// Launches the app in `appearance`. The appearance is the SIMULATOR's: `-AppleInterfaceStyle`
    /// was tried first and Compose ignores it, which left every "light" case rendering dark.
    static func launch(
        state: String?,
        appearance: Appearance,
        size: TextSize,
        settings: String? = nil
    ) -> XCUIApplication {
        XCUIDevice.shared.appearance = appearance.device
        let app = XCUIApplication()
        app.launchArguments = arguments(state: state, size: size, settings: settings)
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15), "the app did not come up")
        return app
    }

    /// Where screenshots and tree dumps go: `A11Y_OUT` (pass `TEST_RUNNER_A11Y_OUT=<dir>` to
    /// xcodebuild), or a folder in the simulator's temporary directory. Every screenshot is also
    /// attached to the result bundle, so nothing depends on the folder being reachable.
    static var outDir: URL {
        let env = ProcessInfo.processInfo.environment["A11Y_OUT"]
        let base = env.map { URL(fileURLWithPath: $0) }
            ?? URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("vinto-a11y")
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    /// Saves a screenshot and the accessibility tree under `name`, on disk and in the result bundle.
    static func record(_ app: XCUIApplication, name: String, into test: XCTestCase) {
        let shot = app.screenshot()
        try? shot.pngRepresentation.write(to: outDir.appendingPathComponent("\(name).png"))
        let image = XCTAttachment(screenshot: shot)
        image.name = name
        image.lifetime = .keepAlways
        test.add(image)
        write(app.debugDescription, name: "\(name).tree.txt")
    }

    static func write(_ text: String, name: String) {
        try? text.write(to: outDir.appendingPathComponent(name), atomically: true, encoding: .utf8)
    }

    /// A button by the name a person would say: its accessibility label, without case, so a label
    /// drawn in capitals ("SWAP CARDS") and spoken in sentence case ("Swap Cards") agree.
    static func button(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label ==[c] %@", label)).firstMatch
    }

    /// A switch by its name.
    static func toggle(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.switches.matching(NSPredicate(format: "label ==[c] %@", label)).firstMatch
    }

    /// Any element whose label contains `text`, for reading what the screen says.
    static func anything(_ app: XCUIApplication, containing text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", text)).firstMatch
    }

    /// Every element in one round trip: type, label, enabled, frame. Reading elements one query at a
    /// time costs a tree walk each, and a Compose screen is a few hundred of them.
    static func snapshot(_ app: XCUIApplication) -> [Node] {
        guard let root = try? app.snapshot() else { return [] }
        var nodes: [Node] = []
        var stack = [root]
        while let node = stack.popLast() {
            nodes.append(Node(
                type: node.elementType, label: node.label, enabled: node.isEnabled, frame: node.frame,
                value: node.value as? String
            ))
            stack += node.children
        }
        return nodes
    }

    /// Opens a screen the way a person does: by pressing the button that names it.
    static func press(_ app: XCUIApplication, _ label: String, timeout: TimeInterval = 15) {
        let button = button(app, label)
        XCTAssertTrue(button.waitForExistence(timeout: timeout), "no button named \"\(label)\"")
        button.tap()
    }
}

/// One element of a snapshot, kept as plain values.
struct Node {
    let type: XCUIElement.ElementType
    let label: String
    let enabled: Bool
    let frame: CGRect
    let value: String?
}

/// Restores the simulator's appearance after a test changed it: it is shared with other agents and
/// other apps' runs, and it was dark when this suite first ran.
@MainActor
class RestoresAppearance: XCTestCase {
    private var original: XCUIDevice.Appearance = .unspecified

    /// How long one test may run before XCTest stops it (the runs pass
    /// `-test-timeouts-enabled YES -default-test-execution-time-allowance 180`). A class whose tests
    /// launch the app a dozen times says so here, and no test may take more than the 600 s maximum.
    class var allowance: TimeInterval { 180 }

    override func setUp() async throws {
        try await super.setUp()
        executionTimeAllowance = type(of: self).allowance
        original = XCUIDevice.shared.appearance
        continueAfterFailure = true
    }

    override func tearDown() async throws {
        if original != .unspecified { XCUIDevice.shared.appearance = original }
        try await super.tearDown()
    }
}
