package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.shapes.Difficulty
import kotlin.test.Test
import kotlin.test.assertTrue

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
 *
 * The loop itself lives in [playSelfPlayGame], shared with the tournament in
 * [TournamentTest]: legality and strength are two questions about one table, and they should
 * not be asked of two subtly different games.
 */
class SelfPlayGateTest {

    /** Enough games to cross the paths that only show up occasionally — Kings, coalitions. */
    private val gameCount = 12

    @Test
    fun botsPlayCompleteGamesAndEveryActionTheyProposeIsLegal() {
        val played = (1L..gameCount).map { seed -> playSelfPlayGame(seed, Difficulty.MODERATE) }

        val bad = played.filter { !it.finished }
        assertTrue(
            bad.isEmpty(),
            "games that never reached scoring:\n" + bad.joinToString("\n") { it.describe() },
        )

        val moved = played.filter { it.callerHandChanged }
        assertTrue(
            moved.isEmpty(),
            "the caller's hand changed during the final round: " + moved.map { it.seed },
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
        val first = playSelfPlayGame(seed = 7L, difficulty = Difficulty.MODERATE)
        val second = playSelfPlayGame(seed = 7L, difficulty = Difficulty.MODERATE)

        // The bots inject their own Random; two runs from one seed must not diverge, or a
        // recorded game cannot be replayed and a desync cannot be reproduced. The hands are
        // compared as well as the length: two games can take the same number of actions and
        // arrive somewhere else entirely.
        assertTrue(
            first.actions == second.actions &&
                first.finalPhase == second.finalPhase &&
                first.handTotals == second.handTotals,
            "seed 7 played differently twice: $first vs $second",
        )
    }
}
