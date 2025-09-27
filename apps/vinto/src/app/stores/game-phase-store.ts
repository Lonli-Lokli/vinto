'use client';

import { makeAutoObservable } from 'mobx';

export type GamePhase = 'setup' | 'playing' | 'final' | 'scoring';
export type GameSubPhase =
  | 'idle'
  | 'drawing'
  | 'choosing'
  | 'selecting'
  | 'tossing'
  | 'processing'
  | 'awaiting_action'
  | 'declaring_rank'
  | 'ai_thinking';


export class GamePhaseStore {
  phase: GamePhase = 'setup';
  subPhase: GameSubPhase = 'idle';

  // Derived from boolean flags in original store
  finalTurnTriggered = false;

  constructor() {
    makeAutoObservable(this);
  }

  get canTransitionTo() {
    return (newPhase: GamePhase, newSubPhase: GameSubPhase): boolean => {
      const currentState = `${this.phase}.${this.subPhase}`;
      const newState = `${newPhase}.${newSubPhase}`;

      // Define valid transitions based on game flow
      const validTransitions: Record<string, string[]> = {
        'setup.idle': ['playing.idle'],
        'playing.idle': ['playing.drawing', 'final.idle', 'scoring.idle'],
        'playing.drawing': ['playing.choosing'],
        'playing.choosing': ['playing.selecting', 'playing.tossing', 'playing.awaiting_action'],
        'playing.selecting': ['playing.declaring_rank', 'playing.tossing'],
        'playing.declaring_rank': ['playing.tossing'],
        'playing.tossing': ['playing.processing', 'playing.idle'],
        'playing.processing': ['playing.awaiting_action', 'playing.idle'],
        'playing.awaiting_action': ['playing.tossing', 'playing.idle'],
        'playing.ai_thinking': ['playing.tossing'],
        'final.idle': ['final.drawing', 'scoring.idle'],
        'final.drawing': ['final.choosing'],
        'final.choosing': ['final.selecting', 'final.tossing', 'final.awaiting_action'],
        'final.selecting': ['final.declaring_rank', 'final.tossing'],
        'final.declaring_rank': ['final.tossing'],
        'final.tossing': ['final.processing', 'scoring.idle'],
        'final.processing': ['final.awaiting_action', 'scoring.idle'],
        'final.awaiting_action': ['final.tossing', 'scoring.idle'],
        'scoring.idle': []
      };

      return validTransitions[currentState]?.includes(newState) ?? false;
    };
  }

  transitionTo(newPhase: GamePhase, newSubPhase: GameSubPhase) {
    if (this.canTransitionTo(newPhase, newSubPhase)) {
      this.phase = newPhase;
      this.subPhase = newSubPhase;
    } else {
      console.warn(`Invalid transition from ${this.phase}.${this.subPhase} to ${newPhase}.${newSubPhase}`);
    }
  }

  // Convenience methods for common state checks
  get isSetup() {
    return this.phase === 'setup';
  }

  get isPlaying() {
    return this.phase === 'playing' || this.phase === 'final';
  }

  get isGameActive() {
    return this.phase === 'playing' || this.phase === 'final';
  }

  get isScoring() {
    return this.phase === 'scoring';
  }

  get isIdle() {
    return this.subPhase === 'idle';
  }

  get isChoosingCardAction() {
    return this.subPhase === 'choosing';
  }

  get isSelectingSwapPosition() {
    return this.subPhase === 'selecting';
  }

  get isAwaitingActionTarget() {
    return this.subPhase === 'awaiting_action';
  }

  get isDeclaringRank() {
    return this.subPhase === 'declaring_rank';
  }

  get isWaitingForTossIn() {
    return this.subPhase === 'tossing';
  }

  get isProcessingTossInQueue() {
    return this.subPhase === 'processing';
  }

  get isAIThinking() {
    return this.subPhase === 'ai_thinking';
  }

  // State update methods
  startGame() {
    this.transitionTo('playing', 'idle');
  }

  finishSetup() {
    this.transitionTo('playing', 'idle');
  }

  startDrawing() {
    this.transitionTo(this.phase, 'drawing');
  }

  startChoosingAction() {
    this.transitionTo(this.phase, 'choosing');
  }

  startSelectingPosition() {
    this.transitionTo(this.phase, 'selecting');
  }

  startAwaitingAction() {
    this.transitionTo(this.phase, 'awaiting_action');
  }

  startDeclaringRank() {
    this.transitionTo(this.phase, 'declaring_rank');
  }

  startTossIn() {
    this.transitionTo(this.phase, 'tossing');
  }

  startProcessingTossIn() {
    this.transitionTo(this.phase, 'processing');
  }

  startAIThinking() {
    this.transitionTo(this.phase, 'ai_thinking');
  }

  returnToIdle() {
    this.transitionTo(this.phase, 'idle');
  }

  triggerFinalTurn() {
    this.finalTurnTriggered = true;
    if (this.phase === 'playing') {
      this.transitionTo('final', this.subPhase);
    }
  }

  startScoring() {
    this.transitionTo('scoring', 'idle');
  }

  reset() {
    this.phase = 'setup';
    this.subPhase = 'idle';
    this.finalTurnTriggered = false;
  }
}