package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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
 * Three seats, three different sentences, and no sentence at all before there is one to say.
 */
@OptIn(ExperimentalTestApi::class)
class CoalitionLineTest {

    @Test
    fun theCallerIsToldTheTableHasClosedRanksAgainstThem() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val ally = whole.players.first { it.id != me }

        val said = linesOn(whole.copy(vintoCallerId = me, coalitionLeaderId = ally.id))

        assertEquals(
            listOf("FINAL ROUND", "Everyone else plays one hand between them, against yours."),
            said,
            "the player who called Vinto is not told what they are up against",
        )
    }

    @Test
    fun theLeaderIsToldItIsTheirHandBeingPlayed() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val caller = whole.players.first { it.id != me }

        val said = linesOn(whole.copy(vintoCallerId = caller.id, coalitionLeaderId = me))

        assertEquals(
            listOf("FINAL ROUND", "You play the coalition’s hand against ${caller.nickname}."),
            said,
            "the seat whose hand decides the round is not told that it does",
        )
    }

    @Test
    fun everybodyElseIsToldWhoIsPlayingIt() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val others = whole.players.filter { it.id != me }
        val caller = others[0]
        val leader = others[1]

        val said = linesOn(whole.copy(vintoCallerId = caller.id, coalitionLeaderId = leader.id))

        assertEquals(
            listOf(
                "FINAL ROUND",
                "${leader.nickname} plays the coalition’s hand against ${caller.nickname}.",
            ),
            said,
            "a coalition member is not told whose hand is carrying theirs",
        )
    }

    @Test
    fun nothingIsSaidBeforeThereIsSomethingToSay() = runComposeUiTest {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }

        assertTrue(linesOn(whole).isEmpty(), "an ordinary turn is not a final round")
        assertTrue(
            linesOn(whole.copy(vintoCallerId = caller.id)).isEmpty(),
            "a coalition with nobody chosen to play its hand has no sentence to say yet",
        )
    }

    /** Every line of the strip above the felt, in the order it reads. */
    private fun ComposeUiTest.linesOn(view: PlayerView): List<String> {
        var lines = emptyList<String>()
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
        lines = SENTENCES.flatMap { onAllNodesWithText(it, substring = true).texts() }
        return lines
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.texts(): List<String> =
        fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }

    private companion object {
        /** What the strip can ever say, so nothing else on the table is mistaken for it. */
        val SENTENCES = listOf("FINAL ROUND", "coalition’s hand", "against yours")
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
