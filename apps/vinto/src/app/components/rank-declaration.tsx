'use client';

import React, { useState } from 'react';
import { observer } from 'mobx-react-lite';
import { Popover } from 'react-tiny-popover';
import { gameStore } from '../stores/game-store';
import { Rank } from '../shapes';
import { ALL_RANKS } from '../lib/game-helpers';
import { getPlayerStore } from '../stores/player-store';
import { getActionStore } from '../stores/action-store';
import { getGamePhaseStore } from '../stores/game-phase-store';

export const RankDeclaration = observer(() => {
  const {currentPlayer} = getPlayerStore();
  const {pendingCard, swapPosition} = getActionStore();
  const {isDeclaringRank} = getGamePhaseStore();

  const [showHelp, setShowHelp] = useState(false);

  if (!isDeclaringRank || swapPosition === null) {
    return null;
  }

  if (!currentPlayer) return null;

  const handleRankClick = (rank: Rank) => {
    gameStore.declareRank(rank);
  };

  const helpContent = (isMobile: boolean) => (
    <div className={`bg-white border border-gray-300 rounded ${isMobile ? 'p-2 max-w-xs' : 'p-3 max-w-sm'} shadow-lg`} style={{ zIndex: 9999 }}>
      <div className={`${isMobile ? 'text-xs' : 'text-sm'} text-gray-700 space-y-2`}>
        <p>
          <strong>Current situation:</strong> You drew{' '}
          <span className="font-semibold text-gray-800">
            {pendingCard?.rank}
          </span>{' '}
          and are swapping with position{' '}
          <span className="font-semibold text-gray-800">
            {(swapPosition ?? 0) + 1}
          </span>
        </p>
        <p>
          <strong>Your task:</strong> Declare what rank you think your
          position {(swapPosition ?? 0) + 1} card is.
        </p>
        <div className={`bg-poker-green-50 rounded p-2 ${isMobile ? 'text-2xs' : 'text-xs'}`}>
          <div className="text-poker-green-700">
            ✅ <strong>Correct:</strong> Use {pendingCard?.rank}&apos;s action
          </div>
          <div className="text-amber-700">
            ❌ <strong>Wrong:</strong> Get penalty card
          </div>
        </div>
      </div>
      <button
        onClick={() => setShowHelp(false)}
        className={`${isMobile ? 'mt-2 text-xs' : 'mt-2 text-sm'} text-gray-500 hover:text-gray-700`}
      >
        Close
      </button>
    </div>
  );

  return (
    <div className="relative w-full max-w-4xl mx-auto px-2 mb-4" style={{ zIndex: 100 }}>
      <div className="bg-white border border-gray-300 rounded-lg p-3 shadow-sm overflow-visible">

        {/* Mobile Layout: 3 rows stacked */}
        <div className="block md:hidden">
          {/* Row 1: Header with help */}
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-1">
              🎯 Declare Rank
            </h3>
            <Popover
              isOpen={showHelp}
              positions={['top', 'bottom', 'left', 'right']}
              align='center'
              content={helpContent(true)}
            >
              <button
                onClick={() => setShowHelp(!showHelp)}
                className="w-5 h-5 bg-gray-200 hover:bg-gray-300 rounded-full text-gray-700 text-xs font-bold transition-colors"
                title="Show help"
              >
                ?
              </button>
            </Popover>
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
            className="w-full bg-gray-400 hover:bg-gray-500 text-white font-medium py-1.5 rounded text-xs transition-colors"
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
            <Popover
              isOpen={showHelp}
              positions={['bottom', 'top', 'left', 'right']}
              content={helpContent(false)}
            >
              <button
                onClick={() => setShowHelp(!showHelp)}
                className="w-6 h-6 bg-gray-200 hover:bg-gray-300 rounded-full text-gray-700 text-sm font-bold transition-colors"
                title="Show help"
              >
                ?
              </button>
            </Popover>
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