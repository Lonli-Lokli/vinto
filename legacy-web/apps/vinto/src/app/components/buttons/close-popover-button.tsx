export const ClosePopoverButton = ({
  onClick,
  className = '',
}: {
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`text-muted hover:text-secondary text-lg leading-none ${className}`}
  >
    ×
  </button>
);
