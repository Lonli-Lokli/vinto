// Button Color System
// Defines consistent colors across all buttons to enable muscle memory

export const BUTTON_COLORS = {
  // Primary action (recommended/most common) - Green
  primary: {
    bg: '#2ECC71',
    hover: '#27AE60',
    active: '#229954',
    text: '#FFFFFF',
    classes: 'bg-[#2ECC71] hover:bg-[#27AE60] active:bg-[#229954] text-white',
  },

  // Secondary action (alternative option) - Blue
  secondary: {
    bg: '#3498DB',
    hover: '#2980B9',
    active: '#21618C',
    text: '#FFFFFF',
    classes: 'bg-[#3498DB] hover:bg-[#2980B9] active:bg-[#21618C] text-white',
  },

  // Neutral action (passive choices) - Dark Slate
  neutral: {
    bg: '#34495E',
    hover: '#2C3E50',
    active: '#1C2833',
    text: '#FFFFFF',
    classes: 'bg-[#34495E] hover:bg-[#2C3E50] active:bg-[#1C2833] text-white',
  },

  // High-stakes action (game-changing) - Orange
  warning: {
    bg: '#E67E22',
    hover: '#D35400',
    active: '#BA4A00',
    text: '#FFFFFF',
    classes: 'bg-[#E67E22] hover:bg-[#D35400] active:bg-[#BA4A00] text-white',
  },

  // Declare rank options - Amber
  declare: {
    bg: '#F39C12',
    hover: '#E67E22',
    active: '#CA6F1E',
    text: '#FFFFFF',
    classes: 'bg-[#F39C12] hover:bg-[#E67E22] active:bg-[#CA6F1E] text-white',
  },

  // Disabled state - Light Gray
  disabled: {
    bg: '#BDC3C7',
    text: '#FFFFFF',
    opacity: 0.6,
    classes: 'bg-gray-300 text-gray-600 cursor-not-allowed',
  },
} as const;

export type ButtonVariant = keyof typeof BUTTON_COLORS;

// Button action mappings
export const BUTTON_ACTION_VARIANTS = {
  // Primary actions (green)
  'draw-card': 'primary',
  'use-action': 'primary',
  'continue': 'primary',

  // Secondary actions (blue)
  'swap': 'secondary',
  'start-game': 'secondary',
  'continue-toss': 'secondary',

  // Neutral actions (dark slate)
  'discard': 'neutral',
  'discard-instead': 'neutral',
  'skip': 'neutral',
  'cancel': 'neutral',
  'reset': 'neutral',

  // High-stakes (orange)
  'call-vinto': 'warning',

  // Declare actions (amber)
  'declare-rank': 'declare',
  'king-action-card': 'declare',
  'king-non-action-card': 'neutral',
} as const;

export type ButtonAction = keyof typeof BUTTON_ACTION_VARIANTS;

/**
 * Get the appropriate button classes for a given action
 */
export function getButtonClasses(
  action: ButtonAction,
  disabled = false
): string {
  if (disabled) {
    return `${BUTTON_COLORS.disabled.classes} shadow-none`;
  }

  const variant = BUTTON_ACTION_VARIANTS[action];
  return `${BUTTON_COLORS[variant].classes} font-semibold rounded shadow-sm transition-colors`;
}

/**
 * Get button classes by variant directly
 */
export function getButtonVariantClasses(
  variant: ButtonVariant,
  disabled = false
): string {
  if (disabled) {
    return `${BUTTON_COLORS.disabled.classes} shadow-none`;
  }

  return `${BUTTON_COLORS[variant].classes} font-semibold rounded shadow-sm transition-colors`;
}
