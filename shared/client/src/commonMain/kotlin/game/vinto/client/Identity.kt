package game.vinto.client

/**
 * Who this device is, online: a guest id, a nickname, and — per room — the seat token.
 *
 * There are no accounts (design R3 reserves the seam; nothing occupies it yet). What online
 * play actually needs is smaller: a stable-enough id for "you again", a name to show, and
 * the one credential that matters — the **seat token** a room hands out on join. The token
 * is what lets a player reconnect to their seat after a tunnel, a crash or a phone call,
 * including a seat a bot has been playing in the meantime; losing it means losing the seat,
 * which is why it goes straight into the vault the moment it arrives.
 *
 * Entropy is the caller's: shared code mints ids from numbers it is handed, never from a
 * clock or an ambient random source — the same rule the engine and the room follow, and the
 * reason every one of these functions is trivially testable.
 */
data class Identity(
    val guestId: String,
    val nickname: String,
    /**
     * The face, as the two numbers that draw it: which family, and the seed within it.
     *
     * Not a picture and not a file name. `shared/protocol`'s `Avatar.kt` turns the pair into
     * geometry every client draws identically, so what is stored here — and what later travels
     * on the wire — is small enough to be free and says nothing about anybody.
     *
     * `-1` means this device has never chosen, which is not the same as choosing family zero:
     * a player who has not picked should be offered a face, and one who deliberately took the
     * first face on the first row should keep it.
     */
    val avatarKind: Int = UNCHOSEN,
    val avatarSeed: Long = 0,
    /** Which of the measured grounds it sits on; an index, so the palette can grow. */
    val avatarGround: Int = 0,
) {
    /** Whether a face has ever been chosen on this device. */
    val hasAvatar: Boolean get() = avatarKind != UNCHOSEN
}

/** No face chosen yet — distinct from having chosen the first one. */
const val UNCHOSEN: Int = -1

/**
 * A ground for somebody who never picked one, from the one thing they already have.
 *
 * Not random: it has to be the same colour every time this device sits down, and a random one
 * would make a player a different colour in every room. The guest id is already stable and
 * already theirs, and every character of it moves the answer, so two ids that differ anywhere
 * differ here.
 */
internal fun groundFrom(guestId: String): Int =
    guestId.fold(0) { acc, ch -> acc * GROUND_MIX + ch.code }.let { if (it < 0) -it else it }

/** An odd multiplier, so every character of the id moves the answer. */
private const val GROUND_MIX = 31

/**
 * This device's identity, minted on first ask and stable after.
 *
 * @param entropy fresh randomness from the platform, used only when no id exists yet.
 */
fun Vault.identity(entropy: () -> Long): Identity {
    val existing = read(GUEST_KEY)
    val guestId = existing ?: mintGuestId(entropy(), entropy()).also { write(GUEST_KEY, it) }
    return Identity(
        guestId = guestId,
        nickname = read(NICKNAME_KEY).orEmpty(),
        avatarKind = read(AVATAR_KIND_KEY)?.toIntOrNull() ?: UNCHOSEN,
        // A seed that will not parse is treated as no seed rather than as zero: zero is a real
        // face, and silently seating somebody behind it would look like the vault had ignored them.
        avatarSeed = read(AVATAR_SEED_KEY)?.toLongOrNull() ?: 0,
        // A ground nobody chose is taken from the guest id rather than left at zero.
        //
        // Zero is the first colour in the palette — a pale pewter — so every player who had not
        // opened the face picker sat behind the *same* pale disc, and a table of them was a
        // table where the colour told you nothing. Reported as "I do not see color assigned to
        // player", from a room with two people in it wearing one colour between them.
        //
        // The id is already this device's own and already stable, so a ground derived from it is
        // stable too: the same player is the same colour in every room they join, without
        // anything new being stored or sent. `PlayerProfile.ground()` takes it modulo the
        // palette, so any integer names some colour and the palette can still grow.
        avatarGround = read(AVATAR_GROUND_KEY)?.toIntOrNull() ?: groundFrom(guestId),
    )
}

/** Remembers what this player likes to be called. Sanitisation is the room's job. */
fun Vault.rememberNickname(nickname: String) {
    if (nickname.isBlank()) erase(NICKNAME_KEY) else write(NICKNAME_KEY, nickname)
}

/**
 * Remembers the face this player sits down behind.
 *
 * Three values written together, because two of them apart are not a face: a seed under the
 * wrong family draws something its owner never chose, which is the one way this can go wrong
 * silently. Written on the way out of the screen, like the nickname, rather than on every tap
 * of a picker that offers forty-two of them.
 */
fun Vault.rememberAvatar(kind: Int, seed: Long, ground: Int) {
    write(AVATAR_KIND_KEY, kind.toString())
    write(AVATAR_SEED_KEY, seed.toString())
    write(AVATAR_GROUND_KEY, ground.toString())
}

/**
 * The room this device stepped out of and can still walk back into, if any.
 *
 * The vault reads and writes by key with no way to enumerate, so "which rooms do I hold a seat
 * in" is not a question it can answer — this is the one room worth remembering, and there is
 * only ever one worth remembering because a person sits at one table.
 *
 * Written on the way into a room and cleared by [forgetRoom] when the seat is given up for good
 * or the room turns out to be gone. The pair with [seatToken] is what makes returning possible:
 * the code says where, the token says who.
 */
fun Vault.currentRoom(): String? = read(ROOM_KEY)?.takeIf { it.isNotBlank() }

/** Remembers the room to offer a way back into. */
fun Vault.rememberRoom(code: String) = write(ROOM_KEY, code.uppercase())

/**
 * Forgets it — the seat was given up, or the room refused us and is not worth offering again.
 *
 * The seat token goes with it. A token for a room that will not have us back is not a
 * credential, it is a stale key in a pocket, and keeping it would make the next refusal look
 * like the same bug twice.
 */
fun Vault.forgetRoom() {
    currentRoom()?.let { forgetSeatToken(it) }
    erase(ROOM_KEY)
}

/** The seat token this device holds for [roomCode], or null if it never joined it. */
fun Vault.seatToken(roomCode: String): String? = read(tokenKey(roomCode))

/**
 * Files the seat token for [roomCode] — called with the `joined` message still in hand,
 * because the token is delivered exactly once and a crash before the write loses the seat.
 */
fun Vault.saveSeatToken(roomCode: String, token: String) = write(tokenKey(roomCode), token)

/** Drops the token once the room is gone; a credential for nothing is only a liability. */
fun Vault.forgetSeatToken(roomCode: String) = erase(tokenKey(roomCode))

/**
 * A guest id from two longs of caller-supplied entropy: `guest-` and 32 hex digits. Not a
 * credential — the token is — just an ownership seam the room stores as `ownerId`.
 */
internal fun mintGuestId(a: Long, b: Long): String {
    val hex = a.toULong().toString(HEX) + b.toULong().toString(HEX)
    return "guest-${hex.padStart(GUEST_HEX_DIGITS, '0')}"
}

private const val GUEST_KEY = "vinto.online.guest"
private const val NICKNAME_KEY = "vinto.online.nickname"
private const val AVATAR_KIND_KEY = "vinto.online.avatar.kind"
private const val AVATAR_SEED_KEY = "vinto.online.avatar.seed"
private const val AVATAR_GROUND_KEY = "vinto.online.avatar.ground"
private const val ROOM_KEY = "vinto.online.room"
private fun tokenKey(roomCode: String) = "vinto.online.token.${roomCode.uppercase()}"

private const val HEX = 16
private const val GUEST_HEX_DIGITS = 32
