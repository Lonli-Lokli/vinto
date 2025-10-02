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
      colors: {
        // Warm poker green - friendly and inviting
        'poker-green': {
          50: '#f0fdf4',
          100: '#dcfce7',
          200: '#bbf7d0',
          300: '#86efac',
          400: '#4ade80',
          500: '#22c55e',  // Main table color
          600: '#16a34a',
          700: '#15803d',  // Darker variant
          800: '#166534',
          900: '#14532d',
        },
        // Warm felted green (traditional poker table)
        'felt': {
          light: '#2d5a3d',
          DEFAULT: '#1e4129',
          dark: '#15301f',
        },
        // Warm wood tones for borders/accents
        'wood': {
          light: '#8b6f47',
          DEFAULT: '#6b5436',
          dark: '#4a3822',
        },
        emerald: {
          50: '#ecfdf5',
          100: '#d1fae5',
          200: '#a7f3d0',
          300: '#6ee7b7',
          400: '#34d399',
          500: '#10b981',
          600: '#059669',
          700: '#047857',
          800: '#065f46',
          900: '#064e3b',
        },
      },
      height: {
        '18': '4.5rem',
      },
      animation: {
        'gentle-pulse': 'gentle-pulse 2s infinite',
        'flip-card': 'flip-card 0.6s ease-in-out',
        'float': 'float 3s ease-in-out infinite',
        'ring-pulse': 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      keyframes: {
        'gentle-pulse': {
          '0%, 100%': {
            boxShadow: '0 0 0 0 rgba(59, 130, 246, 0.7)'
          },
          '50%': {
            boxShadow: '0 0 0 10px rgba(59, 130, 246, 0)'
          },
        },
        'flip-card': {
          '0%': { transform: 'rotateY(0deg)' },
          '50%': { transform: 'rotateY(90deg)' },
          '100%': { transform: 'rotateY(0deg)' },
        },
        'float': {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-5px)' },
        },
        'ring-pulse': {
          '0%, 100%': {
            boxShadow: '0 0 0 2px rgb(250 204 21)'
          },
          '50%': {
            boxShadow: '0 0 0 2px rgb(234 179 8 / 0.5)'
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
