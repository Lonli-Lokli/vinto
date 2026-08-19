package game.vinto.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import game.vinto.app.game.GameScreen
import game.vinto.app.theme.VintoTheme
import game.vinto.shapes.Difficulty

/**
 * The one UI, shared by Android, iOS and the browser. Each platform contributes only an
 * entry point (`MainActivity`, `MainViewController`, `main`) that hosts this composable.
 *
 * Navigation is two screens and a boolean. A library would buy back-stack handling and deep
 * links, neither of which a card game with a home screen and a table has any use for; when
 * rooms arrive and a link can point at one, that is the moment to take on a navigator.
 */
@Composable
fun App(seeds: () -> Long = ::freshSeed) {
    // Saveable, not merely remembered. Android destroys and recreates an activity for a
    // rotation, a font-size change, or simply because the system wanted the memory — and a
    // plain `remember` loses the seed, which drops the player back to the home screen with
    // their game gone. The seed is the whole game: keep it and the round comes back.
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MODERATE) }
    var seed: Long? by rememberSaveable { mutableStateOf(null) }

    VintoTheme {
        // Every phone has something drawn over its edges — a status bar, a gesture handle, a
        // camera cut-out. The table is a fixed arrangement of cards rather than a scrolling
        // list, so anything under those is simply lost rather than reachable.
        Surface(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (val started = seed) {
                null -> HomeScreen(
                    difficulty = difficulty,
                    onDifficulty = { difficulty = it },
                    onPlay = { seed = seeds() },
                )

                else -> GameScreen(
                    seed = started,
                    difficulty = difficulty,
                    onQuit = { seed = null },
                    onPlayAgain = { seed = seeds() },
                )
            }
            }
        }
    }
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
