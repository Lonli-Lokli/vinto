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
 * Answers a [Say] rather than a sentence. This module has no resources and no Compose, so a
 * sentence built here is an English one whatever the phone is set to — see [Say] and §6h.
 *
 * @param before the state the action was applied to, which is where the cards still are.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
fun narrate(action: GameAction, before: GameState, after: GameState, viewerId: String): Say? {
    val actor = action.actorId ?: return null
    val who = if (actor == viewerId) {
        Speaker.You
    } else {
        before.players.firstOrNull { it.id == actor }?.nickname?.let(Speaker::Named)
    } ?: return null

    return when (action) {
        is GameAction.DrawCard -> {
            // Only the drawer sees what it was; to everyone else it is a card off the deck.
            val card = after.pendingAction?.card
            if (actor == viewerId && card != null) Say.DrewKnown(who, card.rank) else Say.Drew(who)
        }

        is GameAction.PlayDiscard -> Say.Took(who, before.discardPile.peekTop()?.rank)

        is GameAction.SwapCard -> Say.Swapped(
            who = who,
            slot = action.payload.position + 1,
            dropped = after.discardPile.peekTop()?.rank,
        )

        is GameAction.DiscardCard -> Say.ThrewAway(who, after.discardPile.peekTop()?.rank)

        is GameAction.UseCardAction -> Say.Played(who, before.pendingAction?.card?.rank)

        is GameAction.ParticipateInTossIn -> Say.TossedIn(who, after.discardPile.peekTop()?.rank)

        is GameAction.CallVinto -> Say.CalledVinto(who)

        is GameAction.ExecuteJackSwap, is GameAction.ExecuteQueenSwap -> Say.SwappedTwo(who)
        is GameAction.SkipJackSwap, is GameAction.SkipQueenSwap -> Say.LeftThemAlone(who)

        is GameAction.DeclareKingAction -> Say.DeclaredRank(who, action.payload.declaredRank)

        // Nothing. Confirming a peek says only that a player stopped looking at a card the
        // reader was never shown — it is the end of a private moment, and putting it in the
        // log spends a line saying so. The web app drops it for the same reason: "don't show
        // if no card info". What the peek *was* is already narrated by the action that caused
        // it, and drawn on the table by the lift and the glow.
        is GameAction.ConfirmPeek, is GameAction.SkipPeek -> null

        is GameAction.PeekSetupCard -> null
        is GameAction.FinishSetup -> Say.RoundBegins

        else -> null
    }
}
