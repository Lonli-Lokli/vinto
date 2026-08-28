export const LoadReplayButton = ({
  onClick,
  disabled = false,
  children = '📂 Load Replay',
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`px-4 py-2 rounded-lg font-semibold text-sm shadow-lg transition-all ${
      disabled
        ? 'bg-muted text-muted-foreground cursor-not-allowed'
        : 'bg-info hover:bg-info-dark text-white hover:shadow-xl'
    } ${className}`}
  >
    {children}
  </button>
);
