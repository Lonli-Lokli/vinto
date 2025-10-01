'use client';

import React, { useRef, useState } from 'react';
import { observer } from 'mobx-react-lite';
import { useGameStateManager, useReplayStore } from './di-provider';
import { GameToastService } from '../lib/toast-service';

/**
 * Replay Loader Component
 * Allows loading a saved game in replay mode
 */
export const ReplayLoader = observer(() => {
  const gameStateManager = useGameStateManager();
  const replayStore = useReplayStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Don't show loader when already in replay mode
  if (replayStore.isReplayMode) {
    return null;
  }

  const handleFileSelect = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setIsLoading(true);
    try {
      const result = await gameStateManager.loadGameFromFileInReplayMode(file);

      if (result.success) {
        GameToastService.success(
          `Loaded ${result.commandCount} commands in replay mode`
        );
      } else {
        GameToastService.error('Failed to load game in replay mode');
      }
    } catch (error) {
      console.error('Error loading replay:', error);
      GameToastService.error(
        `Error loading replay: ${error instanceof Error ? error.message : 'Unknown error'}`
      );
    } finally {
      setIsLoading(false);
      // Reset file input
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleClick = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className="fixed top-4 right-4 z-50">
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        onChange={handleFileSelect}
        className="hidden"
      />
      <button
        onClick={handleClick}
        disabled={isLoading}
        className={`px-4 py-2 rounded-lg font-semibold text-sm shadow-lg transition-all ${
          isLoading
            ? 'bg-gray-400 text-gray-600 cursor-not-allowed'
            : 'bg-blue-600 hover:bg-blue-700 text-white hover:shadow-xl'
        }`}
      >
        {isLoading ? 'Loading...' : '📂 Load Replay'}
      </button>
    </div>
  );
});
