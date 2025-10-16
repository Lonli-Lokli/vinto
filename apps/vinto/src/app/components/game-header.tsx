// components/GameHeader.tsx - Client Component (compact)
'use client';

import React, { useState, useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { DeckManagerPopover } from './deck-manager-popover';
import { ThemeToggle, WakeLockToggle } from './presentational';
import {
  DifficultyButton,
  SettingsButton,
  DeckManagerButton,
  ReportProblemButton,
} from './buttons';
import { SettingsPopover } from './mobile-settings';
import { BugReportModal } from './modals';
import { GameActions } from '@/engine';
import { useGameClient } from '@/client';

export const GameHeader = observer(() => {
  const gameClient = useGameClient();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isDeckManagerOpen, setIsDeckManagerOpen] = useState(false);
  const [isBugReportOpen, setIsBugReportOpen] = useState(false);
  const settingsButtonRef = useRef<HTMLButtonElement>(null);
  const deckManagerButtonRef = useRef<HTMLButtonElement>(null);

  // Get values from GameClient
  const currentPlayer = gameClient.currentPlayer;
  const turnCount = gameClient.state.turnCount;
  const phase = gameClient.state.phase;
  const finalTurnTriggered = gameClient.state.finalTurnTriggered;
  const drawPile = gameClient.state.drawPile;
  const roundNumber = gameClient.state.roundNumber;

  const getPhaseDisplay = () => {
    if (phase === 'scoring') return 'Final Scores';
    if (finalTurnTriggered) return `Final • ${phase}`;
    return `R${roundNumber} / T${turnCount}`;
  };

  const getCurrentPlayerDisplay = () => {
    if (!currentPlayer || phase === 'scoring') return null;
    if (currentPlayer.isHuman) return `Your turn!`;
    return `${currentPlayer.name}`;
  };

  return (
    <div className="sticky top-0 z-50 flex-shrink-0">
      <div className="bg-surface-primary/70 backdrop-blur-md border-b border-primary flex-shrink-0  rounded-lg border-2">
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

                {/* Wake Lock Toggle */}
                <WakeLockToggle />

                {/* Difficulty */}
                <div className="flex items-center gap-1">
                  {(['easy', 'moderate', 'hard'] as const).map((level) => (
                    <DifficultyButton
                      key={level}
                      level={level}
                      isActive={gameClient.state.difficulty === level}
                      onClick={() =>
                        gameClient.dispatch(GameActions.updateDifficulty(level))
                      }
                    />
                  ))}
                </div>
              </div>
            </div>

            {/* Center: Title + Game Info */}
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1">
                {/* Theme Toggle & Wake Lock - visible on mobile next to VINTO */}
                <div className="sm:hidden flex items-center gap-1">
                  <ThemeToggle />
                  <WakeLockToggle />
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

            {/* Right: Cards Left + Deck Manager + Bug Report */}
            <div className="flex items-center gap-2">
              {/* Bug Report Button */}
              <ReportProblemButton onClick={() => setIsBugReportOpen(true)} />

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

      {/* Bug Report Modal */}
      <BugReportModal
        isOpen={isBugReportOpen}
        onClose={() => setIsBugReportOpen(false)}
      />
    </div>
  );
});
