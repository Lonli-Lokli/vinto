// lib/server-utils.ts
// Utility functions that can be used in server components

export function formatGamePhase(phase: string, turnCount: number, roundNumber: number, finalTurnTriggered: boolean): string {
  if (phase === 'scoring') {
    return 'Final Scores';
  }

  if (finalTurnTriggered) {
    return `Final Turn • ${phase}`;
  }

  return `Round ${roundNumber} • ${phase} • Turn ${turnCount}`;
}

export function getCurrentPlayerName(currentPlayer: any): string {
  if (!currentPlayer) return '';
  return `${currentPlayer.name}'s Turn${currentPlayer.isHuman ? ' (Your turn!)' : ''}`;
}