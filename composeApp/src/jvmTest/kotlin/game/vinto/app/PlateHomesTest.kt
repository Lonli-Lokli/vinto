package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.NameRun
import game.vinto.app.game.SeatBadge
import game.vinto.app.game.SeatPlate
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.game.faceTag
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.shapes.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every mark on a seat plate has one home, and a plate is one size whatever it is wearing.
 *
 * The marks used to be a list: a row under the name on the plates above and below the felt, a
 * column under the name at the sides. A list grows as marks arrive, so the final round — when a
 * seat calls, three join the coalition and they start agreeing to a plan — made the plates
 * taller exactly when the table was fullest, and the side seats' cards paid for it by sliding
 * onto each other. And the side plates spent their length on a column of single marks that the
 * turned name then had to shrink to fit beside.
 *
 * So there are four homes, each in space the plate already leaves empty, and marks that can never
 * be seen together share one:
 *
 * - **who plays** — a bot, or a person away with a bot covering (away already means a bot);
 * - **their part in the round** — the crown, or a nod to the plan (the caller never nods);
 * - **the throw-in** — a planned throw, or barred (a barred seat cannot throw), or the hand's
 *   total once the round is scored;
 * - **now** — the table is waiting on this seat.
 *
 * Above and below the felt the homes sit either side of the face, two to a side, and two marks
 * stacked are one face tall. At the sides they run alongside the turned name on the table side,
 * in that order from the face outward.
 */
@OptIn(ExperimentalTestApi::class)
class PlateHomesTest {

    @Test
    fun aPlateIsOneSizeWhateverItIsWearing() {
        for (portrait in PORTRAITS) {
            for (run in NameRun.entries) {
                for (name in NAMES) {
                    val sizes = WEARING.map { wearing -> wearing to plate(portrait, run, name, wearing) }
                    val bare = sizes.first().second
                    sizes.forEach { (wearing, size) ->
                        assertEquals(
                            bare,
                            size,
                            "$name's $run plate at $portrait is $size wearing $wearing and $bare wearing nothing",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aboveAndBelowTheFeltTheHomesAreEitherSideOfTheFace() = runComposeUiTest {
        show(
            NameRun.ACROSS,
            Wearing(listOf(SeatBadge.AWAY, SeatBadge.BOT, SeatBadge.VINTO, SeatBadge.BARRED), thinking = true),
        )
        val face = onNodeWithTag(faceTag(NAME), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val part = described(CALLED)
        val who = described(AWAY)
        val now = described(WAITING)
        val toss = described(BARRED)

        assertTrue(
            part.center.x < face.left && part.center.y < face.center.y,
            "the crown is not top-left of the face: $part, $face",
        )
        assertTrue(
            who.center.x < face.left && who.center.y > face.center.y,
            "who plays is not bottom-left: $who, $face",
        )
        assertTrue(now.center.x > face.right && now.center.y < face.center.y, "now is not top-right: $now, $face")
        assertTrue(
            toss.center.x > face.right && toss.center.y > face.center.y,
            "the throw-in is not bottom-right: $toss, $face",
        )
        // One mark for a person away, and it says both facts: nobody is there, and a bot is playing.
        assertEquals(
            listOf(who),
            allDescribed(BOT),
            "a person away wore a second mark for the bot, or none that says so",
        )
    }

    @Test
    fun atTheSidesTheHomesRunBesideTheNameInOrderFromTheFace() {
        for (run in listOf(NameRun.DOWN, NameRun.UP)) {
            runComposeUiTest {
                show(run, Wearing(listOf(SeatBadge.BOT, SeatBadge.AGREED, SeatBadge.WILL_SHED), thinking = true))
                val face = onNodeWithTag(faceTag(NAME), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val name = onAllNodes(saying(NAME), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
                val homes = listOf(described(WAITING), described(AGREED), described(A_BOT), described(SHEDDING))

                // On the table side of the name: left of it on the right-hand seat, right of it on the left.
                homes.forEach { home ->
                    val tableSide = if (run ==
                        NameRun.DOWN
                    ) {
                        home.center.x < name.center.x
                    } else {
                        home.center.x > name.center.x
                    }
                    assertTrue(tableSide, "$run: a mark at $home is not on the table side of the name at $name")
                }
                // And in order from the face: now, part in the round, who plays, the throw-in.
                val away = homes.map { kotlin.math.abs(it.center.y - face.center.y) }
                assertEquals(away.sorted(), away, "$run: the homes are not in order from the face: $homes")
                // Alongside the name, not in a column of their own after it.
                homes.forEach { home ->
                    assertTrue(
                        home.center.y in name.top..name.bottom,
                        "$run: a mark at $home is not beside the name at $name",
                    )
                }
            }
        }
    }

    @Test
    fun theScoreIsInTheThrowInsHomeOnceTheRoundIsScored() = runComposeUiTest {
        show(NameRun.ACROSS, Wearing(listOf(SeatBadge.VINTO, SeatBadge.BARRED), score = "12"))
        val face = onNodeWithTag(faceTag(NAME), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val score = onAllNodes(saying("12"), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        assertTrue(
            score.center.x > face.right && score.center.y > face.center.y,
            "the score is not bottom-right: $score, $face",
        )
        assertEquals(0, allDescribed(BARRED).size, "the bar was still drawn once the round was scored")
    }

    /**
     * No seat wears the coalition's link. With four seats every seat but the caller's is in the
     * coalition, and the caller wears the crown and the gold edge: three links said nothing the
     * crown had not, and they were half of what made the final round's plates taller.
     */
    @Test
    fun theFinalRoundDrawsNoCoalitionLink() = runComposeUiTest {
        val dealt = teachingSession().view.value
        val caller = dealt.players.first { it.id != dealt.viewerId }
        val coalition = dealt.players.map { it.id } - caller.id
        val view = dealt.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            players = dealt.players.map { seat ->
                if (seat.id == caller.id) {
                    seat.copy(
                        isVintoCaller = true,
                    )
                } else {
                    seat.copy(coalitionWith = coalition - seat.id)
                }
            },
        )
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(411.dp, 740.dp)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1, teaching = true),
                        layout = TableLayout.forScreen(740.dp),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                    )
                }
            }
        }
        waitForIdle()
        assertEquals(1, allDescribed(CALLED).size, "the caller's crown is missing")
        assertEquals(0, allDescribed(IN_THE_COALITION).size, "a seat still wears the coalition's link")
    }

    // ------------------------------------------------------------------ the fixtures

    private data class Wearing(
        val badges: List<SeatBadge> = emptyList(),
        val thinking: Boolean = false,
        val score: String? = null,
    ) {
        override fun toString() = (
            badges.map { it.name } + listOfNotNull(
                "THINKING".takeIf { thinking },
                score,
            )
            ).toString()
    }

    private fun ComposeUiTest.show(run: NameRun, wearing: Wearing) {
        setContent {
            VintoTheme {
                CompositionLocalProvider(LocalStage provides Stage()) {
                    Box(modifier = Modifier.size(400.dp, 400.dp)) {
                        SeatPlate(
                            name = NAME,
                            active = false,
                            badges = wearing.badges,
                            thinking = wearing.thinking,
                            marks = wearing.score,
                            size = 30.dp,
                            run = run,
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    /** One plate's laid-out size, which is what the cards beside it are pitched from. */
    private fun plate(portrait: Dp, run: NameRun, name: String, wearing: Wearing): IntSize {
        var size = IntSize.Zero
        runComposeUiTest {
            setContent {
                VintoTheme {
                    Column(modifier = Modifier.size(600.dp, 600.dp)) {
                        SeatPlate(
                            name = name,
                            active = false,
                            modifier = Modifier.onSizeChanged { size = it },
                            badges = wearing.badges,
                            thinking = wearing.thinking,
                            marks = wearing.score,
                            size = portrait,
                            run = run,
                        )
                    }
                }
            }
            waitForIdle()
        }
        return size
    }

    private fun ComposeUiTest.allDescribed(words: String): List<Rect> =
        onAllNodes(
            SemanticsMatcher("described as $words") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == words } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun ComposeUiTest.described(words: String): Rect =
        allDescribed(words).singleOrNull() ?: error("no single mark described as \"$words\": ${allDescribed(words)}")

    private fun saying(words: String) = SemanticsMatcher("says $words") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == words } == true
    }

    private companion object {
        const val NAME = "Patient Sparrow"
        val NAMES = listOf("Tide", "Patient Sparrow")

        /** The four `TableSizes` portraits, from a short phone to a desktop window. */
        val PORTRAITS = listOf(30.dp, 38.dp, 50.dp, 66.dp)

        val WEARING = listOf(
            Wearing(),
            Wearing(listOf(SeatBadge.BOT)),
            Wearing(listOf(SeatBadge.VINTO)),
            Wearing(listOf(SeatBadge.AGREED, SeatBadge.WILL_SHED)),
            Wearing(listOf(SeatBadge.AWAY, SeatBadge.BOT, SeatBadge.VINTO, SeatBadge.BARRED), thinking = true),
            Wearing(listOf(SeatBadge.BOT, SeatBadge.AGREED, SeatBadge.WILL_SHED), thinking = true),
            Wearing(listOf(SeatBadge.VINTO), score = "12"),
        )

        // What each mark says aloud, from `values/strings.xml`.
        const val CALLED = "called Vinto"
        const val AWAY = "away from the table"
        const val A_BOT = "a bot"
        const val BOT = A_BOT
        const val WAITING = "the table is waiting on this seat"
        const val BARRED = "cannot toss in again"
        const val AGREED = "agreed to the plan"
        const val SHEDDING = "has a card ready to throw in"
        const val IN_THE_COALITION = "in the coalition"
    }
}
