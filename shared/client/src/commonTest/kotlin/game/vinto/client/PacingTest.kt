package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pauses, checked against real frames from a real round.
 *
 * Pacing is the difference between a game that can be followed and one that merely finishes,
 * and it is the kind of thing that decays silently — a new action type gets added, nobody adds
 * it to the list worth reading, and one day the most informative moment of somebody's turn
 * goes past in a third of a second. These cases are cheap and they bite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PacingTest {

    private fun TestScope.framesOf(session: LocalGameSession): List<Frame> {
        val seen = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { seen.addAll(it) } }
        runCurrent()
        return seen
    }

    private suspend fun started(seed: Long = 8L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    @Test
    fun somebodyElseDrawingIsWorthReading() = runTest {
        val session = started()
        val frames = framesOf(session)

        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val theirDraw = frames.last { it.action is GameAction.DrawCard && it.actorId != session.playerId }
        assertEquals(
            Pacing.READ_MS,
            Pacing.dwellAfter(theirDraw, session.playerId),
            "a card somebody else drew is the thing to look at on their turn",
        )
    }

    /** Your own move is answered, not narrated back to you. */
    @Test
    fun yourOwnMovesAreNotDweltOn() = runTest {
        val session = started()
        val frames = framesOf(session)

        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val mine = frames.first { it.actorId == session.playerId && it.hasSomethingToSee }
        assertEquals(Pacing.BEAT_MS, Pacing.dwellAfter(mine, session.playerId))
        assertEquals(
            0L,
            Pacing.thinkBefore(mine, previousActor = null, viewerId = session.playerId),
            "nobody waits to be told what they just decided",
        )
    }

    /** The pause belongs to the turn changing hands, not to every move within one. */
    @Test
    fun theThinkingPauseIsOncePerTurnRatherThanOncePerMove() = runTest {
        val session = started()
        val frames = framesOf(session)

        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val theirs = frames.filter { it.actorId != null && it.actorId != session.playerId && it.hasSomethingToSee }
        assertTrue(theirs.size > 1, "the bots took their turns: ${theirs.size} frames")

        val bot = theirs.first().actorId
        assertEquals(
            Pacing.THINK_MS,
            Pacing.thinkBefore(theirs.first(), previousActor = session.playerId, viewerId = session.playerId),
            "the turn arriving is worth a beat",
        )

        val sameBotAgain = theirs.first { it.actorId == bot && it !== theirs.first() }
        assertEquals(
            0L,
            Pacing.thinkBefore(sameBotAgain, previousActor = bot, viewerId = session.playerId),
            "carrying on with their own turn is not",
        )
    }

    /** A move with nothing to see costs nothing: the table does not pause on bookkeeping. */
    @Test
    fun aMoveWithNothingToSeeIsFree() = runTest {
        val session = started()
        val frames = framesOf(session)

        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val silent = frames.firstOrNull { !it.hasSomethingToSee } ?: return@runTest
        assertEquals(0L, Pacing.dwellAfter(silent, session.playerId))
        assertEquals(0L, Pacing.thinkBefore(silent, previousActor = "somebody", viewerId = session.playerId))
    }
}
