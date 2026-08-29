package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cooperation in the final round, ported from
 * `legacy-web/packages/bot/src/lib/__tests__/mcts-coalition-cooperation.test.ts`.
 *
 * **The originals mostly assert their own fixtures.** They build a context, put a coalition
 * leader in it, and then check the context has a coalition leader; the one behavioural
 * assertion is `expect(decision).toBeDefined()`. Ported literally they would prove nothing,
 * so these test what those cases were reaching for instead — that the coalition is actually
 * scored as a coalition, and that a bot in one plays for the champion rather than for itself.
 *
 * The rule underneath: the coalition wins if its *lowest* hand beats the caller, so the only
 * thing worth improving is the best hand. Helping yourself when a teammate is closer is the
 * mistake this whole mode exists to avoid.
 */
class CoalitionCooperationTest {

    private fun memory() = BotMemory("bot1", Difficulty.HARD, Random(1))

    private fun seat(id: String, cards: Int, score: Double) =
        MctsPlayerState(id, cardCount = cards, score = score)

    private fun finalRound(
        callerScore: Double,
        championScore: Double,
        otherScore: Double,
    ) = MctsGameState(
        players = listOf(
            seat("human1", cards = 3, score = callerScore),
            seat("bot1", cards = 2, score = championScore),
            seat("bot2", cards = 2, score = otherScore),
        ),
        currentPlayerIndex = 2,
        botPlayerId = "bot2",
        discardPile = Pile(),
        deckSize = 20,
        botMemory = memory(),
        vintoCallerId = "human1",
        coalitionLeaderId = "bot1",
        turnCount = 20,
        finalTurnTriggered = true,
    )

    @Test
    fun theCoalitionIsScoredOnItsBestHandNotTheActingBots() {
        // Bot2 is acting and is on 20 — hopeless. Bot1 is on 4 and beats the caller's 10.
        // A self-interested evaluation would call this a bad position; a coalition one knows
        // it is winning.
        val winning = finalRound(callerScore = 10.0, championScore = 4.0, otherScore = 20.0)
        val losing = finalRound(callerScore = 4.0, championScore = 10.0, otherScore = 20.0)

        assertTrue(
            evaluateCoalitionState(winning) > evaluateCoalitionState(losing),
            "the coalition score ignored its own champion",
        )
    }

    @Test
    fun theChampionIsTheLowestCoalitionHandWhoeverIsActing() {
        val state = finalRound(callerScore = 10.0, championScore = 4.0, otherScore = 20.0)

        val champion = findCoalitionChampion(state, vintoCallerId = "human1")

        assertNotNull(champion)
        assertEquals("bot1", champion.id)
        assertTrue(champion.id != state.vintoCallerId, "the caller was chosen as champion")
    }

    @Test
    fun improvingTheChampionMattersAndImprovingAnybodyElseDoesNot() {
        val base = finalRound(callerScore = 10.0, championScore = 8.0, otherScore = 20.0)
        val championImproved = finalRound(callerScore = 10.0, championScore = 4.0, otherScore = 20.0)
        val otherImproved = finalRound(callerScore = 10.0, championScore = 8.0, otherScore = 12.0)

        assertTrue(
            evaluateCoalitionState(championImproved) > evaluateCoalitionState(base),
            "lowering the champion's hand did not help",
        )
        assertEquals(
            evaluateCoalitionState(base),
            evaluateCoalitionState(otherImproved),
            "lowering a non-champion's hand changed the score, which is self-interest",
        )
    }

    @Test
    fun aCoalitionMemberNeverAimsAnActionAtTheVintoCaller() {
        // The rule that makes calling Vinto a commitment. Checked through the move generator,
        // which is what the search is allowed to consider in the first place.
        val state = finalRound(callerScore = 10.0, championScore = 4.0, otherScore = 20.0)
            .copy(pendingCard = testCard(Rank.NINE, "9_0"))

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
        val state = finalRound(callerScore = 10.0, championScore = 4.0, otherScore = 20.0)
            .copy(currentPlayerIndex = 0, botPlayerId = "human1", pendingCard = testCard(Rank.NINE, "9_0"))

        val moves = MoveGenerator.generateMoves(state)

        assertTrue(
            moves.any { move -> move.targets.any { it.playerId != "human1" } },
            "the caller was left with nothing to aim at",
        )
    }

    @Test
    fun aCoalitionAceIsNotAimedAtItsOwnChampion() {
        // Forcing a card on the one member who can still beat the caller is friendly fire.
        val state = finalRound(callerScore = 10.0, championScore = 4.0, otherScore = 20.0)
            .copy(pendingCard = testCard(Rank.ACE, "A_0"))

        val moves = MoveGenerator.generateMoves(state)

        assertTrue(
            moves.none { move -> move.targets.any { it.playerId == "bot1" } },
            "the Ace was aimed at the coalition's own champion",
        )
    }

    @Test
    fun coalitionKnowledgeIsPooledFromEveryMemberNotJustTheActingOne() {
        // The real sharing mechanism, as opposed to the TypeScript's fixture check: what one
        // member has seen of the caller's hand is available to all of them.
        val caller = testPlayer(
            "human1",
            "Human",
            isHuman = true,
            cards = listOf(testCard(Rank.TWO, "h1"), testCard(Rank.THREE, "h2")),
        )
        val seenByBotOne = mapOf(
            "human1" to game.vinto.shapes.SerializedOpponentKnowledge(
                knownCards = mapOf(0 to caller.cards[0]),
            ),
        )
        val botOne = testPlayer(
            "bot1",
            "Bot1",
            isHuman = false,
            cards = listOf(testCard(Rank.FOUR, "b1-1"), testCard(Rank.FIVE, "b1-2")),
        ).copy(isVintoCaller = false, opponentKnowledge = seenByBotOne)
        val botTwo = testPlayer(
            "bot2",
            "Bot2",
            isHuman = false,
            cards = listOf(testCard(Rank.SIX, "b2-1"), testCard(Rank.SEVEN, "b2-2")),
        )

        val state = testState(
            players = listOf(caller.copy(isVintoCaller = true), botOne, botTwo),
            phase = GamePhase.FINAL,
            vintoCallerId = "human1",
            coalitionLeaderId = "bot1",
        )

        // Asked from bot2's side, which saw nothing of the caller itself.
        val input = buildCoalitionPlanInput(state, actingPlayerId = "bot2")

        assertNotNull(input)
        assertEquals(listOf(2), input.callerKnownValues, "bot1's sighting did not reach bot2")
        assertEquals(1, input.callerUnknownCount)
        assertTrue(input.members.map { it.id } == listOf("bot1", "bot2"))
    }
}
