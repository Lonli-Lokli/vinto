package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A toss-in window is one moment at the table, and it is played as one.
 *
 * The engine resolves throws one player at a time, because a wrong throw costs a card and the
 * order decides who pays. Played back one frame at a time, that turns a scramble into a queue:
 * four players throwing at the same rank took four beats of screen time and read as four
 * separate events, each with a pause in front of it.
 */
class TossedTogetherTest {

    private val human = "human-1"

    @Test
    fun throwsInOneWindowShareAScene() = runTest {
        val session = teachingSession()
        val view = projectView(session.state, session.playerId)

        val batch = listOf(
            frame(view, throwBy("bot-1")),
            frame(view, throwBy("bot-2")),
            frame(view, throwBy("bot-3")),
        )

        val merged = batch.tossedTogether()

        assertEquals(1, merged.size, "one moment, not three")
        assertEquals(
            3,
            merged.single().scenes.first().size,
            "and every card in it leaves at the same time",
        )
    }

    /** What is not a throw still stands on its own, in the order it happened. */
    @Test
    fun aTurnBetweenTwoWindowsIsNotSweptUpWithThem() = runTest {
        val session = teachingSession()
        val view = projectView(session.state, session.playerId)

        val batch = listOf(
            frame(view, throwBy("bot-1")),
            frame(view, throwBy("bot-2")),
            frame(view, GameAction.DrawCard(PlayerIdPayload(human))),
            frame(view, throwBy("bot-3")),
        )

        val merged = batch.tossedTogether()

        assertEquals(3, merged.size, "the two throws, the draw, then the third throw")
        assertTrue(merged[1].action is GameAction.DrawCard, "the draw kept its place")
        assertTrue(merged[2].action is GameAction.ParticipateInTossIn)
    }

    /** A single throw is left exactly as it was. */
    @Test
    fun oneThrowIsNotRepackaged() = runTest {
        val session = teachingSession()
        val view = projectView(session.state, session.playerId)
        val only = frame(view, throwBy("bot-1"))

        assertEquals(listOf(only), listOf(only).tossedTogether())
    }

    private fun throwBy(who: String) =
        GameAction.ParticipateInTossIn(ParticipateInTossInPayload(who, listOf(0)))

    /** A frame with one flight in it, which is all these cases look at. */
    private fun frame(view: game.vinto.engine.PlayerView, action: GameAction) = Frame(
        action = action,
        scenes = listOf(listOf(Beat.Move(Anchor.Deck, Anchor.Discard, null))),
        view = view,
    )
}
