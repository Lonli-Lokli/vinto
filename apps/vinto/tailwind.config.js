// const { createGlobPatternsForDependencies } = require('@nx/next/tailwind');

// The above utility import will not work if you are using Next.js' --turbo.
// Instead you will have to manually add the dependent paths to be included.
// For example
// ../libs/buttons/**/*.{ts,tsx,js,jsx,html}',                 <--- Adding a shared lib
// !../libs/buttons/**/*.{stories,spec}.{ts,tsx,js,jsx,html}', <--- Skip adding spec/stories files from shared lib

// If you are **not** using `--turbo` you can uncomment both lines 1 & 19.
// A discussion of the issue can be found: https://github.com/nrwl/nx/issues/26510

/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: [
    './{src,pages,components,app}/**/*.{ts,tsx,js,jsx,html}',
    '!./{src,pages,components,app}/**/*.{stories,spec}.{ts,tsx,js,jsx,html}',
    //     ...createGlobPatternsForDependencies(__dirname)
  ],
  theme: {
    extend: {
      fontSize: {
        '2xs': '0.625rem', // 10px
      },
      // Remove colors entirely - use CSS variable utility classes instead
      // All colors are now defined in theme.css as CSS variables with utility classes
      height: {
        18: '4.5rem',
      },
      animation: {
        // Color-based animations moved to theme.css to use CSS variables
        'flip-card': 'flip-card 0.6s ease-in-out',
        float: 'float 3s ease-in-out infinite',
        'card-click': 'card-click-feedback 0.1s ease',
        'card-select-pulse': 'card-select-pulse 1.5s ease-in-out infinite',
        'gentle-pulse': 'gentle-pulse 2s infinite',
        'ring-pulse': 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'pending-card-border': 'pending-card-border 2s ease-in-out infinite',
        'swap-select-border': 'swap-select-border 1.5s ease-in-out infinite',
      },
      keyframes: {
        // Keep animations simple - colors will be handled by CSS classes with variables
        'flip-card': {
          '0%': { transform: 'rotateY(0deg)' },
          '50%': { transform: 'rotateY(90deg)' },
          '100%': { transform: 'rotateY(0deg)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-5px)' },
        },
        'card-click-feedback': {
          '0%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(0.95)' },
          '100%': { transform: 'scale(1)' },
        },
        'card-select-pulse': {
          '0%, 100%': {
            opacity: '0.6',
            boxShadow:
              '0 0 0 3px rgba(var(--color-accent), 0.6), 0 0 15px 0 rgba(var(--color-accent), 0.4)',
          },
          '50%': {
            opacity: '1',
            boxShadow:
              '0 0 0 3px rgba(var(--color-accent), 1), 0 0 20px 0 rgba(var(--color-accent), 0.7)',
          },
        },
        'gentle-pulse': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.7' },
        },
        'ring-pulse': {
          '0%, 100%': {
            boxShadow: '0 0 0 4px rgba(var(--color-current-player-glow), 0.5)',
          },
          '50%': {
            boxShadow: '0 0 0 8px rgba(var(--color-current-player-glow), 0)',
          },
        },
        'pending-card-border': {
          '0%, 100%': {
            boxShadow:
              '0 0 0 3px rgba(var(--color-warning), 0.7), 0 0 12px 0 rgba(var(--color-warning), 0.5)',
          },
          '50%': {
            boxShadow:
              '0 0 0 3px rgba(var(--color-warning), 1), 0 0 20px 0 rgba(var(--color-warning), 0.8)',
          },
        },
        'swap-select-border': {
          '0%, 100%': {
            boxShadow:
              '0 0 0 3px rgba(var(--color-info), 0.6), 0 0 12px 0 rgba(var(--color-info), 0.4)',
          },
          '50%': {
            boxShadow:
              '0 0 0 3px rgba(var(--color-info), 1), 0 0 18px 0 rgba(var(--color-info), 0.7)',
          },
        },
      },
      backdropBlur: {
        xs: '2px',
      },
    },
  },
  plugins: [],
};
