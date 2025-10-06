// components/DeckManagerPopover.tsx
'use client';

import React, { useRef, useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { useDeckStore } from './di-provider';
import { Card as CardComponent } from './card';
import { Card, Rank } from '../shapes';
import { ClosePopoverButton, DeckCardSelectButton } from './buttons';

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
    const deckStore = useDeckStore();
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
      // Use the MobX action to modify the draw pile
      deckStore.setNextDrawCard(rank);
      onClose();
    };

    // Group available cards by rank
    const availableCards = deckStore.drawPile.reduce((acc, card) => {
      if (!acc[card.rank]) {
        acc[card.rank] = [];
      }
      acc[card.rank].push(card);
      return acc;
    }, {} as Record<Rank, Card[]>);

    return (
      <div
        ref={popoverRef}
        className="absolute top-full right-0 mt-2 bg-white rounded-lg shadow-xl border border-gray-200 p-4 z-50 w-[480px] max-w-[90vw]"
      >
        <div className="space-y-3">
          <div className="flex items-center justify-between border-b border-gray-200 pb-2">
            <h3 className="text-sm font-bold text-gray-800">
              Set Next Draw Card
            </h3>
            <ClosePopoverButton onClick={onClose} />
          </div>

          <div className="text-xs text-gray-600">
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
                      />
                    ) : (
                      <div className="w-full h-full bg-gray-200 rounded flex items-center justify-center text-gray-400 text-sm font-semibold">
                        {rank}
                      </div>
                    )}
                  </div>

                  {/* Count badge */}
                  {isAvailable && (
                    <div className="text-sm font-bold text-emerald-600">
                      ×{count}
                    </div>
                  )}
                </DeckCardSelectButton>
              );
            })}
          </div>

          {/* Current top card */}
          <div className="border-t border-gray-200 pt-3 mt-2">
            <div className="text-xs text-gray-600 mb-2">Current next card:</div>
            {deckStore.drawPile.length > 0 ? (
              <div className="flex items-center gap-3">
                <div className="w-14 h-20">
                  <CardComponent
                    card={deckStore.drawPile[0]}
                    revealed={true}
                    size="auto"
                  />
                </div>
                <div className="text-sm font-semibold text-gray-800">
                  {deckStore.drawPile[0].rank}
                  {deckStore.drawPile[0].action && (
                    <span className="text-xs text-emerald-600 ml-1">
                      ({deckStore.drawPile[0].action})
                    </span>
                  )}
                </div>
              </div>
            ) : (
              <div className="text-sm text-gray-400">No cards in deck</div>
            )}
          </div>
        </div>
      </div>
    );
  }
);
