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
 * **The budget is about how far behind the client is, not how much happened.** Those were the
 * same number until a Vinto call proved they are not: the endgame arrives in one batch and is
 * the one long batch a player is actually watching. See [DEFAULT_BUDGET].
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
         * A whole final round's worth, which is the largest thing that legitimately arrives at
         * once.
         *
         * It was 8 — "about two turns" — and that number was chosen against the case this
         * class was written for, a client catching up after a reconnect. It was wrong for the
         * one case where a lot happens *while the player watches*: calling Vinto plays the
         * call and then all three bots' entire last turns, and `LocalGameSession` hands them
         * over as one batch. Measured at **14** moves in an ordinary deal
         * (`FinalRoundIsWatchedTest`), so the batch went over 8, the queue cleared it, and the
         * player went from tapping "Call Vinto" straight to "Round over" — never seeing the
         * endgame their whole hand had been played for. Reported from a phone, and the worst
         * kind of bug this queue can have: it is doing precisely what it was told.
         *
         * 24 covers a final round with room for three bots who each draw, act, declare and
         * answer a toss-in. It does not weaken the guard it was there for: a remote client's
         * catch-up never reaches here as a long batch, because `RemoteGameSession` collapses a
         * sync to a single frame before this sees it (design C4).
         */
        const val DEFAULT_BUDGET = 24
    }
}
