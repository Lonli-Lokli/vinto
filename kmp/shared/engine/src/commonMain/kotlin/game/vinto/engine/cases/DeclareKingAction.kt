package game.vinto.engine.cases

import game.vinto.engine.MutableActiveTossIn
import game.vinto.engine.MutableGameState
import game.vinto.engine.MutablePendingAction
import game.vinto.engine.MutablePlayerState
import game.vinto.engine.PublicReveal
import game.vinto.engine.clearTossInReadyList
import game.vinto.engine.getAutomaticallyReadyPlayers
import game.vinto.engine.getTargetTypeFromRank
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge

/**
 * DECLARE_KING_ACTION — the King names a rank and points at a card.
 *
 * The King is discarded either way. If the declaration was right, the named card leaves the
 * hand and, if it has an action, that action is played; the toss-in then covers *both* King
 * and the declared rank. If it was wrong, the card stays in hand but is revealed to
 * everyone, the player draws a penalty, and only King is tossable.
 *
 * Ported from `packages/engine/src/lib/cases/declare-king-action.ts`.
 */
fun handleDeclareKingAction(state: MutableGameState, action: GameAction.DeclareKingAction): Boolean {
    val playerId = action.payload.playerId
    val declaredRank = action.payload.declaredRank

    val pending = state.pendingAction ?: return false
    val selectedTarget = pending.targets.firstOrNull() ?: return false

    val targetPlayer = state.playerById(selectedTarget.playerId) ?: return false
    val position = selectedTarget.position
    val selectedCard = targetPlayer.cards.getOrNull(position) ?: return false

    val isCorrect = selectedCard.rank == declaredRank

    // Mid-queue, the King must land beneath the top card so an unplayed action stays on top.
    val isTossInPhase = state.activeTossIn?.queuedActions?.isNotEmpty() == true

    val discardedKing = pending.card.copy().also { it.played = true }
    if (isTossInPhase) state.discardPile.addBeforeTop(discardedKing)
    else state.discardPile.addToTop(discardedKing)

    if (isCorrect) {
        applyCorrectDeclaration(state, targetPlayer, position, playerId, declaredRank, isTossInPhase)
    } else {
        applyIncorrectDeclaration(state, targetPlayer, position, playerId)
    }

    return true
}

private fun applyCorrectDeclaration(
    state: MutableGameState,
    targetPlayer: MutablePlayerState,
    position: Int,
    playerId: String,
    declaredRank: Rank,
    isTossInPhase: Boolean,
) {
    val removedCard = targetPlayer.cards.removeAt(position)

    targetPlayer.knownCardPositions.apply {
        val shifted = filter { it != position }.map { if (it > position) it - 1 else it }
        clear()
        addAll(shifted)
    }

    val targetType = getTargetTypeFromRank(removedCard.rank)
    if (targetType != null) {
        state.pendingAction = MutablePendingAction(
            card = removedCard,
            playerId = playerId,
            actionPhase = ActionPhase.CHOOSING_ACTION,
            from = PendingCardOrigin.HAND,
            targetType = targetType,
            targets = mutableListOf(),
        )
        setupKingTossIn(state, playerId, declaredRank, hasAction = true)
    } else {
        if (isTossInPhase) state.discardPile.addBeforeTop(removedCard)
        else state.discardPile.addToTop(removedCard)
        state.pendingAction = null
        setupKingTossIn(state, playerId, declaredRank, hasAction = false)
    }
}

private fun applyIncorrectDeclaration(
    state: MutableGameState,
    targetPlayer: MutablePlayerState,
    position: Int,
    playerId: String,
) {
    val player = state.playerById(playerId) ?: return
    val selectedCard = targetPlayer.cards.getOrNull(position) ?: return

    // Face up for the table, for this moment: naming a card wrongly shows everybody what it
    // really is, and then it goes back to being a card they are expected to remember.
    state.revealed += PublicReveal(targetPlayer.id, position, selectedCard.freeze())

    // The card stays put but everyone now knows it.
    for (observer in state.players) {
        if (observer.id == targetPlayer.id) {
            if (!observer.knownCardPositions.contains(position)) {
                observer.knownCardPositions.add(position)
            }
            continue
        }
        val knowledge = observer.opponentKnowledge ?: mutableMapOf()
        val about = knowledge[targetPlayer.id] ?: SerializedOpponentKnowledge(emptyMap())
        knowledge[targetPlayer.id] =
            about.copy(knownCards = about.knownCards + (position to selectedCard.freeze()))
        observer.opponentKnowledge = knowledge
    }

    if (state.drawPile.length > 0) {
        state.drawPile.drawTop()?.let { player.cards.add(it) }
    }

    state.pendingAction = null
    setupKingTossIn(state, playerId, declaredRank = null, hasAction = false)
}

/**
 * Opens or widens the toss-in for a King.
 *
 * A correct declaration makes both `K` and the declared rank tossable. Crucially the ranks
 * are **appended** to an existing toss-in rather than replacing it: a King played during
 * someone else's toss-in must not cancel the ranks already in play.
 *
 * @param declaredRank null when the declaration was wrong, leaving only King tossable.
 */
private fun setupKingTossIn(
    state: MutableGameState,
    playerId: String,
    declaredRank: Rank?,
    hasAction: Boolean,
) {
    val kingRanks = if (declaredRank != null) listOf(Rank.KING, declaredRank) else listOf(Rank.KING)
    val nextSubPhase =
        if (hasAction) GameSubPhase.AWAITING_ACTION else GameSubPhase.TOSS_QUEUE_ACTIVE

    val tossIn = state.activeTossIn
    if (tossIn == null) {
        state.activeTossIn = MutableActiveTossIn(
            ranks = kingRanks.toMutableList(),
            initiatorId = playerId,
            originalPlayerIndex = state.currentPlayerIndex,
            participants = mutableListOf(),
            queuedActions = mutableListOf(),
            waitingForInput = !hasAction,
            playersReadyForNextTurn = getAutomaticallyReadyPlayers(state),
        )
        state.subPhase = nextSubPhase
        return
    }

    for (rank in kingRanks) {
        if (!tossIn.ranks.contains(rank)) tossIn.ranks.add(rank)
    }

    // This King came off the queue, so take it off.
    tossIn.queuedActions.firstOrNull()?.let { first ->
        if (first.rank == Rank.KING && first.playerId == playerId) {
            tossIn.queuedActions.removeAt(0)
        }
    }

    clearTossInReadyList(state)
    state.subPhase = nextSubPhase
    tossIn.waitingForInput = !hasAction
}
