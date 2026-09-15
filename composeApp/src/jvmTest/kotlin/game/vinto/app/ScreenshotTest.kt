package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import game.vinto.app.game.HelpSheet
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Question
import game.vinto.client.Settings
import game.vinto.client.TableMode
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import kotlin.test.Test

/**
 * The screens, photographed.
 *
 * Four of them, in both themes: the two menus, and the table in both of its arrangements.
 * These are the screens whose looks are the product — the felt, the fan, the plaques — and
 * whose regressions are invisible to every assertion-based test: a colour token that stops
 * resolving in dark, a felt gradient drawn upside down, a rail that quietly doubles its
 * padding. Each renders headless into an image and stands against its golden; the protocol
 * for goldens — first run writes, mismatch writes an `.actual.png` beside — is [Goldens]'.
 *
 * Everything drawn is deterministic: the table is the lesson's scripted deal, the felt weave
 * is seeded, and the scene is rendered at a fixed time well past the menus' opening
 * animations. What is *not* deterministic across machines is font rasterization, which is
 * why the comparison tolerates a fringe of glyph-edge pixels and why the goldens are
 * generated and kept by the maintainer — run the suite twice: once to write, once to verify.
 */
class ScreenshotTest {

    @Test
    fun theHomeScreen() = shoot("home") {
        HomeScreen(
            settings = Settings(),
            canContinue = true,
            go = HomeActions({}, {}, {}, {}, {}),
            // Pinned, because the real one is `git rev-list --count HEAD`: left to itself this
            // golden gained a wrong digit with every commit and could never be green twice.
            build = PINNED_BUILD,
        )
    }

    @Test
    fun theSettingsScreen() = shoot("settings") {
        SettingsScreen(
            // Pinned for the same reason the home screen's is: this screen draws the build
            // number too, and reading the real one made this golden gain a digit per commit.
            build = PINNED_BUILD,
            settings = Settings(),
            canForget = true,
            page = SettingsPage.ROOT,
            onOpen = {},
            onChange = {},
            onForget = {},
            onBack = {},
        )
    }

    @Test
    fun theTable() {
        val view = teachingSession().view.value
        shoot("table") {
            Box(modifier = Modifier.size(PHONE_W.dp, PHONE_H.dp)) {
                TableScreen(
                    state = TableState(view, tableFor(view), null, emptyList(), 1),
                    layout = TableLayout.forScreen(PHONE_H.dp),
                    onMove = {},
                    onHelp = {},
                    onSettings = {},
                )
            }
        }
    }

    @Test
    fun theTableOnItsSide() {
        val view = teachingSession().view.value
        shoot("table-wide", width = PHONE_H, height = PHONE_W) {
            Box(modifier = Modifier.size(PHONE_H.dp, PHONE_W.dp)) {
                TableScreen(
                    state = TableState(view, tableFor(view), null, emptyList(), 1),
                    layout = TableLayout.forScreen(PHONE_H.dp, PHONE_W.dp),
                    onMove = {},
                    onHelp = {},
                    onSettings = {},
                )
            }
        }
    }

    @Test
    fun thePlanOnTheFelt() {
        // The fifth screen, and the newest: the coalition's plan as table talk (design D18).
        // Its looks *are* the product in the same way the felt's are — a lit switch, a sentence
        // of boxed and plain words, the stops with their faces, the marked cards at the seats
        // that hold them — and none of it is reachable by an assertion about text.
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        val view = whole.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            players = whole.players.map { seat ->
                if (seat.id == caller.id) {
                    seat
                } else {
                    seat.copy(claims = listOf(Claim(seat.id, listOf(0), listOf(Rank.FIVE))))
                }
            },
        )
        val mate = view.players.first { it.id != view.viewerId && it.id != caller.id }
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Swap(CardAt(mate.id, 0), CardAt(view.viewerId, 0)))),
        )
        val table = tableFor(view, question = Question.ThePlan(), plan = plan)

        shoot("plan") {
            Box(modifier = Modifier.size(PHONE_W.dp, PHONE_H.dp)) {
                CompositionLocalProvider(LocalStage provides Stage().apply { mode = TableMode.PLAN }) {
                    TableScreen(
                        state = TableState(view, table, null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H.dp),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                    )
                }
            }
        }
    }

    @Test
    fun theHelpSheet() {
        // The sixth screen, and the one a player opens *during* a turn. It was a single column
        // — thirteen ranks, seven rings and four paragraphs — and is four tabs now, so what it
        // looks like is worth a picture: a legend nobody can find is a legend nobody reads.
        shoot("help") {
            Box(modifier = Modifier.size(PHONE_W.dp, PHONE_H.dp)) {
                HelpSheet(open = true, now = null, left = DECK_LEFT, onDismiss = {})
            }
        }
    }

    /** Renders [content] in each theme and stands both against their goldens. */
    private fun shoot(
        name: String,
        width: Int = PHONE_W,
        height: Int = PHONE_H,
        content: @Composable () -> Unit,
    ) {
        listOf(false, true).forEach { dark ->
            ImageComposeScene(width = width, height = height, density = Density(1f)) {
                VintoTheme(dark = dark) { content() }
            }.use { scene ->
                // Fonts and card art arrive asynchronously, so the first frames may be
                // missing them; render until the screen has had time to fill in, and take
                // the last frame — its time sits past the menus' opening animations too.
                var image = scene.render(0L)
                repeat(WARM_FRAMES) {
                    Thread.sleep(WARM_SLEEP_MS)
                    image = scene.render((it + 1) * WARM_STEP_NANOS)
                }
                Goldens.check("$name-${if (dark) "dark" else "light"}", image)
            }
        }
    }

    private companion object {
        /** Three digits, like a real one, so the footer is laid out at its true width. */
        const val PINNED_BUILD = "000"

        /** A plausible mid-round deck, so the sheet's live count reads like a real one. */
        const val DECK_LEFT = 21

        const val PHONE_W = 411
        const val PHONE_H = 740

        const val WARM_FRAMES = 10
        const val WARM_SLEEP_MS = 50L
        const val WARM_STEP_NANOS = 1_000_000_000L
    }
}
