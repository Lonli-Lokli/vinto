import { Repeat } from 'lucide-react';
import { Button } from './button-base';

export const SwapButton = ({
  onClick,
  disabled = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <Button
    variant="swap"
    icon={<Repeat size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
  >
    Swap Cards
  </Button>
);
