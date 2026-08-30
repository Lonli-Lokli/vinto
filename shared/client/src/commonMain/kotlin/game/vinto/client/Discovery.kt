package game.vinto.client

import game.vinto.protocol.PublicRoom
import kotlin.math.ceil

/**
 * The public-room browser, decided here rather than in a composable.
 *
 * Same split as `lobbyUi`: the screen draws what it is told, and what it is told is a pure
 * function of the answer the service gave. That is what makes "a room with three humans and a
 * countdown of four seconds" a test rather than a thing somebody has to reproduce by opening
 * two phones at the right moment.
 */

/** One room as a row: what it is, and whether tapping it can work. */
data class DiscoveryRow(
    val code: String,
    /** Display text the room already sanitised, or null when the host never gave one. */
    val host: String?,
    val seatsFilled: Int,
    val seatsFree: Int,
    val humans: Int,
    /** False when the table is full — the row is still shown, because it fills and empties. */
    val joinable: Boolean,
    /** Whole seconds until the deal, when a countdown is running. */
    val startsInSeconds: Int?,
)

/**
 * Everything the browser screen needs, including which of the four spinners it is in.
 *
 * [loading] and [refreshing] are separate states rather than one boolean, and the difference
 * is the whole reason this is polished rather than adequate: the first load has nothing to
 * show and earns the middle of the screen, while a refresh has a list already and must not
 * take it away — replacing a list somebody is reading with a spinner loses their place and
 * their tap.
 */
data class DiscoveryState(
    val rows: List<DiscoveryRow> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /**
     * Why there is no list, when there is none.
     *
     * The whole answer rather than its words: a screen that only had the string could say
     * what the service said and not what the player should do about it, and the two are not
     * the same sentence.
     */
    val failure: RoomAnswer.Failed? = null,
) {
    /** True when the service answered, answered fine, and simply had nothing to list. */
    val quiet: Boolean get() = rows.isEmpty() && !loading && failure == null
}

/**
 * Turns the service's answer into rows.
 *
 * The order arrives already decided by the registry — joinable first, then busiest, then by
 * code — and is kept, deliberately: a client that re-sorts makes two people looking at the
 * same lobby see two different lists, and one of them taps the wrong row.
 */
fun discoveryRows(rooms: List<PublicRoom>): List<DiscoveryRow> = rooms.map { room ->
    val filled = room.seatsFilled.coerceIn(0, SEATS)
    DiscoveryRow(
        code = room.code,
        host = room.hostNickname?.takeIf { it.isNotBlank() },
        seatsFilled = filled,
        seatsFree = SEATS - filled,
        humans = room.humans.coerceIn(0, SEATS),
        joinable = filled < SEATS,
        // Rounded up, so a countdown reads "1" for its whole last second rather than sitting
        // on "0" while everybody waits for something to happen. Zero is dropped rather than
        // drawn: a countdown that has run out is a room mid-deal, not a room to wait for.
        //
        // The service resolves the deadline against its own clock and sends what is left, so
        // nothing here depends on this device's idea of the time.
        startsInSeconds = room.msUntilStart?.let { left ->
            ceil(left / MS_PER_SECOND).toInt().takeIf { it > 0 }
        },
    )
}

/** Every game is exactly four players (a decision, not a constant to tune). */
private const val SEATS = 4

private const val MS_PER_SECOND = 1000.0
