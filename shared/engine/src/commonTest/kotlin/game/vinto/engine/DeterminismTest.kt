package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import game.vinto.shapes.canonicalizeGameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Determinism, ported from `legacy-web/packages/engine/src/lib/__tests__/determinism.test.ts`.
 *
 * Replay and cross-language parity both rest on one property: the same state and the same
 * action always produce the same next state — ids and `rngState` included. Everything else
 * in the migration is downstream of this holding.
 */
class DeterminismTest {

    @Test
    fun twoReductionsOfEqualInputsAgreeExactly() {
        val state = testState(
            drawPile = pileOf(
                testCard(Rank.FIVE, "d1"),
                testCard(Rank.NINE, "d2"),
                testCard(Rank.KING, "d3"),
            ),
            rngState = 12345,
        )

        val first = GameEngine.reduce(state, drawCard("p1"))
        val second = GameEngine.reduce(state, drawCard("p1"))

        assertTrue(first is ReduceResult.Success)
        assertTrue(second is ReduceResult.Success)
        assertEquals(canonicalizeGameState(first.state), canonicalizeGameState(second.state))
        assertEquals(first.state.rngState, second.state.rngState)
    }

    @Test
    fun anActionThatNeedsNoRandomnessLeavesTheGeneratorAlone() {
        val state = testState(
            drawPile = pileOf(testCard(Rank.FIVE, "d1"), testCard(Rank.NINE, "d2")),
            rngState = 999,
        )

        val result = GameEngine.reduce(state, drawCard("p1"))

        assertTrue(result is ReduceResult.Success)
        assertEquals(999L, result.state.rngState)
    }

    @Test
    fun aReshuffleAdvancesTheGeneratorReproducibly() {
        // The reshuffle fires from advanceTurnAfterTossIn once the draw pile is down to one
        // card, and it is the engine's only consumer of randomness. Driven through real
        // turns rather than called directly, so the trigger is under test too.
        fun buildState(rngState: Long) = testState(
            subPhase = GameSubPhase.IDLE,
            turnNumber = 1,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.TWO, "p1c1"))),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.THREE, "p2c1"))),
                testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.FOUR, "p3c1"))),
                testPlayer("p4", "Player 4", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p4c1"))),
            ),
            drawPile = pileOf(
                testCard(Rank.TWO, "draw1"), testCard(Rank.THREE, "draw2"),
                testCard(Rank.FOUR, "draw3"), testCard(Rank.FIVE, "draw4"),
            ),
            discardPile = pileOf(
                testCard(Rank.SIX, "discard1"),
                testCard(Rank.SIX, "discard2"),
                testCard(Rank.SIX, "discard3"),
            ),
            rngState = rngState,
        )

        fun playToReshuffle(rngState: Long): GameState {
            var state = buildState(rngState)
            for (playerId in listOf("p1", "p2", "p3")) {
                state = unsafeReduce(state, drawCard(playerId))
                state = unsafeReduce(state, discardCard(playerId))
                state = markPlayersReady(state, listOf("p1", "p2", "p3", "p4"))
            }
            return state
        }

        val runA = playToReshuffle(42)
        val runB = playToReshuffle(42)
        val different = playToReshuffle(4242)

        assertEquals(6, runA.drawPile.size, "the reshuffle did not happen")
        assertEquals(1, runA.discardPile.size)

        assertEquals(runA.drawPile.cards.map { it.id }, runB.drawPile.cards.map { it.id })
        assertEquals(runA.rngState, runB.rngState)

        assertNotEquals(42L, runA.rngState, "the generator never moved")
        assertNotEquals(
            runA.drawPile.cards.map { it.id },
            different.drawPile.cards.map { it.id },
            "a different seed produced the same order",
        )
    }

    @Test
    fun queuedTossInCardIdsAreMintedWithoutAClock() {
        val state = testState(
            phase = GamePhase.PLAYING,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 7,
            activeTossIn = tossIn(
                ranks = listOf(Rank.NINE),
                initiatorId = "p2",
                participants = listOf("p2"),
                queuedActions = listOf(TossInAction(playerId = "p2", rank = Rank.NINE, position = 0)),
                playersReadyForNextTurn = listOf("p1", "p3", "p4"),
            ),
        )

        val finished = GameAction.PlayerTossInFinished(PlayerIdPayload("p2"))
        val first = unsafeReduce(state, finished)
        val second = unsafeReduce(state, finished)

        val id = first.pendingAction?.card?.id
        assertEquals(id, second.pendingAction?.card?.id)
        assertTrue(id?.contains("tossin_queued_7_p2_9") == true, "unexpected id: $id")
        assertTrue(
            id?.let { Regex("\\d{13}").containsMatchIn(it) } == false,
            "the id carries what looks like epoch millis: $id",
        )
    }
}
