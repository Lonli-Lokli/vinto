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
    className={`flex items-center gap-1 px-2 py-1 rounded bg-emerald-50 hover:bg-emerald-100 transition-colors group ${className}`}
    title="Manage deck - Set next card to draw"
  >
    <div className="text-sm font-semibold text-emerald-700">{cardCount}</div>
    <div className="text-2xs text-emerald-600 hidden sm:block">🎴</div>
  </button>
));

DeckManagerButton.displayName = 'DeckManagerButton';
