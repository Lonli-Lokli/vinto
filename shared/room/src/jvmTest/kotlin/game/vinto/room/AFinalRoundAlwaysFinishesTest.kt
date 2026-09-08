package game.vinto.room

import game.vinto.protocol.RoomPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A final round a *person* called always finishes.
 *
 * Found by playing rooms out through the real Worker: a table reached the final round, stopped
 * at a bot's turn, and stayed there. Every seat was waiting on the room, and the room plays its
 * bots inside a request — so with nobody left to send one, the table sat until the session
 * buzzer half an hour later.
 *
 * The wake-up added beside this (`owesMoveAtEpochMs`) makes the room ask again every five
 * seconds, which is the right safety net and not a fix: it turned a permanent stop into a
 * permanent retry. The thing being retried is what this test is about — whether the room's own
 * driver has a move to make at all when a coalition it is playing includes a person.
 *
 * The shape matters and is why the existing suites missed it. `SelfPlayGateTest` plays four
 * bots, so every coalition member is one; `playRoundOut` lets the brain decide who calls Vinto.
 * Here a **human** calls it, which leaves a coalition of one person and two bots — the ordinary
 * online case, and the one nothing was driving to its end.
 */
class AFinalRoundAlwaysFinishesTest {

    @Test
    fun aRoundAPersonCallsVintoInReachesScoring() {
        val stuck = SEEDS.mapNotNull { seed -> whereItStops(seed) }

        assertTrue(
            stuck.isEmpty(),
            "the final round stopped and never restarted:\n" + stuck.joinToString("\n"),
        )
    }

    /**
     * Plays a room out with seat 0 calling Vinto, and reports where it stopped, or null.
     *
     * The branching IS the test: it drives a table through every phase a real one passes, and
     * splitting it into helpers would spread one readable walk across the file for a number.
     */
    @Suppress("CognitiveComplexMethod")
    private fun whereItStops(seed: Long): String? {
        var state = dealtRoom(seed = seed.toDouble())
        var now = START
        var called = false

        repeat(MOVE_LIMIT) {
            val room = decodeRoom(state)
            if (room.phase != RoomPhase.PLAYING) return null
            val game = room.game ?: return null
            if (game.phase == GamePhase.SCORING) return null

            // Only the people act. The bots are the room's, and it plays them in the answer to
            // whatever a person sends — which is exactly the arrangement under test.
            val move = people(room).firstNotNullOfOrNull { (seat, id) ->
                nextMove(room, id, seat.index == 0 && !called)?.also {
                    if (it.first is GameAction.CallVinto) called = true
                }
            }

            // Nobody has a move, so it is the room's turn to be the clock — which is what it is
            // in life, and what this test was missing: a driver that only ever sent actions was
            // modelling a Worker with its alarms switched off. The table is only *stuck* when
            // there is no deadline left to wake it, and that is the thing worth failing on.
            if (move == null) {
                val due = decodeRoom(state).nextAlarmAt ?: return stopped(room, seed)
                now = maxOf(now, due) + 1
                state = encode(decodeLifecycle(onAlarm(state, now)).state)
                return@repeat
            }

            now += MS_BETWEEN_MOVES
            val token = if (move.second == 0) TOKEN_A else TOKEN_B
            val result = decodeAction(applyAction(state, token, actionJson(move.first), now))
            if (result.error != null) return stopped(decodeRoom(state), seed, result.error)
            state = encode(result.state)
        }
        return stopped(decodeRoom(state), seed, "no end in $MOVE_LIMIT moves")
    }

    /** The seated people, as seat and player id. */
    private fun people(room: RoomState): List<Pair<Seat, String>> = room.seats
        .filter { it.tokenHash != null && !it.isBot }
        .mapNotNull { seat -> seat.playerId?.let { seat to it } }

    /**
     * What this person does next, or null when it is not their move.
     *
     * The simplest legal game there is: peek, finish, draw, put it down, answer the window.
     * [mayCall] is what makes this the *reported* shape rather than an ordinary round — a person
     * ending their own turn with a Vinto call, leaving a coalition of one person and two bots.
     */
    private fun nextMove(room: RoomState, me: String, mayCall: Boolean): Pair<GameAction, Int>? {
        val game = room.game ?: return null
        val seatIndex = room.seats.first { it.playerId == me }.index
        val player = game.players.firstOrNull { it.id == me } ?: return null
        fun move(action: GameAction) = action to seatIndex

        if (game.phase == GamePhase.SETUP) {
            if (player.knownCardPositions.size < SETUP_PEEKS) {
                val position = player.cards.indices.first { it !in player.knownCardPositions }
                return move(GameAction.PeekSetupCard(PositionPayload(me, position)))
            }
            // Finishing is for the table, not for a seat: the room refuses it while anybody
            // still owes a peek, and a driver that offered it early spent every pass being
            // told so. The bots are dealt theirs, so this waits on the other person.
            val everybodyLooked = game.players.all { it.knownCardPositions.size >= SETUP_PEEKS }
            return if (everybodyLooked) move(GameAction.FinishSetup(PlayerIdPayload(me))) else null
        }

        val toss = game.activeTossIn
        if (toss != null && toss.waitingForInput && me !in toss.playersReadyForNextTurn) {
            val mine = game.players.getOrNull(toss.originalPlayerIndex)?.id == me
            if (mine && mayCall && game.vintoCallerId == null) {
                return move(GameAction.CallVinto(PlayerIdPayload(me)))
            }
            return move(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
        }

        if (game.players.getOrNull(game.currentPlayerIndex)?.id != me) return null
        return if (game.pendingAction != null) {
            move(GameAction.DiscardCard(PlayerIdPayload(me)))
        } else {
            move(GameAction.DrawCard(PlayerIdPayload(me)))
        }
    }

    private fun stopped(room: RoomState, seed: Long, why: String = "nobody has a move"): String {
        val game = room.game ?: return "  seed $seed: no game ($why)"
        val holder = game.players.getOrNull(game.currentPlayerIndex)
        val seat = room.seats.firstOrNull { it.playerId == holder?.id }
        val toss = game.activeTossIn
        return "  seed $seed: $why — ${game.phase}/${game.subPhase} at seat ${seat?.index} " +
            "(${holder?.id}, bot=${seat?.isBot == true || seat?.tokenHash == null}), " +
            "caller ${game.vintoCallerId}, pending=${game.pendingAction?.actionPhase}, " +
            "toss=${toss?.let { "ranks ${it.ranks} waiting ${it.waitingForInput} " +
                "ready ${it.playersReadyForNextTurn.size} queued ${it.queuedActions.size}"
            }}"
    }

    private companion object {
        val SEEDS = listOf(42L, 7L, 11L, 3L, 99L, 2024L)
        const val MOVE_LIMIT = 400
        const val MS_BETWEEN_MOVES = 2_000.0
        const val SETUP_PEEKS = 2
    }
}
