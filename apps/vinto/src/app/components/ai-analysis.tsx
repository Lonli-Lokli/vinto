// components/AIAnalysis.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';

export function AIAnalysis() {
  const { currentMove, players, currentPlayerIndex } = useGameStore();

  const currentPlayer = players[currentPlayerIndex];
  if (!currentMove || !currentPlayer || currentPlayer.isHuman) {
    return null;
  }

  return (
    <div className="mt-4 sm:mt-6 mx-auto max-w-lg bg-white/90 backdrop-blur-sm border border-gray-200 rounded-2xl p-4 shadow-lg mx-2">
      <div className="text-center">
        <div className="flex items-center justify-center gap-2 mb-3">
          <span className="text-2xl">{currentPlayer.avatar}</span>
          <span className="font-semibold text-gray-700">AI Analysis</span>
        </div>

        <div className="grid grid-cols-2 gap-4 text-sm mb-3">
          <div className="bg-gray-50 rounded-lg p-3">
            <div className="text-gray-500 text-xs font-medium">Action</div>
            <div className="font-bold text-lg">
              {currentMove.type.toUpperCase()}
            </div>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <div className="text-gray-500 text-xs font-medium">Confidence</div>
            <div className="font-bold text-lg text-green-600">
              {(currentMove.confidence * 100).toFixed(0)}%
            </div>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <div className="text-gray-500 text-xs font-medium">
              Expected Value
            </div>
            <div className="font-bold text-blue-600">
              {currentMove.expectedValue > 0 ? '+' : ''}
              {currentMove.expectedValue.toFixed(1)}
            </div>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <div className="text-gray-500 text-xs font-medium">Think Time</div>
            <div className="font-bold text-purple-600">
              {currentMove.thinkingTime}ms
            </div>
          </div>
        </div>

        {currentMove.reasoning && (
          <div className="text-xs text-gray-600 italic bg-gray-50 rounded-lg p-3">
            &quot;{currentMove.reasoning} &quot;
          </div>
        )}
      </div>
    </div>
  );
}
