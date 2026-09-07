package game.vinto.engine

import game.vinto.shapes.ActionTarget
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Jack or a Queen swap moves two cards while the whole table watches, so what anybody knew
 * about either card is still true — about its new position.
 *
 * Reported 2026-09-06 from a real deal, where it cost the table twice from one cause. Ember
 * swapped the Joker into its own row in front of everybody; Tide's Queen then moved it to
 * Tide's hand. Dune, still holding the old address, aimed its own Queen at the seat the Joker
 * had left — and every seat went on believing Ember held the Joker right through the coalition
 * round, planning against a hand that did not exist.
 *
 * The engine already does exactly this for the other thing pinned to a position — a claim
 * follows its card across a watched swap (`swapDeclarationsBetween`). These pin the same rule
 * for what a seat has *seen*, which is the channel the bots actually play on.
 */
class SwapKnowledgeTest {

    private val joker = testCard(Rank.JOKER, "joker")
    private val three = testCard(Rank.THREE, "three")

    /** p1 holds the Joker at 0, p2 a three at 1, and p3 plays the action card. */
    private fun table(
        p1Knows: List<Int> = emptyList(),
        p2Knows: List<Int> = emptyList(),
        p3Sees: Map<String, Map<Int, Card>> = emptyMap(),
        actor: String = "p3",
        rank: Rank,
    ): GameState {
        val seats = listOf(
            testPlayer("p1", "One", isHuman = false, cards = listOf(joker), knownCardPositions = p1Knows),
            testPlayer(
                "p2",
                "Two",
                isHuman = false,
                cards = listOf(testCard(Rank.EIGHT, "filler"), three),
                knownCardPositions = p2Knows,
            ),
            testPlayer("p3", "Three", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p3c"))),
            testPlayer("p4", "Four", isHuman = false, cards = listOf(testCard(Rank.SIX, "p4c"))),
        ).map { seat ->
            if (seat.id == "p3") {
                seat.copy(
                    opponentKnowledge = p3Sees
                        .mapValues { (_, cards) -> SerializedOpponentKnowledge(cards) }
                        .takeIf { it.isNotEmpty() },
                )
            } else {
                seat
            }
        }

        return testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = seats.indexOfFirst { it.id == actor },
            players = seats,
            pendingAction = pending(
                testCard(rank, "action"),
                actor,
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 1)),
            ),
        )
    }

    private fun swap(state: GameState, rank: Rank, actor: String): GameState =
        unsafeReduce(
            state,
            if (rank == Rank.JACK) {
                GameAction.ExecuteJackSwap(PlayerIdPayload(actor))
            } else {
                GameAction.ExecuteQueenSwap(PlayerIdPayload(actor))
            },
        )

    private fun PlayerState.sees(ownerId: String, position: Int): Rank? =
        opponentKnowledge?.get(ownerId)?.knownCards?.get(position)?.rank

    private fun seat(state: GameState, id: String): PlayerState =
        state.players.first { it.id == id }

    @Test
    fun aWatchingSeatFollowsAKnownCardAcrossAJackSwap() {
        // p4 had been shown p1's Joker and watches p3's Jack move it into p2's hand.
        val before = table(rank = Rank.JACK).let { state ->
            state.copy(
                players = state.players.map {
                    if (it.id == "p4") {
                        it.copy(opponentKnowledge = mapOf("p1" to SerializedOpponentKnowledge(mapOf(0 to joker))))
                    } else {
                        it
                    }
                },
            )
        }

        val after = swap(before, Rank.JACK, "p3")

        assertEquals(Rank.JOKER, seat(after, "p4").sees("p2", 1), "the Joker's new address")
        assertNull(seat(after, "p4").sees("p1", 0), "the address it left")
    }

    @Test
    fun aWatchingSeatFollowsAKnownCardAcrossAQueenSwap() {
        val before = table(p3Sees = mapOf("p1" to mapOf(0 to joker)), actor = "p4", rank = Rank.QUEEN)

        val after = swap(before, Rank.QUEEN, "p4")

        assertEquals(Rank.JOKER, seat(after, "p3").sees("p2", 1), "the Joker's new address")
        assertNull(seat(after, "p3").sees("p1", 0), "the address it left")
    }

    @Test
    fun aSeatThatKnewBothSidesKnowsBothAfterTheSwap() {
        val before = table(p3Sees = mapOf("p1" to mapOf(0 to joker), "p2" to mapOf(1 to three)), rank = Rank.JACK)

        val after = swap(before, Rank.JACK, "p3")

        assertEquals(Rank.JOKER, seat(after, "p3").sees("p2", 1))
        assertEquals(Rank.THREE, seat(after, "p3").sees("p1", 0))
    }

    @Test
    fun anOwnerFollowsTheirOwnCardIntoTheHandItWentTo() {
        // p1 knows its own Joker; a Jack it did not play moves it to p2. It stops knowing its
        // own position — a stranger's card arrived — and starts knowing p2's.
        val before = table(p1Knows = listOf(0), rank = Rank.JACK)

        val after = swap(before, Rank.JACK, "p3")

        assertEquals(Rank.JOKER, seat(after, "p1").sees("p2", 1), "where its own card went")
        assertTrue(0 !in seat(after, "p1").knownCardPositions, "the card that arrived is a stranger")
    }

    @Test
    fun aSeatGivenACardItAlreadyKnewKnowsItStill() {
        // p1 had been shown p2's three, and the Jack hands it exactly that card.
        val before = table(rank = Rank.JACK).let { state ->
            state.copy(
                players = state.players.map {
                    if (it.id == "p1") {
                        it.copy(opponentKnowledge = mapOf("p2" to SerializedOpponentKnowledge(mapOf(1 to three))))
                    } else {
                        it
                    }
                },
            )
        }

        val after = swap(before, Rank.JACK, "p3")

        assertTrue(0 in seat(after, "p1").knownCardPositions, "it knew the card that arrived")
        assertNull(seat(after, "p1").sees("p2", 1), "and no longer knows p2's")
    }

    @Test
    fun theQueenPlayerKnowsWhereItsOwnCardWent() {
        // p1 plays the Queen on its own Joker and p2's three, having looked at both.
        val before = table(actor = "p1", rank = Rank.QUEEN)

        val after = swap(before, Rank.QUEEN, "p1")

        assertTrue(0 in seat(after, "p1").knownCardPositions, "it looked at the card it took")
        assertEquals(Rank.JOKER, seat(after, "p1").sees("p2", 1), "and watched its own leave")
    }
}
