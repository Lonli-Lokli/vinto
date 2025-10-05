// components/ActionTargetSelector.tsx
'use client';

import React, { FC } from 'react';
import { observer } from 'mobx-react-lite';
import { OwnCardPeek } from './action-types/own-card-peek';
import { OpponentCardPeek } from './action-types/opponent-card-peek';
import { CardSwap } from './action-types/card-swap';
import { QueenAction } from './action-types/queen-action';
import { KingDeclaration } from './action-types/king-declaration';
import { AceAction } from './action-types/ace-action';
import { NeverError } from '../shapes';
import {
  useActionStore,
  useGamePhaseStore,
  usePlayerStore,
} from './di-provider';
import { TargetType } from '../stores';

/**
 * ActionTargetSelector: Renders action-specific controls during action execution.
 * Each action component is now self-contained with its own instructions,
 * progress tracking, and action buttons (swap, skip, continue, cancel).
 */
export const ActionTargetSelector = observer(() => {
  const gamePhaseStore = useGamePhaseStore();
  const actionStore = useActionStore();
  const playerStore = usePlayerStore();

  if (!gamePhaseStore.isAwaitingActionTarget || !actionStore.actionContext) {
    return null;
  }

  const { playerId, targetType } = actionStore.actionContext;
  const actionPlayer = playerStore.players.find((p) => p.id === playerId);
  const humanPlayer = playerStore.humanPlayer;

  // Only show for human players - bot actions should not display UI
  if (!actionPlayer || !humanPlayer || actionPlayer.isBot) {
    return null;
  }

  return (
    <div className="w-full h-full">
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
