'use client';

import { injectable } from 'tsyringe';
import { makeAutoObservable } from 'mobx';
import { GameToastService } from '../lib/toast-service';

export type GamePhase = 'setup' | 'playing' | 'final' | 'scoring';
export type GameSubPhase =
  | 'idle'
  | 'drawing'
  | 'choosing'
  | 'selecting'
  | 'awaiting_action'
  | 'declaring_rank'
  | 'ai_thinking'
  | 'toss_queue_active'
  | 'toss_queue_processing';
export type FullGameState = `${GamePhase}.${GameSubPhase}`;

@injectable()
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
      const currentState: FullGameState = `${this.phase}.${this.subPhase}`;
      const newState: FullGameState = `${newPhase}.${newSubPhase}`;

      return validTransitions[currentState]?.includes(newState) ?? false;
    };
  }

  transitionTo(newPhase: GamePhase, newSubPhase: GameSubPhase) {
    if (this.canTransitionTo(newPhase, newSubPhase)) {
      this.phase = newPhase;
      this.subPhase = newSubPhase;
    } else {
      console.warn(
        `Invalid transition from ${this.phase}.${this.subPhase} to ${newPhase}.${newSubPhase}`
      );
      GameToastService.warning(
        `Invalid transition from ${this.phase}.${this.subPhase} to ${newPhase}.${newSubPhase}`
      );
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

  get isTossQueueActive() {
    return this.subPhase === 'toss_queue_active';
  }

  get isTossQueueProcessing() {
    return this.subPhase === 'toss_queue_processing';
  }

  get isInTossPhase() {
    return this.isTossQueueActive || this.isTossQueueProcessing;
  }

  get isAIThinking() {
    return this.subPhase === 'ai_thinking';
  }

  get canCallVinto() {
    // Vinto can only be called during normal turn progression, not during any toss-in processing
    return this.isIdle && !this.isInTossPhase;
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

  startTossQueueActive() {
    this.transitionTo(this.phase, 'toss_queue_active');
  }

  startTossQueueProcessing() {
    this.transitionTo(this.phase, 'toss_queue_processing');
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

// Define valid transitions based on game flow
const validTransitions: Partial<Record<FullGameState, FullGameState[]>> = {
  // SETUP PHASE
  'setup.idle': [
    'playing.idle', // When: Human finishes peeking at their 2 cards
  ],

  // PLAYING PHASE - IDLE
  'playing.idle': [
    'playing.drawing', // When: Human clicks draw pile to start their turn
    'playing.ai_thinking', // When: Bot's turn begins
    'playing.awaiting_action', // When: Player takes card from discard pile with action
    'playing.toss_queue_active', // When: Toss-in period starts after card played
    'playing.idle', // When: Self-loop for state refresh
    'final.idle', // When: Vinto is called, triggering final round
    'scoring.idle', // When: Final round completes and all turns are done
  ],

  // PLAYING PHASE - DRAWING
  'playing.drawing': [
    'playing.choosing', // When: Card is drawn, player must choose action
  ],

  // PLAYING PHASE - CHOOSING
  'playing.choosing': [
    'playing.selecting', // When: Player chooses to swap drawn card
    'playing.awaiting_action', // When: Player plays action card
    'playing.idle', // When: Player discards card without swap
  ],

  // PLAYING PHASE - SELECTING (swap position)
  'playing.selecting': [
    'playing.declaring_rank', // When: Player selects position to swap and must declare rank
    'playing.idle', // When: Player cancels swap
  ],

  // PLAYING PHASE - DECLARING RANK
  'playing.declaring_rank': [
    'playing.toss_queue_active', // When: Rank declared/skipped, swap complete, toss-in starts
    'playing.idle', // When: Declaration cancelled or action completes without toss-in
  ],

  // PLAYING PHASE - TOSS QUEUE ACTIVE
  'playing.toss_queue_active': [
    'playing.toss_queue_processing', // When: Timer expires, start processing queued toss-ins
    'playing.awaiting_action', // When: Player tosses in card with action during toss-in period
    'playing.idle', // When: No toss-ins occurred, advance turn
  ],

  // PLAYING PHASE - TOSS QUEUE PROCESSING
  'playing.toss_queue_processing': [
    'playing.awaiting_action', // When: Processing tossed card with action
    'playing.idle', // When: All toss-ins processed, advance turn
  ],

  // PLAYING PHASE - AWAITING ACTION
  'playing.awaiting_action': [
    'playing.toss_queue_active', // When: Action completes during toss-in, return to toss-in period
    'playing.idle', // When: Action completes normally, return to idle
  ],

  // PLAYING PHASE - AI THINKING
  'playing.ai_thinking': [
    'playing.choosing', // When: Bot draws card and decides action
    'playing.selecting', // When: Bot chooses to swap
    'playing.declaring_rank', // When: Bot selects swap position
    'playing.awaiting_action', // When: Bot plays action card
    'playing.toss_queue_active', // When: Bot's turn completes, toss-in starts
    'playing.idle', // When: Bot's turn completes without toss-in
  ],

  // FINAL PHASE - IDLE (same as playing but can transition to scoring)
  'final.idle': [
    'final.drawing', // When: Human draws card in final round
    'final.ai_thinking', // When: Bot's turn in final round
    'final.awaiting_action', // When: Action card played in final round
    'final.toss_queue_active', // When: Toss-in period starts in final round
    'final.idle', // When: Self-loop for state refresh
    'scoring.idle', // When: Final turn completes at human player
  ],

  // FINAL PHASE - DRAWING
  'final.drawing': [
    'final.choosing', // When: Card drawn in final round
  ],

  // FINAL PHASE - CHOOSING
  'final.choosing': [
    'final.selecting', // When: Player chooses to swap in final round
    'final.awaiting_action', // When: Player plays action in final round
    'final.idle', // When: Player discards without action
  ],

  // FINAL PHASE - SELECTING
  'final.selecting': [
    'final.declaring_rank', // When: Position selected, must declare
    'final.idle', // When: Swap cancelled
  ],

  // FINAL PHASE - DECLARING RANK
  'final.declaring_rank': [
    'final.toss_queue_active', // When: Declaration completes, toss-in starts
    'final.idle', // When: Declaration completes without toss-in
  ],

  // FINAL PHASE - TOSS QUEUE ACTIVE
  'final.toss_queue_active': [
    'final.toss_queue_processing', // When: Timer expires, start processing queued toss-ins
    'final.awaiting_action', // When: Player tosses in card with action during toss-in period
    'final.idle', // When: No toss-ins occurred, advance turn
    'scoring.idle', // When: Toss-in completes and game ends
  ],

  // FINAL PHASE - TOSS QUEUE PROCESSING
  'final.toss_queue_processing': [
    'final.awaiting_action', // When: Processing tossed card with action
    'final.idle', // When: All toss-ins processed, advance turn
    'scoring.idle', // When: Processing completes and game ends
  ],

  // FINAL PHASE - AWAITING ACTION
  'final.awaiting_action': [
    'final.toss_queue_active', // When: Action completes during toss-in, return to toss-in period
    'final.idle', // When: Action completes in final round
    'scoring.idle', // When: Action completes and game ends
  ],

  // FINAL PHASE - AI THINKING
  'final.ai_thinking': [
    'final.choosing', // When: Bot draws in final round
    'final.selecting', // When: Bot chooses swap in final round
    'final.declaring_rank', // When: Bot declares rank in final round
    'final.awaiting_action', // When: Bot plays action in final round
    'final.toss_queue_active', // When: Bot's turn completes, toss-in starts
    'final.idle', // When: Bot's turn ends in final round
  ],

  // SCORING PHASE - Terminal state
  'scoring.idle': [
    // No transitions allowed - game is over
  ],
};
