// app/providers-with-gameclient.tsx
'use client';
import { useEffect } from 'react';
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
  useEffect(() => {
    // This only runs on the client after hydration
    // Safe for SSR - won't cause hydration mismatches

    // Check if dialog is natively supported
    if (
      typeof window !== 'undefined' &&
      typeof HTMLDialogElement !== 'function'
    ) {
      // Dynamically import polyfill only when needed
      import('dialog-polyfill')
        .then(({ default: dialogPolyfill }) => {
          document.querySelectorAll('dialog').forEach((dialog) => {
            dialogPolyfill.registerDialog(dialog);
          });
        })
        .catch((error) => {
          console.warn('Failed to load dialog polyfill:', error);
        });
    }
  }, []);
  return (
    <ThemeProvider attribute="class" defaultTheme="dark" enableSystem>
      <DIProvider>
        <GameClientProvider>{children}</GameClientProvider>
      </DIProvider>
    </ThemeProvider>
  );
}
