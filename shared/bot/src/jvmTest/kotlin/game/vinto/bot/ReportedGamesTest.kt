package game.vinto.bot

import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
import game.vinto.shapes.Rank
import game.vinto.shapes.VintoJson
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Games a player reported from the table, replayed up to the moment they complained about,
 * with the bot asked again what it would do there.
 *
 * Each recording under `reports/` is the file the app's "report a problem" writes, kept as a
 * test fixture rather than in the frozen corpus — it carries no cross-implementation hash
 * that matters, only a position. The runner observes every action on the way there, as it
 * does in a real game, so the bot's memory is what it would have been.
 */
class ReportedGamesTest {
    private fun recording(name: String): GameRecording {
        val text = checkNotNull(javaClass.getResource("/reports/$name.json")) { "no report $name" }.readText()
        return VintoJson.decodeFromString(GameRecording.serializer(), text)
    }

    /** The state after [upTo] actions, with [runner] having watched every one of them. */
    private fun replayed(recording: GameRecording, upTo: Int, runner: BotRunner): GameState {
        var state = recording.initialState
        for (entry in recording.actions.take(upTo)) {
            val result = GameEngine.reduce(state, entry.action)
            check(result !is ReduceResult.Failure) { "the report does not replay at " + entry.action.type }
            val after = result.state
            runner.observe(entry.action, state, after)
            state = after
        }
        return state
    }

    @Test
    fun aDrawnJokerIsNeverThrownAway() {
        // Reported 2026-09-06: Ember drew the Joker with two fives it knew about in hand, and
        // put it on the pile. A Joker is worth -1; keeping it in place of any card is better,
        // and in place of a known five it is six points better. No seed should throw it away.
        val report = recording("joker-discarded")
        val drawIndex = report.actions.indexOfLast { it.action is GameAction.DrawCard }
        val bad = mutableListOf<String>()
        for (seed in 1..5) {
            val runner = BotRunner(random = Random(seed))
            val state = replayed(report, upTo = drawIndex + 1, runner = runner)
            val drawn = checkNotNull(state.pendingAction).card
            assertTrue(drawn.rank == Rank.JOKER, "the report's last draw is not the Joker: " + drawn.rank)
            val next = runner.nextAction(state)
            if (next !is GameAction.SwapCard) bad += "seed $seed: $next"
        }
        assertTrue(bad.isEmpty(), "a drawn Joker was thrown away:\n" + bad.joinToString("\n"))
    }
}
