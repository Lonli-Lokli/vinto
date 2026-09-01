package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What may be read back out of the search tree as a plan.
 *
 * The plan for a two-part decision is the most-visited child of the committed move — but the
 * model does not play a King's borrowed action out as its own ply, so after a King move that
 * child is simply the next player's turn. Reading it anyway cached another seat's targets as
 * this bot's plan, and spending that plan on the borrowed card is how a Queen came to peek
 * two *other* players' cards in an ordinary round, its own hand left out of its own action.
 */
class ActionPlanningTest {

    private fun state(botId: String) = MctsGameState(
        players = listOf(
            MctsPlayerState(botId, cardCount = 4),
            MctsPlayerState("human-1", cardCount = 4),
            MctsPlayerState("bot-2", cardCount = 4),
        ),
        currentPlayerIndex = 0,
        botPlayerId = botId,
        botMemory = BotMemory(botId, Difficulty.HARD, Random(1)),
    )

    private fun committed(move: MctsMove, followUp: MctsMove): MctsNode {
        val bestChild = MctsNode(state("bot-1"), move, parent = null)
        val next = MctsNode(state("bot-1"), followUp, bestChild)
        bestChild.addChild(next)
        next.backpropagate(0.0)
        return bestChild
    }

    @Test
    fun anotherSeatsMoveIsNeverReadAsThisBotsPlan() {
        val kingDeclares = MctsMove(
            MctsMoveType.USE_ACTION,
            "bot-1",
            targets = listOf(MctsActionTarget("bot-1", 0)),
            declaredRank = Rank.QUEEN,
        )
        val nextPlayersJack = MctsMove(
            MctsMoveType.USE_ACTION,
            "human-1",
            targets = listOf(
                MctsActionTarget("human-1", 1),
                MctsActionTarget("bot-2", 0),
            ),
            shouldSwap = true,
        )

        assertNull(
            extractActionPlan(committed(kingDeclares, nextPlayersJack)),
            "the next player's move was read back as this bot's plan",
        )
    }

    @Test
    fun theSameSeatsAimingMoveIsStillAPlan() {
        val takeDiscard = MctsMove(
            MctsMoveType.TAKE_DISCARD,
            "bot-1",
            actionCard = testCard(Rank.QUEEN, "queen-on-pile"),
        )
        val aimIt = MctsMove(
            MctsMoveType.USE_ACTION,
            "bot-1",
            targets = listOf(
                MctsActionTarget("bot-1", 2),
                MctsActionTarget("human-1", 1),
            ),
            shouldSwap = true,
        )

        val plan = extractActionPlan(committed(takeDiscard, aimIt))
        assertEquals(
            listOf(BotActionTarget("bot-1", 2), BotActionTarget("human-1", 1)),
            plan?.targets,
            "the bot's own follow-up is exactly what the cache is for",
        )
    }
}
