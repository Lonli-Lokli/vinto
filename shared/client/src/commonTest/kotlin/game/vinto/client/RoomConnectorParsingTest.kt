package game.vinto.client

import game.vinto.protocol.ProtocolJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The half of every connector that is not transport, tested once for all four.
 *
 * `parseCreatedRoom`, `parsePublicRooms` and `createRoomBody` exist so that the JVM, Android,
 * iOS and browser connectors stay transport and nothing else — which means a fault here
 * breaks room creation on every platform at once, and until now nothing ran them outside a
 * real socket. `answering` is the other shared piece: the one place the client may catch
 * broadly, and therefore the one place a platform's own words can reach a screen.
 *
 * `commonTest`, so the same cases run in Node, in a browser and on the simulator — the same
 * argument `RoomTroubleTest` makes about the status mapping.
 */
class RoomConnectorParsingTest {

    // ------------------------------------------------------------------ POST /rooms

    @Test
    fun aCreatedRoomIsReadOffTheServicesAnswer() {
        val created = parseCreatedRoom("""{"code":"7KQ2MP","roomId":"room-7KQ2MP","isPublic":true}""")

        assertEquals(CreatedRoom(code = "7KQ2MP", roomId = "room-7KQ2MP"), created)
    }

    /** A room that was not created has no code to return, so the refusal is an error. */
    @Test
    fun aRefusalToCreateIsAnErrorNotACodelessRoom() {
        val refused = assertFailsWith<IllegalStateException> {
            parseCreatedRoom("""{"error":"too many rooms are open"}""")
        }
        assertTrue("too many rooms are open" in refused.message.orEmpty(), "the service's reason was dropped")

        assertFailsWith<NoSuchElementException> { parseCreatedRoom("""{"roomId":"room-X"}""") }
    }

    @Test
    fun theRequestBodySaysWhatWasAskedAndSurvivesAnAwkwardName() {
        val body = ProtocolJson.parseToJsonElement(
            createRoomBody(isPublic = false, hostNickname = "Ann \"the\" Great"),
        ).jsonObject

        assertEquals(false, body.getValue("isPublic").jsonPrimitive.content.toBoolean())
        // As typed: sanitising is the room's job, and a quote is not a reason to mangle a name.
        assertEquals("Ann \"the\" Great", body.getValue("hostNickname").jsonPrimitive.content)
        assertEquals(setOf("isPublic", "hostNickname"), body.keys, "the body carries exactly what the endpoint takes")
    }

    // ------------------------------------------------------------------ GET /rooms

    /** A client in somebody's pocket is older than the service; a room list is not a contract to refuse over. */
    @Test
    fun aRoomListSkipsWhatItDoesNotKnow() {
        val rooms = parsePublicRooms(
            """{"rooms":[{"code":"7KQ2MP","hostNickname":"Ada","humans":2,"seatsFilled":3,""" +
                """"msUntilStart":9000,"mood":"jolly"}],"served":"edge-7"}""",
        )

        assertEquals(1, rooms.size)
        assertEquals("7KQ2MP", rooms.single().code)
        assertEquals(9_000.0, rooms.single().msUntilStart)

        assertTrue(parsePublicRooms("""{"rooms":[]}""").isEmpty(), "a quiet evening is an ordinary answer")
        assertTrue(parsePublicRooms("{}").isEmpty(), "and so is an answer with nothing to list")
    }

    // ------------------------------------------------------------------ answering

    @Test
    fun answeringHandsBackWhatItGot() = runTest {
        assertEquals(RoomAnswer.Ok(42), answering { 42 })
    }

    /** What the service itself said keeps its trouble and its words — they are better than ours. */
    @Test
    fun answeringKeepsTheServicesOwnRefusal() = runTest {
        val answer = answering<Unit> { throw RoomServiceException(RoomTrouble.NO_SUCH_ROOM, "no such room") }

        assertEquals(RoomAnswer.Failed(RoomTrouble.NO_SUCH_ROOM, "no such room"), answer)
    }

    /**
     * Anything else is the network not being there — and what is said about it is ours, not
     * the platform's.
     *
     * In aeroplane mode Android's answer is `Unable to resolve host "vinto-room.kupalinka.app":
     * No address associated with hostname`, the JVM's names the host and a port, and a
     * browser's is `Failed to fetch`. Each used to be handed to the screen as the detail line
     * under "No connection to the room service" — a hostname in the player's face, on the one
     * screen where they had done nothing wrong. The trouble already says everything a person
     * can act on; the platform's sentence is for a log.
     */
    @Test
    fun answeringNeverShowsAPlayerTheTransportsOwnWords() = runTest {
        val offline = answering<Unit> {
            throw IllegalStateException(
                "Unable to resolve host \"vinto-room.kupalinka.app\": No address associated with hostname",
            )
        }

        val failed = assertIs<RoomAnswer.Failed>(offline)
        assertEquals(RoomTrouble.OFFLINE, failed.trouble)
        assertFalse("kupalinka" in failed.reason, "a hostname reached the screen: ${failed.reason}")
        assertFalse("resolve host" in failed.reason, "the platform's sentence reached the screen: ${failed.reason}")
        assertTrue(failed.reason.isNotBlank(), "a refusal with nothing to say")

        val silent = answering<Unit> { throw IllegalStateException() }
        assertEquals(
            failed.reason,
            assertIs<RoomAnswer.Failed>(silent).reason,
            "one sentence for every transport, message or not",
        )
    }

    /** A cancelled call is the caller going away, not a failure to report. */
    @Test
    fun answeringNeverSwallowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            answering<Unit> { throw CancellationException("the screen went away") }
        }
    }

    @Test
    fun aFailureCarriesNoAccessorThatQuietlyDiscardsIt() {
        // The type's whole reason to exist: a `when` over it has two arms, and a caller that
        // wants the value has to say what happens when there is none.
        val answer: RoomAnswer<Int> = RoomAnswer.Failed(RoomTrouble.BUSY, "full")
        val value = when (answer) {
            is RoomAnswer.Ok -> answer.value
            is RoomAnswer.Failed -> null
        }
        assertNull(value)
    }
}
