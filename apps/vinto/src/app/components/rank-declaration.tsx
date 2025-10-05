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
import { ALL_RANKS } from '../utils/game-helpers';

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

✅ Correct: Use ${pendingCard?.rank}'s action
❌ Wrong: Get penalty card`;
  };

  return (
    <div className="w-full h-full px-3 py-2" style={{ zIndex: 100 }}>
      <div className="h-full bg-white border border-gray-300 rounded-lg p-3 shadow-sm overflow-visible flex flex-col">
        {/* Mobile Layout: 3 rows stacked */}
        <div className="block md:hidden">
          {/* Row 1: Header with help */}
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-1">
              🎯 Declare Rank
            </h3>
            <HelpPopover title="Declare Rank" content={getHelpContent()} />
          </div>

          {/* Row 2: Rank grid (6x2) */}
          <div className="grid grid-cols-6 gap-1 mb-2">
            {ALL_RANKS.map((rank) => (
              <button
                key={rank}
                onClick={() => handleRankClick(rank)}
                className="bg-amber-500 hover:bg-amber-600 text-white font-bold py-1.5 px-3 rounded text-sm transition-colors"
                title={`Declare ${rank}`}
              >
                {rank}
              </button>
            ))}
          </div>

          {/* Row 3: Skip */}
          <button
            onClick={() => gameStore.skipDeclaration()}
            className="w-full bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
          >
            Skip Declaration
          </button>
        </div>

        {/* Desktop Layout: Utilize full height and width */}
        <div className="hidden md:block">
          {/* Header row */}
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-base font-semibold text-gray-800 flex items-center gap-2">
              🎯 Declare Rank
            </h3>
            <HelpPopover title="Declare Rank" content={getHelpContent()} />
          </div>

          {/* Ranks grid - using more vertical space */}
          <div className="grid grid-cols-7 gap-2 mb-3">
            {ALL_RANKS.map((rank) => (
              <button
                key={rank}
                onClick={() => handleRankClick(rank)}
                className="bg-amber-500 hover:bg-amber-600 text-white font-bold py-2 px-4 rounded transition-colors text-base min-h-[2.5rem]"
                title={`Declare ${rank}`}
              >
                {rank}
              </button>
            ))}
          </div>

          {/* Skip button row */}
          <button
            onClick={() => gameStore.skipDeclaration()}
            className="w-full bg-gray-400 hover:bg-gray-500 text-white font-medium py-2 rounded transition-colors text-sm"
          >
            Skip Declaration
          </button>
        </div>
      </div>
    </div>
  );
});
