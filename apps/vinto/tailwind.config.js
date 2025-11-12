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
        '3xs': '0.5rem', // 8px
        '2xs': '0.5625rem', // 9px
        'xs': '0.625rem', // 10px
      },
      // Remove colors entirely - use CSS variable utility classes instead
      // All colors are now defined in theme.css as CSS variables with utility classes
      width: {
        104: '26rem', // 416px - for desktop right sidebar
      },
      height: {
        18: '4.5rem',
      },
      animation: {
        // Color-based animations moved to theme.css to use CSS variables
        'flip-to-front': 'flip-to-front 0.6s ease-in-out',
        'flip-to-back': 'flip-to-back 0.6s ease-in-out',
        float: 'float 3s ease-in-out infinite',
        'card-click': 'card-click-feedback 0.1s ease',
        'card-select-pulse': 'card-select-pulse 1.5s ease-in-out infinite',
        'gentle-pulse': 'gentle-pulse 2s infinite',
        'ring-pulse': 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'pending-card-border': 'pending-card-border 2s ease-in-out infinite',
        'swap-select-border': 'swap-select-border 1.5s ease-in-out infinite',
        shake: 'shake 0.8s ease-in-out',

        // Multi-player visibility animations
        'deal-card': 'deal-card 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)',
        'peek-shift-focus': 'peek-shift-focus 2.5s ease-in-out',
        'peek-indicator': 'peek-indicator 2.5s ease-in-out',
        'target-select': 'target-select 1.5s ease-out',
        'target-confirm': 'target-confirm 0.8s ease-out',
        'swap-arc-reveal':
          'swap-arc-reveal 2s cubic-bezier(0.68, -0.55, 0.265, 1.55)',
        'swap-arc-blind':
          'swap-arc-blind 2s cubic-bezier(0.68, -0.55, 0.265, 1.55)',
        'king-target-highlight': 'king-target-highlight 2.5s ease-in-out',
        'king-reveal-correct': 'king-reveal-correct 1.5s ease-out',
        'king-reveal-wrong': 'king-reveal-wrong 1.2s ease-in-out',
        'action-activation': 'action-activation 1s ease-out',
        'action-complete': 'action-complete 0.8s ease-out',
        'declare-success': 'declare-success 1s ease-out',
        'declare-fail': 'declare-fail 0.8s ease-in-out',
        'toss-in-valid': 'toss-in-valid 0.8s ease-out',
        'toss-in-invalid': 'toss-in-invalid 1s ease-in-out',
        'turn-start-pulse': 'turn-start-pulse 1.5s ease-out',
        'thinking-indicator': 'thinking-indicator 1.5s ease-in-out infinite',
      },
      keyframes: {
        // Flip animations for card reveal/unreveal
        'flip-to-front': {
          '0%': { transform: 'rotateY(180deg)' },
          '100%': { transform: 'rotateY(0deg)' },
        },
        'flip-to-back': {
          '0%': { transform: 'rotateY(0deg)' },
          '100%': { transform: 'rotateY(180deg)' },
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
        shake: {
          '0%, 100%': { transform: 'translateX(0) rotate(0deg)' },
          '12.5%': { transform: 'translateX(-10px) rotate(-5deg)' },
          '25%': { transform: 'translateX(10px) rotate(5deg)' },
          '37.5%': { transform: 'translateX(-10px) rotate(-5deg)' },
          '50%': { transform: 'translateX(10px) rotate(5deg)' },
          '62.5%': { transform: 'translateX(-5px) rotate(-2deg)' },
          '75%': { transform: 'translateX(5px) rotate(2deg)' },
        },

        // Multi-player visibility keyframes
        'deal-card': {
          '0%': {
            transform:
              'translate(var(--deal-from-x, 0), var(--deal-from-y, 0)) rotate(-5deg) scale(0.8)',
            opacity: '0.5',
            zIndex: '20',
          },
          '60%': {
            transform:
              'translate(calc(var(--deal-to-x, 0) * 0.9), calc(var(--deal-to-y, 0) * 0.9)) rotate(2deg) scale(1.05)',
            opacity: '1',
            zIndex: '20',
          },
          '100%': {
            transform:
              'translate(var(--deal-to-x, 0), var(--deal-to-y, 0)) rotate(0deg) scale(1)',
            opacity: '1',
            zIndex: '10',
          },
        },
        'peek-shift-focus': {
          '0%': {
            transform: 'translate(0, 0) scale(1)',
            zIndex: '30',
          },
          '20%': {
            transform:
              'translate(var(--peek-shift-x, 0), var(--peek-shift-y, -30px)) scale(1.15)',
            zIndex: '30',
          },
          '80%': {
            transform:
              'translate(var(--peek-shift-x, 0), var(--peek-shift-y, -30px)) scale(1.15)',
            zIndex: '30',
          },
          '100%': {
            transform: 'translate(0, 0) scale(1)',
            zIndex: '30',
          },
        },
        'peek-indicator': {
          '0%, 100%': {
            boxShadow: '0 0 0 0 rgba(var(--color-info), 0)',
            filter: 'brightness(1)',
          },
          '20%': {
            boxShadow: '0 0 25px 8px rgba(var(--color-info), 0.7)',
            filter: 'brightness(1.2)',
          },
          '80%': {
            boxShadow: '0 0 25px 8px rgba(var(--color-info), 0.7)',
            filter: 'brightness(1.2)',
          },
        },
        'target-select': {
          '0%': {
            boxShadow: '0 0 0 0 rgba(var(--color-warning), 1)',
            transform: 'scale(1)',
          },
          '40%': {
            boxShadow: '0 0 0 15px rgba(var(--color-warning), 0)',
            transform: 'scale(1.08)',
          },
          '100%': {
            boxShadow: '0 0 0 3px rgba(var(--color-warning), 0.6)',
            transform: 'scale(1)',
          },
        },
        'target-confirm': {
          '0%': {
            transform: 'scale(1)',
            boxShadow: '0 0 0 0 rgba(var(--color-success), 0.8)',
          },
          '50%': {
            transform: 'scale(1.12)',
            boxShadow: '0 0 20px 8px rgba(var(--color-success), 0)',
          },
          '100%': {
            transform: 'scale(1)',
            boxShadow: '0 0 0 0 rgba(var(--color-success), 0)',
          },
        },
        'swap-arc-reveal': {
          '0%': {
            transform: 'translate(0, 0) rotateY(0deg) scale(1)',
            opacity: '1',
            zIndex: '25',
          },
          '20%': {
            transform: 'translate(25%, -50px) rotateY(90deg) scale(1.15)',
            opacity: '0.9',
            zIndex: '25',
          },
          '50%': {
            transform: 'translate(50%, -70px) rotateY(180deg) scale(1.2)',
            opacity: '0.8',
            zIndex: '25',
          },
          '80%': {
            transform: 'translate(75%, -50px) rotateY(270deg) scale(1.15)',
            opacity: '0.9',
            zIndex: '25',
          },
          '100%': {
            transform: 'translate(100%, 0) rotateY(360deg) scale(1)',
            opacity: '1',
            zIndex: '25',
          },
        },
        'swap-arc-blind': {
          '0%': {
            transform: 'translate(0, 0) scale(1)',
            opacity: '1',
            zIndex: '25',
          },
          '25%': {
            transform: 'translate(25%, -50px) scale(1.15)',
            opacity: '0.9',
            zIndex: '25',
          },
          '50%': {
            transform: 'translate(50%, -70px) scale(1.2)',
            opacity: '0.8',
            zIndex: '25',
          },
          '75%': {
            transform: 'translate(75%, -50px) scale(1.15)',
            opacity: '0.9',
            zIndex: '25',
          },
          '100%': {
            transform: 'translate(100%, 0) scale(1)',
            opacity: '1',
            zIndex: '25',
          },
        },
        'king-target-highlight': {
          '0%': {
            transform: 'scale(1)',
            boxShadow: '0 0 0 0 rgba(var(--color-warning), 0)',
            filter: 'brightness(1)',
          },
          '15%': {
            transform: 'scale(1.15)',
            boxShadow: '0 0 30px 12px rgba(var(--color-warning), 1)',
            filter: 'brightness(1.4)',
          },
          '30%, 70%': {
            transform: 'scale(1.12)',
            boxShadow: '0 0 25px 10px rgba(var(--color-warning), 0.8)',
            filter: 'brightness(1.3)',
          },
          '85%': {
            transform: 'scale(1.08)',
            boxShadow: '0 0 15px 6px rgba(var(--color-warning), 0.5)',
            filter: 'brightness(1.15)',
          },
          '100%': {
            transform: 'scale(1)',
            boxShadow: '0 0 0 0 rgba(var(--color-warning), 0)',
            filter: 'brightness(1)',
          },
        },
        'king-reveal-correct': {
          '0%': {
            transform: 'scale(1) rotateY(0deg)',
            boxShadow: '0 0 0 0 rgba(var(--color-success), 0.8)',
          },
          '25%': {
            transform: 'scale(1.2) rotateY(90deg)',
            boxShadow: '0 0 35px 15px rgba(var(--color-success), 0.3)',
          },
          '50%': {
            transform: 'scale(1.25) rotateY(180deg)',
            boxShadow: '0 0 40px 18px rgba(var(--color-success), 0)',
          },
          '75%': {
            transform: 'scale(1.2) rotateY(270deg)',
            boxShadow: '0 0 35px 15px rgba(var(--color-success), 0.3)',
          },
          '100%': {
            transform: 'scale(1) rotateY(360deg)',
            boxShadow: '0 0 0 0 rgba(var(--color-success), 0)',
          },
        },
        'king-reveal-wrong': {
          '0%, 100%': {
            transform: 'translateX(0) rotate(0deg) scale(1)',
            boxShadow: '0 0 0 0 rgba(var(--color-error), 0)',
          },
          '15%': {
            transform: 'translateX(-15px) rotate(-8deg) scale(1.05)',
            boxShadow: '0 0 25px 8px rgba(var(--color-error), 1)',
          },
          '30%': {
            transform: 'translateX(15px) rotate(8deg) scale(1.05)',
            boxShadow: '0 0 25px 8px rgba(var(--color-error), 1)',
          },
          '45%': {
            transform: 'translateX(-15px) rotate(-8deg) scale(1.05)',
            boxShadow: '0 0 25px 8px rgba(var(--color-error), 1)',
          },
          '60%': {
            transform: 'translateX(15px) rotate(8deg) scale(1.05)',
            boxShadow: '0 0 20px 6px rgba(var(--color-error), 0.7)',
          },
          '75%': {
            transform: 'translateX(-8px) rotate(-4deg) scale(1.02)',
            boxShadow: '0 0 15px 4px rgba(var(--color-error), 0.4)',
          },
          '90%': {
            transform: 'translateX(8px) rotate(4deg) scale(1.02)',
            boxShadow: '0 0 10px 2px rgba(var(--color-error), 0.2)',
          },
        },
        'action-activation': {
          '0%': {
            transform: 'scale(1) rotate(0deg)',
            boxShadow: '0 0 0 0 rgba(var(--color-accent), 0.9)',
            filter: 'brightness(1)',
          },
          '40%': {
            transform: 'scale(1.25) rotate(10deg)',
            boxShadow: '0 0 40px 15px rgba(var(--color-accent), 0)',
            filter: 'brightness(1.5)',
          },
          '100%': {
            transform: 'scale(1) rotate(0deg)',
            boxShadow: '0 0 0 0 rgba(var(--color-accent), 0)',
            filter: 'brightness(1)',
          },
        },
        'action-complete': {
          '0%': {
            transform: 'scale(1)',
            opacity: '1',
          },
          '50%': {
            transform: 'scale(1.15)',
            opacity: '1',
          },
          '100%': {
            transform: 'scale(0.8)',
            opacity: '0',
          },
        },
        'declare-success': {
          '0%': {
            transform: 'scale(1)',
            boxShadow: '0 0 0 0 rgba(var(--color-success), 0.7)',
          },
          '50%': {
            transform: 'scale(1.2) rotate(5deg)',
            boxShadow: '0 0 35px 12px rgba(var(--color-success), 0)',
          },
          '100%': {
            transform: 'scale(1) rotate(0deg)',
            boxShadow: '0 0 0 0 rgba(var(--color-success), 0)',
          },
        },
        'declare-fail': {
          '0%, 100%': {
            transform: 'translateX(0)',
            filter: 'brightness(1)',
          },
          '25%, 75%': {
            transform: 'translateX(-12px)',
            filter: 'brightness(1.3) hue-rotate(15deg)',
          },
          '50%': {
            transform: 'translateX(12px)',
            filter: 'brightness(1.3) hue-rotate(-15deg)',
          },
        },
        'toss-in-valid': {
          '0%': {
            transform: 'translateY(0) scale(1)',
            opacity: '1',
          },
          '40%': {
            transform: 'translateY(-30px) scale(1.15)',
            opacity: '0.9',
          },
          '100%': {
            transform: 'translateY(80px) scale(0.85)',
            opacity: '0',
          },
        },
        'toss-in-invalid': {
          '0%': {
            transform: 'translateY(0) scale(1)',
          },
          '20%': {
            transform: 'translateY(-25px) scale(1.1)',
          },
          '40%': {
            transform: 'translateY(15px) scale(0.95)',
          },
          '60%': {
            transform: 'translateY(-12px) scale(1.05)',
          },
          '80%': {
            transform: 'translateY(8px) scale(0.98)',
          },
          '100%': {
            transform: 'translateY(0) scale(1)',
          },
        },
        'turn-start-pulse': {
          '0%': {
            boxShadow: '0 0 0 0 rgba(var(--color-current-player-glow), 0.8)',
            transform: 'scale(1)',
          },
          '50%': {
            boxShadow: '0 0 0 20px rgba(var(--color-current-player-glow), 0)',
            transform: 'scale(1.05)',
          },
          '100%': {
            boxShadow: '0 0 0 0 rgba(var(--color-current-player-glow), 0)',
            transform: 'scale(1)',
          },
        },
        'thinking-indicator': {
          '0%, 100%': {
            opacity: '0.4',
            transform: 'scale(0.95)',
          },
          '50%': {
            opacity: '1',
            transform: 'scale(1.05)',
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
