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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.Crashes
import game.vinto.app.game.GameScreen
import game.vinto.app.game.RoomScreen
import game.vinto.app.game.TeachScreen
import game.vinto.app.link.roomCodeFrom
import game.vinto.app.link.takeOpenedLink
import game.vinto.app.net.platformRoomConnector
import game.vinto.app.theme.LocalFeedback
import game.vinto.app.theme.LocalSounds
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.app.theme.rememberFeedback
import game.vinto.app.theme.rememberSounds
import game.vinto.client.Analytics
import game.vinto.client.AnalyticsConsent
import game.vinto.client.LocalGame
import game.vinto.client.RemoteRoom
import game.vinto.client.RoomConnector
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.Vault
import game.vinto.client.forgetGame
import game.vinto.client.identity
import game.vinto.client.loadGame
import game.vinto.client.loadSettings
import game.vinto.client.saveSettings
import game.vinto.protocol.AnalyticsEvent
import game.vinto.protocol.FunnelStep
import game.vinto.protocol.Surface
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
fun App(
    seeds: () -> Long = ::freshSeed,
    vault: Vault = remember { platformVault() },
    /**
     * Where anonymous counts go. Injected like [seeds] and [vault] and for the same reason:
     * a test needs to see what the app would have sent, and the only honest way to check that
     * is to let it send to something that records.
     */
    counting: Counting? = null,
) {
    var settings by remember { mutableStateOf(Settings()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Opening) }

    // One connector for the app's lifetime, and a scope for the rooms it opens: a room's
    // socket loop belongs to the app, not to whichever screen happens to be showing it.
    val connector = remember { platformRoomConnector(ROOM_SERVICE) }
    // With a handler, so a coroutine that fails out here is *reported* rather than printed to
    // a console nobody is reading. Everything long-lived rides on this scope — the socket
    // loop, the analytics sink, a room's reconnects — and those are exactly the failures that
    // leave the app looking fine and doing nothing, which no fatal handler will ever see.
    val appScope = rememberCoroutineScope { Crashes.handler() }

    val sink = rememberSink(appScope)
    val count = counting ?: remember(sink) { counting(sink) }
    ReportCrashes()

    fun enterRoom(code: String, nickname: String): Screen =
        roomScreen(connector, vault, appScope, code, nickname)

    Startup(vault, sink, count, seeds, ::enterRoom) { loaded, where ->
        settings = loaded
        screen = where
    }

    // Every change is written down as it is made. There is no "save" button in a settings
    // screen worth having, and four values are not worth batching.
    fun change(updated: Settings) {
        settings = updated
        vault.saveSettings(updated)
        // Immediately, not on next launch: somebody who just turned it off means now, and
        // `consentChanged` discards whatever was buffered rather than flushing it.
        sink.consentChanged(consentFrom(updated))
    }

    // Back goes home from anywhere that is not home, and closes the app from there — which is
    // what a phone's back button means everywhere else, and what its absence made look like a
    // crash the first time somebody pressed it in the settings.
    SystemBack(enabled = screen !is Screen.Home && screen !is Screen.Opening) {
        screen = screen.backedOutOf(vault)
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
                    // Where the app is, for the events that are about that rather than about
                    // what happened. Provided once here rather than by each screen: the thing
                    // that reads it is `CardStage`, which is the same code under all three
                    // tables and cannot tell them apart on its own.
                    CompositionLocalProvider(
                        LocalSurface provides surfaceOf(screen),
                        LocalVault provides vault,
                    ) {
                        when (val here = screen) {
                            Screen.Opening -> OpeningScreen()

                            is Screen.Home -> HomeScreen(
                                settings = settings,
                                canContinue = here.canContinue,
                                go = homeActions(vault, seeds, settings, count) { screen = it },
                            )

                            is Screen.Settings -> SettingsScreen(
                                settings = settings,
                                canForget = vault.loadGame() != null,
                                onChange = ::change,
                                onForget = {
                                    vault.forgetGame()
                                    screen = Screen.Home(canContinue = false)
                                },
                                onBack = { screen = here.back },
                            )

                            Screen.Teaching -> TeachScreen(
                                botDispatcher = Dispatchers.Default,
                                pace = settings.pace,
                                onSettings = { screen = Screen.Settings(back = here) },
                                onDone = { screen = Screen.Home(canContinue = vault.loadGame() != null) },
                            )

                            is Screen.Playing -> GameScreen(
                                game = here.game,
                                pace = settings.pace,
                                onSettings = { screen = Screen.Settings(back = here) },
                                onQuit = { screen = Screen.Home(canContinue = true) },
                            )

                            is OnlineWay -> OnlineFlow(
                                where = here,
                                connector = connector,
                                vault = vault,
                                enterRoom = ::enterRoom,
                                go = { screen = it },
                            )

                            is Screen.InRoom -> RoomScreen(
                                room = here.room,
                                pace = settings.pace,
                                onSettings = { screen = Screen.Settings(back = here) },
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
}

/** What each home button does — out of [App] only so the destination table stays readable. */
private fun homeActions(
    vault: Vault,
    seeds: () -> Long,
    settings: Settings,
    counting: Counting,
    go: (Screen) -> Unit,
): HomeActions = HomeActions(
    continueGame = {
        counting.record(AnalyticsEvent.Funnel(FunnelStep.PLAY_PRESSED, Surface.SOLO))
        LocalGame.resume(vault, Dispatchers.Default)?.let { go(Screen.Playing(it)) }
    },
    newGame = {
        counting.record(AnalyticsEvent.Funnel(FunnelStep.PLAY_PRESSED, Surface.SOLO))
        go(
            Screen.Playing(
                LocalGame.start(vault, seeds(), settings.difficulty, Dispatchers.Default),
            ),
        )
    },
    teach = {
        counting.record(AnalyticsEvent.Funnel(FunnelStep.PLAY_PRESSED, Surface.LESSON))
        go(Screen.Teaching)
    },
    online = {
        counting.record(AnalyticsEvent.Funnel(FunnelStep.ONLINE_PRESSED, Surface.ONLINE))
        go(Screen.Online)
    },
    settings = { go(Screen.Settings(back = Screen.Home(canContinue = vault.loadGame() != null))) },
)

/** Whether this choice means the dark palette, asking the system only when asked to. */
@Composable
private fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/** Which surface a destination counts as. See [LocalSurface]. */
private fun surfaceOf(screen: Screen): Surface = when (screen) {
    is Screen.Playing -> Surface.SOLO
    Screen.Teaching -> Surface.LESSON
    Screen.Online, is Screen.OpenRoom, is Screen.JoinByCode, is Screen.Discover, is Screen.InRoom ->
        Surface.ONLINE
    Screen.Opening, is Screen.Home, is Screen.Settings -> Surface.MENU
}

/**
 * Where the phone's back button goes from here.
 *
 * Home from almost anywhere, which is what back means everywhere else on a phone and what its
 * absence made look like a crash the first time somebody pressed it in the settings. Two
 * exceptions, both of which would otherwise throw something away:
 *
 *  - **the settings know their own way back.** Since the gear reached the table's header they
 *    are reachable mid-round, and a back that went home would abandon the round somebody
 *    stepped out of to change the pace of.
 *  - **backing out of a room is leaving it**, so the socket loop does not outlive the screen.
 *    The seat token stays vaulted, so this is not a lost seat.
 */
private fun Screen.backedOutOf(vault: Vault): Screen {
    if (this is Screen.Settings) return back
    // The three ways in back out to the front door, which is where their own chevron goes.
    // They did not: `Discover` sent the system back button all the way Home while the button
    // drawn on the screen went to `Online`, so one gesture meant two things depending on
    // whether you used the phone's or the app's. Splitting the lobby into three would have
    // made that inconsistency three times as easy to hit.
    if (this is Screen.OpenRoom || this is Screen.JoinByCode || this is Screen.Discover) {
        return Screen.Online
    }
    (this as? Screen.InRoom)?.room?.leave()
    return Screen.Home(canContinue = vault.loadGame() != null)
}

/** Where the app is. */
private sealed interface Screen {
    /** Finding out whether there is a game to come back to. */
    data object Opening : Screen

    data class Home(val canContinue: Boolean) : Screen

    /**
     * The settings, and where to go when they are closed.
     *
     * It carries its own way back rather than always returning Home, because the settings are
     * now reachable from the table's header (§6g): pace is the setting somebody wants to change
     * *during* the round that is too slow, and a Back that abandoned the round would make it a
     * setting nobody ever changes. [back] is the screen the gear was pressed on — the same
     * `Playing` holding the same `LocalGame`, so nothing is re-dealt or re-connected.
     */
    data class Settings(val back: Screen) : Screen

    /** A real round with a coach over it. */
    data object Teaching : Screen

    data class Playing(val game: LocalGame) : Screen

    /** The front door: a name, and which of the three things you came to do. */
    data object Online : Screen, OnlineWay

    /**
     * Opening a room of your own: the visibility choice, and the button that mints it.
     *
     * Carries the nickname for the same reason [Discover] does — the vault holds what was
     * *saved*, and these screens are reached by a tap that saves on the way through, so
     * passing it explicitly is what makes the two agree.
     */
    data class OpenRoom(val nickname: String) : Screen, OnlineWay

    /** Joining somebody else's: six characters. */
    data class JoinByCode(val nickname: String) : Screen, OnlineWay

    /**
     * The public rooms, for somebody with no code.
     *
     * Carries the nickname rather than reading it back out of the vault, so that a name typed
     * on the way in is the name used on the way through — the vault holds what was *saved*,
     * and a person who edited the field and pressed Browse has not saved anything yet.
     */
    data class Discover(val nickname: String) : Screen, OnlineWay

    /** Inside one: the lobby until the deal, the table after. */
    data class InRoom(val room: RemoteRoom) : Screen
}

/**
 * Where the room service answers. Its own hostname rather than a path on the app's, because
 * that host is a Pages project and layering a Worker route over it is a precedence puzzle —
 * see `worker/cloudflare/wrangler.jsonc`, which routes this name.
 */
private const val ROOM_SERVICE = "vinto-room.kupalinka.app"

/** The app's surface vocabulary, as a crash report spells it. Coarse by construction. */
private fun Surface.asCrashSurface(): CrashSurface = when (this) {
    Surface.SOLO -> CrashSurface.SOLO
    Surface.ONLINE -> CrashSurface.ONLINE
    Surface.LESSON -> CrashSurface.LESSON
    Surface.MENU -> CrashSurface.MENU
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

/**
 * Installs the crash reporter, once, for the life of the app.
 *
 * Same discipline as the counter and deliberately a different pipe. The DSN is a build
 * constant rather than a secret — its key can submit an event and cannot read one — absent
 * means reporting is off, and nothing identifying travels with a report (`crash/Crash.kt`
 * makes that structural rather than careful).
 *
 * **Not gated on the analytics opt-out**, and that is a decision rather than an oversight.
 * The two answer different questions: a count is about what people chose to do, a crash is
 * the app failing at something it promised to do. §6i keeps them on separate pipes for the
 * same reason. Neither carries an identifier, which is what makes the distinction honest
 * instead of merely convenient.
 */
@Composable
private fun ReportCrashes() {
    // **This does not install the reporter**, and that is deliberate. Every entry point does,
    // before it composes anything, because the crash worth having most is the one on the
    // launcher — and the only host that reaches `App()` without going through one of them is a
    // test harness. Installing here as a fallback would mean the Compose suites arm a live
    // reporter against the project's real DSN and post a runner's failures into it.
    //
    // What is left is the half that only a composition knows: *where* the app is, read live at
    // the moment of a crash, so one on the table is not filed as one in the menu.
    val surface = rememberUpdatedState(LocalSurface.current)
    LaunchedEffect(Unit) { Crashes.watching { surface.value.asCrashSurface() } }
}

/**
 * One analytics sink for the app's lifetime.
 *
 * Built opted-**out** and told the truth once the vault has been read, so the window between
 * launching and loading settings cannot emit anything the player did not agree to. Getting
 * that order wrong would send one event per cold start from somebody who had switched
 * counting off, which is the one bug in this area nobody would ever see from the inside.
 */
@Composable
private fun rememberSink(scope: kotlinx.coroutines.CoroutineScope): Analytics = remember(scope) {
    Analytics(
        transport = analyticsTransport(ROOM_SERVICE),
        consent = AnalyticsConsent(optedIn = false, platformObjects = true),
        scope = scope,
    )
}

/**
 * One way into a room, whether the code was typed, tapped off the public list, or arrived in
 * an invitation.
 *
 * All three open the same socket to the same room, so all three build it the same way rather
 * than each remembering to pass the scope.
 *
 * An invite is read once and cleared (`takeOpenedLink`), so pressing Back afterwards lands on
 * the home screen rather than being pulled straight back into the room — an invitation is an
 * instruction, not a destination the app keeps. It uses the stored nickname rather than
 * asking for one: the whole point of the link is that six characters do not have to be typed,
 * and stopping to ask a name would put a form back exactly where one was removed.
 */
private fun roomScreen(
    connector: RoomConnector,
    vault: Vault,
    scope: kotlinx.coroutines.CoroutineScope,
    code: String,
    nickname: String,
): Screen = Screen.InRoom(
    RemoteRoom(connector = connector, code = code, vault = vault, nickname = nickname, scope = scope),
)

/**
 * Everything between launching and playing, which is one disk read and one decision.
 *
 * On a cold start it lands in the same frame; the opening screen exists for the case where it
 * does not, and for a room that has to be reached over a network before anything can be drawn.
 *
 * The order inside matters and is the reason this is one effect rather than three. Consent is
 * told the truth *before* the first event is recorded, so the window between launching and
 * loading settings cannot emit anything the player opted out of — and the invite is read
 * after both, so an invited player is counted as having joined rather than merely opened the
 * app.
 */
@Composable
private fun Startup(
    vault: Vault,
    sink: Analytics,
    count: Counting,
    seeds: () -> Long,
    enterRoom: (String, String) -> Screen,
    onReady: (Settings, Screen) -> Unit,
) {
    LaunchedEffect(Unit) {
        val settings = vault.loadSettings()
        sink.consentChanged(consentFrom(settings))
        count.record(AnalyticsEvent.Funnel(FunnelStep.APP_OPENED, Surface.MENU))

        val invited = roomCodeFrom(takeOpenedLink())
        val where = if (invited != null) {
            count.record(AnalyticsEvent.Funnel(FunnelStep.ROOM_JOINED, Surface.ONLINE))
            enterRoom(invited, vault.identity { seeds() }.nickname)
        } else {
            Screen.Home(canContinue = vault.loadGame() != null)
        }
        onReady(settings, where)
    }
}

/**
 * The four screens between the menu and a seat.
 *
 * A marker on the `Screen` cases rather than a nested type, so `App`'s `when` has one branch
 * for the whole way in and stays readable — it grew past the length limit the moment the
 * lobby became three screens instead of one, which is the honest signal that a group had
 * formed and wanted naming.
 */
private sealed interface OnlineWay

/**
 * The way in, from the front door to a room's code.
 *
 * Every arrow between these four is here, in one place: the tiles lead outward, and each of
 * the three destinations leads back to [Screen.Online] rather than to wherever it was reached
 * from. That is what makes the chevron on each of them mean the same thing.
 */
@Composable
private fun OnlineFlow(
    where: OnlineWay,
    connector: RoomConnector,
    vault: Vault,
    enterRoom: (String, String) -> Screen,
    go: (Screen) -> Unit,
) {
    when (where) {
        Screen.Online -> OnlineScreen(
            vault = vault,
            onOpenRoom = { go(Screen.OpenRoom(it)) },
            onJoinByCode = { go(Screen.JoinByCode(it)) },
            onBrowse = { go(Screen.Discover(it)) },
            onBack = { go(Screen.Home(canContinue = vault.loadGame() != null)) },
        )

        is Screen.OpenRoom -> OpenRoomScreen(
            connector = connector,
            nickname = where.nickname,
            onEnterRoom = { code, nickname -> go(enterRoom(code, nickname)) },
            onBack = { go(Screen.Online) },
        )

        is Screen.JoinByCode -> JoinCodeScreen(
            nickname = where.nickname,
            onEnterRoom = { code, nickname -> go(enterRoom(code, nickname)) },
            onBack = { go(Screen.Online) },
        )

        is Screen.Discover -> DiscoverScreen(
            connector = connector,
            onJoin = { go(enterRoom(it, where.nickname)) },
            onBack = { go(Screen.Online) },
        )
    }
}
