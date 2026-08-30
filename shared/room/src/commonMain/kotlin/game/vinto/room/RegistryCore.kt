package game.vinto.room

import game.vinto.protocol.CODE_ALPHABET
import game.vinto.protocol.CODE_LENGTH
import game.vinto.protocol.PublicRoom
import game.vinto.protocol.PublicRooms
import game.vinto.shapes.VintoJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * The room-code namespace (design R4).
 *
 * Until now `?room=<anything>` reached `idFromName`, so any string brought a Durable Object
 * into existence and onto the bill. The fix is not a check on the way in — it is that **a
 * code must exist before a room does**. One registry owns the namespace: it mints codes,
 * resolves them, lists the public ones, and forgets a room when it dies. A code it has never
 * issued resolves to nothing, and the socket layer stops there without touching a room.
 *
 * Everything here is pure JSON in and JSON out, like [Room]: no clock, no randomness, no
 * platform. The bytes a code is built from arrive from `index.mjs`, which is where the
 * platform's random source lives.
 */

/**
 * 31 symbols, with every glyph that is read wrong out loud removed: no `0`/`O`, no `1`/`I`,
 * no `L`. Six of them is about 900 million codes — short enough to say down a phone, and
 * short enough that scanning is worth rate-limiting rather than worth ignoring (design R6).
 */
// The alphabet, the length and the shape check now live in `shared/protocol`, because a
// client parsing an invite link has to agree with the Worker that refuses a malformed one —
// and `shared/room` targets only jvm and js, so a phone could not have shared them from here.
// Re-exported by name below so this file reads as it did.

/**
 * Caps, which are what actually protect a budget over an hour (design R6).
 *
 * Rate limits bound the *slope* of abuse; these bound the total. An attacker who is happy to
 * wait can defeat a rate limit and cannot defeat a cap, and the free tier cares about how many
 * objects exist rather than how quickly they appeared.
 */
private const val MAX_LIVE_ROOMS = 200

/** One person should not be able to hold the whole namespace open, however patient they are. */
private const val MAX_ROOMS_PER_SOURCE = 5

/**
 * How many public rooms one listing may carry.
 *
 * Below [MAX_LIVE_ROOMS] on purpose. The cap is not there to protect the registry — it holds
 * two hundred at most — but the person reading: a browser is for finding a table, and a list
 * longer than a screen or two is a search problem nobody asked for. It also keeps the
 * response a bounded size whatever the registry grows into.
 */
private const val MAX_PUBLIC_LISTED = 50

/** Four seats to a table, so a room with fewer filled has somewhere to sit. */
private const val SEAT_COUNT = 4

/**
 * How many wrong codes one source may try before the registry stops answering it.
 *
 * A private room is reachable by its code and by nothing else, so the code is the only thing
 * standing between a stranger and somebody's table. The keyspace is 31^6 — 887,503,681 — and
 * with [MAX_LIVE_ROOMS] alive at once a single guess hits with probability about 2.3 in ten
 * million. Even odds needs roughly three million guesses, which is hours at a rate one host
 * can sustain and under an hour spread across a few. Worth rate-limiting, exactly as the
 * comment on [CODE_ALPHABET] has always said.
 *
 * **The rate limit those comments refer to did not exist.** Design R6 put it in the Cloudflare
 * dashboard as a zone rule, `wrangler.jsonc` records that it was deliberately not
 * half-configured there, and nobody ever configured it — so the documented mitigation was a
 * plan, and `resolveRoomCode` was a bare lookup that counted nothing. It lives here now for
 * two reasons beyond that: a zone rule cannot see inside a WebSocket, which is how a room is
 * actually joined, and it cannot tell a wrong code from a right one, so it would have to
 * throttle real players at the same rate as guessers.
 *
 * Twenty is far above a person mistyping a code read down a telephone and far below anything
 * that makes scanning worthwhile.
 */
private const val MAX_MISSES_PER_SOURCE = 20

/** The window a source's misses are counted over, after which it starts fresh. */
private const val MISS_WINDOW_MS = 10 * 60 * 1000.0

/**
 * How many sources are tracked at once.
 *
 * The limiter is itself state an attacker can grow, so it is bounded like everything else
 * here: a guesser rotating addresses could otherwise fill the registry's storage with
 * counters, turning the defence into the attack. Expired records go first; if a flood is live
 * enough that they are all current, the ones with the fewest misses are dropped, because the
 * record worth keeping is the one closest to acting.
 */
private const val MAX_TRACKED_SOURCES = 2_000

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RegisteredRoom(
    val code: String,
    /** The Durable Object name. Distinct from the code so the two can diverge later. */
    val roomId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val isPublic: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val hostNickname: String? = null,
    /** What a browser needs to decide whether to join: how full, and how soon. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val humans: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val seatsFilled: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val startsAtEpochMs: Double? = null,
    /**
     * Who asked for this room, as an opaque id.
     *
     * A hash of the connecting address, computed by `index.mjs` — the registry never sees an
     * IP, which keeps the per-source cap enforceable without the registry storing anything
     * anybody would mind it storing.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val sourceId: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RegistryState(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val rooms: List<RegisteredRoom> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val guesses: List<GuessRecord> = emptyList(),
) {
    val size: Int get() = rooms.size
}

/**
 * Wrong code guesses from one source, and when the count started.
 *
 * Keyed by the same hashed address the room cap uses, so the registry still never sees an
 * address. Only *failures* are counted: somebody typing a code that works is not guessing.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GuessRecord(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val sourceId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val misses: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val since: Double = 0.0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class MintResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val state: RegistryState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val room: RegisteredRoom? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ResolveResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val known: Boolean,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val room: RegisteredRoom? = null,
    /** Set when the source has spent its guesses; the Worker turns this into a 429. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val throttled: Boolean = false,
    /** The registry after counting this attempt. The caller must persist it. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val state: RegistryState? = null,
)

fun newRegistry(): String = VintoJson.encodeToString(RegistryState())

/**
 * Turns random bytes into a code.
 *
 * The caller supplies the entropy — one byte per character — because a Durable Object is the
 * platform's business and this file is not. Bytes are folded onto the alphabet by modulo,
 * which is very slightly biased towards its first symbols; that matters for a key and does
 * not matter here, where the code is a lookup handle backed by rate limiting rather than a
 * secret carrying the weight of an authorisation.
 */
private fun codeFrom(bytes: List<Int>): String =
    (0 until CODE_LENGTH)
        .map { CODE_ALPHABET[(bytes.getOrElse(it) { 0 }.mod(CODE_ALPHABET.length))] }
        .joinToString("")

/**
 * Mints a code and records the room against it.
 *
 * `randomBytes` is a comma-separated list from `crypto.getRandomValues`. A collision with a
 * live room is refused rather than silently reusing the entry — the caller retries with fresh
 * bytes, which is one line there and keeps the "one code, one room" invariant absolute here.
 */
@Suppress("ReturnCount")
fun mintRoomCode(
    registryJson: String,
    randomBytes: String,
    isPublic: Boolean,
    hostNickname: String,
    sourceId: String,
): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)

    // Caps before entropy: refusing is cheaper than minting and then discarding, and the
    // reason returned is the one the caller can act on.
    if (state.rooms.size >= MAX_LIVE_ROOMS) {
        return VintoJson.encodeToString(MintResult(state, error = "too many rooms are open"))
    }
    if (sourceId.isNotBlank() && state.rooms.count { it.sourceId == sourceId } >= MAX_ROOMS_PER_SOURCE) {
        return VintoJson.encodeToString(
            MintResult(state, error = "you already have $MAX_ROOMS_PER_SOURCE rooms open"),
        )
    }

    val bytes = randomBytes.split(",").mapNotNull { it.trim().toIntOrNull() }
    val code = codeFrom(bytes)

    if (state.rooms.any { it.code == code }) {
        return VintoJson.encodeToString(MintResult(state, error = "code collision"))
    }

    val room = RegisteredRoom(
        code = code,
        roomId = "room-$code",
        isPublic = isPublic,
        // Cleaned with the room's own rule, not stored as posted. This string is shown to
        // strangers on the public list, and the endpoint that sets it takes anything a
        // client cares to send — a kilobyte of control characters included. The UI caps the
        // field at sixteen, which stops the honest caller and nobody else.
        hostNickname = cleanNickname(hostNickname).takeIf { it.isNotEmpty() },
        sourceId = sourceId.takeIf { it.isNotBlank() },
    )
    return VintoJson.encodeToString(
        MintResult(state.copy(rooms = state.rooms + room), room = room),
    )
}

/**
 * Whether a code names a room, and which.
 *
 * This is the gate that replaces create-by-URL: the socket layer asks first and only reaches
 * a Durable Object for a code that comes back known.
 */
fun resolveRoomCode(registryJson: String, code: String): String =
    resolveRoomCodeFor(registryJson, code, sourceId = "", nowMs = 0.0)

/**
 * Resolve a code, counting this source's wrong guesses.
 *
 * Returns the answer **and the new registry state**, because counting a miss is a write — the
 * caller has to persist it or the limiter forgets everything between requests, which is the
 * shape of bug that makes a rate limiter look present and do nothing.
 *
 * A source over [MAX_MISSES_PER_SOURCE] is refused without a lookup, and the refusal is
 * marked `throttled` so the Worker can answer 429 rather than 404. That distinction leaks
 * nothing: it says "you have guessed too much", which the guesser already knows, and never
 * says whether the code they just tried exists. A correct guess by a throttled source is
 * refused too — the alternative is a limiter that can be probed past by luck.
 *
 * `sourceId` empty means "not attributable" — a caller with no address, which in practice is
 * the JVM test harness and the gates. Those are not counted and not refused; the boundary is
 * the Worker, which always has one.
 */
fun resolveRoomCodeFor(registryJson: String, code: String, sourceId: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)

    if (sourceId.isEmpty()) {
        val room = state.rooms.firstOrNull { it.code == code.uppercase() }
        return VintoJson.encodeToString(
            ResolveResult(known = room != null, room = room, state = state),
        )
    }

    val fresh = state.guesses.filter { nowMs - it.since < MISS_WINDOW_MS }
    val record = fresh.firstOrNull { it.sourceId == sourceId }

    if (record != null && record.misses >= MAX_MISSES_PER_SOURCE) {
        return VintoJson.encodeToString(
            ResolveResult(known = false, throttled = true, state = state.copy(guesses = fresh)),
        )
    }

    val room = state.rooms.firstOrNull { it.code == code.uppercase() }
    if (room != null) {
        // A hit clears the record. Somebody who mistyped twice and then got it right was not
        // guessing, and should not carry those two into their next invitation.
        return VintoJson.encodeToString(
            ResolveResult(
                known = true,
                room = room,
                state = state.copy(guesses = fresh.filterNot { it.sourceId == sourceId }),
            ),
        )
    }

    val counted =
        if (record == null) {
            fresh + GuessRecord(sourceId = sourceId, misses = 1, since = nowMs)
        } else {
            fresh.map { if (it.sourceId == sourceId) it.copy(misses = it.misses + 1) else it }
        }

    return VintoJson.encodeToString(
        ResolveResult(known = false, state = state.copy(guesses = pruned(counted))),
    )
}

/** Keeps the counter table bounded — see [MAX_TRACKED_SOURCES]. */
private fun pruned(guesses: List<GuessRecord>): List<GuessRecord> =
    if (guesses.size <= MAX_TRACKED_SOURCES) {
        guesses
    } else {
        guesses.sortedByDescending { it.misses }.take(MAX_TRACKED_SOURCES)
    }

/**
 * The public rooms, as a stranger browsing may see them.
 *
 * Private ones are absent — a private room is reachable by its code and by nothing else, and
 * is never named here, so browsing cannot enumerate what it cannot be told.
 *
 * What the public ones disclose is decided by [PublicRoom], an allow-list. This used to
 * answer with the registry's own records minus `sourceId`, which was correct on the day it
 * was written and would have published the next internal field somebody added — it already
 * carried `roomId`, the Durable Object's name, which nothing outside this file has any use
 * for. Naming what is public inverts that: a new field on [RegisteredRoom] stays private
 * until somebody adds it here on purpose.
 */
fun listPublicRooms(registryJson: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)

    val listed = state.rooms
        .filter { it.isPublic }
        // A table somebody can actually sit at comes first, then the busiest of those, and
        // the code breaks ties so the same registry always answers in the same order — a
        // list that reshuffles under a thumb is a list nobody can tap.
        .sortedWith(
            compareByDescending<RegisteredRoom> { it.seatsFilled < SEAT_COUNT }
                .thenByDescending { it.humans }
                .thenBy { it.code },
        )
        .take(MAX_PUBLIC_LISTED)
        .map {
            PublicRoom(
                code = it.code,
                hostNickname = it.hostNickname,
                humans = it.humans,
                seatsFilled = it.seatsFilled,
                // Resolved against the clock here, where the clock is, so a browser never
                // has to trust its own to read it.
                msUntilStart = it.startsAtEpochMs?.let { at -> (at - nowMs).coerceAtLeast(0.0) },
            )
        }

    return VintoJson.encodeToString(PublicRooms(rooms = listed))
}

/** The caps, exported so a harness cannot drift from what is enforced. */
fun maxLiveRooms(): Int = MAX_LIVE_ROOMS

fun maxRoomsPerSource(): Int = MAX_ROOMS_PER_SOURCE

/**
 * Forgets a room.
 *
 * Called when a room deletes itself (task 4.5), so the public list cannot outlive its rooms.
 * Idempotent: forgetting a code that is already gone is not an error, because a room that
 * dies twice is a retry rather than a bug.
 */
fun forgetRoom(registryJson: String, code: String): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)
    return VintoJson.encodeToString(
        state.copy(rooms = state.rooms.filterNot { it.code == code.uppercase() }),
    )
}

/**
 * Records what a lobby browser needs to see without opening a socket.
 *
 * Called by the room on the transitions that change it — a countdown starting or being
 * cancelled, a seat filling — rather than on a timer, so the write count is bounded by play
 * rather than by clock ticks.
 */
fun touchRoom(
    registryJson: String,
    code: String,
    humans: Int,
    seatsFilled: Int,
    startsAtEpochMs: Double,
): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)
    val wanted = code.uppercase()

    return VintoJson.encodeToString(
        state.copy(
            rooms = state.rooms.map {
                if (it.code == wanted) {
                    it.copy(
                        humans = humans,
                        seatsFilled = seatsFilled,
                        // Zero means "no countdown"; a nullable Double across the JS boundary
                        // is more trouble than the sentinel is worth here.
                        startsAtEpochMs = startsAtEpochMs.takeIf { at -> at > 0 },
                    )
                } else {
                    it
                }
            },
        ),
    )
}

/** How many rooms the registry believes are live. The cap in phase 5 is applied against this. */
fun registrySize(registryJson: String): Int =
    VintoJson.decodeFromString(RegistryState.serializer(), registryJson).size
