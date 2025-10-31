// components/action-types/AceAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { OpponentSelectButton, SkipButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';
import { getCardName, getCardShortDescription } from '@vinto/shapes';

export const AceAction = observer(() => {
  const gameClient = useGameClient();
  const [selectedOpponentId, setSelectedOpponentId] = React.useState<
    string | null
  >(null);

  if (!gameClient.visualState.pendingAction) return null;

  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
  const opponents = gameClient.visualState.players.filter(
    (p) => p.id !== humanPlayer?.id
  );
  const action = gameClient.visualState.pendingAction.card.rank;

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  const handleOpponentClick = (opponentId: string) => {
    if (!humanPlayer) return;
    setSelectedOpponentId(opponentId);
    // Select the first card position (index 0) as a dummy - the action only cares about the player
    gameClient.dispatch(
      GameActions.selectAceActionTarget(humanPlayer.id, opponentId)
    );
  };

  return (
    <div className="h-full grid grid-cols-3 grid-rows-[auto_1fr_auto] gap-1.5">
      {/* Header - spans all 3 columns */}
      <div className="col-span-3 flex items-center justify-between">
        <div className="flex flex-col">
          <h3 className="text-xs font-semibold text-primary flex items-center leading-tight">
            🎯 {getCardName(action)}
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
        <HelpPopover title="Ace Action" rank="A" />
      </div>

      {/* Opponent Buttons - each in their own column */}
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

      {/* Skip Button - spans all 3 columns */}
      <div className="col-span-3">
        <SkipButton
          onClick={() => {
            if (!humanPlayer) return;
            gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
          }}
          className="w-full"
        />
      </div>
    </div>
  );
});
