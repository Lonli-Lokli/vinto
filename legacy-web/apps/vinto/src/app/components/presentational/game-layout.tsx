// components/game-layout.tsx
'use client';

import React from 'react';
import { ErrorBoundary } from './error-boundary';
import { useViewport } from '../../hooks/use-viewport';
import { DIProvider } from '../di-provider';

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
    <ErrorBoundary>
      <DIProvider>
        <div
          className="bg-page-gradient overflow-y-auto flex flex-col"
          style={{
            ...style,
            height:
              viewport.visualHeight > 0
                ? `${viewport.visualHeight}px`
                : '100dvh',
          }}
        >
          {children}
        </div>
      </DIProvider>
    </ErrorBoundary>
  );
}
