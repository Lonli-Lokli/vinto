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

  return (
    <div onClick={handleClick}>
      <Card selectionState={selectionState} {...cardProps} />
    </div>
  );
}
