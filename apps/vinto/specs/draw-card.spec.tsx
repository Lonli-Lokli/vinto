import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { DrawCard } from '../src/app/components/presentational/draw-card';

describe('DrawCard Component', () => {
  describe('Basic Rendering', () => {
    it('should render with default size', () => {
      const { container } = render(
        <DrawCard clickable={false} onClick={() => {}} />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with sm size', () => {
      const { container } = render(
        <DrawCard size="sm" clickable={false} onClick={() => {}} />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with md size', () => {
      const { container } = render(
        <DrawCard size="md" clickable={false} onClick={() => {}} />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with lg size (default)', () => {
      const { container } = render(
        <DrawCard size="lg" clickable={false} onClick={() => {}} />
      );
      expect(container.firstChild).toBeTruthy();
    });

    it('should render with xl size', () => {
      const { container } = render(
        <DrawCard size="xl" clickable={false} onClick={() => {}} />
      );
      expect(container.firstChild).toBeTruthy();
    });
  });

  describe('Clickable Behavior', () => {
    it('should call onClick when clickable is true and card is clicked', () => {
      const handleClick = vi.fn();
      const { container } = render(
        <DrawCard clickable={true} onClick={handleClick} />
      );

      const card = container.firstChild as HTMLElement;
      fireEvent.click(card);

      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('should not call onClick when clickable is false', () => {
      const handleClick = vi.fn();
      const { container } = render(
        <DrawCard clickable={false} onClick={handleClick} />
      );

      const card = container.firstChild as HTMLElement;
      fireEvent.click(card);

      expect(handleClick).not.toHaveBeenCalled();
    });

    it('should have hover styles when clickable', () => {
      const { container } = render(
        <DrawCard clickable={true} onClick={() => {}} />
      );

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('cursor-pointer');
      expect(card.className).toContain('hover:scale-105');
    });

    it('should not have hover styles when not clickable', () => {
      const { container } = render(
        <DrawCard clickable={false} onClick={() => {}} />
      );

      const card = container.firstChild as HTMLElement;
      expect(card.className).not.toContain('cursor-pointer');
      expect(card.className).not.toContain('hover:scale-105');
    });
  });

  describe('CSS Classes', () => {
    it('should have flex layout classes', () => {
      const { container } = render(
        <DrawCard clickable={false} onClick={() => {}} />
      );

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('flex');
      expect(card.className).toContain('flex-col');
      expect(card.className).toContain('items-center');
      expect(card.className).toContain('justify-center');
    });

    it('should have transition classes when clickable', () => {
      const { container } = render(
        <DrawCard clickable={true} onClick={() => {}} />
      );

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('transition-all');
      expect(card.className).toContain('duration-150');
    });

    it('should have active scale class when clickable', () => {
      const { container } = render(
        <DrawCard clickable={true} onClick={() => {}} />
      );

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('active:scale-95');
    });

    it('should have shadow effect when clickable', () => {
      const { container } = render(
        <DrawCard clickable={true} onClick={() => {}} />
      );

      const card = container.firstChild as HTMLElement;
      expect(card.className).toContain('hover:shadow-lg');
    });
  });
});
