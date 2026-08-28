export const DeckCardSelectButton = ({
  onClick,
  disabled = false,
  children,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
  className?: string;
}) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={`
      relative flex flex-col items-center gap-2 p-3 rounded-lg border-2 transition-all
      ${
        !disabled
          ? 'border-success hover:bg-success-light cursor-pointer hover:shadow-md hover:scale-105'
          : 'border-primary opacity-40 cursor-not-allowed bg-surface-primary'
      }
      ${className}
    `}
  >
    {children}
  </button>
);
