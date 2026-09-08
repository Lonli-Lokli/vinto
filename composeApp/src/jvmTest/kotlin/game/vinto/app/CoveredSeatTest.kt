package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.seat_is_a_bot
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A seat a bot has taken over stops looking like the person who left it.
 *
 * Reported from a real online game: a *human* was seen playing itself, move after move. The
 * room was behaving — a socket had gone and the seat grace handed the seat to a bot, which is
 * design R5 — but nothing on the felt said so. The takeover is deliberately never written into
 * the game state (`isHuman` and `isBot` are inside the canonical hash, and a round whose
 * recording cannot replay is a worse bug than a missing label), so every client went on
 * drawing the seat with its person's name, their face and no bot mark, while a machine played
 * it. What the room *does* send is `away`, and the table drew that as one small open circle.
 *
 * So: while a bot is covering a seat, the seat is drawn as the bot it is — the element's name
 * and, through the name, the element's portrait — and it wears the machine mark. It goes back
 * to being somebody the moment they do.
 */
@OptIn(ExperimentalTestApi::class)
class CoveredSeatTest {

    @Test
    fun aCoveredSeatIsDrawnAsTheBotThatIsPlayingIt() {
        val view = peopleAtTheTable()
        val covered = view.players[1]

        var botMark = 0
        var stillNamed = 0
        var wearsTheBotsName = 0
        runComposeUiTest {
            val said = mutableStateOf("")
            setContent {
                said.value = stringResource(Res.string.seat_is_a_bot)
                Felt { Table(view, away = setOf(covered.id)) }
            }
            waitForIdle()
            botMark = onAllNodesWithContentDescription(said.value).fetchSemanticsNodes().size
            stillNamed = onAllNodesWithText(covered.nickname).fetchSemanticsNodes().size
            wearsTheBotsName = onAllNodesWithText(BOT_SEAT_ONE).fetchSemanticsNodes().size
        }

        assertTrue(botMark > 0, "no seat on the felt says a machine is playing it")
        assertTrue(
            stillNamed == 0,
            "the felt still calls the covered seat \"${covered.nickname}\", who is not there",
        )
        assertTrue(wearsTheBotsName > 0, "the covered seat is not called $BOT_SEAT_ONE")
    }

    /** And a table where everybody is present is left exactly as it was. */
    @Test
    fun aSeatWithSomebodyInItKeepsTheirName() {
        val view = peopleAtTheTable()
        val present = view.players[1]

        var named = 0
        runComposeUiTest {
            setContent { Felt { Table(view, away = emptySet()) } }
            waitForIdle()
            named = onAllNodesWithText(present.nickname).fetchSemanticsNodes().size
        }

        assertTrue(named > 0, "a seat with its person in it lost their name")
    }

    // ------------------------------------------------------------------ fixtures

    /** The lesson's deal with people in the seats, so a nickname is a person's rather than a bot's. */
    private fun peopleAtTheTable(): PlayerView {
        val view = teachingSession().view.value
        return view.copy(
            players = view.players.mapIndexed { index, seat ->
                val who = PEOPLE[index]
                seat.copy(name = who, nickname = who, isHuman = true, isBot = false)
            },
        )
    }

    @Composable
    private fun Felt(content: @Composable () -> Unit) {
        VintoTheme(dark = false) {
            Surface(color = Rail.fill) {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { content() }
            }
        }
    }

    @Composable
    private fun Table(view: PlayerView, away: Set<String>) {
        TableScreen(
            state = TableState(
                view,
                tableFor(view, Question.None, away = away),
                null,
                emptyList(),
                1,
            ),
            layout = TableLayout.forScreen(PHONE_H),
            onMove = {},
            onHelp = {},
            onSettings = {},
        )
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        val PEOPLE = listOf("Ann", "Bob", "Cass", "Dee")

        /** What the room calls the bot on seat one — `botName(1)`, which the client must agree with. */
        const val BOT_SEAT_ONE = "Ember"
    }
}
