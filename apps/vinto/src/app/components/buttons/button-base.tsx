/**
 * Button Design System
 *
 * Classification:
 * 1. Primary Actions: draw-card, use-action, call-vinto
 * 2. Secondary Actions: swap, continue-toss, start-game
 * 3. Destructive/Warning: discard, reset, cancel
 * 4. Action-specific: skip, king-declaration
 */

import React from 'react';
import { ButtonAction, getButtonClasses } from '../../constants/button-colors';

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
