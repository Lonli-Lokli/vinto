package game.vinto.room

import game.vinto.protocol.PublicRooms
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The registry's caps and its list: the two things that bound what online play costs and
 * what a stranger browsing is told.
 *
 * Design R6 makes the argument: rate limits bound the slope of abuse and caps bound the
 * total, and the free tier cares about how many objects exist rather than how quickly they
 * appeared. `GuessLimitTest` next door holds the slope; this holds the totals, and the
 * ordering promise `Discovery.kt` relies on when it keeps the service's order rather than
 * re-sorting on the phone.
 */
class RegistryCapsTest {

    @Test
    fun theNamespaceIsCappedAtTwoHundredLiveRooms() {
        var json = newRegistry()
        repeat(maxLiveRooms()) { i ->
            val minted = mint(json, bytesFor(i), source = "source-$i")
            assertNull(minted.error, "room $i refused: ${minted.error}")
            json = encodeRegistry(minted.state)
        }
        assertEquals(maxLiveRooms(), registrySize(json))

        val over = mint(json, bytesFor(maxLiveRooms()), source = "source-over")
        assertEquals("too many rooms are open", over.error)
        assertNull(over.room)
        assertEquals(maxLiveRooms(), over.state.rooms.size, "a refused mint still added a row")

        // Forgetting one makes room for one.
        val freed = forgetRoom(json, decodeRegistry(json).rooms.first().code)
        assertNull(mint(freed, bytesFor(maxLiveRooms()), source = "source-over").error)
    }

    @Test
    fun onePersonMayHoldFiveRoomsAndNoMore() {
        var json = newRegistry()
        repeat(maxRoomsPerSource()) { i ->
            json = encodeRegistry(mint(json, bytesFor(i), source = "one-phone").state)
        }

        val sixth = mint(json, bytesFor(9), source = "one-phone")
        assertEquals("you already have ${maxRoomsPerSource()} rooms open", sixth.error)

        assertNull(mint(json, bytesFor(9), source = "somebody-else").error, "one person's cap shut everybody out")
        assertNull(mint(json, bytesFor(9), source = "").error, "an unattributed caller is not a person to cap")
    }

    @Test
    fun aCodeAlreadyInUseIsNotReissued() {
        val first = mint(newRegistry(), "1,2,3,4,5,6", source = "a")
        val collision = mint(encodeRegistry(first.state), "1,2,3,4,5,6", source = "b")

        assertEquals("code collision", collision.error, "the same bytes minted the same code twice")
        assertEquals(1, collision.state.rooms.size)
    }

    /**
     * The bytes are folded onto the alphabet by modulo, and a short list is padded with the
     * alphabet's first symbol rather than crashing — a Durable Object's random source is not
     * something this file gets to assume the shape of.
     */
    @Test
    fun theRandomBytesAreFoldedOntoTheAlphabet() {
        fun codeFrom(bytes: String): String? = mint(newRegistry(), bytes, source = "a").room?.code

        assertEquals("234567", codeFrom("0,1,2,3,4,5"))
        assertEquals("2Z2Z2Z", codeFrom("31,30,62,61,93,92"), "bytes wrap at the alphabet's length")
        assertEquals("222222", codeFrom(""), "no bytes at all is still a code")
        assertEquals("322222", codeFrom("1"), "a short list is padded, not a crash")
        assertEquals(
            "room-234567",
            mint(newRegistry(), "0,1,2,3,4,5", source = "a").room?.roomId,
            "the object is named by the code",
        )
    }

    @Test
    fun forgettingARoomIsIdempotentAndCaseInsensitive() {
        val minted = mint(newRegistry(), "1,2,3,4,5,6", source = "a")
        val code = assertNotNull(minted.room).code
        val json = encodeRegistry(minted.state)

        val forgotten = forgetRoom(json, code.lowercase())
        assertEquals(0, registrySize(forgotten), "a code read in lower case did not match its room")
        assertEquals(0, registrySize(forgetRoom(forgotten, code)), "forgetting twice is a retry, not an error")
        assertEquals(
            1,
            registrySize(forgetRoom(json, "ZZZZZZ")),
            "forgetting a code nobody has touched somebody's room",
        )
    }

    @Test
    fun aCodeResolvesWhateverItsCaseAndAnUnknownOneToNothing() {
        val minted = mint(newRegistry(), "1,2,3,4,5,6", source = "a")
        val code = assertNotNull(minted.room).code
        val json = encodeRegistry(minted.state)

        val known = resolve(json, code.lowercase())
        assertTrue(known.known)
        assertEquals(code, known.room?.code)
        assertFalse(known.throttled)

        val unknown = resolve(json, "ZZZZZZ")
        assertFalse(unknown.known)
        assertNull(unknown.room)
        assertFalse(unknown.throttled, "an unattributed lookup is never throttled")
    }

    // ------------------------------------------------------------------ the list

    /**
     * Joinable tables first, the busiest of those next, and the code breaks ties — so the
     * same registry always answers in the same order, and a list under somebody's thumb does
     * not reshuffle. Private rooms are simply not there.
     */
    @Test
    fun browsingListsPublicRoomsOnlyAndInAStableOrder() {
        var json = newRegistry()
        fun add(bytes: String, public: Boolean, humans: Int, seats: Int, startsAt: Double = 0.0): String {
            val minted = mint(json, bytes, source = "s$bytes", public = public)
            val code = assertNotNull(minted.room).code
            json = touchRoom(encodeRegistry(minted.state), code, humans, seats, startsAt, NOW)
            return code
        }
        val quiet = add("0,0,0,0,0,0", public = true, humans = 1, seats = 1)
        val busy = add("1,1,1,1,1,1", public = true, humans = 3, seats = 3)
        val full = add("2,2,2,2,2,2", public = true, humans = 4, seats = 4)
        val secret = add("3,3,3,3,3,3", public = false, humans = 2, seats = 2)
        val busyToo = add("4,4,4,4,4,4", public = true, humans = 3, seats = 3, startsAt = NOW + 9_000)

        val listed = listed(json, NOW)

        assertEquals(listOf(busy, busyToo, quiet, full), listed.map { it.code })
        assertFalse(listed.any { it.code == secret }, "a private room was advertised: $secret")
        assertEquals(
            9_000.0,
            listed.first { it.code == busyToo }.msUntilStart,
            "the countdown is resolved against the clock here",
        )
        assertNull(listed.first { it.code == busy }.msUntilStart, "no countdown, no number")

        // A countdown that has run out reads as zero rather than as a negative wait.
        assertEquals(0.0, listed(json, NOW + 20_000).first { it.code == busyToo }.msUntilStart)
    }

    /** A browser is for finding a table, and the response is a bounded size whatever the registry holds. */
    @Test
    fun theListIsCappedAtFifty() {
        var json = newRegistry()
        repeat(60) { i ->
            json = encodeRegistry(mint(json, bytesFor(i), source = "s$i", public = true).state)
        }

        assertEquals(60, registrySize(json))
        assertEquals(50, listed(json, NOW).size)
    }

    @Test
    fun onlyAHostNameFromTheSharedVocabularyReachesTheList() {
        val real = mint(newRegistry(), "1,2,3,4,5,6", source = "a", public = true, host = "Quiet Heron")
        assertEquals("Quiet Heron", real.room?.hostNickname, "a minted name is kept")

        // The public list is the one place a stranger reads a name without having joined
        // anything, so it is the place typed text must not reach. Filtering characters was the
        // old rule and never had an opinion about words; the vocabulary does.
        listOf("  <b>Ada</b>   Lovelace  ", "!!!", "Ada", "quiet heron").forEach { host ->
            val sent = mint(newRegistry(), "1,2,3,4,5,6", source = "a", public = true, host = host)
            assertNull(sent.room?.hostNickname, "'$host' reached the public list")
            assertNull(listed(encodeRegistry(sent.state), NOW).single().hostNickname)
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun mint(
        json: String,
        bytes: String,
        source: String,
        public: Boolean = false,
        host: String = "Ada",
    ): MintResult = VintoJson.decodeFromString(
        MintResult.serializer(),
        mintRoomCode(json, bytes, isPublic = public, hostNickname = host, sourceId = source, nowMs = NOW),
    )

    private fun resolve(json: String, code: String): ResolveResult =
        VintoJson.decodeFromString(ResolveResult.serializer(), resolveRoomCode(json, code))

    private fun listed(json: String, at: Double) =
        VintoJson.decodeFromString(PublicRooms.serializer(), listPublicRooms(json, at)).rooms

    private fun encodeRegistry(state: RegistryState): String =
        VintoJson.encodeToString(RegistryState.serializer(), state)

    private fun decodeRegistry(json: String): RegistryState =
        VintoJson.decodeFromString(RegistryState.serializer(), json)

    /** Six bytes that differ for every `i` below 961, so no two mints collide by accident. */
    private fun bytesFor(i: Int): String = "${i / 31},${i % 31},7,7,7,7"

    private companion object {
        const val NOW = 1_000_000_000.0
    }
}
