package game.vinto.client

/**
 * Frames waiting to be played.
 *
 * The reason this exists rather than "just animate it as it happens" is the gap between the
 * two machines: the engine runs in a Durable Object, the animation runs on a phone, and the
 * server does not wait (design C4). Between one tap and the next, three bots take their turns
 * in under a second — several frames' worth of things to see, arriving together.
 *
 * So the queue's job is not to hold work. It is to decide what to *skip*. A client that is
 * twelve events behind — a reconnect, a slow link, an app that was in somebody's pocket — has
 * a player who is not watching twelve animations, and playing them is worse than playing none.
 * Past [budget] the queue drops everything and the table lands on the current state, which is
 * the normal path after a reconnect rather than an error path.
 *
 * Speeding the backlog up instead was considered and rejected: it produces a screen that is
 * fastest exactly when it is least comprehensible.
 *
 * Nothing here is required for correctness. Drop every frame and the game is still right,
 * only less legible — which is the property that lets the queue take this liberty at all.
 *
 * @param takesTime whether an item costs the player any time to watch. Items that do not are
 *   still queued — a frame carries the table it leaves behind, and skipping it would strand
 *   the screen a move in the past — but they are free, and a hundred of them do not make a
 *   client "behind".
 */
class AnimationQueue<T>(
    private val budget: Int = DEFAULT_BUDGET,
    private val takesTime: (T) -> Boolean = { true },
) {

    private val waiting = ArrayDeque<T>()

    /** Items still to play. */
    val pending: Int get() = waiting.size

    /** How many have been dropped for being too far behind, over the session. */
    var skipped: Int = 0
        private set

    /**
     * Adds items, dropping the backlog if that puts the client too far behind.
     *
     * Whole batches are dropped rather than trimmed to the budget: a scene is only legible in
     * the context of the ones around it, so half of a swap is more confusing than none of it.
     */
    fun submit(items: List<T>) {
        if (items.isEmpty()) return

        val cost = waiting.count(takesTime) + items.count(takesTime)
        if (cost > budget) {
            skipped += waiting.size + items.size
            waiting.clear()
            return
        }
        waiting.addAll(items)
    }

    /** The next item to play, or null when there is nothing waiting. */
    fun next(): T? = waiting.removeFirstOrNull()

    /**
     * Abandons everything pending.
     *
     * For the two moments where the past has stopped mattering: a reconnect, and a new round.
     */
    fun collapse() {
        skipped += waiting.size
        waiting.clear()
    }

    private companion object {
        /**
         * About two turns' worth.
         *
         * Enough that a bot's whole turn — draw, decide, discard, and the toss-in window that
         * follows — plays through; not so much that a player who looked away comes back to a
         * replay.
         */
        const val DEFAULT_BUDGET = 8
    }
}
