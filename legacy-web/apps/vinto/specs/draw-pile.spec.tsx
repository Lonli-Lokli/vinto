import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { DrawPile } from '../src/app/components/presentational/draw-pile';

describe('DrawPile Component', () => {
  describe('Basic Rendering', () => {
    it('should render with default props', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with data-deck-pile attribute', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );
      const pile = container.querySelector('[data-deck-pile="true"]');
      expect(pile).toBeTruthy();
    });

    it('should render with data-testid', () => {
      const { getByTestId } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );
      expect(getByTestId('draw-pile')).toBeTruthy();
    });

    it('should display DRAW label', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );
      expect(container.textContent).toContain('DRAW');
    });
  });

  describe('Size Variants', () => {
    it('should render with sm size', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="sm"
          isMobile={false}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with md size', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="md"
          isMobile={false}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with lg size (default)', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with xl size', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="xl"
          isMobile={false}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });

  describe('Click Behavior', () => {
    it('should call onClick when clickable and card is clicked', () => {
      const handleClick = vi.fn();
      const { getByTestId } = render(
        <DrawPile
          clickable={true}
          onClick={handleClick}
          size="lg"
          isMobile={false}
        />
      );

      const pile = getByTestId('draw-pile');
      fireEvent.click(pile.firstChild as HTMLElement);

      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('should not call onClick when not clickable', () => {
      const handleClick = vi.fn();
      const { getByTestId } = render(
        <DrawPile
          clickable={false}
          onClick={handleClick}
          size="lg"
          isMobile={false}
        />
      );

      const pile = getByTestId('draw-pile');
      fireEvent.click(pile.firstChild as HTMLElement);

      expect(handleClick).not.toHaveBeenCalled();
    });
  });

  describe('Mobile vs Desktop', () => {
    it('should render for desktop', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render for mobile', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={true}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });

  describe('Layout', () => {
    it('should have flex column layout', () => {
      const { container } = render(
        <DrawPile
          clickable={false}
          onClick={() => {}}
          size="lg"
          isMobile={false}
        />
      );

      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper.className).toContain('flex');
      expect(wrapper.className).toContain('flex-col');
      expect(wrapper.className).toContain('items-center');
      expect(wrapper.className).toContain('justify-center');
    });
  });
});
