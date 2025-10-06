export const ClosePopoverButton = ({
  onClick,
  className = '',
}: {
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`text-gray-400 hover:text-gray-600 text-lg leading-none ${className}`}
  >
    ×
  </button>
);
