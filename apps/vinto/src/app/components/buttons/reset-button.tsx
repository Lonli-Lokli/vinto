import { RotateCcw } from 'lucide-react';
import { Button } from './button-base';

export const ResetButton = ({
  onClick,
  disabled = false,
  className = '',
  children = 'Reset',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="reset"
    icon={<RotateCcw size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    {children}
  </Button>
);
