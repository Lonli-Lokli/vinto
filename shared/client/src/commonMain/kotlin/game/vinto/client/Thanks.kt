package game.vinto.client

import game.vinto.shapes.VintoJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * How many times this player has said thanks.
 *
 * One number, and it is the only record this app keeps of a purchase. There is still no receipt,
 * no entitlement and nothing to restore — `Support.kt` is unchanged on all three — because what
 * is kept here is not proof of having paid. It buys nothing, it is checked by nothing, and a
 * tampered client that wrote itself a 90 would have given itself a number.
 *
 * ### Why it is not in [Stats]
 *
 * [Stats] is a record of *play*, and Settings can throw it away: a losing streak is somebody's
 * own business and data nobody can clear is data nobody agreed to keep. This is a record of
 * generosity, and the product owner's decision is that "Clear it" must not reach it — a control
 * for forgetting a bad run that also erased four acts of kindness would be the wrong thing
 * happening for a right-sounding reason. Separate key, separate lifetime, and `ThanksTest` pins
 * that `forgetStats` leaves it alone.
 *
 * The cost of that decision, stated plainly because it cuts against the line above: there is no
 * way to clear this from inside the app. It is one integer on the device, tied to nobody.
 *
 * ### Why it only ever goes up
 *
 * Monotonic on purpose, and that is what makes [merge] free. A count that can only rise has a
 * total order, so two copies of it need no clock, no ordering and no conflict resolution — the
 * larger reading is always the truer one. That is the whole of the reinstall story.
 */
@Serializable
data class Thanks(
    @SerialName("version") val version: Int = FORMAT,

    /**
     * Thanks given, not purchases made.
     *
     * Play's quantity selector turns one tap into several — one transaction, one token,
     * `quantity` 3 — and somebody who gives three that way has done the same thing as somebody
     * who crossed three sheets for the same money. Counting transactions would show them a
     * smaller number for it.
     */
    val given: Int = 0,
) {
    /**
     * This record, plus a purchase of [count] of them.
     *
     * Clamped at zero because the argument comes from a store rather than from this app, and a
     * number that can go backwards is not the monotonic one [merge] depends on.
     */
    fun plus(count: Int): Thanks = copy(given = given + count.coerceAtLeast(0))

    /** The truer of two readings of the same count — see the note on going up, above. */
    fun merge(other: Thanks): Thanks = if (other.given > given) other else this

    companion object {
        /** Bumped when the shape changes; an older file is replaced by an empty record. */
        const val FORMAT = 1
    }
}

private const val KEY = "vinto.thanks"

/**
 * What this player has given, or nothing yet.
 *
 * Never throws, for the same reason [loadStats] does not: a file this app wrote badly must not
 * be a reason it cannot start.
 */
fun Vault.loadThanks(): Thanks {
    val stored = read(KEY) ?: return Thanks()

    return try {
        val decoded = VintoJson.decodeFromString(Thanks.serializer(), stored)
        if (decoded.version == Thanks.FORMAT) decoded else Thanks()
    } catch (_: SerializationException) {
        Thanks()
    } catch (_: IllegalArgumentException) {
        Thanks()
    }
}

/** Keeps it. Deliberately has no `forget` beside it — see the note on [Stats] above. */
fun Vault.saveThanks(thanks: Thanks) {
    write(KEY, VintoJson.encodeToString(Thanks.serializer(), thanks))
}
