// components/di-provider.tsx
'use client';

import React, { createContext, useContext, useMemo } from 'react';
import { setupDIContainer, getInstance, isDIConfigured } from '../di/setup';
import {
  GameStore,
  PlayerStore,
  DeckStore,
  GamePhaseStore,
  ActionStore,
  TossInStore,
  ReplayStore,
  CardAnimationStore,
} from '../stores';
import { GameStateManager } from '../commands';
import { CommandHistory } from '../commands/command-history';

/**
 * Store context for accessing DI-managed stores
 */
interface StoreContextValue {
  gameStore: GameStore;
  playerStore: PlayerStore;
  deckStore: DeckStore;
  gamePhaseStore: GamePhaseStore;
  actionStore: ActionStore;
  tossInStore: TossInStore;
  replayStore: ReplayStore;
  cardAnimationStore: CardAnimationStore;
  gameStateManager: GameStateManager;
  commandHistory: CommandHistory;
}

const StoreContext = createContext<StoreContextValue | null>(null);

/**
 * DI Provider - Sets up dependency injection and provides stores to React tree
 */
export function DIProvider({ children }: { children: React.ReactNode }) {
  // Get store instances from DI container
  const stores = useMemo<StoreContextValue>(() => {
    // Only setup DI in browser environment

    if (!isDIConfigured()) {
      setupDIContainer('moderate');
    }

    return {
      gameStore: getInstance<GameStore>(GameStore),
      playerStore: getInstance<PlayerStore>(PlayerStore),
      deckStore: getInstance<DeckStore>(DeckStore),
      gamePhaseStore: getInstance<GamePhaseStore>(GamePhaseStore),
      actionStore: getInstance<ActionStore>(ActionStore),
      tossInStore: getInstance<TossInStore>(TossInStore),
      replayStore: getInstance<ReplayStore>(ReplayStore),
      cardAnimationStore: getInstance<CardAnimationStore>(CardAnimationStore),
      gameStateManager: getInstance<GameStateManager>(GameStateManager),
      commandHistory: getInstance<CommandHistory>(CommandHistory),
    };
  }, []);

  return (
    <StoreContext.Provider value={stores}>{children}</StoreContext.Provider>
  );
}

/**
 * Hook to access stores from DI container
 */
export function useStores(): StoreContextValue {
  const context = useContext(StoreContext);

  if (!context) {
    throw new Error('useStores must be used within a DIProvider');
  }

  return context;
}

/**
 * Hook to access gameStore
 */
export function useGameStore(): GameStore {
  return useStores().gameStore;
}

/**
 * Hook to access playerStore
 */
export function usePlayerStore(): PlayerStore {
  return useStores().playerStore;
}

/**
 * Hook to access deckStore
 */
export function useDeckStore(): DeckStore {
  return useStores().deckStore;
}

/**
 * Hook to access gamePhaseStore
 */
export function useGamePhaseStore(): GamePhaseStore {
  return useStores().gamePhaseStore;
}

/**
 * Hook to access actionStore
 */
export function useActionStore(): ActionStore {
  return useStores().actionStore;
}

/**
 * Hook to access tossInStore
 */
export function useTossInStore(): TossInStore {
  return useStores().tossInStore;
}

export function useGameStateManager(): GameStateManager {
  return useStores().gameStateManager;
}

/**
 * Hook to access replayStore
 */
export function useReplayStore(): ReplayStore {
  return useStores().replayStore;
}

/**
 * Hook to access cardAnimationStore
 */
export function useCardAnimationStore(): CardAnimationStore {
  return useStores().cardAnimationStore;
}

/**
 * Hook to access commandHistory
 */
export function useCommandHistory(): CommandHistory {
  return useStores().commandHistory;
}
