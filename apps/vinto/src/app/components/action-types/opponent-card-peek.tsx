// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { Player } from '../../shapes';

interface OpponentCardPeekProps {
  opponentPlayers: Player[];
  onCardClick: (playerId: string, position: number) => void;
}

export function OpponentCardPeek({ opponentPlayers, onCardClick }: OpponentCardPeekProps) {
  return (
    <div className="bg-orange-50 rounded-lg p-3 mb-3">
      <div className="text-center text-sm text-orange-800">
        👁️ <strong>Opponent Peek</strong>
        <br />
        <span className="text-xs text-orange-600">
          Select an opponent&apos;s card to reveal its value
        </span>
      </div>

      <div className="text-center">
        <p className="text-xs text-gray-500 mt-2 mb-3">
          Click on an opponent&apos;s card to peek at it
        </p>
      </div>

      {/* Show clickable opponent cards */}
      <div className="mt-3 space-y-2">
        {opponentPlayers.map(player => (
          <div key={player.id} className="text-center">
            <div className="text-xs font-semibold text-gray-700 mb-1">
              {player.name}
            </div>
            <div className="flex gap-1 justify-center">
              {player.cards.map((_, position) => (
                <button
                  key={position}
                  onClick={() => onCardClick(player.id, position)}
                  className="w-8 h-8 bg-blue-500 hover:bg-blue-600 text-white text-xs font-bold rounded border-2 border-blue-700 transition-colors"
                  title={`Peek ${player.name}'s card ${position + 1}`}
                >
                  {position + 1}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}