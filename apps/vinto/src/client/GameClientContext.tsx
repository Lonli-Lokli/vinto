// client/GameClientContext.tsx
// React Context and hook for GameClient

import React, { createContext, useContext, useState, useEffect } from 'react';
import { useObserver } from 'mobx-react-lite';
import type { GameState } from '../engine/types';
import { GameClient } from './GameClient';
import { quickStartGame } from './initializeGame';

/**
 * React Context for GameClient
 */
const GameClientContext = createContext<GameClient | null>(null);

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
  initialClient
}) => {
  const [client] = useState(() => {
    // Use provided client or create a new one with quick start game
    return initialClient || new GameClient(quickStartGame());
  });

  // Setup side effects (animations, sounds, etc.)
  useEffect(() => {
    client.onStateUpdate((oldState, newState, action) => {
      // TODO: Trigger animations based on action type
      console.log('[GameClient] Action:', action.type, action);
      console.log('[GameClient] New State:', newState.phase, newState.subPhase);
    });
  }, [client]);

  return (
    <GameClientContext.Provider value={client}>
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
  const client = useContext(GameClientContext);

  if (!client) {
    throw new Error('useGameClient must be used within GameClientProvider');
  }

  return client;
}

/**
 * Hook to get current game state (observable)
 *
 * This hook automatically re-renders when the state changes.
 * Uses MobX's useObserver internally, so components don't need to be wrapped with observer().
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
  // useObserver makes this hook reactive to GameClient.state changes
  return useObserver(() => client.state);
}

/**
 * Hook to get current player (observable)
 *
 * This hook automatically re-renders when the current player changes.
 * Uses MobX's useObserver internally, so components don't need to be wrapped with observer().
 *
 * Usage:
 * ```tsx
 * const MyComponent = () => {
 *   const player = useCurrentPlayer(); // Automatically reactive!
 *   return <div>{player.name}</div>;
 * };
 * ```
 */
export function useCurrentPlayer() {
  const client = useGameClient();
  return useObserver(() => client.currentPlayer);
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
