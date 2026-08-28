package game.vinto.app

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Entry point the Swift side embeds. Exported through the `ComposeApp` framework, so the
 * name is part of the contract with `iosApp/iosApp/ContentView.swift`.
 */
// PascalCase deliberately: Swift calls this as `MainViewControllerKt.MainViewController()`,
// so the name is part of the contract with `iosApp/iosApp/ContentView.swift`.
@Suppress("FunctionNaming")
fun MainViewController() = ComposeUIViewController { App() }
