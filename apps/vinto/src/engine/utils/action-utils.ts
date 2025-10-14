// engine/utils/action-utils.ts
// Utility functions for action handling

import { Rank, TargetType } from '@/shared';

/**
 * Determine the target type based on card rank
 * This defines what kind of target selection is required for each rank's action
 */
export function getTargetTypeFromRank(rank: Rank): TargetType | undefined {
  switch (rank) {
    case '7':
    case '8':
      return 'own-card';
    case '9':
    case '10':
      return 'opponent-card';
    case 'J':
      return 'swap-cards';
    case 'Q':
      return 'peek-then-swap';
    case 'K':
      return 'declare-action';
    case 'A':
      return 'force-draw';
    default:
      return undefined;
  }
}
