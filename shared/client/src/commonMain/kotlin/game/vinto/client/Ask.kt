package game.vinto.client

import game.vinto.shapes.Rank

/**
 * What the table is asking of the player, as a message rather than as a sentence.
 *
 * The third slice of WORDS.md §6h, and the counterpart to [Label]: that names the buttons, this names
 * the line above them. Same reasoning — `shared/client` has no resources, so a sentence built
 * here is English whatever the phone is set to.
 *
 * It also removes a third piece of English assembly. The toss-in prompt joined its ranks with
 * `" or "`, which is a *word* — one this module was in no position to translate. [TossIn]
 * carries the ranks and lets the renderer join them.
 */
sealed interface Ask {

    // --- setting up ------------------------------------------------------------------------

    /** Two peeks to spend, and none spent. */
    data object LookAtTwoOfYours : Ask

    /** One spent. */
    data object OneMoreToLookAt : Ask

    data object ReadyWhenYouAre : Ask

    // --- a turn ----------------------------------------------------------------------------

    data object YourTurn : Ask

    /** [rank] is null when the card is not shown, which is every seat but the drawer's. */
    data class YouDrew(val rank: Rank?) : Ask

    data object WhichCardDoesItReplace : Ask

    /** Naming what is being put down: right plays its action, wrong costs a card. */
    data object NameWhatYouArePuttingDown : Ask

    data object WhatDoYouSayThisCardIs : Ask

    /** A King, borrowing another rank's action. */
    data object SayWhatItIsAndPlayIt : Ask

    // --- an action, resolving ---------------------------------------------------------------

    data object LookAtOneOfYourOwn : Ask
    data object LookAtOneOfAnotherPlayers : Ask
    data object ChooseAnyCard : Ask
    data object ChooseTwoFromDifferentPlayers : Ask
    data object LookAtTwoFromDifferentPlayers : Ask
    data object SwapThem : Ask
    data object RememberIt : Ask
    data object WhoDrawsACard : Ask

    /** A card is face up and waiting to be dealt with. */
    data class TheCardIsWaiting(val rank: Rank?) : Ask

    // --- a toss-in window --------------------------------------------------------------------

    /**
     * A rank went down and anybody holding one may throw it in.
     *
     * [ranks] rather than a joined string: the separator between them is a word, and joining
     * them here would have been a fourth thing this module was translating badly. [barred] is
     * the player who threw a wrong card earlier in the round — they are told what happened and
     * not offered the choice.
     */
    data class TossIn(val ranks: List<Rank>, val barred: Boolean) : Ask

    data object WaitingForTheOthers : Ask

    // --- somebody else's turn -----------------------------------------------------------------

    /** Not seated at this table at all — a spectator, or a view with no viewer in it. */
    data object Watching : Ask

    data class SomebodyIsPlaying(val who: Speaker) : Ask

    /** The final round: the coalition picks whose hand is compared to the caller's. */
    data class WhoPlaysForYou(val caller: Speaker) : Ask

    // --- the end ------------------------------------------------------------------------------

    /**
     * The round is scored.
     *
     * Both totals travel as numbers so the sentence can be built in any order. Null when the
     * view cannot see them, which is a table that ended without a score.
     */
    data class RoundOver(val yours: Int?, val best: Int?) : Ask
}
