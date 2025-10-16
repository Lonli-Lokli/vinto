// components/DeckManagerPopover.tsx
'use client';

import React, { useRef, useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { Card as CardComponent } from './presentational';
import { ClosePopoverButton, DeckCardSelectButton } from './buttons';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';
import { Card, Rank } from '@/shared';

const ALL_RANKS: Rank[] = [
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  '10',
  'J',
  'Q',
  'K',
  'A',
];

export const DeckManagerPopover = observer(
  ({
    isOpen,
    onClose,
    buttonRef,
  }: {
    isOpen: boolean;
    onClose: () => void;
    buttonRef: React.RefObject<HTMLButtonElement | null>;
  }) => {
    const gameClient = useGameClient();
    const popoverRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
      if (!isOpen) return;

      const handleClickOutside = (event: MouseEvent) => {
        if (
          popoverRef.current &&
          !popoverRef.current.contains(event.target as Node) &&
          buttonRef.current &&
          !buttonRef.current.contains(event.target as Node)
        ) {
          onClose();
        }
      };

      document.addEventListener('mousedown', handleClickOutside);
      return () =>
        document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen, onClose, buttonRef]);

    if (!isOpen) return null;

    const handleSetNextCard = (rank: Rank) => {
      // Dispatch SET_NEXT_DRAW_CARD action
      gameClient.dispatch(GameActions.setNextDrawCard(rank));
      onClose();
    };

    // Group available cards by rank
    const availableCards: Partial<Record<Rank, Card[]>> = {};
    for (const card of gameClient.state.drawPile) {
      const existingCards = availableCards[card.rank];
      if (existingCards) {
        existingCards.push(card);
      } else {
        availableCards[card.rank] = [card];
      }
    }

    const topCard = gameClient.state.drawPile.peekTop();

    return (
      <div
        ref={popoverRef}
        className="absolute top-full right-0 mt-2 bg-surface-primary rounded-lg shadow-xl border border-primary p-4 z-[60] w-[480px] max-w-[90vw]"
        style={{ bottom: 'auto' }}
      >
        <div className="space-y-3">
          <div className="flex items-center justify-between border-b border-primary pb-2">
            <h3 className="text-sm font-bold text-primary">
              Set Next Draw Card
            </h3>
            <ClosePopoverButton onClick={onClose} />
          </div>

          <div className="text-xs text-secondary">
            Select a card to place it at the top of the draw pile
          </div>

          {/* Card Grid */}
          <div className="grid grid-cols-4 gap-3 max-h-96 overflow-y-auto pr-1">
            {ALL_RANKS.map((rank) => {
              const cards = availableCards[rank] || [];
              const count = cards.length;
              const isAvailable = count > 0;

              return (
                <DeckCardSelectButton
                  key={rank}
                  onClick={() => handleSetNextCard(rank)}
                  disabled={!isAvailable}
                >
                  {/* Card preview */}
                  <div className="w-14 h-20">
                    {isAvailable ? (
                      <CardComponent
                        card={cards[0]}
                        revealed={true}
                        size="auto"
                        selectionState="default"
                      />
                    ) : (
                      <div className="w-full h-full bg-muted rounded flex items-center justify-center text-muted text-sm font-semibold">
                        {rank}
                      </div>
                    )}
                  </div>

                  {/* Count badge */}
                  {isAvailable && (
                    <div className="text-sm font-bold text-success">
                      ×{count}
                    </div>
                  )}
                </DeckCardSelectButton>
              );
            })}
          </div>

          {/* Current top card */}
          <div className="border-t border-primary pt-3 mt-2">
            <div className="text-xs text-secondary mb-2">
              Current next card:
            </div>
            {gameClient.state.drawPile.length > 0 && topCard ? (
              <div className="flex items-center gap-3">
                <div className="w-14 h-20">
                  <CardComponent
                    card={topCard}
                    revealed={true}
                    size="auto"
                    selectionState="default"
                  />
                </div>
                <div className="text-sm font-semibold text-primary">
                  {topCard.rank}
                  {topCard.actionText && (
                    <span className="text-xs text-success ml-1">
                      ({topCard.actionText})
                    </span>
                  )}
                </div>
              </div>
            ) : (
              <div className="text-sm text-muted">No cards in deck</div>
            )}
          </div>
        </div>
      </div>
    );
  }
);
