import { Button } from './button-base';

export const CancelButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  autoFocus = false,
  children = 'Cancel',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  autoFocus?: boolean;
  children?: React.ReactNode;
}) => (
  <Button
    variant="cancel"
    autoFocus={autoFocus}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);
