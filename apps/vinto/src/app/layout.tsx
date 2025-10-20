// app/layout.tsx

import './reflect-metadata-client-side';
import type { Metadata, Viewport } from 'next';
import { Inter } from 'next/font/google';
import * as Sentry from '@sentry/nextjs';
import { Analytics } from '@vercel/analytics/next';

import './global.css'; // This is where you import your global Tailwind CSS styles
import { Providers } from './providers';

// Optimize fonts using next/font
const inter = Inter({ subsets: ['latin'] });

const toMetadataBase = (value?: string) => {
  if (!value) {
    return undefined;
  }

  const normalized =
    value.startsWith('http://') || value.startsWith('https://')
      ? value
      : `https://${value}`;

  try {
    return new URL(normalized);
  } catch {
    return undefined;
  }
};

const metadataBase =
  toMetadataBase(process.env.NEXT_PUBLIC_APP_URL ?? process.env.VERCEL_URL) ??
  new URL('http://localhost:3000');

// Define viewport configuration
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: 'cover',
};

// Define metadata for SEO and the browser tab
export const metadata = {
  metadataBase,
  title: {
    default: 'Vinto - Strategic Card Game',
    template: '%s | Vinto',
  },
  description:
    'Vinto is a strategic 4-5 player toss-in card game where you minimize your hand value using clever draws, swaps, and action cards from a 54-card deck.',
  abstract:
    'Master action cards, declare Vinto, and outscore the coalition in this fast-paced, multi-round card duel.',
  keywords: [
    'Vinto card game',
    'toss-in mechanics',
    'action card strategy',
    'multiplayer card game',
    'peek and swap gameplay',
  ],
  category: 'game',
  applicationName: 'Vinto',
  alternates: {
    canonical: '/',
  },
  openGraph: {
    title: 'Vinto - Strategic Card Game',
    description:
      'Learn and play Vinto, a tactical toss-in card game for 4-5 players featuring action cards, penalty draws, and dramatic final rounds.',
    type: 'website',
    images: [
      {
        url: '/favicon.png',
        width: 512,
        height: 512,
        alt: 'Vinto card back illustration',
      },
    ],
  },
  twitter: {
    card: 'summary',
    title: 'Vinto - Strategic Card Game',
    description:
      'Race to the lowest hand total by mastering action cards, toss-ins, and the final Vinto call.',
  },
  icons: {
    icon: '/favicon.png',
    shortcut: '/favicon.png',
    apple: '/favicon.png',
  },
  manifest: '/manifest.json',
  other: {
    ...Sentry.getTraceData(),
  },
} satisfies Metadata;

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
        <Providers>{children}</Providers>
        <Analytics />
      </body>
    </html>
  );
}
