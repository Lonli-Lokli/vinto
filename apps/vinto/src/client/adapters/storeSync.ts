// client/adapters/storeSync.ts
/**
 * Store Synchronization Adapter (TEMPORARY)
 *
 * This adapter keeps old MobX stores in sync with the new GameClient
 * during the migration period. Once all components are migrated,
 * this file and the old stores can be removed.
 *
 * Direction: GameClient → Stores (one-way)
 *
 * Why one-way?
 * - GameClient is the new source of truth
 * - Stores are read-only during migration
 * - Prevents circular updates
 */

import { autorun, reaction } from 'mobx';
import { GameClient } from '../GameClient';
import type { GameStore, PlayerStore, DeckStore, GamePhaseStore } from '../../app/stores';

export interface StoreRefs {
  gameStore: GameStore;
  playerStore: PlayerStore;
  deckStore: DeckStore;
  gamePhaseStore: GamePhaseStore;
}

/**
 * Setup one-way sync from GameClient to old stores
 *
 * Call this once when initializing the app:
 * ```tsx
 * const gameClient = new GameClient(quickStartGame());
 * const stores = { gameStore, playerStore, deckStore, gamePhaseStore };
 * syncGameClientToStores(gameClient, stores);
 * ```
 */
export function syncGameClientToStores(
  gameClient: GameClient,
  stores: StoreRefs
): () => void {
  const disposers: Array<() => void> = [];

  // Sync players
  disposers.push(
    reaction(
      () => gameClient.state.players,
      (enginePlayers) => {
        console.log('[StoreSync] Syncing players to PlayerStore');
        // This would need actual implementation based on your store structure
        // stores.playerStore.updateFromEngineState(enginePlayers);
      },
      { fireImmediately: true }
    )
  );

  // Sync draw pile
  disposers.push(
    reaction(
      () => gameClient.state.drawPile,
      (drawPile) => {
        console.log('[StoreSync] Syncing drawPile to DeckStore');
        // stores.deckStore.setDrawPile(drawPile);
      },
      { fireImmediately: true }
    )
  );

  // Sync discard pile
  disposers.push(
    reaction(
      () => gameClient.state.discardPile,
      (discardPile) => {
        console.log('[StoreSync] Syncing discardPile to DeckStore');
        // stores.deckStore.setDiscardPile(discardPile);
      },
      { fireImmediately: true }
    )
  );

  // Sync game phase
  disposers.push(
    reaction(
      () => ({ phase: gameClient.state.phase, subPhase: gameClient.state.subPhase }),
      ({ phase, subPhase }) => {
        console.log('[StoreSync] Syncing phase to GamePhaseStore', { phase, subPhase });
        // stores.gamePhaseStore.setPhase(phase);
        // stores.gamePhaseStore.setSubPhase(subPhase);
      },
      { fireImmediately: true }
    )
  );

  // Sync current player index
  disposers.push(
    reaction(
      () => gameClient.state.currentPlayerIndex,
      (currentPlayerIndex) => {
        console.log('[StoreSync] Syncing currentPlayerIndex to PlayerStore');
        // stores.playerStore.setCurrentPlayerIndex(currentPlayerIndex);
      },
      { fireImmediately: true }
    )
  );

  // Return cleanup function
  return () => {
    console.log('[StoreSync] Disposing all sync reactions');
    disposers.forEach((dispose) => dispose());
  };
}

/**
 * Debug utility: Log all sync events
 */
export function enableSyncLogging(gameClient: GameClient): () => void {
  return autorun(() => {
    const state = gameClient.state;
    console.group('[StoreSync] State Update');
    console.log('Phase:', state.phase, '/', state.subPhase);
    console.log('Current Player:', state.currentPlayerIndex);
    console.log('Draw Pile:', state.drawPile.length);
    console.log('Discard Pile:', state.discardPile.length);
    console.groupEnd();
  });
}

/**
 * Verify sync is working
 *
 * Call this to check if GameClient and stores are in sync:
 * ```tsx
 * const issues = verifySyncState(gameClient, stores);
 * if (issues.length > 0) {
 *   console.error('Sync issues found:', issues);
 * }
 * ```
 */
export function verifySyncState(
  gameClient: GameClient,
  stores: StoreRefs
): string[] {
  const issues: string[] = [];

  // Check draw pile count
  const clientDrawCount = gameClient.state.drawPile.length;
  const storeDrawCount = stores.deckStore.drawPile.length;
  if (clientDrawCount !== storeDrawCount) {
    issues.push(
      `Draw pile mismatch: GameClient=${clientDrawCount}, Store=${storeDrawCount}`
    );
  }

  // Check discard pile count
  const clientDiscardCount = gameClient.state.discardPile.length;
  const storeDiscardCount = stores.deckStore.discardPile.length;
  if (clientDiscardCount !== storeDiscardCount) {
    issues.push(
      `Discard pile mismatch: GameClient=${clientDiscardCount}, Store=${storeDiscardCount}`
    );
  }

  // Check player count
  const clientPlayerCount = gameClient.state.players.length;
  const storePlayerCount = stores.playerStore.players.length;
  if (clientPlayerCount !== storePlayerCount) {
    issues.push(
      `Player count mismatch: GameClient=${clientPlayerCount}, Store=${storePlayerCount}`
    );
  }

  return issues;
}
