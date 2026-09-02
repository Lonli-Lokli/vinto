package game.vinto.client

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.SelectActionTargetPayload
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
            // The rules have a drawn card revealed publicly, and the table draws it face-up
            // under the deck for every seat (VISIBILITY.md); the log says what the felt shows.
            // It used to name the rank to the drawer alone, which read as the log missing a
            // Joker everybody had just watched come off the deck.
            val card = after.pendingAction?.card
            if (card != null) Say.DrewKnown(who, card.rank) else Say.Drew(who)
        }

        is GameAction.PlayDiscard -> Say.Took(who, before.discardPile.peekTop()?.rank)

        is GameAction.SwapCard -> Say.Swapped(
            who = who,
            slot = action.payload.position + 1,
            dropped = after.discardPile.peekTop()?.rank,
        )

        is GameAction.DiscardCard -> Say.ThrewAway(who, after.discardPile.peekTop()?.rank)

        is GameAction.UseCardAction -> Say.Played(who, before.pendingAction?.card?.rank)

        is GameAction.ParticipateInTossIn -> {
            // The rank read from the thrower's hand in the *before* state — exact for both
            // outcomes. The pile's top said the wrong thing twice over: a failed throw never
            // reaches the pile, and a successful one can slide in beneath an unresolved
            // action card, so the top was the window's card rather than the one thrown.
            val rank = action.payload.positions.firstOrNull()?.let { position ->
                before.players.firstOrNull { it.id == actor }?.cards?.getOrNull(position)?.rank
            }
            val missed = after.roundFailedAttempts.size > before.roundFailedAttempts.size
            if (missed) Say.TossInMissed(who, rank) else Say.TossedIn(who, rank)
        }

        is GameAction.CallVinto -> Say.CalledVinto(who)

        is GameAction.ExecuteJackSwap, is GameAction.ExecuteQueenSwap -> Say.SwappedTwo(
            who = who,
            // Which two cards, read from the aim the swap is resolving: whose hand and which
            // slot is public — the whole table watched them be chosen — even where a face is
            // not.
            cards = before.pendingAction?.targets.orEmpty().map { target ->
                val owner = if (target.playerId == viewerId) {
                    Speaker.You
                } else {
                    before.players.firstOrNull { it.id == target.playerId }?.nickname
                        ?.let(Speaker::Named)
                        ?: Speaker.Nobody
                }
                ChosenCard(owner, target.position + 1)
            },
        )
        is GameAction.SkipJackSwap, is GameAction.SkipQueenSwap -> Say.LeftThemAlone(who)

        is GameAction.DeclareKingAction -> Say.DeclaredRank(who, action.payload.declaredRank)

        // An Ace's aim is the one target worth a line of its own: a card lands in somebody's
        // hand and they did nothing to earn it. A peek's aim is drawn on the table instead —
        // the lift says which card — and a swap's is narrated when the swap resolves.
        is GameAction.SelectActionTarget -> aimed(action.payload, who, before, viewerId)

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

/** An Ace's aim, said; every other aim is drawn on the table rather than written down. */
private fun aimed(
    payload: SelectActionTargetPayload,
    who: Speaker,
    before: GameState,
    viewerId: String,
): Say? =
    if (payload is SelectActionTargetPayload.Ace) {
        Say.MadeDraw(who, speakerFor(payload.targetPlayerId, before, viewerId))
    } else {
        null
    }

/** Who a seat is to the reader: "you", or the nickname on the plate. */
private fun speakerFor(playerId: String, state: GameState, viewerId: String): Speaker =
    if (playerId == viewerId) {
        Speaker.You
    } else {
        state.players.firstOrNull { it.id == playerId }?.nickname?.let(Speaker::Named) ?: Speaker.Nobody
    }
