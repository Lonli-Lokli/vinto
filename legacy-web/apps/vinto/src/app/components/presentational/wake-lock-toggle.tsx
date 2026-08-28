'use client';

import React, { useEffect, useState, useCallback, useRef } from 'react';
import { Monitor, MonitorOff } from 'lucide-react';
import toast from 'react-hot-toast';

/**
 * Wake Lock Toggle Component
 * Uses Screen Wake Lock API to keep the screen on during gameplay
 * Hidden if the API is not supported by the browser
 */
export function WakeLockToggle() {
  const [isSupported, setIsSupported] = useState(false);
  const [isEnabled, setIsEnabled] = useState(false); // User's intent: do they want the wake lock?
  const [isLocked, setIsLocked] = useState(false); // Actual state: do we currently have a lock?
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const isRequestingRef = useRef(false); // Guard against concurrent requests

  const requestWakeLock = useCallback(async () => {
    // Guard: prevent concurrent requests or duplicate locks
    if (isRequestingRef.current || wakeLockRef.current) {
      return;
    }

    isRequestingRef.current = true;

    try {
      const lock = await navigator.wakeLock.request('screen');
      wakeLockRef.current = lock;
      setIsLocked(true);
      setIsEnabled(true); // User wants the lock

      // Listen for wake lock release (e.g., when tab becomes inactive)
      // Use { once: true } to prevent accumulating listeners on multiple toggles
      lock.addEventListener(
        'release',
        () => {
          setIsLocked(false);
          wakeLockRef.current = null;
          // Note: We don't set isEnabled to false here, because the lock may have been
          // released automatically by the browser (not by the user). We'll re-acquire
          // when the page becomes visible again if isEnabled is still true.
        },
        { once: true }
      );

      console.log('[WakeLock] Screen wake lock activated');
    } catch (err) {
      console.warn('[WakeLock] Failed to activate:', err);
      setIsLocked(false);
      toast.error('Could not activate screen lock');
    } finally {
      isRequestingRef.current = false;
    }
  }, []);

  const releaseWakeLock = useCallback(async () => {
    if (wakeLockRef.current) {
      try {
        await wakeLockRef.current.release();
        wakeLockRef.current = null;
        setIsLocked(false);
        setIsEnabled(false); // User no longer wants the lock
        console.log('[WakeLock] Screen wake lock released');
      } catch (err) {
        console.warn('[WakeLock] Failed to release:', err);
      }
    } else {
      // No active lock, but user clicked to disable - update intent
      setIsEnabled(false);
    }
  }, []);

  useEffect(() => {
    // Check if Wake Lock API is supported
    if ('wakeLock' in navigator) {
      setIsSupported(true);
      // Note: We don't request wake lock on mount because the API requires
      // user activation (a user gesture). The lock will be acquired when
      // the user clicks the toggle button.
    }

    // Cleanup on unmount
    return () => {
      if (wakeLockRef.current) {
        void wakeLockRef.current.release().catch(() => {
          // Ignore errors on cleanup
        });
      }
    };
  }, []);

  // Re-acquire wake lock when page becomes visible again (important for mobile)
  useEffect(() => {
    if (!isEnabled) return; // Only listen when user wants the lock

    const handleVisibilityChange = () => {
      // Only re-acquire if page is visible, user wants lock, and we don't have one
      if (document.visibilityState === 'visible' && !wakeLockRef.current) {
        void requestWakeLock();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isEnabled, requestWakeLock]);

  const handleToggle = () => {
    if (isLocked || wakeLockRef.current) {
      void releaseWakeLock();
    } else {
      void requestWakeLock();
    }
  };

  // Hide if not supported
  if (!isSupported) {
    return null;
  }

  return (
    <button
      onClick={handleToggle}
      className="px-2 py-1 rounded bg-surface-secondary hover:bg-surface-tertiary text-secondary hover:text-primary transition-colors flex items-center gap-1"
      title={isLocked ? 'Screen wake lock active' : 'Screen wake lock inactive'}
      aria-label={
        isLocked ? 'Disable screen wake lock' : 'Enable screen wake lock'
      }
    >
      {isLocked ? (
        <Monitor className="w-5 h-5 text-success" />
      ) : (
        <MonitorOff className="w-5 h-5 text-secondary" />
      )}
    </button>
  );
}
