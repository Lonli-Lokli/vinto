package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.actorId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A person's turn is theirs for as long as they want it.
 *
 * The room has exactly one clock that moves for a human, and it is the toss-in window: that
 * one holds *every* seat, so one person looking at their phone is four people not playing.
 * An ordinary turn holds nobody but the table's patience, and the room waits — there is no
 * deadline on it, and a seat is only ever taken over when its socket has been gone for the
 * seat grace.
 *
 * Reported from a real online game: a human seat was seen playing itself during its own turn.
 * This is the property that says it must not, measured the only way that means anything — by
 * letting the room's alarm fire, over and over, across ten minutes of a person thinking.
 */
class HumanTurnsWaitTest {

    @Test
    fun aHumanSeatIsNeverPlayedForOnItsOwnTurn() {
        val (state, reached) = atAHumanTurn()
        val before = decodeRoom(state)
        val holder = turnHolder(before)

        var current = state
        var clock = reached
        repeat(ALARMS) {
            clock += BETWEEN_ALARMS_MS
            val fired = decodeLifecycle(onAlarm(current, clock))
            assertFalse(fired.deleted, "the room deleted itself while somebody was thinking")
            current = encode(fired.state)
        }

        val after = decodeRoom(current)
        val played = after.log.drop(before.log.size)
        assertTrue(
            played.isEmpty(),
            "the room played ${played.map { it.action }} while it was a person's turn",
        )
        assertEquals(holder, turnHolder(after), "the turn moved on without them")
    }

    /**
     * And the seat is not quietly handed to a bot either.
     *
     * The takeover is what the room does for a socket that has *gone*. A person sitting on a
     * decision is not away, and a seat that turned into a bot under them would play the rest
     * of the round however fast they came back to it.
     */
    @Test
    fun aThinkingSeatIsNotTakenOver() {
        val (state, reached) = atAHumanTurn()
        val holder = decodeRoom(state).seats.first { it.playerId == turnHolder(decodeRoom(state)) }

        var current = state
        var clock = reached
        repeat(ALARMS) {
            clock += BETWEEN_ALARMS_MS
            current = encode(decodeLifecycle(onAlarm(current, clock)).state)
        }

        val seat = decodeRoom(current).seats.first { it.index == holder.index }
        assertFalse(seat.isBot, "a seat whose socket is open was taken over for thinking")
        assertFalse(seat.botPlayedWhileAway, "the room played a seat that never went away")
    }

    // ------------------------------------------------------------------ plumbing

    /** Who the table is waiting on. */
    private fun turnHolder(room: RoomState): String {
        val game = checkNotNull(room.game) { "a dealt room has a game" }
        return game.players[game.currentPlayerIndex].id
    }

    /**
     * Plays a dealt room forward until an ordinary turn belongs to a seated person, with no
     * toss-in window open — the exact situation the report is about, and the one the room has
     * no clock for. Driven by a runner standing in for both people, exactly as the other room
     * suites drive their humans.
     */
    private fun atAHumanTurn(): Pair<String, Double> {
        var state = dealtRoom()
        val person = BotRunner(Difficulty.EASY, Random(SEED))
        var now = START

        repeat(MOVE_LIMIT) {
            val room = decodeRoom(state)
            if (aPersonIsOnTheClock(room)) return state to now

            val game = room.game ?: fail("the room stopped having a game")
            val everySeat = game.copy(
                players = game.players.map { it.copy(isHuman = false, isBot = true) },
            )
            val action = person.nextAction(everySeat) ?: fail("nothing left to drive")
            val actor = action.actorId
            val seat = actor?.let { id -> room.seats.first { it.playerId == id } }
            val token = when {
                seat == null -> TOKEN_A
                seat.index == 0 -> TOKEN_A
                else -> TOKEN_B
            }

            now += MS_BETWEEN_MOVES
            val result = decodeAction(applyAction(state, token, actionJson(action), now))
            check(result.error == null) { "move refused: ${result.error}" }
            state = encode(result.state)
        }
        fail("no ordinary human turn came round in $MOVE_LIMIT moves")
    }

    /** A seated person's ordinary turn: the game is in play and no window is holding anyone. */
    private fun aPersonIsOnTheClock(room: RoomState): Boolean {
        if (room.phase != RoomPhase.PLAYING) return false
        val game = room.game ?: return false
        if (game.phase != GamePhase.PLAYING) return false
        if (game.activeTossIn?.waitingForInput == true) return false
        val holder = game.players.getOrNull(game.currentPlayerIndex) ?: return false
        val seat = room.seats.firstOrNull { it.playerId == holder.id } ?: return false
        return seat.tokenHash != null && !seat.isBot
    }

    /**
     * The invariant, over whole rounds rather than at one moment.
     *
     * The room may make exactly one move on a person's behalf: finishing their toss-in when the
     * fifteen-second window runs out, because that window holds every other seat too. Anything
     * else it plays for a seated, connected person is the defect this test is named after.
     *
     * Measured over a whole round and then a second one, because a deal remaps every seat's
     * player id and a stale mapping would make the room's own guard — which finds a seat by the
     * action's actor — quietly find nobody, and a guard that finds nobody lets everything past.
     */
    @Test
    fun theRoomOnlyEverFinishesAPersonsTossIn() {
        for (seed in SEEDS) {
            var state = dealtRoom(seed = seed.toDouble())
            state = playRoundOut(state, seed, START)
            blamed(state, seed, round = 1)

            val scored = decodeRoom(state)
            if (scored.phase != RoomPhase.BETWEEN_ROUNDS) continue

            state = encode(decodeJoin(readyForNextRound(state, TOKEN_A, START)).state)
            state = encode(decodeJoin(readyForNextRound(state, TOKEN_B, START)).state)
            if (decodeRoom(state).phase != RoomPhase.PLAYING) continue

            state = playRoundOut(state, seed, START)
            blamed(state, seed, round = 2)
        }
    }

    /** Fails naming every move the room made for somebody who was sitting there. */
    private fun blamed(state: String, seed: Long, round: Int) {
        val room = decodeRoom(state)
        val played = room.log.filter { entry ->
            val seat = room.seats.firstOrNull { it.index == entry.seat }
            entry.byBot &&
                seat?.tokenHash != null &&
                !seat.isBot &&
                entry.action !is GameAction.PlayerTossInFinished
        }
        assertTrue(
            played.isEmpty(),
            "seed $seed, round $round: the room played " +
                played.joinToString { "seat ${it.seat} ${it.action}" } +
                " for a person who was sitting there",
        )
    }

    private companion object {
        const val SEED = 42L
        val SEEDS = listOf(42L, 7L, 11L)
        const val MOVE_LIMIT = 600
        const val MS_BETWEEN_MOVES = 2_000.0

        /** Ten minutes of somebody thinking, in the alarm steps a real room would take. */
        const val ALARMS = 30
        const val BETWEEN_ALARMS_MS = 20_000.0
    }
}
