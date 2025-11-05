// components/action-types/JackAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { SkipButton, SwapButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';
import { getCardShortDescription, getCardName } from '@vinto/shapes';
import { Card } from '../presentational/card';
import { ArrowRightLeft } from 'lucide-react';

export const JackAction = observer(() => {
  const gameClient = useGameClient();
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  const pendingAction = gameClient.visualState.pendingAction;
  if (!pendingAction) return null;

  const swapTargets = pendingAction.targets || [];
  const hasBothCards = swapTargets.length === 2;
  const action = pendingAction.card.rank;

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  // Extract target information
  const target1 = swapTargets[0];
  const target2 = swapTargets[1];

  const player1 = target1
    ? gameClient.visualState.players.find((p) => p.id === target1.playerId)
    : undefined;
  const player2 = target2
    ? gameClient.visualState.players.find((p) => p.id === target2.playerId)
    : undefined;

  // Jack cards are NOT revealed - they remain face down
  const card1Rank = undefined;
  const card2Rank = undefined;

  return (
    <>
      {/* Header */}
      <div className="flex items-center justify-between mb-1 flex-shrink-0">
        <div className="flex flex-col">
          <h3 className="text-xs font-semibold text-primary flex items-center leading-tight">
            🔄 {getCardName(action)}
            {isTossInAction && (
              <span className="ml-2 text-2xs text-accent-primary font-medium">
                ⚡ Toss-in
              </span>
            )}
          </h3>
          <span className="text-2xs text-secondary mt-0.5 ml-5 leading-tight">
            {getCardShortDescription(action)}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <div className="text-2xs text-secondary">{swapTargets.length}/2</div>
          <HelpPopover title="Swap Cards" rank="J" />
        </div>
      </div>

      {/* Instructions and Card Selection - split 1/3 and 2/3 */}
      <div className="flex items-center gap-3 mb-1 flex-1 min-h-0 w-full">
        {/* Instruction text - left 1/3 */}
        <div className="w-1/3 text-2xs text-secondary text-center">
          {swapTargets.length === 0
            ? 'Select any two cards to swap'
            : swapTargets.length === 1
            ? 'Select second card'
            : 'Cards will be swapped'}
        </div>

        {/* Cards - right 2/3 */}
        <div className="w-2/3 h-full flex items-center justify-center gap-3">
          {/* First target slot */}
          <div className="flex flex-col items-center">
            {target1 ? (
              <>
                <Card
                  rank={card1Rank}
                  revealed={false}
                  size="md"
                  selectionState="default"
                />
                <div className="mt-0.5 text-2xs font-medium text-primary truncate max-w-[80px]">
                  {player1?.name || 'Unknown'}
                </div>
                <div className="text-xs text-secondary">
                  Position {target1.position + 1}
                </div>
              </>
            ) : (
              <>
                <div className="w-6 h-9 border-2 border-dashed border-secondary/30 rounded flex items-center justify-center">
                  <span className="text-secondary/50 text-sm">?</span>
                </div>
                <div className="mt-0.5 text-2xs text-secondary">First</div>
              </>
            )}
          </div>

          {/* Swap arrow */}
          <div className="text-base text-secondary">
            <ArrowRightLeft />
          </div>

          {/* Second target slot */}
          <div className="flex flex-col items-center">
            {target2 ? (
              <>
                <Card
                  rank={card2Rank}
                  revealed={false}
                  size="md"
                  selectionState="default"
                />
                <div className="mt-0.5 text-2xs font-medium text-primary truncate max-w-[80px]">
                  {player2?.name || 'Unknown'}
                </div>
                <div className="text-xs text-secondary">
                  {' '}
                  Position {target2.position + 1}
                </div>
              </>
            ) : (
              <>
                <div className="w-6 h-9 border-2 border-dashed border-secondary/30 rounded flex items-center justify-center">
                  <span className="text-secondary/50 text-sm">?</span>
                </div>
                <div className="mt-0.5 text-2xs text-secondary">Second</div>
              </>
            )}
          </div>
        </div>
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
            if (hasBothCards) {
              gameClient.dispatch(GameActions.skipJackSwap(humanPlayer.id));
            } else {
              gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
            }
          }}
        />
      </div>
    </>
  );
});
