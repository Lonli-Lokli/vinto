// client/GameClientContext.tsx

'use client';

import React, { createContext, useContext, useState, useEffect } from 'react';
import { fourPlayerGame } from './initializeGame';
import { GameClient } from './game-client';
import { createBotAI } from './adapters/botAIAdapter';
import { GameState } from '@vinto/shapes';
import {
  triggerStateErrorCallbacks,
  triggerStateUpdateCallbacks,
} from './game-client-events';

/**
 * Context value includes both client and initialization state
 */
interface GameClientContextValue {
  client: GameClient;
  isInitialized: boolean;
}

/**
 * React Context for GameClient
 */
const GameClientContext = createContext<GameClientContextValue | null>(null);

/**
 * Props for GameClientProvider
 */
interface GameClientProviderProps {
  children: React.ReactNode;
  initialClient?: GameClient;
}

/**
 * Provider component for GameClient
 *
 * Usage:
 * ```tsx
 * <GameClientProvider>
 *   <App />
 * </GameClientProvider>
 * ```
 */
export const GameClientProvider: React.FC<GameClientProviderProps> = ({
  children,
  initialClient,
}) => {
  const [client] = useState(() => {
    // Use provided client or create a new one with four player game
    return initialClient || new GameClient(fourPlayerGame());
  });

  const [isInitialized, setIsInitialized] = useState(false);

  // Setup side effects (animations, sounds, bot AI, etc.)
  useEffect(() => {
    console.log('[GameClientProvider] Starting initialization...');

    // Register state update callback dispatcher
    client.onStateUpdateSuccess((oldState, newState, action) => {
      console.log('[GameClient] Action:', action.type, action);
      console.log('[GameClient] New State:', newState.phase, newState.subPhase);
      triggerStateUpdateCallbacks(oldState, newState, action);
    });

    client.onStateUpdateError((reason) => {
      triggerStateErrorCallbacks(reason);
    });

    // Initialize Bot AI Adapter (client-side only, for local games)
    const botAI = createBotAI(client);
    console.log('[GameClient] Bot AI initialized');

    setIsInitialized(true);
    console.log('[GameClientProvider] Initialization complete');

    return () => {
      botAI.dispose();
    };
  }, [client]);

  const contextValue: GameClientContextValue = {
    client,
    isInitialized,
  };

  return (
    <GameClientContext.Provider value={contextValue}>
      {children}
    </GameClientContext.Provider>
  );
};

/**
 * Hook to access GameClient from components
 *
 * Usage:
 * ```tsx
 * const gameClient = useGameClient();
 * gameClient.dispatch(GameActions.drawCard(playerId));
 * ```
 */
export function useGameClient(): GameClient {
  const context = useContext(GameClientContext);

  if (!context) {
    throw new Error('useGameClient must be used within GameClientProvider');
  }

  return context.client;
}

/**
 * Hook to check if GameClient is fully initialized
 *
 * Usage:
 * ```tsx
 * const isInitialized = useGameClientInitialized();
 * if (!isInitialized) return <LoadingScreen />;
 * ```
 */
export function useGameClientInitialized(): boolean {
  const context = useContext(GameClientContext);

  if (!context) {
    throw new Error(
      'useGameClientInitialized must be used within GameClientProvider'
    );
  }

  return context.isInitialized;
}

/**
 * Hook to get current game state (observable)
 *
 * This hook automatically re-renders when the state changes.
 * Uses MobX's Observer component internally, so components don't need to be wrapped with observer().
 *
 * Usage:
 * ```tsx
 * const MyComponent = () => {
 *   const state = useGameState(); // Automatically reactive!
 *   return <div>Phase: {state.phase}</div>;
 * };
 * ```
 */
export function useGameState(): GameState {
  const client = useGameClient();
  // Return the observable state - components using this should be wrapped with observer() or use <Observer>
  return client.visualState;
}

/**
 * Hook to get current player (observable)
 *
 * This hook automatically re-renders when the current player changes.
 * Components using this should be wrapped with observer() or use <Observer>.
 *
 * Usage:
 * ```tsx
 * const MyComponent = observer(() => {
 *   const player = useCurrentPlayer(); // Automatically reactive!
 *   return <div>{player.name}</div>;
 * });
 * ```
 */
export function useCurrentPlayer() {
  const client = useGameClient();
  return client.currentPlayer;
}

/**
 * Hook to dispatch actions
 *
 * Usage:
 * ```tsx
 * const dispatch = useDispatch();
 * dispatch(GameActions.drawCard(playerId));
 * ```
 */
export function useDispatch() {
  const client = useGameClient();
  return (action: Parameters<GameClient['dispatch']>[0]) => {
    client.dispatch(action);
  };
}

/**
 * Hook to check if it's a specific player's turn
 */
export function useIsPlayerTurn(playerId: string): boolean {
  const client = useGameClient();
  return client.isPlayerTurn(playerId);
}

/**
 * Hook to get player by ID
 */
export function usePlayer(playerId: string) {
  const client = useGameClient();
  return client.getPlayer(playerId);
}
