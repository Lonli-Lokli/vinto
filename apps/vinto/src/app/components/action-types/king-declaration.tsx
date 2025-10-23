// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { KingCardButton, SkipButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';
import { getCardShortDescription, getCardName } from '@vinto/shapes';

export function KingDeclaration() {
  const gameClient = useGameClient();
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  if (!gameClient.visualState.pendingAction) return null;
  // Get the pending action
  const targets = gameClient.visualState.pendingAction?.targets || [];
  const selectedTarget = targets[0];
  const action = gameClient.visualState.pendingAction.card.rank;

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

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
            <div className="flex flex-col">
              <h3 className="text-xs font-semibold text-primary leading-tight flex items-center">
                👑 {getCardName(action)}
                {isTossInAction && (
                  <span className="ml-2 text-[10px] text-accent-primary font-medium">
                    ⚡ Toss-in
                  </span>
                )}
              </h3>
              <span className="text-[10px] text-secondary mt-0.5 ml-5">{getCardShortDescription(action)}</span>
            </div>
            <HelpPopover title="King Declaration" rank="K" />
          </div>

          {/* Instructions */}
          <div className="flex-1 flex flex-col justify-center items-center text-center p-4">
            <p className="text-sm text-primary mb-2">
              Select a card from your hand or an opponent&apos;s hand
            </p>
            <p className="text-xs text-gray-500">
              The card will be highlighted but not revealed yet
            </p>
          </div>
          {
            <div className="flex-shrink-0">
              <SkipButton
                onClick={() => {
                  if (!humanPlayer) return;
                  gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
                }}
                className="w-full py-1.5 px-4 text-sm"
              />
            </div>
          }
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
            <div className="flex flex-col">
              <h3 className="text-xs font-semibold text-primary leading-tight flex items-center">
                👑 {getCardName(action)}
                {isTossInAction && (
                  <span className="ml-2 text-[10px] text-accent-primary font-medium">
                    ⚡ Toss-in
                  </span>
                )}
              </h3>
              <span className="text-[10px] text-secondary mt-0.5 ml-5">{getCardShortDescription(action)}</span>
            </div>
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
              Declare the rank you think it is
            </p>
            <div className="grid grid-cols-7 gap-1">
              {/* Action cards with visual distinction */}
              {firstRowCards.map((item) => {
                return (
                  <KingCardButton
                    key={item.rank}
                    rank={item.rank}
                    actionable={item.actionable}
                    onClick={() => {
                      if (!humanPlayer) return;
                      gameClient.dispatch(
                        GameActions.declareKingAction(humanPlayer.id, item.rank)
                      );
                    }}
                  />
                );
              })}

              {/* Non-action cards in same grid */}
              {secondRowCards.map((item) => {
                return (
                  <KingCardButton
                    key={item.rank}
                    rank={item.rank}
                    actionable={item.actionable}
                    onClick={() => {
                      if (!humanPlayer) return;
                      gameClient.dispatch(
                        GameActions.declareKingAction(humanPlayer.id, item.rank)
                      );
                    }}
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
          <div className="flex flex-col">
            <h3 className="text-xs font-semibold text-primary leading-tight flex items-center">
              👑 {getCardName(action)}
            </h3>
            <span className="text-[10px] text-secondary mt-0.5 ml-5">{getCardShortDescription(action)}</span>
          </div>
          <HelpPopover title="King Declaration" rank="K" />
        </div>
        <div className="flex-1 flex items-center justify-center">
          <p className="text-sm text-gray-500">Thinking...</p>
        </div>
      </div>
    </div>
  );
}

// K can now be declared (King is allowed for declaring)
const firstRowCards = [
  { rank: '7' as const, actionable: true },
  { rank: '8' as const, actionable: true },
  { rank: '9' as const, actionable: true },
  { rank: '10' as const, actionable: true },
  { rank: 'J' as const, actionable: true },
  { rank: 'Q' as const, actionable: true },
  { rank: 'K' as const, actionable: true },
];
const secondRowCards = [
  { rank: 'A' as const, actionable: true },
  { rank: '2' as const, actionable: false },
  { rank: '3' as const, actionable: false },
  { rank: '4' as const, actionable: false },
  { rank: '5' as const, actionable: false },
  { rank: '6' as const, actionable: false },
  { rank: 'Joker' as const, actionable: false },
];
