import { Settings } from 'lucide-react';
import React from 'react';

export const SettingsButton = React.forwardRef<
  HTMLButtonElement,
  {
    onClick: () => void;
    className?: string;
  }
>(({ onClick, className = '' }, ref) => (
  <button
    ref={ref}
    onClick={onClick}
    className={`px-2 py-1 rounded bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold text-sm transition-colors flex items-center justify-center ${className}`}
    title="Settings"
  >
    <Settings size={16} />
  </button>
));

SettingsButton.displayName = 'SettingsButton';
