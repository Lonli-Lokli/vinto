import { getButtonClasses } from '../../constants/button-colors';

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
