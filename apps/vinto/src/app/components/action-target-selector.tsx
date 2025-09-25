// components/ActionTargetSelector.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';
import { OwnCardPeek } from './action-types/own-card-peek';
import { OpponentCardPeek } from './action-types/opponent-card-peek';
import { CardSwap } from './action-types/card-swap';
import { QueenAction } from './action-types/queen-action';
import { KingDeclaration } from './action-types/king-declaration';
import { AceAction } from './action-types/ace-action';

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

  const renderActionContent = () => {
    switch (targetType) {
      case 'own-card':
        return <OwnCardPeek />;

      case 'opponent-card':
        return (
          <OpponentCardPeek
            opponentPlayers={opponentPlayers}
            onCardClick={handleCardClick}
          />
        );

      case 'swap-cards':
        return (
          <CardSwap
            players={players}
            swapTargets={swapTargets}
            onCardClick={handleCardClick}
          />
        );

      case 'peek-then-swap':
        return (
          <QueenAction
            players={players}
            peekTargets={peekTargets}
            onCardClick={handleCardClick}
            onExecuteSwap={executeQueenSwap}
            onSkipSwap={skipQueenSwap}
          />
        );

      case 'declare-action':
        return (
          <KingDeclaration
            onDeclareAction={declareKingAction}
          />
        );

      case 'force-draw':
        return (
          <AceAction
            opponentPlayers={opponentPlayers}
            onCardClick={handleCardClick}
          />
        );

      default:
        return null;
    }
  };

  return (
    <div className="mt-3 sm:mt-4 max-w-lg mx-auto px-2">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 sm:p-4 shadow-md">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-semibold text-gray-800">Execute Action</h3>
          <p className="text-xs text-gray-600 mt-1">
            <strong>{action}</strong>
          </p>
        </div>

        {/* Render action-specific content */}
        {renderActionContent()}

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