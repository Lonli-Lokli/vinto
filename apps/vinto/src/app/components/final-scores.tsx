// components/FinalScores.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { getWinnerInfo } from '../lib/game-helpers';

export const FinalScores = observer(() => {
  // Calculate final scores if in scoring phase
  const finalScores =
    gameStore.phase === 'scoring'
      ? gameStore.calculateFinalScores()
      : undefined;
  const winnerInfo = finalScores
    ? getWinnerInfo(finalScores, gameStore.players)
    : undefined;
  if (gameStore.phase !== 'scoring' || !winnerInfo || !finalScores) {
    return null;
  }

  return (
    <div className="mt-4 sm:mt-6 mx-auto max-w-lg bg-white/95 backdrop-blur-sm border border-gray-200 rounded-2xl p-6 shadow-xl mx-2">
      <div className="text-center mb-4">
        <h2 className="text-2xl font-bold text-gray-800 mb-2">🏆 Game Over!</h2>
        <div className="text-lg">
          {winnerInfo.isMultipleWinners ? (
            <span className="text-green-600 font-semibold">
              Tie between: {winnerInfo.winners.join(', ')}
            </span>
          ) : (
            <span className="text-green-600 font-semibold">
              Winner: {winnerInfo.winners[0]}
            </span>
          )}
        </div>
        <div className="text-sm text-gray-600">
          with {winnerInfo.score} points
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {gameStore.players.map((player) => (
          <div
            key={player.id}
            className={`p-3 rounded-lg text-center ${
              winnerInfo.winners.includes(player.name)
                ? 'bg-green-100 border-2 border-green-300'
                : 'bg-gray-50 border border-gray-200'
            }`}
          >
            <div className="text-lg">{player.avatar}</div>
            <div className="font-medium text-gray-700">{player.name}</div>
            <div
              className={`text-xl font-bold ${
                winnerInfo.winners.includes(player.name)
                  ? 'text-green-600'
                  : 'text-gray-800'
              }`}
            >
              {finalScores[player.id]} pts
            </div>
          </div>
        ))}
      </div>
    </div>
  );
});
