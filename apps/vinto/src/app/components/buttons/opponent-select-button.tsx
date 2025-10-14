import { Target } from 'lucide-react';
import { Avatar } from '../presentational';
import { getButtonClasses } from '../../constants/button-colors';

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
      ? 'border-2 border-info bg-info-light'
      : 'border-2 border-primary bg-transparent hover:bg-surface-primary';

    return (
      <button
        onClick={onClick}
        className={`flex flex-col items-center gap-1.5 p-2 rounded-lg ${borderClass} transition-all active:scale-95 ${className}`}
      >
        {/* Avatar Image - md size */}
        <div className="flex items-center justify-center">
          <Avatar playerName={player.name} size="md" />
        </div>
        {/* Player Name */}
        <div className="text-xs font-medium text-primary text-center line-clamp-1 w-full">
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
