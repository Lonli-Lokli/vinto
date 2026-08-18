package game.vinto.app

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Entry point the Swift side embeds. Exported through the `ComposeApp` framework, so the
 * name is part of the contract with `iosApp/iosApp/ContentView.swift`.
 */
fun MainViewController() = ComposeUIViewController { App() }
