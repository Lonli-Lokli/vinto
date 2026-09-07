import ComposeApp
import SwiftUI

/// Hosts the Compose UI. `MainViewControllerKt.MainViewController()` is the Kotlin function
/// in `composeApp/src/iosMain/.../MainViewController.kt`, exported through the `ComposeApp`
/// framework — renaming either side breaks this.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
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
    }
}
