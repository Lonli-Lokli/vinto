import { Button } from './button-base';

export const StartGameButton = ({
  onClick,
  disabled = false,
  fullWidth = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
}) => (
  <Button
    variant="start-game"
    onClick={onClick}
    disabled={disabled}
    fullWidth={fullWidth}
    className={`py-1.5 px-3 ${className}`}
  >
    Start Game
  </Button>
);
