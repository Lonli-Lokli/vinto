package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The screenshots the stores ask for, rendered from the real screens.
 *
 *     ./gradlew :composeApp:jvmTest --tests '*StoreShotsTest*' -Dvinto.storeShots=true --rerun
 *
 * **Skipped unless that property is set**, which is the difference between this and
 * [ScreenshotTest] beside it. That one is a *gate*: it renders four screens small and stands them
 * against committed goldens, and it runs on every test run because its job is to notice a felt
 * gradient drawn upside down. This one is a *tool*: it writes 1290×2796 marketing images into
 * `marketing/captures/`, which nothing asserts and nobody wants regenerated every time they run
 * the suite.
 *
 * ## Why the app can photograph itself
 *
 * `zdymak` — the portfolio's media tool — normally drives a real device: an iOS simulator with a
 * launch argument, or Android over `adb` with an intent extra. **Neither works here**, and
 * `zdymak.config.mjs` says so in as many words: `MainActivity` reads a deep link and nothing
 * else, so there is no handle to put the app into a named state. That left the scene ids in its
 * config as names for shots somebody would take by hand.
 *
 * They need not be. Compose renders headless already — the whole `jvmTest` suite is built on it —
 * and `ImageComposeScene` takes a pixel size and a density as separate arguments. So asking for
 * 1290×2796 at density 3 is asking for a 6.9" iPhone's screen, drawn by the same code that draws
 * the phone's, from fixtures that are deterministic. No simulator, no device, no capture handle,
 * and the shots cannot drift from the app because they *are* the app.
 *
 * What this deliberately does NOT do is frame, caption or letterbox them. That is `zdymak`'s job
 * and it is a design decision per scene; these are the plain screens it takes as input.
 *
 * ## The sizes
 *
 * One set, at the largest size both stores accept, because a store scales down cleanly and up
 * never. 1290×2796 is App Store 6.9" (iPhone 16 Pro Max) and comfortably above Play's 1080px
 * minimum for a phone screenshot.
 */
class StoreShotsTest {

    @Test
    fun theStoreScreens() {
        if (System.getProperty("vinto.storeShots") != "true") return

        val out = File(OUT_DIR)
        out.mkdirs()

        // The five scenes `zdymak.config.mjs` names, in its order, under its ids — so the
        // captions it writes line up with the pictures it is given. The app reaches each through
        // the same `MarketingScene` handle a device capture uses (`CaptureHandleTest`), which is
        // what stops these drifting from what a phone would actually show.
        MarketingScene.entries.forEachIndexed { i, scene ->
            shoot(
                "0${i + 1}-${scene.id}",
            ) { App(seeds = { MARKETING_SEED }, vault = MemoryVault(), marketing = scene.id) }
        }

        val written = out.listFiles { f -> f.extension == "png" }.orEmpty()
        assertTrue(written.size >= EXPECTED, "wrote only ${written.size} shots into $OUT_DIR")
        println("store shots -> $OUT_DIR (${written.size} files, ${WIDE}x$HIGH)")
    }

    /**
     * One screen, in the light scheme, at store resolution.
     *
     * Light only: a store listing wants one coherent set, and the dark screens are already
     * covered as goldens by [ScreenshotTest]. Swap the flag here if the listing ever wants them.
     */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        ImageComposeScene(width = WIDE, height = HIGH, density = Density(DENSITY)) {
            VintoTheme(dark = false) { content() }
        }.use { scene ->
            // Fonts and card art arrive asynchronously, so the first frames are missing them.
            // Render a few and keep the last, whose time also sits past the opening animations —
            // the same warm-up [ScreenshotTest] does, and for the same reason.
            var image = scene.render(0L)
            repeat(WARM_FRAMES) {
                Thread.sleep(WARM_SLEEP_MS)
                image = scene.render((it + 1) * WARM_STEP_NANOS)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("$name did not encode")
            File(OUT_DIR, "$name.png").writeBytes(png.bytes)
        }
    }

    private companion object {
        /** App Store 6.9" — and well over Play's 1080px floor for a phone shot. */
        const val WIDE = 1290
        const val HIGH = 2796
        const val DENSITY = 3f

        const val OUT_DIR = "../marketing/captures/store"
        const val EXPECTED = 5

        /** The same pinned seed `MarketingState` deals from, so a shot is the same shot twice. */
        const val MARKETING_SEED = 20_260_903L

        const val WARM_FRAMES = 10
        const val WARM_SLEEP_MS = 50L
        const val WARM_STEP_NANOS = 1_000_000_000L
    }
}
