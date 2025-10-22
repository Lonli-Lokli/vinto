import React, { useEffect, useState } from 'react';

export const ParallaxStars = () => {
  const [stars, setStars] = useState({ small: '', medium: '', large: '' });

  useEffect(() => {
    // Generate random star positions using box-shadow
    const generateStars = (count: number) => {
      const shadows = [];
      for (let i = 0; i < count; i++) {
        const x = Math.floor(Math.random() * 2000);
        const y = Math.floor(Math.random() * 2000);
        shadows.push(`${x}px ${y}px currentColor`);
      }
      return shadows.join(',');
    };

    // Generate stars only on client side to avoid SSR issues
    setStars({
      small: generateStars(700),
      medium: generateStars(200),
      large: generateStars(100),
    });
  }, []);

  return (
    <div className="relative w-full h-screen overflow-hidden bg-gradient-to-b from-slate-50 to-blue-50 dark:from-slate-950 dark:to-blue-950">
      {/* Background gradient overlay for depth */}
      <div className="absolute inset-0 bg-gradient-to-b from-transparent via-blue-500/5 to-purple-500/10 dark:via-blue-500/10 dark:to-purple-500/20" />

      {/* Stars layers */}
      <div className="stars-container absolute inset-0">
        {/* Small stars - fastest movement */}
        <div className="stars stars-small" style={{ boxShadow: stars.small }} />
        <div
          className="stars stars-small-2"
          style={{ boxShadow: stars.small }}
        />

        {/* Medium stars - medium movement */}
        <div
          className="stars stars-medium"
          style={{ boxShadow: stars.medium }}
        />
        <div
          className="stars stars-medium-2"
          style={{ boxShadow: stars.medium }}
        />

        {/* Large stars - slowest movement */}
        <div className="stars stars-large" style={{ boxShadow: stars.large }} />
        <div
          className="stars stars-large-2"
          style={{ boxShadow: stars.large }}
        />
      </div>

      {/* Optional content overlay */}
      <div className="relative z-10 flex items-center justify-center h-full">
        <div className="text-center px-4">
          <h1 className="text-5xl md:text-7xl font-bold text-gray-800 dark:text-white mb-4 tracking-wider">
            PURE CSS
          </h1>
          <p className="text-2xl md:text-3xl text-gray-600 dark:text-gray-300 tracking-widest">
            PARALLAX PIXEL STARS
          </p>
          <p className="mt-8 text-gray-500 dark:text-gray-400">
            Works seamlessly in both light and dark themes
          </p>
        </div>
      </div>

      <style jsx>{`
        .stars-container {
          transform: translateZ(0);
          will-change: transform;
        }

        .stars {
          position: absolute;
          background: transparent;
          border-radius: 50%;
          color: ${stars.small ? 'var(--star-color)' : 'transparent'};
        }

        /* Light mode star colors */
        :root {
          --star-color: rgb(99 102 241); /* Indigo color for light mode */
          --star-opacity: 0.8;
        }

        /* Dark mode star colors */
        @media (prefers-color-scheme: dark) {
          :root {
            --star-color: rgb(255 255 255); /* White for dark mode */
            --star-opacity: 1;
          }
        }

        /* Support for Tailwind dark mode class */
        :global(.dark) {
          --star-color: rgb(255 255 255);
          --star-opacity: 1;
        }

        :global(html:not(.dark)) {
          --star-color: rgb(99 102 241);
          --star-opacity: 0.8;
        }

        /* Small stars */
        .stars-small {
          width: 1px;
          height: 1px;
          opacity: var(--star-opacity);
          animation: animStar 50s linear infinite;
        }

        .stars-small-2 {
          width: 1px;
          height: 1px;
          opacity: var(--star-opacity);
          top: 2000px;
          animation: animStar 50s linear infinite;
          animation-delay: 25s;
        }

        /* Medium stars */
        .stars-medium {
          width: 2px;
          height: 2px;
          opacity: calc(var(--star-opacity) * 0.9);
          animation: animStar 100s linear infinite;
        }

        .stars-medium-2 {
          width: 2px;
          height: 2px;
          opacity: calc(var(--star-opacity) * 0.9);
          top: 2000px;
          animation: animStar 100s linear infinite;
          animation-delay: 50s;
        }

        /* Large stars */
        .stars-large {
          width: 3px;
          height: 3px;
          opacity: calc(var(--star-opacity) * 0.8);
          animation: animStar 150s linear infinite;
        }

        .stars-large-2 {
          width: 3px;
          height: 3px;
          opacity: calc(var(--star-opacity) * 0.8);
          top: 2000px;
          animation: animStar 150s linear infinite;
          animation-delay: 75s;
        }

        /* Parallax animation */
        @keyframes animStar {
          0% {
            transform: translateY(0px);
          }
          100% {
            transform: translateY(-2000px);
          }
        }

        /* Add subtle twinkling effect */
        @keyframes twinkle {
          0%,
          100% {
            opacity: var(--star-opacity);
          }
          50% {
            opacity: calc(var(--star-opacity) * 0.3);
          }
        }

        .stars:nth-child(odd) {
          animation-name: animStar, twinkle;
          animation-duration: 50s, 3s;
          animation-timing-function: linear, ease-in-out;
          animation-iteration-count: infinite, infinite;
        }

        /* Responsive adjustments */
        @media (max-width: 640px) {
          .stars-small,
          .stars-small-2 {
            animation-duration: 30s;
          }
          .stars-medium,
          .stars-medium-2 {
            animation-duration: 60s;
          }
          .stars-large,
          .stars-large-2 {
            animation-duration: 90s;
          }
        }

        /* Performance optimization for reduced motion preference */
        @media (prefers-reduced-motion: reduce) {
          .stars {
            animation: none !important;
          }
        }

        /* Additional theme-specific styles */
        @media (prefers-color-scheme: light) {
          .stars-small {
            filter: blur(0.5px);
          }
          .stars-medium {
            filter: blur(0.3px);
          }
        }

        @media (prefers-color-scheme: dark) {
          .stars {
            filter: none;
          }
          .stars-small {
            box-shadow: ${stars.small
              ? stars.small + ', 0 0 2px currentColor'
              : 'none'};
          }
          .stars-medium {
            box-shadow: ${stars.medium
              ? stars.medium + ', 0 0 3px currentColor'
              : 'none'};
          }
          .stars-large {
            box-shadow: ${stars.large
              ? stars.large + ', 0 0 4px currentColor'
              : 'none'};
          }
        }

        /* Tailwind dark mode support */
        :global(.dark) .stars-small {
          box-shadow: ${stars.small
            ? stars.small + ', 0 0 2px currentColor'
            : 'none'};
          filter: none;
        }
        :global(.dark) .stars-medium {
          box-shadow: ${stars.medium
            ? stars.medium + ', 0 0 3px currentColor'
            : 'none'};
          filter: none;
        }
        :global(.dark) .stars-large {
          box-shadow: ${stars.large
            ? stars.large + ', 0 0 4px currentColor'
            : 'none'};
          filter: none;
        }

        :global(html:not(.dark)) .stars-small {
          filter: blur(0.5px);
        }
        :global(html:not(.dark)) .stars-medium {
          filter: blur(0.3px);
        }
      `}</style>
    </div>
  );
};

// Alternative Pure CSS Version (No JS for star generation)
export const PureCSSParallaxStars = () => {
  return (
    <div className="relative w-full h-screen overflow-hidden">
      {/* Gradient background */}
      <div className="absolute inset-0 bg-gradient-to-b from-sky-100 via-blue-100 to-indigo-200 dark:from-gray-900 dark:via-blue-950 dark:to-indigo-950 transition-colors duration-500" />

      {/* Pure CSS Stars using gradients and pseudo-elements */}
      <div className="stars-wrapper absolute inset-0">
        <div className="stars-layer-1" />
        <div className="stars-layer-2" />
        <div className="stars-layer-3" />
      </div>

      {/* Content */}
      
      <style jsx>{`
        .stars-wrapper {
          background: transparent;
        }

        /* Create stars using multiple backgrounds */
        .stars-layer-1,
        .stars-layer-2,
        .stars-layer-3 {
          position: absolute;
          width: 100%;
          height: 100%;
          background-repeat: repeat;
          background-size: 1000px 1000px;
          animation: stars-move linear infinite;
        }

        /* Small stars layer */
        .stars-layer-1 {
          background-image: radial-gradient(
              1px 1px at 20px 30px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 40px 70px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 80px 40px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 130px 80px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 170px 10px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 200px 120px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 250px 60px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 300px 90px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 350px 150px,
              var(--tw-star-color-1, #818cf8),
              transparent
            ),
            radial-gradient(
              1px 1px at 400px 30px,
              var(--tw-star-color-1, #818cf8),
              transparent
            );
          animation-duration: 50s;
          opacity: 0.8;
        }

        /* Medium stars layer */
        .stars-layer-2 {
          background-image: radial-gradient(
              2px 2px at 50px 100px,
              var(--tw-star-color-2, #6366f1),
              transparent
            ),
            radial-gradient(
              2px 2px at 150px 50px,
              var(--tw-star-color-2, #6366f1),
              transparent
            ),
            radial-gradient(
              2px 2px at 250px 150px,
              var(--tw-star-color-2, #6366f1),
              transparent
            ),
            radial-gradient(
              2px 2px at 350px 200px,
              var(--tw-star-color-2, #6366f1),
              transparent
            ),
            radial-gradient(
              2px 2px at 450px 100px,
              var(--tw-star-color-2, #6366f1),
              transparent
            );
          animation-duration: 100s;
          opacity: 0.6;
        }

        /* Large stars layer */
        .stars-layer-3 {
          background-image: radial-gradient(
              3px 3px at 100px 200px,
              var(--tw-star-color-3, #4f46e5),
              transparent
            ),
            radial-gradient(
              3px 3px at 300px 100px,
              var(--tw-star-color-3, #4f46e5),
              transparent
            ),
            radial-gradient(
              3px 3px at 500px 250px,
              var(--tw-star-color-3, #4f46e5),
              transparent
            );
          animation-duration: 150s;
          opacity: 0.4;
        }

        /* Animation keyframes */
        @keyframes stars-move {
          from {
            transform: translateY(0);
          }
          to {
            transform: translateY(-1000px);
          }
        }

        /* Light theme stars */
        @media (prefers-color-scheme: light) {
          .stars-layer-1,
          .stars-layer-2,
          .stars-layer-3 {
            --tw-star-color-1: #818cf8;
            --tw-star-color-2: #6366f1;
            --tw-star-color-3: #4f46e5;
          }
        }

        /* Dark theme stars */
        @media (prefers-color-scheme: dark) {
          .stars-layer-1,
          .stars-layer-2,
          .stars-layer-3 {
            --tw-star-color-1: #ffffff;
            --tw-star-color-2: #fef3c7;
            --tw-star-color-3: #fbbf24;
          }
        }

        /* Tailwind dark mode class support */
        :global(.dark) .stars-layer-1,
        :global(.dark) .stars-layer-2,
        :global(.dark) .stars-layer-3 {
          --tw-star-color-1: #ffffff;
          --tw-star-color-2: #fef3c7;
          --tw-star-color-3: #fbbf24;
        }

        :global(html:not(.dark)) .stars-layer-1,
        :global(html:not(.dark)) .stars-layer-2,
        :global(html:not(.dark)) .stars-layer-3 {
          --tw-star-color-1: #818cf8;
          --tw-star-color-2: #6366f1;
          --tw-star-color-3: #4f46e5;
        }
      `}</style>
    </div>
  );
};

// Interactive version with mouse parallax
export const InteractiveParallaxStars = () => {
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMousePos({
        x: (e.clientX / window.innerWidth - 0.5) * 20,
        y: (e.clientY / window.innerHeight - 0.5) * 20,
      });
    };

    window.addEventListener('mousemove', handleMouseMove);
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  return (
    <div className="relative w-full h-screen overflow-hidden bg-gradient-to-b from-slate-100 to-slate-200 dark:from-slate-900 dark:to-slate-950">
      <div
        className="absolute inset-0 transition-transform duration-300 ease-out"
        style={{
          transform: `translate(${mousePos.x}px, ${mousePos.y}px)`,
        }}
      >
        <div className="star-field star-field-1" />
        <div className="star-field star-field-2" />
        <div className="star-field star-field-3" />
      </div>

      <div className="relative z-10 flex items-center justify-center h-full">
        <h1 className="text-5xl font-bold text-gray-800 dark:text-white">
          Move Your Mouse
        </h1>
      </div>

      <style jsx>{`
        .star-field {
          position: absolute;
          width: 110%;
          height: 110%;
          top: -5%;
          left: -5%;
          background-size: 200px 200px;
          animation: drift linear infinite;
        }

        .star-field-1 {
          background-image: radial-gradient(
              1px 1px at 20px 30px,
              currentColor,
              transparent
            ),
            radial-gradient(1px 1px at 40px 70px, currentColor, transparent),
            radial-gradient(1px 1px at 80px 40px, currentColor, transparent),
            radial-gradient(1px 1px at 130px 80px, currentColor, transparent);
          color: rgb(147 197 253 / 0.8);
          animation-duration: 120s;
        }

        .star-field-2 {
          background-image: radial-gradient(
              2px 2px at 50px 100px,
              currentColor,
              transparent
            ),
            radial-gradient(2px 2px at 150px 50px, currentColor, transparent);
          color: rgb(99 102 241 / 0.6);
          animation-duration: 180s;
        }

        .star-field-3 {
          background-image: radial-gradient(
            3px 3px at 100px 150px,
            currentColor,
            transparent
          );
          color: rgb(79 70 229 / 0.4);
          animation-duration: 240s;
        }

        @media (prefers-color-scheme: dark) {
          .star-field-1 {
            color: rgb(255 255 255 / 0.9);
          }
          .star-field-2 {
            color: rgb(254 243 199 / 0.7);
          }
          .star-field-3 {
            color: rgb(251 191 36 / 0.5);
          }
        }

        :global(.dark) .star-field-1 {
          color: rgb(255 255 255 / 0.9);
        }
        :global(.dark) .star-field-2 {
          color: rgb(254 243 199 / 0.7);
        }
        :global(.dark) .star-field-3 {
          color: rgb(251 191 36 / 0.5);
        }

        @keyframes drift {
          from {
            transform: translateY(0px);
          }
          to {
            transform: translateY(-200px);
          }
        }
      `}</style>
    </div>
  );
};
