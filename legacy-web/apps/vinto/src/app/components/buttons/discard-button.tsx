import { Trash2 } from 'lucide-react';
import { Button } from './button-base';

export const DiscardButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Discard',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="discard"
    icon={<Trash2 size={14} />}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);
