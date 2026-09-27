package game.vinto.app

import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableSizes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A table's cards grow with its felt, as a phone's are sized to its own.
 *
 * They grew in three steps, by the felt's height: a phone's table below 560 points, a roomier one
 * to 720, a grand one to 900 and a desktop's above. So a felt of 708 points — a Galaxy Tab on its
 * side — was dealt the cards of a 560-point one: a layout review found a phone's table on a
 * tablet's cloth, with half the green empty. The steps also jumped: a window one point taller than
 * 720 dealt cards a third bigger.
 *
 * A phone's cards are about a tenth of its felt's height, and that is the proportion a table reads
 * at. So above a phone's felt the cards grow with it, continuously, until they reach a desktop's.
 */
class CardsGrowWithTheFeltTest {

    @Test
    fun aTablesCardsAreATenthOfItsFeltAsAPhonesAre() {
        for (felt in 560..900 step 10) {
            val cards = TableSizes.forHeight(felt.dp).theirs
            assertTrue(
                cards.height.value >= felt * TENTH,
                "a $felt-point felt was dealt ${cards.height.value}-point cards, under a tenth of it",
            )
        }
    }

    @Test
    fun theCardsGrowWithTheFeltWithoutAStep() {
        var was = TableSizes.forHeight(560.dp)
        for (felt in 564..1200 step 4) {
            val now = TableSizes.forHeight(felt.dp)
            assertTrue(now.theirs.width >= was.theirs.width, "the cards shrank as the felt grew to $felt")
            assertTrue(
                now.theirs.width.value <= was.theirs.width.value * MOST_PER_STEP,
                "the cards jumped from ${was.theirs.width} to ${now.theirs.width} at a $felt-point felt",
            )
            was = now
        }
    }

    private companion object {
        const val TENTH = 0.1f

        /** Four points of felt may not grow a card by more than three percent. */
        const val MOST_PER_STEP = 1.03f
    }
}
