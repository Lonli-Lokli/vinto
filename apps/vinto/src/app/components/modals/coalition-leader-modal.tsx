// components/CoalitionLeaderModal.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown } from 'lucide-react';
import { useUIStore } from '../di-provider';
import { ContinueButton, OpponentSelectButton } from '../buttons';
import { GameActions } from '@/engine';
import { useGameClient } from '@/client';

export const CoalitionLeaderModal = observer(() => {
  const gameClient = useGameClient();
  const uiStore = useUIStore();

  if (!uiStore.showCoalitionLeaderSelection) {
    return null;
  }

  // Get coalition members (everyone except Vinto caller)
  const players = gameClient.state.players;
  const coalitionMembers = players.filter((p) => !p.isVintoCaller);
  const vintoCaller = players.find((p) => p.isVintoCaller);
  const coalitionLeaderId = gameClient.state.coalitionLeaderId;

  const handleSelectLeader = (playerId: string) => {
    gameClient.dispatch(GameActions.setCoalitionLeader(playerId));
  };

  return (
    <div className="fixed inset-0 bg-overlay backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-surface-primary rounded-2xl shadow-2xl max-w-2xl w-full p-6 animate-in fade-in zoom-in duration-200">
        <div className="text-center mb-6">
          <div className="flex items-center justify-center gap-2 mb-2">
            <Crown className="text-warning" size={32} />
            <h2 className="text-2xl font-bold text-primary">
              Select Coalition Leader
            </h2>
          </div>
          <p className="text-secondary">
            {vintoCaller?.name} called Vinto! Choose who will lead the
            coalition.
          </p>
          <p className="text-sm text-secondary mt-2">
            The leader will see all coalition cards and play for the team.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4 mb-6">
          {coalitionMembers.map((player) => {
            const isCoalitionLeader = coalitionLeaderId === player.id;
            return (
              <div
                key={player.id}
                className={`
                  relative p-2 rounded-xl border-2 transition-all
                  ${
                    isCoalitionLeader
                      ? 'border-warning bg-warning-light'
                      : 'border-primary bg-surface-primary'
                  }
                `}
              >
                <OpponentSelectButton
                  opponentName={`${player.name}${
                    player.isHuman ? ' (You)' : ''
                  }`}
                  onClick={() => handleSelectLeader(player.id)}
                  showAvatar={true}
                  player={player}
                  isSelected={isCoalitionLeader}
                  className="w-full"
                />
                {isCoalitionLeader && (
                  <div className="absolute top-0 right-0 flex items-center gap-1 bg-warning text-white text-xs font-semibold px-2 py-1 rounded-bl-lg rounded-tr-lg">
                    <Crown size={12} />
                    <span>Leader</span>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div className="flex justify-center">
          <ContinueButton
            onClick={() => uiStore.closeCoalitionLeaderSelection()}
            disabled={!coalitionLeaderId}
          >
            Confirm Leader
          </ContinueButton>
        </div>
      </div>
    </div>
  );
});
