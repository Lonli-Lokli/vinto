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
data class Identity(val guestId: String, val nickname: String)

/**
 * This device's identity, minted on first ask and stable after.
 *
 * @param entropy fresh randomness from the platform, used only when no id exists yet.
 */
fun Vault.identity(entropy: () -> Long): Identity {
    val existing = read(GUEST_KEY)
    val guestId = existing ?: mintGuestId(entropy(), entropy()).also { write(GUEST_KEY, it) }
    return Identity(guestId = guestId, nickname = read(NICKNAME_KEY).orEmpty())
}

/** Remembers what this player likes to be called. Sanitisation is the room's job. */
fun Vault.rememberNickname(nickname: String) {
    if (nickname.isBlank()) erase(NICKNAME_KEY) else write(NICKNAME_KEY, nickname)
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
private fun tokenKey(roomCode: String) = "vinto.online.token.${roomCode.uppercase()}"

private const val HEX = 16
private const val GUEST_HEX_DIGITS = 32
