import React from 'react';

// Alternative Pure CSS Version (No JS for star generation)
export const PureCSSParallaxStars = () => {
  return (
    <div className="relative w-full h-full overflow-hidden ">
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