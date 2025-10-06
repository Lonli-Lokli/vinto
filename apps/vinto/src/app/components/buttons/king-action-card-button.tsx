import { getButtonClasses } from '@/app/constants/button-colors';

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
