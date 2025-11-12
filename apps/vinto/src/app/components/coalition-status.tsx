// components/CoalitionStatus.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Swords } from 'lucide-react';
import { useGameClient } from '@vinto/local-client';
import { Avatar } from './presentational';

/**
 * Coalition Status Display - Shows in bottom area during final phase
 * Displays coalition formation: Leader + Members vs Vinto Caller
 * Also shows recent actions from all players
 */
export const CoalitionStatus = observer(() => {
  const gameClient = useGameClient();
    // Get all recent actions from the current turn
  const roundActions = React.useMemo(() => {
    const allActions = gameClient.visualState.roundActions || [];
    return allActions.map(
      (action) => `${action.playerName} ${action.description}`
    );
  }, [gameClient.visualState.roundActions]);
  

  const phase = gameClient.visualState.phase;
  const vintoCallerId = gameClient.visualState.vintoCallerId;
  const coalitionLeader = gameClient.coalitionLeader;
  const currentPlayer = gameClient.currentPlayer;
  const players = gameClient.visualState.players;



  const vintoCaller = players.find((p) => p.id === vintoCallerId);
  const coalitionMembers = players.filter(
    (p) => p.id !== vintoCallerId && !p.isVintoCaller
  );

      // Only show during final phase when coalition exists
  if (phase !== 'final' || !coalitionLeader || !vintoCallerId) {
    return null;
  }

  const isCurrentPlayerLeader = currentPlayer?.id === coalitionLeader.id;
  const isCurrentPlayerVinto = currentPlayer?.id === vintoCallerId;
  const isHumanCoalitionMember =
    currentPlayer?.isHuman &&
    !isCurrentPlayerLeader &&
    !isCurrentPlayerVinto &&
    coalitionMembers.some((m) => m.id === currentPlayer?.id);




  return (
    <div className="absolute top-0 left-0 right-0 z-10">
      <div className="bg-surface-secondary/95 backdrop-blur-sm rounded shadow-lg mx-2 my-0.5 px-1.5 py-1 border border-primary/20">
        {/* Header */}
        <div className="flex items-center justify-between mb-1">
          <div className="flex items-center gap-1">
            <Swords className="text-warning" size={12} />
            <span className="text-primary font-bold text-xs uppercase tracking-wide">
              Final Round ({roundActions.length} actions)
            </span>
          </div>
          {currentPlayer && (
            <div className="text-secondary text-xs">
              <span className="font-semibold text-primary">
                {currentPlayer.name}
              </span>
              {isHumanCoalitionMember && (
                <span className="text-tertiary text-2xs ml-1">
                  (Leader decides)
                </span>
              )}
            </div>
          )}
        </div>

        {/* Recent Actions - only show if there are any */}
        {roundActions.length > 0 && (
          <div className="mb-1 bg-surface-tertiary/40 rounded px-1.5 py-0.5 border border-primary/10">
            <div className="max-h-10 overflow-y-auto space-y-0.5 pr-1">
              {roundActions.map((action, idx) => (
                <div key={idx} className="text-secondary text-2xs leading-tight">
                  {action}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Teams display */}
        <div className="grid grid-cols-[1fr_auto_1fr] gap-1.5 items-start">
          {/* Coalition */}
          <div className="space-y-0.5">
            <div className="text-warning text-2xs font-semibold uppercase tracking-wide mb-0.5">
              Coalition
            </div>

            {/* Leader */}
            <div className="flex items-center gap-1 bg-primary/5 rounded px-1 py-0.5 border-l-2 border-warning">
              <div className="w-4 h-4 flex-shrink-0 overflow-hidden rounded-full">
                <Avatar playerName={coalitionLeader.name} size="sm" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-primary text-xs font-semibold truncate leading-tight">
                  {coalitionLeader.name}
                </div>
                <div className="text-tertiary text-3xs leading-tight">Leader</div>
              </div>
            </div>

            {/* Members */}
            {coalitionMembers
              .filter((m) => m.id !== coalitionLeader.id)
              .map((member) => (
                <div
                  key={member.id}
                  className="flex items-center gap-1 bg-surface-tertiary/50 rounded px-1 py-0.5"
                >
                  <div className="w-4 h-4 flex-shrink-0 overflow-hidden rounded-full">
                    <Avatar playerName={member.name} size="xs" />
                  </div>
                  <div className="text-primary text-xs truncate leading-tight">
                    {member.name}
                  </div>
                </div>
              ))}
          </div>

          {/* VS */}
          <div className="flex items-center justify-center px-0.5">
            <div className="text-tertiary font-bold text-xs">VS</div>
          </div>

          {/* Vinto */}
          <div className="space-y-0.5">
            <div className="text-error text-2xs font-semibold uppercase tracking-wide mb-0.5">
              Vinto Caller
            </div>

            {vintoCaller && (
              <div className="flex items-center gap-1 bg-error/10 rounded px-1 py-0.5 border-l-2 border-error">
                <div className="w-4 h-4 flex-shrink-0 overflow-hidden rounded-full">
                  <Avatar playerName={vintoCaller.name} size="md" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-primary text-xs font-semibold truncate leading-tight">
                    {vintoCaller.name}
                  </div>
                  <div className="text-tertiary text-3xs leading-tight">Solo</div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});
