// client/adapters/typeAdapters.ts
// Type adapters to bridge existing UI types with new Engine types

import { PlayerState as EnginePlayer, GameState as EngineGameState } from '../../engine/types';
import { Player as UIPlayer, GameState as UIGameState, Card, Rank } from '../../app/shapes';

/**
 * Convert Engine PlayerState to UI Player
 *
 * Adds UI-specific fields that aren't part of the engine:
 * - position (for layout)
 * - temporarilyVisibleCards (for animations)
 * - highlightedCards (for bot actions)
 * - opponentKnowledge (for bot AI)
 */
export function enginePlayerToUIPlayer(
  enginePlayer: EnginePlayer,
  position: UIPlayer['position'] = 'bottom',
  existingUIPlayer?: UIPlayer
): UIPlayer {
  return {
    // Core fields from engine
    id: enginePlayer.id,
    name: enginePlayer.name,
    cards: enginePlayer.cards,
    isHuman: enginePlayer.isHuman,
    isBot: enginePlayer.isBot,
    isVintoCaller: enginePlayer.isVintoCaller,
    isCoalitionLeader: false, // TODO: Set from coalition leader ID

    // Convert coalition array to Set
    coalitionWith: new Set(enginePlayer.coalitionWith),

    // Convert known cards array to Set (for UI compatibility)
    knownCardPositions: new Set(enginePlayer.knownCardPositions),

    // UI-specific fields (preserve existing or use defaults)
    position: existingUIPlayer?.position ?? position,
    temporarilyVisibleCards: existingUIPlayer?.temporarilyVisibleCards ?? new Set(),
    highlightedCards: existingUIPlayer?.highlightedCards ?? new Set(),
    opponentKnowledge: existingUIPlayer?.opponentKnowledge ?? new Map(),
  };
}

/**
 * Convert UI Player to Engine PlayerState
 *
 * Strips UI-specific fields and converts types
 */
export function uiPlayerToEnginePlayer(uiPlayer: UIPlayer): EnginePlayer {
  return {
    id: uiPlayer.id,
    name: uiPlayer.name,
    cards: uiPlayer.cards,
    isHuman: uiPlayer.isHuman,
    isBot: uiPlayer.isBot,
    isVintoCaller: uiPlayer.isVintoCaller ?? false,

    // Convert Set to array
    coalitionWith: Array.from(uiPlayer.coalitionWith),

    // Convert Set of positions to array
    knownCardPositions: Array.from(uiPlayer.knownCardPositions),
  };
}

/**
 * Convert Engine GameState to UI GameState
 *
 * Maps engine phases to UI phases and adds UI-specific fields
 */
export function engineGameStateToUIGameState(
  engineState: EngineGameState,
  playerPositions?: Map<string, UIPlayer['position']>
): Partial<UIGameState> {
  // Map engine phase to UI phase
  const uiPhase = mapEnginePhaseToUIPhase(engineState.phase);

  // Convert players with positions
  const players = engineState.players.map((enginePlayer, index) => {
    const position = playerPositions?.get(enginePlayer.id) ?? getDefaultPosition(index);
    return enginePlayerToUIPlayer(enginePlayer, position);
  });

  return {
    players,
    currentPlayerIndex: engineState.currentPlayerIndex,
    drawPile: engineState.drawPile,
    discardPile: engineState.discardPile,
    phase: uiPhase,
    gameId: engineState.gameId,
    roundNumber: engineState.roundNumber,
    turnCount: engineState.turnCount,
    finalTurnTriggered: engineState.finalTurnTriggered,
  };
}

/**
 * Map engine phase/subPhase to UI phase
 */
function mapEnginePhaseToUIPhase(
  enginePhase: EngineGameState['phase']
): UIGameState['phase'] {
  switch (enginePhase) {
    case 'setup':
      return 'setup';
    case 'playing':
      return 'playing';
    case 'final':
      return 'final';
    case 'scoring':
      return 'scoring';
    default:
      return 'playing'; // Default fallback
  }
}

/**
 * Get default player position based on index
 */
function getDefaultPosition(index: number): UIPlayer['position'] {
  const positions: UIPlayer['position'][] = ['bottom', 'left', 'top', 'right'];
  return positions[index] || 'bottom';
}

/**
 * Convert known card positions from Set to Array
 * (for serialization/engine compatibility)
 */
export function knownPositionsSetToArray(
  knownPositions: Set<number>,
  cards: Card[]
): Array<{ position: number; rank: Rank }> {
  return Array.from(knownPositions).map(pos => ({
    position: pos,
    rank: cards[pos]?.rank || 'A',
  }));
}

/**
 * Convert known card positions from Array to Set
 * (for UI compatibility)
 */
export function knownPositionsArrayToSet(
  knownPositions: Array<{ position: number; rank: Rank }>
): Set<number> {
  return new Set(knownPositions.map(k => k.position));
}

/**
 * Merge UI-specific player data into engine player
 *
 * Useful when you want to preserve UI state during updates
 */
export function mergeUIPlayerData(
  enginePlayer: EnginePlayer,
  uiPlayerData: {
    position: UIPlayer['position'];
    temporarilyVisibleCards: Set<number>;
    highlightedCards: Set<number>;
    opponentKnowledge: Map<string, any>;
  }
): UIPlayer {
  return {
    ...enginePlayerToUIPlayer(enginePlayer, uiPlayerData.position),
    temporarilyVisibleCards: uiPlayerData.temporarilyVisibleCards,
    highlightedCards: uiPlayerData.highlightedCards,
    opponentKnowledge: uiPlayerData.opponentKnowledge,
  };
}

/**
 * Extract UI-specific player data
 *
 * Useful for preserving UI state before engine update
 */
export function extractUIPlayerData(uiPlayer: UIPlayer) {
  return {
    position: uiPlayer.position,
    temporarilyVisibleCards: uiPlayer.temporarilyVisibleCards,
    highlightedCards: uiPlayer.highlightedCards,
    opponentKnowledge: uiPlayer.opponentKnowledge,
  };
}
