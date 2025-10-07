// components/action-types/AceAction.tsx
'use client';

import { useActionStore, useGameStore, usePlayerStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';
import { OpponentSelectButton, SkipButton } from '../buttons';

export const AceAction = observer(() => {
  const actionStore = useActionStore();
  const gameStore = useGameStore();
  const playerStore = usePlayerStore();
  const [selectedOpponentId, setSelectedOpponentId] = React.useState<
    string | null
  >(null);

  if (!actionStore.actionContext) return null;

  const humanPlayer = playerStore.humanPlayer;
  const opponents = playerStore.players.filter((p) => p.id !== humanPlayer?.id);

  const handleOpponentClick = (opponentId: string) => {
    setSelectedOpponentId(opponentId);
    // Select the first card position (index 0) as a dummy - the action only cares about the player
    void gameStore.selectActionTarget(opponentId, 0);
  };

  return (
    <div className="w-full h-full px-2 py-1">
      <div className="bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Compact Header */}
        <div className="flex flex-row items-center justify-between mb-1.5 flex-shrink-0">
          <h3 className="text-xs font-semibold text-gray-800 leading-tight">
            🎯 Ace: Force opponent to draw
          </h3>
          <HelpPopover title="Ace Action" rank="A" />
        </div>

        {/* Opponent Selection - Horizontal Grid */}
        <div className="flex-1 flex flex-col justify-center min-h-0">
          <div className="grid grid-cols-3 gap-2">
            {opponents.map((opponent) => (
              <OpponentSelectButton
                key={opponent.id}
                opponentName={opponent.name}
                onClick={() => handleOpponentClick(opponent.id)}
                showAvatar={true}
                player={opponent}
                isSelected={selectedOpponentId === opponent.id}
              />
            ))}
          </div>
        </div>

        {/* Skip Button */}
        <div className="mt-2 flex-shrink-0">
          <SkipButton
            onClick={() => void gameStore.confirmPeekCompletion()}
            fullWidth
          />
        </div>
      </div>
    </div>
  );
});
