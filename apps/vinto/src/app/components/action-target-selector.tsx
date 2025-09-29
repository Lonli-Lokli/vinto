// components/ActionTargetSelector.tsx
'use client';

import React, { FC } from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { OwnCardPeek } from './action-types/own-card-peek';
import { OpponentCardPeek } from './action-types/opponent-card-peek';
import { CardSwap } from './action-types/card-swap';
import { QueenAction } from './action-types/queen-action';
import { KingDeclaration } from './action-types/king-declaration';
import { AceAction } from './action-types/ace-action';
import { NeverError } from '../shapes';
import { TargetType } from '../stores/action-store';

export const ActionTargetSelector = observer(() => {
  if (!gameStore.isAwaitingActionTarget || !gameStore.actionContext) {
    return null;
  }

  const { playerId, targetType } = gameStore.actionContext;
  const actionPlayer = gameStore.players.find((p) => p.id === playerId);
  const humanPlayer = gameStore.players.find((p) => p.isHuman);

  if (!actionPlayer || !humanPlayer) {
    return null;
  }

  return (
    <div className="max-w-lg mx-auto px-3">
      <ActionContent targetType={targetType} />
    </div>
  );
});

const ActionContent: FC<{ targetType: TargetType | undefined }> = ({
  targetType,
}) => {
  switch (targetType) {
    case 'own-card':
      return <OwnCardPeek />;
    case 'opponent-card':
      return <OpponentCardPeek />;
    case 'swap-cards':
      return <CardSwap />;
    case 'peek-then-swap':
      return <QueenAction />;
    case 'force-draw':
      return <AceAction />;
    case 'declare-action':
      return <KingDeclaration />;
    case undefined:
      console.warn('ActionTargetSelector: targetType is undefined');
      return null;
    default:
      throw new NeverError(targetType);
  }
};
