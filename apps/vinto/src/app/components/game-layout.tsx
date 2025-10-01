// components/game-layout.tsx
'use client';

import React from 'react';
import { useViewport } from '../hooks/use-viewport';
import { DIProvider } from './di-provider';
import { ReplayControls } from './replay-controls';
import { AnimatedCardOverlay } from './animated-card';

interface GameLayoutProps {
  children: React.ReactNode;
}

/**
 * Client component handling viewport-specific logic and DI setup
 */
export function GameLayout({ children }: GameLayoutProps) {
  const viewport = useViewport();

  // CSS custom properties for dynamic height calculations
  const style = {
    '--viewport-height': `${viewport.visualHeight}px`,
    '--safe-area-inset-bottom': 'env(safe-area-inset-bottom)',
  } as React.CSSProperties;

  return (
    <DIProvider>
      <div
        className="bg-gradient-to-br from-emerald-50 to-blue-50 overflow-y-auto flex flex-col"
        style={{
          ...style,
          height:
            viewport.visualHeight > 0 ? `${viewport.visualHeight}px` : '100dvh',
        }}
      >
        {children}
        <ReplayControls />
        <AnimatedCardOverlay />
      </div>
    </DIProvider>
  );
}
