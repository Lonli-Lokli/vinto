// components/action-types/QueenAction.tsx
'use client';

import React from 'react';
import { Player } from '../../shapes';

interface QueenActionProps {
  players: Player[];
  peekTargets: { playerId: string; position: number; card?: any }[];
  onCardClick: (playerId: string, position: number) => void;
  onExecuteSwap: () => void;
  onSkipSwap: () => void;
}

export function QueenAction({
  players,
  peekTargets,
  onCardClick,
  onExecuteSwap,
  onSkipSwap
}: QueenActionProps) {
  return (
    <div className="bg-pink-50 rounded-lg p-3 mb-3">
      <div className="text-center text-sm text-pink-800">
        👑 <strong>Queen Action</strong>
        <br />
        <span className="text-xs text-pink-600">
          {peekTargets.length < 2
            ? 'Select any two cards to peek at their values'
            : 'Decide whether to swap the peeked cards'
          }
        </span>
      </div>

      <div className="text-center">
        <p className="text-xs text-gray-500 mt-2 mb-3">
          {peekTargets.length < 2
            ? `Select cards to peek at ${peekTargets.length > 0 ? `(${peekTargets.length}/2 selected)` : '(2 cards)'}`
            : 'Choose to swap the peeked cards or skip'
          }
        </p>
      </div>

      {/* Phase 1: Card Selection (if less than 2 cards selected) */}
      {peekTargets.length < 2 && (
        <div className="mt-3 space-y-2">
          {players.map(player => (
            <div key={player.id} className="text-center">
              <div className="text-xs font-semibold text-gray-700 mb-1">
                {player.name} {player.isHuman && '(You)'}
              </div>
              <div className="flex gap-1 justify-center">
                {player.cards.map((_, position) => {
                  const isSelected = peekTargets.some(
                    target => target.playerId === player.id && target.position === position
                  );
                  return (
                    <button
                      key={position}
                      onClick={() => onCardClick(player.id, position)}
                      className={`w-8 h-8 text-white text-xs font-bold rounded border-2 transition-colors ${
                        isSelected
                          ? 'bg-pink-600 border-pink-800'
                          : 'bg-pink-500 hover:bg-pink-600 border-pink-700'
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
      )}

      {/* Phase 2: Swap Decision (if 2 cards selected) */}
      {peekTargets.length === 2 && (
        <div className="mt-3">
          <div className="text-center mb-3">
            <div className="text-xs text-pink-700 mb-2">Peeked cards:</div>
            {peekTargets.map((target, index) => {
              const player = players.find(p => p.id === target.playerId);
              return (
                <div key={index} className="text-xs text-pink-600">
                  {player?.name} position {target.position + 1}: <strong>{target.card?.rank}</strong> (value {target.card?.value})
                </div>
              );
            })}
          </div>
          <div className="flex gap-2">
            <button
              onClick={onExecuteSwap}
              className="flex-1 bg-gradient-to-r from-pink-600 to-pink-700 hover:from-pink-700 hover:to-pink-800 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
            >
              Swap Cards
            </button>
            <button
              onClick={onSkipSwap}
              className="flex-1 bg-gradient-to-r from-gray-500 to-gray-600 hover:from-gray-600 hover:to-gray-700 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
            >
              Skip Swap
            </button>
          </div>
        </div>
      )}
    </div>
  );
}