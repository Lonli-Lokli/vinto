// components/help-popover.tsx
'use client';

import React, { useState } from 'react';
import { Popover } from 'react-tiny-popover';
import { Rank } from '../shapes';
import { getCardHelpText } from '../constants/game-setup';

interface HelpPopoverProps {
  title: string;
  content?: string;
  rank?: Rank;
}

export const HelpPopover: React.FC<HelpPopoverProps> = ({
  title,
  content,
  rank,
}) => {
  const [showHelp, setShowHelp] = useState(false);

  const helpText = rank ? getCardHelpText(rank) : content;

  const helpContent = (
    <div
      className="bg-white border border-gray-300 rounded p-3 max-w-sm shadow-lg"
      style={{ zIndex: 9999 }}
    >
      <div className="text-sm text-gray-700 space-y-2">
        <p className="font-semibold text-gray-800">{title}</p>
        <div className="text-xs whitespace-pre-line">{helpText}</div>
      </div>
      <button
        onClick={() => setShowHelp(false)}
        className="mt-2 text-sm text-gray-500 hover:text-gray-700"
      >
        Close
      </button>
    </div>
  );

  return (
    <Popover
      isOpen={showHelp}
      positions={['top', 'bottom', 'left', 'right']}
      content={helpContent}
      onClickOutside={() => setShowHelp(false)}
    >
      <button
        onClick={() => setShowHelp(!showHelp)}
        className="text-gray-400 hover:text-gray-600 transition-colors p-1 flex-shrink-0"
        aria-label="Show help"
      >
        <svg
          className="w-5 h-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      </button>
    </Popover>
  );
};
