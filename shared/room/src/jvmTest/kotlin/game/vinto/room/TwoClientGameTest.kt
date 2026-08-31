package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.client.ConnectionState
import game.vinto.client.CreatedRoom
import game.vinto.client.Frame
import game.vinto.client.MemoryVault
import game.vinto.client.RemoteRoom
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.engine.replayRecording
import game.vinto.protocol.ClientMessage
import game.vinto.protocol.LobbyView
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.PublicRoom
import game.vinto.protocol.PublicSeat
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.VintoJson
import game.vinto.shapes.actorId
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole online stack minus the platform, on one JVM.
 *
 * Two real [RemoteRoom]s with real [game.vinto.client.RemoteGameSession]s play a full round
 * against a fake server that is fake only in its transport: every message routes through
 * the same `RoomCore` entry points `index.mjs` calls, serialized with the same
 * `ProtocolJson`, clocked by a hand-driven `now`. What the wrangler gates prove about
 * reachability, this proves about *correctness*: the room's bytes drive two disagreeingly-
 * redacted clients to one agreeing game.
 *
 * Held at the end: both clients reach scoring and agree on every public fact; each client
 * animated exactly one frame per logged action (the per-event views did their job); a
 * mid-game socket kill reconnects by token and resyncs onto the live game; and the round's
 * filed recording replays clean through the engine.
 */
class TwoClientGameTest {

    @Test
    fun twoClientsPlayARoundToTheSameEnd() = runTest {
        val server = FakeRoomServer()
        val ann = RemoteRoom(server.connector, CODE, MemoryVault(), "Ann", this)
        testScheduler.runCurrent()
        val bob = RemoteRoom(server.connector, CODE, MemoryVault(), "Bob", this)
        testScheduler.runCurrent()

        // Two filler bots complete the table; the countdown alarm deals it.
        ann.addBot()
        testScheduler.runCurrent()
        ann.addBot()
        testScheduler.runCurrent()
        server.advanceTo(START + countdownMs() + 1)
        testScheduler.runCurrent()

        val annSession = assertNotNull(ann.session.value, "the deal reached Ann")
        val bobSession = assertNotNull(bob.session.value, "the deal reached Bob")

        val annFrames = mutableListOf<Frame>()
        val bobFrames = mutableListOf<Frame>()
        val collectors = listOf(
            launch { annSession.frames.collect { annFrames += it } },
            launch { bobSession.frames.collect { bobFrames += it } },
        )
        testScheduler.runCurrent()

        // The round, driven FinishesTest-style: the humans' seats decided by the bots' own
        // brain, every move dispatched through the owning client's session — the whole wire
        // in the loop, both directions, every move.
        val person = BotRunner(Difficulty.EASY, Random(SEED))
        var moves = 0
        var reconnected = false

        while (moves < MOVE_LIMIT) {
            val room = decodeRoom(server.stateJson)
            if (room.phase != RoomPhase.PLAYING) break
            val game = assertNotNull(room.game)

            server.now += MS_BETWEEN_MOVES
            val action = assertNotNull(person.nextAction(game.everySeatPlayable()))
            val session = when (val actor = action.actorId) {
                null -> annSession
                else -> {
                    val seat = room.seats.first { it.playerId == actor }
                    assertNotNull(seat.tokenHash, "between requests the wanted move is a human's")
                    if (seat.index == 0) annSession else bobSession
                }
            }

            val outcome = async { session.dispatch(action) }
            testScheduler.runCurrent()
            assertTrue(outcome.isCompleted, "dispatch hung on $action")
            assertNull(outcome.await(), "move $moves refused")
            moves++

            // Mid-game, Ann's socket dies. The token reclaims the seat, the resync lands
            // her on the live game, and the round simply continues.
            if (moves == KILL_AT && !reconnected) {
                reconnected = true
                server.killSeat(0)
                testScheduler.advanceTimeBy(RECONNECT_BACKOFF_MS)
                testScheduler.runCurrent()
                assertEquals(
                    ConnectionState.Connected,
                    ann.connection.value,
                    "the token reconnect landed",
                )
            }
        }

        val settled = decodeRoom(server.stateJson)
        assertTrue(
            settled.phase == RoomPhase.BETWEEN_ROUNDS || settled.phase == RoomPhase.FINISHED,
            "the round never ended: ${settled.phase} after $moves moves",
        )
        testScheduler.runCurrent()

        // Both clients reached the same end, each through its own redacted wire.
        assertTrue(annSession.isOver && bobSession.isOver)
        val annView = annSession.view.value
        val bobView = bobSession.view.value
        assertEquals(GamePhase.SCORING, annView.phase)
        assertEquals(annView.scores, bobView.scores, "one game, two witnesses")
        assertEquals(annView.discardTop, bobView.discardTop)
        assertEquals(annView.turnNumber, bobView.turnNumber)

        // One frame per logged action — the per-event views did their job. Ann carries one
        // extra: the resync's landing frame from her reconnect.
        assertEquals(settled.log.size, bobFrames.size, "Bob animated every action, once")
        assertEquals(settled.log.size + 1, annFrames.size, "Ann: every action plus the landing")

        // And the round the clients just played replays from the room's own recording.
        val filed = VintoJson.decodeFromString(
            RecordingResult.serializer(),
            roundRecording(server.stateJson, recordedAt = "2026-08-26T00:00:00Z"),
        )
        val replay = replayRecording(assertNotNull(filed.recording), verifyFinalState = true)
        assertTrue(replay.ok, "diverged at ${replay.divergence?.index}: ${replay.divergence?.reason}")

        collectors.forEach { it.cancel() }
        ann.leave()
        bob.leave()
    }

    private fun GameState.everySeatPlayable(): GameState =
        copy(players = players.map { it.copy(isHuman = false, isBot = true) })

    private fun decodeRoom(json: String): RoomState =
        VintoJson.decodeFromString(RoomState.serializer(), json)

    // ------------------------------------------------------------------ the fake server

    /**
     * `index.mjs` re-enacted in-process: sockets are channels, the clock is a field, and
     * every decision routes through the same core entry points the Durable Object calls.
     */
    private class FakeRoomServer {
        var now: Double = START
        var stateJson: String = newRoom("room-$CODE", SEED.toDouble(), "easy", now)
        private val sockets = mutableListOf<FakeSocket>()
        private var minted = 0

        val connector = object : RoomConnector {
            override suspend fun connect(code: String): RoomAnswer<RoomSocket> =
                RoomAnswer.Ok(FakeSocket().also { sockets.add(it) })

            override suspend fun createRoom(isPublic: Boolean, hostNickname: String) =
                RoomAnswer.Ok(CreatedRoom(CODE, "room-$CODE"))

            /** The two clients here are handed a code; neither of them browses for one. */
            override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> =
                RoomAnswer.Ok(emptyList())
        }

        inner class FakeSocket : RoomSocket {
            override val incoming = Channel<String>(Channel.UNLIMITED)
            var seat: Int? = null
            var token: String? = null
            var open = true

            override suspend fun send(text: String) {
                check(open) { "socket closed" }
                onMessage(this, text)
            }

            override fun close() = drop(this, cause = null)
        }

        /** The carrier eats [seatIndex]'s socket, as carriers do. */
        fun killSeat(seatIndex: Int) {
            sockets.filter { it.seat == seatIndex }
                .forEach { drop(it, RuntimeException("carrier lost")) }
        }

        /** Fires every alarm that has become due, exactly as the platform's alarm would. */
        fun advanceTo(at: Double) {
            now = at
            repeat(MAX_ALARM_ROUNDS) {
                val due = nextAlarmAt(stateJson)
                if (due == 0.0 || due > now) return
                val fired = VintoJson.decodeFromString(
                    AlarmEnvelopes.serializer(),
                    alarmEnvelopes(stateJson, now),
                )
                check(!fired.deleted) { "the room deleted itself mid-test" }
                stateJson = encode(fired.state)
                sendPrebuilt(fired.messages)
            }
        }

        private fun drop(socket: FakeSocket, cause: Throwable?) {
            if (!socket.open) return
            socket.open = false
            if (cause == null) socket.incoming.close() else socket.incoming.close(cause)
            sockets.remove(socket)
            refreshPresence()
        }

        private fun onMessage(socket: FakeSocket, text: String) {
            when (val message = ProtocolJson.decodeFromString(ClientMessage.serializer(), text)) {
                is ClientMessage.Join -> join(socket, message)
                is ClientMessage.Action -> action(socket, message)
                is ClientMessage.Resync ->
                    deliver(socket, syncEnvelope(stateJson, socket.seat ?: -1, message.sinceIndex, now))

                is ClientMessage.AddBot ->
                    lobbyChange(socket, addBot(stateJson, message.token ?: socket.token!!, now))

                is ClientMessage.RemoveBot ->
                    lobbyChange(socket, removeBot(stateJson, message.token ?: socket.token!!, message.seat, now))

                is ClientMessage.NextRound -> Unit // one round is this harness's scope
            }
        }

        private fun join(socket: FakeSocket, message: ClientMessage.Join) {
            val token = message.token ?: "tok-${minted++}"
            val result = VintoJson.decodeFromString(
                JoinResult.serializer(),
                joinRoom(stateJson, token, message.nickname.orEmpty(), now),
            )
            if (result.error != null) {
                return deliver(socket, encodeServer(ServerMessage.Error(result.error)))
            }

            stateJson = encode(result.state)
            socket.seat = result.seat
            socket.token = token

            deliver(
                socket,
                encodeServer(
                    ServerMessage.Joined(
                        seat = result.seat,
                        token = token,
                        seats = publicSeats(),
                        nextIndex = result.state.log.size,
                        lobby = lobby(),
                        view = viewFor(result.seat),
                    ),
                ),
            )
            refreshPresence()
            broadcastLobby()
        }

        private fun action(socket: FakeSocket, message: ClientMessage.Action) {
            val result = VintoJson.decodeFromString(
                Envelopes.serializer(),
                applyActionEnvelopes(
                    stateJson,
                    message.token ?: socket.token!!,
                    VintoJson.encodeToString(GameAction.serializer(), message.action),
                    now,
                ),
            )
            if (result.error != null) {
                if (result.retryAfterMs != null) stateJson = encode(result.state)
                return deliver(
                    socket,
                    encodeServer(ServerMessage.Error(result.error, result.retryAfterMs)),
                )
            }
            stateJson = encode(result.state)
            sendPrebuilt(result.messages)
        }

        private fun lobbyChange(socket: FakeSocket, resultJson: String) {
            val result = VintoJson.decodeFromString(JoinResult.serializer(), resultJson)
            if (result.error != null) {
                return deliver(socket, encodeServer(ServerMessage.Error(result.error)))
            }
            stateJson = encode(result.state)
            broadcastLobby()
        }

        private fun sendPrebuilt(messages: Map<Int, String>) {
            sockets.filter { it.open }.forEach { socket ->
                socket.seat?.let { seat -> messages[seat]?.let { deliver(socket, it) } }
            }
        }

        private fun broadcastLobby() {
            val text = encodeServer(ServerMessage.Lobby(lobby()))
            sockets.filter { it.open }.forEach { deliver(it, text) }
        }

        private fun refreshPresence() {
            val connected = sockets.mapNotNull { it.seat }.joinToString(",")
            val result = VintoJson.decodeFromString(
                LifecycleResult.serializer(),
                updatePresence(stateJson, connected, now),
            )
            stateJson = encode(result.state)
        }

        private fun lobby(): LobbyView =
            VintoJson.decodeFromString(LobbyView.serializer(), lobbyView(stateJson, now))

        private fun viewFor(seat: Int) = VintoJson.decodeFromString(
            ViewResult.serializer(),
            viewForSeat(stateJson, seat, now),
        ).view

        private fun publicSeats(): List<PublicSeat> =
            VintoJson.decodeFromString(RoomState.serializer(), stateJson).seats.map {
                PublicSeat(it.index, it.playerId, it.profile, it.ownerId, it.tokenHash != null)
            }

        private fun deliver(socket: FakeSocket, text: String) {
            socket.incoming.trySend(text)
        }

        private fun encode(state: RoomState): String =
            VintoJson.encodeToString(RoomState.serializer(), state)

        private fun encodeServer(message: ServerMessage): String =
            ProtocolJson.encodeToString(ServerMessage.serializer(), message)
    }

    private companion object {
        const val CODE = "HARNES"
        const val SEED = 42L
        const val START = 1_000_000.0
        const val MS_BETWEEN_MOVES = 2_000.0
        const val MOVE_LIMIT = 600
        const val KILL_AT = 5
        const val RECONNECT_BACKOFF_MS = 1_100L
        const val MAX_ALARM_ROUNDS = 5
    }
}
