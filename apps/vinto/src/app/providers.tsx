// app/providers-with-gameclient.tsx
'use client';

import { ThemeProvider } from 'next-themes';
import { DIProvider } from './components/di-provider';
import { GameClientProvider } from '@vinto/local-client';

/**
 * Enhanced Providers component that includes both:
 * - Old DI system (for gradual migration)
 * - New GameClient system (for new architecture)
 *
 * This allows both systems to coexist during the migration period.
 */
export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
      <DIProvider>
        <GameClientProvider>{children}</GameClientProvider>
      </DIProvider>
    </ThemeProvider>
  );
}
