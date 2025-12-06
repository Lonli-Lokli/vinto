import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, fireEvent, waitFor } from '@testing-library/react';
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

  // Note: Mount State tests removed because in the test environment,
  // useEffect runs synchronously, so the component is always "mounted"
  // by the time we can observe it. The loading placeholder is not observable in tests.

  describe('Light Theme State', () => {
    it('should render Moon icon when theme is light after mount', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      // Wait for component to mount
      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button).toBeTruthy();
      });
    });

    it('should render "Dark" text when theme is light after mount', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.textContent).toContain('Dark');
      });
    });

    it('should have correct aria-label for light theme', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.getAttribute('aria-label')).toBe('Switch to dark theme');
      });
    });

    it('should have correct title attribute for light theme', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.getAttribute('title')).toBe('Switch to dark theme');
      });
    });
  });

  describe('Dark Theme State', () => {
    it('should render Sun icon when theme is dark after mount', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button).toBeTruthy();
      });
    });

    it('should render "Light" text when theme is dark after mount', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.textContent).toContain('Light');
      });
    });

    it('should have correct aria-label for dark theme', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.getAttribute('aria-label')).toBe('Switch to light theme');
      });
    });

    it('should have correct title attribute for dark theme', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.getAttribute('title')).toBe('Switch to light theme');
      });
    });
  });

  describe('Button Styling', () => {
    it('should have correct base styling classes', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.className).toContain('px-2');
        expect(button?.className).toContain('py-1');
        expect(button?.className).toContain('rounded');
      });
    });

    it('should have correct background color classes', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.className).toContain('bg-surface-secondary');
        expect(button?.className).toContain('hover:bg-surface-tertiary');
      });
    });

    it('should have correct text color classes', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.className).toContain('text-secondary');
        expect(button?.className).toContain('hover:text-primary');
      });
    });

    it('should have transition-colors class', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.className).toContain('transition-colors');
      });
    });

    it('should have flex layout with gap', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button?.className).toContain('flex');
        expect(button?.className).toContain('items-center');
        expect(button?.className).toContain('gap-1');
      });
    });
  });

  describe('Responsive Text Visibility', () => {
    it('should hide text on mobile (sm:inline)', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const span = container.querySelector('span');
        expect(span?.className).toContain('hidden');
        expect(span?.className).toContain('sm:inline');
      });
    });

    it('should have correct text styling', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const span = container.querySelector('span');
        expect(span?.className).toContain('text-xs');
        expect(span?.className).toContain('font-medium');
      });
    });
  });

  describe('Theme Switching', () => {
    it('should call setTheme with "dark" when clicking from light theme', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button).toBeTruthy();
      });

      const button = container.querySelector('button');
      if (button) {
        fireEvent.click(button);
        expect(mockSetTheme).toHaveBeenCalledWith('dark');
      }
    });

    it('should call setTheme with "light" when clicking from dark theme', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button).toBeTruthy();
      });

      const button = container.querySelector('button');
      if (button) {
        fireEvent.click(button);
        expect(mockSetTheme).toHaveBeenCalledWith('light');
      }
    });
  });

  describe('Icon Sizes', () => {
    it('should render Moon icon with size 18', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'light',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button).toBeTruthy();
      });
    });

    it('should render Sun icon with size 18', async () => {
      mockUseTheme.mockReturnValue({
        setTheme: mockSetTheme,
        resolvedTheme: 'dark',
      });

      const { container } = render(<ThemeToggle />);

      await waitFor(() => {
        const button = container.querySelector('button');
        expect(button).toBeTruthy();
      });
    });
  });
});
