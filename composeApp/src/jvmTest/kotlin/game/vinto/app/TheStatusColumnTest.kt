package game.vinto.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.ChoiceButton
import game.vinto.app.game.ChoicesAt
import game.vinto.app.game.ColumnRun
import game.vinto.app.game.choicesAt
import game.vinto.app.game.columnOrder
import game.vinto.app.game.pillOf
import game.vinto.app.game.pillSaid
import game.vinto.app.game.pillsThatFit
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Choice
import game.vinto.client.Label
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Table
import game.vinto.client.tableFor
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The closed iPhone Duo's status column, in the owner's order (2026-10-13): the way out at the top,
 * under the clock and the camera; the rules and the settings; the round and the turn; the plan's
 * switch in a final round; and the turn's choices at the foot, under the thumb, as pills — the one
 * most often taken lowest, Call Vinto always the highest. Pickers stay in the rail, and so does any
 * set the column cannot hold whole.
 */
@OptIn(ExperimentalTestApi::class)
class TheStatusColumnTest {

    @Test
    fun theColumnRunsFromTheWayOutDownToThePillsUnderTheThumb() =
        runDesktopComposeUiTest(Duos.CLOSED_W, Duos.CLOSED_H) {
            val (view, said) = Duos.drawn()
            showTable(view, said, Duos.closed())

            val order = listOf(LEAVE, RULES, SETTINGS).map { whereIs(it) } +
                onAllNodes(hasText(COUNTER, substring = true)).fetchSemanticsNodes().single().boundsInRoot +
                listOf(SWAP, DISCARD).map { whereIs(it) }
            order.forEach { at ->
                assertTrue(at.left >= Duos.CLOSED_COLUMN_LEFT - EDGE, "$at is not in the status column")
            }
            assertTrue(order.first().top >= Duos.CAMERA_FOOT, "the way out is up among the clock and the camera")
            order.zipWithNext().forEach { (above, below) ->
                assertTrue(above.bottom <= below.top + EDGE, "out of order: $above stands over $below")
            }
            assertTrue(order.last().bottom <= Duos.CLOSED_H, "Discard is under the foot of the screen")
            // And there is room between the counter and the pills: they stand at the foot, not under it.
            assertTrue(order[4].top - order[3].bottom > GAP_TO_PILLS, "the pills are not at the foot")
        }

    @Test
    fun theRailDrawsNoChoicesWhileTheColumnHoldsThem() =
        runDesktopComposeUiTest(Duos.CLOSED_W, Duos.CLOSED_H) {
            val (view, said) = Duos.drawn()
            showTable(view, said, Duos.closed())
            // One of each, and it is the column's: the rail beside the felt draws none of its own.
            listOf(SWAP, DISCARD).forEach { name ->
                val found = nodesNamed(name)
                assertEquals(1, found.size, "$name is drawn ${found.size} times")
                assertTrue(found.single().boundsInRoot.left >= Duos.CLOSED_COLUMN_LEFT - EDGE, "$name is in the rail")
            }
        }

    @Test
    fun aKingsRanksStayInTheRailAndTheColumnKeepsItsOwnControls() =
        runDesktopComposeUiTest(Duos.CLOSED_W, Duos.CLOSED_H) {
            val view = Duos.kingNaming()
            assertEquals(Rank.entries.size, tableFor(view).ranks.size, "the fixture is not naming a King's rank")
            showTable(view, emptyList(), Duos.closed())

            val inColumn = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                .fetchSemanticsNodes()
                .filter { it.boundsInRoot.left >= Duos.CLOSED_COLUMN_LEFT - EDGE }
                .map { it.calledAloud() }
            assertEquals(listOf(LEAVE, RULES, SETTINGS), inColumn, "the column holds more than its controls")
            (Rank.entries.map { it.serialName } + PUT_DOWN).forEach { name ->
                assertTrue(
                    whereIs(name).right <= Duos.CLOSED_COLUMN_LEFT + EDGE,
                    "$name left the rail: the King's picker and its way out stand together",
                )
            }
        }

    @Test
    fun pillsStandDiscardLowestThenSwapThenUseTheAction() {
        val drawn = listOf(choice(Label.UseAction), choice(Label.SwapCards), choice(Label.Discard))
        assertEquals(listOf(Label.UseAction, Label.SwapCards, Label.Discard), columnOrder(drawn).map { it.label })
        // Whatever order the model gives them in.
        assertEquals(
            listOf(Label.UseAction, Label.SwapCards, Label.Discard),
            columnOrder(drawn.reversed()).map { it.label },
        )
        assertEquals(
            listOf(Label.UseFromPile(Rank.QUEEN), Label.DrawCard),
            columnOrder(listOf(choice(Label.DrawCard), choice(Label.UseFromPile(Rank.QUEEN)))).map { it.label },
        )
    }

    @Test
    fun callVintoIsAlwaysTheHighestPill() {
        listOf(
            listOf(choice(Label.Continue), choice(Label.CallVinto)),
            listOf(choice(Label.CallVinto), choice(Label.Continue)),
        ).forEach { window ->
            assertEquals(Label.CallVinto, columnOrder(window).first().label, "Call Vinto under a resting thumb")
        }
    }

    @Test
    fun aPickerKeepsItsWholeSetInTheRail() {
        val (view, _) = Duos.drawn()
        // Naming what a swap puts down: the action ranks, and Just Swap beside them.
        val naming = tableFor(view, Question.CallRank(0))
        assertTrue(naming.choices.any { it.label == Label.JustSwap })
        assertEquals(ChoicesAt.RAIL, choicesAt(naming, fit = FITS))
        // The King's fourteen and their way out.
        assertEquals(ChoicesAt.RAIL, choicesAt(tableFor(Duos.kingNaming()), fit = FITS))
        // And the ordinary turn goes to the column.
        assertEquals(ChoicesAt.COLUMN, choicesAt(tableFor(view), fit = FITS))
    }

    @Test
    fun aSetTheColumnCannotHoldWholeStaysWholeInTheRail() {
        val (view, _) = Duos.drawn()
        val drawn = tableFor(view)
        assertEquals(ChoicesAt.RAIL, choicesAt(drawn, fit = drawn.choices.size - 1), "a set is never split")
        // A choice with no pill — the coalition's words — keeps its whole set in the rail too.
        val talk = Table(
            prompt = drawn.prompt,
            choices = listOf(choice(Label.ThatsAllIKnow), choice(Label.Discard)),
        )
        assertEquals(ChoicesAt.RAIL, choicesAt(talk, fit = FITS))
    }

    @Test
    fun theClosedDuoHoldsFourPillsAndThreeBesideThePlansSwitch() {
        val run = ColumnRun(Duos.COLUMN.dp, onRight = true, top = 158.dp, bottom = 650.dp)
        assertEquals(run, Duos.closed().run, "the closed Duo's column runs from under its camera to its foot")
        assertEquals(4, pillsThatFit(run, plan = false, fontScale = 1f))
        assertEquals(3, pillsThatFit(run, plan = true, fontScale = 1f))
        // A doubled system font grows the counter and the switch's word: four still stand under the
        // counter alone, and beside the switch a pill gives way.
        assertEquals(4, pillsThatFit(run, plan = false, fontScale = 2f))
        assertEquals(2, pillsThatFit(run, plan = true, fontScale = 2f))
    }

    /**
     * WCAG 2.5.3: the name a pill is spoken by holds the word it shows, so somebody who reads DISCARD
     * can say "Discard" to Voice Control. Every choice that has a pill, drawn as one.
     */
    @Test
    fun everyPillsWordIsInTheNameItIsSpokenBy() = runDesktopComposeUiTest(WIDE, TALL) {
        val pilled = PILLED.filter { pillOf(it) != null }
        assertEquals(PILLED.size, pilled.size, "a choice of an ordinary turn lost its pill")
        setContent {
            VintoTheme {
                Column {
                    pilled.forEach { label -> ChoiceButton(choice(label), {}, pill = pillOf(label)) }
                }
            }
        }
        waitForIdle()

        val pills = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertEquals(pilled.size, pills.size)
        pills.forEach { pill ->
            val shown = pill.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            val spoken = pill.calledAloud()
            assertTrue(shown.isNotBlank(), "a pill with no word on it: $spoken")
            assertTrue(spoken.contains(shown, ignoreCase = true), "the pill shows ‘$shown’ and is called ‘$spoken’")
        }
    }

    /** And where a translation's name leaves the pill's word out, the word is said first rather than lost. */
    @Test
    fun aNameWithoutThePillsWordIsSaidAfterIt() {
        assertEquals("Swap Cards", pillSaid("Swap", "Swap Cards"))
        assertEquals("Swap, Karten tauschen", pillSaid("Swap", "Karten tauschen"))
    }

    private fun choice(label: Label) = Choice(label, Move.Ask(Question.None))

    private companion object {
        const val LEAVE = "Leave the table"
        const val RULES = "Rules"
        const val SETTINGS = "Settings"

        /** The round's counter, whatever turn the fixture is on. */
        const val COUNTER = "R1 / T"
        const val SWAP = "Swap Cards"
        const val DISCARD = "Discard"
        const val PUT_DOWN = "Put it down"

        /** As many pills as any set has. */
        const val FITS = 4

        const val EDGE = 1f

        /** More than the column's own gap: room, not just spacing. */
        const val GAP_TO_PILLS = 40f

        const val WIDE = 400
        const val TALL = 1400

        /** Every choice of an ordinary turn, each of which the column can draw. */
        val PILLED = listOf(
            Label.DrawCard,
            Label.UseFromPile(Rank.QUEEN),
            Label.UseAction,
            Label.SwapCards,
            Label.KeepIt,
            Label.Discard,
            Label.JustSwap,
            Label.PutItDown,
            Label.LeaveThem,
            Label.Continue,
            Label.CallVinto,
            Label.Done,
            Label.StartRound,
            Label.Back,
        )
    }
}
