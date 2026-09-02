package game.vinto.room

import game.vinto.protocol.RoomPhase
import game.vinto.shapes.GamePhase
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room's own clocks: the thirty-minute buzzer, and the three ways a room ends without it.
 *
 * None of these had a test. They are the part of a room that runs when nobody is doing
 * anything — an alarm, a wake, a decision — which is exactly the part the two-client harness
 * never reaches and the wrangler gates cannot afford to wait for. Every case here is the
 * alarm handler's, driven with a hand-set clock and read through the same envelope builder
 * `index.mjs` calls, so what is asserted is what the Durable Object would actually do.
 *
 * Two of them found something. A buzzer that has been consulted and deliberately left behind
 * — a declared Vinto playing out, a room already finished — was still the earliest thing on
 * `nextAlarmAt`, so the object asked to be woken for a moment already gone and the platform
 * obliged at once, again and again. And a buzzer that fired *between* rounds filed the round
 * that had just been filed, paying it twice on the final standings.
 */
class SessionClockTest {

    // ------------------------------------------------------------------ the buzzer

    @Test
    fun theSessionClockStartsAtTheFirstDealNotAtTheRoom() {
        assertNull(decodeRoom(lobbyOfTwo()).session.endsAtEpochMs, "a lobby is not on the clock")

        val dealtAt = START + countdownMs() + 1
        val dealt = decodeRoom(dealtRoom())
        assertEquals(dealtAt + sessionMs(), dealt.session.endsAtEpochMs)
    }

    /**
     * A round nobody called is thrown away at the buzzer, and recorded as thrown away.
     *
     * Uniform on purpose (design R2b): the visible clock is what makes it fair, and the only
     * way to bank a round near the end is to call Vinto first.
     */
    @Test
    fun theBuzzerDiscardsARoundNobodyCalled() {
        val dealt = decodeRoom(dealtRoom())
        val buzzer = assertNotNull(dealt.session.endsAtEpochMs)

        val fired = decodeAlarm(alarmEnvelopes(encode(dealt), buzzer))
        val over = fired.state
        assertEquals(RoomPhase.FINISHED, over.phase)
        assertNull(over.game, "a discarded round is not left on the table")
        assertTrue(over.session.rounds.isEmpty(), "a discarded round is not a played one")
        assertEquals(1, over.session.discardedRound, "recorded, because the standings cannot say so themselves")
        assertEquals(buzzer, over.finishedAtEpochMs)
        assertFalse(fired.deleted, "the scoreboard outlives the game")
        assertTrue(fired.messages.isEmpty(), "the ended broadcast carries no view and stays with the sockets")

        // And the finished room is swept on its own clock, not the buzzer's.
        assertEquals(buzzer + FINISHED_TTL_MS, fired.nextAlarmAtEpochMs)
        assertTrue(decodeAlarm(alarmEnvelopes(encode(over), buzzer + FINISHED_TTL_MS)).deleted)
    }

    /**
     * A declared Vinto is allowed to finish — and the room does not wake itself in a loop
     * while it does.
     *
     * The second half is the one that was wrong. `closeSession` leaves the state untouched,
     * which is right; `nextAlarmAt` then still named the buzzer as the earliest thing pending,
     * and an alarm set for a time already past fires again immediately. The object woke,
     * decided nothing, asked to be woken for the same past moment, and woke again — for as
     * long as the final round took.
     */
    @Test
    fun aDeclaredVintoIsLeftToFinishWithoutTheRoomWakingItselfForThePast() {
        val dealt = decodeRoom(dealtRoom())
        val game = assertNotNull(dealt.game)
        val called = dealt.copy(
            game = game.copy(
                phase = GamePhase.FINAL,
                vintoCallerId = game.players[0].id,
                coalitionLeaderId = game.players[1].id,
            ),
        )
        val buzzer = assertNotNull(called.session.endsAtEpochMs)

        val fired = decodeAlarm(alarmEnvelopes(encode(called), buzzer + 1))
        assertEquals(RoomPhase.PLAYING, fired.state.phase, "the buzzer cut a declared Vinto short")
        assertNotNull(fired.state.game)
        assertNull(fired.state.session.discardedRound)
        assertFalse(fired.deleted)

        val next = fired.nextAlarmAtEpochMs
        assertTrue(next == null || next > buzzer, "the room asked to be woken for a moment already gone: $next")
        assertEquals(next ?: 0.0, nextAlarmAt(encode(fired.state)), "the exported alarm agrees with the result")
    }

    /**
     * A round that ends past the buzzer is scored, and *that* is what ends the session.
     *
     * The alarm never fires here — the clock ran out mid-round and the round got there first,
     * which is what an evicted object or a late wake looks like. `settleRound` finds the
     * session over on the way into what would have been the between-rounds wait.
     */
    @Test
    fun aRoundThatOutlivesTheBuzzerIsScoredAndThenTheSessionIsOver() {
        val dealt = decodeRoom(dealtRoom())
        val overdue = dealt.copy(session = dealt.session.copy(endsAtEpochMs = START + 1))

        val settled = decodeRoom(playRoundOut(encode(overdue), seed = 42L, from = LATER))

        assertEquals(RoomPhase.FINISHED, settled.phase, "the round ended and the session did not")
        assertEquals(1, settled.session.rounds.size, "the round that played out is filed")
        assertNull(settled.session.discardedRound, "and not thrown away")
        assertNull(settled.game)
        assertNotNull(settled.finishedAtEpochMs)

        // The last round's recording outlives the game the room discarded.
        val filed = VintoJson.decodeFromString(
            RecordingResult.serializer(),
            roundRecording(encode(settled), recordedAt = "2026-09-02T00:00:00Z"),
        )
        assertNotNull(filed.recording, "a finished room lost its last round's recording: ${filed.error}")
    }

    /** A room that has scored its round and is waiting for agreement is simply over at the buzzer. */
    @Test
    fun theBuzzerBetweenRoundsEndsTheSessionWithoutFilingTheRoundAgain() {
        val between = betweenRounds(dealtRoom())
        val buzzer = assertNotNull(between.session.endsAtEpochMs)

        val fired = decodeAlarm(alarmEnvelopes(encode(between), buzzer))
        val over = fired.state
        assertEquals(RoomPhase.FINISHED, over.phase)
        assertEquals(1, over.session.rounds.size, "the round on record was filed twice")
        assertEquals(between.session.standings, over.session.standings, "the standings moved at the buzzer")
        assertNull(over.session.discardedRound, "a scored round is not a discarded one")
        assertNull(over.game)
        assertNotNull(over.roundFinal, "the recording of the last round outlives the room finishing")
    }

    /** A finished room has no session to end, and must not be woken for the one it had. */
    @Test
    fun aFinishedRoomIsNotWokenForABuzzerThatAlreadyWent() {
        val dealt = decodeRoom(dealtRoom())
        val buzzer = assertNotNull(dealt.session.endsAtEpochMs)
        val over = decodeAlarm(alarmEnvelopes(encode(dealt), buzzer)).state
        assertEquals(RoomPhase.FINISHED, over.phase)

        val woken = decodeAlarm(alarmEnvelopes(encode(over), buzzer + 1))
        assertFalse(woken.deleted, "swept before the scoreboard could be read")
        val next = woken.nextAlarmAtEpochMs
        assertTrue(next == null || next > buzzer + 1, "woken again for the past: $next")
    }

    // ------------------------------------------------------------------ the humans leaving

    /**
     * One human left is a game the device plays for free (design R1): the seat is held for
     * thirty seconds, a bot plays it, and at sixty the session ends.
     */
    @Test
    fun aSessionWithOneHumanLeftIsPlayedForThirtySecondsThenEndedAtSixty() {
        val dropped = decodeLifecycle(updatePresence(dealtRoom(), "1", LATER))
        assertEquals(LATER + SEAT_GRACE_MS, dropped.state.seatGrace[0], "Ann's seat is held")
        assertEquals(LATER + LONELY_GRACE_MS, dropped.state.lonelyUntilEpochMs, "and the session is on notice")
        assertEquals(LATER + SEAT_GRACE_MS, dropped.nextAlarmAtEpochMs, "the earlier clock is the one set")

        val takeover = decodeAlarm(alarmEnvelopes(encode(dropped.state), LATER + SEAT_GRACE_MS))
        assertEquals(listOf(0), takeover.tookOver)
        assertEquals(RoomPhase.PLAYING, takeover.state.phase, "a bot playing a seat is not the session ending")
        assertEquals(LATER + LONELY_GRACE_MS, takeover.nextAlarmAtEpochMs)

        val ended = decodeAlarm(alarmEnvelopes(encode(takeover.state), LATER + LONELY_GRACE_MS))
        assertEquals(RoomPhase.FINISHED, ended.state.phase)
        assertNull(ended.state.lonelyUntilEpochMs, "an expired clock does not linger")
        assertEquals(LATER + LONELY_GRACE_MS, ended.state.finishedAtEpochMs)
        assertFalse(ended.deleted, "Bob is still looking at the scoreboard")
        assertTrue(ended.messages.isEmpty(), "the ended broadcast is the socket layer's")

        val sweep = assertNotNull(ended.nextAlarmAtEpochMs)
        assertEquals(LATER + LONELY_GRACE_MS + FINISHED_TTL_MS, sweep)
        assertTrue(decodeAlarm(alarmEnvelopes(encode(ended.state), sweep)).deleted)
    }

    /** Coming back in time takes the seat off both clocks. */
    @Test
    fun aReconnectBeforeTheGraceCancelsBothClocks() {
        val dropped = decodeLifecycle(updatePresence(dealtRoom(), "1", LATER)).state

        val back = decodeLifecycle(updatePresence(encode(dropped), "0,1", LATER + 5_000)).state

        assertTrue(back.seatGrace.isEmpty(), "a seat with its socket back is still on grace")
        assertNull(back.lonelyUntilEpochMs, "two humans connected is not lonely")
        assertNull(back.emptyUntilEpochMs)
    }

    /** Nobody connected at all, whatever the phase: two minutes and the room is gone. */
    @Test
    fun aRoomEverybodyHasLeftIsDeletedAfterTwoMinutes() {
        val empty = decodeLifecycle(updatePresence(dealtRoom(), "", LATER)).state
        assertEquals(LATER + ROOM_TTL_MS, empty.emptyUntilEpochMs)
        assertEquals(LATER + LONELY_GRACE_MS, empty.lonelyUntilEpochMs, "the lonely clock runs too, and is sooner")

        // Deletion is checked before anything else the same wake might do: a room that is
        // ending has no use for a bot playing one more turn.
        assertTrue(decodeAlarm(alarmEnvelopes(encode(empty), LATER + ROOM_TTL_MS)).deleted)

        // And a lobby nobody ever connected to is on the same clock from the moment it is made.
        val unvisited = newRoom("room-EMPTY", seed = 1.0, difficulty = "easy", nowMs = START)
        assertEquals(START + ROOM_TTL_MS, nextAlarmAt(unvisited))
        assertTrue(decodeAlarm(alarmEnvelopes(unvisited, START + ROOM_TTL_MS)).deleted)
    }

    /** One alarm, whichever deadline comes first — the room works out which. */
    @Test
    fun theAlarmIsSetForWhicheverDeadlineComesFirst() {
        val dealt = decodeRoom(dealtRoom())
        assertEquals(dealt.session.endsAtEpochMs, dealt.nextAlarmAt, "a healthy game waits only for the buzzer")

        val dropped = decodeLifecycle(updatePresence(encode(dealt), "1", LATER)).state
        val expected = listOfNotNull(
            dropped.seatGrace[0],
            dropped.lonelyUntilEpochMs,
            dropped.session.endsAtEpochMs,
        ).min()
        assertEquals(expected, dropped.nextAlarmAt)
        assertEquals(expected, nextAlarmAt(encode(dropped)), "the export is the same rule")
    }
}
