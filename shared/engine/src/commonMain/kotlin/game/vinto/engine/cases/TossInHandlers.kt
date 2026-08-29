package game.vinto.engine.cases

import game.vinto.engine.MutableCard
import game.vinto.engine.MutableGameState
import game.vinto.engine.MutablePendingAction
import game.vinto.engine.areAllPlayersReady
import game.vinto.engine.clearTossInAfterActionableCard
import game.vinto.engine.getTargetTypeFromRank
import game.vinto.engine.queuedTossInCardId
import game.vinto.engine.swapDeclarationsBetween
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue

/**
 * PLAYER_TOSS_IN_FINISHED — one player says they are done tossing in.
 *
 * Once everyone has said so, either the queued toss-in actions start resolving, or there is
 * nothing to resolve and `reduce` advances the turn. Bots are marked ready automatically, as
 * are players who have called Vinto and may no longer participate.
 *
 * Ported from `legacy-web/packages/engine/src/lib/cases/player-toss-in-finished.ts`.
 */
fun handlePlayerTossInFinished(
    state: MutableGameState,
    action: GameAction.PlayerTossInFinished,
): Boolean {
    val tossIn = state.activeTossIn ?: return false
    val playerId = action.payload.playerId

    if (!tossIn.playersReadyForNextTurn.contains(playerId)) {
        tossIn.playersReadyForNextTurn.add(playerId)
    }

    if (!areAllPlayersReady(state)) return true

    val firstAction = tossIn.queuedActions.firstOrNull()
    if (firstAction == null) {
        // Nothing queued: clearing this lets reduce's post-action step advance the turn.
        state.pendingAction = null
        return true
    }

    // Every queued toss-in action starts at target selection; the UI still offers a skip,
    // King included.
    state.pendingAction = MutablePendingAction(
        card = MutableCard(
            id = queuedTossInCardId(
                state.turnNumber,
                firstAction.playerId,
                firstAction.rank,
                tossIn.queuedActions.size,
            ),
            rank = firstAction.rank,
            value = getCardValue(firstAction.rank),
            actionText = getCardShortDescription(firstAction.rank),
            played = false,
        ),
        playerId = firstAction.playerId,
        actionPhase = ActionPhase.SELECTING_TARGET,
        from = PendingCardOrigin.HAND,
        targetType = getTargetTypeFromRank(firstAction.rank),
        targets = mutableListOf(),
    )

    val actionPlayerIndex = state.players.indexOfFirst { it.id == firstAction.playerId }
    if (actionPlayerIndex != -1) state.currentPlayerIndex = actionPlayerIndex

    // A human needs the UI to offer use-or-skip; a bot decides for itself.
    val isHumanPlayer = state.playerById(firstAction.playerId)?.isHuman ?: false
    state.subPhase = if (isHumanPlayer) GameSubPhase.AWAITING_ACTION else GameSubPhase.SELECTING
    tossIn.waitingForInput = false

    return true
}

/**
 * EXECUTE_JACK_SWAP — swap the two selected cards.
 *
 * Jack is a *blind* swap: nobody looked first, so both owners lose whatever they knew about
 * the position, and the player who played the Jack learns nothing.
 *
 * Ported from `legacy-web/packages/engine/src/lib/cases/execute-jack-swap.ts`.
 */
fun handleExecuteJackSwap(state: MutableGameState, action: GameAction.ExecuteJackSwap): Boolean {
    val pending = state.pendingAction ?: return false
    val target1 = pending.targets.getOrNull(0) ?: return false
    val target2 = pending.targets.getOrNull(1) ?: return false

    val player1 = state.playerById(target1.playerId) ?: return false
    val player2 = state.playerById(target2.playerId) ?: return false

    val card1 = player1.cards[target1.position]
    val card2 = player2.cards[target2.position]
    player1.cards[target1.position] = card2
    player2.cards[target2.position] = card1

    player1.knownCardPositions.remove(target1.position)
    player2.knownCardPositions.remove(target2.position)
    swapDeclarationsBetween(player1, target1.position, player2, target2.position)

    clearTossInAfterActionableCard(
        pending.card.copy().also { it.played = true },
        state,
        action.payload.playerId,
    )
    return true
}

/**
 * EXECUTE_QUEEN_SWAP — swap the two cards the player just peeked at.
 *
 * Unlike Jack, the acting player saw both cards, so they keep knowledge of wherever those
 * cards ended up. The other owners did not look, so they lose theirs.
 *
 * Ported from `legacy-web/packages/engine/src/lib/cases/execute-queen-swap.ts`.
 */
fun handleExecuteQueenSwap(state: MutableGameState, action: GameAction.ExecuteQueenSwap): Boolean {
    val pending = state.pendingAction ?: return false
    val target1 = pending.targets.getOrNull(0) ?: return false
    val target2 = pending.targets.getOrNull(1) ?: return false

    val player1 = state.playerById(target1.playerId) ?: return false
    val player2 = state.playerById(target2.playerId) ?: return false

    val card1 = player1.cards[target1.position]
    val card2 = player2.cards[target2.position]
    player1.cards[target1.position] = card2
    player2.cards[target2.position] = card1

    swapDeclarationsBetween(player1, target1.position, player2, target2.position)

    val currentPlayer = state.players[state.currentPlayerIndex]

    when (currentPlayer.id) {
        player1.id ->
            if (!currentPlayer.knownCardPositions.contains(target1.position)) {
                currentPlayer.knownCardPositions.add(target1.position)
            }

        player2.id ->
            if (!currentPlayer.knownCardPositions.contains(target2.position)) {
                currentPlayer.knownCardPositions.add(target2.position)
            }

        else -> {
            // Swapped two other players' cards, having seen both.
            val knowledge = currentPlayer.opponentKnowledge ?: mutableMapOf()
            knowledge.learn(player1.id, target1.position, card2)
            knowledge.learn(player2.id, target2.position, card1)
            currentPlayer.opponentKnowledge = knowledge
        }
    }

    if (currentPlayer.id != player1.id) player1.knownCardPositions.remove(target1.position)
    if (currentPlayer.id != player2.id) player2.knownCardPositions.remove(target2.position)

    clearTossInAfterActionableCard(
        pending.card.copy().also { it.played = true },
        state,
        action.payload.playerId,
    )
    return true
}

/**
 * SKIP_JACK_SWAP / SKIP_QUEEN_SWAP — decline the swap.
 *
 * Identical to each other and to the tail of the execute handlers: discard the action card
 * and move on, having changed nothing about anyone's hand.
 */
fun handleSkipJackSwap(state: MutableGameState, action: GameAction.SkipJackSwap): Boolean =
    discardPendingAndContinue(state, action.payload.playerId)

fun handleSkipQueenSwap(state: MutableGameState, action: GameAction.SkipQueenSwap): Boolean =
    discardPendingAndContinue(state, action.payload.playerId)

private fun discardPendingAndContinue(state: MutableGameState, playerId: String): Boolean {
    clearTossInAfterActionableCard(
        state.pendingAction?.card?.copy()?.also { it.played = true },
        state,
        playerId,
    )
    return true
}

private fun MutableMap<String, game.vinto.shapes.SerializedOpponentKnowledge>.learn(
    ownerId: String,
    position: Int,
    card: MutableCard,
) {
    val about = this[ownerId] ?: game.vinto.shapes.SerializedOpponentKnowledge(emptyMap())
    this[ownerId] = about.copy(knownCards = about.knownCards + (position to card.freeze()))
}
