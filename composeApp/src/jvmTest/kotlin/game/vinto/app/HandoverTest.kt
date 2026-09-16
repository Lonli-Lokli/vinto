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
 * The hand-over into a scene, which used to drop the card for a frame or two.
 *
 * Reported from a phone, twice: *"it was blinking on pile to the left of toss in area before
 * animating to discard"*, and then *"there is still blink when swapping the card — bot drawn and
 * decided to swap with his own"*. Both are the drawn slot, and both are the same fault.
 *
 * `Stage.expecting` already carries the fix for it on the *arriving* side of a flight, and its
 * own KDoc describes it in the reporter's word: the table steps to the move before the cards
 * move, so for a frame or two the engine's answer is on screen while the card is still where it
 * started, and a place that does not know what is coming draws the card, takes it away, and puts
 * it back — "one card, three appearances, and the middle one a blink."
 *
 * Two things were never registered that way, and the drawn slot is where both show.
 *
 * A **flourish**: `DrawnCard` drops its card the instant the action engages (`actionPhase` leaves
 * `choosing-action`, which is exactly when `cardInPlay` starts saying the card is on the pile),
 * and the bloom that takes over does not start until the scene plays.
 *
 * A flight's **origin**: `fly` hands the destination over as the flight starts — "one drawing
 * owns the card at every moment" — and nothing did the same for the place being left. So a card
 * drawn and then swapped into a hand vanished from the slot the moment the table stepped, and
 * came back only once it was in the air.
 */
class HandoverTest {

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
            stage.heldAt(Anchor.Pending),
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

    /**
     * And the slot a card is about to fly *out* of keeps it, until the flight picks it up.
     *
     * A swap is two flights and the first of them leaves the drawn slot — which the stepped
     * view has already emptied, because the swap consumed the pending action.
     */
    @Test
    fun theSlotACardWillFlyOutOfKeepsItUntilTheFlightStarts() {
        val stage = Stage()
        val eight = card(Rank.EIGHT)

        stage.prepareFor(frameWith(Beat.Move(Anchor.Pending, Anchor.Seat("bot-1", 0), eight)))

        assertEquals(
            CardView.Visible(eight),
            stage.heldAt(Anchor.Pending),
            "the drawn slot was not told its card is about to fly out of it",
        )
    }

    /** A card nobody may see travels face-down, and is held face-down. */
    @Test
    fun aCardNobodyMaySeeIsHeldAsItsBack() {
        val stage = Stage()

        stage.prepareFor(frameWith(Beat.Move(Anchor.Pending, Anchor.Seat("bot-1", 0), null)))

        assertEquals(CardView.Hidden, stage.heldAt(Anchor.Pending), "a face-down card was dropped")
    }

    /**
     * A card *arriving* at a place is not held there, and is not a flourish either.
     *
     * The control both halves need: what is held at a place is what that place is about to lose,
     * and the arriving side has had its own answer — `expecting` — all along.
     */
    @Test
    fun aCardArrivingIsNotACardBeingLetGoOf() {
        val stage = Stage()

        stage.prepareFor(frameWith(Beat.Move(Anchor.Deck, Anchor.Pending, card(Rank.EIGHT))))

        assertNull(stage.heldAt(Anchor.Pending), "the slot held a card that is flying towards it")
        assertEquals(false, stage.flourishing, "a flight was mistaken for a flourish")
    }
}
