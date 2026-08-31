package game.vinto.bot

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SerializedOpponentKnowledge
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The engine's record of what a seat has seen of the other seats reaches the bot's
 * decisions — through the real [BotRunner], not a hand-built context.
 *
 * The engine writes `PlayerState.opponentKnowledge` on every peek, reveal and watched
 * swap-in, and renumbers it as hands shrink. For a long time the runner threw that record
 * away at the context boundary, so a bot that had peeked an opponent's Joker forgot it by
 * its next decision. These tests pin the wiring: what the engine says this seat has seen is
 * what the seat plays on.
 */
class OpponentKnowledgeFlowTest {

    private val botId = "bot1"
    private val humanId = "human1"

    private fun jackState(botKnowsTheJoker: Boolean): GameState {
        val human = testPlayer(
            humanId,
            "Human",
            isHuman = true,
            cards = listOf(
                testCard(Rank.NINE, "h-0"),
                testCard(Rank.NINE, "h-1"),
                testCard(Rank.JOKER, "h-joker"),
                testCard(Rank.NINE, "h-3"),
            ),
            knownCardPositions = emptyList(),
        )
        val bot = testPlayer(
            botId,
            "Bot",
            isHuman = false,
            cards = listOf(testCard(Rank.TEN, "b-0"), testCard(Rank.TEN, "b-1")),
        ).copy(
            opponentKnowledge = if (botKnowsTheJoker) {
                mapOf(humanId to SerializedOpponentKnowledge(mapOf(2 to human.cards[2])))
            } else {
                null
            },
        )

        return testState(
            players = listOf(bot, human),
            subPhase = GameSubPhase.AWAITING_ACTION,
            turnNumber = 5,
            discardPile = Pile(listOf(testCard(Rank.SIX, "discard-6"))),
        ).copy(
            pendingAction = PendingAction(
                card = testCard(Rank.JACK, "jack-card"),
                playerId = botId,
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.DRAWING,
                targets = emptyList(),
            ),
        )
    }

    @Test
    fun aJokerTheBotOnceSawIsAimedAtThroughTheRunner() {
        // The Joker hides at position 2 of a four-card hand whose other cards are identical
        // nines. A bot that remembers its earlier peek aims the Jack exactly there; a bot
        // without the memory has no reason to prefer that slot over any other.
        val runner = BotRunner(Difficulty.HARD, Random(11))
        var state = jackState(botKnowsTheJoker = true)

        val aimed = mutableListOf<Pair<String, Int>>()
        repeat(2) {
            val action = runner.nextAction(state)
            assertTrue(action is GameAction.SelectActionTarget, "expected a target, got $action")
            val payload = action.payload as SelectActionTargetPayload.Positional
            aimed += payload.targetPlayerId to payload.position
            state = when (val r = game.vinto.engine.GameEngine.reduce(state, action)) {
                is game.vinto.engine.ReduceResult.Success -> r.state
                is game.vinto.engine.ReduceResult.Failure -> error("engine refused: ${r.reason}")
            }
        }

        assertTrue(
            (humanId to 2) in aimed,
            "the bot forgot the Joker it saw at position 2 — it aimed at $aimed",
        )
    }

    private fun queenState(ownPeeked: Rank, theirsPeeked: Rank): GameState {
        val human = testPlayer(
            humanId,
            "Human",
            isHuman = true,
            cards = listOf(testCard(Rank.NINE, "h-0"), testCard(theirsPeeked, "h-peeked")),
            knownCardPositions = emptyList(),
        )
        val bot = testPlayer(
            botId,
            "Bot",
            isHuman = false,
            cards = listOf(testCard(ownPeeked, "b-peeked"), testCard(Rank.EIGHT, "b-1")),
        )

        return testState(
            players = listOf(bot, human),
            subPhase = GameSubPhase.AWAITING_ACTION,
            turnNumber = 5,
            discardPile = Pile(listOf(testCard(Rank.SIX, "discard-6"))),
        ).copy(
            pendingAction = PendingAction(
                card = testCard(Rank.QUEEN, "queen-card"),
                playerId = botId,
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.DRAWING,
                targets = listOf(
                    ActionTarget(botId, 0, bot.cards[0]),
                    ActionTarget(humanId, 1, human.cards[1]),
                ),
            ),
        )
    }

    @Test
    fun aQueenActsOnWhatItActuallyPeeked() {
        // The peek used to be discarded — the service read a context field nothing set, so
        // the swap decision ran blind on the opponent's card. Now the peeked pair reaches
        // memory through the runner, and the decision follows the values: give away a ten
        // for their ace, keep an ace against their ten.
        val goodTrade = BotRunner(Difficulty.HARD, Random(7))
            .nextAction(queenState(ownPeeked = Rank.TEN, theirsPeeked = Rank.ACE))
        assertTrue(
            goodTrade is GameAction.ExecuteQueenSwap,
            "peeked a ten-for-ace trade and refused it: $goodTrade",
        )

        val badTrade = BotRunner(Difficulty.HARD, Random(7))
            .nextAction(queenState(ownPeeked = Rank.ACE, theirsPeeked = Rank.TEN))
        assertTrue(
            badTrade is GameAction.SkipQueenSwap,
            "peeked an ace-for-ten trade and took it: $badTrade",
        )
    }
}
