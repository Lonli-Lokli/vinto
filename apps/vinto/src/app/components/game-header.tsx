// components/GameHeader.tsx - Client Component (compact)
'use client';

import React, { useState, useRef, useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import {
  useGameStore,
  usePlayerStore,
  useGamePhaseStore,
  useDeckStore,
} from './di-provider';
import { GameCommandGroup } from './game-command-group';

const SettingsPopover = observer(
  ({
    isOpen,
    onClose,
    buttonRef,
  }: {
    isOpen: boolean;
    onClose: () => void;
    buttonRef: React.RefObject<HTMLButtonElement | null>;
  }) => {
    const gameStore = useGameStore();
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
        className="absolute top-full left-0 mt-2 bg-white rounded-lg shadow-lg border border-gray-200 p-4 z-50 min-w-[280px]"
      >
        <div className="space-y-4">
          {/* Difficulty */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Difficulty
            </label>
            <div className="grid grid-cols-2 gap-2">
              {(['easy', 'moderate', 'hard'] as const).map((level) => (
                <button
                  key={level}
                  onClick={() => gameStore.updateDifficulty(level)}
                  className={`px-3 py-2 rounded text-sm font-semibold transition-colors ${
                    gameStore.difficulty === level
                      ? 'bg-emerald-500 text-white'
                      : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                  }`}
                >
                  {level.charAt(0).toUpperCase() + level.slice(1)}
                </button>
              ))}
            </div>
          </div>

          {/* Toss-in time settings */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Toss-in Time
            </label>
            <div className="grid grid-cols-3 gap-2">
              {([5, 7, 10] as const).map((time) => (
                <button
                  key={time}
                  onClick={() => gameStore.updateTossInTime(time)}
                  className={`px-3 py-2 rounded text-sm font-semibold transition-colors ${
                    gameStore.tossInTimeConfig === time
                      ? 'bg-green-500 text-white'
                      : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                  }`}
                >
                  {time}s
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }
);

export const GameHeader = observer(() => {
  const gameStore = useGameStore();
  const { currentPlayer, turnCount } = usePlayerStore();
  const { phase, finalTurnTriggered } = useGamePhaseStore();
  const { drawPile } = useDeckStore();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const settingsButtonRef = useRef<HTMLButtonElement>(null);

  const getPhaseDisplay = () => {
    if (phase === 'scoring') return 'Final Scores';
    if (finalTurnTriggered) return `Final • ${phase}`;
    return `R${gameStore.roundNumber} • ${phase} • T${turnCount}`;
  };

  const getCurrentPlayerDisplay = () => {
    if (!currentPlayer || phase === 'scoring') return null;
    if (currentPlayer.isHuman) return `Your turn!`;
    return `${currentPlayer.name}`;
  };

  return (
    <div className="sticky top-0 z-50 flex-shrink-0">
      <div className="bg-white/70 backdrop-blur-md border-b border-gray-200 flex-shrink-0">
        <div className="max-w-6xl mx-auto px-3 py-1">
          <div className="flex items-center justify-between gap-4">
            {/* Left: Settings */}
            <div className="flex items-center gap-2 relative">
              {/* Mobile: Settings Button */}
              <button
                ref={settingsButtonRef}
                onClick={() => setIsSettingsOpen(!isSettingsOpen)}
                className="sm:hidden px-2 py-1 rounded bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold text-sm transition-colors"
                title="Settings"
              >
                ⚙️
              </button>

              <SettingsPopover
                isOpen={isSettingsOpen}
                onClose={() => setIsSettingsOpen(false)}
                buttonRef={settingsButtonRef}
              />

              {/* Desktop: Inline Settings */}
              <div className="hidden sm:flex items-center gap-2">
                {/* Difficulty */}
                <div className="flex items-center gap-1">
                  {(['easy', 'moderate', 'hard'] as const).map((level) => (
                    <button
                      key={level}
                      onClick={() => gameStore.updateDifficulty(level)}
                      className={`px-2 py-1 rounded text-[10px] font-semibold transition-colors ${
                        gameStore.difficulty === level
                          ? 'bg-emerald-500 text-white'
                          : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                      }`}
                      title={`Difficulty: ${level}`}
                    >
                      {level[0].toUpperCase()}
                    </button>
                  ))}
                </div>

                {/* Toss-in time settings */}
                <div className="flex items-center gap-1 ml-2">
                  <span className="text-xs text-gray-500 mr-1">Toss:</span>
                  {([5, 7, 10] as const).map((time) => (
                    <button
                      key={time}
                      onClick={() => gameStore.updateTossInTime(time)}
                      className={`px-1.5 py-0.5 rounded text-[9px] font-semibold transition-colors ${
                        gameStore.tossInTimeConfig === time
                          ? 'bg-green-500 text-white'
                          : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                      }`}
                      title={`Toss-in time: ${time}s`}
                    >
                      {time}s
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Center: Title + Game Info */}
            <div className="flex items-center gap-3">
              <h1 className="text-lg font-bold text-transparent bg-clip-text bg-gradient-to-r from-poker-green-700 to-emerald-600">
                VINTO
              </h1>
              <div className="text-xs text-gray-600 font-medium">
                {getPhaseDisplay()}
              </div>
              {getCurrentPlayerDisplay() && (
                <div className="text-xs font-medium text-emerald-600 hidden sm:block">
                  {getCurrentPlayerDisplay()}
                </div>
              )}
            </div>

            {/* Right: Cards Left + Command History */}
            <div className="flex items-center gap-2">
              <GameCommandGroup />

              {/* Cards Left */}
              <div className="flex items-center gap-1">
                <div className="text-sm font-semibold text-gray-700">
                  {drawPile.length}
                </div>
                <div className="text-2xs text-gray-500 hidden sm:block">
                  cards
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});
