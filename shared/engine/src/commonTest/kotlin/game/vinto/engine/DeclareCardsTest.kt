package game.vinto.engine

import game.vinto.shapes.ActionTarget
import game.vinto.shapes.DeclareCardsPayload
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DECLARE_CARDS — coalition table talk. A member says what they believe their cards are;
 * the claims are public, optional, merged over earlier ones, never checked against the real
 * cards, and dropped the moment the card they describe moves.
 */
class DeclareCardsTest {

    private fun declareCards(playerId: String, claims: Map<Int, Rank>) =
        GameAction.DeclareCards(DeclareCardsPayload(playerId, claims))

    private fun finalRound(
        players: List<PlayerState>,
        subPhase: GameSubPhase = GameSubPhase.IDLE,
        currentPlayerIndex: Int = 1,
    ) = testState(
        phase = GamePhase.FINAL,
        subPhase = subPhase,
        currentPlayerIndex = currentPlayerIndex,
        finalTurnTriggered = true,
        vintoCallerId = "p1",
        players = players,
    )

    private fun caller() = testPlayer(
        "p1", "Player 1", isHuman = true,
        cards = listOf(testCard(Rank.KING, "p1c1")),
    ).copy(isVintoCaller = true)

    private fun member(id: String, vararg ranks: Rank) = testPlayer(
        id, "Player $id", isHuman = false,
        cards = ranks.mapIndexed { i, rank -> testCard(rank, "${id}c$i") },
    )

    @Test
    fun theCallerMayNotDeclare() {
        val state = finalRound(listOf(caller(), member("p2", Rank.FIVE)))
        assertTrue(rejects(state, declareCards("p1", mapOf(0 to Rank.KING))))
    }

    @Test
    fun declarationsAreOnlyLegalInTheFinalRound() {
        val playing = testState(
            players = listOf(caller().copy(isVintoCaller = false), member("p2", Rank.FIVE)),
        )
        assertTrue(rejects(playing, declareCards("p2", mapOf(0 to Rank.FIVE))))
    }

    @Test
    fun aWrongClaimIsAcceptedWithoutBeingCheckedAgainstTheCard() {
        // p2's card really is a FIVE; the claim says QUEEN. The engine takes the claim as
        // said — being wrong is a memory problem, not a rules problem.
        val state = finalRound(listOf(caller(), member("p2", Rank.FIVE)))
        val next = unsafeReduce(state, declareCards("p2", mapOf(0 to Rank.QUEEN)))
        assertEquals(mapOf(0 to Rank.QUEEN), next.players[1].declaredCards)
        assertEquals(Rank.FIVE, next.players[1].cards[0].rank, "the real card is untouched")
    }

    @Test
    fun anOutOfRangePositionIsRefused() {
        val state = finalRound(listOf(caller(), member("p2", Rank.FIVE)))
        assertTrue(rejects(state, declareCards("p2", mapOf(3 to Rank.FIVE))))
        assertTrue(rejects(state, declareCards("p2", emptyMap())))
    }

    @Test
    fun reDeclaringAPositionOverwritesTheClaim() {
        val state = finalRound(listOf(caller(), member("p2", Rank.FIVE, Rank.NINE)))
        var next = unsafeReduce(state, declareCards("p2", mapOf(0 to Rank.FIVE)))
        next = unsafeReduce(next, declareCards("p2", mapOf(0 to Rank.SIX, 1 to Rank.NINE)))
        assertEquals(mapOf(0 to Rank.SIX, 1 to Rank.NINE), next.players[1].declaredCards)
    }

    @Test
    fun aTossedInCardShiftsDeclarationsBelowIt() {
        val p3 = member("p3", Rank.FIVE, Rank.NINE, Rank.QUEEN)
            .copy(declaredCards = mapOf(0 to Rank.FIVE, 1 to Rank.NINE, 2 to Rank.QUEEN))
        val state = finalRound(
            listOf(caller(), member("p2", Rank.TWO), p3),
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        ).copy(activeTossIn = tossIn(ranks = listOf(Rank.FIVE), initiatorId = "p2", originalPlayerIndex = 1))

        val next = unsafeReduce(state, participateInTossIn("p3", listOf(0)))

        // The claim for the tossed card is gone and the ones above it moved down with
        // their cards.
        assertEquals(mapOf(0 to Rank.NINE, 1 to Rank.QUEEN), next.players[2].declaredCards)
    }

    @Test
    fun aSwapCarriesClaimsWithTheCards() {
        // The whole table watches a Jack move two cards, so a standing claim about either
        // card follows it to its new hand — still a claim, still possibly wrong.
        val p2 = member("p2", Rank.TWO, Rank.NINE).copy(declaredCards = mapOf(0 to Rank.TWO, 1 to Rank.NINE))
        val p3 = member("p3", Rank.SIX).copy(declaredCards = mapOf(0 to Rank.SIX))
        val state = finalRound(
            listOf(caller(), p2, p3),
            subPhase = GameSubPhase.AWAITING_ACTION,
        ).copy(
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p2",
                targets = listOf(ActionTarget("p2", 0), ActionTarget("p3", 0)),
            ),
        )

        val next = unsafeReduce(state, GameAction.ExecuteJackSwap(PlayerIdPayload("p2")))

        assertEquals(mapOf(0 to Rank.SIX, 1 to Rank.NINE), next.players[1].declaredCards)
        assertEquals(mapOf(0 to Rank.TWO), next.players[2].declaredCards)
    }

    @Test
    fun aSwapWithAnUndeclaredCardMovesTheOneClaimAndClearsTheOtherSide() {
        val p2 = member("p2", Rank.TWO).copy(declaredCards = mapOf(0 to Rank.TWO))
        val p3 = member("p3", Rank.SIX) // nothing declared
        val state = finalRound(
            listOf(caller(), p2, p3),
            subPhase = GameSubPhase.AWAITING_ACTION,
        ).copy(
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p2",
                targets = listOf(ActionTarget("p2", 0), ActionTarget("p3", 0)),
            ),
        )

        val next = unsafeReduce(state, GameAction.ExecuteJackSwap(PlayerIdPayload("p2")))

        assertNull(next.players[1].declaredCards, "the undeclared card arrived with no claim")
        assertEquals(mapOf(0 to Rank.TWO), next.players[2].declaredCards)
    }

    @Test
    fun aSwapInClearsTheDisplacedClaim() {
        val p2 = member("p2", Rank.NINE, Rank.TWO).copy(declaredCards = mapOf(0 to Rank.NINE, 1 to Rank.TWO))
        val state = finalRound(
            listOf(caller(), p2),
            subPhase = GameSubPhase.CHOOSING,
        ).copy(pendingAction = pending(testCard(Rank.THREE, "drawn1"), "p2"))

        val next = unsafeReduce(state, swapCard("p2", 0))

        // The drawn THREE now sits at position 0 — whatever was claimed about the NINE that
        // left is no longer about any card p2 holds.
        assertEquals(mapOf(1 to Rank.TWO), next.players[1].declaredCards)
    }

    @Test
    fun aCorrectKingDeclarationShiftsClaimsLikeATossIn() {
        val p2 = member("p2", Rank.FIVE, Rank.NINE)
            .copy(declaredCards = mapOf(0 to Rank.FIVE, 1 to Rank.NINE))
        val state = finalRound(
            listOf(caller(), p2),
            subPhase = GameSubPhase.AWAITING_ACTION,
        ).copy(
            pendingAction = pending(
                testCard(Rank.KING, "king1"), "p2",
                targets = listOf(ActionTarget("p2", 0, testCard(Rank.FIVE, "p2c0"))),
            ),
        )

        val next = unsafeReduce(state, declareKing("p2", Rank.FIVE))

        assertEquals(1, next.players[1].cards.size)
        assertEquals(mapOf(0 to Rank.NINE), next.players[1].declaredCards)
    }

    @Test
    fun declaringDuringATossInDoesNotAdvanceTheTurn() {
        // Everyone but p2 is already ready; p2's declaration is table talk and must not be
        // the action that closes the window.
        val state = finalRound(
            listOf(caller(), member("p2", Rank.FIVE), member("p3", Rank.SIX)),
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        ).copy(
            activeTossIn = tossIn(
                ranks = listOf(Rank.TWO),
                initiatorId = "p2",
                originalPlayerIndex = 1,
                playersReadyForNextTurn = listOf("p1", "p3"),
            ),
        )

        val next = unsafeReduce(state, declareCards("p2", mapOf(0 to Rank.FIVE)))

        assertEquals(GamePhase.FINAL, next.phase, "table talk ended the round")
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertEquals(1, next.currentPlayerIndex)
    }

    @Test
    fun aGameWithDeclarationsReplaysToTheSameHashes() {
        // Determinism: the same actions from the same state land on the same hash, claims
        // included.
        val start = finalRound(listOf(caller(), member("p2", Rank.FIVE), member("p3", Rank.SIX)))
        val actions = listOf(
            declareCards("p2", mapOf(0 to Rank.FIVE)),
            declareCards("p3", mapOf(0 to Rank.QUEEN)),
            declareCards("p2", mapOf(0 to Rank.SIX)),
        )

        val once = actions.fold(start) { s, a -> unsafeReduce(s, a) }
        val twice = actions.fold(start) { s, a -> unsafeReduce(s, a) }

        assertEquals(
            game.vinto.shapes.hashGameState(once),
            game.vinto.shapes.hashGameState(twice),
        )
        assertEquals(mapOf(0 to Rank.SIX), once.players[1].declaredCards)
    }
}
