import { Trophy } from 'lucide-react';
import { Button } from './button-base';

export const CallVintoButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Call Vinto',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="call-vinto"
    icon={<Trophy size={14} />}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
    data-testid="call-vinto"
  >
    {children}
  </Button>
);
