package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The final round says in words who is playing for whom.
 *
 * Vinto being called is the one moment the rules change under the player: three opponents
 * become one hand, only that hand is compared, and nobody may touch the caller's cards. The
 * table said all of that in colour — a blue ring on the coalition, gold on the caller — which
 * works only for a player who remembers the legend, at the point in the game where they have
 * least attention to spare for remembering it.
 *
 * Two chairs, two sentences. There were four: the coalition used to nominate a member to play
 * its hand, so the strip named a leader, addressed them differently from the rest, and had a
 * fourth line for the window before they were chosen. Only the lowest hand counts, whoever
 * holds it, so there is nobody to nominate and the coalition reads one line from the call to
 * the score.
 *
 * The strip also carries a line of portraits saying who is on which side. Those are checked by
 * their spoken description rather than by their text, because they have none — which is the
 * property that matters: a row of four faces with no label is a legend a screen reader cannot
 * read at all.
 */
@OptIn(ExperimentalTestApi::class)
class CoalitionLineTest {

    @Test
    fun theCallerIsToldTheTableHasClosedRanksAgainstThem() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val said = linesOn(whole.copy(vintoCallerId = me))

        assertEquals(
            listOf("FINAL ROUND", "Everyone else plays one hand between them, against yours."),
            said,
            "the player who called Vinto is not told what they are up against",
        )
    }

    @Test
    fun everyCoalitionMemberReadsTheSameLine() = runComposeUiTest {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }

        val said = linesOn(whole.copy(vintoCallerId = caller.id))

        assertEquals(
            listOf(
                "FINAL ROUND",
                "One hand between the three of you, against ${caller.nickname}.",
            ),
            said,
            "a coalition member is not told what the round now is",
        )
    }

    @Test
    fun anOrdinaryTurnIsNotAFinalRound() = runComposeUiTest {
        assertTrue(linesOn(teachingSession().view.value).isEmpty(), "no call, no strip")
    }

    /**
     * Who is on which side, said once for the whole row.
     *
     * Four portraits read out one at a time are four names with no relationship between them,
     * and the relationship is the only thing the row is for.
     */
    @Test
    fun theSidesAreSpokenAsWellAsDrawn() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val caller = whole.players.first { it.id != me }

        val spoken = describedOn(whole.copy(vintoCallerId = caller.id))

        assertTrue(
            spoken.any { it == "One hand between the three of you, against ${caller.nickname}." },
            "the roster is a legend a screen reader cannot read: $spoken",
        )
        assertTrue(
            spoken.containsAll(whole.players.map { it.nickname }),
            "not every seat is on the roster: $spoken",
        )
    }

    /** Every line of the strip above the felt, in the order it reads. */
    private fun ComposeUiTest.linesOn(view: PlayerView): List<String> {
        show(view)
        return SENTENCES.flatMap { onAllNodesWithText(it, substring = true).texts() }
    }

    private fun ComposeUiTest.show(view: PlayerView) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    /** Every description the strip contributes: the roster's own, and each face's name. */
    private fun ComposeUiTest.describedOn(view: PlayerView): List<String> {
        show(view)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.texts(): List<String> =
        fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }

    private companion object {
        /** What the strip can ever say, so nothing else on the table is mistaken for it. */
        val SENTENCES = listOf(
            "FINAL ROUND",
            "against yours",
            "between the three of you",
        )
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
