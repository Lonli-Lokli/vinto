package game.vinto.room

import game.vinto.engine.CardView
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GameAction
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The envelope builders: the wire messages, built where the rules are.
 *
 * Two promises under test. **The trail is complete** — every applied step arrives as an
 * event entry with the receiving seat's view after that step, which is what a remote client
 * animates from. And **redaction survives the trip** — each seat's message is checked as
 * *serialized text* for the one string that must not be there: the id of a card that seat
 * has not seen, because a card id spells out its rank and a leak in any intermediate view
 * would hand a hand to a hand.
 */
class EnvelopeTest {

    @Test
    fun everySeatGetsTheWholeTrailAsItsOwnView() {
        val state = dealtRoom()
        val annId = decodeRoom(state).seats[0].playerId!!
        val move = action(GameAction.PeekSetupCard(PositionPayload(annId, 0)))

        val envelopes = decodeEnvelopes(applyActionEnvelopes(state, TOKEN_A, move, LATER))
        assertNull(envelopes.error)
        assertEquals(setOf(0, 1, 2, 3), envelopes.messages.keys, "one message per seated seat")

        // The plain form and the envelope form are the same computation; the trail lengths
        // must agree or the two wire generations have diverged.
        val plain = VintoJson.decodeFromString(
            ActionResult.serializer(),
            applyAction(state, TOKEN_A, move, LATER),
        )

        envelopes.messages.forEach { (seatIndex, text) ->
            val message = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
            assertIs<ServerMessage.Events>(message, "seat $seatIndex gets an events message")
            assertEquals(plain.events.size, message.events.size)

            val seatPlayer = envelopes.state.seats[seatIndex].playerId
            message.events.forEach { entry ->
                val view = assertNotNull(entry.view, "every entry carries a per-event view")
                assertEquals(seatPlayer, view.viewerId, "and it is built for the receiver")
            }
            assertEquals(seatPlayer, message.view?.viewerId)
        }
    }

    @Test
    fun aPeekedCardReachesItsPeekerAndNobodyElse() {
        val state = dealtRoom()
        val room = decodeRoom(state)
        val annId = room.seats[0].playerId!!

        // The actual card at Ann's position 0, read from the full state the way only the
        // server can. Its id encodes its rank, which is exactly why it must not travel.
        val secret = room.game!!.players[0].cards[0].id

        val envelopes = decodeEnvelopes(
            applyActionEnvelopes(state, TOKEN_A, action(GameAction.PeekSetupCard(PositionPayload(annId, 0))), LATER),
        )

        // As parsed structure: visible to Ann, a blank token to Bob.
        val annLast = lastEntryView(envelopes.messages[0]!!)
        assertIs<CardView.Visible>(annLast.players[0].cards[0], "Ann sees the card she peeked")
        val bobLast = lastEntryView(envelopes.messages[1]!!)
        assertIs<CardView.Hidden>(bobLast.players[0].cards[0], "Bob sees that a card exists")

        // And as text, which is what actually travels: the id appears in Ann's message and
        // in nobody else's — not in any intermediate view, not in a reveal, not anywhere.
        assertTrue(secret in envelopes.messages[0]!!, "the peeked card rides to its peeker")
        (1..3).forEach { seat ->
            assertFalse(
                secret in envelopes.messages[seat]!!,
                "seat $seat's message leaks the id of a card it never saw",
            )
        }
    }

    @Test
    fun aSyncLandsAReconnectorOnThePresent() {
        val state = dealtRoom()
        val annId = decodeRoom(state).seats[0].playerId!!

        var current = state
        listOf(0, 1).forEach { position ->
            val applied = decodeEnvelopes(
                applyActionEnvelopes(
                    current,
                    TOKEN_A,
                    action(GameAction.PeekSetupCard(PositionPayload(annId, position))),
                    LATER,
                ),
            )
            current = VintoJson.encodeToString(RoomState.serializer(), applied.state)
        }

        val sync = ProtocolJson.decodeFromString(
            ServerMessage.serializer(),
            syncEnvelope(current, seat = 0, sinceIndex = 0, nowMs = LATER),
        )
        assertIs<ServerMessage.Sync>(sync)
        assertEquals(decodeRoom(current).log.size, sync.events.size)
        assertEquals(annId, sync.view?.viewerId, "the reconnector lands on its current view")
        assertTrue(
            sync.events.all { it.view == null },
            "catch-up entries carry no per-event views — the room keeps no past states",
        )

        // A socket that never joined has no seat; it may read the public log, nothing more.
        val strangers = ProtocolJson.decodeFromString(
            ServerMessage.serializer(),
            syncEnvelope(current, seat = -1, sinceIndex = 0, nowMs = LATER),
        )
        assertIs<ServerMessage.Sync>(strangers)
        assertNull(strangers.view)
    }

    @Test
    fun theCountdownAlarmDealsWithPrebuiltStarts() {
        var state = newRoom("room-TEST", seed = 42.0, difficulty = "easy", nowMs = NOW)
        state = encode(decodeJoin(joinRoom(state, TOKEN_A, "Ann", NOW)).state)
        state = encode(decodeJoin(joinRoom(state, TOKEN_B, "Bob", NOW)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)

        val fired = VintoJson.decodeFromString(
            AlarmEnvelopes.serializer(),
            alarmEnvelopes(state, NOW + countdownMs() + 1),
        )
        assertTrue(fired.started)
        assertEquals(setOf(0, 1, 2, 3), fired.messages.keys)

        fired.messages.forEach { (seatIndex, text) ->
            val message = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
            assertIs<ServerMessage.Started>(message)
            assertEquals(
                fired.state.seats[seatIndex].playerId,
                message.view?.viewerId,
                "each seat is dealt its own view",
            )
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun dealtRoom(): String {
        var state = newRoom("room-TEST", seed = 42.0, difficulty = "easy", nowMs = NOW)
        state = encode(decodeJoin(joinRoom(state, TOKEN_A, "Ann", NOW)).state)
        state = encode(decodeJoin(joinRoom(state, TOKEN_B, "Bob", NOW)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, NOW)).state)
        return encode(
            VintoJson.decodeFromString(
                LifecycleResult.serializer(),
                onAlarm(state, NOW + countdownMs() + 1),
            ).state,
        )
    }

    private fun lastEntryView(text: String) =
        (ProtocolJson.decodeFromString(ServerMessage.serializer(), text) as ServerMessage.Events)
            .events.last().view!!

    private fun action(action: GameAction): String =
        VintoJson.encodeToString(GameAction.serializer(), action)

    private fun encode(state: RoomState): String =
        VintoJson.encodeToString(RoomState.serializer(), state)

    private fun decodeRoom(json: String): RoomState =
        VintoJson.decodeFromString(RoomState.serializer(), json)

    private fun decodeJoin(json: String): JoinResult =
        VintoJson.decodeFromString(JoinResult.serializer(), json)

    private fun decodeEnvelopes(json: String): Envelopes =
        VintoJson.decodeFromString(Envelopes.serializer(), json)

    private companion object {
        const val NOW = 1_000_000.0
        const val LATER = NOW + 20_000.0
        const val TOKEN_A = "token-ann"
        const val TOKEN_B = "token-bob"
    }
}
