import { Button } from './button-base';

export const CancelButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Cancel',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="cancel"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);
