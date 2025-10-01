'use client';

import React, { useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { useGameStateManager } from './di-provider';
import { GameToastService } from '../lib/toast-service';

export const SaveLoadButtons = observer(() => {
  const gameStateManager = useGameStateManager();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const handleExportCommands = () => {
    const { canSave, reason } = gameStateManager.canSaveGame();

    if (!canSave) {
      GameToastService.error(`Cannot save game: ${reason}`);
      return;
    }

    gameStateManager.saveGameToFile(`vinto-${Date.now()}.json`);
  };

  const handleImportCommands = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const result = await gameStateManager.loadGameFromFile(file);

      if (result.success) {
        GameToastService.info(
          `Game loaded: ${result.commandsReplayed} commands replayed`
        );
        return true;
      } else {
        GameToastService.error(
          `Failed to load game: ${result.errors
            .map((e) => e.message)
            .join('; ')}`
        );
        return false;
      }
    } catch (error) {
      GameToastService.error(`Error loading game from file: ${error}`);
      return false;
    }
  };

  return (
    <div className="flex items-center gap-1">
      <button
        onClick={handleExportCommands}
        className="px-2 py-1 rounded bg-blue-100 hover:bg-blue-200 text-blue-700 font-semibold text-xs transition-colors"
        title="Export command history"
      >
        <span className="hidden sm:inline">💾 Export</span>
        <span className="sm:hidden">💾</span>
      </button>
      <button
        onClick={() => fileInputRef.current?.click()}
        className="px-2 py-1 rounded bg-purple-100 hover:bg-purple-200 text-purple-700 font-semibold text-xs transition-colors"
        title="Import command history"
      >
        <span className="hidden sm:inline">📂 Import</span>
        <span className="sm:hidden">📂</span>
      </button>
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        onChange={handleImportCommands}
        className="hidden"
      />
    </div>
  );
});
