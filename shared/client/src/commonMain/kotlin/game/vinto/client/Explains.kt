package game.vinto.client

import game.vinto.shapes.Rank

/**
 * What the "?" explains, for whatever is happening.
 *
 * The sixth slice of §6h, and the last of `TableModel`. The card in play when there is one,
 * since that is nearly always what the player is unsure about; otherwise the rule that governs
 * the phase.
 *
 * [TheCardInPlay] carries only the rank. Everything the paragraph needs — the card's name, its
 * value, what it does and how to do it — is in `CARD_CONFIGS`, and assembling it here would
 * mean this module choosing the order of a sentence again. Note the renderer must use
 * `longDescription` and `helpText`, never `shortDescription`: see `CardCopyIsDataTest`.
 */
sealed interface Explains {

    /** The card the player is holding a decision about. */
    data class TheCardInPlay(val rank: Rank) : Explains

    /** Before the round: the two peeks, and that everything after is memory. */
    data object HowSetupWorks : Explains

    /** After it: how the hands are compared. */
    data object HowScoringWorks : Explains

    /** A window is open: anybody may throw a match, and a wrong one costs. */
    data object HowTossingInWorks : Explains

    /** The final round: one turn each, coalition, best hand against the caller's. */
    data object HowTheFinalRoundWorks : Explains

    /** The ordinary turn, for a player who opened the "?" with nothing else happening. */
    data object HowATurnWorks : Explains
}
