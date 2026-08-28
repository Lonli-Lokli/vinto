import { Button } from './button-base';

export const CancelButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  autoFocus = false,
  children = 'Cancel',
  'data-testid': dataTestId,
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  autoFocus?: boolean;
  children?: React.ReactNode;
  'data-testid'?: string;
}) => (
  <Button
    variant="cancel"
    autoFocus={autoFocus}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
    data-testid={dataTestId}
  >
    {children}
  </Button>
);
