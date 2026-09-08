package game.vinto.app

import game.vinto.app.game.seatingFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Everybody at the table agrees who is sitting where.
 *
 * You see yourself at the bottom and everybody else around you, so no two players see the same
 * picture — but they must see the *same table*. If Ann is on Bob's left then Bob is on Ann's
 * right, in every client, or the four of them are not playing the same game: "the one on my
 * left" is how people at a card table refer to each other, and a Jack that swaps "two cards
 * from two different players" is aimed by pointing at a chair.
 *
 * Reported from two browsers side by side, in a room with two people in it: each saw the other
 * on their *left*, which cannot be true of one table. The felt was picking chairs by index into
 * the list with the viewer removed, so where the opponents landed depended on where the viewer
 * had been sitting in it.
 */
class SeatingTest {

    private val table = listOf("ann", "bob", "cat", "dan")

    @Test
    fun everySeatSeesTheSameTable() {
        // For every pair, what A calls B has to be the mirror of what B calls A.
        val opposite = mapOf("left" to "right", "right" to "left", "top" to "top")

        for (viewer in table) {
            val mine = seatingFor(table, viewer)
            for (other in table.filter { it != viewer }) {
                val whereTheyAre = chairOf(mine, other)
                val theirs = seatingFor(table, other)
                val whereIAm = chairOf(theirs, viewer)
                assertEquals(
                    opposite[whereTheyAre],
                    whereIAm,
                    "$viewer has $other on the $whereTheyAre, but $other has $viewer on the $whereIAm",
                )
            }
        }
    }

    @Test
    fun youAreAlwaysAtTheBottom() {
        table.forEach { viewer ->
            assertEquals(viewer, seatingFor(table, viewer).near, "$viewer is not in the near chair")
        }
    }

    /** A watcher has no seat, and the four players still fill the four chairs. */
    @Test
    fun aWatcherStillSeesFourSeats() {
        val watching = seatingFor(table, viewer = null)
        assertEquals(
            table.toSet(),
            listOfNotNull(watching.near, watching.left, watching.top, watching.right).toSet(),
            "a watcher lost a player: $watching",
        )
    }

    private fun chairOf(seating: game.vinto.app.game.Seating<String>, who: String) = when (who) {
        seating.left -> "left"
        seating.top -> "top"
        seating.right -> "right"
        else -> "near"
    }
}
