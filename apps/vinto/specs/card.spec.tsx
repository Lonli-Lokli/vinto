import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Card } from '../src/app/components/presentational/card';

describe('Card Component', () => {
  describe('Basic Rendering', () => {
    it('should render unrevealed card by default', () => {
      const { container } = render(
        <Card revealed={false} selectionState="default" />
      );
      const card = container.querySelector('[data-revealed="false"]');
      expect(card).toBeTruthy();
    });

    it('should render revealed card with rank', () => {
      const { container } = render(
        <Card rank="A" revealed={true} selectionState="default" />
      );
      const card = container.querySelector('[data-revealed="true"]');
      expect(card).toBeTruthy();
    });

    it('should not render rank image when unrevealed', () => {
      const { container } = render(
        <Card rank="K" revealed={false} selectionState="default" />
      );
      // Card back should be shown instead of rank
      const card = container.querySelector('[data-revealed="false"]');
      expect(card).toBeTruthy();
    });
  });

  describe('Size Variants', () => {
    it('should render with sm size', () => {
      const { container } = render(
        <Card size="sm" selectionState="default" />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with md size (default)', () => {
      const { container } = render(<Card selectionState="default" />);
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with lg size', () => {
      const { container } = render(
        <Card size="lg" selectionState="default" />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with xl size', () => {
      const { container } = render(
        <Card size="xl" selectionState="default" />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });

  describe('Selection States', () => {
    it('should render default selection state without special classes', () => {
      const { container } = render(<Card selectionState="default" />);
      const card = container.firstChild as HTMLElement;
      expect(card.className).not.toContain('card-selectable');
      expect(card.className).not.toContain('card-not-selectable');
    });

    it('should render selectable state with animation class', () => {
      const { container } = render(<Card selectionState="selectable" />);
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('card-selectable');
      expect(card.className).toContain('animate-card-select-pulse');
    });

    it('should render not-selectable state with dimmed class', () => {
      const { container } = render(<Card selectionState="not-selectable" />);
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('card-not-selectable');
    });
  });

  describe('Selection Variants', () => {
    it('should use action variant animation by default for selectable cards', () => {
      const { container } = render(
        <Card selectionState="selectable" selectionVariant="action" />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('animate-card-select-pulse');
    });

    it('should use swap variant animation when specified', () => {
      const { container } = render(
        <Card selectionState="selectable" selectionVariant="swap" />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('animate-swap-select-border');
    });
  });

  describe('Intent Feedback', () => {
    it('should render success intent with checkmark', () => {
      const { container } = render(
        <Card selectionState="default" intent="success" />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('declaration-correct');
      expect(card.textContent).toContain('✓');
    });

    it('should render failure intent with X mark', () => {
      const { container } = render(
        <Card selectionState="default" intent="failure" />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('declaration-incorrect');
      expect(card.textContent).toContain('✗');
    });

    it('should not render intent feedback when intent is undefined', () => {
      const { container } = render(<Card selectionState="default" />);
      const card = container.firstChild as HTMLElement;
      expect(card.className).not.toContain('declaration-correct');
      expect(card.className).not.toContain('declaration-incorrect');
      expect(card.textContent).not.toContain('✓');
      expect(card.textContent).not.toContain('✗');
    });

    it('should render error overlay for failure intent', () => {
      const { container } = render(
        <Card selectionState="default" intent="failure" />
      );
      const errorOverlay = container.querySelector('.bg-error\\/20');
      expect(errorOverlay).toBeTruthy();
    });
  });

  describe('Special States', () => {
    it('should render pending card with animation', () => {
      const { container } = render(
        <Card selectionState="default" isPending={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('animate-pending-card-border');
    });

    it('should render peeked card with blue shadow', () => {
      const { container } = render(
        <Card selectionState="default" isPeeked={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.style.boxShadow).toBeTruthy();
      expect(card.style.outline).toBeTruthy();
    });

    it('should render highlighted card when not selectable', () => {
      const { container } = render(
        <Card selectionState="default" highlighted={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.style.animation).toBeTruthy();
    });

    it('should not apply highlight animation when card is selectable', () => {
      const { container } = render(
        <Card selectionState="selectable" highlighted={true} />
      );
      const card = container.firstChild as HTMLElement;
      // Highlighted animation should not apply when selectable
      expect(card.style.animation).toBeFalsy();
    });

    it('should render bot peeking state on card back', () => {
      const { container } = render(
        <Card selectionState="default" revealed={false} botPeeking={true} />
      );
      // Bot peeking applies special styling to card back
      expect(container.firstChild).toBeTruthy();
    });

    it('should render rotated card with transform class', () => {
      const { container } = render(
        <Card selectionState="default" rotated={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('card-rotated');
      expect(card.className).toContain('transform-gpu');
    });

    it('should not render rotated class by default', () => {
      const { container } = render(<Card selectionState="default" />);
      const card = container.firstChild as HTMLElement;
      expect(card.className).not.toContain('card-rotated');
    });
  });

  describe('Action Target Selection', () => {
    it('should render action target selected class', () => {
      const { container } = render(
        <Card selectionState="default" actionTargetSelected={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('action-target-selected');
    });

    it('should not render action target class by default', () => {
      const { container } = render(<Card selectionState="default" />);
      const card = container.firstChild as HTMLElement;
      expect(card.className).not.toContain('action-target-selected');
    });
  });

  describe('Animation Tracking Attributes', () => {
    it('should render data attributes for animation tracking', () => {
      const { container } = render(
        <Card
          selectionState="default"
          playerId="player-1"
          cardIndex={2}
        />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('data-player-id')).toBe('player-1');
      expect(card.getAttribute('data-card-position')).toBe('2');
    });

    it('should render pending card data attribute', () => {
      const { container } = render(
        <Card selectionState="default" isPending={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('data-pending-card')).toBe('true');
    });

    it('should not render data attributes when playerId is missing', () => {
      const { container } = render(
        <Card selectionState="default" cardIndex={2} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('data-player-id')).toBeNull();
      expect(card.getAttribute('data-card-position')).toBeNull();
    });

    it('should not render data attributes when cardIndex is missing', () => {
      const { container } = render(
        <Card selectionState="default" playerId="player-1" />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('data-player-id')).toBeNull();
      expect(card.getAttribute('data-card-position')).toBeNull();
    });

    it('should render data attributes when cardIndex is 0', () => {
      const { container } = render(
        <Card
          selectionState="default"
          playerId="player-1"
          cardIndex={0}
        />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('data-player-id')).toBe('player-1');
      expect(card.getAttribute('data-card-position')).toBe('0');
    });
  });

  describe('Hidden State', () => {
    it('should render transparent card when hidden', () => {
      const { container } = render(
        <Card selectionState="default" hidden={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.style.opacity).toBe('0');
      expect(card.style.border).toBe('none');
    });

    it('should have aria-hidden attribute when hidden', () => {
      const { container } = render(
        <Card selectionState="default" hidden={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('aria-hidden')).toBe('true');
    });

    it('should preserve data attributes when hidden', () => {
      const { container } = render(
        <Card
          selectionState="default"
          hidden={true}
          playerId="player-1"
          cardIndex={2}
        />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.getAttribute('data-player-id')).toBe('player-1');
      expect(card.getAttribute('data-card-position')).toBe('2');
    });
  });

  describe('Flip Animation', () => {
    it('should render flip container by default', () => {
      const { container } = render(
        <Card rank="K" revealed={true} selectionState="default" />
      );
      const flipContainer = container.querySelector('.flip-card-container');
      expect(flipContainer).toBeTruthy();
    });

    it('should not render flip container when disableFlipAnimation is true', () => {
      const { container } = render(
        <Card
          rank="K"
          revealed={true}
          selectionState="default"
          disableFlipAnimation={true}
        />
      );
      const flipContainer = container.querySelector('.flip-card-container');
      expect(flipContainer).toBeNull();
    });

    it('should render flip-card-revealed class when revealed', () => {
      const { container } = render(
        <Card rank="K" revealed={true} selectionState="default" />
      );
      const flipInner = container.querySelector('.flip-card-inner');
      expect(flipInner?.className).toContain('flip-card-revealed');
    });

    it('should not render flip-card-revealed class when unrevealed', () => {
      const { container } = render(
        <Card rank="K" revealed={false} selectionState="default" />
      );
      const flipInner = container.querySelector('.flip-card-inner');
      expect(flipInner?.className).not.toContain('flip-card-revealed');
    });
  });

  describe('Edge Cases', () => {
    it('should handle pending state taking priority over selectable state', () => {
      const { container } = render(
        <Card selectionState="selectable" isPending={true} />
      );
      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('animate-pending-card-border');
      expect(card.className).not.toContain('animate-card-select-pulse');
    });

    it('should render with all props combined', () => {
      const { container } = render(
        <Card
          rank="Q"
          revealed={true}
          size="lg"
          highlighted={true}
          botPeeking={false}
          isPeeked={true}
          rotated={false}
          playerId="player-2"
          cardIndex={3}
          isPending={false}
          selectionState="selectable"
          selectionVariant="swap"
          intent="success"
          actionTargetSelected={true}
        />
      );
      const card = container.firstChild as HTMLElement;
      expect(card).toBeTruthy();
      expect(card.getAttribute('data-player-id')).toBe('player-2');
      expect(card.getAttribute('data-card-position')).toBe('3');
    });

    it('should handle revealed card without rank gracefully', () => {
      const { container } = render(
        <Card revealed={true} selectionState="default" />
      );
      // Should still render, but without rank image
      expect(container.firstChild).toBeTruthy();
    });
  });
});
