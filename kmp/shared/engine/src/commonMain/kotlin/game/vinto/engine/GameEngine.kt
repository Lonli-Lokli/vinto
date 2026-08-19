package game.vinto.engine

import game.vinto.engine.cases.handleConfirmPeek
import game.vinto.engine.cases.handleDiscardCard
import game.vinto.engine.cases.handleDrawCard
import game.vinto.engine.cases.handleEmpty
import game.vinto.engine.cases.handleFinishSetup
import game.vinto.engine.cases.handlePeekSetupCard
import game.vinto.engine.cases.handlePlayDiscard
import game.vinto.engine.cases.handleSwapCard
import game.vinto.engine.cases.handleUseCardAction
import game.vinto.engine.cases.handleProcessAiTurn
import game.vinto.engine.cases.handleSetCoalitionLeader
import game.vinto.engine.cases.handleSkipPeek
import game.vinto.engine.cases.handleUpdateDifficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState

/** The outcome of a reduction. An invalid action leaves the state untouched. */
sealed interface ReduceResult {
    val state: GameState

    data class Success(override val state: GameState) : ReduceResult
    data class Failure(override val state: GameState, val reason: String) : ReduceResult
}

/**
 * Raised by a handler that has not been ported yet.
 *
 * Deliberately not a `Failure`: a rejection and a gap in the port mean different things,
 * and collapsing them would let the parity harness report "the engine rejected this" for
 * code that simply does not exist. The harness reports it separately, which is what makes
 * the remaining work measurable.
 */
class UnportedHandlerException(val actionType: String) :
    RuntimeException("handler for $actionType is not ported yet")

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

        val changed = when (action) {
            is GameAction.DrawCard -> handleDrawCard(working, action)
            is GameAction.DiscardCard -> handleDiscardCard(working, action)
            is GameAction.ConfirmPeek -> handleConfirmPeek(working, action)
            is GameAction.SkipPeek -> handleSkipPeek(working, action)
            is GameAction.SetCoalitionLeader -> handleSetCoalitionLeader(working, action)
            is GameAction.ProcessAiTurn -> handleProcessAiTurn(working, action)
            is GameAction.PeekSetupCard -> handlePeekSetupCard(working, action)
            is GameAction.FinishSetup -> handleFinishSetup(working, action)
            is GameAction.UpdateDifficulty -> handleUpdateDifficulty(working, action)
            is GameAction.Empty -> handleEmpty(working, action)
            is GameAction.PlayDiscard -> handlePlayDiscard(working, action)
            is GameAction.UseCardAction -> handleUseCardAction(working, action)
            is GameAction.SwapCard -> handleSwapCard(working, action)

            // Still to port (phase 4). Listed one by one rather than caught by an `else`,
            // so finishing the port is a matter of emptying this list and the compiler
            // keeps score.
            is GameAction.SelectActionTarget,
            is GameAction.CallVinto,
            is GameAction.ExecuteJackSwap,
            is GameAction.SkipJackSwap,
            is GameAction.ExecuteQueenSwap,
            is GameAction.SkipQueenSwap,
            is GameAction.DeclareKingAction,
            is GameAction.ParticipateInTossIn,
            is GameAction.PlayerTossInFinished,
            is GameAction.FinishTossInPeriod,
            is GameAction.SetNextDrawCard,
            is GameAction.SwapHandWithDeck,
            -> throw UnportedHandlerException(action.type)
        }

        if (!changed) {
            return ReduceResult.Failure(
                state,
                "Action handler for ${action.type} did not modify state",
            )
        }

        // Post-action: advance the turn once the toss-in queue has drained and everyone is
        // ready. EMPTY is excluded so a no-op cannot end a turn.
        val tossIn = working.activeTossIn
        if (tossIn != null &&
            tossIn.queuedActions.isEmpty() &&
            working.pendingAction == null &&
            tossIn.playersReadyForNextTurn.size == working.players.size &&
            action !is GameAction.Empty
        ) {
            advanceTurnAfterTossIn(working)
        }

        return ReduceResult.Success(working.freeze())
    }
}
