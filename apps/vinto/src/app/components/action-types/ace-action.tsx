// components/action-types/AceAction.tsx
'use client';

import React from 'react';
import { Player } from '../../shapes';

interface AceActionProps {
  opponentPlayers: Player[];
  onCardClick: (playerId: string, position: number) => void;
}

export function AceAction({ opponentPlayers, onCardClick }: AceActionProps) {
  return (
    <div className="bg-red-50 rounded-lg p-3 mb-3">
      <div className="text-center text-sm text-red-800">
        🃁 <strong>Ace Action</strong>
        <br />
        <span className="text-xs text-red-600">
          Force an opponent to draw a penalty card
        </span>
      </div>

      <div className="text-center">
        <p className="text-xs text-gray-500 mt-2 mb-3">
          Choose an opponent to force them to draw a card
        </p>
      </div>

      {/* Show opponent players for selection */}
      <div className="mt-3 space-y-2">
        {opponentPlayers.map(player => (
          <button
            key={player.id}
            onClick={() => onCardClick(player.id, 0)} // Position 0 is ignored for force-draw
            className="w-full bg-gradient-to-r from-red-500 to-red-600 hover:from-red-600 hover:to-red-700 text-white font-semibold py-3 px-4 rounded-lg shadow transition-colors"
            title={`Force ${player.name} to draw a card`}
          >
            <div className="flex items-center justify-center gap-2">
              <span>{player.name}</span>
              <span className="text-xs bg-red-700 px-2 py-1 rounded">
                {player.cards.length} cards
              </span>
            </div>
          </button>
        ))}
      </div>

      {opponentPlayers.length === 0 && (
        <div className="mt-3 text-center text-sm text-red-600">
          No opponents available to target
        </div>
      )}
    </div>
  );
}