package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The queue's job is deciding what to skip.
 *
 * Playing everything is easy and wrong: a client twelve events behind has a player who is not
 * watching twelve animations. These are the cases that decide whether a reconnect looks like
 * a game resuming or like a replay nobody asked for.
 */
class AnimationQueueTest {

    private fun scene(n: Int): Scene = listOf(Beat.Move(Anchor.Deck, Anchor.Seat("p", n)))

    /**
     * A queue of bare scenes.
     *
     * The app queues `Frame`s, which are scenes plus the table they leave behind; the queue
     * itself does not care which, and testing it on the simpler of the two keeps these cases
     * about backlog and skipping rather than about building player views.
     */
    private fun queueOf(budget: Int = 8) =
        AnimationQueue<Scene>(budget = budget, takesTime = { it.isNotEmpty() })

    @Test
    fun scenesComeBackOutInTheOrderTheyWentIn() {
        val queue = queueOf()
        queue.submit(listOf(scene(1), scene(2)))

        assertEquals(2, queue.pending)
        assertEquals(scene(1), queue.next())
        assertEquals(scene(2), queue.next())
        assertNull(queue.next(), "and then nothing")
    }

    /**
     * Aiming a Jack moves nothing — but it is still a move, and in the app the item carries
     * the table that move left behind. Dropping it would strand the screen a move in the
     * past; charging the backlog for it would have a King's worth of silent bookkeeping
     * counting as a client falling behind.
     */
    @Test
    fun somethingWithNothingToSeeIsKeptButCostsNothing() {
        val queue = queueOf(budget = 2)
        queue.submit(listOf(emptyList(), scene(1), emptyList()))
        queue.submit(listOf(scene(2)))

        assertEquals(4, queue.pending, "all four are still there, in order")
        assertEquals(0, queue.skipped, "two silent ones did not push it over a budget of two")
    }

    /**
     * The case this class exists for: a client that fell behind lands on the current state
     * rather than working through the backlog.
     */
    @Test
    fun aClientTooFarBehindPlaysNoneOfIt() {
        val queue = queueOf(budget = 4)
        queue.submit(List(3) { scene(it) })
        assertEquals(3, queue.pending, "three is within budget")

        queue.submit(List(9) { scene(it) })

        assertEquals(0, queue.pending, "twelve is not, so none of it plays")
        assertEquals(12, queue.skipped, "and it says so, rather than losing it quietly")
        assertNull(queue.next())
    }

    /**
     * Whole batches, not trimmed ones. Half of a swap — one card crossing and the other not —
     * is more confusing than neither.
     */
    @Test
    fun aBatchIsDroppedWholeRatherThanTrimmed() {
        val queue = queueOf(budget = 3)
        queue.submit(List(2) { scene(it) })
        queue.submit(List(2) { scene(it + 10) })

        assertEquals(0, queue.pending)
    }

    @Test
    fun collapsingAbandonsWhatIsWaiting() {
        val queue = queueOf()
        queue.submit(List(3) { scene(it) })

        queue.collapse()

        assertEquals(0, queue.pending)
        assertEquals(3, queue.skipped)
        assertTrue(queue.next() == null, "a reconnect starts from now, not from then")
    }
}
