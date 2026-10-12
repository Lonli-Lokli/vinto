import ComposeApp
import SwiftUI

/// Hosts the Compose UI. `MainViewControllerKt.MainViewController()` is the Kotlin function
/// in `composeApp/src/iosMain/.../MainViewController.kt`, exported through the `ComposeApp`
/// framework — renaming either side breaks this.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context _: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_: UIViewController, context _: Context) {
        // Nothing to do: the Compose side owns its own state and redraws itself. SwiftUI has no
        // update to hand it, and the protocol requires the method whether or not there is work.
    }
}

/// The iPhone Duo's hinge, handed to the Compose table as it moves (`HingeBridge.kt`), so the
/// table spreads across the opening phone with the hand that opens it, and the felt's lamp opens
/// with it, as the wallpaper does. iOS 27.1's `onHingeChange`; on anything older, or a phone with
/// no hinge, nothing is reported and the table glides on its own clock.
///
/// Building this needs the iOS 27.1 SDK — Xcode 27.1 or later (VERSIONING.md).
struct FollowsTheHinge: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 27.1, *) {
            content.onHingeChange { _, context in
                if let hinge = context.hinge {
                    HingeBridgeKt.hingeMoved(
                        partiallyOpen: hinge.status == .partiallyOpen,
                        degrees: hinge.angle.degrees
                    )
                } else {
                    HingeBridgeKt.hingeGone()
                }
            }
        } else {
            content
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Both halves, and both deliberate. Compose is handed the WHOLE screen and insets
            // it itself, once, via `WindowInsets.safeDrawing` in `App.kt` — respecting the
            // container safe area here as well applied it twice and cost about 61pt above
            // every screen. It is also what lets the rail be painted behind the bars, so they
            // read as the edge of the table rather than a border around it. The keyboard is
            // ignored for the older reason: Compose has its own handler for it.
            .ignoresSafeArea(.container)
            .ignoresSafeArea(.keyboard)
            .modifier(FollowsTheHinge())
    }
}
