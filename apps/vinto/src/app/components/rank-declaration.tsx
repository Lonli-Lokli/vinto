'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from './help-popover';
import {
  useGameStore,
  usePlayerStore,
  useActionStore,
  useGamePhaseStore,
} from './di-provider';
import { Rank } from '../shapes';

// Only show action cards (7-K, A) since 2-6 and Joker have no actions
const ACTION_RANKS: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

export const RankDeclaration = observer(() => {
  const gameStore = useGameStore();
  const { currentPlayer } = usePlayerStore();
  const { pendingCard, swapPosition } = useActionStore();
  const { isDeclaringRank } = useGamePhaseStore();

  if (!isDeclaringRank || swapPosition === null) {
    return null;
  }

  // Only show for human players - bot declarations should not display UI
  if (!currentPlayer || currentPlayer.isBot) return null;

  const handleRankClick = (rank: Rank) => {
    gameStore.declareRank(rank);
  };

  const getHelpContent = () => {
    return `Current situation: You drew ${
      pendingCard?.rank
    } and are swapping with position ${(swapPosition ?? 0) + 1}

Your task: Declare what rank you think your position ${
      (swapPosition ?? 0) + 1
    } card is.

✅ Correct: Use the card's action
❌ Wrong: Get penalty card

Note: 2-6 and Joker are not shown because they have no actions. Declaring them correctly provides no benefit, while declaring them incorrectly still results in a penalty.`;
  };

  return (
    <div className="w-full h-full px-2 py-1.5 z-[100] relative">
      <div className="h-full bg-white border border-gray-300 rounded-lg p-2 shadow-sm flex flex-col">
        {/* Header with help */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs font-semibold text-gray-800 leading-tight">
            🎯 Declare Rank
          </h3>
          <HelpPopover title="Declare Rank" content={getHelpContent()} />
        </div>

        {/* Ranks grid - 4 columns = exactly 2 rows for 8 action cards */}
        <div className="grid grid-cols-4 gap-1 mb-1 flex-1 content-start min-h-0">
          {ACTION_RANKS.map((rank) => (
            <button
              key={rank}
              onClick={() => handleRankClick(rank)}
              className="bg-amber-500 hover:bg-amber-600 active:bg-amber-700 text-white font-bold py-1.5 px-1 rounded text-xs transition-colors min-h-[36px] flex items-center justify-center"
              title={`Declare ${rank}`}
            >
              {rank}
            </button>
          ))}
        </div>

        {/* Skip button - compact */}
        <button
          onClick={() => gameStore.skipDeclaration()}
          className="w-full bg-slate-600 hover:bg-slate-700 active:bg-slate-800 text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-xs min-h-[36px] flex-shrink-0"
        >
          Skip
        </button>
      </div>
    </div>
  );
});
