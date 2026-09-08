package game.vinto.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import game.vinto.client.Settings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The one image App Store Connect will not create an in-app purchase without.
 *
 *     npm run capture-iap             # render it, then flatten it for upload
 *     ./gradlew :composeApp:jvmTest --tests '*IapShotTest*' -PstoreShots --rerun
 *
 * Gated on the same property as [StoreShotsTest] and for the same reason: it is a tool that
 * writes a file, not a gate that asserts one, and nobody wants it regenerated on every run.
 *
 * ## Why this is rendered rather than photographed
 *
 * Apple asks for "a screenshot of the in-app purchase as it appears in your app", and the app
 * cannot show it yet on any surface a camera can reach. The desktop and the web answer
 * `Support.Elsewhere` — a link to a page, because no store's rules reach those two — and a phone
 * answers `Support.Unavailable` until the product exists in the console. So the honest device
 * screenshot is of the product NOT being sold, and the picture Apple wants only becomes takeable
 * after the thing it is required for has already been submitted.
 *
 * `LocalSupport` cuts that knot. The screen below is the real `SettingsScreen` — same rows, same
 * type, same felt — with the platform's answer replaced by the offer the console is about to
 * make. Nothing about the purchase is misrepresented: [PRICE] is the `iaps` price in
 * `vydanne.config.mjs`, which is the number that goes into App Store Connect, and
 * `SupportProductTest` fails if the two ever disagree.
 *
 * ## The two things ASC rejects
 *
 * **Alpha**, which every Compose render carries and which `bridge` refuses on the way to Apple —
 * so the shot is flattened before it is uploaded, with vydanne's own helper. That is the second
 * half of `npm run capture-iap`, and it writes a sibling: **`support-review-iap.png` is the file
 * that goes to the console**, not the one this test writes.
 *
 * **Size**: at least 640x920. [WIDE] x [HIGH] is the 6.9" phone the listing shots use, which is
 * comfortably past it and is the same screen a buyer will be looking at.
 */
class IapShotTest {

    @Test
    fun theReviewScreenshotForTheSupportProduct() {
        if (System.getProperty("vinto.storeShots") != "true") return

        val out = File(OUT_DIR)
        out.mkdirs()

        ImageComposeScene(width = WIDE, height = HIGH, density = Density(DENSITY)) {
            VintoTheme(dark = false) {
                CompositionLocalProvider(
                    LocalVault provides MemoryVault(),
                    LocalSupport provides Support.Offered(PRICE),
                ) {
                    SettingsScreen(
                        settings = Settings(),
                        canForget = false,
                        page = SettingsPage.ROOT,
                        onOpen = {},
                        onChange = {},
                        onForget = {},
                        onBack = {},
                    )
                }
            }
        }.use { scene ->
            // Fonts and card art arrive asynchronously, so the first frames are missing them —
            // the same warm-up [StoreShotsTest] does, for the same reason.
            var image = scene.render(0L)
            repeat(WARM_FRAMES) {
                Thread.sleep(WARM_SLEEP_MS)
                image = scene.render((it + 1) * WARM_STEP_NANOS)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("the shot did not encode")
            File(out, SHOT).writeBytes(png.bytes)
        }

        assertTrue(File(out, SHOT).length() > 0, "wrote an empty $SHOT into $OUT_DIR")
        println("iap review screenshot -> $OUT_DIR/$SHOT (${WIDE}x$HIGH) — flatten before upload")
    }

    private companion object {
        /** App Store 6.9", well past the 640x920 floor an IAP screenshot has to clear. */
        const val WIDE = 1290
        const val HIGH = 2796
        const val DENSITY = 3f

        const val OUT_DIR = "../marketing/captures/iap"
        const val SHOT = "support-review.png"

        /**
         * The store's own five-unit point, as a US account reads it.
         *
         * Dollars rather than the listing's British pounds because App Review buys from a US
         * storefront, and a reviewer holding a screenshot whose price is not the price their own
         * device would show has one more thing to ask about.
         */
        const val PRICE = "\$4.99"

        const val WARM_FRAMES = 10
        const val WARM_SLEEP_MS = 50L
        const val WARM_STEP_NANOS = 1_000_000_000L
    }
}
