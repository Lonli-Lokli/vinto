import { ArrowRight } from 'lucide-react';
import { Button } from './button-base';

export const SkipButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
  children = 'Skip',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="skip"
    icon={<ArrowRight size={14} />}
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={className}
  >
    {children}
  </Button>
);
