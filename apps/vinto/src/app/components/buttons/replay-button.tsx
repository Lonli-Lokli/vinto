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
    className={`px-2 py-1 rounded bg-green-100 hover:bg-green-200 text-green-700 font-semibold text-xs transition-colors ${className}`}
    title="Load game in replay mode"
  >
    {children}
  </button>
);
