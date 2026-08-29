package game.vinto.bot

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.PendingCardOrigin

/**
 * What the table just watched happen, translated for [OpponentModeler].
 *
 * The engine already publishes the *hard* facts — a swap-in is seen by everyone and lands in
 * `opponentKnowledge`, a failed declaration reveals the card — so those never come through
 * here. This mapper carries the *soft* evidence a good player reads anyway: throwing a drawn
 * card away says the hand behind it is better, playing a swap action says its owner is
 * tidying up to call, and any card that moves or leaves makes the beliefs pinned to its
 * position wrong.
 *
 * Pure on purpose: action plus the states around it in, events out. The interpretation into
 * modeler mutations happens in [BotRunner.observe], so this table of inferences is testable
 * without a modeler in hand.
 */
sealed interface TableObservation {

    /** Behaviour worth an inference; forwarded to [OpponentModeler.handleObservedAction]. */
    data class Acted(val observed: ObservedAction) : TableObservation

    /** The card at this position changed, so whatever was believed about it is now noise. */
    data class BeliefInvalidated(val playerId: String, val position: Int) : TableObservation

    /**
     * A card left this hand and everything after it renumbered. Emitted in **descending**
     * position order, so applying [OpponentModeler.shiftCardBeliefs] one at a time composes
     * correctly — ascending order would shift a belief once for a removal *behind* it and
     * then shift it again for its own.
     */
    data class CardRemoved(val playerId: String, val position: Int) : TableObservation
}

/** The events one accepted action produces; empty for actions the table learns nothing from. */
fun observationsFor(
    action: GameAction,
    before: GameState,
    after: GameState,
): List<TableObservation> = when (action) {
    is GameAction.DiscardCard -> {
        val pending = before.pendingAction
        if (pending != null && pending.playerId == action.payload.playerId &&
            pending.from == PendingCardOrigin.DRAWING
        ) {
            listOf(TableObservation.Acted(ObservedAction.DiscardDrawn(pending.playerId, pending.card)))
        } else {
            emptyList()
        }
    }

    is GameAction.SwapCard -> listOf(
        TableObservation.BeliefInvalidated(action.payload.playerId, action.payload.position),
        TableObservation.Acted(ObservedAction.SwapOwn(action.payload.playerId)),
    )

    is GameAction.UseCardAction ->
        before.pendingAction
            ?.takeIf { it.playerId == action.payload.playerId }
            ?.let { listOf(TableObservation.Acted(ObservedAction.UseAction(it.playerId, it.card))) }
            .orEmpty()

    is GameAction.DeclareKingAction ->
        before.pendingAction
            ?.takeIf { it.playerId == action.payload.playerId }
            ?.let { listOf(TableObservation.Acted(ObservedAction.UseAction(it.playerId, it.card))) }
            .orEmpty()

    // A Jack or Queen swap moves two cards the beliefs may have been about. The modeler has
    // no way to carry a belief across a swap, so both positions honestly reset to unknown.
    is GameAction.ExecuteJackSwap, is GameAction.ExecuteQueenSwap ->
        before.pendingAction
            ?.targets.orEmpty()
            .map { TableObservation.BeliefInvalidated(it.playerId, it.position) }

    is GameAction.ParticipateInTossIn -> {
        val handBefore = before.players.firstOrNull { it.id == action.payload.playerId }?.cards?.size
        val handAfter = after.players.firstOrNull { it.id == action.payload.playerId }?.cards?.size
        // A wrong toss-in removes nothing — the card comes back and a penalty card arrives —
        // so only a hand that actually shrank renumbers anything.
        if (handBefore != null && handAfter != null && handAfter < handBefore) {
            action.payload.positions.sortedDescending()
                .map { TableObservation.CardRemoved(action.payload.playerId, it) }
        } else {
            emptyList()
        }
    }

    else -> emptyList()
}
