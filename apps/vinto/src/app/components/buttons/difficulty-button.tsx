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
        ? 'bg-success text-white'
        : 'bg-surface-secondary hover:bg-surface-tertiary text-primary'
    } ${className}`}
    title={`Difficulty: ${level}`}
  >
    {level}
  </button>
);
