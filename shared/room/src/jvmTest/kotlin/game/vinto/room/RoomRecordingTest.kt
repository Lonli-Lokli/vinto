package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.engine.replayRecording
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.VintoJson
import game.vinto.shapes.actorId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The server-recording parity loop, closed locally.
 *
 * A whole round is played through the room — the humans' seats driven by the bots' own
 * brain, every move through `applyAction` with its seat's token, exactly as sockets would —
 * and the recording the room files at the end is then replayed through the engine. If the
 * two disagree, either the room logged something it did not apply, applied something it did
 * not log, or dealt from a different state than it recorded; all three have been the bug in
 * some system, and all three are invisible until somebody replays.
 *
 * Until this test, that replay only happened after a deploy, against `/replay`, by hand.
 */
class RoomRecordingTest {

    @Test
    fun aRoomsRoundReplaysFromItsOwnRecording() {
        var state = dealtRoom()
        val person = BotRunner(Difficulty.EASY, Random(42))
        var now = START
        var moves = 0

        while (moves < MOVE_LIMIT) {
            val room = decodeRoom(state)
            if (room.phase == RoomPhase.BETWEEN_ROUNDS || room.phase == RoomPhase.FINISHED) break
            val game = assertNotNull(room.game, "a playing room has a game")

            // Every seat marked playable so the brain answers for the humans; the bots'
            // own moves never reach applyAction — the room plays them before returning.
            val everySeat = game.copy(
                players = game.players.map { it.copy(isHuman = false, isBot = true) },
            )
            val action = assertNotNull(person.nextAction(everySeat), "the brain went silent")

            // Whose token authorises it: the actor's seat when the action names one, and any
            // seated human for the actorless ones (choosing a coalition leader names nobody
            // — the boundary check skips actions that claim no actor).
            val actor = action.actorId
            val token = if (actor == null) {
                TOKENS[0]!!
            } else {
                val seat = room.seats.first { it.playerId == actor }
                assertNotNull(seat.tokenHash, "between requests the wanted move is a human's")
                TOKENS[seat.index]!!
            }

            // The clock advances between moves, as it would in life — and as the rate
            // limiter requires: a whole round at one instant is exactly a flood.
            now += MS_BETWEEN_MOVES
            val actionJson = VintoJson.encodeToString(GameAction.serializer(), action)
            val result = VintoJson.decodeFromString(
                ActionResult.serializer(),
                applyAction(state, token, actionJson, now),
            )
            assertNull(result.error, "move $moves refused: ${result.error}")
            state = VintoJson.encodeToString(RoomState.serializer(), result.state)
            moves++
        }

        val settled = decodeRoom(state)
        assertTrue(
            settled.phase == RoomPhase.BETWEEN_ROUNDS || settled.phase == RoomPhase.FINISHED,
            "the round never ended: phase=${settled.phase} after $moves moves",
        )
        assertEquals(1, settled.session.rounds.size, "one round, filed once")

        // The payoff: what the room recorded, the engine replays — to the same final state.
        val filed = VintoJson.decodeFromString(
            RecordingResult.serializer(),
            roundRecording(state, recordedAt = "2026-08-26T00:00:00Z"),
        )
        val recording = assertNotNull(filed.recording, "a settled round has a recording")
        assertTrue(recording.actions.isNotEmpty())

        val replay = replayRecording(recording, verifyFinalState = true)
        assertTrue(
            replay.ok,
            "the recording diverged from its own game at action " +
                "${replay.divergence?.index}: ${replay.divergence?.reason}",
        )
    }

    @Test
    fun anUnfinishedRoundHasNoRecordingYet() {
        val dealt = roundRecording(dealtRoom(), recordedAt = "2026-08-26T00:00:00Z")
        val result = VintoJson.decodeFromString(RecordingResult.serializer(), dealt)
        assertNull(result.recording)
        assertEquals("the round has not ended", result.error)
    }

    // ------------------------------------------------------------------ plumbing

    private fun dealtRoom(): String {
        var state = newRoom("room-TEST", seed = 42.0, difficulty = "easy", nowMs = START)
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
        const val MS_BETWEEN_MOVES = 2_000.0
        const val MOVE_LIMIT = 600
        val TOKENS = mapOf(0 to "token-ann", 1 to "token-bob")
    }
}
