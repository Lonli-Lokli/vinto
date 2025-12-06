import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WakeLockToggle } from '../src/app/components/presentational/wake-lock-toggle';

// Mock toast
vi.mock('react-hot-toast', () => ({
  default: {
    error: vi.fn(),
  },
}));

import toast from 'react-hot-toast';

describe('WakeLockToggle', () => {
  let mockWakeLock: {
    request: ReturnType<typeof vi.fn>;
  };
  let mockSentinel: {
    release: ReturnType<typeof vi.fn>;
    addEventListener: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    // Reset mocks
    vi.clearAllMocks();

    // Create mock wake lock sentinel
    mockSentinel = {
      release: vi.fn().mockResolvedValue(undefined),
      addEventListener: vi.fn(),
    };

    // Create mock wake lock API
    mockWakeLock = {
      request: vi.fn().mockResolvedValue(mockSentinel),
    };

    // Add to navigator
    Object.defineProperty(navigator, 'wakeLock', {
      value: mockWakeLock,
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    // Clean up
    delete (navigator as any).wakeLock;
  });

  it('should have non-prod environment', () => {
    expect(process.env.NODE_ENV).toBe('test');
  });

  it('should render when Wake Lock API is supported', () => {
    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });
    expect(button).toBeTruthy();
  });

  it('should not render when Wake Lock API is not supported', () => {
    delete (navigator as any).wakeLock;
    const { container } = render(<WakeLockToggle />);
    expect(container.firstChild).toBeNull();
  });

  it('should request wake lock when toggle is clicked', async () => {
    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    fireEvent.click(button);

    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalledWith('screen');
    });
  });

  it('should add event listener with { once: true } option', async () => {
    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    fireEvent.click(button);

    await waitFor(() => {
      expect(mockSentinel.addEventListener).toHaveBeenCalledWith(
        'release',
        expect.any(Function),
        { once: true }
      );
    });
  });

  it('should release wake lock when toggle is clicked again', async () => {
    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    // Enable
    fireEvent.click(button);
    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalled();
    });

    // Wait for UI to update to "disable" state
    await waitFor(() => {
      screen.getByRole('button', { name: /disable screen wake lock/i });
    });

    // Disable
    const disableButton = screen.getByRole('button', {
      name: /disable screen wake lock/i,
    });
    fireEvent.click(disableButton);

    await waitFor(() => {
      expect(mockSentinel.release).toHaveBeenCalled();
    });
  });

  it('should show toast error when wake lock request fails', async () => {
    mockWakeLock.request.mockRejectedValueOnce(new Error('Permission denied'));

    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    fireEvent.click(button);

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        'Could not activate screen lock'
      );
    });
  });

  it('should re-acquire wake lock when page becomes visible', async () => {
    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    // Enable wake lock
    fireEvent.click(button);
    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalledTimes(1);
    });

    // Simulate the lock being released (e.g., when page becomes hidden)
    const releaseCallback = mockSentinel.addEventListener.mock.calls[0][1];
    releaseCallback();

    // Wait for React to fully process the release state update
    // This ensures isLocked is false and the UI has updated before we fire visibility change
    await waitFor(() => {
      screen.getByRole('button', { name: /enable screen wake lock/i });
    });

    // Simulate page becoming visible again
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
      configurable: true,
    });
    fireEvent(document, new Event('visibilitychange'));

    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalledTimes(2);
    });
  });

  it('should not re-acquire wake lock when page becomes visible if user disabled it', async () => {
    render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    // Enable wake lock
    fireEvent.click(button);
    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalledTimes(1);
    });

    // Wait for UI to update to "disable" state
    await waitFor(() => {
      screen.getByRole('button', { name: /disable screen wake lock/i });
    });

    // User disables it
    const disableButton = screen.getByRole('button', {
      name: /disable screen wake lock/i,
    });
    fireEvent.click(disableButton);
    await waitFor(() => {
      expect(mockSentinel.release).toHaveBeenCalled();
    });

    // Wait for React to finish processing state updates and effect cleanup
    await waitFor(() => {
      screen.getByRole('button', { name: /enable screen wake lock/i });
    });

    // Simulate page becoming visible again
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
      configurable: true,
    });
    fireEvent(document, new Event('visibilitychange'));

    // Should not re-acquire since user disabled it
    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalledTimes(1); // Still just the initial call
    });
  });

  it('should clean up wake lock on unmount', async () => {
    const { unmount } = render(<WakeLockToggle />);
    const button = screen.getByRole('button', {
      name: /enable screen wake lock/i,
    });

    // Enable wake lock
    fireEvent.click(button);
    await waitFor(() => {
      expect(mockWakeLock.request).toHaveBeenCalled();
    });

    // Unmount
    unmount();

    await waitFor(() => {
      expect(mockSentinel.release).toHaveBeenCalled();
    });
  });
});
