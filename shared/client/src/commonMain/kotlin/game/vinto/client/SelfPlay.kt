package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.shapes.Difficulty
import kotlin.random.Random

/**
 * Plays the person's seat with the bots' own brain until the round is over.
 *
 * Exists for tests — `FinishesTest` proves every game terminates this way, and the UI
 * package's full-game test needs the same drive without the internal state this module keeps
 * to itself: choosing a move takes the full state, the view is redacted by design, and a
 * public helper is a narrower door than a public state.
 *
 * Every move still goes through [LocalGameSession.dispatch], so the session records, narrates
 * and emits frames exactly as it does for a person — which is the point for a UI test: the
 * screen above cannot tell this from a fast player.
 *
 * @param seed for the stand-in player's own decisions; the game already has its seed.
 * @return true if the round reached its scoring, false if the drive gave out first — a
 *   refused move, a silent brain, or the move limit, each of which a caller should fail on.
 */
suspend fun LocalGameSession.playItselfOut(seed: Long, moveLimit: Int = MOVE_LIMIT): Boolean {
    val person = BotRunner(Difficulty.EASY, Random(seed))
    var moves = 0

    while (!isOver && moves < moveLimit) {
        // Every seat marked playable, so the brain will answer for the person's. The bots'
        // own moves never reach dispatch: each accepted action plays them forward before it
        // returns, so the next thing wanted is always the person's again.
        val everySeat = state.copy(
            players = state.players.map { it.copy(isHuman = false, isBot = true) },
        )
        val action = person.nextAction(everySeat) ?: return false
        if (dispatch(action) != null) return false
        moves++
    }

    return isOver
}

/** FinishesTest's bound: no terminating game has come within half of it. */
private const val MOVE_LIMIT = 600
