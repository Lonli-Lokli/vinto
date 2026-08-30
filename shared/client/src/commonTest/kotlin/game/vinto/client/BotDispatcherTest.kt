package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search runs where it was told to run.
 *
 * `LocalGameSession` takes a `botDispatcher` and the app passes `Dispatchers.Default`, because
 * three bots taking a turn costs up to 1.6 s and a phone drawing a frame cannot wait for it.
 * Nothing checked that the wiring was live. It is one `?.let { withContext(it) }` in
 * `onBotDispatcher`, and a new code path that reaches the runner without going through it
 * would move the search back onto whatever thread called `dispatch` — silently, because the
 * game would still be perfectly correct. It would just stutter, on a device, in a build
 * nobody runs a test on.
 *
 * So the dispatcher is asked whether it was used, from *inside* the block that does the
 * thinking: a [BotDirector] is consulted by `nextBotAction`, which is the innermost point of
 * the bot loop, and it reports what context it was called on.
 *
 * This is the coroutine half of task 6.5. The rest of what the TypeScript `BotAIAdapter` did —
 * the sequential queue, coalition routing, leader selection, opponent tracking — is
 * `BotRunner`, which is a pure function of the state and is shared with the Durable Object;
 * `await delay(...)` for animation has no counterpart here on purpose, because pacing is the
 * UI's job and reaches it as frames.
 */
class BotDispatcherTest {

    /**
     * Runs its blocks inline, so the test stays single-threaded and deterministic on JS and
     * Wasm as well as the JVM — what is under test is *which context*, not concurrency.
     */
    // `kotlinx.coroutines.Runnable`, not `java.lang.Runnable` — the latter is JVM-only and
    // this suite compiles for JS and Wasm too, where the import is the whole difference.
    private class Marking : CoroutineDispatcher() {
        var dispatches = 0
        var inside = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            inside = true
            try {
                block.run()
            } finally {
                inside = false
            }
        }
    }

    /** Reports where it was asked, and always falls through to the real search. */
    private class Watcher(private val dispatcher: Marking) : BotDirector {
        var calls = 0
        var everyCallWasOnTheDispatcher = true

        override fun nextAction(state: GameState): GameAction? {
            calls++
            if (!dispatcher.inside) everyCallWasOnTheDispatcher = false
            return null
        }
    }

    private suspend fun startedGame(
        dispatcher: CoroutineDispatcher?,
        director: BotDirector,
    ): LocalGameSession {
        val session = LocalGameSession(
            seed = SEED,
            difficulty = Difficulty.EASY,
            botDispatcher = dispatcher,
            director = director,
        )
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    @Test
    fun theBotsThinkOnTheDispatcherTheyWereGiven() = runTest {
        val dispatcher = Marking()
        val watcher = Watcher(dispatcher)

        val session = startedGame(dispatcher, watcher)
        assertTrue(session.playItselfOut(seed = SEED), "the game never finished")

        assertTrue(watcher.calls > 0, "the bots never ran at all")
        assertTrue(dispatcher.dispatches > 0, "the injected dispatcher was never used")
        assertTrue(
            watcher.everyCallWasOnTheDispatcher,
            "the search ran on the caller's context ${watcher.calls} times",
        )

        // One hop per run of bot turns, not one per bot: `playBots` wraps the whole sequence,
        // so a table of three bots never bounces back to the drawing thread mid-thought.
        assertTrue(
            watcher.calls > dispatcher.dispatches,
            "one dispatch per decision (${watcher.calls} calls, ${dispatcher.dispatches} hops)",
        )
    }

    @Test
    fun withoutOneTheBotsThinkWhereTheyAreCalled() = runTest {
        val unused = Marking()
        val watcher = Watcher(unused)

        val session = startedGame(dispatcher = null, director = watcher)
        assertTrue(session.playItselfOut(seed = SEED), "the game never finished")

        // The default. Tests want it, because a hop is a scheduling point and a scheduling
        // point is a place two runs can differ.
        assertTrue(watcher.calls > 0, "the bots never ran at all")
        assertEquals(0, unused.dispatches, "a dispatcher nobody was given was used anyway")
        assertEquals(
            false,
            watcher.everyCallWasOnTheDispatcher,
            "the search claimed to be on a dispatcher it was never given",
        )
    }

    private companion object {
        const val SEED = 1234L
    }
}
