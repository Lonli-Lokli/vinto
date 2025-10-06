import { Sparkles } from 'lucide-react';
import { Button } from './button-base';

export const DrawCardButton = ({
  onClick,
  disabled = false,
  className = '',
}: {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <Button
    variant="draw-card"
    icon={<Sparkles size={14} />}
    onClick={onClick}
    disabled={disabled}
    className={className}
    title={disabled ? 'Deck is empty' : 'Draw a new card from deck'}
  >
    Draw Card
  </Button>
);
