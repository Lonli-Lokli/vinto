export const ExitReplayButton = ({
  onClick,
  className = '',
}: {
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 bg-error hover:bg-error-dark text-white text-xs rounded transition-colors ${className}`}
  >
    Exit
  </button>
);
