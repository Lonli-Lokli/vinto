// components/action-types/CardSwap.tsx
'use client';

import React from 'react';
import { Player } from '../../shapes';

interface CardSwapProps {
  players: Player[];
  swapTargets: { playerId: string; position: number }[];
  onCardClick: (playerId: string, position: number) => void;
}

export function CardSwap({ players, swapTargets, onCardClick }: CardSwapProps) {
  return (
    <div className="bg-purple-50 rounded-lg p-3 mb-3">
      <div className="text-center text-sm text-purple-800">
        🔄 <strong>Card Swap</strong>
        <br />
        <span className="text-xs text-purple-600">
          Select any two cards from the table to swap them
        </span>
      </div>

      <div className="text-center">
        <p className="text-xs text-gray-500 mt-2 mb-3">
          Select two cards to swap {swapTargets.length > 0 && `(${swapTargets.length}/2 selected)`}
        </p>
      </div>

      {/* Show all players and their cards for selection */}
      <div className="mt-3 space-y-2">
        {players.map(player => (
          <div key={player.id} className="text-center">
            <div className="text-xs font-semibold text-gray-700 mb-1">
              {player.name} {player.isHuman && '(You)'}
            </div>
            <div className="flex gap-1 justify-center">
              {player.cards.map((_, position) => {
                const isSelected = swapTargets.some(
                  target => target.playerId === player.id && target.position === position
                );
                return (
                  <button
                    key={position}
                    onClick={() => onCardClick(player.id, position)}
                    className={`w-8 h-8 text-white text-xs font-bold rounded border-2 transition-colors ${
                      isSelected
                        ? 'bg-purple-600 border-purple-800'
                        : 'bg-purple-500 hover:bg-purple-600 border-purple-700'
                    }`}
                    title={`${isSelected ? 'Deselect' : 'Select'} ${player.name}'s card ${position + 1}`}
                  >
                    {position + 1}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}