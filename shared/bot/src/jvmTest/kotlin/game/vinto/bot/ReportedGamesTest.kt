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

    @Test
    fun theTableFollowsTheJokerWhenAQueenMovesIt() {
        // Reported 2026-09-06, as two complaints that turned out to be one defect. Ember
        // swapped the Joker into its own row in front of everybody; Tide's Queen then moved
        // it to Tide's hand. Dune aimed its own Queen at the row the Joker had left, and all
        // three seats went on declaring "Ember has the Joker" through the coalition round —
        // Ember held 4,4, and the Joker was in the caller's hand.
        //
        // The cause was in the engine, not the bot: a Jack or a Queen moved two cards and
        // left every watcher's memory pinned to the old addresses. What is asserted is the
        // property, not the two moves: `opponentKnowledge` is the engine's record of what a
        // seat has been *shown*, so an entry that does not match the card lying there is the
        // engine having told that seat something false.
        val report = recording("joker-tracked-across-a-swap")
        var state = report.initialState
        val untrue = mutableListOf<String>()

        report.actions.forEachIndexed { index, entry ->
            val result = GameEngine.reduce(state, entry.action)
            check(result !is ReduceResult.Failure) { "the report does not replay at " + entry.action.type }
            state = result.state
            for (seat in state.players) {
                for ((ownerId, about) in seat.opponentKnowledge.orEmpty()) {
                    val owner = state.players.first { it.id == ownerId }
                    for ((position, believed) in about.knownCards) {
                        val truth = owner.cards.getOrNull(position)
                        if (truth?.rank == believed.rank) continue
                        untrue += "#$index ${entry.action.type}: ${seat.id} believes " +
                            "$ownerId@$position is ${believed.rank}, it is ${truth?.rank}"
                    }
                }
            }
        }

        // Before the fix this reported 898 of them, the first at the Queen swap on move 159.
        assertTrue(untrue.isEmpty(), "seats were told untrue things:\n" + untrue.take(10).joinToString("\n"))
    }
}
