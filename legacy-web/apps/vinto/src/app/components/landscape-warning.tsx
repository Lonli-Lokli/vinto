'use client';

import React, { useEffect, useState } from 'react';
import { RotateCw } from 'lucide-react';
import { useIsDesktop } from '../hooks/use-is-desktop';

/**
 * Landscape Warning Overlay
 * Shows a full-screen message on mobile devices when in landscape orientation
 * Asks users to rotate their device to portrait mode
 */
export function LandscapeWarning() {
  const isDesktop = useIsDesktop();
  const [isLandscape, setIsLandscape] = useState(false);

  useEffect(() => {
    const checkOrientation = () => {
      // Check if in landscape orientation
      const isLandscapeOrientation = window.matchMedia(
        '(orientation: landscape)'
      ).matches;

      setIsLandscape(isLandscapeOrientation);
    };

    // Initial check
    checkOrientation();

    // Listen for orientation changes
    window.addEventListener('resize', checkOrientation);
    window.addEventListener('orientationchange', checkOrientation);

    return () => {
      window.removeEventListener('resize', checkOrientation);
      window.removeEventListener('orientationchange', checkOrientation);
    };
  }, []);

  // Only show on mobile devices (non-desktop) in landscape orientation
  if (isDesktop || !isLandscape) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[9999] bg-surface-primary flex items-center justify-center p-8">
      <div className="space-y-6 max-w-2xl mx-auto">
        {/* Title */}
        <h1 className="text-3xl font-bold text-primary text-center">
          Please Rotate Your Device
        </h1>

        <div className="flex flex-row items-start justify-center gap-8">
          {/* Animated rotate icon on the left */}
          <div className="flex flex-col items-center justify-center min-w-[6rem]">
            <div className="animate-bounce" style={{ overflow: 'visible' }}>
              <RotateCw
                className="w-24 h-24 text-primary animate-spin"
                style={{ animationDuration: '3s', display: 'block' }}
              />
            </div>
          </div>
          {/* Main content on the right */}
          <div className="flex-1 space-y-6">
            {/* Description */}
            <p className="text-lg text-secondary leading-relaxed">
              This game is designed for portrait orientation.
              <br />
              <span className="font-semibold text-primary">
                Rotate your device to portrait mode
              </span>
              <br />
              for the best gaming experience.
            </p>

            {/* Visual indicator */}
            <div className="flex items-center justify-center gap-8 pt-4">
              <div className="text-center">
                <div className="w-24 h-16 border-4 border-error rounded-lg mb-2 opacity-50 flex items-center justify-center">
                  {/* Landscape box is wider than tall */}
                </div>
                <p className="text-xs text-error font-semibold">Landscape</p>
              </div>
              <RotateCw
                className="w-8 h-8 text-secondary -scale-x-100"
                style={{ transform: 'scaleX(-1) rotate(-90deg)' }}
              />
              <div className="text-center">
                <div className="w-16 h-24 border-4 border-success rounded-lg mb-2 flex items-center justify-center">
                  {/* Portrait box is taller than wide */}
                </div>
                <p className="text-xs text-success font-semibold">Portrait</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
