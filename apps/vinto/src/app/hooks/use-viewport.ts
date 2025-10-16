'use client';

import { useState, useEffect } from 'react';

interface ViewportSize {
  width: number;
  height: number;
  visualHeight: number; // Available height considering mobile browser UI
  isRotated: boolean; // Whether the viewport is being rotated by CSS
}

/**
 * Detects if device is a touch device in landscape mode
 * (where we apply CSS rotation to maintain portrait layout)
 */
function isMobileLandscape(): boolean {
  if (typeof window === 'undefined') return false;

  // Check if device has coarse pointer (touch screen)
  const hasCoarsePointer = window.matchMedia('(any-pointer: coarse)').matches;

  // Check if device is in landscape orientation
  const isLandscape = window.matchMedia('(orientation: landscape)').matches;

  return hasCoarsePointer && isLandscape;
}

export function useViewport() {
  const [viewport, setViewport] = useState<ViewportSize>({
    width: 0,
    height: 0,
    visualHeight: 0,
    isRotated: false,
  });

  useEffect(() => {
    function updateViewport() {
      const isRotated = isMobileLandscape();

      let width = window.innerWidth;
      let height = window.innerHeight;
      let visualHeight = window.visualViewport
        ? window.visualViewport.height
        : height;

      // When we rotate the content via CSS, swap width and height
      // so that the reported dimensions match the visual portrait layout
      if (isRotated) {
        [width, height] = [height, width];
        visualHeight = width; // In rotated view, visual height is actually the physical width
      }

      setViewport({
        width,
        height,
        visualHeight,
        isRotated,
      });
    }

    // Initial measurement
    updateViewport();

    // Listen to both resize and visualViewport changes
    window.addEventListener('resize', updateViewport);

    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', updateViewport);
      window.visualViewport.addEventListener('scroll', updateViewport);
    }

    // Also listen to orientation changes on mobile
    window.addEventListener('orientationchange', () => {
      // Delay to allow browser to complete orientation change
      setTimeout(updateViewport, 100);
    });

    return () => {
      window.removeEventListener('resize', updateViewport);

      if (window.visualViewport) {
        window.visualViewport.removeEventListener('resize', updateViewport);
        window.visualViewport.removeEventListener('scroll', updateViewport);
      }

      window.removeEventListener('orientationchange', updateViewport);
    };
  }, []);

  return viewport;
}