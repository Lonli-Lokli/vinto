// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { KingActionCardButton, KingNonActionCardButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';

export function KingDeclaration() {
  const gameClient = useGameClient();
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);

  // K can now be declared (King is allowed for declaring)
  const actionCards = ['7', '8', '9', '10', 'J', 'Q', 'A'] as const;
  const nonActionCards = ['2', '3', '4', '5', '6', 'K', 'Joker'] as const;

  // Get the pending action
  const targets = gameClient.state.pendingAction?.targets || [];
  const selectedTarget = targets[0];

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.state.activeTossIn &&
    gameClient.state.activeTossIn.queuedActions.length > 0;

  // Check if we're in the card selection phase or rank declaration phase
  // Step 1: targets.length === 0 → selecting card
  // Step 2: targets.length === 1 → declaring rank
  const isSelectingCard = targets.length === 0;
  const isDeclaringRank = targets.length === 1;

  if (isSelectingCard) {
    // Step 1: Show instructions to select a card from hand or opponent's hand
    return (
      <div className="w-full h-full">
        <div className="bg-surface-primary border border-primary rounded-lg p-2 shadow-sm h-full flex flex-col">
          {/* Header */}
          <div className="flex flex-row items-center justify-between mb-1.5">
            <h3 className="text-xs font-semibold text-primary leading-tight">
              👑 King: Select a card
              {isTossInAction && (
                <span className="ml-2 text-[10px] text-accent-primary font-medium">
                  ⚡ Toss-in
                </span>
              )}
            </h3>
            <HelpPopover title="King Declaration" rank="K" />
          </div>

          {/* Instructions */}
          <div className="flex-1 flex flex-col justify-center items-center text-center p-4">
            <p className="text-sm text-primary mb-2">
              Step 1: Select a card from your hand or an opponent&apos;s hand
            </p>
            <p className="text-xs text-gray-500">
              The card will be highlighted but not revealed yet
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (isDeclaringRank) {
    // Step 2: Show rank selection UI with selected card info
    return (
      <div className="w-full h-full">
        <div className="bg-surface-primary border border-primary rounded-lg p-2 shadow-sm h-full flex flex-col">
          {/* Header */}
          <div className="flex flex-row items-center justify-between mb-1.5">
            <h3 className="text-xs font-semibold text-primary leading-tight">
              👑 King: Declare rank
              {isTossInAction && (
                <span className="ml-2 text-[10px] text-accent-primary font-medium">
                  ⚡ Toss-in
                </span>
              )}
            </h3>
            <HelpPopover title="King Declaration" rank="K" />
          </div>

          {/* Selected card info */}
          {selectedTarget && (
            <div className="mb-2 text-xs text-gray-500 text-center">
              Card selected at position {selectedTarget.position + 1}
            </div>
          )}

          {/* Single unified grid for all cards */}
          <div className="flex-1 flex flex-col justify-center">
            <p className="text-xs text-primary mb-2 text-center">
              Step 2: Declare the rank you think it is
            </p>
            <div className="grid grid-cols-7 gap-1">
              {/* Action cards with visual distinction */}
              {actionCards.map((rank) => {
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
                    disabled={false}
                  />
                );
              })}

              {/* Non-action cards in same grid */}
              {nonActionCards.map((rank) => {
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
                    disabled={false}
                  />
                );
              })}
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Fallback (shouldn't reach here in normal flow)
  return (
    <div className="w-full h-full">
      <div className="bg-surface-primary border border-primary rounded-lg p-2 shadow-sm h-full flex flex-col">
        <div className="flex flex-row items-center justify-between mb-1.5">
          <h3 className="text-xs font-semibold text-primary leading-tight">
            👑 King Action
          </h3>
          <HelpPopover title="King Declaration" rank="K" />
        </div>
        <div className="flex-1 flex items-center justify-center">
          <p className="text-sm text-gray-500">Waiting...</p>
        </div>
      </div>
    </div>
  );
}
