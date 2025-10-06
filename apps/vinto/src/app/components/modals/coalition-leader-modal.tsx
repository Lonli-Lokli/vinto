// components/CoalitionLeaderModal.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown } from 'lucide-react';
import { useGamePhaseStore, useGameStore, usePlayerStore } from '../di-provider';
import { ContinueButton, OpponentSelectButton } from '../buttons';

export const CoalitionLeaderModal = observer(() => {
  const gameStore = useGameStore();
  const phaseStore = useGamePhaseStore();
  const { players } = usePlayerStore();

  if (!phaseStore.showCoalitionLeaderSelection) {
    return null;
  }

  // Get coalition members (everyone except Vinto caller)
  const coalitionMembers = players.filter((p) => !p.isVintoCaller);
  const vintoCaller = players.find((p) => p.isVintoCaller);

  const handleSelectLeader = (playerId: string) => {
    gameStore.setCoalitionLeader(playerId);
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full p-6 animate-in fade-in zoom-in duration-200">
        <div className="text-center mb-6">
          <div className="flex items-center justify-center gap-2 mb-2">
            <Crown className="text-yellow-500" size={32} />
            <h2 className="text-2xl font-bold text-gray-800">
              Select Coalition Leader
            </h2>
          </div>
          <p className="text-gray-600">
            {vintoCaller?.name} called Vinto! Choose who will lead the
            coalition.
          </p>
          <p className="text-sm text-gray-500 mt-2">
            The leader will see all coalition cards and play for the team.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4 mb-6">
          {coalitionMembers.map((player) => (
            <div
              key={player.id}
              className={`
                relative p-2 rounded-xl border-2 transition-all
                ${
                  player.isCoalitionLeader
                    ? 'border-yellow-500 bg-yellow-50'
                    : 'border-gray-300 bg-white'
                }
              `}
            >
              <OpponentSelectButton
                opponentName={`${player.name}${player.isHuman ? ' (You)' : ''}`}
                onClick={() => handleSelectLeader(player.id)}
                showAvatar={true}
                player={player}
                isSelected={player.isCoalitionLeader}
                className="w-full"
              />
              {player.isCoalitionLeader && (
                <div className="absolute top-0 right-0 flex items-center gap-1 bg-yellow-500 text-white text-xs font-semibold px-2 py-1 rounded-bl-lg rounded-tr-lg">
                  <Crown size={12} />
                  <span>Leader</span>
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="flex justify-center">
          <ContinueButton
            onClick={() => phaseStore.closeCoalitionLeaderSelection()}
            disabled={!coalitionMembers.some((p) => p.isCoalitionLeader)}
          >
            Confirm Leader
          </ContinueButton>
        </div>
      </div>
    </div>
  );
});
