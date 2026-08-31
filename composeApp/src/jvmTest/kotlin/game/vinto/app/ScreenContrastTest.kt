package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import game.vinto.app.game.HelpSheet
import game.vinto.app.game.StandingsSheet
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CreatedRoom
import game.vinto.client.MemoryVault
import game.vinto.client.Question
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.RoomTrouble
import game.vinto.client.RoundResult
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.roundPoints
import game.vinto.client.saveSettings
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.protocol.PublicRoom
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every screen, photographed and read.
 *
 * [ContrastTest] measures the palette: every pair of colours somebody declared goes together.
 * This measures the screens, because the failure that actually ships is not a bad pair in the
 * palette — it is a good colour on the wrong background. The Play online screen shipped
 * exactly that: `Rail` ink, dark in the light scheme because it is made for the paper rail,
 * written over the felt and over the tiles' fixed charcoal, both of which are dark in *both*
 * schemes. Every token in that pairing passed the palette test individually, and the screen
 * was unreadable on a light phone.
 *
 * So: each screen is rendered, its pixels captured, and every visible piece of text held to
 * WCAG 2.1 — the colour the style resolved for it, against the dominant colour of the pixels
 * the renderer actually put behind it. 4.5:1 for text, 3:1 for large text, disabled controls
 * exempt as SC 1.4.3 says they are. Both themes, always, because a pairing can be right in
 * one and invisible in the other — which is precisely how the defect above got out: the dark
 * scheme was fine.
 *
 * One `commonMain` is all three clients, so what this holds is what Android, iOS and the
 * browser all draw. The menu screens are reached through the real [App], so the sweep sees
 * the scaffolds and backdrops a player gets rather than a fixture's idea of them; the table,
 * the sheets and the public-rooms screen are composed directly with the same deterministic
 * fixtures the rest of the suite uses. The one screen not swept is the online lobby, which
 * cannot be reached without a socket; every material it is built from — rail, tiles, fields,
 * plates — is measured on the screens here.
 *
 * What it measures is a floor, not the whole truth: text below the fold of a 740dp phone is
 * clipped out of the capture, and text whose style never resolves a colour is skipped. Both
 * are deliberate — a screen is judged as a phone shows it, and a node this cannot measure is
 * not silently guessed at.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenContrastTest {

    // ------------------------------------------------------------------ the screens

    /** Home, the settings, and all three doors of online play, walked through the real app. */
    @Test
    fun theMenusAndTheWayIntoOnlinePlayCanBeRead() = eachScheme { dark, scheme ->
        runComposeUiTest {
            val vault = MemoryVault()
            vault.saveSettings(Settings(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT))
            setContent {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    App(seeds = { SEED }, vault = vault)
                }
            }
            waitForIdle()
            val found = mutableListOf<String>()
            found += unreadableHere("the home screen")

            press("Settings")
            found += unreadableHere("the settings screen")
            press("Back")

            press("Play online")
            found += unreadableHere("the front door of online play")

            press("Open a room")
            found += unreadableHere("opening a room")
            press("Back")

            press("Join with a code")
            found += unreadableHere("joining with a code")

            judged(scheme, found)
        }
    }

    /** The public rooms: a busy evening, a quiet one, and a service that cannot be reached. */
    @Test
    fun thePublicRoomsCanBeReadHoweverTheEveningIsGoing() = eachScheme { dark, scheme ->
        val found = shown(dark, "the public rooms") {
            DiscoverScreen(connector = listing(OPEN_TABLES), onJoin = {}, onBack = {})
        } + shown(dark, "the public rooms, nobody listed") {
            DiscoverScreen(connector = listing(emptyList()), onJoin = {}, onBack = {})
        } + shown(dark, "the public rooms, unreachable") {
            DiscoverScreen(connector = unreachable(), onJoin = {}, onBack = {})
        }
        judged(scheme, found)
    }

    @Test
    fun theTableCanBeRead() {
        val view = teachingSession().view.value
        eachScheme { dark, scheme ->
            judged(scheme, shown(dark, "the table during setup") { table(view, Question.None) })
        }
    }

    /** The rail with the most on it: a drawn card, and the rank grid asking for an answer. */
    @Test
    fun theRailAskingForARankCanBeRead() {
        val view = drawn()
        eachScheme { dark, scheme ->
            judged(
                scheme,
                shown(dark, "the rail asking for a rank") { table(view, Question.CallRank(0)) },
            )
        }
    }

    @Test
    fun theHelpSheetCanBeRead() = eachScheme { dark, scheme ->
        judged(
            scheme,
            shown(dark, "the help sheet") { HelpSheet(open = true, now = null, onDismiss = {}) },
        )
    }

    @Test
    fun theScoreSheetCanBeRead() = eachScheme { dark, scheme ->
        val hands = mapOf("p1" to 6, "p2" to 20, "p3" to 15, "p4" to 30)
        val found = shown(dark, "the score sheet") {
            StandingsSheet(
                open = true,
                round = 3,
                you = "p1",
                result = RoundResult(
                    callerId = "p1",
                    hands = hands,
                    points = roundPoints(hands, "p1"),
                    seats = SEATS,
                ),
                standings = emptyMap(),
                onNextRound = {},
                onQuit = {},
            )
        }
        judged(scheme, found)
    }

    // ------------------------------------------------------------------ the measuring

    /** Captures the screen as it stands and names every piece of text that cannot be read. */
    private fun ComposeUiTest.unreadableHere(what: String): List<String> {
        val image = onRoot(useUnmergedTree = true).captureToImage().toPixelMap()
        return textNodes().mapNotNull { unreadable(it, image) }.map { "  $what: $it" }
    }

    /** One verdict per scheme, carrying every screen's complaints rather than the first. */
    private fun judged(scheme: String, found: List<String>) = assertTrue(
        found.isEmpty(),
        "$scheme scheme — text that cannot be read:\n" + found.joinToString("\n"),
    )

    private fun ComposeUiTest.textNodes(): List<SemanticsNode> = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
            .or(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()

    /** Why this node cannot be read, or null for one that can (or that carries no claim). */
    private fun unreadable(node: SemanticsNode, image: PixelMap): String? {
        if (node.disabled()) return null
        val words = node.words() ?: return null
        val style = node.layout()?.layoutInput?.style ?: return null
        val ink = style.color
        if (ink.isUnspecified || ink.alpha == 0f) return null
        val grounds = node.grounds(image, ink) ?: return null

        // Over a gradient the pass has to hold at every stop the text crosses, not at the
        // average — which is how a white label on the top of a green gradient once shipped
        // at 3.1:1 ([ContrastTest] tells that story about the declared pair).
        val need = if (style.isLarge()) Wcag.UI else Wcag.TEXT
        val (worst, got) = grounds
            .map { it to Wcag.contrast(Wcag.over(ink, it), it) }
            .minBy { (_, ratio) -> ratio }
        if (got >= need) return null
        return "\"${words.take(SAID)}\" is ${Wcag.over(ink, worst).hex()} on ${worst.hex()} " +
            "at %.2f:1, and has to be %.1f:1".format(got, need)
    }

    /** SC 1.4.3 exempts text in an inactive control, and so does this. */
    private fun SemanticsNode.disabled(): Boolean = generateSequence(this) { it.parent }
        .any { it.config.getOrNull(SemanticsProperties.Disabled) != null }

    /**
     * What the node says — or null for a node making no textual claim: blank, or a glyph
     * with no letter or digit in it, which on this table is always furniture (the em-dash in
     * an empty pile slot, a tile's chevron) whose information is carried by the shape it sits
     * in rather than by reading it.
     */
    private fun SemanticsNode.words(): String? {
        val said = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
        return said?.takeIf { spoken -> spoken.any { it.isLetterOrDigit() } }
    }

    private fun SemanticsNode.layout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    /**
     * The colours the text is actually written over.
     *
     * The most common colour inside the bounds is not enough: over a gradient every scanline
     * is its own colour, so the text's hundreds of identical pixels win the vote and the node
     * measures as ink on ink. So pixels near the ink's own colour are set aside first — the
     * glyphs and their antialiased edges — and the rest are pooled into coarse buckets so a
     * gradient's neighbouring rows count together. Every bucket holding at least a tenth of
     * the ground is a colour the text must clear. If nothing survives the setting-aside, all
     * the ground there is looks like the ink, and the plain mode is the honest answer.
     *
     * The bounds are the clipped ones on purpose: text scrolled off the screen measures empty
     * and is skipped, because a screen is judged as it is shown.
     */
    private fun SemanticsNode.grounds(image: PixelMap, ink: Color): List<Color>? {
        val b = boundsInRoot
        val left = b.left.toInt().coerceAtLeast(0)
        val top = b.top.toInt().coerceAtLeast(0)
        val right = b.right.toInt().coerceAtMost(image.width)
        val bottom = b.bottom.toInt().coerceAtMost(image.height)
        if (right - left < SPAN || bottom - top < SPAN) return null

        val everything = HashMap<Color, Int>()
        val buckets = HashMap<Int, Bucket>()
        var kept = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = image[x, y]
                everything.merge(pixel, 1, Int::plus)
                if (pixel.near(ink)) continue
                kept++
                buckets.getOrPut(pixel.bucketKey()) { Bucket() }.add(pixel)
            }
        }
        if (kept == 0) return everything.maxByOrNull { it.value }?.key?.let { listOf(it) }

        val floor = kept * SHARE
        val main = buckets.values.filter { it.count >= floor }
            .ifEmpty { listOf(buckets.values.maxBy { it.count }) }
        return main.map { it.centre() }
    }

    /** A coarse colour cell, wide enough that a gradient's neighbouring rows land together. */
    private class Bucket {
        var count = 0
        private var r = 0f
        private var g = 0f
        private var b = 0f

        fun add(c: Color) {
            count++
            r += c.red
            g += c.green
            b += c.blue
        }

        fun centre(): Color = Color(r / count, g / count, b / count)
    }

    /** Close enough to the ink to be a glyph or its antialiased edge. */
    private fun Color.near(ink: Color): Boolean {
        val dr = red - ink.red
        val dg = green - ink.green
        val db = blue - ink.blue
        return dr * dr + dg * dg + db * db < NEAR2
    }

    private fun Color.bucketKey(): Int {
        val r = (red * QUANT).toInt()
        val g = (green * QUANT).toInt()
        val b = (blue * QUANT).toInt()
        return r shl BITS * 2 or (g shl BITS) or b
    }

    /**
     * WCAG's large-text line, at which 3:1 is enough: 18pt, or 14pt bold — which is 24sp and
     * 18.5sp on a screen. The test runs at fontScale 1, so sp and px agree.
     */
    private fun TextStyle.isLarge(): Boolean {
        val sp = fontSize.takeIf { it.isSp }?.value ?: return false
        if (sp >= LARGE_SP) return true
        val weight = (fontWeight ?: FontWeight.Normal).weight
        return sp >= LARGE_BOLD_SP && weight >= SEMI_BOLD
    }

    private fun Color.hex(): String = "#%06X".format(HEX_MASK and toArgb())

    // ------------------------------------------------------------------ the fixtures

    private fun eachScheme(check: (dark: Boolean, scheme: String) -> Unit) {
        check(false, "light")
        check(true, "dark")
    }

    /** One screen, composed the way [App] frames every screen: on the rail, phone-sized. */
    private fun shown(dark: Boolean, what: String, content: @Composable () -> Unit): List<String> {
        var found: List<String> = emptyList()
        runComposeUiTest {
            setContent {
                VintoTheme(dark = dark) {
                    Surface(color = Rail.fill) {
                        Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { content() }
                    }
                }
            }
            waitForIdle()
            found = unreadableHere(what)
        }
        return found
    }

    @Composable
    private fun table(view: PlayerView, question: Question) {
        TableScreen(
            state = TableState(view, tableFor(view, question), null, emptyList(), 1),
            layout = TableLayout.forScreen(PHONE_H),
            onMove = {},
            onHelp = {},
            onSettings = {},
            onReport = {},
            onDeck = {},
        )
    }

    private fun ComposeUiTest.press(label: String) {
        val node = onNodeWithContentDescription(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    /** A table with a card drawn and waiting, so the rail has a question to draw. */
    private fun drawn(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
        }
        return view
    }

    /** A connector that answers the listing and nothing else — this screen asks for no more. */
    private fun listing(rooms: List<PublicRoom>): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = refused()
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            refused()
        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = RoomAnswer.Ok(rooms)
    }

    private fun unreachable(): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = refused()
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            refused()
        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = refused()
    }

    private fun refused(): RoomAnswer.Failed =
        RoomAnswer.Failed(RoomTrouble.OFFLINE, "this test has no network")

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        const val SEED = 20_260_831L

        /** Anything thinner than this is a sliver, not a place text is read from. */
        const val SPAN = 2

        /** How much of the offending text a failure line quotes. */
        const val SAID = 40

        /** A ground has to cover this share of the non-ink pixels to be one the text is on. */
        const val SHARE = 0.1

        /** Squared RGB distance under which a pixel is the ink rather than the ground. */
        const val NEAR2 = 0.0225f

        /** Four bits a channel: coarse enough to pool a gradient, fine enough to keep hues. */
        const val QUANT = 15f
        const val BITS = 4

        const val LARGE_SP = 24f
        const val LARGE_BOLD_SP = 18.5f
        const val SEMI_BOLD = 600
        const val HEX_MASK = 0xFFFFFF

        val SEATS = listOf(
            "p1" to "You",
            "p2" to "Raphael",
            "p3" to "Michelangelo",
            "p4" to "Donatello",
        )

        val OPEN_TABLES = listOf(
            PublicRoom(code = "ABC234", hostNickname = "Raphael", humans = 2, seatsFilled = 3),
            PublicRoom(code = "DEF567", hostNickname = null, humans = 1, seatsFilled = 4),
        )
    }
}
