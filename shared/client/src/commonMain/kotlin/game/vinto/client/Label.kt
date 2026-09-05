package game.vinto.client

import game.vinto.shapes.Rank
import game.vinto.shapes.TableTalk

/**
 * What a button says, as an *identity* rather than as words.
 *
 * The second slice of WORDS.md §6h, and the one that turned out to carry a bug rather than only a
 * translation problem.
 *
 * `Choice.label` was a `String`, and two things read it: the UI, to draw the button, and
 * `TeachScript`, to decide which button the lesson should point at. The second is the problem.
 * Identifying a control by the English it happens to display is a coupling that no test sees
 * and no compiler checks — and it was already broken: the lesson looked for a label starting
 * with `"Take the"`, the model produced `"Use Queen"`, and so the beat that teaches the second
 * way to start a turn never fired. The lesson's director goes to deliberate trouble to leave
 * an unused action card on the pile for that beat (UI.md §6g), and nothing was said when it arrived.
 *
 * With a type, the lesson asks `is Label.UseFromPile` and the compiler answers. A translation
 * cannot break it, and neither can a reworded button.
 */
sealed interface Label {

    /** Go back to whatever was being asked before. */
    data object Back : Label

    /** Take back what you said about a hand. Saying nothing again is itself worth saying. */
    data object Withdraw : Label

    /**
     * Do the move somebody suggested.
     *
     * One tap, and what it sends is the *viewer's own* action — a proposal is a suggestion,
     * never an instruction, and nothing in this app acts for another seat.
     */
    data object DoAsSuggested : Label

    /** Say no to a suggestion. Said rather than ignored, so the proposer knows it landed. */
    data object DeclineSuggestion : Label

    /** End this seat's share of the coalition's confer window. */
    data object DoneTalking : Label

    /**
     * Where this hand stands, as one tap.
     *
     * The round is decided by the coalition's **lowest** hand, so which hand that is has to be
     * settled before any of the rest is worth planning. Declaring cards implies it, but only
     * for a player who has looked at enough of their own hand to add it up — this says the
     * conclusion directly, which is what a person at a table would do.
     */
    data class SayStanding(val where: TableTalk.Standing.Where) : Label

    /**
     * One of the two ways a claimed pair could be round: this card is that rank, and the
     * other is the other.
     *
     * Carries both halves because the button has to *show* the arrangement — two cards and
     * two ranks read as a sentence nobody can parse, and as two little cards they read at a
     * glance.
     */
    data class ThisWayRound(
        val firstPosition: Int,
        val firstRank: Rank,
        val secondPosition: Int,
        val secondRank: Rank,
    ) : Label

    /**
     * The third answer, and the commonest one: both ranks are there and the order is gone.
     *
     * Beside the two orderings rather than under them, and never behind a mode — it is what
     * a person usually actually knows, and a picker that hid it would make them guess.
     */
    data object NotSureWhichWayRound : Label

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

    // --- the shared plan (design D7a) -------------------------------------------------------

    /** Yes to the board as it stands. */
    data object Agree : Label

    /** The viewer's own lane, on their turn, when the draw has made it legal. */
    data object DoAsPlanned : Label

    data object PlanASwap : Label

    data object PlanADeclare : Label

    data object PlanTakeTheDiscard : Label

    data object ClearLane : Label
}
