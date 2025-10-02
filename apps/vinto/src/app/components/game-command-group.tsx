'use client';

import React, { useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { useGameStateManager, useReplayStore } from './di-provider';
import { GameToastService } from '../services/toast-service';
import { DownloadIcon, PlayIcon } from 'lucide-react';

export const GameCommandGroup = observer(() => {
  const gameStateManager = useGameStateManager();
  const replayStore = useReplayStore();

  const replayInputRef = useRef<HTMLInputElement>(null);

  const handleExportCommands = () => {
    const { canSave, reason } = gameStateManager.canSaveGame();

    if (!canSave) {
      GameToastService.error(`Cannot save game: ${reason}`);
      return;
    }

    gameStateManager.saveGameToFile(`vinto-${Date.now()}.json`);
  };

  const handleReplayMode = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const result = await gameStateManager.loadGameFromFileInReplayMode(file);

      if (result.success) {
        GameToastService.success(
          `Replay mode: ${result.commandCount} commands loaded`
        );
      } else {
        GameToastService.error('Failed to load replay');
      }
    } catch (error) {
      GameToastService.error(`Error loading replay: ${error}`);
    } finally {
      if (replayInputRef.current) {
        replayInputRef.current.value = '';
      }
    }
  };

  return (
    <div className="flex items-center gap-1">
      <button
        onClick={handleExportCommands}
        className="px-2 py-1 rounded bg-blue-100 hover:bg-blue-200 text-blue-700 font-semibold text-xs transition-colors"
        title="Export command history"
      >
        <DownloadIcon className="inline w-3 h-3 mr-1" />
        <span className="hidden sm:inline">Export</span>
      </button>
      {!replayStore.isReplayMode && (
        <button
          onClick={() => replayInputRef.current?.click()}
          className="px-2 py-1 rounded bg-green-100 hover:bg-green-200 text-green-700 font-semibold text-xs transition-colors"
          title="Load game in replay mode"
        >
          <PlayIcon className="inline w-3 h-3 mr-1" />
          <span className="hidden sm:inline">Replay</span>
        </button>
      )}
      <input
        ref={replayInputRef}
        type="file"
        accept=".json"
        onChange={handleReplayMode}
        className="hidden"
      />
    </div>
  );
});
