package game.vinto.client

/**
 * Scenes waiting to be played.
 *
 * The reason this exists rather than "just animate it as it happens" is the gap between the
 * two machines: the engine runs in a Durable Object, the animation runs on a phone, and the
 * server does not wait (design C4). Between one tap and the next, three bots take their turns
 * in under a second — several scenes' worth of things to see, arriving together.
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
 * Nothing here is required for correctness. Drop every scene and the game is still right,
 * only less legible — which is the property that lets the queue take this liberty at all.
 */
class AnimationQueue(private val budget: Int = DEFAULT_BUDGET) {

    private val waiting = ArrayDeque<Scene>()

    /** Scenes still to play. */
    val pending: Int get() = waiting.size

    /** How many scenes have been dropped for being too far behind, over the session. */
    var skipped: Int = 0
        private set

    /**
     * Adds scenes, dropping the backlog if that puts the client too far behind.
     *
     * Whole batches are dropped rather than trimmed to the budget: a scene is only legible in
     * the context of the ones around it, so half of a swap is more confusing than none of it.
     */
    fun submit(scenes: List<Scene>) {
        val meaningful = scenes.filter { it.isNotEmpty() }
        if (meaningful.isEmpty()) return

        if (waiting.size + meaningful.size > budget) {
            skipped += waiting.size + meaningful.size
            waiting.clear()
            return
        }
        waiting.addAll(meaningful)
    }

    /** The next scene to play, or null when there is nothing waiting. */
    fun next(): Scene? = waiting.removeFirstOrNull()

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
