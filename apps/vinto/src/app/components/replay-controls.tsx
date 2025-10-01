'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useGameStateManager, useReplayStore } from './di-provider';

/**
 * Replay Controls Component
 * Provides UI for stepping through game commands in replay mode
 */
export const ReplayControls = observer(() => {
  const gameStateManager = useGameStateManager();
  const replayStore = useReplayStore();
  const [isExecuting, setIsExecuting] = React.useState(false);

  if (!replayStore.isReplayMode) {
    return null;
  }

  const handleNext = async () => {
    if (isExecuting) return;

    setIsExecuting(true);
    try {
      await gameStateManager.executeNextReplayCommand();
    } finally {
      setIsExecuting(false);
    }
  };

  const handleExit = () => {
    gameStateManager.exitReplayMode();
  };

  const progress = replayStore.progress;
  const hasNext = replayStore.hasNextCommand;

  return (
    <div className="fixed bottom-4 left-1/2 transform -translate-x-1/2 z-50">
      <div className="bg-orange-900/95 border-2 border-yellow-600 rounded-lg shadow-2xl p-4">
        <div className="flex flex-col gap-3">
          {/* Header */}
          <div className="flex items-center justify-between gap-4">
            <div className="text-yellow-100 font-bold text-sm">
              REPLAY MODE
            </div>
            <button
              onClick={handleExit}
              className="px-2 py-1 bg-red-700 hover:bg-red-600 text-white text-xs rounded transition-colors"
            >
              Exit
            </button>
          </div>

          {/* Progress */}
          <div className="text-center">
            <div className="text-yellow-200 text-sm">
              Command {progress.current} of {progress.total}
            </div>
            {replayStore.currentCommand && (
              <div className="text-yellow-400 text-xs mt-1 font-mono">
                {replayStore.currentCommand.type}
              </div>
            )}
          </div>

          {/* Controls */}
          <div className="flex gap-2">
            <button
              onClick={handleNext}
              disabled={!hasNext || isExecuting}
              className={`flex-1 px-6 py-3 rounded-lg font-bold text-lg transition-all ${
                hasNext && !isExecuting
                  ? 'bg-green-600 hover:bg-green-500 text-white shadow-lg hover:shadow-xl'
                  : 'bg-gray-600 text-gray-400 cursor-not-allowed'
              }`}
            >
              {isExecuting ? 'Executing...' : hasNext ? 'Next →' : 'Finished'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
});
