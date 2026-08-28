import { Button } from './button-base';

export const DiscardInsteadButton = ({
  onClick,
  disabled = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <Button
    variant="discard-instead"
    onClick={onClick}
    disabled={disabled}
    className={`px-3 whitespace-nowrap flex-shrink-0 ${className}`}
  >
    Discard Instead
  </Button>
);
