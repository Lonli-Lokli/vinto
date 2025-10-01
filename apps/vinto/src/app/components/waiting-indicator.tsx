'use client';
import { useGameStore, usePlayerStore } from './di-provider';

export function WaitingIndicator() {
  const { isCurrentPlayerWaiting } = useGameStore();
  const { currentPlayer } = usePlayerStore();

  if (!isCurrentPlayerWaiting) return null;

  return (
    <div className="max-w-lg mx-auto px-3 py-2">
      <div className="bg-white/80 backdrop-blur-sm border border-gray-200 rounded-xl p-3 shadow-sm">
        <div className="text-center text-sm text-gray-600">
          {currentPlayer?.name}&apos;s turn
        </div>
      </div>
    </div>
  );
}
