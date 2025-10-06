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
