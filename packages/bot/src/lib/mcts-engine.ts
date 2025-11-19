/**
 * MCTS Engine
 * Core Monte Carlo Tree Search algorithm implementation
 */

import copy from 'fast-copy';
import {
  Card,
  getCardValue,
  getCardShortDescription,
  Rank,
} from '@vinto/shapes';
import { MCTSMoveGenerator } from './mcts-move-generator';
import { MCTSStateTransition } from './mcts-state-transition';
import { MCTSGameState, MCTSMove, MCTSNode, MCTSConfig } from './mcts-types';
import {
  evaluateTossInPotential,
  evaluateRelativePosition,
  evaluateActionCardValue,
  evaluateInformationAdvantage,
  evaluateThreatLevel,
} from './evaluation-helpers';

export class MCTSEngine {
  constructor(private config: MCTSConfig) {}

  /**
   * Run MCTS algorithm and return best move
   */
  runMCTS(rootState: MCTSGameState): MCTSMove {
    const root = new MCTSNode(rootState, null, null);
    root.untriedMoves = this.generatePossibleMoves(rootState);

    if (root.untriedMoves.length === 0) {
      console.log(`[MCTS] No possible moves available, returning pass`);
      return {
        type: 'pass',
        playerId: rootState.botPlayerId,
      };
    }

    const startTime = Date.now();
    let iterations = 0;

    // Run MCTS iterations
    while (
      iterations < this.config.iterations &&
      Date.now() - startTime < this.config.timeLimit
    ) {
      let node = this.select(root);

      if (!node.isTerminal && node.hasUntriedMoves()) {
        node = this.expand(node);
      }

      const reward = this.simulate(node.state);
      node.backpropagate(reward);

      iterations++;
    }

    console.log(
      `[MCTS] Completed ${iterations} iterations in ${Date.now() - startTime}ms`
    );

    // Select best move
    const bestChild = root.selectMostVisitedChild();

    if (!bestChild || !bestChild.move) {
      return {
        type: 'pass',
        playerId: rootState.botPlayerId,
      };
    }

    console.log(
      `[MCTS] Best move: ${bestChild.move.type} (visits: ${
        bestChild.visits
      }, reward: ${bestChild.getAverageReward().toFixed(3)})`
    );

    return bestChild.move;
  }

  /**
   * Selection phase - traverse tree using UCB1
   */
  private select(node: MCTSNode): MCTSNode {
    while (!node.isTerminal) {
      if (node.hasUntriedMoves()) {
        return node;
      }

      if (!node.isFullyExpanded) {
        return node;
      }

      const child = node.selectBestChildUCB1(this.config.explorationConstant);
      if (!child) break;

      node = child;
    }

    return node;
  }

  /**
   * Expansion phase - add a new child node
   */
  private expand(node: MCTSNode): MCTSNode {
    const move = node.getRandomUntriedMove();
    if (!move) return node;

    const newState = this.applyMove(node.state, move);
    const child = new MCTSNode(newState, move, node);
    child.untriedMoves = this.generatePossibleMoves(newState);

    node.addChild(child);

    return child;
  }

  /**
   * Simulation phase - prioritized rollout with determinization
   */
  private simulate(state: MCTSGameState): number {
    // Determinize: sample hidden information
    const deterministicState = this.determinize(state);

    let currentState = copy(deterministicState);
    let depth = 0;

    // Fast rollout with prioritized move selection
    while (!this.isTerminal(currentState) && depth < this.config.rolloutDepth) {
      const moves = this.generatePossibleMoves(currentState);
      if (moves.length === 0) break;

      const move = this.selectRolloutMove(currentState, moves);
      currentState = this.applyMove(currentState, move);

      depth++;
    }

    // Evaluate final state
    return this.evaluateState(currentState, state.botPlayerId);
  }

  /**
   * Select move during rollout using prioritized policy
   */
  private selectRolloutMove(state: MCTSGameState, moves: MCTSMove[]): MCTSMove {
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer || moves.length === 0) {
      return moves[0];
    }

    // Priority 1: Game-Ending Moves
    const vintoMoves = moves.filter((m) => m.type === 'call-vinto');
    if (vintoMoves.length > 0) {
      const botScore = currentPlayer.score;
      const opponentScores = state.players
        .filter((p) => p.id !== currentPlayer.id)
        .map((p) => p.score);
      const avgOpponentScore =
        opponentScores.reduce((a, b) => a + b, 0) /
        (opponentScores.length || 1);

      if (botScore < avgOpponentScore - 5) {
        return vintoMoves[0];
      }
    }

    const tossInMoves = moves.filter((m) => m.type === 'toss-in');
    if (tossInMoves.length > 0 && currentPlayer.cardCount === 1) {
      return tossInMoves[0];
    }

    // Priority 2: Information-Gathering
    if (tossInMoves.length > 0) {
      const discardRank = state.discardPileTop?.rank;
      if (discardRank) {
        for (const move of tossInMoves) {
          if (move.tossInPositions && move.tossInPositions.length > 0) {
            for (const position of move.tossInPositions) {
              const card = state.hiddenCards.get(
                `${currentPlayer.id}-${position}`
              );
              const memory = currentPlayer.knownCards.get(position);
              if (
                memory &&
                memory.confidence > 0.5 &&
                card &&
                card.rank === discardRank
              ) {
                return move;
              }
            }
          }
        }
      }
    }

    // Peek actions - 75% probability
    const peekMoves = moves.filter((m) => {
      if (m.type !== 'use-action') return false;
      const card = state.pendingCard;
      if (!card) return false;
      return card.rank === '7' || card.rank === '8' || card.rank === 'Q';
    });

    if (peekMoves.length > 0 && Math.random() < 0.75) {
      return peekMoves[Math.floor(Math.random() * peekMoves.length)];
    }

    // Priority 3: Score Reduction
    const swapMoves = moves.filter((m) => m.type === 'swap');
    if (swapMoves.length > 0) {
      for (const move of swapMoves) {
        if (move.swapPosition !== undefined) {
          const oldCard = state.hiddenCards.get(
            `${currentPlayer.id}-${move.swapPosition}`
          );
          const newCard = state.pendingCard;
          const memory = currentPlayer.knownCards.get(move.swapPosition);

          if (
            oldCard &&
            newCard &&
            memory &&
            memory.confidence > 0.5 &&
            oldCard.value > 9 &&
            newCard.value < 3
          ) {
            return move;
          }
        }
      }
    }

    // Priority 4: Defensive Moves
    const opponentsCloseToWinning = state.players.filter(
      (p) => p.id !== currentPlayer.id && p.cardCount <= 2
    );

    if (opponentsCloseToWinning.length > 0) {
      const aceMoves = moves.filter((m) => {
        if (m.type !== 'use-action') return false;
        const card = state.pendingCard;
        if (!card) return false;
        return card.rank === 'A';
      });

      if (aceMoves.length > 0) {
        const targetOpponent = opponentsCloseToWinning.reduce((closest, opp) =>
          opp.cardCount < closest.cardCount ? opp : closest
        );

        const targetedAceMove = aceMoves.find(
          (m) =>
            m.targets &&
            m.targets.length > 0 &&
            m.targets[0].playerId === targetOpponent.id
        );

        if (targetedAceMove) {
          return targetedAceMove;
        }
      }
    }

    // Priority 5: Fallback - Random
    return moves[Math.floor(Math.random() * moves.length)];
  }

  /**
   * Determinize hidden information by sampling
   */
  private determinize(state: MCTSGameState): MCTSGameState {
    const newState = copy(state);
    newState.hiddenCards = new Map();

    // Build deck of available ranks
    const standardRanks: Rank[] = [
      'A',
      'A',
      'A',
      'A',
      '2',
      '2',
      '2',
      '2',
      '3',
      '3',
      '3',
      '3',
      '4',
      '4',
      '4',
      '4',
      '5',
      '5',
      '5',
      '5',
      '6',
      '6',
      '6',
      '6',
      '7',
      '7',
      '7',
      '7',
      '8',
      '8',
      '8',
      '8',
      '9',
      '9',
      '9',
      '9',
      '10',
      '10',
      '10',
      '10',
      'J',
      'J',
      'J',
      'J',
      'Q',
      'Q',
      'Q',
      'Q',
      'K',
      'K',
      'K',
      'K',
      'Joker',
      'Joker',
    ];

    const availableRanks: Rank[] = [...standardRanks];

    // Remove discarded cards
    for (const discardedCard of state.discardPile) {
      const idx = availableRanks.indexOf(discardedCard.rank);
      if (idx >= 0) {
        availableRanks.splice(idx, 1);
      }
    }

    // Remove known cards
    for (const player of state.players) {
      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);
        if (memory && memory.confidence > 0.5) {
          const knownRank = memory.card!.rank;
          const idx = availableRanks.indexOf(knownRank);
          if (idx >= 0) {
            availableRanks.splice(idx, 1);
          }
        }
      }
    }

    // Sample cards for each position
    for (const player of state.players) {
      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);

        if (!memory || memory.confidence < 0.5) {
          let sampledRank: Rank;

          if (availableRanks.length > 0) {
            const idx = Math.floor(Math.random() * availableRanks.length);
            sampledRank = availableRanks[idx];
            availableRanks.splice(idx, 1);
          } else {
            sampledRank = state.botMemory.sampleCardFromDistribution() || '6';
          }

          const card: Card = {
            id: `${player.id}-${pos}-sampled`,
            rank: sampledRank,
            value: getCardValue(sampledRank),
            actionText: getCardShortDescription(sampledRank),
            played: false,
          };
          newState.hiddenCards.set(`${player.id}-${pos}`, card);
        } else {
          newState.hiddenCards.set(`${player.id}-${pos}`, memory.card!);
        }
      }
    }

    return newState;
  }

  /**
   * Evaluate state - improved with coalition awareness
   */
  private evaluateState(state: MCTSGameState, botPlayerId: string): number {
    const botPlayer = state.players.find((p) => p.id === botPlayerId);
    if (!botPlayer) return 0;

    // Terminal state check
    if (state.isTerminal) {
      if (state.vintoCallerId && state.coalitionLeaderId) {
        const isCoalitionVictory = state.winner !== state.vintoCallerId;
        return isCoalitionVictory ? 1.0 : 0.0;
      }

      return state.winner === botPlayerId ? 1.0 : 0.0;
    }

    // Coalition mode evaluation
    if (
      state.vintoCallerId &&
      state.coalitionLeaderId &&
      botPlayerId !== state.vintoCallerId
    ) {
      return this.evaluateCoalitionState(state, botPlayerId);
    }

    // Normal mode evaluation
    const tossInScore = evaluateTossInPotential(state, botPlayer);
    const positionScore = evaluateRelativePosition(state, botPlayer);
    const actionScore = evaluateActionCardValue(state, botPlayer);
    const infoScore = evaluateInformationAdvantage(state, botPlayer);
    const threatScore = evaluateThreatLevel(state, botPlayer);

    const finalScore =
      tossInScore * 0.3 +
      positionScore * 0.25 +
      actionScore * 0.2 +
      infoScore * 0.15 +
      threatScore * 0.1;

    return Math.max(0, Math.min(1, finalScore));
  }

  /**
   * Coalition evaluation
   */
  private evaluateCoalitionState(
    state: MCTSGameState,
    _botPlayerId: string
  ): number {
    const vintoCallerId = state.vintoCallerId;
    if (!vintoCallerId) return 0;

    const vintoPlayer = state.players.find((p) => p.id === vintoCallerId);
    if (!vintoPlayer) return 0;

    // Find coalition champion
    const coalitionMembers = state.players.filter(
      (p) => p.id !== vintoCallerId
    );

    let championPlayer = coalitionMembers[0];
    let championScore = championPlayer.score;

    for (const member of coalitionMembers) {
      if (member.score < championScore) {
        championScore = member.score;
        championPlayer = member;
      }
    }

    console.log(
      `[Coalition Eval] Champion: ${championPlayer.id} (score: ${championScore}), ` +
        `Vinto: ${vintoCallerId} (score: ${vintoPlayer.score})`
    );

    const scoreDifference = vintoPlayer.score - championScore;
    const cardDifference = vintoPlayer.cardCount - championPlayer.cardCount;

    const scoreAdvantage = Math.max(
      0,
      Math.min(1, (scoreDifference + 10) / 30)
    );
    const cardAdvantage = Math.max(0, Math.min(1, (cardDifference + 2) / 5));
    const championTossInScore = evaluateTossInPotential(state, championPlayer);
    const vintoThreatScore = 1.0 - evaluateActionCardValue(state, vintoPlayer);

    const coalitionScore =
      scoreAdvantage * 0.4 +
      cardAdvantage * 0.3 +
      championTossInScore * 0.2 +
      vintoThreatScore * 0.1;

    console.log(
      `[Coalition Eval] Final score: ${coalitionScore.toFixed(3)} ` +
        `(scoreAdv: ${scoreAdvantage.toFixed(
          2
        )}, cardAdv: ${cardAdvantage.toFixed(2)})`
    );

    return Math.max(0, Math.min(1, coalitionScore));
  }

  // Helper methods
  private generatePossibleMoves(state: MCTSGameState): MCTSMove[] {
    const moves = MCTSMoveGenerator.generateMoves(state);
    return MCTSMoveGenerator.pruneMoves(state, moves);
  }

  private applyMove(state: MCTSGameState, move: MCTSMove): MCTSGameState {
    return MCTSStateTransition.applyMove(state, move);
  }

  private isTerminal(state: MCTSGameState): boolean {
    return MCTSStateTransition.isTerminal(state);
  }
}
