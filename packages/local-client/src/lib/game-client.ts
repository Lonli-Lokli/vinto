// client/GameClient.ts
// Observable wrapper around GameEngine for UI integration

import { makeObservable, observable, action, computed } from 'mobx';
import { deepEqual } from 'fast-equals';

import copy from 'fast-copy';
import {
  type GameState,
  type GameAction,
  type GameActionHistory,
  type PlayerState,
  type Card,
  type Rank,
  logger,
} from '@vinto/shapes';
import { GameEngine } from '@vinto/engine';

/**
 * GameClient - Observable wrapper around GameEngine
 *
 * This class bridges the pure GameEngine with the UI layer by:
 * - Holding the current GameState as an observable
 * - Providing a dispatch method for actions
 * - Exposing computed properties for common UI needs
 * - Handling side effects (animations, sounds, etc.)
 *
 * Key principles:
 * - GameEngine remains pure (no side effects)
 * - GameClient handles all side effects
 * - State is observable for React components
 * - All mutations go through dispatch()
 */
export class GameClient {
  /**
   * Internal game state (observable)
   * This is the LOGICAL state - always current and accurate
   * Used by bot AI, validation, and game logic
   */
  @observable
  private _state: GameState;

  /**
   * Visual state (observable)
   * This is what the UI displays - lags behind _state during animations
   * Updated by animations to smoothly transition from old to new state
   */
  @observable
  private _visualState: GameState;

  /**
   * Full action history for debugging/bug reports
   * Stores all dispatched actions with timestamps
   */
  private _actionHistory: Array<{ action: GameAction; timestamp: number }> = [];

  /**
   * Public readonly accessor for logical game state
   * Used by bot AI and game logic
   */
  get state(): Readonly<GameState> {
    return this._state;
  }

  /**
   * Public readonly accessor for visual state
   * UI components should read from this instead of state
   * This ensures UI updates only after animations complete
   */
  get visualState(): Readonly<GameState> {
    return this._visualState;
  }

  /**
   * Animation/effect callbacks (optional)
   * Triggered after state updates
   */
  private onStateChange?: (
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ) => void;

  /**  * Error callback (optional)
   */
  private onStateError?: (reason: string) => void;

  constructor(initialState: GameState) {
    this._state = initialState;
    this._visualState = copy(initialState); // Visual state starts synchronized
    makeObservable(this);
  }

  /**
   * Dispatch an action to the engine
   * This is the ONLY way to modify game state
   *
   * Flow:
   * 1. Pass action to GameEngine.reduce()
   * 2. Get new state back (immutable)
   * 3. Update observable state
   * 4. Trigger side effects (animations, etc.)
   * 5. Handle automatic turn advancement after toss-in
   */
  @action
  dispatch(action: GameAction): void {
    const oldState = this._state;
    const result = GameEngine.reduce(this._state, action);

    // Track action in full history for debugging
    this._actionHistory.push({ action, timestamp: Date.now() });

    if (result.success) {
      // Add action to history (for UI display)
      const newState = this.addActionToHistory(this._state, result.state, action);

      // Update observable state
      this._state = newState;

      // Trigger side effects
      if (this.onStateChange) {
        this.onStateChange(oldState, newState, action);
      }
    } else {
      logger.warn(`Invalid action ${action.type}: ${result.reason}`, {
        actionType: action.type,
        reason: result.reason,
        currentPhase: result.state.phase,
        currentSubPhase: result.state.subPhase,
        action: action,
        actionHistory: this._actionHistory
      });
      // Notify about error
      if (this.onStateError) {
        this.onStateError(result.reason);
      }
    }
  }

  /**
   * Add action to history for UI display
   * Keeps last 10 actions per turn
   */
  private addActionToHistory(oldState: GameState,state: GameState, action: GameAction): GameState {
    // Only track certain actions for display
    const description = this.getActionDescription(oldState, state, action);
    if (!description) return state;

    const player = state.players.find((p) => {
      // Find player associated with this action
      if ('payload' in action && 'playerId' in action.payload) {
        return p.id === action.payload.playerId;
      }
      return false;
    });

    if (!player) return state;

    const historyEntry: GameActionHistory = {
      playerId: player.id,
      playerName: player.nickname,
      description,
      timestamp: Date.now(),
      turnNumber: state.turnNumber,
    };

    const newState = copy(state);
    // Only keep actions from the latest turnNumber
    newState.recentActions = [...state.recentActions, historyEntry].filter(
      (a) => a.turnNumber === historyEntry.turnNumber
    );

    return newState;
  }

  /**
   * Get human-readable description of action
   */
  private getActionDescription(
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ): string | null {
    const oldPlayer = oldState.players.find((p) => {
      if ('payload' in action && 'playerId' in action.payload) {
        return p.id === action.payload.playerId;
      }
    });
    const player = newState.players.find((p) => {
      if ('payload' in action && 'playerId' in action.payload) {
        return p.id === action.payload.playerId;
      }
      return false;
    });

    if (!player) return null;

    const formatCard = (card: Card) => card.rank;

    switch (action.type) {
      case 'DRAW_CARD': {
        const drawnCard = newState.pendingAction?.card;
        return drawnCard ? `drew ${formatCard(drawnCard)}` : `drew a card`;
      }

      case 'PLAY_DISCARD': {
        const takenCard = newState.pendingAction?.card;
        return takenCard
          ? `played ${formatCard(takenCard)} from discard`
          : `played from discard`;
      }

      case 'USE_CARD_ACTION': {
        const actionCard = newState.pendingAction?.card;
        return actionCard
          ? `played ${formatCard(actionCard)}`
          : `played action card`;
      }

      case 'SWAP_CARD': {        
        const declaredRank = action.payload.declaredRank;
        const correctlyDeclared = oldPlayer?.cards.length === player.cards.length;
        return declaredRank !== undefined
          ? `swapped for ${declaredRank} (${correctlyDeclared ? 'correct' : 'incorrect'})`
          : `swapped card at position ${action.payload.position + 1}`;
      }

      case 'DISCARD_CARD': {
        const discardedCard = newState.discardPile.peekTop();
        return discardedCard
          ? `discarded ${formatCard(discardedCard)}`
          : `discarded`;
      }

      case 'CONFIRM_PEEK': {
        return null; // Don't show if no card info
      }

      case 'EXECUTE_QUEEN_SWAP':
      case 'EXECUTE_JACK_SWAP': {
        return 'swapped cards';
      }
      case 'SKIP_JACK_SWAP':
      case 'SKIP_QUEEN_SWAP': {
        return `didn't swap`;
      }

      case 'PARTICIPATE_IN_TOSS_IN': {
        const tossedCard = newState.discardPile.peekTop();
        return tossedCard
          ? `tossed in ${formatCard(tossedCard)}`
          : `tossed in a card`;
      }

      case 'CALL_VINTO':
        return `called Vinto!`;
      case 'SELECT_ACTION_TARGET': {
        switch (newState.pendingAction?.targetType) {
          case 'peek-then-swap':
            return newState.pendingAction.targets.length === 2
              ? `peeking at ${
                  newState.players.find(
                    (p) => p.id === newState.pendingAction?.targets[0].playerId
                  )?.nickname
                } (pos ${newState.pendingAction?.targets[0].position + 1}) and ${
                  newState.players.find(
                    (p) => p.id === newState.pendingAction?.targets[1].playerId
                  )?.nickname
                } (pos ${newState.pendingAction?.targets[1].position + 1})`
              : null;
          case 'swap-cards':
            return newState.pendingAction.targets.length === 2
              ? `thinking about ${
                  newState.players.find(
                    (p) => p.id === newState.pendingAction?.targets[0].playerId
                  )?.nickname
                } (pos ${newState.pendingAction?.targets[0].position + 1}) and ${
                  newState.players.find(
                    (p) => p.id === newState.pendingAction?.targets[1].playerId
                  )?.nickname
                } (pos ${newState.pendingAction?.targets[1].position + 1})`
              : null;
          case 'force-draw':
            return newState.pendingAction.targets.length === 1
              ? `forcing ${
                  newState.players.find(
                    (p) => p.id === newState.pendingAction?.targets[0].playerId
                  )?.nickname
                } to draw`
              : null;
          case 'opponent-card':
            return newState.pendingAction.targets.length === 1
              ? `peeked at ${
                  newState.players.find(
                    (p) => p.id === newState.pendingAction?.targets[0].playerId
                  )?.nickname
                }'s card  (pos ${newState.pendingAction?.targets[0].position + 1})`
              : null;

          case 'own-card':
            return newState.pendingAction.targets.length === 1
              ? `peeked at own card (pos ${
                  newState.pendingAction?.targets[0].position + 1
                })`
              : null;
          default:
            return null;
        }
      }

      case 'DECLARE_KING_ACTION':
        const rank = action.payload.declaredRank;
        return rank ? `declared ${rank}` : null;

      default:
        return null;
    }
  }

  /**
   * Register a callback for state changes
   * Used for animations, sounds, network sync, etc.
   */
  onStateUpdateSuccess(
    callback: (
      oldState: GameState,
      newState: GameState,
      action: GameAction
    ) => void
  ): void {
    this.onStateChange = callback;
  }

  /**
   * Register a callback for state errored changes
   */
  onStateUpdateError(callback: (reason: string) => void): void {
    this.onStateError = callback;
  }

  /**
   * Update visual state to match logical state
   * Called by AnimationService after animations complete
   * This is when the UI actually updates to show the new state
   */
  @action
  syncVisualState(): void {
    if (!deepEqual(this._visualState, this._state)) {
      console.log('[GameClient] Syncing visual state to logical state');
      this._visualState = copy(this._state);
    }
  }

  /**
   * Get the old visual state before starting animations
   * Used by AnimationService to calculate animation start positions
   */
  getVisualStateSnapshot(): GameState {
    return copy(this._visualState);
  }

  // ==========================================
  // Computed Properties (for UI convenience)
  // ==========================================

  /**
   * Get the current player
   */
  @computed
  get currentPlayer(): PlayerState {
    return this._state.players[this._state.currentPlayerIndex];
  }

  /**
   * Check if current player is a bot
   */
  @computed
  get isCurrentPlayerBot(): boolean {
    return this.currentPlayer.isBot;
  }

  /**
   * Check if current player is human
   */
  @computed
  get isCurrentPlayerHuman(): boolean {
    return this.currentPlayer.isHuman;
  }

  /**
   * Get the top card of the discard pile
   */
  @computed
  get topDiscardCard(): Card | undefined {
    return this._state.discardPile.peekTop();
  }

  /**
   * Get the top card of the discard pile
   */
  @computed
  get tossInRanks(): Rank[] {
    return this._state.activeTossIn?.ranks || [];
  }

  /**
   * Check if game is in setup phase
   */
  @computed
  get isSetupPhase(): boolean {
    return this._state.phase === 'setup';
  }

  /**
   * Check if game is in playing phase
   */
  @computed
  get isPlayingPhase(): boolean {
    return this._state.phase === 'playing';
  }

  /**
   * Check if game is over
   */
  @computed
  get isGameOver(): boolean {
    return this._state.phase === 'final';
  }

  /**
   * Check if player can draw from draw pile
   */
  @computed
  get canDrawCard(): boolean {
    return (
      (this._state.subPhase === 'idle' ||
        this._state.subPhase === 'ai_thinking') &&
      this._state.drawPile.length > 0
    );
  }

  /**
   * Check if player can take from discard pile
   */
  @computed
  get canTakeDiscard(): boolean {
    return (
      (this._state.subPhase === 'idle' ||
        this._state.subPhase === 'ai_thinking') &&
      this._state.discardPile.length > 0
    );
  }

  /**
   * Check if there's a pending action (drawn card waiting to be swapped)
   */
  @computed
  get hasPendingAction(): boolean {
    return this._state.pendingAction !== null;
  }

  /**
   * Get the pending card (if any)
   */
  @computed
  get pendingCard(): Card | undefined {
    return this._state.pendingAction?.card;
  }

  /**
   * Check if a toss-in is active
   */
  @computed
  get hasTossIn(): boolean {
    return this._state.activeTossIn !== null;
  }

  /**
   * Get player by ID
   */
  getPlayer(playerId: string): PlayerState | undefined {
    return this._state.players.find((p) => p.id === playerId);
  }

  /**
   * Get player index by ID
   */
  getPlayerIndex(playerId: string): number {
    return this._state.players.findIndex((p) => p.id === playerId);
  }

  /**
   * Check if it's a specific player's turn
   */
  isPlayerTurn(playerId: string): boolean {
    return this.currentPlayer.id === playerId;
  }

  /**
   * Get number of cards remaining in draw pile
   */
  @computed
  get drawPileCount(): number {
    return this._state.drawPile.length;
  }

  /**
   * Get number of cards in discard pile
   */
  @computed
  get discardPileCount(): number {
    return this._state.discardPile.length;
  }

  /**
   * Get the vinto caller (if any)
   */
  @computed
  get vintoCaller(): PlayerState | undefined {
    if (!this._state.vintoCallerId) return undefined;
    return this.getPlayer(this._state.vintoCallerId);
  }

  /**
   * Check if final turn has been triggered
   */
  @computed
  get isFinalTurn(): boolean {
    return this._state.finalTurnTriggered;
  }

  /**
   * Check if toss in activated
   */
  @computed
  get isTossInActivated(): boolean {
    return (
      this._state.phase === 'playing' &&
      this._state.subPhase === 'toss_queue_active'
    );
  }

  /**
   * Get coalition leader (if any)
   */
  @computed
  get coalitionLeader(): PlayerState | undefined {
    if (!this._state.coalitionLeaderId) return undefined;
    return this.getPlayer(this._state.coalitionLeaderId);
  }

  // ==========================================
  // Debug Helpers
  // ==========================================

  /**
   * Get current game phase as string (for debugging)
   */
  @computed
  get phaseString(): string {
    return `${this._state.phase} / ${this._state.subPhase}`;
  }

  /**
   * Export current state as JSON (for debugging/persistence)
   */
  exportState(): string {
    return JSON.stringify(this._state, null, 2);
  }

  /**
   * Import state from JSON (for debugging/persistence)
   */
  @action
  importState(json: string): void {
    try {
      const newState = JSON.parse(json) as GameState;
      this._state = newState;
    } catch (error) {
      logger.error('Failed to import state:', error);
    }
  }

  /**
   * Export action history for bug reports
   */
  exportActionHistory(): string {
    return JSON.stringify(this._actionHistory, null, 2);
  }

  /**
   * Export complete debug data (state + action history)
   */
  exportDebugData(): string {
    return JSON.stringify(
      {
        timestamp: Date.now(),
        gameState: this._state,
        actionHistory: this._actionHistory,
        userAgent:
          typeof navigator !== 'undefined' ? navigator.userAgent : 'unknown',
        viewport:
          typeof window !== 'undefined'
            ? { width: window.innerWidth, height: window.innerHeight }
            : null,
      },
      null,
      2
    );
  }
}
