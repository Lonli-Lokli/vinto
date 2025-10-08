export const ReplayButton = ({
  onClick,
  children = 'Replay',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 rounded bg-success-light hover:bg-success text-success font-semibold text-xs transition-colors ${className}`}
    title="Load game in replay mode"
  >
    {children}
  </button>
);
