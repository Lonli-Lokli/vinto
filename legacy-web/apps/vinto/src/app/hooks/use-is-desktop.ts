// hooks/use-is-desktop.ts
'use client';

import { useMediaQuery } from './use-media-query';

/**
 * Hook to detect if the current device is a desktop (non-touch device)
 * Uses the same logic as LandscapeWarning component
 * @returns boolean indicating if the device is desktop
 */
export function useIsDesktop(): boolean {
  // Check if NOT a touch device (desktop)
  const isTouchDevice = useMediaQuery('(any-pointer: coarse)');
  return !isTouchDevice;
}