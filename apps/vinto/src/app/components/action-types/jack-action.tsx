// components/action-types/JackAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { SkipButton, SwapButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';

export const JackAction = observer(() => {
  const gameClient = useGameClient();
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  const pendingAction = gameClient.visualState.pendingAction;
  if (!pendingAction) return null;

  const swapTargets = pendingAction.targets || [];
  const hasBothCards = swapTargets.length === 2;

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  const getPlayerName = (playerId: string) => {
    const player = gameClient.visualState.players.find(
      (p) => p.id === playerId
    );
    return player?.name || 'Unknown';
  };

  return (
    <div className="w-full h-full">
      <div className="bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-primary">
            🔄 Jack Action
            {isTossInAction && (
              <span className="ml-2 text-[10px] text-accent-primary font-medium">
                ⚡ Toss-in
              </span>
            )}
          </h3>
          <div className="flex items-center gap-2">
            <div className="text-2xs md:text-xs text-secondary">
              {swapTargets.length}/2 selected
            </div>
            <HelpPopover title="Swap Cards" rank="J" />
          </div>
        </div>

        {/* Instructions or Selected Cards Info */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {swapTargets.length === 0 ? (
            <p className="text-xs text-secondary text-center">
              Select any two cards to swap (from any players)
            </p>
          ) : swapTargets.length === 1 ? (
            <div className="text-xs text-secondary text-center">
              <div className="mb-1">First card selected:</div>
              <div className="text-sm font-medium text-primary">
                {getPlayerName(swapTargets[0].playerId)} • Position{' '}
                {swapTargets[0].position + 1}
              </div>
              <p className="text-2xs text-secondary mt-1">
                Select second card to swap
              </p>
            </div>
          ) : (
            <div className="text-xs text-secondary text-center">
              <div className="mb-1">Cards will be swapped:</div>
              <div className="text-sm font-medium text-primary">
                {swapTargets
                  .map(
                    (target) =>
                      `${getPlayerName(target.playerId)} Position ${
                        target.position + 1
                      }`
                  )
                  .join(' ⟷ ')}
              </div>
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="grid grid-cols-2 gap-1 flex-shrink-0">
          <SwapButton
            disabled={!hasBothCards}
            onClick={() => {
              if (!humanPlayer) return;
              gameClient.dispatch(GameActions.executeJackSwap(humanPlayer.id));
            }}
          />
          <SkipButton
            onClick={() => {
              if (!humanPlayer) return;
              gameClient.dispatch(GameActions.skipJackSwap(humanPlayer.id));
            }}
          />
        </div>
      </div>
    </div>
  );
});
