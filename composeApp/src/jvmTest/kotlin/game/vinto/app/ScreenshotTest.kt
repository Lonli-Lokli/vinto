package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Settings
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
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
        )
    }

    @Test
    fun theSettingsScreen() = shoot("settings") {
        SettingsScreen(
            settings = Settings(),
            canForget = true,
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
                    onReport = {},
                    onDeck = {},
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
                    onReport = {},
                    onDeck = {},
                )
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
        const val PHONE_W = 411
        const val PHONE_H = 740

        const val WARM_FRAMES = 10
        const val WARM_SLEEP_MS = 50L
        const val WARM_STEP_NANOS = 1_000_000_000L
    }
}
