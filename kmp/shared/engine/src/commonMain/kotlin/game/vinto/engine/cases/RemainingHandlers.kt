package game.vinto.engine.cases

import game.vinto.engine.MutableGameState
import game.vinto.engine.getAutomaticallyReadyPlayers
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase

/**
 * FINISH_TOSS_IN_PERIOD — the toss-in window closes.
 *
 * Participation data is cleared but the ranks are deliberately kept: they describe the cards
 * currently on the discard pile. A King that correctly declared an Ace leaves `[K, A]`, and
 * both must survive into the next turn.
 *
 * Ported from `packages/engine/src/lib/cases/finish-toss-in.ts`.
 */
@Suppress("UnusedParameter")
fun handleFinishTossInPeriod(
    state: MutableGameState,
    action: GameAction.FinishTossInPeriod,
): Boolean {
    state.activeTossIn?.let { tossIn ->
        tossIn.participants = mutableListOf()
        tossIn.queuedActions = mutableListOf()
        tossIn.waitingForInput = false
        tossIn.playersReadyForNextTurn = getAutomaticallyReadyPlayers(state)
        tossIn.failedAttempts = mutableListOf()
    }
    state.subPhase = GameSubPhase.IDLE
    return true
}

/**
 * SET_NEXT_DRAW_CARD — debug only. Moves a card of the given rank to the top of the draw
 * pile so a specific draw can be tested.
 *
 * Ported from `packages/engine/src/lib/cases/set-next-draw-card.ts`.
 */
fun handleSetNextDrawCard(state: MutableGameState, action: GameAction.SetNextDrawCard): Boolean {
    val rank = action.payload.rank
    val cardIndex = state.drawPile.cards.indexOfFirst { it.rank == rank }
    if (cardIndex == -1) return false

    val card = state.drawPile.takeAt(cardIndex) ?: return false
    state.drawPile.addToTop(card)
    return true
}

/**
 * SWAP_HAND_WITH_DECK — debug only. Exchanges a card in a hand for one of a given rank from
 * the draw pile, so a specific hand can be set up.
 *
 * Ported from `packages/engine/src/lib/cases/swap-hand-with-deck.ts`.
 */
fun handleSwapHandWithDeck(state: MutableGameState, action: GameAction.SwapHandWithDeck): Boolean {
    val payload = action.payload
    val player = state.playerById(payload.playerId) ?: return false
    if (payload.handPosition !in player.cards.indices) return false

    val deckCardIndex = state.drawPile.cards.indexOfFirst { it.rank == payload.deckCardRank }
    if (deckCardIndex == -1) return false

    val handCard = player.cards[payload.handPosition]
    val deckCard = state.drawPile.takeAt(deckCardIndex) ?: return false

    player.cards[payload.handPosition] = deckCard
    state.drawPile.addToTop(handCard)
    return true
}
