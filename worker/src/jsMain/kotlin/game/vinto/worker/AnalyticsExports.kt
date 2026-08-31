package game.vinto.worker

import game.vinto.protocol.AnalyticsEvent
import game.vinto.protocol.AnalyticsJson
import game.vinto.protocol.Cost
import game.vinto.protocol.DataPoint
import game.vinto.protocol.Difficulty
import game.vinto.protocol.RoundEnding
import game.vinto.protocol.SessionEnding
import game.vinto.protocol.toDataPoint
import kotlinx.serialization.encodeToString

/**
 * Data points, built on the Kotlin side so the JS shim never invents a shape.
 *
 * The shim knows *when* something happened; it does not get to decide what a data point looks
 * like. Everything below goes through the same sealed `AnalyticsEvent` and the same
 * `toDataPoint`, which is what makes `AnalyticsPrivacyTest`'s guarantee reach the wire rather
 * than stopping at the type: there is no path from the Worker to the store that skips it.
 *
 * Each returns a JSON string because that is what crosses the Kotlin/JS boundary cleanly, and
 * `null` when the event could not be built — the shim's `emit` treats null as "nothing to
 * write", so a bad call loses a count instead of failing a request.
 */

private fun point(event: AnalyticsEvent, wallMs: Double, requests: Double): String =
    AnalyticsJson.encodeToString(event.toDataPoint(Cost(wallMs, requests)))

private fun difficultyOf(name: String): Difficulty =
    Difficulty.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Difficulty.MODERATE

@JsExport
public fun roomCreatedPoint(listed: Boolean, difficulty: String, wallMs: Double, requests: Double): String =
    point(AnalyticsEvent.RoomCreated(listed, difficultyOf(difficulty)), wallMs, requests)

@JsExport
public fun seatFilledPoint(
    humans: Int,
    bots: Int,
    byBot: Boolean,
    wallMs: Double,
    requests: Double,
): String = point(AnalyticsEvent.SeatFilled(humans, bots, byBot), wallMs, requests)

@JsExport
public fun seatVacatedPoint(
    humans: Int,
    bots: Int,
    grace: Boolean,
    wallMs: Double,
    requests: Double,
): String = point(AnalyticsEvent.SeatVacated(humans, bots, grace), wallMs, requests)

@JsExport
public fun botTookOverPoint(humans: Int, wallMs: Double, requests: Double): String =
    point(AnalyticsEvent.BotTookOver(humans), wallMs, requests)

@JsExport
public fun reconnectedPoint(awayMs: Double, wallMs: Double, requests: Double): String =
    point(AnalyticsEvent.Reconnected(awayMs), wallMs, requests)

@JsExport
public fun roundStartPoint(
    humans: Int,
    bots: Int,
    roundNumber: Int,
    wallMs: Double,
    requests: Double,
): String = point(AnalyticsEvent.RoundStart(humans, bots, roundNumber), wallMs, requests)

@JsExport
public fun roundEndPoint(
    actions: Int,
    durationMs: Double,
    endedBy: String,
    callerWon: Boolean,
    wallMs: Double,
    requests: Double,
): String {
    val ending = RoundEnding.entries.firstOrNull { it.name.equals(endedBy, ignoreCase = true) }
        ?: RoundEnding.ABANDONED
    return point(AnalyticsEvent.RoundEnd(actions, durationMs, ending, callerWon), wallMs, requests)
}

@JsExport
public fun sessionEndedPoint(
    reason: String,
    rounds: Int,
    durationMs: Double,
    wallMs: Double,
    requests: Double,
): String {
    val ending = SessionEnding.entries.firstOrNull { it.name.equals(reason, ignoreCase = true) }
        ?: SessionEnding.EVERYBODY_LEFT
    return point(AnalyticsEvent.SessionEnded(ending, rounds, durationMs), wallMs, requests)
}

/**
 * A client's event, re-built here from what it posted rather than forwarded.
 *
 * This is the whole security posture of `POST /e`: the body is decoded into the sealed type,
 * which drops anything not declared there, and the point is built from the decoded value. A
 * field nobody declared cannot reach the store, and a client-supplied timestamp is not
 * believed because there is nowhere for one to go.
 *
 * Returns null when the body is not a known event, which the shim treats as nothing to write.
 */
@JsExport
public fun clientEventPoint(eventJson: String): String? = runCatching {
    val event = AnalyticsJson.decodeFromString(AnalyticsEvent.serializer(), eventJson)
    // No `Cost`: a client cannot know what the room spent, and inventing a zero would put a
    // number in the same column that means something else entirely.
    AnalyticsJson.encodeToString<DataPoint>(event.toDataPoint())
}.getOrNull()
