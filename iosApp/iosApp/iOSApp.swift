import ComposeApp
import SwiftUI

/// Which ways the window may turn: an iPad every way, a phone upright — except a phone that folds.
///
/// A phone on its side leaves the felt too short for four hands (Info.plist has the arithmetic).
/// The closed iPhone Duo turned keeps its whole height, because iOS stands its status bar in a
/// column down the side rather than across the top, and Apple's own apps turn with it: so the table
/// does (owner, 2026-10-13). Known by model at launch, and by its hinge as soon as iOS reports one,
/// so a foldable this list has not heard of turns too.
@MainActor
enum Orientations {
    /// The iPhone Duo.
    private static let foldingModels: Set<String> = ["iPhone19,4"]

    private(set) static var foldable: Bool = foldingModels.contains(model())

    static var allowed: UIInterfaceOrientationMask {
        if UIDevice.current.userInterfaceIdiom == .pad {
            return .all
        }
        return foldable ? .allButUpsideDown : .portrait
    }

    /// iOS reported a hinge: this phone folds, whatever its model, and may turn from now on.
    static func hingeReported() {
        guard !foldable else {
            return
        }
        foldable = true
        // Hinges exist only from iOS 27.1, so this always runs on a system that has the call; the
        // check is for the compiler, which builds the app for older ones too.
        guard #available(iOS 16.0, *) else {
            return
        }
        for case let scene as UIWindowScene in UIApplication.shared.connectedScenes {
            scene.windows.first?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        }
    }

    /// The model identifier: the simulated one in the simulator, the hardware's on a phone.
    private static func model() -> String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return simulated
        }
        var system: utsname = .init()
        uname(&system)
        let machine: [UInt8] = withUnsafeBytes(of: &system.machine) { bytes in
            Array(bytes.prefix { $0 != 0 })
        }
        return String(bytes: machine, encoding: .utf8) ?? ""
    }
}

/// Answers iOS's one question the app cannot answer from Info.plist alone: which ways it may turn.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _: UIApplication,
        supportedInterfaceOrientationsFor _: UIWindow?
    ) -> UIInterfaceOrientationMask {
        MainActor.assumeIsolated { Orientations.allowed }
    }

    deinit {
        // Nothing to release: the delegate lives as long as the app.
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
    private var appDelegate: AppDelegate

    init() {
        // First, before anything that can block: compile the Metal programs Compose's first frame needs on a
        // background thread, so a cold shader cache (every install, every iOS update) is not paid frozen on the
        // main thread. Niva (NIVA-6) and Vodar (1.0+108) both stalled 2 s there. FirstFrameWarmUp (composeApp,
        // a copy of games-core's) has the reasoning and its honest limits.
        MainViewControllerKt.WarmUpFirstFrame()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // An invitation, in both the forms iOS can deliver it.
                //
                // `onOpenURL` covers the custom scheme (vinto://7KQ2MP), which works with no
                // hosted file and no verification. `onContinueUserActivity` covers the
                // Universal Link (https://vinto.kupalinka.app/r/7KQ2MP), which is the better
                // one — it opens the app *and* falls back to the website for somebody who
                // does not have it — but only once
                // https://vinto.kupalinka.app/.well-known/apple-app-site-association names
                // this app's team id and bundle id. Until that file is published the https
                // half silently does nothing, which is why the scheme is here as well.
                //
                // Both do the same three lines of work: hand the URL to Kotlin, where what a
                // link *means* is decided and tested (composeApp .../link/Invite.kt). Nothing
                // about room codes is written twice.
                .onOpenURL { url in
                    MainViewControllerKt.HandleOpenedLink(url: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    MainViewControllerKt.HandleOpenedLink(url: activity.webpageURL?.absoluteString)
                }
        }
    }
}
