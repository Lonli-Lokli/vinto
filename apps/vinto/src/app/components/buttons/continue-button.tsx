import { ChevronRight } from 'lucide-react';
import { Button } from './button-base';

export const ContinueButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Continue',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="continue-toss"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={`px-3 whitespace-nowrap ${
      fullWidth ? '' : 'flex-shrink-0'
    } ${className}`}
  >
    <span className="flex items-center gap-1">
      {children}
      <ChevronRight size={14} />
    </span>
  </Button>
);
