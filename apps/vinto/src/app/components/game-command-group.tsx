'use client';

import React, { useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { useGameStateManager, useReplayStore } from './di-provider';
import { GameToastService } from '../services/toast-service';
import { DownloadIcon, PlayIcon } from 'lucide-react';
import { ExportButton, ReplayButton } from './buttons';

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
      <ExportButton onClick={handleExportCommands}>
        <DownloadIcon className="inline w-3 h-3 mr-1" />
        <span className="hidden sm:inline">Export</span>
      </ExportButton>
      {!replayStore.isReplayMode && (
        <ReplayButton onClick={() => replayInputRef.current?.click()}>
          <PlayIcon className="inline w-3 h-3 mr-1" />
          <span className="hidden sm:inline">Replay</span>
        </ReplayButton>
      )}
      <input
        ref={replayInputRef}
        type="file"
        accept=".json"
        onChange={(e) => void handleReplayMode(e)}
        className="hidden"
      />
    </div>
  );
});
