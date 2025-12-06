import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { ClickableCard } from '../src/app/components/presentational/clickable-card';

describe('ClickableCard Component', () => {
  describe('Basic Rendering', () => {
    it('should render with default selection state', () => {
      const { container } = render(
        <ClickableCard
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with revealed card', () => {
      const { container } = render(
        <ClickableCard
          rank="A"
          revealed={true}
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should pass through card props', () => {
      const { container } = render(
        <ClickableCard
          rank="K"
          revealed={true}
          size="lg"
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });

  describe('Selection States', () => {
    it('should render with selectable state', () => {
      const { container } = render(
        <ClickableCard
          selectionState="selectable"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with selected state', () => {
      const { container } = render(
        <ClickableCard
          selectionState="selected"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with disabled state', () => {
      const { container } = render(
        <ClickableCard
          selectionState="disabled"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });

  describe('Click Behavior', () => {
    it('should call onClick when selectable and clicked', () => {
      const handleClick = vi.fn();
      const { container } = render(
        <ClickableCard
          selectionState="selectable"
          onClick={handleClick}
        />
      );

      const card = container.firstChild as HTMLElement;
      fireEvent.click(card);

      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('should not call onClick when not selectable', () => {
      const handleClick = vi.fn();
      const { container } = render(
        <ClickableCard
          selectionState="default"
          onClick={handleClick}
        />
      );

      const card = container.firstChild as HTMLElement;
      fireEvent.click(card);

      expect(handleClick).not.toHaveBeenCalled();
    });

    it('should not call onClick when disabled', () => {
      const handleClick = vi.fn();
      const { container } = render(
        <ClickableCard
          selectionState="disabled"
          onClick={handleClick}
        />
      );

      const card = container.firstChild as HTMLElement;
      fireEvent.click(card);

      expect(handleClick).not.toHaveBeenCalled();
    });

    it('should not call onClick when selected', () => {
      const handleClick = vi.fn();
      const { container } = render(
        <ClickableCard
          selectionState="selected"
          onClick={handleClick}
        />
      );

      const card = container.firstChild as HTMLElement;
      fireEvent.click(card);

      expect(handleClick).not.toHaveBeenCalled();
    });
  });

  describe('Data Attributes', () => {
    it('should forward data-testid attribute', () => {
      const { container } = render(
        <ClickableCard
          selectionState="default"
          onClick={() => {}}
          data-testid="my-card"
        />
      );

      const card = container.querySelector('[data-testid="my-card"]');
      expect(card).toBeTruthy();
    });
  });

  describe('Size Variants', () => {
    it('should render with sm size', () => {
      const { container } = render(
        <ClickableCard
          size="sm"
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with md size', () => {
      const { container } = render(
        <ClickableCard
          size="md"
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with lg size', () => {
      const { container } = render(
        <ClickableCard
          size="lg"
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with xl size', () => {
      const { container } = render(
        <ClickableCard
          size="xl"
          selectionState="default"
          onClick={() => {}}
        />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });
});
