package game.vinto.protocol

import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire, pinned by literals.
 *
 * Every string in this file is the shape `index.mjs` sends or accepts — copied from the
 * JavaScript, not derived from the Kotlin — because the JavaScript *is* the contract: it has
 * been serving the gate harnesses since the room existed, and this module's entire job is to
 * transcribe it faithfully. A drift on either side fails here rather than in a game.
 *
 * Two kinds of assertion, chosen per message:
 *
 * - **Round trip as elements**: decode the literal, re-encode, and compare *parsed* JSON.
 *   Byte equality would be the stronger pin but is not honest across the seam — JavaScript
 *   writes `1500` where a Kotlin `Double` writes `1500.0`, and both sides read either.
 * - **Decode-only** where a value has no canonical text form, asserting the decoded fields.
 */
class ProtocolWireTest {

    // ------------------------------------------------------------------ client → server

    @Test
    fun aFirstJoinCarriesANicknameAndNoToken() {
        val literal = """{"type":"join","nickname":"Ann"}"""

        val decoded = decodeClient(literal)
        assertIs<ClientMessage.Join>(decoded)
        assertNull(decoded.token, "a first join has no token — the room mints one")
        assertEquals("Ann", decoded.nickname)

        assertSameJson(literal, encodeClient(decoded))
    }

    @Test
    fun aRejoinCarriesTheToken() {
        val literal = """{"type":"join","token":"tok-abc","nickname":"Ann"}"""

        val decoded = decodeClient(literal)
        assertIs<ClientMessage.Join>(decoded)
        assertEquals("tok-abc", decoded.token)

        assertSameJson(literal, encodeClient(decoded))
    }

    @Test
    fun anActionNestsTheEnginesOwnWireForm() {
        // The inner action is the engine's discriminated union, exactly as the corpus and
        // the TypeScript app write it: {"type": ..., "payload": {...}}.
        val literal =
            """{"type":"action","token":"tok-abc",""" +
                """"action":{"type":"DRAW_CARD","payload":{"playerId":"p1"}}}"""

        val decoded = decodeClient(literal)
        assertIs<ClientMessage.Action>(decoded)
        assertEquals(GameAction.DrawCard(PlayerIdPayload("p1")), decoded.action)

        assertSameJson(literal, encodeClient(decoded))
    }

    @Test
    fun theCursorMessagesRoundTrip() {
        listOf(
            """{"type":"resync","sinceIndex":7}""",
            """{"type":"add-bot"}""",
            """{"type":"remove-bot","seat":2}""",
            """{"type":"next-round"}""",
        ).forEach { literal ->
            assertSameJson(literal, encodeClient(decodeClient(literal)))
        }
    }

    // ------------------------------------------------------------------ server → client

    @Test
    fun aRefusalCarriesItsBackoffOnlyWhenThrottled() {
        // JavaScript writes the integer form; either side reads either. Decode-only.
        val throttled = decodeServer(
            """{"type":"error","message":"too many actions","retryAfterMs":1500}""",
        )
        assertIs<ServerMessage.Error>(throttled)
        assertEquals(1500.0, throttled.retryAfterMs)

        val plain = """{"type":"error","message":"malformed json"}"""
        val decoded = decodeServer(plain)
        assertIs<ServerMessage.Error>(decoded)
        assertNull(decoded.retryAfterMs, "no backoff unless the refusal was a rate limit")
        assertSameJson(plain, encodeServer(decoded))
    }

    @Test
    fun aSyncIsTheLogFromTheCursor() {
        val literal =
            """{"type":"sync","events":[{"index":3,"seat":-1,"playerId":"p4",""" +
                """"action":{"type":"DRAW_CARD","payload":{"playerId":"p4"}},"byBot":true}],""" +
                """"nextIndex":4}"""

        val decoded = decodeServer(literal)
        assertIs<ServerMessage.Sync>(decoded)
        assertEquals(1, decoded.events.size)
        assertTrue(decoded.events.single().byBot, "the room's own driver marks its moves")

        assertSameJson(literal, encodeServer(decoded))
    }

    @Test
    fun theLobbyBroadcastRoundTrips() {
        // Epoch fields as JavaScript writes them: plain integers. A Kotlin re-encode renders
        // the same values in scientific notation — both are JSON, both are read by both ends
        // — so this one is pinned by decode plus a value round trip, not by text.
        val literal =
            """{"type":"lobby","lobby":{"phase":"STARTING","seats":[""" +
                """{"index":0,"occupied":true,"isBot":false,"removable":false,"nickname":"Ann"},""" +
                """{"index":1,"occupied":true,"isBot":true,"removable":true,"nickname":"Bot 2"},""" +
                """{"index":2,"occupied":false,"isBot":false,"removable":false,"nickname":null},""" +
                """{"index":3,"occupied":true,"isBot":false,"removable":false,"nickname":"Bob"}],""" +
                """"humans":2,"startsAtEpochMs":1700000010000,"msUntilStart":9000}}"""
        // Deliberately without `botsOffered`, which is newer than this literal: a client or a
        // stored room from before the field must still decode, and the default is the answer
        // that was true for all of them — nobody's seats were filled by the room back then.

        val decoded = decodeServer(literal)
        assertIs<ServerMessage.Lobby>(decoded)
        assertEquals(RoomPhase.STARTING, decoded.lobby.phase)
        assertEquals(1_700_000_010_000.0, decoded.lobby.startsAtEpochMs)
        assertEquals(listOf(true, true, false, true), decoded.lobby.seats.map { it.occupied })
        assertTrue(decoded.lobby.seats[1].removable, "a filler bot may be taken back out")

        assertEquals(decoded, decodeServer(encodeServer(decoded)), "a re-encode loses nothing")
    }

    @Test
    fun eventsCarryTheLogAndAPerSeatView() {
        // `view` is written even when null — `#viewFor` returns `result.view ?? null` and
        // JSON.stringify keeps it — so the Kotlin field is EncodeDefault(ALWAYS) to match.
        val literal =
            """{"type":"events","events":[{"index":0,"seat":1,"playerId":"p2",""" +
                """"action":{"type":"DRAW_CARD","payload":{"playerId":"p2"}},"byBot":false}],""" +
                """"nextIndex":1,"view":null}"""

        val decoded = decodeServer(literal)
        assertIs<ServerMessage.Events>(decoded)
        assertEquals(0, decoded.events.single().index)
        assertNull(decoded.view)

        assertSameJson(literal, encodeServer(decoded))
    }

    @Test
    fun aLobbyJoinedHasEverythingButAView() {
        val literal =
            """{"type":"joined","seat":0,"token":"tok-raw","seats":[""" +
                """{"index":0,"playerId":null,"profile":{"nickname":"Ann"},"ownerId":null,""" +
                """"occupied":true},""" +
                """{"index":1,"playerId":null,"profile":null,"ownerId":null,"occupied":false},""" +
                """{"index":2,"playerId":null,"profile":null,"ownerId":null,"occupied":false},""" +
                """{"index":3,"playerId":null,"profile":null,"ownerId":null,"occupied":false}],""" +
                """"nextIndex":0,"lobby":{"phase":"LOBBY","seats":[""" +
                """{"index":0,"occupied":true,"isBot":false,"removable":false,"nickname":"Ann"},""" +
                """{"index":1,"occupied":false,"isBot":false,"removable":false,"nickname":null},""" +
                """{"index":2,"occupied":false,"isBot":false,"removable":false,"nickname":null},""" +
                """{"index":3,"occupied":false,"isBot":false,"removable":false,"nickname":null}],""" +
                """"humans":1,"startsAtEpochMs":null,"msUntilStart":null,""" +
                """"botsOffered":false},"view":null}"""

        val decoded = decodeServer(literal)
        assertIs<ServerMessage.Joined>(decoded)
        assertEquals("tok-raw", decoded.token, "the one message that carries the raw token")
        assertNull(decoded.view, "a lobby has no game and therefore no view")
        assertEquals(4, decoded.seats.size)

        assertSameJson(literal, encodeServer(decoded))
    }

    @Test
    fun theRoundBoundariesRoundTrip() {
        val standings =
            """[{"roundNumber":1,"vintoCallerId":"p1","scores":{"p1":5,"p2":9},""" +
                """"points":{"p1":3,"p2":-1}}]"""

        listOf(
            // The countdown path deals with nothing to report; no standings key at all.
            """{"type":"started","view":null,"nextIndex":0}""",
            // The next-round path reports the rounds so far on both of its outcomes.
            """{"type":"started","view":null,"nextIndex":42,"standings":$standings}""",
            """{"type":"between-rounds","view":null,"standings":$standings,"nextIndex":42}""",
            """{"type":"ended","reason":"not enough players"}""",
            """{"type":"closed","reason":"the room ended"}""",
        ).forEach { literal ->
            assertSameJson(literal, encodeServer(decodeServer(literal)))
        }
    }

    // ------------------------------------------------------------------ compatibility

    @Test
    fun anUnknownFieldIsSkippedNotFatal() {
        // The additive-compatibility rule made mechanical: an older client reading a newer
        // room's message must skip what it does not know.
        val decoded = decodeServer("""{"type":"error","message":"x","novelField":true}""")
        assertIs<ServerMessage.Error>(decoded)
        assertEquals("x", decoded.message)
    }

    // ------------------------------------------------------------------ plumbing

    private fun decodeClient(json: String): ClientMessage =
        ProtocolJson.decodeFromString(ClientMessage.serializer(), json)

    private fun encodeClient(message: ClientMessage): String =
        ProtocolJson.encodeToString(ClientMessage.serializer(), message)

    private fun decodeServer(json: String): ServerMessage =
        ProtocolJson.decodeFromString(ServerMessage.serializer(), json)

    private fun encodeServer(message: ServerMessage): String =
        ProtocolJson.encodeToString(ServerMessage.serializer(), message)

    /** Structural equality of the parsed JSON — see the class note on why not bytes. */
    private fun assertSameJson(expected: String, actual: String) {
        assertEquals(
            ProtocolJson.parseToJsonElement(expected),
            ProtocolJson.parseToJsonElement(actual),
            "the wire drifted:\n  contract: $expected\n  encoded:  $actual",
        )
    }
}
