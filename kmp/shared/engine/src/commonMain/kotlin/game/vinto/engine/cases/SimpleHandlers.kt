package game.vinto.engine.cases

import game.vinto.engine.MutableGameState
import game.vinto.engine.MutablePendingAction
import game.vinto.engine.clearTossInAfterActionableCard
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin

/**
 * The handlers small enough that a file each would be noise. Each is a direct port of the
 * matching file in `packages/engine/src/lib/cases/`.
 *
 * A handler returns `false` when it declines to act. TypeScript signals that by returning
 * the original `state` object and `reduce` comparing references — content equality would be
 * wrong there, since `handleEmpty` deliberately returns an identical-but-new copy and still
 * counts as success.
 */

/** PEEK_SETUP_CARD — during setup a player peeks at their own card and remembers it. */
fun handlePeekSetupCard(state: MutableGameState, action: GameAction.PeekSetupCard): Boolean {
    val player = state.playerById(action.payload.playerId) ?: return false
    player.cards.getOrNull(action.payload.position) ?: return false

    if (!player.knownCardPositions.contains(action.payload.position)) {
        player.knownCardPositions.add(action.payload.position)
    }
    return true
}

/**
 * FINISH_SETUP — everyone has peeked; the game proper begins.
 *
 * `action` is unused: every handler takes the same pair so the dispatch in [GameEngine] is
 * uniform, and this one needs nothing from the payload.
 */
@Suppress("UnusedParameter")
fun handleFinishSetup(state: MutableGameState, action: GameAction.FinishSetup): Boolean {
    if (state.phase == GamePhase.SETUP) {
        state.phase = GamePhase.PLAYING
        state.subPhase = GameSubPhase.IDLE
    }
    return true
}

/** DRAW_CARD — take the top card and hold it as the pending decision. */
fun handleDrawCard(state: MutableGameState, action: GameAction.DrawCard): Boolean {
    // Bots are already 'ai_thinking'; leaving that alone keeps the UI honest about why the
    // game is pausing.
    if (state.subPhase != GameSubPhase.AI_THINKING) {
        state.subPhase = GameSubPhase.DRAWING
    }

    val drawnCard = state.drawPile.drawTop() ?: return false

    state.pendingAction = MutablePendingAction(
        card = drawnCard,
        playerId = action.payload.playerId,
        actionPhase = ActionPhase.CHOOSING_ACTION,
        from = PendingCardOrigin.DRAWING,
        targets = mutableListOf(),
    )
    state.subPhase = GameSubPhase.CHOOSING
    return true
}

/** DISCARD_CARD — the pending card goes to the discard pile and opens a toss-in. */
fun handleDiscardCard(state: MutableGameState, action: GameAction.DiscardCard): Boolean {
    val discardedCard = state.pendingAction?.card ?: return false
    clearTossInAfterActionableCard(discardedCard.copy(), state, action.payload.playerId)
    return true
}

/**
 * CONFIRM_PEEK — the player has seen the card. The peek itself happened in
 * SELECT_ACTION_TARGET; this only completes the turn.
 */
fun handleConfirmPeek(state: MutableGameState, action: GameAction.ConfirmPeek): Boolean {
    val pendingCard = state.pendingAction?.card
    clearTossInAfterActionableCard(
        pendingCard?.copy()?.also { it.played = true },
        state,
        action.payload.playerId,
    )
    return true
}

/**
 * SKIP_PEEK — the player declined to look. Identical to CONFIRM_PEEK in every observable
 * way, including marking the card played; the difference lives in the UI, not the state.
 */
fun handleSkipPeek(state: MutableGameState, action: GameAction.SkipPeek): Boolean {
    val pendingCard = state.pendingAction?.card
    clearTossInAfterActionableCard(
        pendingCard?.copy()?.also { it.played = true },
        state,
        action.payload.playerId,
    )
    return true
}

/** UPDATE_DIFFICULTY — affects bot decision-making; may change at any time. */
fun handleUpdateDifficulty(state: MutableGameState, action: GameAction.UpdateDifficulty): Boolean {
    state.difficulty = action.payload.difficulty
    return true
}

/** SET_COALITION_LEADER — the seat the coalition forms around for the final round. */
fun handleSetCoalitionLeader(state: MutableGameState, action: GameAction.SetCoalitionLeader): Boolean {
    state.coalitionLeaderId = action.payload.leaderId
    return true
}

/**
 * PROCESS_AI_TURN — a marker. The bot's real moves arrive as ordinary actions through the
 * same path a human uses, which is what keeps bots rules-neutral.
 *
 * Returning a constant is the whole behaviour, not an oversight: TypeScript's handler copies
 * the state and changes nothing, and `reduce` still counts that as a success.
 */
@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
fun handleProcessAiTurn(state: MutableGameState, action: GameAction.ProcessAiTurn): Boolean = true

/** EMPTY — a no-op that still counts as a successful reduction, for the same reason. */
@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
fun handleEmpty(state: MutableGameState, action: GameAction.Empty): Boolean = true

/**
 * END_ROUND — the final round has reached a position nobody can play on, so it is scored.
 *
 * Reachable only through the validator's conditions: an empty deck, nothing takeable, and no
 * action still in flight. Everything is already where it needs to be for scoring; the phase is
 * the only thing that changes.
 */
fun handleEndRound(state: MutableGameState): Boolean {
    state.phase = GamePhase.SCORING
    state.subPhase = GameSubPhase.IDLE
    state.activeTossIn = null
    return true
}
