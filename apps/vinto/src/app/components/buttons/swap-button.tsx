import { Repeat } from 'lucide-react';
import { Button } from './button-base';

export const SwapButton = ({
  onClick,
  disabled = false,
  className = '',
  children = 'Swap',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="swap"
    icon={<Repeat size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    {children}
  </Button>
);
