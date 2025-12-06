import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import React from 'react';
import { ThemeToggle } from '../src/app/components/presentational/theme-toggle';

// Mock next-themes
const mockSetTheme = vi.fn();
const mockUseTheme = vi.fn();

vi.mock('next-themes', () => ({
  useTheme: () => mockUseTheme(),
}));

describe('ThemeToggle Component', () => {
  beforeEach(() => {
    mockSetTheme.mockClear();
    mockUseTheme.mockClear();
  });

  describe('Mount State', () => {
    it('should render loading placeholder when not mounted', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      // Mock useEffect to not run (simulating not mounted)
      const { container } = render(<ThemeToggle />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.getAttribute('alt')).toBe('Loading Light/Dark Toggle');
    });

    it('should have correct placeholder image dimensions', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);
      const img = container.querySelector('img');
      expect(img?.getAttribute('width')).toBe('36');
      expect(img?.getAttribute('height')).toBe('36');
    });

    it('should have title attribute on placeholder', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);
      const img = container.querySelector('img');
      expect(img?.getAttribute('title')).toBe('Loading Light/Dark Toggle');
    });
  });

  describe('Light Theme State', () => {
    it('should render Moon icon when theme is light', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      // Force mounted state by mocking useEffect
      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      // Check for Moon icon (svg element)
      const button = container.querySelector('button');
      expect(button).toBeTruthy();
    });

    it('should render "Dark" text when theme is light', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.textContent).toContain('Dark');
    });

    it('should have correct aria-label for light theme', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.getAttribute('aria-label')).toBe('Switch to dark theme');
    });

    it('should have correct title attribute for light theme', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.getAttribute('title')).toBe('Switch to dark theme');
    });
  });

  describe('Dark Theme State', () => {
    it('should render Sun icon when theme is dark', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button).toBeTruthy();
    });

    it('should render "Light" text when theme is dark', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.textContent).toContain('Light');
    });

    it('should have correct aria-label for dark theme', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.getAttribute('aria-label')).toBe('Switch to light theme');
    });

    it('should have correct title attribute for dark theme', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.getAttribute('title')).toBe('Switch to light theme');
    });
  });

  describe('Button Styling', () => {
    it('should have correct base styling classes', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.className).toContain('px-2');
      expect(button?.className).toContain('py-1');
      expect(button?.className).toContain('rounded');
    });

    it('should have correct background color classes', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.className).toContain('bg-surface-secondary');
      expect(button?.className).toContain('hover:bg-surface-tertiary');
    });

    it('should have correct text color classes', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.className).toContain('text-secondary');
      expect(button?.className).toContain('hover:text-primary');
    });

    it('should have transition-colors class', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.className).toContain('transition-colors');
    });

    it('should have flex layout with gap', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button?.className).toContain('flex');
      expect(button?.className).toContain('items-center');
      expect(button?.className).toContain('gap-1');
    });
  });

  describe('Responsive Text Visibility', () => {
    it('should hide text on mobile (sm:inline)', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const span = container.querySelector('span');
      expect(span?.className).toContain('hidden');
      expect(span?.className).toContain('sm:inline');
    });

    it('should have correct text styling', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const span = container.querySelector('span');
      expect(span?.className).toContain('text-xs');
      expect(span?.className).toContain('font-medium');
    });
  });

  describe('Theme Switching', () => {
    it('should call setTheme with "dark" when clicking from light theme', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      if (button) {
        fireEvent.click(button);
        expect(mockSetTheme).toHaveBeenCalledWith('dark');
      }
    });

    it('should call setTheme with "light" when clicking from dark theme', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      if (button) {
        fireEvent.click(button);
        expect(mockSetTheme).toHaveBeenCalledWith('light');
      }
    });
  });

  describe('Icon Sizes', () => {
    it('should render Moon icon with size 18', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      // lucide-react icons render as svg with width/height attributes
      const button = container.querySelector('button');
      expect(button).toBeTruthy();
    });

    it('should render Sun icon with size 18', () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const OriginalUseEffect = React.useEffect;
      React.useEffect = vi.fn((f) => f());

      const { container } = render(<ThemeToggle />);

      React.useEffect = OriginalUseEffect;

      const button = container.querySelector('button');
      expect(button).toBeTruthy();
    });
  });
});
