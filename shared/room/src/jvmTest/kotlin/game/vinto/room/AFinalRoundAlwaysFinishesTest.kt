package game.vinto.room

import game.vinto.engine.tossInIsOpen
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
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
        val stuck = SEEDS.mapNotNull { seed -> whereItStops(seed, silent = null) }

        assertTrue(
            stuck.isEmpty(),
            "the final round stopped and never restarted:\n" + stuck.joinToString("\n"),
        )
    }

    /**
     * And a person who stops answering does not stop the table.
     *
     * This is the case the room's clock exists for, and the one that was broken. The window a
     * queued toss-in action reopens has `waitingForInput` false — see `tossInIsOpen` — and
     * `laggingHumans` was asking that flag who it was waiting on. It answered "nobody", so the
     * room set **no deadline**, and a window with a silent person in it had nothing left to end
     * it: every other seat sat behind a turn that could not be taken.
     *
     * Seat 1 goes quiet here from the moment the final round starts, which is the shape of
     * somebody putting their phone down — not dropping, so no seat grace, nothing else to
     * rescue it.
     */
    @Test
    fun aPersonWhoStopsAnsweringDoesNotStopTheTable() {
        val stuck = SEEDS.mapNotNull { seed -> whereItStops(seed, silent = 1) }

        assertTrue(
            stuck.isEmpty(),
            "one person went quiet and the table stopped with them:\n" + stuck.joinToString("\n"),
        )
    }

    /**
     * Plays a room out with seat 0 calling Vinto, and reports where it stopped, or null.
     *
     * The branching IS the test: it drives a table through every phase a real one passes, and
     * splitting it into helpers would spread one readable walk across the file for a number.
     */
    @Suppress("CognitiveComplexMethod")
    private fun whereItStops(seed: Long, silent: Int?): String? {
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
            //
            // [silent] answers no toss-in window — and still takes its turns, deliberately. A
            // person who goes quiet on their *own turn* holds the table for as long as they
            // like, which is the decided rule and not a defect: only a window is bounded,
            // because a window holds every other seat as well.
            val move = people(room).firstNotNullOfOrNull { (seat, id) ->
                nextMove(room, id, seat.index == 0 && !called, quiet = seat.index == silent)?.also {
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
    private fun nextMove(
        room: RoomState,
        me: String,
        mayCall: Boolean,
        quiet: Boolean = false,
    ): Pair<GameAction, Int>? {
        val game = room.game ?: return null
        val seatIndex = room.seats.first { it.playerId == me }.index
        val action = when {
            game.phase == GamePhase.SETUP -> settingUp(game, me)
            game.tossInIsOpen -> inTheWindow(game, me, mayCall, quiet)
            game.players.getOrNull(game.currentPlayerIndex)?.id == me -> takingATurn(game, me)
            else -> null
        }
        return action?.let { it to seatIndex }
    }

    /** Two peeks each, and finishing only once the whole table has taken theirs. */
    private fun settingUp(game: GameState, me: String): GameAction? {
        val player = game.players.firstOrNull { it.id == me } ?: return null
        if (player.knownCardPositions.size < SETUP_PEEKS) {
            val position = player.cards.indices.first { it !in player.knownCardPositions }
            return GameAction.PeekSetupCard(PositionPayload(me, position))
        }
        // Finishing is for the table, not for a seat: the room refuses it while anybody still
        // owes a peek, and a driver that offered it early spent every pass being told so. The
        // bots are dealt theirs, so this waits on the other person.
        val everybodyLooked = game.players.all { it.knownCardPositions.size >= SETUP_PEEKS }
        return if (everybodyLooked) GameAction.FinishSetup(PlayerIdPayload(me)) else null
    }

    /**
     * A window open for throws is answered; one this seat has already answered is *waited* on.
     *
     * Read from `tossInIsOpen` rather than the window's own `waitingForInput`, because that is
     * what a client asks (`tossInTable`) and the flag disagrees with it — a driver reading the
     * flag drew into an open window on every pass and was told, correctly, that it could not.
     */
    private fun inTheWindow(
        game: GameState,
        me: String,
        mayCall: Boolean,
        quiet: Boolean,
    ): GameAction? {
        val toss = game.activeTossIn ?: return null
        if (quiet || me in toss.playersReadyForNextTurn) return null

        val mine = game.players.getOrNull(toss.originalPlayerIndex)?.id == me
        return if (mine && mayCall && game.vintoCallerId == null) {
            GameAction.CallVinto(PlayerIdPayload(me))
        } else {
            GameAction.PlayerTossInFinished(PlayerIdPayload(me))
        }
    }

    /** Draw, and put it straight down: a round that ends, not a good one. */
    private fun takingATurn(game: GameState, me: String): GameAction =
        if (game.pendingAction != null) {
            GameAction.DiscardCard(PlayerIdPayload(me))
        } else {
            GameAction.DrawCard(PlayerIdPayload(me))
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
