package game.vinto.worker

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
private const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

private const val CODE_LENGTH = 6

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RegisteredRoom(
    val code: String,
    /** The Durable Object name. Distinct from the code so the two can diverge later. */
    val roomId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val isPublic: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val hostNickname: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RegistryState(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val rooms: List<RegisteredRoom> = emptyList(),
) {
    val size: Int get() = rooms.size
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class MintResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val state: RegistryState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val room: RegisteredRoom? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class ResolveResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val known: Boolean,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val room: RegisteredRoom? = null,
)

@JsExport
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
@JsExport
fun mintRoomCode(
    registryJson: String,
    randomBytes: String,
    isPublic: Boolean,
    hostNickname: String,
): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)
    val bytes = randomBytes.split(",").mapNotNull { it.trim().toIntOrNull() }
    val code = codeFrom(bytes)

    if (state.rooms.any { it.code == code }) {
        return VintoJson.encodeToString(MintResult(state, error = "code collision"))
    }

    val room = RegisteredRoom(
        code = code,
        roomId = "room-$code",
        isPublic = isPublic,
        hostNickname = hostNickname.takeIf { it.isNotBlank() },
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
@JsExport
fun resolveRoomCode(registryJson: String, code: String): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)
    val room = state.rooms.firstOrNull { it.code == code.uppercase() }
    return VintoJson.encodeToString(ResolveResult(known = room != null, room = room))
}

/** The public rooms, as a stranger browsing may see them. Private ones are simply absent. */
@JsExport
fun listPublicRooms(registryJson: String): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)
    return VintoJson.encodeToString(
        RegistryState(rooms = state.rooms.filter { it.isPublic }),
    )
}

/**
 * Forgets a room.
 *
 * Called when a room deletes itself (task 4.5), so the public list cannot outlive its rooms.
 * Idempotent: forgetting a code that is already gone is not an error, because a room that
 * dies twice is a retry rather than a bug.
 */
@JsExport
fun forgetRoom(registryJson: String, code: String): String {
    val state = VintoJson.decodeFromString(RegistryState.serializer(), registryJson)
    return VintoJson.encodeToString(
        state.copy(rooms = state.rooms.filterNot { it.code == code.uppercase() }),
    )
}

/** How many rooms the registry believes are live. The cap in phase 5 is applied against this. */
@JsExport
fun registrySize(registryJson: String): Int =
    VintoJson.decodeFromString(RegistryState.serializer(), registryJson).size
