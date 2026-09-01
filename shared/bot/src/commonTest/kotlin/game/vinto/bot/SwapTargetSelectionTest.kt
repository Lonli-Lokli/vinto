package game.vinto.bot

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a Jack or Queen gets aimed. Ported from
 * `legacy-web/packages/bot/src/lib/__tests__/mcts-bot-jack-swap.test.ts` and
 * `mcts-bot-queen-swap.test.ts`.
 *
 * Both cards take two cards from two *different* players — that part is a rule and is checked
 * every time. Which two is judgement, and the original tests check the shape the Kotlin bot
 * should show as well: include yourself when you have a blind spot to fill or a good card to
 * steal, because a swap between two opponents mostly helps whichever of them was behind.
 */
class SwapTargetSelectionTest {

    private val botId = "bot1"
    private val humanId = "human1"

    private fun service() = MctsBotDecisionService(Difficulty.HARD, Random(11))

    private fun contextFor(
        actionCard: Card,
        botCards: List<Card>,
        botKnown: List<Int>,
        humanCards: List<Card>,
        knownOfHuman: Map<Int, Card> = emptyMap(),
    ): BotDecisionContext {
        val botPlayer = testPlayer(
            botId,
            "Bot Player",
            isHuman = false,
            cards = botCards,
            knownCardPositions = botKnown,
        )
        val human = testPlayer(
            humanId,
            "Human Player",
            isHuman = true,
            cards = humanCards,
            knownCardPositions = emptyList(),
        )
        val state = testState(
            players = listOf(botPlayer, human),
            subPhase = GameSubPhase.AWAITING_ACTION,
            turnNumber = 5,
            discardPile = Pile(listOf(testCard(Rank.SIX, "discard-6"))),
        ).copy(
            pendingAction = PendingAction(
                card = actionCard,
                playerId = botId,
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.DRAWING,
                targets = emptyList(),
            ),
        )

        val ownKnowledge = botKnown.associateWith { botCards[it] }

        return botContext(botId, state).copy(
            pendingCard = actionCard,
            activeActionCard = actionCard,
            discardPile = state.discardPile,
            opponentKnowledge = mapOf(botId to ownKnowledge, humanId to knownOfHuman),
        )
    }

    private fun assertTwoTargetsFromDifferentPlayers(decision: BotActionDecision) {
        assertEquals(2, decision.targets.size, "expected exactly two targets")
        assertTrue(
            decision.targets[0].playerId != decision.targets[1].playerId,
            "both targets came from the same player, which the rules forbid",
        )
    }

    private fun botTargets(decision: BotActionDecision) =
        decision.targets.filter { it.playerId == botId }

    // --- Jack -----------------------------------------------------------------------------

    @Test
    fun aJackIncludesTheBotWhenItStillHasABlindSpot() {
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.JACK, "jack-card"),
                botCards = listOf(
                    testCard(Rank.EIGHT, "bot-card-0"),
                    testCard(Rank.NINE, "bot-card-1"),
                    testCard(Rank.TEN, "bot-card-2"),
                ),
                botKnown = listOf(0, 1),
                humanCards = listOf(
                    testCard(Rank.FIVE, "human-card-0"),
                    testCard(Rank.KING, "human-card-1"),
                    testCard(Rank.ACE, "human-card-2"),
                ),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(botTargets(decision).isNotEmpty(), "the bot left itself out of its own Jack")
    }

    @Test
    fun aJackIncludesTheBotWhenAnOpponentIsKnownToHoldAJoker() {
        val joker = testCard(Rank.JOKER, "human-joker")
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.JACK, "jack-card"),
                botCards = listOf(
                    testCard(Rank.EIGHT, "bot-card-0"),
                    testCard(Rank.NINE, "bot-card-1"),
                    testCard(Rank.TEN, "bot-card-2"),
                ),
                botKnown = listOf(0, 1, 2),
                humanCards = listOf(joker, testCard(Rank.KING, "human-card-1")),
                knownOfHuman = mapOf(0 to joker),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(botTargets(decision).isNotEmpty(), "a Joker was there for the taking")
    }

    @Test
    fun aJackIncludesTheBotWhenAnOpponentIsKnownToHoldSomethingCheap() {
        val two = testCard(Rank.TWO, "human-two")
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.JACK, "jack-card"),
                botCards = listOf(
                    testCard(Rank.TEN, "bot-card-0"),
                    testCard(Rank.TEN, "bot-card-1"),
                    testCard(Rank.TEN, "bot-card-2"),
                ),
                botKnown = listOf(0, 1, 2),
                humanCards = listOf(two, testCard(Rank.KING, "human-card-1")),
                knownOfHuman = mapOf(0 to two),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(botTargets(decision).isNotEmpty())
    }

    @Test
    fun aJackAimedAtAKnownJokerActuallySwaps() {
        // The regression that made every solo Jack a no-op: the generator left `shouldSwap`
        // null, the decision service coerced null to false, and the runner skipped the swap
        // it had just aimed. Holding three known tens against a seen Joker, the trade sheds
        // eleven points — a plan that declines it is not a judgement call.
        val joker = testCard(Rank.JOKER, "human-joker")
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.JACK, "jack-card"),
                botCards = listOf(
                    testCard(Rank.TEN, "bot-card-0"),
                    testCard(Rank.TEN, "bot-card-1"),
                    testCard(Rank.TEN, "bot-card-2"),
                ),
                botKnown = listOf(0, 1, 2),
                humanCards = listOf(joker, testCard(Rank.KING, "human-card-1")),
                knownOfHuman = mapOf(0 to joker),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(
            decision.shouldSwap != false,
            "the Jack was aimed and then declined its own swap",
        )
    }

    // --- Queen ----------------------------------------------------------------------------

    @Test
    fun aQueenIncludesTheBotWhenAnOpponentIsKnownToHoldAJoker() {
        val joker = testCard(Rank.JOKER, "human-joker")
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.QUEEN, "queen-card"),
                botCards = listOf(
                    testCard(Rank.EIGHT, "bot-card-0"),
                    testCard(Rank.NINE, "bot-card-1"),
                    testCard(Rank.TEN, "bot-card-2"),
                ),
                botKnown = listOf(0, 1, 2),
                humanCards = listOf(joker, testCard(Rank.KING, "human-card-1")),
                knownOfHuman = mapOf(0 to joker),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(botTargets(decision).isNotEmpty())
    }

    @Test
    fun aQueenIncludesTheBotWhenItStillHasABlindSpot() {
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.QUEEN, "queen-card"),
                botCards = listOf(
                    testCard(Rank.EIGHT, "bot-card-0"),
                    testCard(Rank.NINE, "bot-card-1"),
                    testCard(Rank.TEN, "bot-card-2"),
                ),
                botKnown = listOf(0, 1),
                humanCards = listOf(
                    testCard(Rank.FIVE, "human-card-0"),
                    testCard(Rank.KING, "human-card-1"),
                ),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(
            botTargets(decision).isNotEmpty(),
            "a Queen peeks before deciding, so a blind spot is exactly what it is for",
        )
    }

    @Test
    fun aQueenIncludesTheBotWhenAnOpponentIsKnownToHoldSomethingCheap() {
        val two = testCard(Rank.TWO, "human-two")
        val decision = service().selectActionTargets(
            contextFor(
                actionCard = testCard(Rank.QUEEN, "queen-card"),
                botCards = listOf(
                    testCard(Rank.TEN, "bot-card-0"),
                    testCard(Rank.TEN, "bot-card-1"),
                ),
                botKnown = listOf(0, 1),
                humanCards = listOf(two, testCard(Rank.KING, "human-card-1")),
                knownOfHuman = mapOf(0 to two),
            ),
        )

        assertTwoTargetsFromDifferentPlayers(decision)
        assertTrue(botTargets(decision).isNotEmpty())
    }

    // --- the rule, whatever the judgement --------------------------------------------------

    @Test
    fun theTwoDifferentPlayersRuleHoldsAcrossEveryTableShapeTried() {
        // The judgement above may legitimately shift as the bot is tuned. This may not.
        val shapes = listOf(
            Triple(listOf(0, 1, 2), 3, Rank.JACK),
            Triple(listOf(0), 3, Rank.JACK),
            Triple(emptyList<Int>(), 2, Rank.QUEEN),
            Triple(listOf(0, 1), 5, Rank.QUEEN),
        )

        for ((known, humanHandSize, rank) in shapes) {
            val decision = service().selectActionTargets(
                contextFor(
                    actionCard = testCard(rank, "action-card"),
                    botCards = listOf(
                        testCard(Rank.EIGHT, "b0"),
                        testCard(Rank.NINE, "b1"),
                        testCard(Rank.TEN, "b2"),
                    ),
                    botKnown = known,
                    humanCards = List(humanHandSize) { testCard(Rank.FIVE, "h$it") },
                ),
            )

            assertTwoTargetsFromDifferentPlayers(decision)
        }
    }
}
