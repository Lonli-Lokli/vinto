package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
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

        // The scenes `zdymak.config.mjs` names, under its ids and in its captures directories,
        // because that is how it finds them: a scene's source is `<capturesDir>/<id>.png` and
        // nothing else. The app reaches each through the same `MarketingScene` handle a device
        // capture uses (`CaptureHandleTest`), which is what stops these drifting from what a
        // phone would actually show.
        SLOTS.forEach { slot ->
            File(slot.dir).mkdirs()
            MarketingScene.entries.forEach { scene ->
                shoot(scene.id, slot) {
                    // A phone, not the desktop the JVM's own `actual` reports: the header is one
                    // of three known shapes and picks its shape from the host, so drawing a
                    // 411 dp window as a desktop gave every control its word and pushed the
                    // wordmark off the left edge. Tablets are phones here too, which is what the
                    // enum means by it — the landscape shots label themselves from their shape.
                    CompositionLocalProvider(LocalHost provides Host.PHONE) {
                        App(seeds = { MARKETING_SEED }, vault = MemoryVault(), marketing = scene.id)
                    }
                }
            }

            val written = File(slot.dir).listFiles { f -> f.extension == "png" }.orEmpty()
            assertTrue(
                written.size >= EXPECTED,
                "wrote only ${written.size} shots into ${slot.dir}",
            )
            println("${slot.name} -> ${slot.dir} (${written.size} files, ${slot.wide}x${slot.high})")
        }
    }

    /**
     * One store slot's shape: the pixels it takes, and the dp that makes.
     *
     * **A slot each, rather than one shot scaled into all of them.** zdymak cover-fits a capture
     * into its target and anchors the Play sets to the top, which is right for a small difference
     * of aspect and catastrophic for a large one: a portrait phone capture fitted into the Play
     * *tablet* slot, which is 16:9 **landscape**, kept its top third — six screenshots of a status
     * bar, a header and a field of green felt, uploaded and live. The app has a landscape
     * arrangement for exactly that shape; it simply was never asked for one.
     */
    private data class Slot(
        val name: String,
        val dir: String,
        val wide: Int,
        val high: Int,
        val density: Float,
    )

    /**
     * One screen, in the light scheme, at store resolution.
     *
     * Light only: a store listing wants one coherent set, and the dark screens are already
     * covered as goldens by [ScreenshotTest]. Swap the flag here if the listing ever wants them.
     */
    private fun shoot(name: String, slot: Slot, content: @Composable () -> Unit) {
        ImageComposeScene(
            width = slot.wide,
            height = slot.high,
            density = Density(slot.density),
        ) {
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
            stillShuffling(image, "${slot.name}/$name")
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("$name did not encode")
            File(slot.dir, "$name.png").writeBytes(png.bytes)
        }
    }

    /**
     * Refuses a shot of the opening splash.
     *
     * Two of these scenes stage a whole game before they have anything to draw, and a render
     * that arrives first photographs *"Vinto! Shuffling…"* — a flat green field with a card and
     * two words on it. That is not a failure anybody notices: the file is written, the run is
     * green, `bridge` copies it and the store shows a loading screen as a screenshot. It is
     * exactly the fault `zdymak.config.mjs` records against the simulator, which sat on that
     * splash after 75 s of settle.
     *
     * The splash is almost entirely one colour, and no real screen is: the felt alone carries a
     * gradient, and every screen has a rail under it. So a shot whose sampled pixels are nine
     * tenths one colour has not finished, and the run stops rather than writing it.
     */
    private fun stillShuffling(image: Image, name: String) {
        val pixels = image.toComposeImageBitmap().toPixelMap()
        var commonest = 0
        val seen = mutableMapOf<Int, Int>()
        var sampled = 0
        var y = 0
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                val at = pixels[x, y].toArgb()
                val count = (seen[at] ?: 0) + 1
                seen[at] = count
                if (count > commonest) commonest = count
                sampled++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
        assertTrue(
            commonest < sampled * FLAT_ENOUGH,
            "$name is still on the shuffling splash: ${commonest * 100 / sampled}% of it is one " +
                "colour. The scene stages a game before it can draw; give it longer to settle.",
        )
    }

    private companion object {
        /** Every nth pixel each way — enough to tell a splash from a table, cheap enough to run. */
        const val SAMPLE_STEP = 16

        /** How much of a shot may be a single colour before it is not a screen at all. */
        const val FLAT_ENOUGH = 0.9

        /**
         * Every slot the two stores upload, each at its own shape.
         *
         * The directories are `zdymak.config.mjs`'s own `capturesDir`s, so these ARE the
         * captures — the same files `zdymak capture` would write from a simulator or over adb,
         * written by the app drawing itself instead. They used to go to a directory of their
         * own, which nothing read: `zdymak screenshots` went on building the store's media from
         * whatever a device had last been driven through, and the listings quietly carried a
         * build that was three weeks old.
         */
        val SLOTS = listOf(
            // App Store 6.9" (iPhone 16 Pro Max), and well over Play's 1080px phone floor.
            Slot("iphone", "../marketing/captures/ios", 1290, 2796, 3f),
            /*
             * App Store 13" iPad, portrait.
             *
             * An iPad is @2x, and getting this wrong is not a rounding error. At the phone's 3f,
             * 2064x2752 is 688x917 dp — a large phone, so the app laid it out as one: the tablet
             * card sizes never engaged and the type scale never engaged either, and the shot went
             * to the store looking like a stretched phone. At 2f it is 1032x1376 dp, which is
             * what an iPad Pro 13" actually reports.
             */
            Slot("ipad", "../marketing/captures/ios-ipad", 2064, 2752, 2f),
            /*
             * Play phone, 9:16.
             *
             * 2.625 rather than 3, which would make 1080x1920 a 360x640 dp screen — shorter than
             * any phone sold. At 2.625 it is 411x731 dp, which is a Pixel, and a Pixel is what
             * the hand layout was measured against (`CrowdedTableTest`).
             */
            Slot("android", "../marketing/captures/android", 1080, 1920, 2.625f),
            /*
             * Play tablet, and it is **landscape** 16:9 — which is why it needs its own render
             * rather than a crop of the phone's. 2560x1440 at 2f is 1280x720 dp, and the table
             * turns on its side there the way it does on a real tablet held that way.
             */
            Slot("android-tablet", "../marketing/captures/android-tablet", 2560, 1440, 2f),
        )

        const val EXPECTED = 5

        /** The same pinned seed `MarketingState` deals from, so a shot is the same shot twice. */
        const val MARKETING_SEED = 20_260_903L

        const val WARM_FRAMES = 120
        const val WARM_SLEEP_MS = 100L
        const val WARM_STEP_NANOS = 1_000_000_000L
    }
}
