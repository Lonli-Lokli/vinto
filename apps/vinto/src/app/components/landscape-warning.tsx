'use client';

import React, { useEffect, useState } from 'react';
import { RotateCw } from 'lucide-react';

/**
 * Landscape Warning Overlay
 * Shows a full-screen message on mobile devices when in landscape orientation
 * Asks users to rotate their device to portrait mode
 */
export function LandscapeWarning() {
  const [isLandscape, setIsLandscape] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkOrientation = () => {
      // Check if device is mobile (touch device with coarse pointer)
      const isTouchDevice = window.matchMedia('(any-pointer: coarse)').matches;

      // Check if in landscape orientation
      const isLandscapeOrientation = window.matchMedia('(orientation: landscape)').matches;

      setIsMobile(isTouchDevice);
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

  // Only show on mobile devices in landscape orientation
  if (!isMobile || !isLandscape) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[9999] bg-surface-primary flex items-center justify-center p-8">
      <div className="text-center space-y-6 max-w-md">
        {/* Animated rotate icon */}
        <div className="flex justify-center">
          <div className="animate-bounce">
            <RotateCw
              className="w-24 h-24 text-primary animate-spin"
              style={{ animationDuration: '3s' }}
            />
          </div>
        </div>

        {/* Title */}
        <h1 className="text-3xl font-bold text-primary">
          Please Rotate Your Device
        </h1>

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
            <div className="w-16 h-24 border-4 border-error rounded-lg mb-2 opacity-50"></div>
            <p className="text-xs text-error font-semibold">Landscape</p>
          </div>
          <RotateCw className="w-8 h-8 text-secondary" />
          <div className="text-center">
            <div className="w-16 h-24 border-4 border-success rounded-lg mb-2"></div>
            <p className="text-xs text-success font-semibold">Portrait</p>
          </div>
        </div>
      </div>
    </div>
  );
}
