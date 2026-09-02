package game.vinto.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import game.vinto.app.crash.Crashes
import game.vinto.app.crash.appReportingScope

/**
 * The desktop app.
 *
 * It exists for the developer rather than for a player: there is no desktop release and none
 * is planned. What it is for is the loop — `./gradlew :composeApp:run` puts the real table on
 * screen in seconds, with no emulator to boot, no device to plug in and no APK to install,
 * which is the difference between trying a spacing change and deciding not to bother.
 *
 * It is also the only way to check the things the JVM test suites cannot: that the four
 * sounds actually play and land where they should (`docs/kotlin/ROOM.md` §6i step 1 asks
 * for exactly this, and until this file existed there was nothing to run).
 *
 * The same `App()` as every other target — a desktop entry point that assembled its own
 * screen would be testing a fourth app rather than this one.
 */
fun main() {
    Crashes.install(appReportingScope())
    desktop()
}

private fun desktop() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Vinto",
        // A phone's proportions, because that is what the layout is designed against and a
        // maximised desktop window would show the tablet arrangement instead — useful, but
        // not the thing you are usually checking. Resize it to see the other one; the felt
        // is capped and centred above a certain width by design (UI.md §6f).
        state = rememberWindowState(size = DpSize(430.dp, 860.dp)),
    ) {
        App()
    }
}
