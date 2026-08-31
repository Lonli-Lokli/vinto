package game.vinto.protocol

/**
 * The shape of a room code, declared once for everybody who has to agree on it.
 *
 * It lives here rather than in `shared/room` for the reason this module exists: a room code is
 * something a **client** and the **room** both reason about, and two implementations that
 * resemble each other is exactly the failure `shared/protocol` was created to prevent. The
 * registry mints them, the Worker refuses malformed ones before waking a Durable Object, and
 * the client now parses them out of an invite link — three callers, one rule.
 *
 * (`shared/room` targets only jvm and js, so a client on Android, iOS or Wasm could not have
 * shared it from there even if the layering were right.)
 */

/**
 * Unambiguous when read aloud or typed: no `0`/`O`, no `1`/`I`/`L`.
 *
 * A code is read down a telephone and typed by somebody who did not choose it. Every character
 * that has a lookalike is a support question, so none of them are here.
 */
public const val CODE_ALPHABET: String = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** Long enough that guessing is not worth it, short enough to read out. */
public const val CODE_LENGTH: Int = 6

/**
 * Whether a string could be a code the registry has ever issued.
 *
 * A **shape** check, not a lookup, and it holds no state — which is the whole point of it
 * being separable. The Worker asks this before the registry is asked anything, so a scan of
 * made-up values is refused by the stateless half of the service instead of waking the one
 * single-threaded object that knows every live room. The client asks it so a mistyped invite
 * fails on the device rather than costing a round trip.
 *
 * It is **not** the security boundary — `resolveRoomCode` is, and an attacker sending
 * well-formed guesses still reaches it. What this removes is the cheapest possible attack:
 * arbitrary strings, which cost the sender nothing and the registry a round trip each.
 */
public fun looksLikeRoomCode(code: String): Boolean {
    val upper = code.uppercase()
    return upper.length == CODE_LENGTH && upper.all { it in CODE_ALPHABET }
}
