package game.vinto.client

import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A player's own move is announced before the bots have thought about theirs.
 *
 * **Reported from a real game: "I pressed a card and nothing happened."** Nothing was broken.
 * The session published the new state immediately, so the card left the hand at once — and then
 * put the player's frame and every bot frame into ONE batch, emitted after `playBots` had
 * finished searching. The screen animates a batch when it arrives, so the card's flight to the
 * discard could not begin until three bots had finished thinking: up to `MAX_BOT_STEPS` of
 * search, a second or more on a phone. The player pressed again, the in-flight guard swallowed
 * it, and a working guard looked like a broken app.
 *
 * It is intermittent, which is why it was hard to believe: when the bots have nothing to do
 * `playBots` returns at once and the same code feels instant.
 *
 * So the player's move goes out ALONE and FIRST, and the bots follow in their own batch. This
 * asserts the order on the stream itself rather than through a screen, because the order *is*
 * the fix — a screen can only animate what it has been handed.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `runCurrent`, to drain the collector deterministically.
class YourOwnMoveTravelsFirstTest {

    @Test
    fun aMoveIsItsOwnBatchAndTheBotsFollowInTheNext() = runTest {
        val session = LocalGameSession(seed = 20_260_909L)
        val me = session.playerId

        val batches = mutableListOf<List<Frame>>()
        backgroundScope.launch { session.frames.collect { batches += it } }
        // Up to the `collect` before anything is dispatched: a shared flow buffers for the
        // subscribers it has, and anything emitted before this line would read as "the session
        // announced nothing" rather than as a race.
        runCurrent()

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        runCurrent()
        batches.clear()

        // A discard: the move the report was actually about. It ends the turn and opens the
        // toss-in window, so the bots have plenty to do behind it — this is the exact shape
        // where the card used to sit still while three of them decided whether to throw.
        val mine = GameAction.DiscardCard(PlayerIdPayload(me))
        session.dispatch(mine)
        runCurrent()

        assertTrue(batches.isNotEmpty(), "the move was never announced at all")
        assertEquals(
            listOf(mine),
            batches.first().map { it.action },
            "the player's own move did not arrive alone and first: ${batches.map { b -> b.map { it.action } }}",
        )

        val afterwards = batches.drop(1).flatten()
        assertTrue(
            afterwards.isNotEmpty(),
            "the bots never moved, so this proves nothing — pick a seed or a move where they do",
        )
        assertTrue(
            afterwards.none { it.action == mine },
            "the player's move was announced twice: ${afterwards.map { it.action }}",
        )
    }

    /**
     * And a move the bots do not follow is still announced, exactly once.
     *
     * The guard on the second emission is `isNotEmpty`, so this is the case that would break if
     * that ever became an unconditional `tryEmit`: a player peeking at their own setup card ends
     * no turn, and an empty batch behind it would make the screen submit nothing to its queue
     * and count a drain that never happened.
     */
    @Test
    fun aMoveWithNoBotsBehindItIsAnnouncedOnce() = runTest {
        val session = LocalGameSession(seed = 20_260_909L)
        val me = session.playerId

        val batches = mutableListOf<List<Frame>>()
        backgroundScope.launch { session.frames.collect { batches += it } }
        runCurrent()

        val mine = GameAction.PeekSetupCard(PositionPayload(me, 0))
        session.dispatch(mine)
        runCurrent()

        assertEquals(
            listOf(listOf(mine)),
            batches.map { batch -> batch.map { it.action } },
            "a lone move should be one batch of one frame",
        )
    }
}
