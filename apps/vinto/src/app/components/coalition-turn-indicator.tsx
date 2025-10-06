// components/CoalitionTurnIndicator.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown, Users } from 'lucide-react';
import { useGameStore, usePlayerStore } from './di-provider';
import { Avatar } from './avatar';

export const CoalitionTurnIndicator = observer(() => {
  const gameStore = useGameStore();
  const { currentPlayer, coalitionLeader } = usePlayerStore();

  if (
    !gameStore.isCoalitionLeaderPlaying ||
    !currentPlayer ||
    !coalitionLeader
  ) {
    return null;
  }

  return (
    <div className="fixed top-20 left-1/2 transform -translate-x-1/2 z-40 animate-in fade-in slide-in-from-top duration-300">
      <div className="bg-gradient-to-r from-blue-500 to-blue-600 text-white rounded-xl shadow-2xl px-6 py-3 border-2 border-blue-300">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Crown className="text-yellow-300" size={20} />
            <div className="w-10 h-10">
              <Avatar player={coalitionLeader} size="sm" />
            </div>
            <div>
              <div className="text-xs font-medium opacity-90">
                Coalition Leader
              </div>
              <div className="text-sm font-bold">{coalitionLeader.name}</div>
            </div>
          </div>

          <div className="text-xl font-bold opacity-75">→</div>

          <div className="flex items-center gap-2">
            <Users className="text-white" size={20} />
            <div className="w-10 h-10">
              <Avatar player={currentPlayer} size="sm" />
            </div>
            <div>
              <div className="text-xs font-medium opacity-90">Playing For</div>
              <div className="text-sm font-bold">{currentPlayer.name}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});
