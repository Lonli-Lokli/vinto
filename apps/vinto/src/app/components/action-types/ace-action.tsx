// components/action-types/AceAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';
import { OpponentSelectButton, SkipButton } from '../buttons';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';

export const AceAction = observer(() => {
  const gameClient = useGameClient();
  const [selectedOpponentId, setSelectedOpponentId] = React.useState<
    string | null
  >(null);

  if (!gameClient.state.pendingAction) return null;

  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const opponents = gameClient.state.players.filter(
    (p) => p.id !== humanPlayer?.id
  );

  const handleOpponentClick = (opponentId: string) => {
    if (!humanPlayer) return;
    setSelectedOpponentId(opponentId);
    // Select the first card position (index 0) as a dummy - the action only cares about the player
    gameClient.dispatch(
      GameActions.selectActionTarget(humanPlayer.id, opponentId, 0)
    );
  };

  return (
    <div className="w-full h-full py-1">
      <div className="bg-surface-primary/98 backdrop-blur-sm supports-[backdrop-filter]:bg-surface-primary/95 border border-primary rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Compact Header */}
        <div className="flex flex-row items-center justify-between mb-1.5 flex-shrink-0">
          <h3 className="text-xs font-semibold text-primary leading-tight">
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
            onClick={() => {
              if (!humanPlayer) return;
              gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
            }}
            fullWidth
          />
        </div>
      </div>
    </div>
  );
});
