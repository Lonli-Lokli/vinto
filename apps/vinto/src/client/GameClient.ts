// client/GameClient.ts
// Observable wrapper around GameEngine for UI integration

import { makeObservable, observable, action, computed } from 'mobx';
import { GameEngine } from '../engine/GameEngine';
import type { GameState, GameAction, PlayerState } from '../engine/types';
import type { Card } from '../app/shapes';

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
   * Current game state (observable)
   * UI components will react to changes in this state
   */
  @observable
  state: GameState;

  /**
   * Animation/effect callbacks (optional)
   * Triggered after state updates
   */
  private onStateChange?: (oldState: GameState, newState: GameState, action: GameAction) => void;

  constructor(initialState: GameState) {
    this.state = initialState;
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
   */
  @action
  dispatch(action: GameAction): void {
    const oldState = this.state;
    const newState = GameEngine.reduce(this.state, action);

    // Update observable state
    this.state = newState;

    // Trigger side effects
    if (this.onStateChange) {
      this.onStateChange(oldState, newState, action);
    }
  }

  /**
   * Register a callback for state changes
   * Used for animations, sounds, network sync, etc.
   */
  onStateUpdate(callback: (oldState: GameState, newState: GameState, action: GameAction) => void): void {
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
    return this.state.players[this.state.currentPlayerIndex];
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
    return this.state.discardPile[this.state.discardPile.length - 1];
  }

  /**
   * Check if game is in setup phase
   */
  @computed
  get isSetupPhase(): boolean {
    return this.state.phase === 'setup';
  }

  /**
   * Check if game is in playing phase
   */
  @computed
  get isPlayingPhase(): boolean {
    return this.state.phase === 'playing';
  }

  /**
   * Check if game is over
   */
  @computed
  get isGameOver(): boolean {
    return this.state.phase === 'final';
  }

  /**
   * Check if player can draw from draw pile
   */
  @computed
  get canDrawCard(): boolean {
    return (
      (this.state.subPhase === 'idle' || this.state.subPhase === 'ai_thinking') &&
      this.state.drawPile.length > 0
    );
  }

  /**
   * Check if player can take from discard pile
   */
  @computed
  get canTakeDiscard(): boolean {
    return (
      (this.state.subPhase === 'idle' || this.state.subPhase === 'ai_thinking') &&
      this.state.discardPile.length > 0
    );
  }

  /**
   * Check if there's a pending action (drawn card waiting to be swapped)
   */
  @computed
  get hasPendingAction(): boolean {
    return this.state.pendingAction !== null;
  }

  /**
   * Get the pending card (if any)
   */
  @computed
  get pendingCard(): Card | undefined {
    return this.state.pendingAction?.card;
  }

  /**
   * Check if a toss-in is active
   */
  @computed
  get hasTossIn(): boolean {
    return this.state.activeTossIn !== null;
  }

  /**
   * Get player by ID
   */
  getPlayer(playerId: string): PlayerState | undefined {
    return this.state.players.find(p => p.id === playerId);
  }

  /**
   * Get player index by ID
   */
  getPlayerIndex(playerId: string): number {
    return this.state.players.findIndex(p => p.id === playerId);
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
    return this.state.drawPile.length;
  }

  /**
   * Get number of cards in discard pile
   */
  @computed
  get discardPileCount(): number {
    return this.state.discardPile.length;
  }

  /**
   * Get the vinto caller (if any)
   */
  @computed
  get vintoCaller(): PlayerState | undefined {
    if (!this.state.vintoCallerId) return undefined;
    return this.getPlayer(this.state.vintoCallerId);
  }

  /**
   * Check if final turn has been triggered
   */
  @computed
  get isFinalTurn(): boolean {
    return this.state.finalTurnTriggered;
  }

  /**
   * Get coalition leader (if any)
   */
  @computed
  get coalitionLeader(): PlayerState | undefined {
    if (!this.state.coalitionLeaderId) return undefined;
    return this.getPlayer(this.state.coalitionLeaderId);
  }

  // ==========================================
  // Debug Helpers
  // ==========================================

  /**
   * Get current game phase as string (for debugging)
   */
  @computed
  get phaseString(): string {
    return `${this.state.phase} / ${this.state.subPhase}`;
  }

  /**
   * Export current state as JSON (for debugging/persistence)
   */
  exportState(): string {
    return JSON.stringify(this.state, null, 2);
  }

  /**
   * Import state from JSON (for debugging/persistence)
   */
  @action
  importState(json: string): void {
    try {
      const newState = JSON.parse(json) as GameState;
      this.state = newState;
    } catch (error) {
      console.error('Failed to import state:', error);
    }
  }
}
