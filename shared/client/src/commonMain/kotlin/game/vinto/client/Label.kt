package game.vinto.client

import game.vinto.shapes.Rank

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

    /**
     * End this seat's share of the coalition's confer window, when other people share it.
     *
     * It said "Done talking", which named the half of it that costs nothing — talk is free and
     * can be resumed on any later turn — and hid the half that does not. Now it names exactly
     * what it does and no more: **readiness**, which is all a press can declare at a table
     * where somebody else has still to press. Where nobody else has, the same move wears
     * [StartTheTurns] instead, because there it really does start them.
     */
    data object Ready : Label

    /**
     * Start the coalition's turns: the one press the final round is actually waiting on.
     *
     * Worn where this seat is the last the window is holding for — a solo table, or an online
     * one whose other coalition seats are bots — because there the press is not a declaration of
     * anything, it is the round beginning. "Play" was the obvious word and is taken: it is what
     * a card does on a turn, and a button that says Play beside a plan of turns reads as playing
     * one of them.
     *
     * Offered only while there are turns left to start. A round already running has nothing to
     * begin, and a button that begins it would be a lie with nothing behind it.
     */
    data object StartTheTurns : Label

    /**
     * Finish saying what you know, and go to the plan.
     *
     * The window's first step, and the reason there are two. One button used to end the talking
     * *and* set three turns going, so a player who had only meant "that is everything I can
     * remember" watched the round run — and was then put back on the plan board, which draws the
     * plan's table rather than the live one. A real move, then a card in the plan's rose,
     * reported from a phone. This press goes to the plan and releases nothing.
     *
     * Always offered, with or without a claim: ten turns after setup a person may genuinely
     * remember none of their cards, and saying so is an answer rather than a failure to give one.
     */
    data object ThatsAllIKnow : Label

    /**
     * Send the claim the rail has been building.
     *
     * A confirm rather than a send-on-tap, because a rank is now a **toggle**: one names the
     * card exactly, several say it is one of them, and neither can be told from the other
     * until the player says they are finished. Sending on the first rank made "a 7 or an 8"
     * unsayable, which is most of what anybody actually remembers.
     */
    data object SayIt : Label

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

    // What a turn can do with the card it takes — `Question.Doing` — is asked with the rail's
    // own [UseAction], [SwapCards] and [Discard]. It had three labels of its own here, and one
    // move with two names is one move a player has to learn twice.

    /** The rank somebody said the pointed-at card is, offered as the King's answer. */
    data class RankSaidBy(val rank: Rank, val who: Speaker) : Label

    /** A rank nobody said: the whole set, one touch further. */
    data object AnotherRank : Label

    /** Take one throw-in off the turn it was said on. */
    data object RemoveThrow : Label
}
