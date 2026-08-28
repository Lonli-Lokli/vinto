import { Button } from './button-base';

export const SkipDeclarationButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: React.ReactNode;
}) => (
  <Button
    variant="skip-declaration"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={`w-full bg-surface-secondary hover:bg-surface-tertiary border border-primary rounded text-xs font-medium text-secondary transition-colors flex-shrink-0 ${className}`}
  >
    Just Swap (No Declaration)
  </Button>
);
