// components/ActionTargetSelector.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';
import { Rank } from '../shapes';

export function ActionTargetSelector() {
  const {
    isAwaitingActionTarget,
    actionContext,
    players,
    swapTargets,
    peekTargets,
    selectActionTarget,
    executeQueenSwap,
    skipQueenSwap,
    declareKingAction,
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
              Click on an opponent&apos;s card to peek at it
            </p>
          )}
          {targetType === 'swap-cards' && (
            <p className="text-xs text-gray-500 mt-1">
              Select two cards to swap {swapTargets.length > 0 && `(${swapTargets.length}/2 selected)`}
            </p>
          )}
          {targetType === 'peek-then-swap' && (
            <p className="text-xs text-gray-500 mt-1">
              {peekTargets.length < 2
                ? `Select cards to peek at ${peekTargets.length > 0 ? `(${peekTargets.length}/2 selected)` : '(2 cards)'}`
                : 'Choose to swap the peeked cards or skip'
              }
            </p>
          )}
          {targetType === 'declare-action' && (
            <p className="text-xs text-gray-500 mt-1">
              Choose any card rank to execute its action
            </p>
          )}
          {targetType === 'force-draw' && (
            <p className="text-xs text-gray-500 mt-1">
              Choose an opponent to force them to draw a card
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
                Select an opponent&apos;s card to reveal its value
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

        {targetType === 'peek-then-swap' && (
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
                            onClick={() => handleCardClick(player.id, position)}
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
                    onClick={executeQueenSwap}
                    className="flex-1 bg-gradient-to-r from-pink-600 to-pink-700 hover:from-pink-700 hover:to-pink-800 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
                  >
                    Swap Cards
                  </button>
                  <button
                    onClick={skipQueenSwap}
                    className="flex-1 bg-gradient-to-r from-gray-500 to-gray-600 hover:from-gray-600 hover:to-gray-700 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
                  >
                    Skip Swap
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {targetType === 'declare-action' && (
          <div className="bg-yellow-50 rounded-lg p-3 mb-3">
            <div className="text-center text-sm text-yellow-800">
              👑 <strong>King Declaration</strong>
              <br />
              <span className="text-xs text-yellow-600">
                Choose any card rank to execute its action
              </span>
            </div>

            <div className="mt-3 grid grid-cols-3 gap-2">
              {/* Action cards with descriptions */}
              {[
                { rank: '7' as Rank, action: 'Peek own card', color: 'from-blue-500 to-blue-600' },
                { rank: '8' as Rank, action: 'Peek own card', color: 'from-blue-500 to-blue-600' },
                { rank: '9' as Rank, action: 'Peek opponent card', color: 'from-orange-500 to-orange-600' },
                { rank: '10' as Rank, action: 'Peek opponent card', color: 'from-orange-500 to-orange-600' },
                { rank: 'J' as Rank, action: 'Swap any 2 cards', color: 'from-purple-500 to-purple-600' },
                { rank: 'Q' as Rank, action: 'Peek 2 then swap', color: 'from-pink-500 to-pink-600' },
                { rank: 'K' as Rank, action: 'Declare action', color: 'from-yellow-500 to-yellow-600' },
                { rank: 'A' as Rank, action: 'Force draw', color: 'from-red-400 to-red-500' }
              ].map(({ rank, action, color }) => (
                <button
                  key={rank}
                  onClick={() => declareKingAction(rank)}
                  className={`bg-gradient-to-r ${color} hover:scale-105 text-white font-bold py-2 px-2 rounded-lg shadow transition-all text-xs`}
                  title={`Declare ${rank}: ${action}`}
                >
                  <div className="font-bold text-sm">{rank}</div>
                  <div className="text-xs opacity-90">{action}</div>
                </button>
              ))}
            </div>

            {/* Non-action cards (shown but disabled/grayed) */}
            <div className="mt-2 text-center">
              <div className="text-xs text-yellow-600 mb-1">No action cards:</div>
              <div className="flex gap-1 justify-center">
                {['2', '3', '4', '5', '6', 'Joker'].map((rank) => (
                  <button
                    key={rank}
                    disabled
                    className="bg-gray-300 text-gray-500 font-bold py-1 px-2 rounded text-xs cursor-not-allowed"
                    title={`${rank} has no action`}
                  >
                    {rank}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {targetType === 'force-draw' && (
          <div className="bg-red-50 rounded-lg p-3 mb-3">
            <div className="text-center text-sm text-red-800">
              🃁 <strong>Ace Action</strong>
              <br />
              <span className="text-xs text-red-600">
                Force an opponent to draw a penalty card
              </span>
            </div>

            {/* Show opponent players for selection */}
            <div className="mt-3 space-y-2">
              {opponentPlayers.map(player => (
                <button
                  key={player.id}
                  onClick={() => handleCardClick(player.id, 0)} // Position 0 is ignored for force-draw
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