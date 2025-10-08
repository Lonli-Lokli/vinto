import React from 'react';

export const DeckManagerButton = React.forwardRef<
  HTMLButtonElement,
  {
    cardCount: number;
    onClick: () => void;
    className?: string;
  }
>(({ cardCount, onClick, className = '' }, ref) => (
  <button
    ref={ref}
    onClick={onClick}
    className={`flex items-center gap-1 px-2 py-1 rounded bg-success-light hover:bg-success transition-colors group ${className}`}
    title="Manage deck - Set next card to draw"
  >
    <div className="text-sm font-semibold text-success">{cardCount}</div>
    <div className="text-2xs text-success hidden sm:block">🎴</div>
  </button>
));

DeckManagerButton.displayName = 'DeckManagerButton';
