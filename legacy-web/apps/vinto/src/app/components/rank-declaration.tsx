'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from './presentational';
import { RankDeclarationButton } from './buttons';
import { Rank } from '@vinto/shapes';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { useUIStore } from './di-provider';
import { SkipDeclarationButton } from './buttons/skip-declaration';

// Only show action cards (7-K, A) since 2-6 and Joker have no actions
const ACTION_RANKS: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

export const RankDeclaration = observer(() => {
  const gameClient = useGameClient();
  const uiStore = useUIStore();

  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
  const pendingCard = gameClient.visualState.pendingAction?.card;
  const swapPosition = uiStore.selectedSwapPosition;

  // Show if user is selecting swap position and has selected a card
  if (
    !uiStore.isSelectingSwapPosition ||
    swapPosition === null ||
    swapPosition === undefined
  ) {
    return null;
  }

  // Only show for human players
  if (!humanPlayer) return null;

  const handleRankClick = (rank: Rank) => {
    if (!humanPlayer || swapPosition === null) return;
    gameClient.dispatch(
      GameActions.swapCard(humanPlayer.id, swapPosition, rank)
    );
    uiStore.cancelSwapSelection();
  };

  const handleJustSwap = () => {
    if (!humanPlayer || swapPosition === null) return;
    gameClient.dispatch(GameActions.swapCard(humanPlayer.id, swapPosition));
    uiStore.cancelSwapSelection();
  };

  const getHelpContent = () => {
    return `Current situation: You drew ${
      pendingCard?.rank
    } and are swapping with position ${(swapPosition ?? 0) + 1}

Your task: Declare what rank you think your position ${
      (swapPosition ?? 0) + 1
    } card is to use its action, or just swap without declaring.

✅ Correct declaration: Use the card's action
❌ Wrong declaration: Get penalty card
🔄 Just Swap: No action, no penalty

Note: 2-6 and Joker are not shown because they have no actions.`;
  };

  return (
    <div className="w-full h-full z-[100] relative">
      <div className="h-full bg-surface-primary border border-primary rounded-lg p-2 shadow-sm flex flex-col">
        {/* Header with help */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs font-semibold text-primary leading-tight">
            🎯 Declare Rank or Just Swap
          </h3>
          <HelpPopover title="Declare Rank" content={getHelpContent()} />
        </div>

        {/* Ranks grid - 4 columns = exactly 2 rows for 8 action cards */}
        <div className="grid grid-cols-4 gap-1 mb-1 flex-1 content-center min-h-0">
          {ACTION_RANKS.map((rank) => (
            <RankDeclarationButton
              key={rank}
              rank={rank}
              onClick={() => handleRankClick(rank)}
            />
          ))}
        </div>

        {/* Just Swap button */}
        <SkipDeclarationButton onClick={handleJustSwap}>
          Just Swap (No Declaration)
        </SkipDeclarationButton>
      </div>
    </div>
  );
});
