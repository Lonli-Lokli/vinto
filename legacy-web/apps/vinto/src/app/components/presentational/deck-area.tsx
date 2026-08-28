// components/DeckArea.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card as CardType, Pile, Rank } from '@vinto/shapes';
import { DiscardPile } from './discard-pile';
import { DrawPile } from './draw-pile';
import { DrawnCard } from '../drawn-card';
import { TossInArea } from './toss-in-area';
import { useCardAnimationStore } from '../di-provider';

interface TossInQueueItem {
  playerId: string;
  playerName: string;
  rank: Rank;
}

interface DeckAreaProps {
  discardPile: Pile;
  pendingCard: CardType | null;
  tossInRanks: Rank[];
  tossInQueue: TossInQueueItem[];
  canDrawCard: boolean;
  onDrawCard: () => void;
  isMobile?: boolean;
  isSelectingActionTarget?: boolean;
}

export const DeckArea: React.FC<DeckAreaProps> = observer(
  ({
    discardPile,
    pendingCard,
    tossInRanks,
    tossInQueue,
    canDrawCard,
    onDrawCard,
    isMobile = false,
    isSelectingActionTarget = false,
  }) => {
    const animationStore = useCardAnimationStore();
    const cardSize = 'lg';
    const columnGap = isMobile ? 'gap-x-2' : 'gap-x-12';
    const rowGap = isMobile ? 'gap-y-2' : 'gap-y-3';
    // Reserve vertical space so the grid doesn't jump when lower-row content appears
    const rowMinHeight = isMobile ? 'min-h-20' : 'min-h-24';

    // Show drawn card whenever there's a pending card
    const isDrawnCardVisible = !!pendingCard;

    // Dim deck area when selecting action targets OR when blocking animations are active
    const hasBlockingAnimations = animationStore.hasBlockingAnimations;
    const shouldDimDeckArea = isSelectingActionTarget || hasBlockingAnimations;

    return (
      <div
        className={`grid grid-cols-2 grid-rows-[auto_1fr] ${columnGap} ${rowGap} pointer-events-auto ${
          shouldDimDeckArea ? 'area-dimmed' : ''
        }`}
      >
        {/* Row 1, Col 1: Draw Pile */}
        <div className="flex justify-center items-start">
          <DrawPile
            clickable={canDrawCard && !hasBlockingAnimations}
            onClick={onDrawCard}
            size={cardSize}
            isMobile={isMobile}
          />
        </div>

        {/* Row 1, Col 2: Discard Pile */}
        <div className="flex justify-center items-start">
          <DiscardPile pile={discardPile} size={cardSize} isMobile={isMobile} />
        </div>

        {/* Row 2, Col 1: Drawn Card - Directly under Draw Pile */}
        <div className={`flex justify-center items-start ${rowMinHeight}`}>
          <DrawnCard
            rank={pendingCard?.rank}
            isVisible={isDrawnCardVisible}
            size={cardSize}
            isMobile={isMobile}
          />
        </div>

        {/* Row 2, Col 2: Toss-In Area - Under Discard Pile - Informational only */}
        <div className={`flex justify-center items-start ${rowMinHeight}`}>
          <TossInArea
            tossInRanks={tossInRanks}
            tossInQueue={tossInQueue}
            isMobile={isMobile}
          />
        </div>
      </div>
    );
  }
);
