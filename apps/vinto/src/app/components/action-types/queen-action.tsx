// components/action-types/QueenAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { SwapButton, SkipButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';

export const QueenAction = observer(() => {
  const gameClient = useGameClient();
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  if (!gameClient.visualState.pendingAction) return null;

  const peekTargets = gameClient.visualState.pendingAction.targets || [];
  const hasBothCards = peekTargets.length === 2;

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  return (
    <div className="w-full h-full">
      <div className="bg-surface-primary/98 backdrop-blur-sm supports-[backdrop-filter]:bg-surface-primary/95 border border-primary rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex flex-row items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs font-semibold text-primary leading-tight">
            👑 Queen Action
            {isTossInAction && (
              <span className="ml-2 text-[10px] text-accent-primary font-medium">
                ⚡ Toss-in
              </span>
            )}
          </h3>
          <div className="flex flex-row items-center gap-1">
            <div className="text-xs text-secondary leading-tight">
              {peekTargets.length}/2 selected
            </div>
            <HelpPopover title="Queen Action" rank="Q" />
          </div>
        </div>

        {/* Instructions - only show when cards not yet selected */}
        <div className="flex-1 flex flex-col justify-center mb-1 min-h-0">
          {
            <div className="text-center space-y-1">
              <p className="text-xs text-primary font-medium leading-tight">
                👁️ Peek at two cards from different players
              </p>
              <p className="text-xs text-secondary leading-tight">
                One card may be yours, one must be from another player
              </p>
              <p className="text-xs text-secondary leading-tight">
                Then decide whether to swap them
              </p>
              {peekTargets.length === 1 && (
                <p className="text-xs text-success font-semibold leading-tight">
                  ✓ First card selected - choose a card from a different player
                </p>
              )}
              {peekTargets.length === 2 && (
                <p className="text-xs text-success font-semibold leading-tight">
                  ✓ Both cards selected - you can swap them
                </p>
              )}
            </div>
          }
        </div>

        {/* Action Buttons - only show when both cards selected */}
        {hasBothCards && (
          <div className="grid grid-cols-2 gap-1 flex-shrink-0">
            <SwapButton
              onClick={() => {
                if (!humanPlayer) return;
                gameClient.dispatch(
                  GameActions.executeQueenSwap(humanPlayer.id)
                );
              }}
            />
            <SkipButton
              onClick={() => {
                if (!humanPlayer) return;
                gameClient.dispatch(GameActions.skipQueenSwap(humanPlayer.id));
              }}
            />
          </div>
        )}
      </div>
    </div>
  );
});
