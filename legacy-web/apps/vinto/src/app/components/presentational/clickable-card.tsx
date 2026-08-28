'use client';

import { Card, CardProps } from './card';

interface ClickableCardProps extends CardProps {
  onClick: () => void;
}

export function ClickableCard({
  onClick,
  selectionState,
  ...cardProps
}: ClickableCardProps) {
  const handleClick = () => {
    if (selectionState === 'selectable' && onClick) {
      onClick();
    }
  };

  // Extract data-testid from cardProps if present
  const { 'data-testid': dataTestId, ...restCardProps } = cardProps as any;

  return (
    <div onClick={handleClick} data-testid={dataTestId}>
      <Card selectionState={selectionState} {...restCardProps} />
    </div>
  );
}
