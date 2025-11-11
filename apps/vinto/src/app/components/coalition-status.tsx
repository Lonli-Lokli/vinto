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
      <div className="bg-surface-secondary/95 backdrop-blur-sm rounded-lg shadow-lg mx-2 my-1 px-3 py-2.5 border border-primary/20">
        {/* Header */}
        <div className="flex items-center justify-between mb-2.5">
          <div className="flex items-center gap-2">
            <Swords className="text-warning" size={16} />
            <span className="text-primary font-bold text-sm uppercase tracking-wide">
              Final Round
            </span>
          </div>
          {currentPlayer && (
            <div className="text-secondary text-sm">
              <span className="font-semibold text-primary">
                {currentPlayer.name}
              </span>
              {isHumanCoalitionMember && (
                <span className="text-tertiary text-xs ml-1">
                  (Leader decides)
                </span>
              )}
            </div>
          )}
        </div>

        {/* Recent Actions - only show if there are any */}
        {roundActions.length > 0 && (
          <div className="mb-2.5 bg-surface-tertiary/40 rounded px-2 py-1.5 border border-primary/10">
            <div className="text-tertiary text-xs font-semibold uppercase tracking-wide mb-1">
              Round Actions
            </div>
            <div className="space-y-0.5">
              {roundActions.map((action, idx) => (
                <div key={idx} className="text-secondary text-xs">
                  {action}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Teams display */}
        <div className="grid grid-cols-[1fr_auto_1fr] gap-3 items-start">
          {/* Coalition */}
          <div className="space-y-1.5">
            <div className="text-warning text-xs font-semibold uppercase tracking-wide mb-1.5">
              Coalition
            </div>

            {/* Leader */}
            <div className="flex items-center gap-2 bg-primary/5 rounded px-2 py-1.5 border-l-2 border-warning">
              <div className="w-6 h-6 flex-shrink-0">
                <Avatar playerName={coalitionLeader.name} size="sm" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-primary text-sm font-semibold truncate">
                  {coalitionLeader.name}
                </div>
                <div className="text-tertiary text-xs">Leader</div>
              </div>
            </div>

            {/* Members */}
            {coalitionMembers
              .filter((m) => m.id !== coalitionLeader.id)
              .map((member) => (
                <div
                  key={member.id}
                  className="flex items-center gap-2 bg-surface-tertiary/50 rounded px-2 py-1.5"
                >
                  <div className="w-6 h-6 flex-shrink-0">
                    <Avatar playerName={member.name} size="sm" />
                  </div>
                  <div className="text-primary text-sm truncate">
                    {member.name}
                  </div>
                </div>
              ))}
          </div>

          {/* VS */}
          <div className="flex items-center justify-center px-2">
            <div className="text-tertiary font-bold text-base">VS</div>
          </div>

          {/* Vinto */}
          <div className="space-y-1.5">
            <div className="text-error text-xs font-semibold uppercase tracking-wide mb-1.5">
              Vinto Caller
            </div>

            {vintoCaller && (
              <div className="flex items-center gap-2 bg-error/10 rounded px-2 py-1.5 border-l-2 border-error">
                <div className="w-6 h-6 flex-shrink-0">
                  <Avatar playerName={vintoCaller.name} size="sm" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-primary text-sm font-semibold truncate">
                    {vintoCaller.name}
                  </div>
                  <div className="text-tertiary text-xs">Solo</div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});
