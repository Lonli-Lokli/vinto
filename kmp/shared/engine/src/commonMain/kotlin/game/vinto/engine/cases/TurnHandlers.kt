package game.vinto.engine.cases

import game.vinto.engine.MutableActiveTossIn
import game.vinto.engine.MutableGameState
import game.vinto.engine.MutablePendingAction
import game.vinto.engine.addTossInCard
import game.vinto.engine.clearDeclarationAt
import game.vinto.engine.getAutomaticallyReadyPlayers
import game.vinto.engine.getTargetTypeFromRank
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.SerializedOpponentKnowledge

/**
 * PLAY_DISCARD — take the top discard and use its action immediately.
 *
 * Unlike drawing, there is no option to keep it: the rules require the action to be played,
 * so this goes straight to target selection.
 *
 * Ported from `packages/engine/src/lib/cases/play-discard.ts`.
 */
fun handlePlayDiscard(state: MutableGameState, action: GameAction.PlayDiscard): Boolean {
    if (state.subPhase != GameSubPhase.AI_THINKING) {
        state.subPhase = GameSubPhase.DRAWING
    }

    val takenCard = state.discardPile.drawTop() ?: return false

    state.pendingAction = MutablePendingAction(
        card = takenCard,
        playerId = action.payload.playerId,
        actionPhase = ActionPhase.SELECTING_TARGET,
        from = PendingCardOrigin.HAND,
        targetType = getTargetTypeFromRank(takenCard.rank),
        targets = mutableListOf(),
    )
    state.subPhase = GameSubPhase.AWAITING_ACTION
    return true
}

/**
 * USE_CARD_ACTION — the player commits to playing the pending card's action.
 *
 * Every rank goes through the same `selecting-target` phase; multi-step actions (J, Q, K)
 * track their progress by how many targets have been collected.
 *
 * Ported from `packages/engine/src/lib/cases/use-card.ts`.
 */
fun handleUseCardAction(state: MutableGameState, action: GameAction.UseCardAction): Boolean {
    val pending = state.pendingAction ?: return true

    pending.actionPhase = ActionPhase.SELECTING_TARGET
    pending.targetType = getTargetTypeFromRank(pending.card.rank)

    val tossIn = state.activeTossIn ?: MutableActiveTossIn(
        ranks = mutableListOf(pending.card.rank),
        initiatorId = action.payload.playerId,
        originalPlayerIndex = state.currentPlayerIndex,
        participants = mutableListOf(),
        queuedActions = mutableListOf(),
        waitingForInput = true,
        playersReadyForNextTurn = getAutomaticallyReadyPlayers(state),
    ).also { state.activeTossIn = it }

    tossIn.ranks =
        if (pending.from == PendingCardOrigin.DRAWING) mutableListOf(pending.card.rank)
        else addTossInCard(tossIn.ranks, pending.card.rank)

    state.subPhase = GameSubPhase.AWAITING_ACTION
    return true
}

/**
 * SWAP_CARD — put the pending card into the hand and discard what it displaced.
 *
 * The optional rank declaration is the interesting part. Declaring correctly earns the
 * swapped-out card's action; declaring wrongly costs a penalty card and reveals the card to
 * everyone. Declining to declare simply discards it.
 *
 * Ported from `packages/engine/src/lib/cases/swap-card.ts`.
 */
fun handleSwapCard(state: MutableGameState, action: GameAction.SwapCard): Boolean {
    val (playerId, position, declaredRank) = Triple(
        action.payload.playerId,
        action.payload.position,
        action.payload.declaredRank,
    )

    val pendingCard = state.pendingAction?.card ?: return false
    val player = state.playerById(playerId) ?: return false

    val cardFromHand = player.cards[position]
    player.cards[position] = pendingCard
    player.clearDeclarationAt(position)

    if (!player.knownCardPositions.contains(position)) {
        player.knownCardPositions.add(position)
    }

    // Everyone watches the swap happen, so everyone learns what now sits at that position.
    learnCardAt(state, playerId, position, pendingCard.freeze())

    state.activeTossIn = MutableActiveTossIn(
        ranks = mutableListOf(cardFromHand.rank),
        initiatorId = playerId,
        originalPlayerIndex = state.currentPlayerIndex,
        participants = mutableListOf(),
        queuedActions = mutableListOf(),
        waitingForInput = true,
        playersReadyForNextTurn = getAutomaticallyReadyPlayers(state),
    )

    if (declaredRank != null) {
        val declarationCorrect = cardFromHand.rank == declaredRank

        if (!declarationCorrect && state.drawPile.length > 0) {
            state.drawPile.drawTop()?.let { player.cards.add(it) }

            // A failed declaration reveals the card to the table.
            if (!player.knownCardPositions.contains(position)) {
                player.knownCardPositions.add(position)
            }
            learnCardAt(state, playerId, position, player.cards[position].freeze())
        }

        if (declarationCorrect) {
            state.pendingAction = MutablePendingAction(
                card = cardFromHand,
                playerId = playerId,
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.HAND,
                targetType = getTargetTypeFromRank(cardFromHand.rank),
                targets = mutableListOf(),
                // Kept so the animation knows which seat position the card came from.
                swapPosition = position,
            )
            state.subPhase = GameSubPhase.AWAITING_ACTION
            return true
        }
    }

    state.discardPile.addToTop(cardFromHand)
    state.pendingAction = null
    state.subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE
    return true
}

/** Records, for every player but [ownerId], that [card] is at [position] of [ownerId]'s hand. */
private fun learnCardAt(
    state: MutableGameState,
    ownerId: String,
    position: Int,
    card: Card,
) {
    for (observer in state.players) {
        if (observer.id == ownerId) continue

        val knowledge = observer.opponentKnowledge ?: mutableMapOf()
        val aboutOwner = knowledge[ownerId] ?: SerializedOpponentKnowledge(emptyMap())
        knowledge[ownerId] = aboutOwner.copy(knownCards = aboutOwner.knownCards + (position to card))
        observer.opponentKnowledge = knowledge
    }
}
