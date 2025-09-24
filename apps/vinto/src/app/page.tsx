// app/page.tsx
'use client';

import React, { useEffect, useState } from 'react';
import { useGameStore } from './stores/game-store';
import { Difficulty } from './shapes';
import { ToastProvider } from './components/toast-provider';
import { getWinnerInfo } from './lib/game-helpers';
import { GameHeader } from './components/game-header';
import { GameTable } from './components/game-table';
import { GameControls } from './components/game-controls';
import { GamePhaseIndicators } from './components/game-phase-indicators';
import { AIAnalysis } from './components/ai-analysis';
import { FinalScores } from './components/final-scores';

export default function VintoGame() {
  const {
    players,
    currentPlayerIndex,
    drawPile,
    discardPile,
    roundNumber,
    phase,
    turnCount,
    maxTurns,
    finalTurnTriggered,
    aiThinking,
    currentMove,
    sessionActive,
    pendingCard,
    isSelectingSwapPosition,
    setupPeeksRemaining,
    waitingForTossIn,
    tossInTimer,
    initGame,
    updateDifficulty,
    peekCard,
    finishSetup,
    drawCard,
    takeFromDiscard,
    swapCard,
    cancelSwap,
    tossInCard,
    makeAIMove,
    callVinto,
    calculateFinalScores,
  } = useGameStore();

  const [difficulty, setDifficulty] = useState<Difficulty>('moderate');

  // Initialize game on mount
  useEffect(() => {
    if (players.length === 0) {
      initGame();
    }
  }, [players.length, initGame]);

  // Handle AI turns
  useEffect(() => {
    const currentPlayer = players[currentPlayerIndex];
    if (
      currentPlayer &&
      !currentPlayer.isHuman &&
      !aiThinking &&
      sessionActive
    ) {
      const timer = setTimeout(() => {
        makeAIMove(difficulty);
      }, 1000);
      return () => clearTimeout(timer);
    }
    return () => {
      /* empty */
    };
  }, [
    currentPlayerIndex,
    aiThinking,
    makeAIMove,
    difficulty,
    players,
    sessionActive,
  ]);

  const handleDifficultyChange = (newDifficulty: Difficulty) => {
    setDifficulty(newDifficulty);
    updateDifficulty(newDifficulty);
  };

  const handleCardClick = (position: number) => {
    const humanPlayer = players.find((p) => p.isHuman);
    if (!humanPlayer) return;

    // During setup phase, allow peeking at cards for memorization
    if (phase === 'setup') {
      if (setupPeeksRemaining > 0 && !humanPlayer.knownCardPositions.has(position)) {
        peekCard(humanPlayer.id, position);
      }
      return;
    }

    // If selecting swap position, perform the swap
    if (isSelectingSwapPosition) {
      swapCard(position);
      return;
    }

    // During toss-in period, allow tossing in cards
    if (waitingForTossIn) {
      tossInCard(humanPlayer.id, position);
      return;
    }
  };

  const handleDrawCard = () => {
    const currentPlayer = players[currentPlayerIndex];
    if (currentPlayer && currentPlayer.isHuman) {
      drawCard();
    }
  };

  // Loading state
  if (!sessionActive || players.length === 0) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-emerald-50 to-blue-50 flex items-center justify-center">
        <div className="text-center">
          <div className="text-6xl mb-4 animate-bounce">🎮</div>
          <div className="text-xl font-semibold text-gray-700 mb-2">
            Setting up Vinto game...
          </div>
          <div className="text-sm text-gray-500">Connecting to AI servers</div>
        </div>
        <ToastProvider />
      </div>
    );
  }

  const currentPlayer = players[currentPlayerIndex];
  const humanPlayer = players.find((p) => p.isHuman)!;

  // Calculate final scores if in scoring phase
  const finalScores = phase === 'scoring' ? calculateFinalScores() : undefined;
  const winnerInfo = finalScores
    ? getWinnerInfo(finalScores, players)
    : undefined;

  return (
    <div className="bg-gradient-to-br from-emerald-50 to-blue-50 pb-4">
      <ToastProvider />

      <GameHeader
        phase={phase}
        roundNumber={roundNumber}
        turnCount={turnCount}
        maxTurns={maxTurns}
        finalTurnTriggered={finalTurnTriggered}
        drawPileCount={drawPile.length}
        currentPlayer={currentPlayer}
        difficulty={difficulty}
        onDifficultyChange={handleDifficultyChange}
      />

      <GameTable
        players={players}
        currentPlayer={currentPlayer}
        humanPlayer={humanPlayer}
        aiThinking={aiThinking}
        gamePhase={phase}
        finalScores={finalScores}
        isSelectingSwapPosition={isSelectingSwapPosition}
        pendingCard={pendingCard}
        discardPile={discardPile}
        onCardClick={handleCardClick}
        onDrawCard={handleDrawCard}
      />

      <AIAnalysis currentMove={currentMove} currentPlayer={currentPlayer} />

      <FinalScores
        phase={phase}
        winnerInfo={winnerInfo}
        finalScores={finalScores}
        players={players}
      />

      <GamePhaseIndicators
        phase={phase}
        setupPeeksRemaining={setupPeeksRemaining}
        isSelectingSwapPosition={isSelectingSwapPosition}
        waitingForTossIn={waitingForTossIn}
        tossInTimer={tossInTimer}
        discardPile={discardPile}
        onFinishSetup={finishSetup}
        onCancelSwap={cancelSwap}
      />

      <GameControls
        currentPlayerIsHuman={currentPlayer?.isHuman || false}
        phase={phase}
        isSelectingSwapPosition={isSelectingSwapPosition}
        waitingForTossIn={waitingForTossIn}
        drawPileLength={drawPile.length}
        discardPile={discardPile}
        onDrawCard={handleDrawCard}
        onTakeFromDiscard={takeFromDiscard}
        onCallVinto={callVinto}
      />
    </div>
  );
}
