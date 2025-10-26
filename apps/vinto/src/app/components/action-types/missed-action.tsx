import React, { useState } from 'react';
import { observer } from 'mobx-react-lite';
import { useGameClient } from '@vinto/local-client';

/**
 * MissedAction: Displayed when an action target type is undefined or missing.
 * This helps identify when action handling is incomplete.
 */
export const MissedAction = observer(() => {
  const [isExpanded, setIsExpanded] = useState(false);
  const gameClient = useGameClient();

  const pendingAction = gameClient.visualState.pendingAction;

  return (
    <div className="p-4 bg-red-100 border border-red-300 rounded-lg">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-red-800">
          Action Not Implemented
        </h3>
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className="text-red-600 hover:text-red-800 font-medium text-sm"
        >
          {isExpanded ? 'Hide Details' : 'Show Details'}
        </button>
      </div>

      {isExpanded && (
        <div className="mt-3">
          <p className="text-red-700 mb-2">
            The current action target type is undefined or not recognized.
            This indicates a missing implementation for this action type.
          </p>

          <div className="bg-red-50 p-3 rounded border border-red-200">
            <h4 className="font-medium text-red-800 mb-2">Current State:</h4>
            <div className="text-sm text-red-700 space-y-1">
              <div><strong>Phase:</strong> {gameClient.visualState.phase}</div>
              <div><strong>SubPhase:</strong> {gameClient.visualState.subPhase}</div>
              <div><strong>Current Player:</strong> {gameClient.visualState.players[gameClient.visualState.currentPlayerIndex]?.id}</div>
            </div>

            {pendingAction && (
              <div className="mt-3">
                <h4 className="font-medium text-red-800 mb-2">Pending Action:</h4>
                <div className="text-sm text-red-700 space-y-1">
                  <div><strong>Player ID:</strong> {pendingAction.playerId}</div>
                  <div><strong>Target Type:</strong> {pendingAction.targetType || 'undefined'}</div>
                  <div><strong>Action Phase:</strong> {pendingAction.actionPhase}</div>
                  {pendingAction.card && (
                    <div><strong>Card:</strong> {pendingAction.card.rank} (played: {pendingAction.card.played ? 'yes' : 'no'})</div>
                  )}
                  {pendingAction.targets && pendingAction.targets.length > 0 && (
                    <div><strong>Targets:</strong> {pendingAction.targets.length} selected</div>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className="mt-3 text-sm text-red-600">
            <strong>Debug Info:</strong> Check the pendingAction.targetType in the game state.
          </div>
        </div>
      )}
    </div>
  );
});