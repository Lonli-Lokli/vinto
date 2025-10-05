// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { HelpPopover } from '../help-popover';
import { getButtonClasses } from '../../constants/button-colors';
import { useGameStore, useActionStore } from '../di-provider';

export function KingDeclaration() {
  const gameStore = useGameStore();
  const actionStore = useActionStore();

  // K cannot declare itself - only the action cards that can be executed
  const actionCards = ['7', '8', '9', '10', 'J', 'Q', 'A'] as const;
  const nonActionCards = ['2', '3', '4', '5', '6', 'K', 'Joker'] as const;

  // Get the pending card (the King card being played)
  const pendingCard = actionStore.pendingCard;

  // Function to check if a rank is disabled
  // King cannot declare itself (K), even though K appears in the UI
  const isRankDisabled = (rank: string): boolean => {
    return rank === 'K' && pendingCard?.rank === 'K';
  };

  // Get explanation for disabled state
  const getDisabledExplanation = (rank: string): string => {
    if (rank === 'K' && pendingCard?.rank === 'K') {
      return 'Cannot declare King from King card';
    }
    return '';
  };

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        <div className="text-center mb-2">
          <div className="flex flex-row items-center justify-center gap-2">
            <h3 className="text-xs md:text-sm font-semibold text-gray-800 leading-tight">
              👑 King Declaration
            </h3>
            <HelpPopover title="King Declaration" rank="K" />
          </div>
          <p className="text-xs md:text-sm text-gray-600 mt-1 leading-normal">
            Choose which card action to execute
          </p>
        </div>

        <div className="flex-1 flex flex-col justify-center space-y-2">
          {/* Action cards - primary focus */}
          <div>
            <div className="text-xs md:text-sm font-medium text-gray-600 mb-1 text-center leading-tight">
              Action Cards
            </div>
            <div className="grid grid-cols-4 md:grid-cols-7 gap-1 md:gap-2">
              {actionCards.map((rank) => {
                const disabled = isRankDisabled(rank);
                return (
                  <button
                    key={rank}
                    onClick={() => !disabled && gameStore.declareKingAction(rank)}
                    disabled={disabled}
                    className={`${getButtonClasses('king-action-card', disabled)} font-bold py-1.5 px-3 text-sm min-h-[44px] flex items-center justify-center`}
                    title={disabled ? getDisabledExplanation(rank) : `Execute ${rank} action`}
                  >
                    {rank}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Non-action cards - secondary, smaller */}
          <div>
            <div className="text-xs font-medium text-gray-500 mb-1 text-center leading-tight">
              No Action Cards
            </div>
            <div className="grid grid-cols-6 gap-1">
              {nonActionCards.map((rank) => {
                const disabled = isRankDisabled(rank);
                const explanation = getDisabledExplanation(rank);
                return (
                  <div key={rank} className="flex flex-col gap-0.5">
                    <button
                      onClick={() => !disabled && gameStore.declareKingAction(rank)}
                      disabled={disabled}
                      className={`${getButtonClasses('king-non-action-card', disabled)} font-medium py-1.5 px-2 text-xs flex items-center justify-center min-h-[44px]`}
                      title={disabled ? explanation : `Declare ${rank} (no action)`}
                    >
                      {rank}
                    </button>
                    {disabled && explanation && (
                      <div className="text-2xs text-gray-500 text-center italic px-1 leading-tight">
                        {explanation}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
