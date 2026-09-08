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
 * type, same felt — with the platform's answer replaced by the offer the console is about to make.
 *
 * ## No price in it, deliberately
 *
 * A store screenshot must not carry a figure. It is right for one storefront and wrong for the
 * other 174; the tier can move without the picture moving with it; and a reviewer holding a
 * screenshot whose price is not what their own device shows has one more thing to ask about.
 * Apple's own screenshot guidance says the same.
 *
 * So the offer is made with **no price**, and the button falls back to the figure-less label the
 * app already ships — the same rendering a real phone gives when Play returns an empty
 * `formattedPrice`. It is a state the app genuinely has, held by `SupportPriceTest`, rather than a
 * picture staged to look like one.
 *
 * ## It is a capture, not the upload
 *
 * This writes a plain render with an alpha channel, and **App Store Connect rejects alpha**. The
 * store-ready file is zdymak's — `npm run capture-iap` renders this and then composes it through
 * the `iap` device group in `zdymak.config.mjs`, which is the tool that owns "a file a store will
 * accept" for every other image in this repository. The upload is
 * `store-assets/iap-review/01-support-review.png`, not this file.
 *
 * ## The size is the purchase form's, not the listing's
 *
 * The slot's help says only that the image must meet "any of the screenshot specifications your
 * app supports", which reads as though a listing size would do. It does not. This was rendered at
 * 1290x2796 and then at 1320x2868 — both sizes Apple's own screenshot specification lists for the
 * 6.9" class, both taken without complaint by the listing slots — and **App Store Connect refused
 * each one**: "The dimensions of one or more screenshots are wrong." Nothing about either file was
 * wrong; the purchase form is older than the listing form and validates against an older table.
 *
 * So the size comes from `appstore-iap-review` in zdymak, which carries that slot's own table with
 * the oldest size first, and this renders at whatever that target asks for rather than at a phone
 * shape somebody picked. [WIDE] x [HIGH] is 640x920 — a real screenshot specification (Apple's
 * 3.5" class) and the size Apple's in-app purchase documentation asked for when the slot was built.
 *
 * **It is short and square-ish, and that is the point of rendering rather than scaling.** A phone
 * capture squeezed into 0.7:1 is a crop of a screen; this is the screen laid out for 320x460dp,
 * which puts the purchase — the one thing the reviewer is looking for — at the top of a page that
 * fits.
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
                    LocalSupport provides Support.Offered(price = ""),
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
        println("iap capture -> $OUT_DIR/$SHOT (${WIDE}x$HIGH) — zdymak composes the upload")
    }

    private companion object {
        /** `appstore-iap-review`'s own size — see the note above for why it is not a phone's. */
        const val WIDE = 640
        const val HIGH = 920

        /**
         * 320x460dp at 2x, which is a real iPhone width (the SE's) rather than an arbitrary one.
         *
         * At 3x it would be 213dp — narrower than any device the app runs on, so the layout would
         * be answering a question it is never asked and the shot would show a screen nobody sees.
         */
        const val DENSITY = 2f

        const val OUT_DIR = "../marketing/captures/iap"
        const val SHOT = "support-review.png"

        const val WARM_FRAMES = 10
        const val WARM_SLEEP_MS = 50L
        const val WARM_STEP_NANOS = 1_000_000_000L
    }
}
