export const ExportButton = ({
  onClick,
  children = 'Export',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`px-2 py-1 rounded bg-info-light hover:bg-info text-info font-semibold text-xs transition-colors ${className}`}
    title="Export command history"
  >
    {children}
  </button>
);
