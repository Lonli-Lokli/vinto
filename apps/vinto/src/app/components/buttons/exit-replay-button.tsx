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
