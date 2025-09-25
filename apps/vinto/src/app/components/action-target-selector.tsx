// components/ActionTargetSelector.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';

export function ActionTargetSelector() {
  const {
    isAwaitingActionTarget,
    actionContext,
    players,
    swapTargets,
    selectActionTarget,
    cancelAction
  } = useGameStore();

  if (!isAwaitingActionTarget || !actionContext) {
    return null;
  }

  const { action, playerId, targetType } = actionContext;
  const actionPlayer = players.find(p => p.id === playerId);
  const humanPlayer = players.find(p => p.isHuman);

  if (!actionPlayer || !humanPlayer) {
    return null;
  }

  const handleCardClick = (playerId: string, position: number) => {
    selectActionTarget(playerId, position);
  };

  // Get opponent players (non-human players for opponent targeting)
  const opponentPlayers = players.filter(p => !p.isHuman);

  return (
    <div className="mt-3 sm:mt-4 max-w-lg mx-auto px-2">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 sm:p-4 shadow-md">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-semibold text-gray-800">Execute Action</h3>
          <p className="text-xs text-gray-600 mt-1">
            <strong>{action}</strong>
          </p>
          {targetType === 'own-card' && (
            <p className="text-xs text-gray-500 mt-1">
              Click on one of your cards to peek at it
            </p>
          )}
          {targetType === 'opponent-card' && (
            <p className="text-xs text-gray-500 mt-1">
              Click on an opponent's card to peek at it
            </p>
          )}
          {targetType === 'swap-cards' && (
            <p className="text-xs text-gray-500 mt-1">
              Select two cards to swap {swapTargets.length > 0 && `(${swapTargets.length}/2 selected)`}
            </p>
          )}
        </div>

        {/* Action context info */}
        {targetType === 'own-card' && (
          <div className="bg-blue-50 rounded-lg p-3 mb-3">
            <div className="text-center text-sm text-blue-800">
              🔍 <strong>Peek Action</strong>
              <br />
              <span className="text-xs text-blue-600">
                Select a card from your hand to reveal its value
              </span>
            </div>
          </div>
        )}

        {targetType === 'opponent-card' && (
          <div className="bg-orange-50 rounded-lg p-3 mb-3">
            <div className="text-center text-sm text-orange-800">
              👁️ <strong>Opponent Peek</strong>
              <br />
              <span className="text-xs text-orange-600">
                Select an opponent's card to reveal its value
              </span>
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
                        onClick={() => handleCardClick(player.id, position)}
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
        )}

        {targetType === 'swap-cards' && (
          <div className="bg-purple-50 rounded-lg p-3 mb-3">
            <div className="text-center text-sm text-purple-800">
              🔄 <strong>Card Swap</strong>
              <br />
              <span className="text-xs text-purple-600">
                Select any two cards from the table to swap them
              </span>
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
                          onClick={() => handleCardClick(player.id, position)}
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
        )}

        {/* Cancel Button */}
        <button
          onClick={cancelAction}
          className="w-full bg-gradient-to-r from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white font-semibold py-2.5 px-4 rounded-lg shadow transition-colors"
          aria-label="Cancel action"
          title="Cancel action and pass turn"
        >
          Cancel Action
        </button>
      </div>
    </div>
  );
}