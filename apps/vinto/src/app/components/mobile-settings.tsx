import { observer } from 'mobx-react-lite';
import { useRef, useEffect } from 'react';
import { DifficultyButton } from './buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';

export const SettingsPopover = observer(
  ({
    isOpen,
    onClose,
    buttonRef,
  }: {
    isOpen: boolean;
    onClose: () => void;
    buttonRef: React.RefObject<HTMLButtonElement | null>;
  }) => {
    const gameClient = useGameClient();
    const popoverRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
      if (!isOpen) return;

      const handleClickOutside = (event: MouseEvent) => {
        if (
          popoverRef.current &&
          !popoverRef.current.contains(event.target as Node) &&
          buttonRef.current &&
          !buttonRef.current.contains(event.target as Node)
        ) {
          onClose();
        }
      };

      document.addEventListener('mousedown', handleClickOutside);
      return () =>
        document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen, onClose, buttonRef]);

    if (!isOpen) return null;

    return (
      <div
        ref={popoverRef}
        className="absolute top-full left-0 mt-2 bg-surface-primary rounded-lg shadow-lg border border-primary p-4 z-50 min-w-[280px]"
      >
        <div className="space-y-4">
          {/* Difficulty */}
          <div>
            <label className="block text-base font-medium text-primary mb-2">
              Difficulty
            </label>
            <div className="flex gap-2">
              {(['easy', 'moderate', 'hard'] as const).map((level) => (
                <DifficultyButton
                  key={level}
                  level={level}
                  isActive={gameClient.visualState.difficulty === level}
                  onClick={() =>
                    gameClient.dispatch(GameActions.updateDifficulty(level))
                  }
                  className="px-3 py-2 text-base"
                />
              ))}
            </div>
          </div>

          {/* Bot Version */}
          <div>
            <label className="block text-base font-medium text-primary mb-2">
              Bot Version
            </label>
            <div className="flex gap-2">
              {(['v1', 'v2'] as const).map((version) => (
                <button
                  key={version}
                  onClick={() =>
                    gameClient.dispatch(GameActions.updateBotVersion(version))
                  }
                  className={`px-3 py-2 text-base rounded-md border transition-colors ${
                    gameClient.visualState.botVersion === version
                      ? 'bg-primary text-primary-foreground border-primary'
                      : 'bg-surface-secondary text-primary border-primary/20 hover:bg-surface-primary'
                  }`}
                >
                  {version.toUpperCase()}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }
);
