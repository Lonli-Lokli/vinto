package game.vinto.app

import game.vinto.app.game.Stage
import game.vinto.app.game.prepareFor
import game.vinto.client.Anchor
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hand-over into a flourish, which used to drop the card for a frame or two.
 *
 * Reported from a phone: *"it was blinking on pile to the left of toss in area before animating
 * to discard"* — the drawn slot, where a card played from the deck is shown off.
 *
 * `Stage.expecting` already carries the fix for the same fault on **flights**, and its own
 * KDoc describes it in the reporter's word: the table steps to the move before the cards move,
 * so for a frame or two the engine's answer is on screen while the card is still where it
 * started, and a place that does not know what is coming draws the card, takes it away, and
 * puts it back — "one card, three appearances, and the middle one a blink."
 *
 * A flourish was never registered that way. `DrawnCard` drops its card the instant the action
 * engages (`actionPhase` leaves `choosing-action`, which is exactly when `cardInPlay` says the
 * card is on the pile), and the bloom that takes over does not start until the scene plays. In
 * between, the slot draws nothing and the card is at neither place.
 */
class BloomHandoverTest {

    private fun frameWith(vararg beats: Beat) = Frame(
        action = GameAction.UseCardAction(PlayerIdPayload("human-1")),
        scenes = listOf(beats.toList()),
        view = teachingSession().view.value,
    )

    private fun card(rank: Rank) = Card(
        id = "${rank.serialName}_probe",
        rank = rank,
        value = 0,
        played = false,
        actionText = "probe",
    )

    @Test
    fun theSlotACardWillBloomInKeepsItUntilTheBloomStarts() {
        val stage = Stage()
        val eight = card(Rank.EIGHT)

        stage.prepareFor(frameWith(Beat.Flourish(eight, Anchor.Pending)))

        assertEquals(
            CardView.Visible(eight),
            stage.aboutToBloom(Anchor.Pending),
            "the drawn slot was not told a card is about to be shown off in it",
        )
    }

    /**
     * And every pile that draws "the card in play" is told at the same moment, so it does not
     * put the played card down before the bloom has picked it up.
     */
    @Test
    fun theTableKnowsACardIsBeingShownOffBeforeTheSceneStarts() {
        val stage = Stage()

        assertEquals(false, stage.flourishing, "a fresh stage is showing something off")
        stage.prepareFor(frameWith(Beat.Flourish(card(Rank.EIGHT), Anchor.Pending)))
        assertTrue(stage.flourishing, "the table was not told until the scene started")
    }

    /** A frame with nothing to show off leaves both answers empty. */
    @Test
    fun aFrameThatShowsNothingOffPreparesNothing() {
        val stage = Stage()

        stage.prepareFor(frameWith(Beat.Move(Anchor.Deck, Anchor.Pending, card(Rank.EIGHT))))

        assertNull(stage.aboutToBloom(Anchor.Pending), "a flight was mistaken for a flourish")
        assertEquals(false, stage.flourishing, "a flight was mistaken for a flourish")
    }
}
