package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import game.vinto.app.game.GameScreen
import game.vinto.app.theme.RailFill
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGame
import game.vinto.client.Vault
import game.vinto.client.loadGame
import game.vinto.shapes.Difficulty
import kotlinx.coroutines.Dispatchers

/**
 * The one UI, shared by Android, iOS and the browser. Each platform contributes only an
 * entry point (`MainActivity`, `MainViewController`, `main`) that hosts this composable.
 *
 * Three screens and a nullable game. A navigation library would buy a back stack and deep
 * links, neither of which a card game with a home screen and a table has any use for; when
 * rooms arrive and a link can point at one, that is the moment to take on a navigator.
 */
@Composable
fun App(seeds: () -> Long = ::freshSeed, vault: Vault = remember { platformVault() }) {
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MODERATE) }
    var screen by remember { mutableStateOf<Screen>(Screen.Opening) }

    // Reading the saved game is the only thing between launching and playing, and on a cold
    // start it lands in the same frame. The opening screen exists for the case where it does
    // not — and, later, for a room that has to be reached over a network before anything can
    // be drawn.
    LaunchedEffect(Unit) {
        screen = Screen.Home(canContinue = vault.loadGame() != null)
    }

    VintoTheme {
        // Every phone has something drawn over its edges — a status bar, a gesture handle, a
        // camera cut-out. The table is a fixed arrangement of cards rather than a scrolling
        // list, so anything under those is simply lost rather than reachable, and the content
        // is inset out of their way. What is *behind* them is the rail rather than a page
        // colour, so the bars read as the edge of the table instead of a border around it.
        Surface(modifier = Modifier.fillMaxSize(), color = RailFill) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (val here = screen) {
                    Screen.Opening -> OpeningScreen()

                    is Screen.Home -> HomeScreen(
                        difficulty = difficulty,
                        canContinue = here.canContinue,
                        onDifficulty = { difficulty = it },
                        onContinue = {
                            LocalGame.resume(vault, Dispatchers.Default)?.let {
                                screen = Screen.Playing(it)
                            }
                        },
                        onPlay = {
                            screen = Screen.Playing(
                                LocalGame.start(vault, seeds(), difficulty, Dispatchers.Default),
                            )
                        },
                    )

                    is Screen.Playing -> GameScreen(
                        game = here.game,
                        onQuit = { screen = Screen.Home(canContinue = true) },
                    )
                }
            }
        }
    }
}

/** Where the app is. */
private sealed interface Screen {
    /** Finding out whether there is a game to come back to. */
    data object Opening : Screen

    data class Home(val canContinue: Boolean) : Screen

    data class Playing(val game: LocalGame) : Screen
}

/**
 * A seed for a new game.
 *
 * Picking one is ambient randomness, which is why the engine refuses to do it and this is
 * the outermost place that can. Injectable so a test or a bug report can pin a deal.
 */
expect fun freshSeed(): Long

/**
 * Names the host platform. Exists so each target is forced to supply an `actual` — the
 * cheapest possible proof that the expect/actual wiring is sound on every target before
 * anything real depends on it (storage and clocks will, per design D1).
 */
expect fun platformName(): String
