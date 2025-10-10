'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from './help-popover';
import { RankDeclarationButton, SkipButton } from './buttons';
import { Rank } from '@/shared';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';

// Only show action cards (7-K, A) since 2-6 and Joker have no actions
const ACTION_RANKS: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

export const RankDeclaration = observer(() => {
  const gameClient = useGameClient();

  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const pendingCard = gameClient.state.pendingAction?.card;
  const swapPosition = gameClient.state.pendingAction?.swapPosition;
  const isDeclaringRank = gameClient.state.subPhase === 'declaring_rank';

  if (!isDeclaringRank || swapPosition === null || swapPosition === undefined) {
    return null;
  }

  // Only show for human players - bot declarations should not display UI
  if (!humanPlayer) return null;

  const handleRankClick = (rank: Rank) => {
    if (!humanPlayer) return;
    gameClient.dispatch(
      GameActions.swapCard(humanPlayer.id, swapPosition, rank)
    );
  };

  const getHelpContent = () => {
    return `Current situation: You drew ${
      pendingCard?.rank
    } and are swapping with position ${(swapPosition ?? 0) + 1}

Your task: Declare what rank you think your position ${
      (swapPosition ?? 0) + 1
    } card is.

✅ Correct: Use the card's action
❌ Wrong: Get penalty card

Note: 2-6 and Joker are not shown because they have no actions. Declaring them correctly provides no benefit, while declaring them incorrectly still results in a penalty.`;
  };

  return (
    <div className="w-full h-full py-1.5 z-[100] relative">
      <div className="h-full bg-surface-primary border border-primary rounded-lg p-2 shadow-sm flex flex-col">
        {/* Header with help */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs font-semibold text-primary leading-tight">
            🎯 Declare Rank
          </h3>
          <HelpPopover title="Declare Rank" content={getHelpContent()} />
        </div>

        {/* Ranks grid - 4 columns = exactly 2 rows for 8 action cards */}
        <div className="grid grid-cols-4 gap-1 mb-1 flex-1 content-start min-h-0">
          {ACTION_RANKS.map((rank) => (
            <RankDeclarationButton
              key={rank}
              rank={rank}
              onClick={() => handleRankClick(rank)}
            />
          ))}
        </div>

        {/* Skip button - compact */}
        <SkipButton
          onClick={() => {
            if (!humanPlayer) return;
            gameClient.dispatch(
              GameActions.swapCard(humanPlayer.id, swapPosition)
            );
          }}
          className="w-full py-1.5 px-3 flex-shrink-0"
        >
          Skip
        </SkipButton>
      </div>
    </div>
  );
});
