import { Zap } from 'lucide-react';
import { getButtonClasses } from '../../constants/button-colors';

export const PlayDiscardButton = ({
  onClick,
  disabled = false,
  className = '',
  title = '',
  text,
  subtitle,
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  title?: string;
  text: string;
  subtitle?: string | null;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    title={title}
    className={`${getButtonClasses(
      'use-action',
      disabled
    )} flex flex-col items-center justify-center py-1.5 px-2 text-xs min-h-[36px] ${className}`}
  >
    <div className="flex items-center gap-1">
      <Zap size={14} />
      <span>{text}</span>
    </div>
    {subtitle && (
      <div className="text-xs opacity-75 mt-0.5">{subtitle}</div>
    )}
  </button>
);
