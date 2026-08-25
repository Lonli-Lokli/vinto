package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.initializeGame
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The gate.
 *
 * The bot is not required to make the same decisions as the TypeScript one — that latitude
 * was given deliberately, and the heuristics differ in places. It **is** required to follow
 * the rules, and this is what checks that: four Kotlin bots play whole games through the real
 * [GameEngine], and every single action they propose is put through [ActionValidator] first.
 *
 * Two things could go wrong that only a whole game finds:
 *
 * - an **illegal** action. The validator is the same anti-cheat boundary the Durable Object
 *   runs, so anything it rejects is something a live room would reject too — leaving a bot
 *   stuck mid-turn, waiting for an engine that has refused it.
 * - a **stall**. Every action can be individually legal while the game never finishes,
 *   because two states hand back and forth. That is why reaching `scoring` is asserted rather
 *   than just "nothing threw".
 */
class SelfPlayGateTest {

    /** Enough games to cross the paths that only show up occasionally — Kings, coalitions. */
    private val gameCount = 12

    /** A whole game is a few hundred actions; well past that means it has stopped advancing. */
    private val actionLimit = 1_500

    private data class PlayedGame(
        val seed: Long,
        val actions: Int,
        val finalPhase: GamePhase,
        val calledVinto: Boolean,
        val stalledAt: String = "",
    )

    @Test
    fun botsPlayCompleteGamesAndEveryActionTheyProposeIsLegal() {
        val played = (1L..gameCount).map { seed -> playGame(seed) }

        val unfinished = played.filter { it.finalPhase != GamePhase.SCORING }
        assertTrue(
            unfinished.isEmpty(),
            "games that never reached scoring:\n" + unfinished.joinToString("\n") {
                "  seed ${it.seed}: ${it.finalPhase.serialName} after ${it.actions} actions — ${it.stalledAt}"
            },
        )

        // A game that ends only because the deck ran dry is a game the bots never took charge
        // of. Vinto being called at all is what says the endgame logic is reachable in play.
        assertTrue(
            played.any { it.calledVinto },
            "no bot called Vinto in $gameCount games; the endgame is unreachable in practice",
        )
    }

    @Test
    fun aSeedReplaysToTheSameGame() {
        val first = playGame(seed = 7L)
        val second = playGame(seed = 7L)

        // The bots inject their own Random; two runs from one seed must not diverge, or a
        // recorded game cannot be replayed and a desync cannot be reproduced.
        assertTrue(
            first.actions == second.actions && first.finalPhase == second.finalPhase,
            "seed 7 played differently twice: $first vs $second",
        )
    }

    /**
     * Runs one game to completion, refusing anything illegal on the way.
     *
     * The bots' Random is seeded from the game seed rather than left to the default, so a
     * failure here names a seed that reproduces it exactly.
     */
    private fun playGame(seed: Long): PlayedGame {
        var state = allBots(initializeGame(seed, Difficulty.MODERATE))
        val runner = BotRunner(Difficulty.MODERATE, Random(seed))

        var actions = 0
        var calledVinto = false
        var callerFrozenHand: List<String>? = null

        while (actions < actionLimit && state.phase != GamePhase.SCORING) {
            val action = runner.nextAction(state) ?: return PlayedGame(
                seed, actions, state.phase, calledVinto,
                stalledAt = "subPhase=${state.subPhase.serialName} " +
                    "current=${state.players.getOrNull(state.currentPlayerIndex)?.id} " +
                    "isBot=${state.players.getOrNull(state.currentPlayerIndex)?.isBot} " +
                    "pending=${state.pendingAction?.card?.rank?.serialName}" +
                    "(${state.pendingAction?.targets?.size}) " +
                    "tossIn=${state.activeTossIn?.ranks?.map { it.serialName }} " +
                    "ready=${state.activeTossIn?.playersReadyForNextTurn} " +
                    "queued=${state.activeTossIn?.queuedActions?.size} " +
                    "draw=${state.drawPile.size}",
            )

            when (val validation = ActionValidator.validate(state, action)) {
                is Validation.Invalid -> fail(
                    "seed $seed, action #$actions: the bot proposed an illegal " +
                        "${action.type} — ${validation.reason}\n" +
                        "phase=${state.phase.serialName} subPhase=${state.subPhase.serialName} " +
                        "current=${state.players.getOrNull(state.currentPlayerIndex)?.id} " +
                        "pending=${state.pendingAction?.card?.rank?.serialName}" +
                        "(${state.pendingAction?.targets?.size} targets)",
                )

                Validation.Valid -> Unit
            }

            when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> state = result.state
                is ReduceResult.Failure -> fail(
                    "seed $seed, action #$actions: the engine rejected a validated " +
                        "${action.type} — ${result.reason}",
                )
            }

            if (action is GameAction.CallVinto) {
                calledVinto = true
                // The caller's hand is frozen from the call: the caller cannot toss in and
                // the coalition cannot target it, so whatever it holds now, it scores.
                callerFrozenHand = state.players
                    .first { it.id == state.vintoCallerId }.cards.map { it.id }
            }
            actions++
        }

        callerFrozenHand?.let { frozen ->
            assertEquals(
                frozen,
                state.players.first { it.id == state.vintoCallerId }.cards.map { it.id },
                "seed $seed: the caller's hand changed during the final round",
            )
        }

        return PlayedGame(seed, actions, state.phase, calledVinto)
    }

    /** Every seat is a bot, so the runner drives the whole table. */
    private fun allBots(state: GameState): GameState =
        state.copy(players = state.players.map { it.copy(isHuman = false, isBot = true) })
}
