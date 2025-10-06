// components/ui/button.tsx
'use client';

import React from 'react';
import {
  Zap,
  Repeat,
  Trash2,
  Trophy,
  Play,
  ChevronRight,
  Settings,
  Crown,
  Target,
  RotateCcw,
  X,
  HelpCircle,
  Sparkles,
  ArrowRight
} from 'lucide-react';
import { getButtonClasses, ButtonAction } from '../../constants/button-colors';
import { Avatar } from '../avatar';

/**
 * Button Design System
 *
 * Classification:
 * 1. Primary Actions: draw-card, use-action, call-vinto
 * 2. Secondary Actions: swap, continue-toss, start-game
 * 3. Destructive/Warning: discard, reset, cancel
 * 4. Action-specific: skip, king-declaration
 */

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonAction;
  icon?: React.ReactNode;
  fullWidth?: boolean;
  children: React.ReactNode;
}

/**
 * Base Button Component
 * Uses the existing button-colors system for consistent styling
 */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'use-action',
      icon,
      fullWidth = false,
      className = '',
      disabled = false,
      children,
      ...props
    },
    ref
  ) => {
    const baseClasses = getButtonClasses(variant, disabled);
    const widthClass = fullWidth ? 'w-full' : '';
    const iconGap = icon ? 'gap-1' : '';

    return (
      <button
        ref={ref}
        disabled={disabled}
        className={`${baseClasses} ${widthClass} ${iconGap} flex flex-row items-center justify-center py-1.5 px-2 text-xs min-h-[36px] ${className}`}
        {...props}
      >
        {icon && <span>{icon}</span>}
        <span>{children}</span>
      </button>
    );
  }
);

Button.displayName = 'Button';

/**
 * Specialized Button Components
 * These provide semantic names and default configurations
 */

export const DrawCardButton = ({
  onClick,
  disabled = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <Button
    variant="draw-card"
    icon={<Sparkles size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
    title={disabled ? 'Deck is empty' : 'Draw a new card from deck'}
  >
    Draw Card
  </Button>
);

export const UseActionButton = ({
  onClick,
  disabled = false,
  className = '',
  children = 'Use',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="use-action"
    icon={<Zap size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    {children}
  </Button>
);

export const SwapButton = ({
  onClick,
  disabled = false,
  className = '',
  children = 'Swap',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="swap"
    icon={<Repeat size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    {children}
  </Button>
);

export const DiscardButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Discard',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="discard"
    icon={<Trash2 size={14} />}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);

export const CallVintoButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Call Vinto',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="call-vinto"
    icon={<Trophy size={14} />}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);

export const StartGameButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
}) => (
  <Button
    variant="start-game"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={`py-1.5 px-3 ${className}`}
  >
    Start Game
  </Button>
);

export const ContinueButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Continue',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="continue-toss"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={`px-3 whitespace-nowrap ${fullWidth ? '' : 'flex-shrink-0'} ${className}`}
  >
    <span className="flex items-center gap-1">
      {children}
      <ChevronRight size={14} />
    </span>
  </Button>
);

export const DiscardInsteadButton = ({
  onClick,
  disabled = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <Button
    variant="discard-instead"
    onClick={onClick}
    disabled={disabled}
    className={`px-3 whitespace-nowrap flex-shrink-0 ${className}`}
  >
    Discard Instead
  </Button>
);

export const SkipButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Skip',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="skip"
    icon={<ArrowRight size={14} />}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);

export const ResetButton = ({
  onClick,
  disabled = false,
  className = '',
  children = 'Reset',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="reset"
    icon={<RotateCcw size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    {children}
  </Button>
);

export const CancelButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Cancel',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="cancel"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);

export const QueenSwapButton = ({
  onClick,
  disabled = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <Button
    variant="swap"
    icon={<Repeat size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    Swap Cards
  </Button>
);

/**
 * Difficulty Selection Button
 * Special toggle button with active state
 */
export const DifficultyButton = ({
  level,
  isActive,
  onClick,
  className = '',
}: {
  level: 'easy' | 'moderate' | 'hard';
  isActive: boolean;
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 rounded text-[10px] font-semibold transition-colors ${
      isActive
        ? 'bg-emerald-500 text-white'
        : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
    } ${className}`}
    title={`Difficulty: ${level}`}
  >
    {level[0].toUpperCase()}
  </button>
);

/**
 * Settings Button
 * Icon-only button for mobile settings
 */
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

/**
 * Deck Manager Button
 * Shows card count with icon
 */
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

/**
 * King Declaration Button
 * For selecting card ranks in King action
 */
export const KingActionCardButton = ({
  rank,
  onClick,
  disabled = false,
  className = '',
}: {
  rank: string;
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`${getButtonClasses(
      'king-action-card',
      disabled
    )} font-bold py-1 px-2 text-xs min-h-[32px] flex items-center justify-center ${className}`}
    title={disabled ? `Cannot declare ${rank}` : `Execute ${rank} action`}
  >
    {rank}
  </button>
);

export const KingNonActionCardButton = ({
  rank,
  onClick,
  disabled = false,
  className = '',
}: {
  rank: string;
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`${getButtonClasses(
      'king-non-action-card',
      disabled
    )} font-medium py-1 px-1.5 text-xs flex items-center justify-center min-h-[32px] ${className}`}
    title={disabled ? `Cannot declare ${rank}` : `Declare ${rank} (no action)`}
  >
    {rank}
  </button>
);

/**
 * Utility Buttons
 * For error states and special UI actions
 */

export const ReloadButton = ({
  onClick,
  children = 'Reload Page',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`w-full bg-emerald-600 hover:bg-emerald-700 text-white font-medium py-2 px-4 rounded-lg flex items-center justify-center gap-2 transition-colors ${className}`}
  >
    {children}
  </button>
);

export const TryAgainButton = ({
  onClick,
  children = 'Try Again',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`w-full bg-emerald-600 hover:bg-emerald-700 text-white font-medium py-2 px-4 rounded-lg flex items-center justify-center gap-2 transition-colors ${className}`}
  >
    {children}
  </button>
);

/**
 * Action-specific utility buttons
 */

export const OpponentSelectButton = ({
  opponentName,
  onClick,
  showAvatar = false,
  player,
  isSelected = false,
  className = '',
}: {
  opponentName: string;
  onClick: () => void;
  showAvatar?: boolean;
  player?: any; // Player object for Avatar component
  isSelected?: boolean;
  className?: string;
}) => {
  if (showAvatar && player) {
    const borderClass = isSelected
      ? 'border-2 border-blue-400 bg-blue-50'
      : 'border-2 border-gray-200 bg-transparent hover:bg-gray-50';

    return (
      <button
        onClick={onClick}
        className={`flex flex-col items-center gap-1.5 p-2 rounded-lg ${borderClass} transition-all active:scale-95 ${className}`}
      >
        {/* Avatar Image - md size */}
        <div className="flex items-center justify-center">
          <Avatar player={player} size="md" />
        </div>
        {/* Player Name */}
        <div className="text-xs font-medium text-gray-800 text-center line-clamp-1 w-full">
          {opponentName}
        </div>
      </button>
    );
  }

  return (
    <button
      onClick={onClick}
      className={`${getButtonClasses(
        'swap'
      )} py-3 px-4 rounded-lg text-base flex flex-row items-center justify-center gap-2 min-h-[44px] ${className}`}
    >
      <Target size={18} />
      <span>{opponentName}</span>
    </button>
  );
};

/**
 * Rank Declaration Button
 */
export const RankDeclarationButton = ({
  rank,
  onClick,
  className = '',
}: {
  rank: string;
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`${getButtonClasses(
      'declare-rank'
    )} font-bold py-1.5 px-1 text-xs min-h-[36px] flex items-center justify-center ${className}`}
    title={`Declare ${rank}`}
  >
    {rank}
  </button>
);

/**
 * Replay/Command History Buttons
 */
export const ExportButton = ({
  onClick,
  children = 'Export',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 rounded bg-blue-100 hover:bg-blue-200 text-blue-700 font-semibold text-xs transition-colors ${className}`}
    title="Export command history"
  >
    {children}
  </button>
);

export const ReplayButton = ({
  onClick,
  children = 'Replay',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 rounded bg-green-100 hover:bg-green-200 text-green-700 font-semibold text-xs transition-colors ${className}`}
    title="Load game in replay mode"
  >
    {children}
  </button>
);

export const LoadReplayButton = ({
  onClick,
  disabled = false,
  children = '📂 Load Replay',
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`px-4 py-2 rounded-lg font-semibold text-sm shadow-lg transition-all ${
      disabled
        ? 'bg-gray-400 text-gray-600 cursor-not-allowed'
        : 'bg-blue-600 hover:bg-blue-700 text-white hover:shadow-xl'
    } ${className}`}
  >
    {children}
  </button>
);

export const ExitReplayButton = ({
  onClick,
  className = '',
}: {
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 bg-red-700 hover:bg-red-600 text-white text-xs rounded transition-colors ${className}`}
  >
    Exit
  </button>
);

export const NextCommandButton = ({
  onClick,
  disabled = false,
  isExecuting = false,
  hasNext = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  isExecuting?: boolean;
  hasNext?: boolean;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`flex-1 px-6 py-3 rounded-lg font-bold text-lg transition-all ${
      hasNext && !isExecuting
        ? 'bg-green-600 hover:bg-green-500 text-white shadow-lg hover:shadow-xl'
        : 'bg-gray-600 text-gray-400 cursor-not-allowed'
    } ${className}`}
  >
    {isExecuting ? 'Executing...' : hasNext ? 'Next →' : 'Finished'}
  </button>
);

/**
 * Utility close button for popovers
 */
export const ClosePopoverButton = ({
  onClick,
  className = '',
}: {
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`text-gray-400 hover:text-gray-600 text-lg leading-none ${className}`}
  >
    ×
  </button>
);

/**
 * Help button for popovers
 */
export const HelpButton = React.forwardRef<
  HTMLButtonElement,
  {
    onClick: () => void;
    className?: string;
  }
>(({ onClick, className = '' }, ref) => (
  <button
    ref={ref}
    onClick={onClick}
    className={`text-gray-400 hover:text-gray-600 transition-colors p-1 flex-shrink-0 ${className}`}
    aria-label="Show help"
    type="button"
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
));

HelpButton.displayName = 'HelpButton';

/**
 * Deck card selection button for DeckManagerPopover
 */
export const DeckCardSelectButton = ({
  onClick,
  disabled = false,
  children,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`
      relative flex flex-col items-center gap-2 p-3 rounded-lg border-2 transition-all
      ${
        !disabled
          ? 'border-emerald-500 hover:bg-emerald-50 cursor-pointer hover:shadow-md hover:scale-105'
          : 'border-gray-200 opacity-40 cursor-not-allowed bg-gray-50'
      }
      ${className}
    `}
  >
    {children}
  </button>
);
