import { render } from '@testing-library/react';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { HorizontalPlayerCards } from '../src/app/components/presentational/horizontal-player-cards';
import { VerticalPlayerCards } from '../src/app/components/presentational/vertical-player-cards';
import type { PlayerState, GamePhase } from '@vinto/shapes';
import { CardSize } from '../src/app/helpers';

// Mock dependencies
vi.mock('../src/app/components/di-provider', () => ({
  useCardAnimationStore: () => ({
    isCardAnimating: vi.fn(() => false),
    getPlayerAnimations: vi.fn(() => []),
  }),
}));

vi.mock('../src/app/components/presentational/clickable-card', () => ({
  ClickableCard: ({ isBotKnown, playerId, cardIndex }: any) => (
    <div
      data-testid={`card-${playerId}-${cardIndex}`}
      data-bot-known={isBotKnown}
    />
  ),
}));

describe('Bot Known Card Indicator', () => {
  let humanPlayer: PlayerState;
  let botPlayer: PlayerState;
  let botPlayer2: PlayerState;
  const cardSize: CardSize = 'md';

  beforeEach(() => {
    humanPlayer = {
      id: 'human-1',
      name: 'Human Player',
      isHuman: true,
      isBot: false,
      cards: [
        { id: 'h1', rank: '2' },
        { id: 'h2', rank: '3' },
      ],
      knownCardPositions: [0],
      coalitionWith: [],
    } as PlayerState;

    botPlayer = {
      id: 'bot-1',
      name: 'Bot Player',
      isHuman: false,
      isBot: true,
      cards: [
        { id: 'b1', rank: '5' },
        { id: 'b2', rank: '6' },
        { id: 'b3', rank: '7' },
      ],
      knownCardPositions: [0, 2], // Bot knows cards at positions 0 and 2
      coalitionWith: [],
      opponentKnowledge: {},
    } as PlayerState;

    botPlayer2 = {
      id: 'bot-2',
      name: 'Bot Player 2',
      isHuman: false,
      isBot: true,
      cards: [
        { id: 'b4', rank: '4' },
        { id: 'b5', rank: '8' },
      ],
      knownCardPositions: [],
      coalitionWith: [],
      opponentKnowledge: {
        'bot-1': {
          knownCards: {
            1: { id: 'b2', rank: '6' }, // Bot 2 knows about bot-1's card at position 1
          },
        },
      },
    } as PlayerState;
  });

  describe('HorizontalPlayerCards', () => {
    it('shows bot-known indicator when human calls Vinto in final phase', () => {
      const { getByTestId } = render(
        <HorizontalPlayerCards
          player={botPlayer}
          position="top"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // Cards at positions 0 and 2 should be marked as bot-known (from bot's own knowledge)
      // Card at position 1 should also be marked as bot-known (from bot2's opponent knowledge)
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('true');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('true'); // Known by bot-2
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('true');
    });

    it('does not show indicator before final phase', () => {
      const { getByTestId } = render(
        <HorizontalPlayerCards
          player={botPlayer}
          position="top"
          cardSize={cardSize}
          gamePhase="playing"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // No cards should be marked as bot-known during playing phase
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('false');
    });

    it('does not show indicator when bot calls Vinto', () => {
      const { getByTestId } = render(
        <HorizontalPlayerCards
          player={botPlayer}
          position="top"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId="bot-2" // Different bot is Vinto caller
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // No cards should be marked when observing player is not Vinto caller
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('false');
    });

    it('does not show indicator for human player cards', () => {
      const { getByTestId } = render(
        <HorizontalPlayerCards
          player={humanPlayer}
          position="bottom"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // Human player's cards should never show bot-known indicator
      expect(getByTestId('card-human-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-human-1-1').dataset.botKnown).toBe('false');
    });

    it('does not show indicator when vintoCallerId is null', () => {
      const { getByTestId } = render(
        <HorizontalPlayerCards
          player={botPlayer}
          position="top"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={null}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('false');
    });

    it('shows indicator for cards known by OTHER bots (cross-bot knowledge)', () => {
      const { getByTestId } = render(
        <HorizontalPlayerCards
          player={botPlayer}
          position="top"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // All three cards should be marked as known because:
      // - Position 0 and 2: known by bot-1 itself
      // - Position 1: known by bot-2 (via opponentKnowledge)
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('true');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('true'); // Known by bot-2
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('true');
    });
  });

  describe('VerticalPlayerCards', () => {
    it('shows bot-known indicator when human calls Vinto in final phase', () => {
      const { getByTestId } = render(
        <VerticalPlayerCards
          player={botPlayer}
          position="left"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // All three cards should be marked as known (including cross-bot knowledge)
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('true');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('true'); // Known by bot-2
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('true');
    });

    it('does not show indicator before final phase', () => {
      const { getByTestId } = render(
        <VerticalPlayerCards
          player={botPlayer}
          position="left"
          cardSize={cardSize}
          gamePhase="playing"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // No cards should be marked as bot-known during playing phase
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('false');
    });

    it('does not show indicator when bot calls Vinto', () => {
      const { getByTestId } = render(
        <VerticalPlayerCards
          player={botPlayer}
          position="right"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId="bot-2" // Different bot is Vinto caller
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // No cards should be marked when observing player is not Vinto caller
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('false');
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('false');
    });

    it('does not show indicator for human player cards', () => {
      const { getByTestId } = render(
        <VerticalPlayerCards
          player={humanPlayer}
          position="left"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // Human player's cards should never show bot-known indicator
      expect(getByTestId('card-human-1-0').dataset.botKnown).toBe('false');
      expect(getByTestId('card-human-1-1').dataset.botKnown).toBe('false');
    });

    it('shows indicator for cards known by OTHER bots (cross-bot knowledge)', () => {
      const { getByTestId } = render(
        <VerticalPlayerCards
          player={botPlayer}
          position="left"
          cardSize={cardSize}
          gamePhase="final"
          isSelectingSwapPosition={false}
          swapPosition={null}
          isSelectingActionTarget={false}
          temporarilyVisibleCards={new Map()}
          highlightedCards={new Set()}
          coalitionLeaderId={null}
          observingPlayer={humanPlayer}
          vintoCallerId={humanPlayer.id}
          allPlayers={[humanPlayer, botPlayer, botPlayer2]}
        />
      );

      // All three cards should be marked as known because:
      // - Position 0 and 2: known by bot-1 itself
      // - Position 1: known by bot-2 (via opponentKnowledge)
      expect(getByTestId('card-bot-1-0').dataset.botKnown).toBe('true');
      expect(getByTestId('card-bot-1-1').dataset.botKnown).toBe('true'); // Known by bot-2
      expect(getByTestId('card-bot-1-2').dataset.botKnown).toBe('true');
    });
  });
});
