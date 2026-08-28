export const ReloadButton = ({
  onClick,
  children = 'Reload Page',
  className = '',
}: {
  onClick: () => void;
  children?: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`w-full bg-success hover:bg-success-dark text-white font-medium py-2 px-4 rounded-lg flex items-center justify-center gap-2 transition-colors ${className}`}
  >
    {children}
  </button>
);
