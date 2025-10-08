// app/layout.tsx

import './reflect-metadata-client-side';
import type { Metadata, Viewport } from 'next';
import { Inter } from 'next/font/google';
import * as Sentry from '@sentry/nextjs';

import './global.css'; // This is where you import your global Tailwind CSS styles
import { Providers } from './providers';

// Optimize fonts using next/font
const inter = Inter({ subsets: ['latin'] });

// Define viewport configuration
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: 'cover',
};

// Define metadata for SEO and the browser tab
export const metadata: Metadata = {
  title: 'Vinto - The Card Game',
  description: 'Play the Vinto card game against advanced AI opponents.',
  icons: {
    icon: '/favicon.png',
    shortcut: '/favicon.png',
    apple: '/favicon.png',
  },
  other: {
    ...Sentry.getTraceData(),
  },
};

/**
 * This is the Root Layout for the entire application.
 * It's a Server Component by default.
 */
export default function RootLayout({
  children,
}: {
  children: React.ReactNode; // This prop is mandatory
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${inter.className} bg-surface-primary text-on-surface`}>
        <Providers>
          {/* 
            You could place a site-wide header or navigation bar here.
            For example:
            <nav>
              <a href="/">Home</a>
              <a href="/rules">Rules</a>
            </nav>
          */}

          {/* The `children` prop will be the content of our `app/page.tsx` file. */}
          {children}
        </Providers>
      </body>
    </html>
  );
}
