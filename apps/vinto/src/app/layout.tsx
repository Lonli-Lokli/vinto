// app/layout.tsx

import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './global.css'; // This is where you import your global Tailwind CSS styles

// Optimize fonts using next/font
const inter = Inter({ subsets: ['latin'] });

// Define metadata for SEO and the browser tab
export const metadata: Metadata = {
  title: 'Vinto - The Card Game',
  description: 'Play the Vinto card game against advanced AI opponents.',
  icons: {
    icon: '/favicon.png',
    shortcut: '/favicon.png',
    apple: '/favicon.png',
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
    <html lang="en">
      <body className={`${inter.className} bg-gray-900 text-white`}>
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
      </body>
    </html>
  );
}
