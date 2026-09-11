package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CardRef
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Saying what you hold, pointed at on the table rather than described in the rail.
 *
 * The report this answers is one sentence: *"not clear how I should declare mine or other
 * cards"*. The table said "Tap one of your cards to say what you think it is" while the primary
 * button read **DRAW CARD**, and nothing on the felt marked which cards that sentence was about
 * — the instruction was in the rail and the thing it pointed at was on the table.
 */
@OptIn(ExperimentalTestApi::class)
class ClaimsOnTheFeltTest {

    @Test
    fun exactlyTheCardsAMemberMayClaimAboutAreTheOnesTheFeltOffers() = runComposeUiTest {
        // "Ringed" is not a colour a test can read; what the ring is *driven by* is whether the
        // card answers a touch, and that is the same thing a screen reader is told. So this
        // checks the offer, card by card, over the whole table.
        val view = conferring()
        val table = tableFor(view)
        assertTrue(table.taps.isNotEmpty(), "the fixture is not a window anybody can talk in")

        show(view)

        for (seat in view.players) {
            for (position in seat.cards.indices) {
                val ref = CardRef(seat.id, position)
                val touchable = hasClickAction().matches(fetchNode(view, ref))
                assertEquals(
                    ref in table.taps,
                    touchable,
                    "${label(view, ref)} is offered on the felt and not by the table, or the other way about",
                )
            }
        }
    }

    @Test
    fun theInstructionGoesWhenThereIsNothingToPointAt() = runComposeUiTest {
        // An instruction to tap a card, shown where no card can be tapped, sends somebody
        // hunting for a control that is not there.
        val quiet = teachingSession().view.value

        val words = textsOn(quiet)

        assertTrue(
            words.none { it.contains("Tap one of", ignoreCase = true) },
            "the table asked for a claim outside a final round: $words",
        )
        assertTrue(
            tableFor(quiet).taps.keys.all { it.playerId == quiet.viewerId },
            "cards were claimable outside a final round",
        )
    }

    @Test
    fun theInstructionIsThereWhileThereIsSomethingToPointAt() = runComposeUiTest {
        val words = textsOn(conferring())

        assertTrue(
            words.any { it.contains("say what you think it is", ignoreCase = true) },
            "the window says nothing about the cards it has just ringed: $words",
        )
    }

    @Test
    fun aClaimMadeOnTheFeltIsTheClaimTheRailWouldHaveMade() {
        // The felt is another way in, not another claim: the tap opens the same picker, and the
        // picker sends the same `GameAction` it always sent.
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId }
        val ref = CardRef(mate.id, 0)

        val fromTheFelt = tableFor(view).taps[ref]
        assertEquals(
            Move.Ask(Question.Claiming(mate.id, listOf(0))),
            fromTheFelt,
            "a tap on a card does not open the claim for that card",
        )

        // And the picker, carried to its end, produces one send — the same one from either path.
        val picking = Question.Claiming(mate.id, listOf(0))
        val ranked = tableFor(view, question = picking).ranks.first { it.rank == Rank.QUEEN }.move
        assertTrue(ranked is Move.Send, "naming a rank did not send the claim: $ranked")
    }

    // ------------------------------------------------------------------ fixtures

    private fun ComposeUiTest.fetchNode(view: PlayerView, ref: CardRef) =
        onNodeWithContentDescription(label(view, ref), substring = true).fetchSemanticsNode()

    private fun label(view: PlayerView, ref: CardRef): String {
        val seat = view.players.first { it.id == ref.playerId }
        return "${seat.nickname}, card ${ref.position + 1}"
    }

    /** A final round somebody else called, with this seat's confer window open. */
    private fun conferring(): PlayerView {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        return whole.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            conferMsRemaining = WINDOW_MS,
        )
    }

    private fun ComposeUiTest.textsOn(view: PlayerView): List<String> {
        show(view)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    }

    private fun ComposeUiTest.show(view: PlayerView) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(view, tableFor(view), null, emptyList(), 1),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        /** Long enough that the window is open rather than closing. */
        const val WINDOW_MS = 20_000L
    }
}
