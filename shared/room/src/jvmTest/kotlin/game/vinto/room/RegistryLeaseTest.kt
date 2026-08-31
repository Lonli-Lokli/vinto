package game.vinto.room

import game.vinto.protocol.PublicRooms
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The registry's lease: silence is death, and a dead row cannot hurt anybody.
 *
 * A room delists itself when it dies, and that message is best-effort by design — a registry
 * briefly unreachable must not take a room down with it. The lease is what makes best-effort
 * safe: a row whose room has stopped speaking falls off the public list, stops resolving, and
 * is swept at the next mint. Without it, one lost `/forget` was an immortal row — which is
 * not hypothetical: three ghost lobbies sat on the live public list for hours while the
 * `/forget` handler read the code from the wrong half of the request, and every deletion ever
 * sent removed nothing.
 *
 * The other half of that incident — the real Room's dying request meeting the real Registry
 * handler — cannot be tested from the JVM at all; `gate-delisting.mjs` holds it.
 */
class RegistryLeaseTest {

    private val minute = 60_000.0
    private val t0 = 1_000_000_000.0

    private fun minted(at: Double, bytes: String = "1,2,3,4,5,6", source: String = "host"): MintResult =
        VintoJson.decodeFromString(
            MintResult.serializer(),
            mintRoomCode(
                newRegistry(),
                bytes,
                isPublic = true,
                hostNickname = "Ada",
                sourceId = source,
                nowMs = at,
            ),
        )

    private fun listed(json: String, at: Double): List<String> =
        VintoJson.decodeFromString(PublicRooms.serializer(), listPublicRooms(json, at))
            .rooms
            .map { it.code }

    private fun stateJson(result: MintResult): String =
        VintoJson.encodeToString(RegistryState.serializer(), result.state)

    @Test
    fun aRoomThatKeepsSpeakingStaysListed() {
        val mint = minted(at = t0)
        val code = mint.room!!.code
        var json = stateJson(mint)

        // Nine minutes of silence is inside the lease; a touch then restarts it.
        assertTrue(code in listed(json, t0 + 9 * minute), "listed inside the lease")
        json = touchRoom(json, code, humans = 2, seatsFilled = 3, startsAtEpochMs = 0.0, nowMs = t0 + 9 * minute)
        assertTrue(code in listed(json, t0 + 18 * minute), "a touch renews the lease")
    }

    @Test
    fun aRoomThatHasStoppedSpeakingFallsOffTheListButStillResolves() {
        val mint = minted(at = t0)
        val code = mint.room!!.code
        val json = stateJson(mint)

        val silent = t0 + 11 * minute
        assertFalse(code in listed(json, silent), "eleven silent minutes and it is not advertised")

        // Still resolvable: hiding is about strangers browsing, and a seat token reconnecting
        // must reach its room for as long as the room could possibly still exist.
        val answer = VintoJson.decodeFromString(
            ResolveResult.serializer(),
            resolveRoomCodeFor(json, code, sourceId = "somebody", nowMs = silent),
        )
        assertTrue(answer.known, "hidden is not gone")
    }

    @Test
    fun anHourOfSilenceStopsResolvingEntirely() {
        val mint = minted(at = t0)
        val code = mint.room!!.code
        val json = stateJson(mint)

        val dead = t0 + 61 * minute
        val answer = VintoJson.decodeFromString(
            ResolveResult.serializer(),
            resolveRoomCodeFor(json, code, sourceId = "somebody", nowMs = dead),
        )
        // Admitting this join would mint a fresh empty room under a dead code — a ghost that
        // then touches its own leaked row back to life.
        assertFalse(answer.known, "a dead row does not resolve")
    }

    @Test
    fun mintingSweepsTheDeadSoLeakedRowsCannotHoldTheCaps() {
        val first = minted(at = t0)
        val json = stateJson(first)

        val later = t0 + 61 * minute
        val next = VintoJson.decodeFromString(
            MintResult.serializer(),
            mintRoomCode(json, "9,9,9,9,9,9", isPublic = true, hostNickname = "Bo", sourceId = "host", nowMs = later),
        )

        val codes = next.state.rooms.map { it.code }
        assertEquals(listOf(next.room!!.code), codes, "the dead row was swept by the mint")
    }

    /**
     * The row shape the live registry actually holds today: written before the lease existed,
     * so it has no `lastTouchedAtEpochMs` at all. It decodes as zero, and zero is long-dead —
     * which is the whole migration: the poisoned rows hide immediately and sweep on the next
     * mint, with no operator step.
     */
    @Test
    fun aRowFromBeforeTheLeaseIsAlreadyDead() {
        val legacy = """{"rooms":[{"code":"GHSTX2","roomId":"room-GHSTX2","isPublic":true,""" +
            """"hostNickname":"Ghost","humans":1,"seatsFilled":1,"startsAtEpochMs":null,""" +
            """"sourceId":null}],"guesses":[]}"""
        val now = 1_756_680_000_000.0

        assertTrue(listed(legacy, now).isEmpty(), "a legacy row is not advertised")

        val answer = VintoJson.decodeFromString(
            ResolveResult.serializer(),
            resolveRoomCodeFor(legacy, "GHSTX2", sourceId = "somebody", nowMs = now),
        )
        assertFalse(answer.known, "and does not resolve")

        val mint = VintoJson.decodeFromString(
            MintResult.serializer(),
            mintRoomCode(legacy, "3,1,4,1,5,9", isPublic = false, hostNickname = "", sourceId = "s", nowMs = now),
        )
        assertEquals(1, mint.state.rooms.size, "and the next mint sweeps it")
        assertFalse(mint.state.rooms.any { it.code == "GHSTX2" })
    }
}
