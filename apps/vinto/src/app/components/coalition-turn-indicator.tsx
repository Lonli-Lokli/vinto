// components/CoalitionTurnIndicator.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown, Users } from 'lucide-react';
import { useGameClient } from '@vinto/local-client';
import { Avatar } from './presentational';

export const CoalitionTurnIndicator = observer(() => {
  const gameClient = useGameClient();

  // Get current player and coalition leader
  const currentPlayer = gameClient.currentPlayer;
  const coalitionLeader = gameClient.coalitionLeader;

  // Check if coalition leader is playing
  const isCoalitionLeaderPlaying =
    gameClient.state.phase === 'playing' &&
    coalitionLeader &&
    currentPlayer.id === coalitionLeader.id;

  if (!isCoalitionLeaderPlaying || !currentPlayer || !coalitionLeader) {
    return null;
  }

  return (
    <div className="fixed top-20 left-1/2 transform -translate-x-1/2 z-40 animate-in fade-in slide-in-from-top duration-300">
      <div className="bg-coalition-gradient text-on-primary rounded-xl shadow-2xl px-6 py-3 border-2 border-coalition">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Crown className="text-yellow-300" size={20} />
            <div className="w-10 h-10">
              <Avatar playerName={coalitionLeader.name} size="sm" />
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
              <Avatar playerName={currentPlayer.name} size="sm" />
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
