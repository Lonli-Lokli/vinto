import { Zap } from 'lucide-react';
import { Button } from './button-base';

export const UseActionButton = ({
  onClick,
  disabled = false,
  className = '',
  children = 'Use',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="use-action"
    icon={<Zap size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    {children}
  </Button>
);
