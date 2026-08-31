package game.vinto.room

import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A private room cannot be found by guessing.
 *
 * A private room is reachable by its code and by nothing else — it is never listed, and
 * `listPublicRooms` cannot name it — so the code is the only thing between a stranger and
 * somebody's table. The keyspace is 31^6 = 887,503,681 and at most 200 rooms are alive, so a
 * guess hits with probability about 2.3 in ten million: even odds needs roughly three million
 * guesses, which is hours from one host and under an hour spread across a few.
 *
 * Worth counting, therefore — which is what `CODE_ALPHABET`'s comment has always said and what
 * nothing did. Design R6 put the rate limit in a Cloudflare dashboard rule, `wrangler.jsonc`
 * recorded that it was deliberately not half-configured there, and it was never configured at
 * all; `resolveRoomCode` was a bare lookup that counted nothing. The limiter lives in the
 * registry rather than at the edge for two reasons a zone rule cannot answer: it cannot see
 * inside a WebSocket, which is how a room is joined, and it cannot tell a wrong code from a
 * right one, so it would throttle real players at the same rate as guessers.
 */
class GuessLimitTest {

    private val guesser = "aaaaaaaaaaaaaaaa"
    private val bystander = "bbbbbbbbbbbbbbbb"

    private fun resolve(json: String, code: String, source: String, at: Double): ResolveResult =
        VintoJson.decodeFromString(
            ResolveResult.serializer(),
            resolveRoomCodeFor(json, code, source, at),
        )

    /** A registry holding one private room, and the code that reaches it. */
    private fun registryWithARoom(): Pair<String, String> {
        val minted = VintoJson.decodeFromString(
            MintResult.serializer(),
            mintRoomCode(
                newRegistry(),
                "1,2,3,4,5,6",
                isPublic = false,
                hostNickname = "host",
                sourceId = "host",
                nowMs = 0.0,
            ),
        )
        return VintoJson.encodeToString(RegistryState.serializer(), minted.state) to minted.room!!.code
    }

    @Test
    fun guessingIsCutOffLongBeforeTheKeyspaceMatters() {
        var (json, real) = registryWithARoom()

        var throttledAt = -1
        for (attempt in 1..40) {
            val answer = resolve(json, "ZZZZZ$attempt".take(6), guesser, attempt.toDouble())
            json = VintoJson.encodeToString(RegistryState.serializer(), answer.state!!)
            if (answer.throttled && throttledAt < 0) throttledAt = attempt
        }

        assertTrue(throttledAt in 1..25, "never throttled, or far too late: $throttledAt")

        // And the refusal is not an oracle: a throttled source is refused whether or not it
        // guessed right. If the real code answered differently once the limit had bitten, the
        // limiter would be a way to *confirm* a guess rather than a way to stop them.
        val lucky = resolve(json, real, guesser, 41.0)
        assertTrue(lucky.throttled, "a throttled source was let through by guessing correctly")
        assertFalse(lucky.known, "and it was told the code exists")
    }

    /**
     * One guesser does not lock out everybody else.
     *
     * The failure this rules out is a limiter that counts globally, where a single scanner
     * takes online play down for everyone — a worse outcome than the scan.
     */
    @Test
    fun aScannerDoesNotShutTheDoorOnEverybodyElse() {
        var (json, real) = registryWithARoom()
        repeat(40) { i ->
            val answer = resolve(json, "ZZZZZ${i % 10}", guesser, i.toDouble())
            json = VintoJson.encodeToString(RegistryState.serializer(), answer.state!!)
        }

        val other = resolve(json, real, bystander, 41.0)
        assertFalse(other.throttled, "a bystander was throttled by somebody else's guessing")
        assertTrue(other.known, "and could not reach a room they had the code for")
    }

    /** A window that never expires is a permanent ban on a shared address. */
    @Test
    fun theCountForgetsAfterItsWindow() {
        var (json, real) = registryWithARoom()
        repeat(40) { i ->
            val answer = resolve(json, "ZZZZZ${i % 10}", guesser, i.toDouble())
            json = VintoJson.encodeToString(RegistryState.serializer(), answer.state!!)
        }
        assertTrue(resolve(json, real, guesser, 100.0).throttled, "not throttled to begin with")

        val later = 11 * 60 * 1000.0
        assertTrue(resolve(json, real, guesser, later).known, "still shut out after the window passed")
    }

    /**
     * Getting it right clears the record.
     *
     * Somebody who mistyped a code read down a telephone twice and then got it right was not
     * guessing, and should not carry those two into their next invitation.
     */
    @Test
    fun arrivingSomewhereRealForgivesTheTypos() {
        var (json, real) = registryWithARoom()
        repeat(3) { i ->
            val answer = resolve(json, "ZZZZZ$i", guesser, i.toDouble())
            json = VintoJson.encodeToString(RegistryState.serializer(), answer.state!!)
        }

        val hit = resolve(json, real, guesser, 10.0)
        assertTrue(hit.known)
        json = VintoJson.encodeToString(RegistryState.serializer(), hit.state!!)

        assertEquals(
            0,
            VintoJson.decodeFromString(RegistryState.serializer(), json).guesses.size,
            "the typos were remembered after the code worked",
        )
    }

    /**
     * The limiter is itself state an attacker can grow, and is bounded like everything else.
     *
     * A guesser rotating addresses could otherwise fill the registry's storage with counters —
     * turning the defence into the attack.
     */
    @Test
    fun theCounterTableCannotBeGrownWithoutLimit() {
        var json = registryWithARoom().first
        repeat(2_500) { i ->
            val answer = resolve(json, "ZZZZZ${i % 10}", "source-$i", i.toDouble())
            json = VintoJson.encodeToString(RegistryState.serializer(), answer.state!!)
        }

        val tracked = VintoJson.decodeFromString(RegistryState.serializer(), json).guesses.size
        assertTrue(tracked <= 2_000, "the counter table grew to $tracked records")
    }

    /**
     * An unattributed caller is neither counted nor refused.
     *
     * That is the JVM harness and the gate scripts, which have no address. The boundary is the
     * Worker, which always has one — so this is a convenience for tests and not a way in: a
     * real request cannot arrive without `cf-connecting-ip`, because Cloudflare sets it.
     */
    @Test
    fun aCallerWithNoAddressIsNotCounted() {
        var (json, real) = registryWithARoom()
        repeat(40) { i ->
            val answer = resolve(json, "ZZZZZ${i % 10}", "", i.toDouble())
            json = VintoJson.encodeToString(RegistryState.serializer(), answer.state ?: return@repeat)
        }
        assertTrue(resolve(json, real, "", 99.0).known, "the unattributed path started refusing")
    }
}
