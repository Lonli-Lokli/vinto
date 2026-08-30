package game.vinto.client

import game.vinto.shapes.Rank

/**
 * What a button says, as an *identity* rather than as words.
 *
 * The second slice of §6h, and the one that turned out to carry a bug rather than only a
 * translation problem.
 *
 * `Choice.label` was a `String`, and two things read it: the UI, to draw the button, and
 * `TeachScript`, to decide which button the lesson should point at. The second is the problem.
 * Identifying a control by the English it happens to display is a coupling that no test sees
 * and no compiler checks — and it was already broken: the lesson looked for a label starting
 * with `"Take the"`, the model produced `"Use Queen"`, and so the beat that teaches the second
 * way to start a turn never fired. The lesson's director goes to deliberate trouble to leave
 * an unused action card on the pile for that beat (§6g), and nothing was said when it arrived.
 *
 * With a type, the lesson asks `is Label.UseFromPile` and the compiler answers. A translation
 * cannot break it, and neither can a reworded button.
 */
sealed interface Label {

    /** Go back to whatever was being asked before. */
    data object Back : Label

    /** Finish the setup peeks and begin. */
    data object StartRound : Label

    data object DrawCard : Label

    /**
     * Take the unused action card off the discard pile and play it now.
     *
     * Carries the rank because the button names it — "Use Queen" — and because that is the
     * information the player needs to decide. It is also the one the lesson looks for.
     */
    data class UseFromPile(val rank: Rank) : Label

    /** Play the action of the card just drawn, instead of keeping it. */
    data object UseAction : Label

    /** Put the card in play into the hand, in place of one already there. */
    data object SwapCards : Label

    /** Throw the drawn card away without taking it. */
    data object Discard : Label

    /** Swap without naming what is being put down — no guess, no penalty, no action. */
    data object JustSwap : Label

    /** Stop looking at a card that was peeked. */
    data object PutItDown : Label

    /** A Jack or Queen whose owner looked and chose to change nothing. */
    data object LeaveThem : Label

    /** Done with this toss-in window. */
    data object Continue : Label

    /** End the round, and bet that this hand is the lowest. */
    data object CallVinto : Label

    /** Acknowledge something the table has finished showing. */
    data object Done : Label
}
