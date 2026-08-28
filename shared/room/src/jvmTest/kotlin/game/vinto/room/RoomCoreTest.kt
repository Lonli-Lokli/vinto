package game.vinto.room

import game.vinto.protocol.RoomPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room's rules, tested for the first time.
 *
 * Everything in `RoomCore` ran for months as JavaScript-only code, exercised solely through
 * wrangler gate scripts — integration checks that need a running `wrangler dev` and prove
 * reachability more than rules. Moving the core to a jvm+js module is what makes this file
 * possible, and this file is the reason the move happened.
 *
 * The style mirrors how `index.mjs` actually calls the core: JSON strings in, JSON strings
 * out, the clock passed in as an argument. No mocks, because there is nothing to mock — the
 * core's whole design is that the platform stays outside it.
 */
class RoomCoreTest {

    // ------------------------------------------------------------------ the lobby

    @Test
    fun aLobbyFillsCountsDownAndDeals() {
        var state = newRoom("room-TEST", seed = 42.0, difficulty = "easy", nowMs = NOW)

        val ann = join(state, TOKEN_A, "Ann")
        assertEquals(0, ann.seat)
        state = encode(ann.state)

        val bob = join(state, TOKEN_B, "Bob")
        assertEquals(1, bob.seat)
        state = encode(bob.state)

        // Two humans and two empty seats: not startable yet, so still a plain lobby.
        assertEquals(RoomPhase.LOBBY, bob.state.phase)

        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)
        val full = decodeJoin(addBot(state, TOKEN_A, NOW))
        state = encode(full.state)

        // Four seats, two humans: the countdown starts on the transition, ten seconds out.
        assertEquals(RoomPhase.STARTING, full.state.phase)
        assertEquals(NOW + countdownMs(), full.state.startsAtEpochMs)

        // The countdown is not advisory: dealing before it expires is refused.
        val early = decodeJoin(startGame(state, NOW + 1))
        assertEquals("the countdown has not expired", early.error)

        // The alarm fires past the deadline; the room works out that the deal was due.
        val fired = decodeLifecycle(onAlarm(state, NOW + countdownMs() + 1))
        assertTrue(fired.started, "the alarm past the deadline deals the game")
        val dealt = fired.state
        assertEquals(RoomPhase.PLAYING, dealt.phase)
        assertNotNull(dealt.game, "a playing room has a game")
        assertTrue(dealt.seats.all { it.playerId != null }, "every seat maps to a player")

        // The bots begin set up — their opening peeks are the engine's, humans' are not.
        val players = dealt.game!!.players
        assertTrue(players[0].isHuman && players[1].isHuman, "token seats are people")
        assertTrue(players[2].isBot && players[3].isBot, "the fillers are bots")
        assertTrue(
            players[0].knownCardPositions.isEmpty(),
            "a person starts having seen nothing — the dealt peek belongs to bots alone",
        )
    }

    // ------------------------------------------------------------------ acting

    @Test
    fun aSeatActsAsItsOwnPlayerAndNobodyElses() {
        val state = dealtRoom()
        val room = decodeRoom(state)
        val annId = room.seats[0].playerId!!
        val bobId = room.seats[1].playerId!!

        // The happy path: Ann's own setup peek, accepted and logged under her seat.
        val accepted = decodeAction(
            applyAction(state, TOKEN_A, action(GameAction.PeekSetupCard(PositionPayload(annId, 0))), LATER),
        )
        assertNull(accepted.error)
        assertTrue(accepted.events.isNotEmpty(), "an accepted action comes back as events")
        assertEquals(0, accepted.events.first().seat)
        assertEquals(annId, accepted.events.first().playerId)

        // The boundary: Ann's token with Bob's player in the payload, refused before the
        // engine ever sees it — whether or not it would have been legal for Bob.
        val crossed = decodeAction(
            applyAction(state, TOKEN_A, action(GameAction.PeekSetupCard(PositionPayload(bobId, 0))), LATER),
        )
        assertEquals("seat 0 may only act as $annId", crossed.error)
    }

    @Test
    fun aFloodIsRefusedBeforeItCostsAnything() {
        val state = dealtRoom()
        val annId = decodeRoom(state).seats[0].playerId!!

        // Same clock for every call, so the bucket never refills: ten actions spend the
        // burst — validity does not matter, the charge lands before validation on purpose.
        var current = state
        repeat(10) {
            val result = decodeAction(
                applyAction(current, TOKEN_A, action(GameAction.DrawCard(PlayerIdPayload(annId))), LATER),
            )
            assertNull(result.retryAfterMs, "the burst allowance is ten, not fewer")
            current = encode(result.state)
        }

        val throttled = decodeAction(
            applyAction(current, TOKEN_A, action(GameAction.DrawCard(PlayerIdPayload(annId))), LATER),
        )
        assertEquals("too many actions", throttled.error)
        assertNotNull(throttled.retryAfterMs, "a throttle names its backoff so clients wait")
    }

    // ------------------------------------------------------------------ the cursor

    @Test
    fun theLogIndexIsTheSyncCursor() {
        // The deal itself logs nothing — the bots wait on the humans' setup — so the log is
        // grown the ordinary way: both humans take their peeks and finish. Finishing waits
        // on everybody's peeks, which is why the order interleaves.
        var state = dealtRoom()
        val annId = decodeRoom(state).seats[0].playerId!!
        val bobId = decodeRoom(state).seats[1].playerId!!
        listOf(
            TOKEN_A to GameAction.PeekSetupCard(PositionPayload(annId, 0)),
            TOKEN_A to GameAction.PeekSetupCard(PositionPayload(annId, 1)),
            TOKEN_B to GameAction.PeekSetupCard(PositionPayload(bobId, 0)),
            TOKEN_B to GameAction.PeekSetupCard(PositionPayload(bobId, 1)),
            // One finish is the table's: once every peek is in, it starts the round for
            // everybody, so a second would find the setup already over.
            TOKEN_A to GameAction.FinishSetup(PlayerIdPayload(annId)),
        ).forEach { (token, move) ->
            val result = decodeAction(applyAction(state, token, action(move), LATER))
            assertNull(result.error, "setup move refused: ${result.error}")
            state = encode(result.state)
        }

        val room = decodeRoom(state)
        assertTrue(room.log.size >= 5, "the humans' five setup moves are on the log")

        val fromStart = decodeSync(eventsSince(state, 0))
        assertEquals(room.log.size, fromStart.events.size)
        assertEquals(room.log.size, fromStart.nextIndex)

        val fromTwo = decodeSync(eventsSince(state, 2))
        assertEquals(room.log.size - 2, fromTwo.events.size)
        assertEquals(2, fromTwo.events.first().index)

        // A cursor past the end is a client from the future; it gets nothing, not a crash.
        val beyond = decodeSync(eventsSince(state, room.log.size + 50))
        assertTrue(beyond.events.isEmpty())
    }

    // ------------------------------------------------------------------ the seat grace

    @Test
    fun aDroppedSeatIsHeldThenPlayedThenHandedBack() {
        val state = dealtRoom()

        // Ann's socket goes away; only Bob's seat reports connected. Her seat starts a
        // grace — held for her token, not surrendered.
        val dropped = decodeLifecycle(updatePresence(state, "1", LATER))
        assertEquals(LATER + SEAT_GRACE_MS, dropped.state.seatGrace[0])

        // The grace expires: a bot takes the seat over, and remembers to say so.
        val expired = decodeLifecycle(onAlarm(encode(dropped.state), LATER + SEAT_GRACE_MS + 1))
        assertEquals(listOf(0), expired.tookOver)
        val seat = expired.state.seats[0]
        assertTrue(seat.isBot, "the seat is being played")
        assertNotNull(seat.tokenHash, "and still belongs to its token")
        assertTrue(seat.botPlayedWhileAway, "the takeover is remembered for the return")

        // Ann returns with the same token: same seat, the bot steps aside, and the one-time
        // "a bot played while you were away" flag is delivered and cleared.
        val back = decodeJoin(joinRoom(encode(expired.state), TOKEN_A, "Ann", LATER + 60_000))
        assertEquals(0, back.seat)
        assertTrue(back.botPlayedWhileAway, "the return is told what happened")
        val reclaimed = back.state.seats[0]
        assertTrue(!reclaimed.isBot && !reclaimed.botPlayedWhileAway)
    }

    // ------------------------------------------------------------------ plumbing

    /** A room with two humans, two filler bots, and the game dealt — every test's opening. */
    private fun dealtRoom(): String {
        var state = newRoom("room-TEST", seed = 42.0, difficulty = "easy", nowMs = NOW)
        state = encode(join(state, TOKEN_A, "Ann").state)
        state = encode(join(state, TOKEN_B, "Bob").state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)
        return encode(decodeLifecycle(onAlarm(state, NOW + countdownMs() + 1)).state)
    }

    private fun join(state: String, token: String, name: String): JoinResult {
        val result = decodeJoin(joinRoom(state, token, name, NOW))
        assertNull(result.error, "join refused: ${result.error}")
        return result
    }

    private fun action(action: GameAction): String =
        VintoJson.encodeToString(GameAction.serializer(), action)

    private fun encode(state: RoomState): String =
        VintoJson.encodeToString(RoomState.serializer(), state)

    private fun decodeRoom(json: String): RoomState =
        VintoJson.decodeFromString(RoomState.serializer(), json)

    private fun decodeJoin(json: String): JoinResult =
        VintoJson.decodeFromString(JoinResult.serializer(), json)

    private fun decodeAction(json: String): ActionResult =
        VintoJson.decodeFromString(ActionResult.serializer(), json)

    private fun decodeSync(json: String): SyncResult =
        VintoJson.decodeFromString(SyncResult.serializer(), json)

    private fun decodeLifecycle(json: String): LifecycleResult =
        VintoJson.decodeFromString(LifecycleResult.serializer(), json)

    private companion object {
        const val NOW = 1_000_000.0
        const val LATER = NOW + 20_000.0
        const val TOKEN_A = "token-ann"
        const val TOKEN_B = "token-bob"

        /** `SEAT_GRACE_MS` in RoomCore — private there; a drift fails the grace assertion. */
        const val SEAT_GRACE_MS = 30_000.0
    }
}
