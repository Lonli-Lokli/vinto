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

    /** Declaring your hand to the coalition, who have no way to check. */
    data object TableTalkIsTakenOnTrust : Detail

    /** Guessing what you are putting down. */
    data object RightPlaysItWrongCostsACard : Detail

    /** A toss-in window, for somebody who may still throw. */
    data object AWrongOneCostsAPenaltyCard : Detail

    /** A toss-in window, for somebody who got this card wrong and may try the next one. */
    data object BarredFromThisCard : Detail

    /** The same, in the final round, where the bar runs to the end of it. */
    data object BarredForTheRestOfTheRound : Detail

    /**
     * What a two-card action has aimed at so far, in the order it was chosen.
     *
     * The gold ring on a chosen card is the only record there was, and on a crowded table a
     * ring is easy to lose — so the line under the prompt names the hand and the slot:
     * "Raph, card 3". Slots are one-based, because they are read by a person counting cards
     * along a row.
     */
    data class Aimed(val cards: List<ChosenCard>) : Detail

    /** The round was scored against the caller's hand. */
    data class ScoredAgainstTheCaller(val caller: Speaker) : Detail

    /** Nobody called; the deck simply ran out. */
    data object TheDeckRanOut : Detail
}

/** One card an action is aimed at: whose hand, and which slot along it (one-based). */
data class ChosenCard(val who: Speaker, val slot: Int)
