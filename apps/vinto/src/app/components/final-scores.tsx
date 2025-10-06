// components/FinalScores.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown, Users } from 'lucide-react';
import { useGameStore, useGamePhaseStore, usePlayerStore } from './di-provider';
import { getWinnerInfo, calculateActualScore } from '../utils/game-helpers';
import { Avatar } from './avatar';

export const FinalScores = observer(() => {
  const gameStore = useGameStore();
  const gamePhaseStore = useGamePhaseStore();
  const playerStore = usePlayerStore();
  const { players } = playerStore;

  // Calculate final scores if in scoring phase
  const finalScores =
    gamePhaseStore.phase === 'scoring'
      ? gameStore.calculateFinalScores()
      : undefined;
  const winnerInfo = finalScores
    ? getWinnerInfo(finalScores, players)
    : undefined;

  if (gamePhaseStore.phase !== 'scoring' || !winnerInfo || !finalScores) {
    return null;
  }

  // Get Vinto caller and coalition members
  const vintoCaller = playerStore.vintoCaller;
  const hasCoalition = !!vintoCaller;

  return (
    <div className="mt-4 sm:mt-6 mx-auto max-w-lg bg-white/95 backdrop-blur-sm border border-gray-200 rounded-2xl p-6 shadow-lg mx-2">
      <div className="text-center mb-4">
        <h2 className="text-2xl font-bold text-gray-800 mb-2">🏆 Game Over!</h2>
        <div className="text-lg">
          {winnerInfo.isCoalitionWin ? (
            <div>
              <span className="text-green-600 font-semibold">
                Coalition Victory!
              </span>
              <div className="text-base mt-1">
                Team: {winnerInfo.winners.join(', ')}
              </div>
            </div>
          ) : winnerInfo.isMultipleWinners ? (
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

        {hasCoalition && (
          <div className="text-xs text-blue-600 mt-2">
            Coalition wins if ANY member beats the Vinto caller
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        {players.map((player) => {
          const actualScore = calculateActualScore(player);
          const displayScore = finalScores[player.id];
          const showBothScores = hasCoalition && actualScore !== displayScore;

          return (
            <div
              key={player.id}
              className={`p-3 rounded-lg text-center relative ${
                winnerInfo.winnerIds?.includes(player.id)
                  ? 'bg-green-100 border-2 border-green-300'
                  : 'bg-gray-50 border border-gray-200'
              }`}
            >
              {player.isVintoCaller && (
                <div className="absolute top-1 right-1">
                  <Crown className="text-yellow-500" size={16} />
                </div>
              )}
              {player.coalitionWith.size > 0 && (
                <div className="absolute top-1 left-1">
                  <Users className="text-blue-500" size={16} />
                </div>
              )}

              <Avatar player={player} />
              <div className="font-medium text-gray-700">{player.name}</div>

              {showBothScores ? (
                <div>
                  <div
                    className={`text-xl font-bold ${
                      winnerInfo.winners.includes(player.name)
                        ? 'text-green-600'
                        : 'text-gray-800'
                    }`}
                  >
                    {displayScore} pts
                  </div>
                  <div className="text-xs text-gray-500">
                    (Actual: {actualScore})
                  </div>
                </div>
              ) : (
                <div
                  className={`text-xl font-bold ${
                    winnerInfo.winners.includes(player.name)
                      ? 'text-green-600'
                      : 'text-gray-800'
                  }`}
                >
                  {displayScore} pts
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
});
