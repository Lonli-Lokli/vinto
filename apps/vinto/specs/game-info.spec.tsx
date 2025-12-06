import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { GameInfo } from '../src/app/components/presentational/game-info';

describe('GameInfo Component', () => {
  describe('Draw Pile Count Display', () => {
    it('should render draw pile count', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id-12345678" />
      );
      const count = container.querySelector('.font-semibold');
      expect(count?.textContent).toBe('42');
    });

    it('should render zero count', () => {
      const { container } = render(
        <GameInfo drawPileCount={0} gameId="test-game-id" />
      );
      const count = container.querySelector('.font-semibold');
      expect(count?.textContent).toBe('0');
    });

    it('should render single digit count', () => {
      const { container } = render(
        <GameInfo drawPileCount={5} gameId="test-game-id" />
      );
      const count = container.querySelector('.font-semibold');
      expect(count?.textContent).toBe('5');
    });

    it('should render large count', () => {
      const { container } = render(
        <GameInfo drawPileCount={999} gameId="test-game-id" />
      );
      const count = container.querySelector('.font-semibold');
      expect(count?.textContent).toBe('999');
    });

    it('should have correct styling for count', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const count = container.querySelector('.font-semibold');
      expect(count?.className).toContain('text-sm');
      expect(count?.className).toContain('font-semibold');
      expect(count?.className).toContain('text-primary');
    });
  });

  describe('Cards Left Label', () => {
    it('should render "cards left" label', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const label = container.querySelector('.text-secondary');
      expect(label?.textContent).toBe('cards left');
    });

    it('should have correct styling for label', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const label = container.querySelector('.text-secondary');
      expect(label?.className).toContain('text-xs');
      expect(label?.className).toContain('text-secondary');
    });
  });

  describe('Game ID Display', () => {
    it('should render last 8 characters of game ID', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="abcdef1234567890" />
      );
      const gameIdText = container.querySelector('.text-muted');
      expect(gameIdText?.textContent).toBe('Game ID: 34567890');
    });

    it('should handle game ID shorter than 8 characters', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="short" />
      );
      const gameIdText = container.querySelector('.text-muted');
      expect(gameIdText?.textContent).toBe('Game ID: short');
    });

    it('should handle exactly 8 character game ID', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="12345678" />
      );
      const gameIdText = container.querySelector('.text-muted');
      expect(gameIdText?.textContent).toBe('Game ID: 12345678');
    });

    it('should handle empty game ID', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="" />
      );
      const gameIdText = container.querySelector('.text-muted');
      expect(gameIdText?.textContent).toBe('Game ID: ');
    });

    it('should have correct styling for game ID', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id-12345678" />
      );
      const gameIdText = container.querySelector('.text-muted');
      expect(gameIdText?.className).toContain('text-xs');
      expect(gameIdText?.className).toContain('text-muted');
    });
  });

  describe('Layout Structure', () => {
    it('should have right-aligned text for draw pile section', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const section = container.querySelector('.text-right');
      expect(section).toBeTruthy();
    });

    it('should have centered text for game ID section', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const section = container.querySelector('.text-center');
      expect(section).toBeTruthy();
    });

    it('should have mt-6 margin on game ID section', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const section = container.querySelector('.mt-6');
      expect(section).toBeTruthy();
    });

    it('should have space-y-1 on game ID section', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="test-game-id" />
      );
      const section = container.querySelector('.space-y-1');
      expect(section).toBeTruthy();
    });
  });

  describe('Edge Cases', () => {
    it('should handle negative draw pile count', () => {
      const { container } = render(
        <GameInfo drawPileCount={-5} gameId="test-game-id" />
      );
      const count = container.querySelector('.font-semibold');
      expect(count?.textContent).toBe('-5');
    });

    it('should handle very long game ID', () => {
      const { container } = render(
        <GameInfo
          drawPileCount={42}
          gameId="this-is-a-very-long-game-id-with-many-characters-1234567890"
        />
      );
      const gameIdText = container.querySelector('.text-muted');
      // Should show last 8 characters
      expect(gameIdText?.textContent).toBe('Game ID: 34567890');
    });

    it('should handle game ID with special characters', () => {
      const { container } = render(
        <GameInfo drawPileCount={42} gameId="special-id-@#$%^&*()" />
      );
      const gameIdText = container.querySelector('.text-muted');
      expect(gameIdText?.textContent).toBe('Game ID: #$%^&*()');
    });
  });
});
