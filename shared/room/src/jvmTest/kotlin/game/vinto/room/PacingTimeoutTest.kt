package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.engine.initializeGame
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.VintoJson
import game.vinto.shapes.actorId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The pacing deadlines (migrate task 9.4): the two situations where the whole table waits
 * on one human, bounded by the wall clock.
 *
 * Three properties, each its own case. A deadline **exists exactly while its situation
 * does** and is not refreshed by unrelated play; **acting clears it** the ordinary way; and
 * **expiry moves for the laggard** through the same validate-and-reduce path a client's
 * action takes, logged `byBot` and delivered as ordinary events — a client cannot tell an
 * expiry from a very slow "done", which is the point.
 */
class PacingTimeoutTest {

    @Test
    fun aTossInWindowWaitsFifteenSecondsAndNoLonger() {
        val (state, openedAt) = playedToOpenWindow()
        val room = decodeRoom(state)

        val deadline = assertNotNull(
            room.tossInDeadlineEpochMs,
            "a window waiting on a human carries a deadline",
        )
        assertEquals(openedAt + TOSS_IN_MS, deadline, "fifteen seconds from the opening")

        // The expiry: the room finishes the window for the laggards, byBot, and the
        // envelope form delivers it as ordinary events with per-event views.
        val fired = VintoJson.decodeFromString(
            AlarmEnvelopes.serializer(),
            alarmEnvelopes(state, deadline + 1),
        )
        val after = fired.state
        assertNull(after.tossInDeadlineEpochMs, "an expired deadline does not linger")
        val synthesized = after.log.drop(room.log.size)
            .filter { it.byBot && it.action is GameAction.PlayerTossInFinished }
        assertTrue(synthesized.isNotEmpty(), "the room moved for the humans it out-waited")

        assertTrue(fired.messages.isNotEmpty(), "the expiry reaches the table as events")
        fired.messages.values.forEach { text ->
            assertIs<ServerMessage.Events>(
                ProtocolJson.decodeFromString(ServerMessage.serializer(), text),
            )
        }
    }

    @Test
    fun actingClearsTheDeadlineTheOrdinaryWay() {
        val (state, _) = playedToOpenWindow()
        val room = decodeRoom(state)
        assertNotNull(room.tossInDeadlineEpochMs)

        // Every lagging human says "done" themselves, before the deadline.
        var current = state
        var now = room.tossInDeadlineEpochMs!! - 5_000.0
        laggingHumanIds(room).forEach { playerId ->
            now += 1_000.0
            val seat = room.seats.first { it.playerId == playerId }
            val result = VintoJson.decodeFromString(
                ActionResult.serializer(),
                applyAction(
                    current,
                    TOKENS[seat.index]!!,
                    VintoJson.encodeToString(
                        GameAction.serializer(),
                        GameAction.PlayerTossInFinished(PlayerIdPayload(playerId)),
                    ),
                    now,
                ),
            )
            assertNull(result.error, "finishing refused: ${result.error}")
            current = encode(result.state)
        }

        assertNull(
            decodeRoom(current).tossInDeadlineEpochMs,
            "nobody is being waited on, so nothing is on the clock",
        )
    }

    @Test
    fun moreTimeExtendsTheWindowTwiceAndNoFurther() {
        val (state, _) = playedToOpenWindow()
        val room = decodeRoom(state)
        val deadline = assertNotNull(room.tossInDeadlineEpochMs)
        val lagging = laggingHumanIds(room)
        val seat = room.seats.first { it.playerId == lagging.first() }
        val token = TOKENS[seat.index]!!
        val now = deadline - 5_000.0

        // First ask: a full window again, measured from the running deadline — asking early
        // must not cost the time still on the clock. Every seat is told, as an empty events
        // message whose view carries the refreshed countdown.
        val first = VintoJson.decodeFromString(
            Envelopes.serializer(),
            moreTimeEnvelopes(state, token, now),
        )
        assertNull(first.error, "a lagging human's first ask is granted: ${first.error}")
        assertEquals(deadline + MORE_TIME_MS, first.state.tossInDeadlineEpochMs)
        assertEquals(1, first.state.tossInExtensions)
        assertTrue(first.messages.isNotEmpty(), "the moved clock reaches the table")
        first.messages.values.forEach { text ->
            val events = assertIs<ServerMessage.Events>(
                ProtocolJson.decodeFromString(ServerMessage.serializer(), text),
            )
            assertTrue(events.events.isEmpty(), "nothing moved on the table")
            assertEquals(
                (deadline + MORE_TIME_MS - now).toLong(),
                assertNotNull(events.view?.tossInMsRemaining, "the view carries the countdown"),
            )
        }

        // A second ask is granted; a third is the wall.
        val second = askedForTime(first.state, token, now)
        assertNull(second.error)
        assertEquals(2, second.state.tossInExtensions)
        val third = askedForTime(second.state, token, now)
        assertNotNull(third.error, "a window is extended at most twice")

        // And the allowance dies with the window: the expiry clears both the deadline and
        // the count, so the next window starts fresh.
        val fired = VintoJson.decodeFromString(
            AlarmEnvelopes.serializer(),
            alarmEnvelopes(encode(second.state), second.state.tossInDeadlineEpochMs!! + 1),
        )
        assertNull(fired.state.tossInDeadlineEpochMs)
        assertEquals(0, fired.state.tossInExtensions)
    }

    @Test
    fun onlySomebodyTheWindowWaitsOnMayAskForTime() {
        val (state, _) = playedToOpenWindow()
        val room = decodeRoom(state)
        val lagging = laggingHumanIds(room)

        // A token no seat holds is refused outright.
        val stranger = VintoJson.decodeFromString(
            Envelopes.serializer(),
            moreTimeEnvelopes(state, "token-mallory", NOW),
        )
        assertNotNull(stranger.error, "an unseated token cannot hold the table")

        // A seated human the window is not waiting on is refused too — the clock is not
        // theirs to move. Only checkable when the window is not waiting on both humans.
        val rested = room.seats.firstOrNull { it.tokenHash != null && it.playerId !in lagging }
        if (rested != null) {
            val refused = VintoJson.decodeFromString(
                Envelopes.serializer(),
                moreTimeEnvelopes(state, TOKENS[rested.index]!!, NOW),
            )
            assertNotNull(refused.error, "a seat that already answered cannot buy time")
        }
    }

    /** One more-time ask against an in-memory state, decoded. */
    private fun askedForTime(state: RoomState, token: String, now: Double): Envelopes =
        VintoJson.decodeFromString(
            Envelopes.serializer(),
            moreTimeEnvelopes(encode(state), token, now),
        )

    @Test
    fun theCoalitionGetsADefaultLeaderInTableOrder() {
        // A final round stalled on the leader choice, built directly: the engine's own
        // tests cover reaching this position; this one covers what the room does about it.
        val dealt = initializeGame(7L, Difficulty.EASY)
        val players = dealt.players.mapIndexed { index, player ->
            if (index < 2) {
                player.copy(isHuman = true, isBot = false)
            } else {
                player.copy(isHuman = false, isBot = true)
            }
        }
        val caller = players[1].id
        val game = dealt.copy(
            players = players,
            phase = GamePhase.FINAL,
            vintoCallerId = caller,
            coalitionLeaderId = null,
        )
        val state = RoomState(
            roomId = "room-TEST",
            seed = 7,
            difficulty = Difficulty.EASY,
            seats = players.mapIndexed { index, player ->
                Seat(
                    index = index,
                    tokenHash = if (index < 2) "hash-$index" else null,
                    isBot = index >= 2,
                    playerId = player.id,
                )
            },
            phase = RoomPhase.PLAYING,
            game = game,
            createdAtEpochMs = NOW,
            leaderDeadlineEpochMs = NOW - 1,
        )

        val fired = VintoJson.decodeFromString(
            AlarmEnvelopes.serializer(),
            alarmEnvelopes(encode(state), NOW),
        )
        val after = fired.state

        // The first coalition seat in table order — the caller is seat 1, so seat 0 leads.
        // Not a choice anybody made, but one everybody could predict.
        assertEquals(players[0].id, after.game?.coalitionLeaderId)
        assertNull(after.leaderDeadlineEpochMs)
        val entry = after.log.firstOrNull { it.action is GameAction.SetCoalitionLeader }
        assertNotNull(entry, "the appointment is on the log")
        assertTrue(entry.byBot, "and marked as the room's own move")
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Plays rooms forward until a toss-in window is waiting on a human, returning the state
     * and the clock at the move that opened it. Tries a handful of seeds so the test rests
     * on "toss-ins happen in Vinto" rather than on one seed's script.
     */
    private fun playedToOpenWindow(): Pair<String, Double> {
        for (seed in SEEDS) {
            var state = dealtRoom(seed)
            val person = BotRunner(Difficulty.EASY, Random(seed))
            var now = START
            var moves = 0

            while (moves < MOVE_LIMIT) {
                if (decodeRoom(state).tossInDeadlineEpochMs != null) return state to now
                now += 2_000.0
                state = oneHumanMove(state, person, now) ?: break
                moves++
            }
        }
        fail("no seed in $SEEDS produced a toss-in window waiting on a human")
    }

    /** One human move through `applyAction`, or null when this game has nothing to drive. */
    private fun oneHumanMove(state: String, person: BotRunner, now: Double): String? {
        val room = decodeRoom(state)
        val game = room.game.takeIf { room.phase == RoomPhase.PLAYING } ?: return null

        val everySeat = game.copy(
            players = game.players.map { it.copy(isHuman = false, isBot = true) },
        )
        val action = person.nextAction(everySeat) ?: return null
        val token = when (val actor = action.actorId) {
            null -> TOKENS[0]!!
            else -> {
                val seat = room.seats.firstOrNull { it.playerId == actor } ?: return null
                if (seat.tokenHash == null) return null
                TOKENS[seat.index]!!
            }
        }

        val result = VintoJson.decodeFromString(
            ActionResult.serializer(),
            applyAction(state, token, VintoJson.encodeToString(GameAction.serializer(), action), now),
        )
        return if (result.error == null) encode(result.state) else null
    }

    private fun laggingHumanIds(room: RoomState): List<String> {
        val toss = room.game?.activeTossIn ?: return emptyList()
        return room.game!!.players
            .filter { it.isHuman && it.id !in toss.playersReadyForNextTurn }
            .map { it.id }
    }

    private fun dealtRoom(seed: Long): String {
        var state = newRoom("room-TEST", seed = seed.toDouble(), difficulty = "easy", nowMs = START)
        state = encode(decodeJoin(joinRoom(state, TOKENS[0]!!, "Ann", START)).state)
        state = encode(decodeJoin(joinRoom(state, TOKENS[1]!!, "Bob", START)).state)
        state = encode(decodeJoin(addBot(state, TOKENS[0]!!, START)).state)
        state = encode(decodeJoin(addBot(state, TOKENS[0]!!, START)).state)
        return encode(
            VintoJson.decodeFromString(
                LifecycleResult.serializer(),
                onAlarm(state, START + countdownMs() + 1),
            ).state,
        )
    }

    private fun encode(state: RoomState): String =
        VintoJson.encodeToString(RoomState.serializer(), state)

    private fun decodeRoom(json: String): RoomState =
        VintoJson.decodeFromString(RoomState.serializer(), json)

    private fun decodeJoin(json: String): JoinResult =
        VintoJson.decodeFromString(JoinResult.serializer(), json)

    private companion object {
        const val START = 1_000_000.0
        const val NOW = 1_000_000.0
        const val MOVE_LIMIT = 600
        const val TOSS_IN_MS = 15_000.0
        const val MORE_TIME_MS = 15_000.0
        val SEEDS = listOf(42L, 7L, 11L)
        val TOKENS = mapOf(0 to "token-ann", 1 to "token-bob")
    }
}
