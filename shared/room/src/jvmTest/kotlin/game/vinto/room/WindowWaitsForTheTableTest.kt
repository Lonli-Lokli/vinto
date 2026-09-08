package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.Difficulty
import game.vinto.shapes.actorId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The toss-in clock counts a person thinking, not a table still moving.
 *
 * The window is the one place online play cannot simply wait — it holds *every* seat — so the
 * room finishes it for whoever it out-waits. That is fair only if the fifteen seconds are
 * fifteen seconds of somebody deciding. They were not: the deadline was stamped the moment the
 * window opened server-side, and what the room sends back in that same response is the whole
 * batch of moves that led to it — three bots' turns, each a card lifted, flown, turned over
 * and read. The client plays those out before the window is answerable at all, and on a calm
 * pace that is comfortably over ten seconds. So the window arrived with a third of it left,
 * and the player watched the rest of their own thinking time run out during an animation.
 *
 * Reported from a real game, in as many words: the auto-advance must not run while there is
 * still a set of actions to be played out; it is for waiting on the next person.
 *
 * So the clock carries the batch it arrived behind. The room cannot watch a client animate,
 * but it knows exactly how many moves it just sent, and what one costs to watch is a number
 * this repository already has (`Pacing`).
 */
class WindowWaitsForTheTableTest {

    @Test
    fun aWindowThatArrivesBehindABatchOfMovesWaitsForThemToPlay() {
        val opened = windowOpenedBehindMoves()

        val thinking = opened.deadline - opened.at
        val forTheMoves = opened.moves * ANIMATION_PER_MOVE_MS
        assertTrue(
            thinking >= TOSS_IN_MS + forTheMoves,
            "the window arrived behind ${opened.moves} moves and gave ${thinking.toLong()} ms — " +
                "the moves alone take about ${forTheMoves.toLong()} ms to watch, so the person " +
                "is left ${(thinking - forTheMoves).toLong()} ms of the ${TOSS_IN_MS.toLong()} " +
                "the window is supposed to be",
        )
    }

    /** And a window with nothing to watch first is the window it always was, to the millisecond. */
    @Test
    fun aWindowWithNothingToPlayFirstIsExactlyTheWindow() {
        val open = windowOpenedBehindMoves()
        val idle = withPacing(decodeRoom(encode(open.state)).copy(tossInDeadlineEpochMs = null), LATER)

        assertTrue(
            idle.tossInDeadlineEpochMs == LATER + TOSS_IN_MS,
            "a window nobody has to watch anything for is ${idle.tossInDeadlineEpochMs} " +
                "rather than ${LATER + TOSS_IN_MS}",
        )
    }

    // ------------------------------------------------------------------ plumbing

    private data class Opened(
        val state: RoomState,
        /** When the response that opened it was served. */
        val at: Double,
        val deadline: Double,
        /** Moves that came back in the same response, for the client to play out. */
        val moves: Int,
    )

    /**
     * Plays a dealt room until one response both opens a toss-in window and carries moves the
     * client has to watch first — which is the ordinary case, not a corner: a window opens on
     * a discard, and the discard that opens it is usually the last of a run of bot turns.
     */
    private fun windowOpenedBehindMoves(): Opened {
        SEEDS.forEach { seed -> playedForward(seed)?.let { return it } }
        fail("no seed opened a toss-in window behind a batch of moves")
    }

    /** One room, played move by move until a window opens behind a batch, or it runs out. */
    private fun playedForward(seed: Long): Opened? {
        var state = dealtRoom(seed = seed.toDouble())
        val person = BotRunner(Difficulty.EASY, Random(seed))
        var now = START

        repeat(MOVE_LIMIT) {
            val before = decodeRoom(state)
            now += MS_BETWEEN_MOVES
            state = oneMove(before, state, person, now) ?: return null
            openedHere(before, decodeRoom(state), now)?.let { return it }
        }
        return null
    }

    /** One move by whichever seated person the runner wants next, through the room's own door. */
    private fun oneMove(before: RoomState, state: String, person: BotRunner, at: Double): String? {
        if (before.phase != RoomPhase.PLAYING) return null
        val game = before.game ?: return null

        val everySeat = game.copy(
            players = game.players.map { it.copy(isHuman = false, isBot = true) },
        )
        val action = person.nextAction(everySeat) ?: return null
        val seat = action.actorId?.let { id -> before.seats.first { it.playerId == id } }
        if (seat != null && seat.tokenHash == null) return null

        val token = if (seat == null || seat.index == 0) TOKEN_A else TOKEN_B
        val result = decodeAction(applyAction(state, token, actionJson(action), at))
        return if (result.error == null) encode(result.state) else null
    }

    /** Whether this move's response is the one: a fresh window, behind moves to watch. */
    private fun openedHere(before: RoomState, after: RoomState, at: Double): Opened? {
        if (before.tossInDeadlineEpochMs != null) return null
        val deadline = after.tossInDeadlineEpochMs ?: return null
        val watched = after.log.drop(before.log.size).count { it.byBot }
        return if (watched >= FEW) Opened(after, at, deadline, watched) else null
    }

    private companion object {
        const val MOVE_LIMIT = 400
        const val MS_BETWEEN_MOVES = 2_000.0
        val SEEDS = listOf(42L, 7L, 11L, 3L)

        /** `RoomCore`'s own numbers, pinned here so a drift fails the test that reads them. */
        const val TOSS_IN_MS = 15_000.0
        const val ANIMATION_PER_MOVE_MS = 1_200.0

        /** Enough moves for the wait to be the point rather than a rounding. */
        const val FEW = 2
    }
}
