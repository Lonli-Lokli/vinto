// components/FinalScores.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown, Users } from 'lucide-react';
import { useGameStore, useGamePhaseStore, usePlayerStore } from './di-provider';
import { getWinnerInfo, calculateActualScore } from '../utils/game-helpers';
import { Avatar } from './avatar';
import { HelpPopover } from './help-popover';

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

  const getWinnerText = () => {
    if (winnerInfo.isCoalitionWin) {
      return `🎉 Coalition Victory!`;
    } else if (winnerInfo.isMultipleWinners) {
      return `🎉 Tie: ${winnerInfo.winners.join(', ')}`;
    } else {
      return `🎉 Winner: ${winnerInfo.winners[0]}`;
    }
  };

  const getHelpContent = () => {
    return `🏆 Final Scores & Scoring Rules

👑 Crown icon: Vinto caller
👥 Users icon: Coalition member

${hasCoalition ? `🤝 Coalition Game Scoring:
The Vinto caller's score is compared to the lowest Coalition score:

• If Vinto < Coalition lowest → Vinto +3 points; each Coalition −1
• If Coalition lowest < Vinto → Vinto −1; each Coalition +3  
• If tie → Vinto +3; Coalition 0

Displayed scores show the actual card totals from the round.` : 
`📊 Regular Game Scoring:
Scores reflect the total points from cards in each player's hand at round end.
Lower card totals are better during the round, but game points are awarded by final ranking.`}`;
  };

  return (
    <div className="w-full h-full px-2 py-1">
      <div className="h-full bg-white border border-gray-300 rounded-lg p-1.5 shadow-sm flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <div className="flex-1 min-w-0">
            <h3 className="text-xs md:text-sm font-semibold text-gray-800 leading-tight truncate">
              {getWinnerText()}
            </h3>
            {hasCoalition && (
              <div className="text-xs text-gray-500 leading-tight">
                {(() => {
                  const vintoScore = calculateActualScore(vintoCaller);
                  const coalitionLowest = winnerInfo.score;
                  
                  if (winnerInfo.isCoalitionWin) {
                    return `Coalition wins: Vinto ${vintoScore} vs Coalition lowest ${coalitionLowest}`;
                  } else {
                    return `Vinto wins: Vinto ${vintoScore} vs Coalition lowest ${coalitionLowest}`;
                  }
                })()}
              </div>
            )}
          </div>
          <HelpPopover title="Final Scores" content={getHelpContent()} />
        </div>

        {/* Scores layout - 2x2 grid with horizontal cards */}
        <div className="grid grid-cols-2 gap-1 flex-1 min-h-0">
          {players.map((player) => {
            const actualScore = calculateActualScore(player);
            const isWinner = winnerInfo.winnerIds?.includes(player.id);

            return (
              <div
                key={player.id}
                className={`flex items-center gap-2 px-2 py-1.5 rounded border transition-colors ${
                  actualScore === winnerInfo.score
                    ? 'bg-green-50 border-green-300 ring-1 ring-green-200'
                    : 'bg-gray-50 border-gray-200'
                }`}
              >
                {/* Avatar - clean without badges */}
                <div className="flex-shrink-0">
                  <Avatar player={player} size="sm" />
                </div>

                {/* Player info - flexible width with better overflow handling */}
                <div className="flex-1 min-w-0 mr-1">
                  <div className="text-xs font-medium text-gray-800 truncate">
                    {player.name}
                  </div>
                </div>

                {/* Score with role indicators - more compact */}
                <div className="flex-shrink-0 flex items-center gap-1">
                  {player.isVintoCaller && (
                    <Crown className="text-yellow-500 flex-shrink-0" size={16} />
                  )}
                  {player.coalitionWith.size > 0 && (
                    <Users className="text-blue-500 flex-shrink-0" size={16} />
                  )}
                  <div className={`text-sm font-bold leading-none min-w-[1.5rem] text-right ${
                    isWinner ? 'text-green-600' : 'text-gray-800'
                  }`}>
                    {actualScore}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
});
