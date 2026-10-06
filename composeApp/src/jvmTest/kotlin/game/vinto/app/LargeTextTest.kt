package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import game.vinto.app.game.HelpSheet
import game.vinto.app.game.LocalStage
import game.vinto.app.game.RoomScreen
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.app.theme.breaksAWord
import game.vinto.client.MemoryVault
import game.vinto.client.Pace
import game.vinto.client.Question
import game.vinto.client.Settings
import game.vinto.client.Table
import game.vinto.client.TableMode
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The main screens at twice the font: photographed for a person, and read for cut words.
 *
 * Android 14 lets a reader scale text to 200%, and iOS Dynamic Type goes further. Nobody
 * developing the game has that switched on, so a word that fits at 1.0 and is sliced in half at
 * 2.0 is invisible from a desk. [RailFitsTest], [LobbyReachTest] and [TouchTargetTest] already
 * measure the *controls* at this size; this is the rest of the words.
 *
 * Each screen is drawn on the smallest phone still sold (360 x 640 dp) and on the phone the
 * suites are drawn on (411 x 740), at density 2 so the pictures can be read, and written to
 * `build/large-text/` for review. What a test can say about them is asserted:
 *
 * - **No word is cut silently.** A line past its `maxLines`, or a box too short for its lines,
 *   with no ellipsis to say so. An ellipsis is a decision, and is listed rather than failed.
 * - **No word is outside the screen or its box with nothing to scroll it into view.** Measured
 *   from the *clipped* bounds against the unclipped size, the way [RailFitsTest] measures its
 *   buttons, and forgiven only inside something that scrolls.
 *
 * What neither can see — a word drawn over another, a tile grown out of proportion — is what the
 * pictures are for.
 */
@OptIn(ExperimentalTestApi::class)
class LargeTextTest {

    /**
     * The home screen where it is shortest: an Android 7 phone at 540 x 960, 240 dpi, which is
     * 360 x 640 dp and 568 under its status and navigation bars. At the ordinary font size the
     * menu is meant to fit with its scroll inert, and on that phone the last row of buttons was
     * cut in half above the footer; at twice the size it scrolls, and nothing may be cut.
     */
    @Test
    fun theHomeScreenOnAnAndroid7Phone() = onEachPhone("home-android7", ANDROID_7) {
        app("home")
        snap("home")
        if (phone.fontScale == 1f) everyControlWhole("home")
    }

    @Test
    fun theHomeScreen() = onEachPhone("home") {
        app("home")
        snap("home")
        // The menu scrolls on a small phone at this size; its foot is the half with the buttons.
        test.onNodeWithContentDescription("Settings").performScrollTo()
        snap("home-foot")
    }

    @Test
    fun theWayIntoARoom() = onEachPhone("online") {
        app("lobby")
        snap("online")
        press("Open a room")
        snap("online-open")
        press("Back")
        press("Join with a code")
        snap("online-join")
    }

    @Test
    fun aLobby() = onEachPhone("lobby") {
        val room = stagedRoom(busyLobby())
        show { RoomScreen(room, Pace.STEADY, onSettings = {}, onLeft = {}) }
        snap("lobby")
        room.leave()
    }

    @Test
    fun theLesson() = onEachPhone("teach") {
        app("teach")
        snap("teach")
    }

    @Test
    fun aRoundInProgress() = onEachPhone("table") {
        app("table")
        waitFor("DRAW")
        snap("table")
    }

    @Test
    fun aFinishedRound() = onEachPhone("score") {
        app("score")
        waitFor("game")
        // The sheet covers the felt, whose words are read where nothing covers them: aScoredTable.
        snap("score", sheetOnly = true)
    }

    /**
     * The felt under the score sheet: every hand face up, a total in each seat's home and the
     * claims refereed. Drawn without the sheet, which covers it in the round's own ending.
     */
    @Test
    fun aScoredTable() {
        val view = runBlocking { stagedGame(MemoryVault(), toTheEnd = true).session.view.value }
        onEachPhone("scored") {
            show {
                BoxWithConstraints {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(maxHeight),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                    )
                }
            }
            snap("scored")
        }
    }

    @Test
    fun thePlanOnTheFelt() {
        // Staged as `ScreenshotTest` stages it rather than through the capture scene, which
        // plays a dozen turns of real search to reach a bot's call and does not settle under a
        // test clock.
        val (view, table) = aStandingPlan()
        onEachPhone("plan") {
            show {
                CompositionLocalProvider(LocalStage provides Stage().apply { mode = TableMode.PLAN }) {
                    BoxWithConstraints {
                        TableScreen(
                            state = TableState(view, table, null, emptyList(), 1),
                            layout = TableLayout.forScreen(maxHeight),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
            snap("plan")
        }
    }

    /** `ScreenshotTest`'s plan: a bot's call, a claim at each other seat, one lane drawn. */
    private fun aStandingPlan(): Pair<PlayerView, Table> {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        val view = whole.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            players = whole.players.map { seat ->
                if (seat.id == caller.id) {
                    seat.copy(isVintoCaller = true)
                } else {
                    seat.copy(claims = listOf(Claim(seat.id, listOf(0), listOf(Rank.FIVE))))
                }
            },
        )
        val mate = view.players.first { it.id != view.viewerId && it.id != caller.id }
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Swap(CardAt(mate.id, 0), CardAt(view.viewerId, 0)))),
        )
        return view to tableFor(view, question = Question.ThePlan(), plan = plan)
    }

    @Test
    fun theSettings() = onEachPhone("settings") {
        SettingsPage.entries.forEach { page ->
            show {
                SettingsScreen(
                    build = "000",
                    version = "1.0",
                    settings = Settings(),
                    canForget = true,
                    page = page,
                    onOpen = {},
                    onChange = {},
                    onForget = {},
                    onBack = {},
                )
            }
            snap("settings-${page.name.lowercase()}")
            if (page == SettingsPage.GAME) {
                press("Language: Follow the device")
                snap("settings-language")
            }
        }
    }

    @Test
    fun theHelpSheet() = onEachPhone("help") {
        show { HelpSheet(open = true, now = null, left = DECK_LEFT, onDismiss = {}) }
        snap("help-cards")
        listOf("RINGS", "BADGES", "MORE").forEach { tab ->
            test.onNodeWithText(tab).performClick()
            snap("help-${tab.lowercase()}")
        }
    }

    // ------------------------------------------------------------------ the drawing

    private data class Phone(val name: String, val wide: Int, val high: Int, val fontScale: Float = FONT_SCALE)

    /** One run of [walk] on each phone, and one verdict over everything it photographed. */
    private fun onEachPhone(scene: String, phones: List<Phone> = PHONES, walk: Shots.() -> Unit) {
        val found = mutableListOf<String>()
        val listed = mutableListOf<String>()
        phones.forEach { phone ->
            runDesktopComposeUiTest(phone.wide * SCALE, phone.high * SCALE) {
                val shots = Shots(this, phone)
                shots.walk()
                found += shots.found
                listed += shots.listed
            }
        }
        val report = File(OUT, "$scene.txt")
        report.parentFile.mkdirs()
        val (owners, cut) = found.partition { finding -> OWNER.any { it.containsMatchIn(finding) } }
        report.writeText(
            (cut.map { "CUT   $it" } + owners.map { "OWNER $it" } + listed.map { "NOTE  $it" })
                .joinToString("\n", postfix = "\n"),
        )
        assertTrue(cut.isEmpty(), "at twice the font, $scene loses words:\n" + cut.joinToString("\n"))
    }

    private inner class Shots(val test: ComposeUiTest, val phone: Phone) {
        val found = mutableListOf<String>()
        val listed = mutableListOf<String>()

        fun show(content: @Composable () -> Unit) {
            test.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(SCALE.toFloat(), phone.fontScale),
                    // A phone, not the desktop the JVM's own `actual` reports: the header picks
                    // its shape from the host, as `StoreShotsTest` records.
                    LocalHost provides Host.PHONE,
                ) {
                    VintoTheme(dark = false) {
                        Box(modifier = Modifier.size(phone.wide.dp, phone.high.dp)) { content() }
                    }
                }
            }
            test.waitForIdle()
        }

        fun app(scene: String) = show { App(seeds = { SEED }, vault = MemoryVault(), marketing = scene) }

        fun waitFor(words: String) = test.waitUntil(timeoutMillis = STAGE_TIMEOUT_MS) {
            test.onAllNodesWithText(words, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        fun press(label: String) {
            val node = test.onNodeWithContentDescription(label)
            if (!node.isDisplayed()) node.performScrollTo()
            node.performClick()
            test.waitForIdle()
        }

        /** Every control on the screen as it opens is wholly on it, unscrolled. */
        fun everyControlWhole(name: String) {
            val shot = "$name-${phone.name}"
            val screen = Rect(0f, 0f, (phone.wide * SCALE).toFloat(), (phone.high * SCALE).toFloat())
            test.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .forEach { node ->
                    val shown = node.boundsInRoot.intersect(screen)
                    if (shown.height < node.size.height - EDGE || shown.width < node.size.width - EDGE) {
                        val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                        found += "$shot: the control \"$name\" shows ${shown.height.toInt()} of " +
                            "${node.size.height} without scrolling"
                    }
                }
        }

        /** Writes the screen as it stands and reads every word on it. */
        fun snap(name: String, sheetOnly: Boolean = false) {
            test.waitForIdle()
            val shot = "$name-${phone.name}"
            val bitmap = test.onRoot().captureToImage().asSkiaBitmap()
            val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)
                ?: error("$shot did not encode")
            File(OUT, "$shot.png").apply { parentFile.mkdirs() }.writeBytes(png.bytes)

            val screen = Rect(0f, 0f, (phone.wide * SCALE).toFloat(), (phone.high * SCALE).toFloat())
            val sheet = if (sheetOnly) sheet() else null
            test.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .filter { node -> sheet == null || node.inside(sheet) }
                .forEach { node -> read(shot, node, screen) }
        }

        /** The open sheet: what holds its close button, which the scrim under it shares a name with. */
        private fun sheet(): Int {
            val close = test.onAllNodes(hasContentDescription("Close"), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .first { it.size.width < phone.wide * SCALE }
            return checkNotNull(close.parent) { "the sheet's close button has no parent" }.id
        }

        private fun SemanticsNode.inside(id: Int): Boolean {
            var at: SemanticsNode? = parent
            while (at != null) {
                if (at.id == id) return true
                at = at.parent
            }
            return false
        }

        private fun read(shot: String, node: SemanticsNode, screen: Rect) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: return
            val words = layout.layoutInput.text.text.replace('\n', ' ')
            if (words.isBlank() || node.size.width == 0 || node.size.height == 0) return

            when {
                layout.cutShort() && layout.ellipsizes() -> listed += "$shot: \"$words\" ends in an ellipsis"
                // A mark drawn with a glyph — the back chevron, the re-roll arrow — sits in a tap
                // target rather than on a line, and its line box is taller than its ink. Whether
                // the ink is whole is a question for the pictures; the line overflowing is not.
                layout.cutShort() && words.isMark() ->
                    listed += "$shot: the mark \"$words\" has a line taller than its box"
                layout.cutShort() -> found += "$shot: \"$words\" is cut off (${layout.lineCount} lines drawn)"
                layout.breaksAWord() -> found += "$shot: \"$words\" is broken inside a word"
            }

            // Against the node's own rectangle in the root, unclipped, rather than its size: a
            // seat's name on the side of the felt is turned a quarter, and its size is not.
            val coordinates = node.layoutInfo.coordinates
            val drawn = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
            val shown = node.boundsInRoot.intersect(screen)
            val whole = shown.width >= drawn.width - EDGE && shown.height >= drawn.height - EDGE
            if (!whole && !node.scrolls()) {
                found += "$shot: \"$words\" shows ${shown.width.toInt()}x${shown.height.toInt()} " +
                    "of ${drawn.width.toInt()}x${drawn.height.toInt()}, with nothing to scroll it into view"
            }
        }
    }

    /**
     * Whether a cut is drawn as one. Asked of the text's own setting rather than of
     * `isLineEllipsized`, which answers false on this platform for an ellipsis that is on screen.
     */
    private fun TextLayoutResult.ellipsizes(): Boolean = layoutInput.overflow == TextOverflow.Ellipsis

    /** One glyph that is not a letter or a digit: a drawn mark rather than a word. */
    private fun String.isMark(): Boolean = trim().length == 1 && !trim().first().isLetterOrDigit()

    /**
     * Lines past `maxLines`, a box too short for them, or characters the last line never reached
     * — trailing spaces excepted, which no line is said to reach.
     */
    private fun TextLayoutResult.cutShort(): Boolean {
        val last = lineCount - 1
        val unreached = getLineEnd(last, visibleEnd = true) < layoutInput.text.trimEnd().length
        // The box ends above the last line's baseline: letters lost, not the space under them. A
        // line box a couple of points taller than its slot trims only its own leading.
        val sliced = getLineBaseline(last) > size.height + 1
        return multiParagraph.didExceedMaxLines || unreached || sliced
    }

    /** Whether anything this node sits in can scroll, which is how a word below the fold is reached. */
    private fun SemanticsNode.scrolls(): Boolean {
        var at: SemanticsNode? = this
        while (at != null) {
            val config = at.config
            if (config.getOrNull(SemanticsActions.ScrollBy) != null ||
                config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
                config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            ) {
                return true
            }
            at = at.parent
        }
        return false
    }

    private companion object {
        /** The smallest phone still sold, and the one the suites are drawn on. */
        val PHONES = listOf(Phone("360x640", 360, 640), Phone("411x740", 411, 740))

        /** 540 x 960 at 240 dpi, under Android 7's bars: the ordinary font, then the doubled one. */
        val ANDROID_7 = listOf(Phone("360x568-1.0", 360, 568, fontScale = 1f), Phone("360x568", 360, 568))

        /** Android 14's largest step, and short of where iOS Dynamic Type stops. */
        const val FONT_SCALE = 2f

        /** Pixels per dp in the pictures: enough to read a 13 sp word at a glance. */
        const val SCALE = 2

        /** A pixel of rounding each way, in picture pixels. */
        const val EDGE = 2f

        const val SEED = 20_260_903L
        const val DECK_LEFT = 21

        /** Long enough for a staged round to play out; short enough to fail rather than hang. */
        const val STAGE_TIMEOUT_MS = 45_000L

        val OUT = File("build/large-text")

        /**
         * What these pictures found that is the owner's to decide, because the fix is a layout and
         * not a line: listed in each scene's report rather than failed, and only these.
         *
         * A bot's seat tile on a 360 dp phone has about 35 dp for its name between the face and
         * the cross that removes it, so at this size a one-word name breaks inside the word even
         * at the tile's floor. One seat to a row while the text is this large would give it room;
         * that is a change to the lobby's shape.
         *
         * The plan's small numerals — the turn in each stop's circle, a card's place at the foot of
         * the cards in a sentence — are 8 sp on marks drawn in dp, and their line is the paragraph
         * height of the default style, which is taller than the mark at every font size. They are
         * already mis-set at 1.0 (the stops' digits sit cut at the foot of their circles in
         * `plan-light.png`) and gone at 2.0. A line as tall as the digit fixes both, and moves
         * those digits in the golden, so it is the owner's to look at.
         */
        val OWNER = listOf(
            Regex("""lobby-360x640: "Tide" is broken inside a word"""),
            Regex("""plan-\d+x\d+: "\d" is cut off"""),
        )
    }
}
