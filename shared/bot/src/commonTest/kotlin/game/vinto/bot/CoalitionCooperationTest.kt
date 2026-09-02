package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cooperation in the final round, as the search's own model sees it.
 *
 * The rule underneath: the coalition wins if its *lowest* hand beats the caller, so the only
 * thing worth improving is the best hand. These pin that the model scores a final-round
 * position the way the rules score it — every coalition seat gets the coalition's result,
 * and only the champion's hand moves it — and that the generator keeps the caller's cards
 * out of reach.
 */
class CoalitionCooperationTest {

    private fun memory() = BotMemory("bot1", Difficulty.HARD, Random(1))

    private fun hand(prefix: String, vararg ranks: Rank): List<Card> =
        ranks.mapIndexed { index, rank -> testCard(rank, "$prefix-$index") }

    /** A determinized final-round world: three seats, the human has called, every card dealt. */
    private fun finalRound(
        caller: List<Card>,
        champion: List<Card>,
        other: List<Card>,
    ): MctsGameState {
        val hands = mapOf("human1" to caller, "bot1" to champion, "bot2" to other)
        return MctsGameState(
            players = hands.map { (id, cards) -> MctsPlayerState(id, cardCount = cards.size) },
            currentPlayerIndex = 2,
            botPlayerId = "bot2",
            discardPile = Pile(),
            deckSize = 20,
            botMemory = memory(),
            hiddenCards = hands.flatMap { (id, cards) ->
                cards.mapIndexed { position, card -> "$id-$position" to card }
            }.toMap(),
            vintoCallerId = "human1",
            coalitionLeaderId = "bot1",
            turnCount = 20,
            finalTurnTriggered = true,
        )
    }

    @Test
    fun theCoalitionIsScoredOnItsBestHandNotTheActingBots() {
        // Bot2 is acting and is on 20 — hopeless. Bot1 is on 4 and beats the caller's 10.
        // A self-interested evaluation would call this a bad position; the rule says the
        // coalition is winning, and every coalition seat is paid the same.
        val winning = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        )
        val losing = finalRound(
            caller = hand("h", Rank.TWO, Rank.TWO),
            champion = hand("c", Rank.FIVE, Rank.FIVE),
            other = hand("o", Rank.TEN, Rank.TEN),
        )

        val paidWinning = rewards(winning)
        val paidLosing = rewards(losing)
        assertTrue(paidWinning[2] > paidLosing[2], "the acting bot was not paid for its champion's win")
        assertEquals(paidWinning[1], paidWinning[2], "two coalition seats were paid differently")
        assertTrue(paidWinning[0] < paidLosing[0], "the caller was paid for losing")
    }

    @Test
    fun aTieGoesToTheCaller() {
        val level = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.FIVE, Rank.FIVE),
            other = hand("o", Rank.TEN, Rank.TEN),
        )

        val paid = rewards(level)
        assertEquals(1.0, paid[0], "the caller takes a tie")
        assertTrue(paid[1] < 1.0 && paid[1] > 0.0, "a tie costs the coalition nothing and wins nothing")
    }

    @Test
    fun theChampionIsTheLowestCoalitionHandWhoeverIsActing() {
        val state = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        )

        val champion = MoveGenerator.coalitionChampion(state)

        assertNotNull(champion)
        assertEquals("bot1", champion.id)
        assertTrue(champion.id != state.vintoCallerId, "the caller was chosen as champion")
    }

    @Test
    fun improvingTheChampionMattersAndImprovingAnybodyElseDoesNot() {
        // The champion on 12 loses to a caller on 10. Bring the champion to 4 and the
        // coalition wins; bring the *other* member from 20 to 14 and nothing has changed.
        val base = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.SIX, Rank.SIX),
            other = hand("o", Rank.TEN, Rank.TEN),
        )
        val championImproved = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        )
        val otherImproved = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.SIX, Rank.SIX),
            other = hand("o", Rank.SEVEN, Rank.SEVEN),
        )

        assertTrue(
            rewards(championImproved)[2] > rewards(base)[2],
            "lowering the champion's hand did not help",
        )
        assertEquals(
            rewards(base)[2],
            rewards(otherImproved)[2],
            "lowering a non-champion's hand changed the score, which is self-interest",
        )
    }

    @Test
    fun aCoalitionMemberNeverAimsAnActionAtTheVintoCaller() {
        // The rule that makes calling Vinto a commitment. Checked through the move generator,
        // which is what the search is allowed to consider in the first place.
        val state = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        ).copy(pendingCard = testCard(Rank.NINE, "9_0"), pendingOrigin = PendingOrigin.DRAWN)

        val moves = MoveGenerator.generateMoves(state)

        assertTrue(moves.isNotEmpty(), "no moves were offered at all")
        assertTrue(
            moves.none { move -> move.targets.any { it.playerId == "human1" } },
            "a move targeting the Vinto caller was offered to a coalition member",
        )
    }

    @Test
    fun theCallerThemselvesIsNotBoundByThatRestriction() {
        // It protects the caller, not everyone: the caller may still act on the coalition.
        val state = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        ).copy(
            currentPlayerIndex = 0,
            botPlayerId = "human1",
            pendingCard = testCard(Rank.NINE, "9_0"),
            pendingOrigin = PendingOrigin.DRAWN,
        )

        val moves = MoveGenerator.generateMoves(state)

        assertTrue(
            moves.any { move -> move.targets.any { it.playerId != "human1" } },
            "the caller was left with nothing to aim at",
        )
    }

    @Test
    fun aCoalitionAceIsNotAimedAtItsOwnChampion() {
        // Forcing a card on the one member who can still beat the caller is friendly fire.
        val state = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        ).copy(pendingCard = testCard(Rank.ACE, "A_0"), pendingOrigin = PendingOrigin.DRAWN)

        val moves = MoveGenerator.generateMoves(state)

        assertTrue(
            moves.none { move -> move.targets.any { it.playerId == "bot1" } },
            "the Ace was aimed at the coalition's own champion",
        )
    }

    @Test
    fun coalitionKnowledgeIsPooledFromEveryMemberNotJustTheActingOne() {
        // Kept from the original suite: the planner's input pools what every member has seen
        // of the caller. Held in CoalitionPlannerTest in full; here it is enough that the
        // model's champion is found from the world rather than from any one seat's beliefs.
        val state = finalRound(
            caller = hand("h", Rank.FIVE, Rank.FIVE),
            champion = hand("c", Rank.TWO, Rank.TWO),
            other = hand("o", Rank.TEN, Rank.TEN),
        )
        assertEquals(4, StateTransition.handTotal(state, "bot1"))
        assertEquals(20, StateTransition.handTotal(state, "bot2"))
    }
}
