package game.vinto.client

import game.vinto.shapes.Rank

/**
 * The smaller line under the prompt: the rule that applies, or what the card in play does.
 *
 * The fifth slice of WORDS.md §6h, and the one that had to wait for a boundary to be established
 * (`CardCopyIsDataTest`): most of what fills a detail comes from `CardConfig`, and one of that
 * type's fields is **hashed state** rather than copy.
 *
 * So [WhatTheCardDoes] and [KingDeclared] carry a [Rank] and let the renderer reach for
 * `longDescription`, which is presentation. Neither goes near `shortDescription`, which is
 * what `Card.actionText` is made of and therefore cannot be translated without diverging every
 * recording. The King's line used to be built from exactly that field.
 */
sealed interface Detail {

    /** What the card in play does, in its own words. */
    data class WhatTheCardDoes(val rank: Rank) : Detail

    /**
     * A King borrowing another rank's action.
     *
     * Without naming it, "choose two cards from two different players" arrives with no
     * explanation — the Queen it is imitating was never on the table.
     */
    data class KingDeclared(val rank: Rank) : Detail

    /** Setting up a King's declaration. */
    data object TapACardToSayWhatItIs : Detail

    /**
     * An Ace in the final round, where the caller is out of bounds and every legal target is
     * therefore a teammate — including, possibly, the one hand still able to win the round.
     */
    data object AnAceOnlyHurtsYourOwnSide : Detail

    /**
     * How the rank rail is read, and what a claim is worth.
     *
     * Both halves in one line because the rail cannot say either on its own. A rank is a
     * **toggle** — name one and the card is that card, name several and it is one of them —
     * and nothing about fourteen plaques in a grid says so. And nothing checks a claim when it
     * is made: the coalition takes the speaker's word, and the reveal at scoring is the only
     * referee there is.
     */
    data object NameEveryRankItCouldBe : Detail

    /** Guessing what you are putting down. */
    data object RightPlaysItWrongCostsACard : Detail

    /** A toss-in window, for somebody who may still throw. */
    data object AWrongOneCostsAPenaltyCard : Detail

    /** A toss-in window, for somebody who got this card wrong and may try the next one. */
    data object BarredFromThisCard : Detail

    /** The same, in the final round, where the bar runs to the end of it. */
    data object BarredForTheRestOfTheRound : Detail

    /** The round was scored against the caller's hand. */
    data class ScoredAgainstTheCaller(val caller: Speaker) : Detail

    /** Nobody called; the deck simply ran out. */
    data object TheDeckRanOut : Detail

    /** Boxed means touchable: the one line that says how the sentence is changed. */
    data object TouchAWord : Detail

    /** A teammate changed the turn on screen since the viewer last looked. */
    data class ChangedBy(val who: Speaker) : Detail

    /** A throw-in is picked on the felt: gold for a match, dim for a card nobody has named. */
    data object TouchACardToThrow : Detail

    /** The caller opened the plan: every word is plain, and nothing answers a touch. */
    data object TheCallerReads : Detail

    /** Propose, never command: the person on play decides. */
    data object APlanIsASuggestion : Detail

    /**
     * How a card is named on the felt, both ways in one sentence: carry it, or touch it and
     * then the place. One edit, two paths (design D5), and neither needs a pointer or a hold.
     */
    data object CarryACardOrTouchIt : Detail

    /** A step on the board was built on a claim the reveal has proved wrong (design D9). */
    data object AClaimWasWrong : Detail

    /** A card is named on the felt with one touch: the one a 9 looks at, the one a King points at. */
    data object TouchTheCard : Detail
}

/**
 * One card an action moved: whose hand, and which slot along it (one-based).
 *
 * The log's half of the story. What a two-card action is *currently* aimed at is [Aim], which
 * carries the face as well, because the rail draws it rather than reading it out.
 */
data class ChosenCard(val who: Speaker, val slot: Int)
