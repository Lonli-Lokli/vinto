// components/FinalScores.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown, Users } from 'lucide-react';
import { HelpPopover, Avatar } from './presentational';
import { useGameClient } from '@vinto/local-client';
import { PlayerState } from '@vinto/shapes';
import { calculateFinalScores } from '@vinto/engine';

export const FinalScores = observer(() => {
  const gameClient = useGameClient();

  const phase = gameClient.state.phase;
  const vintoCallerId = gameClient.state.vintoCallerId;
  const players = gameClient.state.players;

  // Calculate final scores if in scoring phase
  const finalScores =
    phase === 'scoring'
      ? calculateFinalScores(players, vintoCallerId)
      : undefined;
  const winnerInfo = finalScores
    ? getWinnerInfo(finalScores, players)
    : undefined;

  if (phase !== 'scoring' || !winnerInfo || !finalScores) {
    return null;
  }

  // Get Vinto caller
  const vintoCaller = vintoCallerId
    ? players.find((p) => p.id === vintoCallerId)
    : null;
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

${
  hasCoalition
    ? `🤝 Coalition Game Scoring:
The Vinto caller's score is compared to the lowest Coalition score:

• If Vinto < Coalition lowest → Vinto +3 points; each Coalition −1
• If Coalition lowest < Vinto → Vinto −1; each Coalition +3  
• If tie → Vinto +3; Coalition 0

Displayed scores show the actual card totals from the round.`
    : `📊 Regular Game Scoring:
Scores reflect the total points from cards in each player's hand at round end.
Lower card totals are better during the round, but game points are awarded by final ranking.`
}`;
  };

  return (
    <div className="w-full h-full">
      <div className="h-full bg-surface-primary border border-primary rounded-lg p-1.5 shadow-theme-sm flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <div className="flex-1 min-w-0">
            <h3 className="text-xs md:text-sm font-semibold text-primary leading-tight truncate">
              {getWinnerText()}
            </h3>
            {hasCoalition && vintoCaller && (
              <div className="text-xs text-tertiary leading-tight">
                {(() => {
                  const vintoScore = calculateActualScore(vintoCaller.cards);
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
            const actualScore = calculateActualScore(player.cards);
            const isWinner = winnerInfo.winnerIds?.includes(player.id);
            const isVintoCaller = player.id === vintoCallerId;
            const isInCoalition = player.coalitionWith.length > 0;

            return (
              <div
                key={player.id}
                className={`flex items-center gap-2 px-2 py-1.5 rounded border transition-colors ${
                  actualScore === winnerInfo.score
                    ? 'bg-surface-tertiary border-success ring-2 ring-success'
                    : 'bg-surface-secondary border-secondary'
                }`}
              >
                {/* Avatar */}
                <div className="flex-shrink-0">
                  <Avatar playerName={player.name} size="sm" />
                </div>

                {/* Player name */}
                <div className="flex-1 min-w-0 mr-1">
                  <div className="text-xs font-medium text-primary truncate">
                    {player.name}
                  </div>
                </div>

                {/* Score with role indicators - more compact */}
                <div className="flex-shrink-0 flex items-center gap-1">
                  {isVintoCaller && (
                    <Crown className="text-warning flex-shrink-0" size={16} />
                  )}
                  {isInCoalition && (
                    <Users className="text-info flex-shrink-0" size={16} />
                  )}
                  <div
                    className={`text-sm font-bold leading-none min-w-[1.5rem] text-right ${
                      isWinner ? 'text-success' : 'text-primary'
                    }`}
                  >
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

// Helper to calculate actual card score
function calculateActualScore(cards: PlayerState['cards']): number {
  return cards.reduce((total, card) => total + card.value, 0);
}

// Helper to get winner info from PlayerState
function getWinnerInfo(
  finalScores: Record<string, number>,
  players: PlayerState[]
) {
  const lowestScore = Math.min(...Object.values(finalScores));
  const winnerIds = Object.keys(finalScores).filter(
    (id) => finalScores[id] === lowestScore
  );

  // Detect coalition win
  const hasCoalitionWinner = winnerIds.some((id) => {
    const player = players.find((p) => p.id === id);
    return player && player.coalitionWith.length > 0;
  });

  // Build winner names
  const winners: string[] = [];
  const processedIds = new Set<string>();

  winnerIds.forEach((winnerId) => {
    if (processedIds.has(winnerId)) return;

    const winner = players.find((p) => p.id === winnerId);
    if (!winner) return;

    if (winner.coalitionWith.length > 0) {
      // Coalition winner - group all coalition members who won
      const coalitionWinners = winnerIds.filter((id) => {
        const p = players.find((player) => player.id === id);
        return (
          p &&
          (p.id === winnerId ||
            winner.coalitionWith.includes(id) ||
            p.coalitionWith.includes(winnerId))
        );
      });

      coalitionWinners.forEach((id) => processedIds.add(id));
      const names = coalitionWinners
        .map((id) => players.find((p) => p.id === id)?.name || 'Unknown')
        .join(' & ');
      winners.push(names);
    } else {
      processedIds.add(winnerId);
      winners.push(winner.name);
    }
  });

  return {
    winners,
    winnerIds,
    score: lowestScore,
    isMultipleWinners: winnerIds.length > 1 && !hasCoalitionWinner,
    isCoalitionWin: hasCoalitionWinner,
  };
}
