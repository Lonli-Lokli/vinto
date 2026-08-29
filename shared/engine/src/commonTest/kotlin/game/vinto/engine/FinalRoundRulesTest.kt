package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The final round as a whole: who gets a turn, whose cards are out of bounds, and how the
 * round ends. `VINTO_RULES.md` — "Declaring Vinto": each other player takes exactly one more
 * turn, and no one may interact with the caller's cards.
 *
 * The engine never asks whether a seat is a human or a bot for any of these rules, and the
 * humans-only test at the bottom leans on exactly that: a table of four humans plays the
 * same final round the bots do, through the same validator.
 */
class FinalRoundRulesTest {

    private fun endRound(playerId: String) = GameAction.EndRound(PlayerIdPayload(playerId))

    /** One full turn for the seat on play: draw, discard, everyone waves the toss-in through. */
    private fun playPlainTurn(state: GameState, playerId: String): GameState {
        var s = unsafeReduce(state, drawCard(playerId))
        s = unsafeReduce(s, discardCard(playerId))
        val notReady = s.players.map { it.id }
            .filterNot { it in (s.activeTossIn?.playersReadyForNextTurn ?: emptyList()) }
        return markPlayersReady(s, notReady)
    }

    @Test
    fun theFinalRoundGivesEachCoalitionMemberExactlyOneTurnThenScores() {
        val players = listOf(
            testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.TWO, "p1c1"))),
            testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p2c1"))),
            testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SIX, "p3c1"))),
            testPlayer("p4", "Player 4", isHuman = false, cards = listOf(testCard(Rank.NINE, "p4c1"))),
        )
        val deck = pileOf(
            testCard(Rank.THREE, "d1"),
            testCard(Rank.FOUR, "d2"),
            testCard(Rank.THREE, "d3"),
            testCard(Rank.FOUR, "d4"),
        )
        var state = testState(players = players, drawPile = deck)

        // The caller finishes an ordinary turn and calls Vinto in their own toss-in window.
        state = unsafeReduce(state, drawCard("p1"))
        state = unsafeReduce(state, discardCard("p1"))
        state = unsafeReduce(state, callVinto("p1"))

        assertEquals(GamePhase.FINAL, state.phase)
        assertEquals(1, state.currentPlayerIndex, "play moves past the caller")

        // Members two and three play; the round is still going and the seat advances by one.
        state = playPlainTurn(state, "p2")
        assertEquals(GamePhase.FINAL, state.phase)
        assertEquals(2, state.currentPlayerIndex)

        state = playPlainTurn(state, "p3")
        assertEquals(GamePhase.FINAL, state.phase)
        assertEquals(3, state.currentPlayerIndex)

        // The last member's turn would hand play back to the caller — that scores the round
        // instead. Three coalition turns, one each, and not a fourth.
        state = playPlainTurn(state, "p4")
        assertEquals(GamePhase.SCORING, state.phase)
        assertNull(state.activeTossIn)
        assertTrue(rejects(state, drawCard("p1")), "nobody plays on once the round is scored")
    }

    @Test
    fun aCoalitionMemberCannotTargetTheCallerBeforeALeaderIsChosen() {
        // Same rule as `RulesTest.theCoalitionCannotTouchTheVintoCallersCardsInTheFinalRound`,
        // but with no coalition leader chosen. Choosing one is optional — the caller's
        // protection must not wait for it.
        val state = testState(
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 1,
            finalTurnTriggered = true,
            vintoCallerId = "p1",
            coalitionLeaderId = null,
            players = listOf(
                testPlayer(
                    "p1",
                    "Player 1",
                    isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.TWO, "p2c1"))),
                testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.THREE, "p3c1"))),
            ),
            pendingAction = pending(testCard(Rank.JACK, "jack1"), "p2"),
        )

        assertTrue(rejects(state, selectTarget("p2", "p1", 0)), "the caller's card was targetable")
        assertTrue(!rejects(state, selectTarget("p2", "p3", 0)))
    }

    @Test
    fun theVintoCallerCannotTossInDuringTheFinalRound() {
        val state = testState(
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            currentPlayerIndex = 1,
            finalTurnTriggered = true,
            vintoCallerId = "p1",
            players = listOf(
                testPlayer(
                    "p1",
                    "Player 1",
                    isHuman = true,
                    cards = listOf(testCard(Rank.FIVE, "p1c1")),
                ),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.TWO, "p2c1"))),
                testPlayer(
                    "p3",
                    "Player 3",
                    isHuman = false,
                    cards = listOf(testCard(Rank.FIVE, "p3c1"), testCard(Rank.NINE, "p3c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.FIVE), initiatorId = "p2", originalPlayerIndex = 1),
        )

        // The caller holds a matching five and still may not shed it: their hand is frozen
        // from the call.
        assertTrue(rejects(state, participateInTossIn("p1", listOf(0))))

        // A coalition member's matching toss-in is the coalition's tool and stays legal.
        val next = unsafeReduce(state, participateInTossIn("p3", listOf(0)))
        assertEquals(1, next.players[2].cards.size)
        assertEquals(Rank.NINE, next.players[2].cards[0].rank)
    }

    @Test
    fun vintoDuringATossInBelongsToTheTurnOwner() {
        val players = listOf(
            testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.TWO, "p1c1"))),
            testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p2c1"))),
            testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SIX, "p3c1"))),
        )

        // After a toss-in queue drains, the seat pointer can rest on the last toss-in actor.
        // That seat passes the turn check and still may not call Vinto: the window belongs to
        // the player whose turn it closes.
        val outOfSeat = testState(
            players = players,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            currentPlayerIndex = 2,
            activeTossIn = tossIn(ranks = listOf(Rank.FIVE), initiatorId = "p1", originalPlayerIndex = 0),
        )
        assertTrue(rejects(outOfSeat, callVinto("p3")))

        val ownSeat = testState(
            players = players,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            currentPlayerIndex = 0,
            activeTossIn = tossIn(ranks = listOf(Rank.FIVE), initiatorId = "p1", originalPlayerIndex = 0),
        )
        val next = unsafeReduce(ownSeat, callVinto("p1"))
        assertEquals("p1", next.vintoCallerId)
        assertEquals(GamePhase.FINAL, next.phase)
    }

    @Test
    fun aDeadFinalRoundEndsOnlyThroughEndRound() {
        fun finalRound(drawPile: game.vinto.shapes.Pile, discardPile: game.vinto.shapes.Pile) =
            testState(
                phase = GamePhase.FINAL,
                subPhase = GameSubPhase.IDLE,
                currentPlayerIndex = 1,
                finalTurnTriggered = true,
                vintoCallerId = "p1",
                players = listOf(
                    testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.TWO, "p1c1"))),
                    testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p2c1"))),
                ),
                drawPile = drawPile,
                discardPile = discardPile,
            )

        // A card left to draw, or an unused action card to take — the round can still be played.
        assertTrue(rejects(finalRound(pileOf(testCard(Rank.FOUR, "d1")), pileOf()), endRound("p2")))
        assertTrue(
            rejects(finalRound(pileOf(), pileOf(testCard(Rank.QUEEN, "q1"))), endRound("p2")),
        )

        // Nothing to draw and nothing takeable: the position is dead, and END_ROUND scores it.
        val spentQueen = testCard(Rank.QUEEN, "q1").copy(played = true)
        val dead = finalRound(pileOf(), pileOf(spentQueen))
        val next = unsafeReduce(dead, endRound("p2"))
        assertEquals(GamePhase.SCORING, next.phase)
    }

    @Test
    fun aHumansOnlyCoalitionPlaysTheFinalRoundToScoring() {
        // Scenario (c): every seat is a person. The engine runs the same final round it runs
        // for bots — no leader is ever chosen, the caller stays protected, each human gets
        // exactly one turn, and the scoring comes out per the rules.
        val players = listOf(
            testPlayer(
                "p1",
                "Player 1",
                isHuman = true,
                cards = listOf(testCard(Rank.SIX, "p1c1"), testCard(Rank.SEVEN, "p1c2")),
            ),
            testPlayer("p2", "Player 2", isHuman = true, cards = listOf(testCard(Rank.FIVE, "p2c1"))),
            testPlayer("p3", "Player 3", isHuman = true, cards = listOf(testCard(Rank.SIX, "p3c1"))),
            testPlayer("p4", "Player 4", isHuman = true, cards = listOf(testCard(Rank.NINE, "p4c1"))),
        )
        val deck = pileOf(
            testCard(Rank.THREE, "d1"), // p1's draw, discarded before the call
            testCard(Rank.JACK, "dJack"), // p2 draws the Jack and plays it
            testCard(Rank.FOUR, "d3"), // p3's draw
            testCard(Rank.FOUR, "d4"), // p4's draw
        )
        var state = testState(players = players, drawPile = deck)
        val callerCardIds = state.players[0].cards.map { it.id }

        // The caller's last ordinary turn, ending in the Vinto call.
        state = unsafeReduce(state, drawCard("p1"))
        state = unsafeReduce(state, discardCard("p1"))
        state = unsafeReduce(state, callVinto("p1"))
        assertEquals(GamePhase.FINAL, state.phase)
        assertNull(state.coalitionLeaderId)

        // p2 draws the Jack and uses it. Aiming it at the caller is refused — even with no
        // leader chosen — so the swap lands between two coalition hands instead.
        state = unsafeReduce(state, drawCard("p2"))
        state = unsafeReduce(state, useCardAction("p2"))
        assertTrue(rejects(state, selectTarget("p2", "p1", 0)), "the caller's card was targetable")
        state = unsafeReduce(state, selectTarget("p2", "p3", 0))
        state = unsafeReduce(state, selectTarget("p2", "p4", 0))
        state = unsafeReduce(state, GameAction.ExecuteJackSwap(PlayerIdPayload("p2")))
        state = markPlayersReady(state, listOf("p2", "p3", "p4"))
        assertEquals(GamePhase.FINAL, state.phase)

        // p3 and p4 take their one turn each; p4's toss-in window closing ends the round.
        var s = unsafeReduce(state, drawCard("p3"))
        s = unsafeReduce(s, discardCard("p3"))
        s = markPlayersReady(s, listOf("p2", "p3", "p4"))
        assertEquals(GamePhase.FINAL, s.phase)

        s = unsafeReduce(s, drawCard("p4"))
        s = unsafeReduce(s, discardCard("p4"))
        s = markPlayersReady(s, listOf("p2", "p3", "p4"))
        assertEquals(GamePhase.SCORING, s.phase)

        // The caller's hand is untouched, card for card.
        assertEquals(callerCardIds, s.players[0].cards.map { it.id })

        // And the arithmetic is the rules': the best coalition hand beats the caller's 13,
        // so the caller loses a point and every member gains three.
        val scores = calculateFinalScores(s.players, s.vintoCallerId)
        val bestCoalition = s.players.drop(1).minOf { calculateCardTotal(it.cards) }
        assertEquals(13, scores["p1"])
        assertEquals(bestCoalition, scores["p2"])
        assertTrue(bestCoalition < 13)

        val points = calculateRoundPoints(s.players, s.vintoCallerId)
        assertEquals(-1, points["p1"])
        assertEquals(3, points["p2"])
        assertEquals(3, points["p3"])
        assertEquals(3, points["p4"])
    }
}
