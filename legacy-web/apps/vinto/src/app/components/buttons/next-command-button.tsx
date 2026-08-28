export const NextCommandButton = ({
  onClick,
  disabled = false,
  isExecuting = false,
  hasNext = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  isExecuting?: boolean;
  hasNext?: boolean;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`flex-1 px-6 py-3 rounded-lg font-bold text-lg transition-all ${
      hasNext && !isExecuting
        ? 'bg-success hover:bg-success-dark text-white shadow-lg hover:shadow-xl'
        : 'bg-muted text-muted-foreground cursor-not-allowed'
    } ${className}`}
  >
    {isExecuting ? 'Executing...' : hasNext ? 'Next →' : 'Finished'}
  </button>
);
