// Button Color System
// Defines consistent colors across all buttons to enable muscle memory

export const BUTTON_COLORS = {
  // Primary action (recommended/most common) - Green
  primary: {
    bg: 'var(--color-success)',
    hover: 'var(--color-success-dark)',
    active: 'var(--color-success-darker)',
    text: '#FFFFFF',
    classes:
      'bg-success hover:bg-success-dark active:bg-success-darker text-white',
  },

  // Secondary action (alternative option) - Blue
  secondary: {
    bg: 'var(--color-info)',
    hover: 'var(--color-info-dark)',
    active: 'var(--color-info-darker)',
    text: '#FFFFFF',
    classes: 'bg-info hover:bg-info-dark active:bg-info-darker text-white',
  },

  // Neutral action (passive choices) - Dark Slate
  neutral: {
    bg: 'var(--color-surface-secondary)',
    hover: 'var(--color-surface-tertiary)',
    active: 'var(--color-surface-quaternary)',
    text: '#FFFFFF',
    classes:
      'bg-surface-secondary hover:bg-surface-tertiary active:bg-surface-quaternary text-white',
  },

  // High-stakes action (game-changing) - Orange
  warning: {
    bg: 'var(--color-warning)',
    hover: 'var(--color-warning-dark)',
    active: 'var(--color-warning-darker)',
    text: '#FFFFFF',
    classes:
      'bg-warning hover:bg-warning-dark active:bg-warning-darker text-white',
  },

  // Declare rank options - Amber
  declare: {
    bg: 'var(--color-accent)',
    hover: 'var(--color-accent-dark)',
    active: 'var(--color-accent-darker)',
    text: '#FFFFFF',
    classes:
      'bg-accent hover:bg-accent-dark active:bg-accent-darker text-white',
  },

  // Disabled state - Light Gray
  disabled: {
    bg: 'var(--color-muted)',
    text: 'var(--color-muted-foreground)',
    opacity: 0.6,
    classes: 'bg-muted text-muted-foreground cursor-not-allowed',
  },
} as const;

export type ButtonVariant = keyof typeof BUTTON_COLORS;

// Button action mappings
export const BUTTON_ACTION_VARIANTS = {
  // Primary actions (green)
  'draw-card': 'primary',
  'use-action': 'primary',
  continue: 'primary',

  // Secondary actions (blue)
  swap: 'secondary',
  'start-game': 'secondary',
  'continue-toss': 'secondary',

  // Neutral actions (dark slate)
  discard: 'neutral',
  'discard-instead': 'neutral',
  skip: 'neutral',
  'skip-declaration': 'neutral',
  cancel: 'neutral',
  reset: 'neutral',

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
