// Pure functions for extracting action plans from MCTS tree

import { MCTSNode } from './mcts-types';
import { BotActionDecision } from './shapes';

/**
 * Extract action plan from MCTS tree after take-discard or King declaration
 * This looks at the best child nodes to see what the bot plans to do with the action
 */
export function extractActionPlan(
  node: MCTSNode
): BotActionDecision | undefined {
  // The node's best child should represent using the action
  const actionChild = node.selectMostVisitedChild();

  if (!actionChild || !actionChild.move) {
    return undefined;
  }

  // If the move has targets already, use those
  if (actionChild.move.targets && actionChild.move.targets.length > 0) {
    return {
      targets: actionChild.move.targets.map((t) => ({
        playerId: t.playerId,
        position: t.position,
      })),
      shouldSwap: actionChild.move.shouldSwap,
      declaredRank: actionChild.move.declaredRank,
    };
  }

  return undefined;
}

/**
 * Check if a move requires action plan extraction
 */
export function shouldExtractActionPlan(move: {
  type: string;
  actionCard?: any;
  declaredRank?: any;
  targets?: any[];
}): boolean {
  // take-discard with action card
  if (move.type === 'take-discard' && move.actionCard?.actionText) {
    return true;
  }

  // King declaring an action card
  if (move.type === 'use-action' && move.declaredRank) {
    return true;
  }

  // Direct use-action with targets
  if (move.type === 'use-action' && move.targets && move.targets.length > 0) {
    return true;
  }

  return false;
}
