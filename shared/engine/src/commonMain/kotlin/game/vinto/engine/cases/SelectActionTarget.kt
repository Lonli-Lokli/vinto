package game.vinto.engine.cases

import game.vinto.engine.MutableGameState
import game.vinto.engine.MutablePlayerState
import game.vinto.engine.clearTossInAfterActionableCard
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.GameAction
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SerializedOpponentKnowledge

/**
 * SELECT_ACTION_TARGET — the player names the card or player their action applies to.
 *
 * Each rank consumes a different number of targets, and progress through a multi-step action
 * is tracked by how many have been collected rather than by a separate phase field:
 *
 *  - 7/8 peek one of your own cards, 9/10 peek an opponent's — one target, then CONFIRM_PEEK
 *  - J and Q take two, which must belong to two *different* players
 *  - K takes one, then DECLARE_KING_ACTION names the rank
 *  - A takes one player and resolves immediately
 *
 * Ported from `legacy-web/packages/engine/src/lib/cases/select-action-target.ts`. King's declaration
 * step has its own handler.
 */
fun handleSelectActionTarget(state: MutableGameState, action: GameAction.SelectActionTarget): Boolean {
    val payload = action.payload
    val targetPlayer = state.playerById(payload.targetPlayerId) ?: return false

    // Ace names a player rather than a card, so its "position" is where the penalty card is
    // about to land — the end of the target's hand.
    val position = when (payload) {
        is SelectActionTargetPayload.Ace -> targetPlayer.cards.size
        is SelectActionTargetPayload.Positional -> payload.position
    }

    val pending = state.pendingAction
    pending?.targets?.add(ActionTarget(playerId = payload.targetPlayerId, position = position))

    val targets = pending?.targets ?: mutableListOf()

    when (pending?.card?.rank) {
        Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN -> recordPeek(state, targets.firstOrNull())

        Rank.JACK, Rank.QUEEN -> {
            // Both need two cards from two different players; the only difference is that
            // Queen reveals what it selects and Jack does not, which is a UI concern.
            if (targets.size == 2 && targets[0].playerId == targets[1].playerId) {
                targets.removeAt(targets.lastIndex)
                return true
            }
            rememberChosenCard(targets, targetPlayer, position)
        }

        Rank.KING -> if (targets.size == 1) rememberChosenCard(targets, targetPlayer, position)

        Rank.ACE -> {
            if (targets.size != 1) return true

            if (state.drawPile.length > 0) {
                state.drawPile.drawTop()?.let { targetPlayer.cards.add(it) }
            }
            clearTossInAfterActionableCard(
                pending.card.copy().also { it.played = true },
                state,
                payload.playerId,
            )
        }

        // Joker is wild and resolves elsewhere; 2-6 carry no action and cannot reach here.
        else -> Unit
    }

    return true
}

/**
 * A peek teaches the peeking player something. Their own card becomes a known position;
 * an opponent's card is filed under what they know about that opponent.
 */
private fun recordPeek(state: MutableGameState, target: ActionTarget?) {
    if (target == null) return
    val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return

    if (target.playerId == currentPlayer.id) {
        if (!currentPlayer.knownCardPositions.contains(target.position)) {
            currentPlayer.knownCardPositions.add(target.position)
        }
        return
    }

    val peekedCard = state.playerById(target.playerId)?.cards?.getOrNull(target.position) ?: return

    val knowledge = currentPlayer.opponentKnowledge ?: mutableMapOf()
    val aboutTarget = knowledge[target.playerId] ?: SerializedOpponentKnowledge(emptyMap())
    knowledge[target.playerId] = aboutTarget.copy(
        knownCards = aboutTarget.knownCards + (target.position to peekedCard.freeze()),
    )
    currentPlayer.opponentKnowledge = knowledge
}

/**
 * Stores the chosen card on the target itself, so an online client can be shown exactly what
 * was selected without being handed the whole hand.
 */
private fun rememberChosenCard(
    targets: MutableList<ActionTarget>,
    targetPlayer: MutablePlayerState,
    position: Int,
) {
    val index = targets.lastIndex
    if (index < 0) return
    val chosenCard = targetPlayer.cards.getOrNull(position) ?: return
    targets[index] = targets[index].copy(card = chosenCard.freeze())
}
