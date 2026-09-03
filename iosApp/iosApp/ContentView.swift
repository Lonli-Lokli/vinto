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
            .ignoresSafeArea(.keyboard)
    }
}
