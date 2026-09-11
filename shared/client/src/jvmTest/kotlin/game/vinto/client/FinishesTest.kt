package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.engine.ActionValidator
import game.vinto.engine.Validation
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameState
import game.vinto.shapes.actorId
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Every game finishes.
 *
 * The one property a solo card game cannot do without, and the one the other tests were not
 * checking: they drove particular situations and asserted particular answers, so a position
 * where *nobody* has a legal move went unnoticed until a real round hung on a phone.
 *
 * A stall is not a wrong answer, which is what makes it easy to miss. The engine is right, the
 * bot is right about what it wants, and the game stops — because what the bot wants is a move
 * the rules forbid, and it has no second choice. From the player's side it looks like the app
 * has frozen.
 *
 * Runs on the JVM only. What it checks is common code and platform-independent — a position
 * nobody can move in is the same position everywhere — and a hundred-odd full games is minutes
 * on an iOS simulator for no extra signal. The cross-platform tests next door cover *running*
 * on each target.
 */
class FinishesTest {

    /** The person's seat, driven by the same brain, so a whole game can be played out. */
    private fun GameState.everySeatPlayable(): GameState =
        copy(players = players.map { it.copy(isHuman = false, isBot = true) })

    /**
     * Drives one seat's brain over all four seats until the round ends or the drive gives out.
     *
     * A function of its own so the case above stays readable — and because the loop answers two
     * different kinds of thing: moves, and the coalition's window, which is not one.
     *
     * @return how many it made, for the report.
     */
    private suspend fun playOut(session: LocalGameSession, person: BotRunner): Int {
        var moves = 0
        while (!session.isOver && moves < MOVE_LIMIT) {
            // The window is the one thing a seat answers with something that is not a
            // `GameAction`, so a drive made only of actions waits in it forever. The person
            // here is this loop; a person at a table presses the button.
            if (session.view.value.conferMsRemaining != null) {
                session.doneConferring()
                moves++
                continue
            }

            val action = person.nextAction(session.state.everySeatPlayable()) ?: return moves
            if (session.dispatch(action) != null) return moves
            moves++
        }
        return moves
    }

    @Test
    fun aWholeGamePlaysItselfOutFromAnySeed() = runTest(timeout = LONG) {
        val stalled = mutableListOf<String>()

        for (seed in 1L..SEEDS) {
            val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
            val moves = playOut(session, BotRunner(Difficulty.EASY, Random(seed)))
            if (session.isOver) continue

            // Say *why* it stopped. A stall reported as "did not finish" is a stall somebody
            // has to reproduce before they can start; reported like this it is a bug report.
            val state = session.state
            val wanted = BotRunner(Difficulty.EASY, Random(seed)).nextAction(state)
            val verdict = wanted?.let { ActionValidator.validate(state, it) }
            stalled += "seed $seed after $moves moves: phase=${state.phase} " +
                "sub=${state.subPhase} card=${state.pendingAction?.card?.rank} " +
                "target=${state.pendingAction?.targetType} " +
                "wanted=${wanted?.let { it::class.simpleName }} by ${wanted?.actorId} " +
                "→ ${(verdict as? Validation.Invalid)?.reason ?: verdict}"
        }

        assertTrue(stalled.isEmpty(), "games that never finished:\n${stalled.joinToString("\n")}")
    }

    private companion object {
        const val SEEDS = 120L
        const val MOVE_LIMIT = 600
        val LONG = 10.minutes
    }
}
