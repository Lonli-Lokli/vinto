// client/GameClientDebug.tsx
'use client';

import { useEffect } from 'react';
import { useGameClient } from './game-client-context';

/**
 * GameClient Debug Utility
 *
 * Exposes GameClient to browser console for debugging and testing.
 *
 * Usage in browser console:
 * ```javascript
 * // Access the client
 * __gameClient__
 *
 * // Check state
 * __gameClient__.state
 * __gameClient__.currentPlayer
 * __gameClient__.canDrawCard
 *
 * // Dispatch actions
 * __gameClient__.dispatch({ type: 'DRAW_CARD', payload: { playerId: 'human-1' } })
 *
 * // Helper methods (also available)
 * __gameClient__.logState()
 * __gameClient__.logActions()
 * ```
 */
export function GameClientDebugProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const gameClient = useGameClient();

  useEffect(() => {
    // Expose to window for console access
    if (typeof window !== 'undefined') {
      // @ts-expect-error - Intentionally adding to window for debugging
      window.__gameClient__ = gameClient;

      // Add convenience methods
      // @ts-expect-error - Adding debug methods
      window.__gameClient__.logState = () => {
        console.group('🎮 GameClient State');
        console.log(
          'Phase:',
          gameClient.state.phase,
          '/',
          gameClient.state.subPhase
        );
        console.log('Turn:', gameClient.state.turnNumber);
        console.log('Round:', gameClient.state.roundNumber);
        console.log(
          'Current Player:',
          gameClient.currentPlayer.name,
          `(${gameClient.currentPlayer.id})`
        );
        console.log('Is My Turn:', gameClient.isCurrentPlayerHuman);
        console.log('Draw Pile:', gameClient.drawPileCount, 'cards');
        console.log('Discard Pile:', gameClient.discardPileCount, 'cards');
        console.log('Top Discard:', gameClient.topDiscardCard);
        console.log('Pending Card:', gameClient.pendingCard);
        console.log('Can Draw:', gameClient.canDrawCard);
        console.log('Can Take Discard:', gameClient.canTakeDiscard);
        console.log('Final Turn:', gameClient.state.finalTurnTriggered);
        console.groupEnd();
      };

      // @ts-expect-error - Adding debug methods
      window.__gameClient__.logPlayers = () => {
        console.group('👥 Players');
        gameClient.state.players.forEach((player, index) => {
          console.group(
            `${index === gameClient.state.currentPlayerIndex ? '➤' : ' '} ${
              player.name
            } (${player.id})`
          );
          console.log('Type:', player.isHuman ? '👤 Human' : '🤖 Bot');
          console.log('Cards:', player.cards.length);
          console.log('Known Positions:', player.knownCardPositions.length);
          console.log('Coalition:', player.coalitionWith);
          console.log('Vinto Caller:', player.isVintoCaller);
          console.groupEnd();
        });
        console.groupEnd();
      };

      // @ts-expect-error - Adding debug methods
      window.__gameClient__.logActions = () => {
        console.group('⚡ Available Actions');
        console.log('canDrawCard:', gameClient.canDrawCard);
        console.log('canTakeDiscard:', gameClient.canTakeDiscard);
        console.log('hasPendingAction:', gameClient.hasPendingAction);
        console.log('hasTossIn:', gameClient.hasTossIn);
        console.groupEnd();
      };

      // @ts-expect-error - Adding debug methods
      window.__gameClient__.quickDraw = () => {
        const playerId = gameClient.currentPlayer.id;
        console.log('🎴 Drawing card for:', playerId);
        gameClient.dispatch({ type: 'DRAW_CARD', payload: { playerId } });
      };

      // @ts-expect-error - Adding debug methods
      window.__gameClient__.quickSwap = (position: number) => {
        const playerId = gameClient.currentPlayer.id;
        console.log(`🔄 Swapping card at position ${position}`);
        gameClient.dispatch({
          type: 'SWAP_CARD',
          payload: { playerId, position },
        });
      };

      // @ts-expect-error - Adding debug methods
      window.__gameClient__.quickDiscard = () => {
        const playerId = gameClient.currentPlayer.id;
        console.log('🗑️ Discarding card');
        gameClient.dispatch({ type: 'DISCARD_CARD', payload: { playerId } });
      };

      console.log(
        '%c🎮 GameClient Debug Mode Enabled',
        'color: #00ff00; font-weight: bold; font-size: 14px;'
      );
      console.log(
        '%cAccess with: window.__gameClient__',
        'color: #00aaff; font-size: 12px;'
      );
      console.log(
        '%cTry: __gameClient__.logState()',
        'color: #00aaff; font-size: 12px;'
      );
      console.log(
        '%cQuick actions: __gameClient__.quickDraw()',
        'color: #00aaff; font-size: 12px;'
      );
    }

    return () => {
      // Cleanup on unmount
      if (typeof window !== 'undefined') {
        // @ts-expect-error - Removing debug reference
        delete window.__gameClient__;
      }
    };
  }, [gameClient]);

  return <>{children}</>;
}

/**
 * TypeScript declarations for console access
 *
 * Add this to your global.d.ts:
 * ```typescript
 * declare global {
 *   interface Window {
 *     __gameClient__: GameClient & {
 *       logState: () => void;
 *       logPlayers: () => void;
 *       logActions: () => void;
 *       quickDraw: () => void;
 *       quickSwap: (position: number) => void;
 *       quickDiscard: () => void;
 *       quickAdvanceTurn: () => void;
 *     };
 *   }
 * }
 * ```
 */
