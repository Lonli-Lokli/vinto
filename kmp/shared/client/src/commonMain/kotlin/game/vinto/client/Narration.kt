package game.vinto.client

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.actorId

/**
 * What just happened, in one short phrase.
 *
 * A card table is mostly other people doing things quickly, and three bots take their turns in
 * under a second each. Without a record of it the player sees the discard pile change and has
 * to work backwards; the web app keeps a "Recent Actions" strip for exactly this, and it is
 * the difference between watching a game and following one.
 *
 * Returns null for the moves that are pure bookkeeping — agreeing that a toss-in window is
 * over, nominating a coalition leader. Narrating those would bury the ones that matter.
 *
 * @param before the state the action was applied to, which is where the cards still are.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
fun narrate(action: GameAction, before: GameState, after: GameState, viewerId: String): String? {
    val actor = action.actorId ?: return null
    val who = if (actor == viewerId) "You" else before.players.firstOrNull { it.id == actor }?.nickname
    if (who == null) return null

    val verb = if (actor == viewerId) ::youForm else ::theyForm

    return when (action) {
        is GameAction.DrawCard -> {
            // Only the drawer sees what it was; to everyone else it is a card off the deck.
            val card = after.pendingAction?.card
            if (actor == viewerId && card != null) {
                "$who drew the ${card.rank.serialName}"
            } else {
                "$who ${verb("draw")} a card"
            }
        }

        is GameAction.PlayDiscard ->
            "$who took the ${before.discardPile.peekTop()?.rank?.serialName ?: "top card"}"

        is GameAction.SwapCard -> {
            val out = after.discardPile.peekTop()?.rank?.serialName
            val slot = action.payload.position + 1
            if (out == null) {
                "$who ${verb("swap")} card $slot"
            } else {
                "$who ${verb("swap")} card $slot, dropping the $out"
            }
        }

        is GameAction.DiscardCard ->
            "$who ${verb("throw")} away the ${after.discardPile.peekTop()?.rank?.serialName ?: "card"}"

        is GameAction.UseCardAction ->
            "$who ${verb("play")} the ${before.pendingAction?.card?.rank?.serialName ?: "card"}"

        // The rank, not a count. "Mikey tossed in 1 card" tells you somebody acted and nothing
        // about the game; "Mikey tossed in a 6" is a card leaving a hand you are trying to
        // remember. The web app says the rank too.
        is GameAction.ParticipateInTossIn -> {
            val rank = after.discardPile.peekTop()?.rank?.serialName
            if (rank != null) "$who tossed in the $rank" else "$who tossed in a card"
        }

        is GameAction.CallVinto -> "$who called Vinto"

        is GameAction.ExecuteJackSwap, is GameAction.ExecuteQueenSwap -> "$who swapped two cards"
        is GameAction.SkipJackSwap, is GameAction.SkipQueenSwap -> "$who left them alone"

        is GameAction.DeclareKingAction ->
            "$who declared a ${action.payload.declaredRank.serialName}"

        // Nothing. Confirming a peek says only that a player stopped looking at a card the
        // reader was never shown — it is the end of a private moment, and putting it in the
        // log spends a line saying so. The web app drops it for the same reason: "don't show
        // if no card info". What the peek *was* is already narrated by the action that caused
        // it, and drawn on the table by the lift and the glow.
        is GameAction.ConfirmPeek, is GameAction.SkipPeek -> null

        is GameAction.PeekSetupCard -> null
        is GameAction.FinishSetup -> "the round begins"

        else -> null
    }
}

/** "You draw" and "Raph draws" — the same verb, and English wants them spelled differently. */
private fun youForm(verb: String) = verb

private fun theyForm(verb: String) = when (verb) {
    "throw" -> "throws"
    else -> verb + "s"
}
