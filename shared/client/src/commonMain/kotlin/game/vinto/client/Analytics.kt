package game.vinto.client

import game.vinto.protocol.AnalyticsEvent
import game.vinto.protocol.AnalyticsJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * Where a client's events go. One method, because there is one verb.
 *
 * Returning `Unit` rather than a success flag is deliberate: nothing upstream may branch on
 * whether analytics worked. A failed send is not an error condition, it is a lost count.
 */
public fun interface AnalyticsTransport {
    public suspend fun send(payloadJson: String)
}

/**
 * Whether anything may be sent at all, asked once before the first event rather than filtered
 * afterwards.
 *
 * Two independent reasons to stay silent, and either is sufficient: the platform reports a
 * Global Privacy Control or Do-Not-Track signal, or the player turned analytics off in
 * Settings. There is no reduced mode — a signal that says "do not track me" is not an
 * invitation to send less.
 */
public data class AnalyticsConsent(val optedIn: Boolean, val platformObjects: Boolean) {
    public val allowed: Boolean get() = optedIn && !platformObjects
}

/**
 * The client's sink: batched, bounded, fire-and-forget.
 *
 * What it deliberately is not is a pipeline. There is no retry, no persistence and no queue
 * that survives a restart, because a lost analytics event is worth nothing and a client
 * spending battery to re-send one is worth less than nothing.
 *
 * Four properties, each answering a way this normally goes wrong:
 *
 * - **Never on the hot path.** [record] is a `trySend` to a buffer and nothing else. It
 *   cannot suspend, cannot block a move, an animation frame or a socket write, and cannot
 *   throw. `LocalPacing` set this precedent: the table is not made slower by something that
 *   is not the game.
 * - **Bounded, dropping the newest.** A bug that emits in a loop costs a fixed amount.
 *   Dropping the newest keeps the beginning of the session, which is the part that explains
 *   what happened; dropping the oldest would keep the loop and throw away the cause.
 * - **Consent first.** [record] checks before buffering, so an opted-out session never holds
 *   an event even in memory.
 * - **No identity.** There is no session id, no device id and nothing derived from either.
 *   Grouping within a sitting is done server-side from what arrives together in one batch,
 *   which needs nothing stored and cannot follow anybody to tomorrow.
 */
public class Analytics(
    private val transport: AnalyticsTransport,
    private var consent: AnalyticsConsent,
    scope: CoroutineScope,
    private val batchSize: Int = BATCH,
    capacity: Int = CAP,
) {
    // A plain bounded channel, *not* `BufferOverflow.DROP_LATEST`.
    //
    // They drop the same event — the newest — but the overflow policy drops it silently and
    // still reports success to `trySend`, so the sink would count a flood as accepted and the
    // cap would be a comment rather than a fact. With the default policy a full buffer makes
    // `trySend` fail, which is both the drop and the signal, and `record` still never
    // suspends. The first version used the policy and `aFloodIsDroppedRatherThanQueued`
    // caught it: 1000 accepted against a capacity of 8.
    private val pending = Channel<AnalyticsEvent>(capacity = capacity)

    /** Events accepted since the sink was made. Exposed so a test can prove the cap bites. */
    public var accepted: Int = 0
        private set

    /** Events refused because the buffer was full or consent was absent. */
    public var dropped: Int = 0
        private set

    init {
        scope.launch {
            val batch = mutableListOf<AnalyticsEvent>()
            while (isActive) {
                // Waits for the first, then takes whatever else is already there. A batch
                // therefore forms around real activity rather than on a timer that wakes a
                // sleeping phone to say nothing.
                batch += pending.receive()
                while (batch.size < batchSize) {
                    batch += pending.tryReceive().getOrNull() ?: break
                }
                flush(batch.toList())
                batch.clear()
            }
        }
    }

    /**
     * Notes something happened. Returns immediately, always.
     *
     * @return true when the event was accepted into the buffer, for tests. No caller should
     *   branch on it.
     */
    public fun record(event: AnalyticsEvent): Boolean {
        if (!consent.allowed) {
            dropped++
            return false
        }
        val taken = pending.trySend(event).isSuccess
        if (taken) accepted++ else dropped++
        return taken
    }

    /**
     * Consent changed while the app was running.
     *
     * Turning it off discards what is buffered rather than flushing it: a player who opts out
     * mid-round did not agree to the first half of the round either.
     */
    public fun consentChanged(now: AnalyticsConsent) {
        consent = now
        if (!now.allowed) {
            while (pending.tryReceive().isSuccess) dropped++
        }
    }

    private suspend fun flush(batch: List<AnalyticsEvent>) {
        if (batch.isEmpty()) return
        val payload = runCatching {
            AnalyticsJson.encodeToString(ListSerializer(AnalyticsEvent.serializer()), batch)
        }.getOrNull() ?: return
        // A transport that fails, times out or throws must not take the sink down with it —
        // the loop has to survive to accept the next batch.
        runCatching { transport.send(payload) }
    }

    private companion object {
        /** Enough that a round's worth of events travels in one or two posts. */
        const val BATCH = 20

        /**
         * A session's whole allowance.
         *
         * Generous for honest use — a long session emits tens of events — and small enough
         * that a loop costs a bounded amount of memory and one post.
         */
        const val CAP = 200
    }
}
