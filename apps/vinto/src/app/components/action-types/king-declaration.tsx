// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { KingActionCardButton, KingNonActionCardButton } from '../buttons';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';
import { HelpPopover } from '../presentational';

export function KingDeclaration() {
  const gameClient = useGameClient();
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);

  // K cannot declare itself - only the action cards that can be executed
  const actionCards = ['7', '8', '9', '10', 'J', 'Q', 'A'] as const;
  const nonActionCards = ['2', '3', '4', '5', '6', 'K', 'Joker'] as const;

  // Get the pending card (the King card being played)
  const pendingCard = gameClient.state.pendingAction?.card;

  // Function to check if a rank is disabled
  // King cannot declare itself (K), even though K appears in the UI
  const isRankDisabled = (rank: string): boolean => {
    return rank === 'K' && pendingCard?.rank === 'K';
  };

  return (
    <div className="w-full h-full py-1">
      <div className="bg-surface-primary border border-primary rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Compact header - single line */}
        <div className="flex flex-row items-center justify-between mb-1.5">
          <h3 className="text-xs font-semibold text-primary leading-tight">
            👑 King: Choose action
          </h3>
          <HelpPopover title="King Declaration" rank="K" />
        </div>

        {/* Single unified grid for all cards */}
        <div className="flex-1 flex flex-col justify-center">
          <div className="grid grid-cols-7 gap-1">
            {/* Action cards with visual distinction */}
            {actionCards.map((rank) => {
              const disabled = isRankDisabled(rank);
              return (
                <KingActionCardButton
                  key={rank}
                  rank={rank}
                  onClick={() => {
                    if (!humanPlayer) return;
                    gameClient.dispatch(
                      GameActions.declareKingAction(humanPlayer.id, rank)
                    );
                  }}
                  disabled={disabled}
                />
              );
            })}

            {/* Non-action cards in same grid */}
            {nonActionCards.map((rank) => {
              const disabled = isRankDisabled(rank);
              return (
                <KingNonActionCardButton
                  key={rank}
                  rank={rank}
                  onClick={() => {
                    if (!humanPlayer) return;
                    gameClient.dispatch(
                      GameActions.declareKingAction(humanPlayer.id, rank)
                    );
                  }}
                  disabled={disabled}
                />
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
