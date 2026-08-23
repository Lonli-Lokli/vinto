package game.vinto.engine

import game.vinto.engine.cases.handleConfirmPeek
import game.vinto.engine.cases.handleDiscardCard
import game.vinto.engine.cases.handleDrawCard
import game.vinto.engine.cases.handleEmpty
import game.vinto.engine.cases.handleEndRound
import game.vinto.engine.cases.handleFinishSetup
import game.vinto.engine.cases.handleFinishTossInPeriod
import game.vinto.engine.cases.handlePeekSetupCard
import game.vinto.engine.cases.handlePlayDiscard
import game.vinto.engine.cases.handleSwapCard
import game.vinto.engine.cases.handleUseCardAction
import game.vinto.engine.cases.handleProcessAiTurn
import game.vinto.engine.cases.handleExecuteJackSwap
import game.vinto.engine.cases.handleExecuteQueenSwap
import game.vinto.engine.cases.handleCallVinto
import game.vinto.engine.cases.handleDeclareKingAction
import game.vinto.engine.cases.handleParticipateInTossIn
import game.vinto.engine.cases.handlePlayerTossInFinished
import game.vinto.engine.cases.handleSelectActionTarget
import game.vinto.engine.cases.handleSkipJackSwap
import game.vinto.engine.cases.handleSkipQueenSwap
import game.vinto.engine.cases.handleSetCoalitionLeader
import game.vinto.engine.cases.handleSetNextDrawCard
import game.vinto.engine.cases.handleSkipPeek
import game.vinto.engine.cases.handleSwapHandWithDeck
import game.vinto.engine.cases.handleUpdateDifficulty
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState

/** The outcome of a reduction. An invalid action leaves the state untouched. */
/**
 * A card the table was shown, and by whom it is held.
 *
 * The rules turn a card face up in two places — a King's declaration and a failed toss-in —
 * and in both the card stays in the hand it was in. It is public for that moment and private
 * again afterwards, which makes it an *event* rather than a fact about the game: this is what
 * a room would send its four clients, and what this engine hands back to the one it has.
 */
data class PublicReveal(val playerId: String, val position: Int, val card: Card)

sealed interface ReduceResult {
    val state: GameState

    data class Success(
        override val state: GameState,
        /** What this action turned face up for everybody. Usually nothing. */
        val revealed: List<PublicReveal> = emptyList(),
    ) : ReduceResult
    data class Failure(override val state: GameState, val reason: String) : ReduceResult
}

/**
 * The authoritative game logic: state + action -> new state.
 *
 * Pure and stateless. The mutation inside is confined to a working copy that never escapes
 * this call — see [MutableGameState].
 *
 * Ported from `packages/engine/src/lib/game-engine.ts`.
 */
object GameEngine {

    fun reduce(state: GameState, action: GameAction): ReduceResult {
        val validation = ActionValidator.validate(state, action)
        if (validation is Validation.Invalid) {
            return ReduceResult.Failure(state, validation.reason)
        }

        val working = state.toMutable()
        val changed = dispatch(working, action)

        if (!changed) {
            return ReduceResult.Failure(
                state,
                "Action handler for ${action.type} did not modify state",
            )
        }

        if (shouldAdvanceTurn(working, action)) {
            advanceTurnAfterTossIn(working)
        }

        return ReduceResult.Success(working.freeze(), working.revealed.toList())
    }

    /**
     * Routes an action to its handler.
     *
     * Detekt reads this as highly complex; what it is measuring is the size of the action
     * union, not the difficulty of the code. An exhaustive `when` over the sealed hierarchy
     * is what makes adding an action a compile error at this site — and it is why there is
     * no `else` branch: every action has a handler, and the compiler holds that true.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun dispatch(working: MutableGameState, action: GameAction): Boolean =
        when (action) {
            is GameAction.DrawCard -> handleDrawCard(working, action)
            is GameAction.DiscardCard -> handleDiscardCard(working, action)
            is GameAction.ConfirmPeek -> handleConfirmPeek(working, action)
            is GameAction.SkipPeek -> handleSkipPeek(working, action)
            is GameAction.EndRound -> handleEndRound(working)

            is GameAction.SetCoalitionLeader -> handleSetCoalitionLeader(working, action)
            is GameAction.ProcessAiTurn -> handleProcessAiTurn(working, action)
            is GameAction.PeekSetupCard -> handlePeekSetupCard(working, action)
            is GameAction.FinishSetup -> handleFinishSetup(working, action)
            is GameAction.UpdateDifficulty -> handleUpdateDifficulty(working, action)
            is GameAction.Empty -> handleEmpty(working, action)
            is GameAction.PlayDiscard -> handlePlayDiscard(working, action)
            is GameAction.UseCardAction -> handleUseCardAction(working, action)
            is GameAction.SwapCard -> handleSwapCard(working, action)
            is GameAction.SelectActionTarget -> handleSelectActionTarget(working, action)
            is GameAction.PlayerTossInFinished -> handlePlayerTossInFinished(working, action)
            is GameAction.ParticipateInTossIn -> handleParticipateInTossIn(working, action)
            is GameAction.DeclareKingAction -> handleDeclareKingAction(working, action)
            is GameAction.CallVinto -> handleCallVinto(working, action)
            is GameAction.FinishTossInPeriod -> handleFinishTossInPeriod(working, action)
            is GameAction.SetNextDrawCard -> handleSetNextDrawCard(working, action)
            is GameAction.SwapHandWithDeck -> handleSwapHandWithDeck(working, action)
            is GameAction.ExecuteJackSwap -> handleExecuteJackSwap(working, action)
            is GameAction.SkipJackSwap -> handleSkipJackSwap(working, action)
            is GameAction.ExecuteQueenSwap -> handleExecuteQueenSwap(working, action)
            is GameAction.SkipQueenSwap -> handleSkipQueenSwap(working, action)
        }

    /**
     * Whether the turn should advance now: the toss-in queue has drained, nothing is
     * pending, and every player has confirmed. EMPTY is excluded so a no-op cannot end a
     * turn.
     */
    private fun shouldAdvanceTurn(working: MutableGameState, action: GameAction): Boolean {
        if (action is GameAction.Empty) return false
        val tossIn = working.activeTossIn ?: return false

        return tossIn.queuedActions.isEmpty() &&
            working.pendingAction == null &&
            tossIn.playersReadyForNextTurn.size == working.players.size
    }
}
