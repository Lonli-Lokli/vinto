// components/presentational/player-avatar.tsx
'use client';

import React from 'react';
import { Users, Crown } from 'lucide-react';
import { Avatar } from './avatar';

interface PlayerAvatarProps {
  playerName: string;
  isCurrentPlayer: boolean;
  isCoalitionMember: boolean;
  isCoalitionLeader: boolean;
}

export const PlayerAvatar: React.FC<PlayerAvatarProps> = ({
  playerName,
  isCurrentPlayer,
  isCoalitionMember,
  isCoalitionLeader,
}) => {
  const currentPlayerAnimation = isCurrentPlayer
    ? { animation: 'border-flash 1s ease-in-out infinite' }
    : undefined;

  return (
    <div className="flex flex-col items-center justify-center gap-1 md:gap-2">
      {/* Mobile: Combined avatar + name in rounded box */}
      <div
        className={`
          md:hidden
          flex items-center gap-2
          bg-surface-primary/95 backdrop-blur-sm
          px-2 py-1
          rounded-full
          shadow-theme-lg border-2
          ${isCurrentPlayer ? 'border-accent' : 'border-primary'}
        `}
        style={currentPlayerAnimation}
      >
        <div className="w-8 h-8">
          <Avatar playerName={playerName} size="sm" />
        </div>
        <div
          className={`
            text-xs
            font-extrabold
            ${isCurrentPlayer ? 'text-accent' : 'text-secondary'}
          `}
        >
          {playerName}
        </div>
        {isCoalitionMember && (
          <div
            className="bg-info text-white rounded-full p-0.5"
            title="Coalition Member"
          >
            <Users size={10} />
          </div>
        )}
        {isCoalitionLeader && (
          <div
            className="bg-warning text-white rounded-full p-0.5"
            title="Coalition Leader"
          >
            <Crown size={10} />
          </div>
        )}
      </div>

      {/* Desktop: Separate avatar and name */}
      <div className="hidden md:flex md:flex-col md:items-center md:justify-center md:gap-2">
        <div
          className={`
            w-32 h-32
            ${isCurrentPlayer ? 'scale-110' : 'scale-100'}
            transition-all duration-300
            drop-shadow-2xl
          `}
          style={{
            filter: isCurrentPlayer
              ? 'drop-shadow(0 0 10px rgba(16, 185, 129, 0.5))'
              : undefined,
          }}
        >
          <Avatar playerName={playerName} size="lg" />
        </div>

        <div className="flex flex-col items-center gap-2">
          <div
            className={`
              text-lg
              font-extrabold
              ${isCurrentPlayer ? 'text-accent' : 'text-secondary'}
              bg-surface-primary/95 backdrop-blur-sm
              px-4 py-2
              rounded-full
              shadow-theme-lg border-2
              ${isCurrentPlayer ? 'border-accent' : 'border-primary'}
            `}
            style={currentPlayerAnimation}
          >
            {playerName}
          </div>
          {isCoalitionMember && (
            <div
              className="flex items-center gap-1 bg-info text-white rounded-full px-3 py-1 text-sm font-semibold shadow-theme-md"
              title="Coalition Member"
            >
              <Users size={14} />
              <span>Team</span>
            </div>
          )}
          {isCoalitionLeader && (
            <div
              className="flex items-center gap-1 bg-warning text-white rounded-full px-3 py-1 text-sm font-semibold shadow-theme-md"
              title="Coalition Leader"
            >
              <Crown size={14} />
              <span>Leader</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
