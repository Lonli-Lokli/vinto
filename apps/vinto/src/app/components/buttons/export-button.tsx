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
    className={`px-2 py-1 rounded bg-blue-100 hover:bg-blue-200 text-blue-700 font-semibold text-xs transition-colors ${className}`}
    title="Export command history"
  >
    {children}
  </button>
);
