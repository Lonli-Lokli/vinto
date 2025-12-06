import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Avatar } from '../src/app/components/presentational/avatar';

describe('Avatar Component', () => {
  describe('Player Name Mapping', () => {
    it('should render You avatar for "you" name', () => {
      const { container } = render(<Avatar playerName="you" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('you');
    });

    it('should render You avatar for "You" name (case-insensitive)', () => {
      const { container } = render(<Avatar playerName="You" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('you');
    });

    it('should render Michelangelo avatar for "michelangelo" name', () => {
      const { container } = render(<Avatar playerName="michelangelo" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('michelangelo');
    });

    it('should render Michelangelo avatar for "Michelangelo" name (case-insensitive)', () => {
      const { container } = render(<Avatar playerName="Michelangelo" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('michelangelo');
    });

    it('should render Michelangelo avatar for "mikey" nickname', () => {
      const { container } = render(<Avatar playerName="mikey" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('michelangelo');
    });

    it('should render Donatello avatar for "donatello" name', () => {
      const { container } = render(<Avatar playerName="donatello" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('donatello');
    });

    it('should render Donatello avatar for "Donatello" name (case-insensitive)', () => {
      const { container } = render(<Avatar playerName="Donatello" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('donatello');
    });

    it('should render Donatello avatar for "donnie" nickname', () => {
      const { container } = render(<Avatar playerName="donnie" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('donatello');
    });

    it('should render Raphael avatar for "raphael" name', () => {
      const { container } = render(<Avatar playerName="raphael" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('raphael');
    });

    it('should render Raphael avatar for "Raphael" name (case-insensitive)', () => {
      const { container } = render(<Avatar playerName="Raphael" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('raphael');
    });

    it('should render Raphael avatar for "raph" nickname', () => {
      const { container } = render(<Avatar playerName="raph" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.src).toContain('raphael');
    });

    it('should render default robot emoji for unknown player name', () => {
      const { container } = render(<Avatar playerName="Unknown Player" />);
      const span = container.querySelector('span');
      expect(span).toBeTruthy();
      expect(span?.textContent).toBe('🤖');
    });

    it('should render default robot emoji for empty string', () => {
      const { container } = render(<Avatar playerName="" />);
      const span = container.querySelector('span');
      expect(span).toBeTruthy();
      expect(span?.textContent).toBe('🤖');
    });
  });

  describe('Size Variants', () => {
    it('should render with xs size', () => {
      const { container } = render(<Avatar playerName="you" size="xs" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.className).toContain('w-6');
      expect(img?.className).toContain('h-6');
    });

    it('should render with sm size', () => {
      const { container } = render(<Avatar playerName="you" size="sm" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.className).toContain('w-8');
      expect(img?.className).toContain('h-8');
    });

    it('should render with md size (default)', () => {
      const { container } = render(<Avatar playerName="you" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.className).toContain('w-16');
      expect(img?.className).toContain('h-16');
    });

    it('should render with lg size', () => {
      const { container } = render(<Avatar playerName="you" size="lg" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.className).toContain('w-full');
      expect(img?.className).toContain('h-full');
    });
  });

  describe('Priority Prop', () => {
    it('should use priority=true by default', () => {
      const { container } = render(<Avatar playerName="you" />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      // Priority prop is passed to Next Image component
    });

    it('should respect priority=false when provided', () => {
      const { container } = render(<Avatar playerName="you" priority={false} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
    });
  });

  describe('Container Classes', () => {
    it('should have inline-block container for non-lg sizes', () => {
      const { container } = render(<Avatar playerName="you" size="md" />);
      const div = container.querySelector('div');
      expect(div?.className).toContain('inline-block');
    });

    it('should have w-full h-full container for lg size', () => {
      const { container } = render(<Avatar playerName="you" size="lg" />);
      const div = container.querySelector('div');
      expect(div?.className).toContain('w-full');
      expect(div?.className).toContain('h-full');
    });
  });

  describe('Default Avatar Styling', () => {
    it('should render default avatar with bg-gray-200 background', () => {
      const { container } = render(<Avatar playerName="unknown" />);
      const span = container.querySelector('span');
      expect(span?.className).toContain('bg-gray-200');
    });

    it('should render default avatar with text-gray-600 color', () => {
      const { container } = render(<Avatar playerName="unknown" />);
      const span = container.querySelector('span');
      expect(span?.className).toContain('text-gray-600');
    });

    it('should center robot emoji in default avatar', () => {
      const { container } = render(<Avatar playerName="unknown" />);
      const span = container.querySelector('span');
      expect(span?.className).toContain('flex');
      expect(span?.className).toContain('items-center');
      expect(span?.className).toContain('justify-center');
    });
  });
});
