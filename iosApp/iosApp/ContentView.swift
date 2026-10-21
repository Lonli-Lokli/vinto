import ComposeApp
import SwiftUI

/// Hosts the Compose UI. `MainViewControllerKt.MainViewController()` is the Kotlin function
/// in `composeApp/src/iosMain/.../MainViewController.kt`, exported through the `ComposeApp`
/// framework — renaming either side breaks this.
///
/// The Capture configuration (the capture build; CLAUDE.md, "The capture build") opens
/// `CaptureViewController` instead, from `composeApp/src/captureIos`: the app on the store scene its
/// launch argument names. No other configuration's framework has that function, nor this branch.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context _: Context) -> UIViewController {
        #if CAPTURE
        let root: UIViewController = CaptureViewControllerKt.CaptureViewController()
        #else
        let root: UIViewController = MainViewControllerKt.MainViewController()
        #endif
        return root
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
                    // A phone with a hinge may turn, whatever its model (`Orientations`).
                    Orientations.hingeReported()
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

/// What iOS reserves in the window right now, as the text `ReservedBridgeKt.reservedChanged`
/// reads (`Reserved.parse` in Kotlin): each region `left,top,width,height`, `;` between them.
@available(iOS 27.1, *)
struct RegionsNow: Equatable {
    let occlusions: String
    let division: String

    init(_ proxy: GeometryProxy) {
        occlusions = Self.encoded(proxy.reservedRegions(kind: .occlusion))
        division = Self.encoded(proxy.reservedRegions(kind: .division))
    }

    private static func encoded(_ regions: [ReservedRegion]) -> String {
        regions.filter(\.isActive)
            .map { region in
                let frame: CGRect = region.frame
                return "\(frame.minX),\(frame.minY),\(frame.width),\(frame.height)"
            }
            .joined(separator: ";")
    }
}

/// The iPhone Duo's reserved regions, handed to the Compose table (`ReservedBridge.kt`): where the
/// clock and the camera stand in its status column, so the table's controls start under them and
/// stop short of them, and where the fold runs while it is half open, so no card lies on it. An
/// inset says only how wide the column is. Read through a geometry reader over the whole window,
/// so the regions arrive in the window's coordinates, which are Compose's; nothing on older iOS.
struct ReportsReservedRegions: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 27.1, *) {
            GeometryReader { proxy in
                content.onChange(of: RegionsNow(proxy), initial: true) { _, now in
                    ReservedBridgeKt.reservedChanged(occlusions: now.occlusions, division: now.division)
                }
            }
            .ignoresSafeArea()
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
            .modifier(ReportsReservedRegions())
    }
}
