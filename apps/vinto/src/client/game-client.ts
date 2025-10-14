// client/GameClient.ts
// Observable wrapper around GameEngine for UI integration

import { makeObservable, observable, action, computed } from 'mobx';

import copy from 'fast-copy';
import type {
  GameState,
  GameAction,
  GameActionHistory,
  PlayerState,
  Card,
} from '@/shared';
import { GameEngine } from '@/engine';

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
   * UI components access this via the readonly getter
   */
  @observable
  private _state: GameState;

  /**
   * Public readonly accessor for game state
   * Prevents direct state mutation from UI components
   */
  get state(): Readonly<GameState> {
    return this._state;
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

  constructor(initialState: GameState) {
    this._state = initialState;
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
    let newState = GameEngine.reduce(this._state, action);

    // Add action to history (for UI display)
    newState = this.addActionToHistory(newState, action);

    // Update observable state
    this._state = newState;

    // Trigger side effects
    if (this.onStateChange) {
      this.onStateChange(oldState, newState, action);
    }
  }

  /**
   * Add action to history for UI display
   * Keeps last 10 actions per turn
   */
  private addActionToHistory(state: GameState, action: GameAction): GameState {
    // Only track certain actions for display
    const description = this.getActionDescription(state, action);
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
      playerName: player.name,
      description,
      timestamp: Date.now(),
      turnNumber: state.turnCount,
    };

    const newState = copy(state);
    newState.recentActions = [...state.recentActions, historyEntry];

    // Keep only last 10 actions
    if (newState.recentActions.length > 10) {
      newState.recentActions = newState.recentActions.slice(-10);
    }

    return newState;
  }

  /**
   * Get human-readable description of action
   */
  private getActionDescription(
    state: GameState,
    action: GameAction
  ): string | null {
    const player = state.players.find((p) => {
      if ('payload' in action && 'playerId' in action.payload) {
        return p.id === action.payload.playerId;
      }
      return false;
    });

    if (!player) return null;

    switch (action.type) {
      case 'DRAW_CARD':
        return `${player.name} drew a card`;
      case 'SWAP_CARD':
        return `${player.name} swapped a card`;
      case 'DISCARD_CARD':
        return `${player.name} discarded`;
      case 'PARTICIPATE_IN_TOSS_IN':
        return `${player.name} tossed in a card`;
      case 'CALL_VINTO':
        return `${player.name} called Vinto!`;
      default:
        return null;
    }
  }

  /**
   * Register a callback for state changes
   * Used for animations, sounds, network sync, etc.
   */
  onStateUpdate(
    callback: (
      oldState: GameState,
      newState: GameState,
      action: GameAction
    ) => void
  ): void {
    this.onStateChange = callback;
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
      console.error('Failed to import state:', error);
    }
  }
}
