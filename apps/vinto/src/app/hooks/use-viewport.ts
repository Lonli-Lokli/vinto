'use client';

import { useState, useEffect } from 'react';

interface ViewportSize {
  width: number;
  height: number;
  visualHeight: number; // Available height considering mobile browser UI
}

export function useViewport() {
  const [viewport, setViewport] = useState<ViewportSize>({
    width: 0,
    height: 0,
    visualHeight: 0,
  });

  useEffect(() => {
    function updateViewport() {
      const width = window.innerWidth;
      const height = window.innerHeight;

      // For mobile devices, use visualViewport if available to account for virtual keyboard and address bar
      const visualHeight = window.visualViewport
        ? window.visualViewport.height
        : height;

      setViewport({
        width,
        height,
        visualHeight,
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