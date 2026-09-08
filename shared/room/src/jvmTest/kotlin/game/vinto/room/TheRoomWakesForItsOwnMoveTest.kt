package game.vinto.room

import game.vinto.protocol.RoomPhase
import game.vinto.shapes.GamePhase
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A table never sits waiting on a move the room itself owes.
 *
 * The bots are played *inside a request*: somebody acts, and the answer to that action carries
 * every bot turn that followed it. It is a good design — one send, everything that happened
 * because of it — and it has one hole. If a request ever ends with a bot still to move, nothing
 * is left to make it. Every seat is waiting on the room, the room is waiting to be asked, and
 * the next thing on its clock is the session buzzer half an hour away.
 *
 * That is not hypothetical: the confer window holds the bots while it is open, and it can be
 * closed by a path that plays nobody — `withPacing` drops it the moment the last coalition
 * human is no longer connected, and presence is recomputed on a socket closing, which is not a
 * request that plays anything. A table found sitting at a bot's turn in the final round, with
 * nothing due for thirty minutes, is what that looks like from a chair.
 *
 * So the fix is not another special case at the place it was seen. It is a rule: **while the
 * room owes a move, it keeps an alarm for making it.** Any way of reaching that state, known or
 * not, heals in a second or two.
 */
class TheRoomWakesForItsOwnMoveTest {

    @Test
    fun aTableLeftAtABotsTurnCarriesAnAlarmToPlayIt() {
        val owing = leftOwingAMove()

        val due = assertNotNull(
            owing.nextAlarmAt,
            "the room owes a move and has nothing on its clock to make it with",
        )
        assertTrue(
            due <= LATER + SOON_ENOUGH,
            "the room owes a move and will not wake for ${((due - LATER) / 1_000).toLong()} s",
        )
    }

    /** And waking makes it: the alarm plays the seats the room is holding. */
    @Test
    fun theAlarmPlaysTheMoveItWokeFor() {
        val owing = leftOwingAMove()
        val before = owing.log.size

        val woken = decodeLifecycle(onAlarm(encode(owing), LATER + SOON_ENOUGH)).state

        assertTrue(
            woken.log.size > before,
            "the alarm fired on a table owing a move and played nothing",
        )
        assertTrue(
            woken.log.drop(before).all { it.byBot },
            "the alarm played something that was not the room's to play",
        )
    }

    /** A table waiting on a person keeps no such alarm — that wait is the game. */
    @Test
    fun aTableWaitingOnAPersonIsNotWokenAtAll() {
        val dealt = decodeRoom(dealtRoom())
        val holder = dealt.game?.players?.get(dealt.game!!.currentPlayerIndex)?.id
        val seat = dealt.seats.first { it.playerId == holder }

        assertTrue(seat.tokenHash != null, "this fixture wants a person on the clock")
        val paced = withPacing(dealt, LATER)
        val due = paced.nextAlarmAt

        assertTrue(
            due == null || due > LATER + SOON_ENOUGH,
            "the room is waiting on a person and woke itself up about it",
        )
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * A dealt room whose turn belongs to a bot, with the confer window closed behind it — the
     * shape a request leaves when it stops without playing what it owes.
     */
    private fun leftOwingAMove(): RoomState {
        val dealt = decodeRoom(dealtRoom())
        val game = checkNotNull(dealt.game) { "a dealt room has a game" }

        // Seats 2 and 3 are the filler bots, so the turn is one the room plays. Setup is done
        // with, because a table in setup is waiting on people rather than on itself.
        val played = game.copy(
            phase = GamePhase.PLAYING,
            currentPlayerIndex = BOT_SEAT,
            players = game.players.map { it.copy(knownCardPositions = listOf(0, 1)) },
        )
        return withPacing(dealt.copy(phase = RoomPhase.PLAYING, game = played), LATER)
    }

    private companion object {
        const val BOT_SEAT = 2

        /** How long a table may sit on a move the room owes before something is wrong. */
        const val SOON_ENOUGH = 5_000.0
    }
}
