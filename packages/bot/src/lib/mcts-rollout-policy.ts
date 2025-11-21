// Pure functions for prioritized rollout move selection

import { MCTSGameState, MCTSMove, MCTSPlayerState } from './mcts-types';

/**
 * Select move during rollout using prioritized policy
 * Priority: Game-ending > Information-gathering > Score reduction > Defensive > Random
 */
export function selectRolloutMove(
  state: MCTSGameState,
  moves: MCTSMove[]
): MCTSMove {
  const currentPlayer = state.players[state.currentPlayerIndex];
  if (!currentPlayer || moves.length === 0) {
    return moves[0];
  }

  // Priority 1: Game-Ending Moves
  const gameEndingMove = selectGameEndingMove(state, moves, currentPlayer);
  if (gameEndingMove) return gameEndingMove;

  // Priority 2: Information-Gathering
  const infoGatheringMove = selectInfoGatheringMove(
    state,
    moves,
    currentPlayer
  );
  if (infoGatheringMove) return infoGatheringMove;

  // Priority 3: Score Reduction
  const scoreReductionMove = selectScoreReductionMove(
    state,
    moves,
    currentPlayer
  );
  if (scoreReductionMove) return scoreReductionMove;

  // Priority 4: Defensive Moves
  const defensiveMove = selectDefensiveMove(state, moves, currentPlayer);
  if (defensiveMove) return defensiveMove;

  // Priority 5: Fallback - Random move
  return moves[Math.floor(Math.random() * moves.length)];
}

/**
 * Priority 1: Select game-ending moves (call-vinto, winning toss-in)
 */
function selectGameEndingMove(
  state: MCTSGameState,
  moves: MCTSMove[],
  currentPlayer: MCTSPlayerState
): MCTSMove | null {
  // Check for call-vinto
  const vintoMoves = moves.filter((m) => m.type === 'call-vinto');
  if (vintoMoves.length > 0) {
    const botScore = currentPlayer.score;
    const opponentScores = state.players
      .filter((p) => p.id !== currentPlayer.id)
      .map((p) => p.score);
    const avgOpponentScore =
      opponentScores.reduce((a, b) => a + b, 0) / (opponentScores.length || 1);

    // Call vinto if bot score is significantly lower
    if (botScore < avgOpponentScore - 5) {
      return vintoMoves[0];
    }
  }

  // Check for winning toss-in
  const tossInMoves = moves.filter((m) => m.type === 'toss-in');
  if (tossInMoves.length > 0 && currentPlayer.cardCount === 1) {
    return tossInMoves[0];
  }

  return null;
}

/**
 * Priority 2: Select information-gathering moves (toss-in, peek actions)
 */
function selectInfoGatheringMove(
  state: MCTSGameState,
  moves: MCTSMove[],
  currentPlayer: MCTSPlayerState
): MCTSMove | null {
  // Known matching card toss-in
  const tossInMoves = moves.filter((m) => m.type === 'toss-in');
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

  // Peek actions (7, 8, Q) - 75% probability
  const peekMoves = moves.filter((m) => {
    if (m.type !== 'use-action') return false;
    const card = state.pendingCard;
    if (!card) return false;
    return card.rank === '7' || card.rank === '8' || card.rank === 'Q';
  });

  if (peekMoves.length > 0 && Math.random() < 0.75) {
    return peekMoves[Math.floor(Math.random() * peekMoves.length)];
  }

  return null;
}

/**
 * Priority 3: Select score reduction moves (swap high for low)
 */
function selectScoreReductionMove(
  state: MCTSGameState,
  moves: MCTSMove[],
  currentPlayer: MCTSPlayerState
): MCTSMove | null {
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

  return null;
}

/**
 * Priority 4: Select defensive moves (Ace against opponents near winning)
 */
function selectDefensiveMove(
  state: MCTSGameState,
  moves: MCTSMove[],
  currentPlayer: MCTSPlayerState
): MCTSMove | null {
  // Check if an opponent is close to winning
  const opponentsCloseToWinning = state.players.filter(
    (p) => p.id !== currentPlayer.id && p.cardCount <= 2
  );

  if (opponentsCloseToWinning.length > 0) {
    // Look for Ace (force draw) action
    const aceMoves = moves.filter((m) => {
      if (m.type !== 'use-action') return false;
      const card = state.pendingCard;
      if (!card) return false;
      return card.rank === 'A';
    });

    if (aceMoves.length > 0) {
      // Target the opponent closest to winning
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

  return null;
}
