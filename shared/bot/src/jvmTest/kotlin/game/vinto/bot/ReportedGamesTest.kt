package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
import game.vinto.shapes.Rank
import game.vinto.shapes.VintoJson
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /**
     * Every card thrown into one window plays its action, not just the first.
     *
     * Reported 2026-09-16: *"there was 3 tossin 9 by 3 bots but only 1 has been played."* The
     * recording is exactly that — Ember, Tide and Dune each throw a nine at bot-1's discard,
     * and the pile ends with four nines on it. Only Ember's peeked at anything; the other two
     * went down without a target.
     *
     * The engine is not what refuses them. It materialises the first queued card at
     * `selecting-target` and every one after it at `choosing-action`
     * (`clearTossInAfterActionableCard`), and `ActionValidator` does not read that field at all
     * — a target is legal from either, which is why a *person* throwing in second has always
     * been able to aim. It is [BotRunner.turnAction] that reads it, to catch a different case:
     * a card drawn before a window opened comes back at `choosing-action` with nothing that can
     * be done to it, and is put down rather than played. A queued throw looks identical and is
     * the opposite thing.
     *
     * So the two are told apart by the queue itself, which is the engine's own definition of
     * the difference (`ActionValidator.isProcessingTossInAction`, now shared as
     * `resolvingATossIn`). Fixing it in the bot rather than in the engine is not a dodge: the
     * phase is inside the canonical hash and moving it diverges **25 of the 50** parity
     * recordings, half of them in the first fifty actions — which would cost most of the
     * corpus's cross-implementation evidence to change a field nothing reads.
     */
    @Test
    fun everyNineThrownIntoTheWindowPlaysItsAction() {
        val report = recording("three-nines-tossed-in-one-played")

        // The moment the report is about: the three nines are in, and the person saying they
        // are done is what closes the window and starts the queue.
        val lastThrow = report.actions.indexOfLast { it.action is GameAction.ParticipateInTossIn }
        val opens = report.actions.drop(lastThrow).indexOfFirst { entry ->
            val action = entry.action
            action is GameAction.PlayerTossInFinished && action.payload.playerId == "human-1"
        } + lastThrow
        val runner = BotRunner(random = Random(1))
        var state = replayed(report, upTo = opens + 1, runner = runner)

        val throwers = checkNotNull(state.activeTossIn).queuedActions.map { it.playerId }
        assertEquals(3, throwers.size, "the report's window does not hold three throws")

        // Drive the queue to the end, exactly as a session does: one action at a time, each
        // through the validator, each observed.
        val aimed = mutableListOf<String>()
        var steps = 0
        while (state.activeTossIn?.queuedActions.orEmpty().isNotEmpty() && steps++ < STEP_LIMIT) {
            val action = runner.nextAction(state) ?: break
            check(ActionValidator.validate(state, action) !is Validation.Invalid) {
                "the bot proposed ${action.type}, which the validator refuses"
            }
            val after = (GameEngine.reduce(state, action) as ReduceResult.Success).state
            if (action is GameAction.SelectActionTarget) aimed += action.payload.playerId
            runner.observe(action, state, after)
            state = after
        }

        // Before the fix this was ["bot-1"]: the first throw aimed, and the two behind it were
        // put down unplayed with a bare CONFIRM_PEEK.
        assertEquals(
            throwers,
            aimed,
            "a nine was thrown in and never played — the window's throws, in order, were $throwers",
        )
    }

    private companion object {
        /** Three peeks is six actions; the bound is only there so a defect cannot hang the suite. */
        const val STEP_LIMIT = 40
    }
}
