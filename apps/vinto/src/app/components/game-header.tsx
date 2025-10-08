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
import { DeckManagerPopover } from './deck-manager-popover';
import { ThemeToggle } from './theme-toggle';
import { DifficultyButton, SettingsButton, DeckManagerButton } from './buttons';

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
        className="absolute top-full left-0 mt-2 bg-surface-primary rounded-lg shadow-lg border border-primary p-4 z-50 min-w-[280px]"
      >
        <div className="space-y-4">
          {/* Difficulty */}
          <div>
            <label className="block text-sm font-medium text-primary mb-2">
              Difficulty
            </label>
            <div className="grid grid-cols-2 gap-2">
              {(['easy', 'moderate', 'hard'] as const).map((level) => (
                <DifficultyButton
                  key={level}
                  level={level}
                  isActive={gameStore.difficulty === level}
                  onClick={() => gameStore.updateDifficulty(level)}
                  className="px-3 py-2 text-sm"
                />
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
  const [isDeckManagerOpen, setIsDeckManagerOpen] = useState(false);
  const settingsButtonRef = useRef<HTMLButtonElement>(null);
  const deckManagerButtonRef = useRef<HTMLButtonElement>(null);

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
      <div className="bg-surface-primary/70 backdrop-blur-md border-b border-primary flex-shrink-0">
        <div className="max-w-6xl mx-auto px-3 py-1">
          <div className="flex items-center justify-between gap-4">
            {/* Left: Settings */}
            <div className="flex items-center gap-2 relative">
              {/* Mobile: Settings Button */}
              <SettingsButton
                ref={settingsButtonRef}
                onClick={() => setIsSettingsOpen(!isSettingsOpen)}
                className="sm:hidden"
              />

              <SettingsPopover
                isOpen={isSettingsOpen}
                onClose={() => setIsSettingsOpen(false)}
                buttonRef={settingsButtonRef}
              />

              {/* Desktop: Inline Settings */}
              <div className="hidden sm:flex items-center gap-2">
                {/* Theme Toggle */}
                <ThemeToggle />

                {/* Difficulty */}
                <div className="flex items-center gap-1">
                  {(['easy', 'moderate', 'hard'] as const).map((level) => (
                    <DifficultyButton
                      key={level}
                      level={level}
                      isActive={gameStore.difficulty === level}
                      onClick={() => gameStore.updateDifficulty(level)}
                    />
                  ))}
                </div>
              </div>
            </div>

            {/* Center: Title + Game Info */}
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1">
                {/* Theme Toggle - visible on mobile next to VINTO */}
                <div className="sm:hidden">
                  <ThemeToggle />
                </div>
                <h1 className="text-lg font-bold text-transparent bg-clip-text bg-title-gradient">
                  VINTO
                </h1>

                <a
                  href="/VintoRules.pdf"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-secondary hover:text-success transition-colors"
                  title="View game rules"
                >
                  📖
                </a>
              </div>
              <div className="text-xs text-secondary font-medium">
                {getPhaseDisplay()}
              </div>
              {getCurrentPlayerDisplay() && (
                <div className="text-xs font-medium text-success hidden sm:block">
                  {getCurrentPlayerDisplay()}
                </div>
              )}
            </div>

            {/* Right: Cards Left + Command History */}
            <div className="flex items-center gap-2">
              <GameCommandGroup />

              {/* Cards Left + Deck Manager */}
              <div className="flex items-center gap-1 relative">
                <DeckManagerButton
                  ref={deckManagerButtonRef}
                  cardCount={drawPile.length}
                  onClick={() => setIsDeckManagerOpen(!isDeckManagerOpen)}
                />

                <DeckManagerPopover
                  isOpen={isDeckManagerOpen}
                  onClose={() => setIsDeckManagerOpen(false)}
                  buttonRef={deckManagerButtonRef}
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});
