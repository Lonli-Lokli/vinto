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
        ? 'bg-gray-400 text-gray-600 cursor-not-allowed'
        : 'bg-blue-600 hover:bg-blue-700 text-white hover:shadow-xl'
    } ${className}`}
  >
    {children}
  </button>
);
