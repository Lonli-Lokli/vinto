// app/providers-with-gameclient.tsx
'use client';

import { ThemeProvider } from 'next-themes';
import { GameClientProvider } from '../client/GameClientContext';
import { DIProvider } from './components/di-provider';

/**
 * Enhanced Providers component that includes both:
 * - Old DI system (for gradual migration)
 * - New GameClient system (for new architecture)
 *
 * This allows both systems to coexist during the migration period.
 */
export function ProvidersWithGameClient({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
      <DIProvider>
        <GameClientProvider>
          {children}
        </GameClientProvider>
      </DIProvider>
    </ThemeProvider>
  );
}
