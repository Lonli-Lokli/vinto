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
          ? 'border-emerald-500 hover:bg-emerald-50 cursor-pointer hover:shadow-md hover:scale-105'
          : 'border-gray-200 opacity-40 cursor-not-allowed bg-gray-50'
      }
      ${className}
    `}
  >
    {children}
  </button>
);
