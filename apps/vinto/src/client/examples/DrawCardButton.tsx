// client/examples/DrawCardButton.tsx
// Example component showing how to use GameClient

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useGameClient, useDispatch } from '../GameClientContext';
import { GameActions } from '../../engine/types';

/**
 * Example: Draw Card Button
 *
 * This component demonstrates:
 * 1. Using useGameClient to access game state
 * 2. Using useDispatch to dispatch actions
 * 3. Using computed properties for UI logic
 * 4. Observing state changes with MobX
 */
export const DrawCardButton: React.FC = observer(() => {
  const gameClient = useGameClient();
  const dispatch = useDispatch();

  // Check if draw is allowed
  const canDraw = gameClient.canDrawCard;
  const isMyTurn = gameClient.isPlayerTurn('human-1'); // Assuming human player ID

  const handleDraw = () => {
    // Dispatch DRAW_CARD action
    const action = GameActions.drawCard('human-1');
    dispatch(action);
  };

  return (
    <button
      onClick={handleDraw}
      disabled={!canDraw || !isMyTurn}
      style={{
        padding: '12px 24px',
        fontSize: '16px',
        backgroundColor: canDraw && isMyTurn ? '#4CAF50' : '#ccc',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: canDraw && isMyTurn ? 'pointer' : 'not-allowed',
      }}
    >
      Draw Card ({gameClient.drawPileCount} left)
    </button>
  );
});

/**
 * Example: Take Discard Button
 */
export const TakeDiscardButton: React.FC = observer(() => {
  const gameClient = useGameClient();
  const dispatch = useDispatch();

  const canTake = gameClient.canTakeDiscard;
  const isMyTurn = gameClient.isPlayerTurn('human-1');
  const topCard = gameClient.topDiscardCard;

  const handleTakeDiscard = () => {
    const action = GameActions.takeDiscard('human-1');
    dispatch(action);
  };

  return (
    <button
      onClick={handleTakeDiscard}
      disabled={!canTake || !isMyTurn}
      style={{
        padding: '12px 24px',
        fontSize: '16px',
        backgroundColor: canTake && isMyTurn ? '#2196F3' : '#ccc',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: canTake && isMyTurn ? 'pointer' : 'not-allowed',
      }}
    >
      Take Discard {topCard ? `(${topCard.rank})` : ''}
    </button>
  );
});

/**
 * Example: Game Status Display
 */
export const GameStatus: React.FC = observer(() => {
  const gameClient = useGameClient();

  return (
    <div style={{ padding: '16px', border: '1px solid #ddd', borderRadius: '4px' }}>
      <h3>Game Status</h3>
      <div>
        <strong>Phase:</strong> {gameClient.phaseString}
      </div>
      <div>
        <strong>Current Player:</strong> {gameClient.currentPlayer.name}
      </div>
      <div>
        <strong>Turn:</strong> {gameClient.state.turnCount}
      </div>
      <div>
        <strong>Draw Pile:</strong> {gameClient.drawPileCount} cards
      </div>
      <div>
        <strong>Discard Pile:</strong> {gameClient.discardPileCount} cards
      </div>
      {gameClient.hasPendingAction && (
        <div style={{ marginTop: '8px', color: '#f59e0b' }}>
          <strong>Pending Card:</strong> {gameClient.pendingCard?.rank}
        </div>
      )}
      {gameClient.vintoCaller && (
        <div style={{ marginTop: '8px', color: '#ef4444' }}>
          <strong>Vinto Called By:</strong> {gameClient.vintoCaller.name}
        </div>
      )}
    </div>
  );
});

/**
 * Example: Full Turn Flow Component
 *
 * This shows a complete turn sequence:
 * 1. Draw or Take Discard
 * 2. Swap with hand
 * 3. Discard or Use Action
 */
export const TurnFlow: React.FC = observer(() => {
  const gameClient = useGameClient();
  const dispatch = useDispatch();

  const currentPlayer = gameClient.currentPlayer;
  const phase = gameClient.state.subPhase;
  const pendingCard = gameClient.pendingCard;

  // Handle swap with position
  const handleSwap = (position: number) => {
    const action = GameActions.swapCard('human-1', position);
    dispatch(action);
  };

  // Handle discard
  const handleDiscard = () => {
    const action = GameActions.discardCard('human-1');
    dispatch(action);
  };

  return (
    <div style={{ padding: '16px', border: '1px solid #ddd', borderRadius: '4px' }}>
      <h3>Your Turn</h3>

      {/* Phase 1: Draw or Take */}
      {phase === 'idle' && (
        <div>
          <p>Choose an action:</p>
          <DrawCardButton />
          <TakeDiscardButton />
        </div>
      )}

      {/* Phase 2: Swap with hand */}
      {phase === 'choosing' && pendingCard && (
        <div>
          <p>Swap drawn card ({pendingCard.rank}) with a card in your hand:</p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {currentPlayer.cards.map((card, index) => (
              <button
                key={card.id}
                onClick={() => handleSwap(index)}
                style={{
                  padding: '8px 16px',
                  fontSize: '14px',
                  backgroundColor: '#8B5CF6',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                }}
              >
                Position {index + 1}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Phase 3: Discard or Use Action */}
      {phase === 'selecting' && pendingCard && (
        <div>
          <p>What to do with this card ({pendingCard.rank})?</p>
          <button
            onClick={handleDiscard}
            style={{
              padding: '8px 16px',
              fontSize: '14px',
              backgroundColor: '#EF4444',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
            }}
          >
            Discard
          </button>
          {/* TODO: Add "Use Action" button */}
        </div>
      )}
    </div>
  );
});
