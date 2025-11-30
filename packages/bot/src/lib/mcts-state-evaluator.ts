// Pure functions for evaluating MCTS game states

import { MCTSGameState, MCTSPlayerState } from './mcts-types';
import {
  evaluateTossInPotential,
  evaluateRelativePosition,
  evaluateActionCardValue,
  evaluateInformationAdvantage,
  evaluateThreatLevel,
} from './evaluation-helpers';
import { evaluateCoalitionState } from './mcts-coalition-evaluator';

/**
 * Evaluate game state from bot's perspective
 * Returns a score between 0 and 1 (higher is better)
 */
export function evaluateState(
  state: MCTSGameState,
  botPlayerId: string
): number {
  const botPlayer = state.players.find((p) => p.id === botPlayerId);
  if (!botPlayer) return 0;

  // Terminal state check
  if (state.isTerminal) {
    return evaluateTerminalState(state, botPlayerId);
  }

  // Coalition mode: evaluate coalition's best chance
  // Use coalition evaluation if we're in coalition mode,
  // regardless of which specific bot is being evaluated
  const isInCoalitionMode = !!(state.vintoCallerId && state.coalitionLeaderId);
  const isCoalitionMember = botPlayerId !== state.vintoCallerId;

  if (isInCoalitionMode && isCoalitionMember) {
    // Use coalition evaluation for ANY coalition member
    return evaluateCoalitionState(state, botPlayerId);
  }

  // Normal mode: self-interested evaluation
  return evaluateNormalState(state, botPlayer);
}

/**
 * Evaluate terminal state
 */
function evaluateTerminalState(
  state: MCTSGameState,
  botPlayerId: string
): number {
  // Coalition mode: win if ANY coalition member wins
  if (state.vintoCallerId && state.coalitionLeaderId) {
    const isCoalitionVictory = state.winner !== state.vintoCallerId;
    return isCoalitionVictory ? 1.0 : 0.0;
  }

  // Normal mode: win if bot wins
  return state.winner === botPlayerId ? 1.0 : 0.0;
}

/**
 * Evaluate state in normal (self-interested) mode
 */
function evaluateNormalState(
  state: MCTSGameState,
  botPlayer: MCTSPlayerState
): number {
  // Component 1: Toss-in Potential (30% weight)
  const tossInScore = evaluateTossInPotential(state, botPlayer);

  // Component 2: Relative Position (25% weight)
  const positionScore = evaluateRelativePosition(state, botPlayer);

  // Component 3: Action Card Value (20% weight)
  const actionScore = evaluateActionCardValue(state, botPlayer);

  // Component 4: Information Advantage (15% weight)
  const infoScore = evaluateInformationAdvantage(state, botPlayer);

  // Component 5: Threat Level (10% weight)
  const threatScore = evaluateThreatLevel(state, botPlayer);

  const finalScore =
    tossInScore * 0.3 +
    positionScore * 0.25 +
    actionScore * 0.2 +
    infoScore * 0.15 +
    threatScore * 0.1;

  return Math.max(0, Math.min(1, finalScore));
}
