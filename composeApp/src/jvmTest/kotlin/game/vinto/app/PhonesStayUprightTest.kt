package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A phone is held upright; a tablet turns every way.
 *
 * The table had an arrangement for a phone on its side — the rail beside the felt — and a layout
 * review found it could not hold the game: on an iPhone 16 turned over, the felt is 276 points
 * tall once the header and the room's clock are taken, and the side seats' cards ran up over the
 * seat opposite and down over the player's own plate. There is no arrangement of four hands, two
 * piles and four plates that fits there, so a phone stays upright, as most card games on a phone
 * do. Tablets and an opened fold keep every orientation, because a tablet's landscape is a real
 * table and not a squeezed one.
 *
 * One rule decides what a phone is ([phoneHeldUpright], the short side under 600 points, the same
 * line the type scale already uses), and each platform is held to applying it.
 */
class PhonesStayUprightTest {

    @Test
    fun aPhoneIsHeldUprightAndATabletOrAnOpenFoldIsNot() {
        listOf(320, 360, 375, 402, 440, 599).forEach {
            assertTrue(phoneHeldUpright(it), "a screen $it points across its short side is a phone")
        }
        listOf(600, 673, 744, 820, 1032).forEach {
            assertFalse(phoneHeldUpright(it), "a screen $it points across its short side is a tablet")
        }
    }

    /**
     * iOS says it per device family, iPads every way including upside down — and for a phone, per
     * phone: upright, unless it folds. The closed iPhone Duo on its side keeps its whole height,
     * since iOS stands its status bar in a column down the side, and Apple's apps turn with it
     * (owner, 2026-10-13). So the phone list names the landscapes, and the app delegate, which iOS
     * asks first, grants them only to a phone that folds.
     */
    @Test
    fun iPhonesAreUprightUnlessTheyFoldAndIpadsTurnEveryWay() {
        val plist = File(
            "../iosApp/iosApp/Info.plist",
        ).also { assertTrue(it.exists(), "the Info.plist moved") }.readText()
        assertEquals(
            setOf(
                "UIInterfaceOrientationPortrait",
                "UIInterfaceOrientationLandscapeLeft",
                "UIInterfaceOrientationLandscapeRight",
            ),
            orientations(plist, "UISupportedInterfaceOrientations").toSet(),
        )
        val app = File(
            "../iosApp/iosApp/iOSApp.swift",
        ).also { assertTrue(it.exists(), "iOSApp.swift moved") }.readText()
        assertTrue("supportedInterfaceOrientationsFor" in app, "nothing narrows the phone list down per phone")
        assertTrue("foldable ? .allButUpsideDown : .portrait" in app, "a phone that does not fold may turn")
        assertTrue("\"iPhone19,4\"" in app, "the iPhone Duo is not known to fold")
        assertTrue("Orientations.hingeReported()" in File("../iosApp/iosApp/ContentView.swift").readText())
        assertEquals(
            setOf(
                "UIInterfaceOrientationPortrait",
                "UIInterfaceOrientationPortraitUpsideDown",
                "UIInterfaceOrientationLandscapeLeft",
                "UIInterfaceOrientationLandscapeRight",
            ),
            orientations(plist, "UISupportedInterfaceOrientations~ipad").toSet(),
        )
    }

    /**
     * Android has no device family to say it by, so the activity asks at launch from the screen's
     * smallest width — and again when the screen changes, because a fold opened or closed is the
     * same activity on a different screen (the manifest keeps the activity through that change).
     */
    @Test
    fun androidAsksForUprightOnAPhoneAndAsksAgainWhenTheScreenChanges() {
        val activity = File("../androidApp/src/main/kotlin/game/vinto/app/MainActivity.kt")
            .also { assertTrue(it.exists(), "MainActivity moved") }
            .readText()
        assertTrue("phoneHeldUpright(" in activity, "the activity does not ask whether it is on a phone")
        assertTrue("smallestScreenWidthDp" in activity, "the activity does not measure the screen's short side")
        assertTrue("SCREEN_ORIENTATION_PORTRAIT" in activity, "the activity never asks for portrait")
        assertTrue("onConfigurationChanged" in activity, "a fold opened or closed is never asked about again")
    }

    private fun orientations(plist: String, key: String): List<String> {
        val at = plist.indexOf("<key>$key</key>")
        assertTrue(at >= 0, "$key is not in the Info.plist")
        val array = plist.substring(plist.indexOf("<array>", at), plist.indexOf("</array>", at))
        return Regex("<string>([^<]+)</string>").findAll(array).map { it.groupValues[1] }.toList()
    }
}
