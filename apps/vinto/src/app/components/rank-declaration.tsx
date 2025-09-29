'use client';

import React, { useState } from 'react';
import { observer } from 'mobx-react-lite';
import { Popover } from 'react-tiny-popover';
import { gameStore } from '../stores/game-store';
import { Rank } from '../shapes';
import { ALL_RANKS } from '../lib/game-helpers';

export const RankDeclaration = observer(() => {
  const [showHelp, setShowHelp] = useState(false);

  if (!gameStore.isDeclaringRank || gameStore.swapPosition === null) {
    return null;
  }

  const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];
  if (!currentPlayer) return null;

  const handleRankClick = (rank: Rank) => {
    gameStore.declareRank(rank);
  };

  return (
    <div className="relative w-full max-w-4xl mx-auto px-2 mb-4" style={{ zIndex: 100 }}>
      <div className="bg-gradient-to-br from-yellow-50 to-orange-50 border border-yellow-300 rounded-lg p-4 md:p-6 shadow-sm overflow-visible">
        
        {/* Mobile Layout: 3 rows stacked */}
        <div className="block md:hidden">
          {/* Row 1: Header with help */}
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-base font-semibold text-yellow-800 flex items-center gap-1">
              🎯 Declare Rank
            </h3>
            <Popover
              isOpen={showHelp}
              positions={['top', 'bottom', 'left', 'right']}
              align='center'
              content={
                <div className="bg-white border border-yellow-300 rounded-lg p-3 shadow-2xl max-w-xs" style={{ zIndex: 9999 }}>
                  <div className="text-sm text-gray-700 space-y-2">
                    <p>
                      <strong>Current situation:</strong> You drew{' '}
                      <span className="font-semibold text-yellow-700">
                        {gameStore.pendingCard?.rank}
                      </span>{' '}
                      and are swapping with position{' '}
                      <span className="font-semibold text-yellow-700">
                        {(gameStore.swapPosition ?? 0) + 1}
                      </span>
                    </p>
                    <p>
                      <strong>Your task:</strong> Declare what rank you think your
                      position {(gameStore.swapPosition ?? 0) + 1} card is.
                    </p>
                    <div className="bg-yellow-50 rounded p-2 text-[10px]">
                      <div className="text-green-700">
                        ✅ <strong>Correct:</strong> Use {gameStore.pendingCard?.rank}&apos;s action
                      </div>
                      <div className="text-red-700">
                        ❌ <strong>Wrong:</strong> Get penalty card
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => setShowHelp(false)}
                    className="mt-2 text-sm text-gray-500 hover:text-gray-700"
                  >
                    Close
                  </button>
                </div>
              }
            >
              <button
                onClick={() => setShowHelp(!showHelp)}
                className="w-5 h-5 bg-yellow-200 hover:bg-yellow-300 rounded-full text-yellow-700 text-sm font-bold transition-colors"
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
                className="bg-yellow-400 hover:bg-yellow-500 text-white font-bold py-1.5 px-1 rounded text-sm transition-colors"
                title={`Declare ${rank}`}
              >
                {rank}
              </button>
            ))}
          </div>

          {/* Row 3: Skip */}
          <button
            onClick={() => gameStore.skipDeclaration()}
            className="w-full bg-gray-400 hover:bg-gray-500 text-white font-medium py-1.5 rounded text-sm transition-colors"
          >
            Skip Declaration
          </button>
        </div>

        {/* Desktop Layout: Utilize full height and width */}
        <div className="hidden md:block">
          {/* Header row */}
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xl font-semibold text-yellow-800 flex items-center gap-2">
              🎯 Declare Rank
            </h3>
            <Popover
              isOpen={showHelp}
              positions={['bottom', 'top', 'left', 'right']}
              content={
                <div className="bg-white border border-yellow-300 rounded-lg p-4 shadow-2xl max-w-sm" style={{ zIndex: 9999 }}>
                  <div className="text-base text-gray-700 space-y-2">
                    <p>
                      <strong>Current situation:</strong> You drew{' '}
                      <span className="font-semibold text-yellow-700">
                        {gameStore.pendingCard?.rank}
                      </span>{' '}
                      and are swapping with position{' '}
                      <span className="font-semibold text-yellow-700">
                        {(gameStore.swapPosition ?? 0) + 1}
                      </span>
                    </p>
                    <p>
                      <strong>Your task:</strong> Declare what rank you think your
                      position {(gameStore.swapPosition ?? 0) + 1} card is.
                    </p>
                    <div className="bg-yellow-50 rounded p-2 text-sm">
                      <div className="text-green-700">
                        ✅ <strong>Correct:</strong> Use {gameStore.pendingCard?.rank}&apos;s action
                      </div>
                      <div className="text-red-700">
                        ❌ <strong>Wrong:</strong> Get penalty card
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => setShowHelp(false)}
                    className="mt-3 text-base text-gray-500 hover:text-gray-700"
                  >
                    Close
                  </button>
                </div>
              }
            >
              <button
                onClick={() => setShowHelp(!showHelp)}
                className="w-8 h-8 bg-yellow-200 hover:bg-yellow-300 rounded-full text-yellow-700 text-lg font-bold transition-colors"
                title="Show help"
              >
                ?
              </button>
            </Popover>
          </div>

          {/* Ranks grid - using more vertical space */}
          <div className="grid grid-cols-7 gap-3 mb-4">
            {ALL_RANKS.map((rank) => (
              <button
                key={rank}
                onClick={() => handleRankClick(rank)}
                className="bg-yellow-400 hover:bg-yellow-500 text-white font-bold py-3 px-4 rounded-lg transition-colors text-lg min-h-[3rem]"
                title={`Declare ${rank}`}
              >
                {rank}
              </button>
            ))}
          </div>

          {/* Skip button row */}
          <button
            onClick={() => gameStore.skipDeclaration()}
            className="w-full bg-gray-400 hover:bg-gray-500 text-white font-medium py-3 rounded-lg transition-colors text-lg"
          >
            Skip Declaration
          </button>
        </div>


      </div>
    </div>
  );
});