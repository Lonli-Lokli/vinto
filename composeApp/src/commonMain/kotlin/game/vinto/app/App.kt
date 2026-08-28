package game.vinto.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import game.vinto.app.game.GameScreen
import game.vinto.app.game.RoomScreen
import game.vinto.app.game.TeachScreen
import game.vinto.app.net.platformRoomConnector
import game.vinto.app.theme.LocalFeedback
import game.vinto.app.theme.LocalSounds
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.app.theme.rememberFeedback
import game.vinto.app.theme.rememberSounds
import game.vinto.client.LocalGame
import game.vinto.client.RemoteRoom
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.Vault
import game.vinto.client.forgetGame
import game.vinto.client.loadGame
import game.vinto.client.loadSettings
import game.vinto.client.saveSettings
import kotlinx.coroutines.Dispatchers

/**
 * The one UI, shared by Android, iOS and the browser. Each platform contributes only an
 * entry point (`MainActivity`, `MainViewController`, `main`) that hosts this composable.
 *
 * Seven destinations and a nullable game. A navigation library would buy a back stack and
 * deep links; a card game whose screens are "the menu", "a table" and a handful of things
 * reached from the menu has no use for either, and every one of these has exactly one way
 * back. A link that points at a room (`?room=CODE` on the web) is the one thing that would
 * justify a navigator; it can land in `Screen.Online` pre-filled when somebody asks for it.
 */
@Composable
fun App(seeds: () -> Long = ::freshSeed, vault: Vault = remember { platformVault() }) {
    var settings by remember { mutableStateOf(Settings()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Opening) }

    // One connector for the app's lifetime, and a scope for the rooms it opens: a room's
    // socket loop belongs to the app, not to whichever screen happens to be showing it.
    val connector = remember { platformRoomConnector(ROOM_SERVICE) }
    val appScope = rememberCoroutineScope()

    // Reading what is on disk is the only thing between launching and playing, and on a cold
    // start it lands in the same frame. The opening screen exists for the case where it does
    // not — and, later, for a room that has to be reached over a network before anything can
    // be drawn.
    LaunchedEffect(Unit) {
        settings = vault.loadSettings()
        screen = Screen.Home(canContinue = vault.loadGame() != null)
    }

    // Every change is written down as it is made. There is no "save" button in a settings
    // screen worth having, and four values are not worth batching.
    fun change(updated: Settings) {
        settings = updated
        vault.saveSettings(updated)
    }

    // Back goes home from anywhere that is not home, and closes the app from there — which is
    // what a phone's back button means everywhere else, and what its absence made look like a
    // crash the first time somebody pressed it in the settings.
    SystemBack(enabled = screen !is Screen.Home && screen !is Screen.Opening) {
        // Backing out of a room is leaving it: the socket loop must not outlive the screen.
        // The seat token stays vaulted, so the same back button is not a lost seat.
        (screen as? Screen.InRoom)?.room?.leave()
        screen = Screen.Home(canContinue = vault.loadGame() != null)
    }

    val dark = settings.theme.isDark()
    SystemBars(dark)
    VintoTheme(dark = dark) {
        CompositionLocalProvider(
            LocalFeedback provides rememberFeedback(settings.haptics),
            LocalReducedMotion provides settings.motion.reduced(systemPrefersReducedMotion()),
            LocalSounds provides rememberSounds(settings.sound),
        ) {
            // Every phone has something drawn over its edges — a status bar, a gesture handle, a
            // camera cut-out. The table is a fixed arrangement of cards rather than a scrolling
            // list, so anything under those is simply lost rather than reachable, and the content
            // is inset out of their way. What is *behind* them is the rail rather than a page
            // colour, so the bars read as the edge of the table instead of a border around it
            // — and since the rail now has a light half, `SystemBars` above turns the icons
            // in them the right way round to be seen against it.
            Surface(modifier = Modifier.fillMaxSize(), color = Rail.fill) {
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (val here = screen) {
                        Screen.Opening -> OpeningScreen()

                        is Screen.Home -> HomeScreen(
                            settings = settings,
                            canContinue = here.canContinue,
                            go = homeActions(vault, seeds, settings) { screen = it },
                        )

                        Screen.Settings -> SettingsScreen(
                            settings = settings,
                            canForget = vault.loadGame() != null,
                            onChange = ::change,
                            onForget = {
                                vault.forgetGame()
                                screen = Screen.Home(canContinue = false)
                            },
                            onBack = { screen = Screen.Home(canContinue = vault.loadGame() != null) },
                        )

                        Screen.Teaching -> TeachScreen(
                            botDispatcher = Dispatchers.Default,
                            pace = settings.pace,
                            onDone = { screen = Screen.Home(canContinue = vault.loadGame() != null) },
                        )

                        is Screen.Playing -> GameScreen(
                            game = here.game,
                            pace = settings.pace,
                            onQuit = { screen = Screen.Home(canContinue = true) },
                        )

                        Screen.Online -> OnlineScreen(
                            connector = connector,
                            vault = vault,
                            onEnterRoom = { code, nickname ->
                                screen = Screen.InRoom(
                                    RemoteRoom(
                                        connector = connector,
                                        code = code,
                                        vault = vault,
                                        nickname = nickname,
                                        scope = appScope,
                                    ),
                                )
                            },
                            onBack = {
                                screen = Screen.Home(canContinue = vault.loadGame() != null)
                            },
                        )

                        is Screen.InRoom -> RoomScreen(
                            room = here.room,
                            pace = settings.pace,
                            onLeft = {
                                screen = Screen.Home(canContinue = vault.loadGame() != null)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** What each home button does — out of [App] only so the destination table stays readable. */
private fun homeActions(
    vault: Vault,
    seeds: () -> Long,
    settings: Settings,
    go: (Screen) -> Unit,
): HomeActions = HomeActions(
    continueGame = {
        LocalGame.resume(vault, Dispatchers.Default)?.let { go(Screen.Playing(it)) }
    },
    newGame = {
        go(
            Screen.Playing(
                LocalGame.start(vault, seeds(), settings.difficulty, Dispatchers.Default),
            ),
        )
    },
    teach = { go(Screen.Teaching) },
    online = { go(Screen.Online) },
    settings = { go(Screen.Settings) },
)

/** Whether this choice means the dark palette, asking the system only when asked to. */
@Composable
private fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/** Where the app is. */
private sealed interface Screen {
    /** Finding out whether there is a game to come back to. */
    data object Opening : Screen

    data class Home(val canContinue: Boolean) : Screen

    data object Settings : Screen

    /** A real round with a coach over it. */
    data object Teaching : Screen

    data class Playing(val game: LocalGame) : Screen

    /** The way into a room: a name and a code. */
    data object Online : Screen

    /** Inside one: the lobby until the deal, the table after. */
    data class InRoom(val room: RemoteRoom) : Screen
}

/**
 * Where the room service answers. Its own hostname rather than a path on the app's, because
 * that host is a Pages project and layering a Worker route over it is a precedence puzzle —
 * see `worker/cloudflare/wrangler.jsonc`, which routes this name.
 */
private const val ROOM_SERVICE = "vinto-room.kupalinka.app"

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
