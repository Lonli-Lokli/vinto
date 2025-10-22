// components/DeckManagerPopover.tsx
'use client';

import React, { useRef, useEffect, useState } from 'react';
import { observer } from 'mobx-react-lite';
import { Card as CardComponent } from './presentational';
import { ClosePopoverButton, DeckCardSelectButton } from './buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { ALL_RANKS, Card, Rank } from '@vinto/shapes';

type DeckManagerMode = 'set-next-card' | 'swap-hand-card';

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
    const [mode, setMode] = useState<DeckManagerMode>('set-next-card');
    const [selectedHandPosition, setSelectedHandPosition] = useState<
      number | null
    >(null);

    useEffect(() => {
      if (!isOpen) {
        // Reset state when closing
        setMode('set-next-card');
        setSelectedHandPosition(null);
        return;
      }

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

    const handleSwapCard = (deckCardRank: Rank) => {
      if (selectedHandPosition === null) return;

      const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
      if (!humanPlayer) return;

      // Dispatch SWAP_HAND_WITH_DECK action
      gameClient.dispatch(
        GameActions.swapHandWithDeck(
          humanPlayer.id,
          selectedHandPosition,
          deckCardRank
        )
      );
      setSelectedHandPosition(null);
      onClose();
    };

    // Group available cards by rank
    const availableCards: Partial<Record<Rank, Card[]>> = {};
    for (const card of gameClient.visualState.drawPile) {
      const existingCards = availableCards[card.rank];
      if (existingCards) {
        existingCards.push(card);
      } else {
        availableCards[card.rank] = [card];
      }
    }

    const topCard = gameClient.visualState.drawPile.peekTop();
    const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

    return (
      <div
        ref={popoverRef}
        className="absolute top-full right-0 mt-2 bg-surface-primary rounded-lg shadow-xl border border-primary p-4 z-[60] w-[480px] max-w-[90vw]"
        style={{ bottom: 'auto' }}
      >
        <div className="space-y-3">
          <div className="flex items-center justify-between border-b border-primary pb-2">
            <h3 className="text-sm font-bold text-primary">
              {mode === 'set-next-card'
                ? 'Set Next Draw Card'
                : 'Swap Hand Card'}
            </h3>
            <ClosePopoverButton onClick={onClose} />
          </div>

          {/* Mode Tabs */}
          <div className="flex gap-2">
            <button
              onClick={() => {
                setMode('set-next-card');
                setSelectedHandPosition(null);
              }}
              className={`flex-1 px-3 py-2 text-xs font-semibold rounded ${
                mode === 'set-next-card'
                  ? 'bg-primary text-white'
                  : 'bg-muted text-secondary hover:bg-hover'
              }`}
            >
              Set Next Card
            </button>
            <button
              onClick={() => {
                setMode('swap-hand-card');
                setSelectedHandPosition(null);
              }}
              className={`flex-1 px-3 py-2 text-xs font-semibold rounded ${
                mode === 'swap-hand-card'
                  ? 'bg-primary text-white'
                  : 'bg-muted text-secondary hover:bg-hover'
              }`}
            >
              Swap with Hand
            </button>
          </div>

          {mode === 'set-next-card' && (
            <div className="text-xs text-secondary">
              Select a card to place it at the top of the draw pile
            </div>
          )}

          {mode === 'swap-hand-card' && (
            <>
              <div className="text-xs text-secondary">
                {selectedHandPosition === null
                  ? 'Select a card from your hand to swap'
                  : 'Select a card from the deck to swap with'}
              </div>

              {/* Hand Cards */}
              {selectedHandPosition === null && humanPlayer && (
                <div className="border border-primary rounded p-3">
                  <div className="text-xs font-semibold text-primary mb-2">
                    Your Hand:
                  </div>
                  <div className="grid grid-cols-4 gap-2">
                    {humanPlayer.cards.map((card, index) => (
                      <button
                        key={index}
                        onClick={() => setSelectedHandPosition(index)}
                        className="p-2 border-2 border-transparent hover:border-primary rounded bg-surface-secondary"
                      >
                        <div className="w-14 h-20">
                          <CardComponent
                            card={card}
                            revealed={true}
                            size="auto"
                            selectionState="default"
                          />
                        </div>
                        <div className="text-xs text-center mt-1 font-semibold">
                          Pos {index}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}

          {/* Card Grid - Show only when appropriate */}
          {(mode === 'set-next-card' ||
            (mode === 'swap-hand-card' && selectedHandPosition !== null)) && (
            <div className="grid grid-cols-4 gap-3 max-h-96 overflow-y-auto pr-1">
              {ALL_RANKS.map((rank) => {
                const cards = availableCards[rank] || [];
                const count = cards.length;
                const isAvailable = count > 0;

                return (
                  <DeckCardSelectButton
                    key={rank}
                    onClick={() => {
                      if (mode === 'set-next-card') {
                        handleSetNextCard(rank);
                      } else {
                        handleSwapCard(rank);
                      }
                    }}
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
                        <div className="w-full h-full bg-muted rounded flex items-center justify-center text-muted text-sm font-semibold cursor-not-allowed">
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
          )}

          {/* Current state display */}
          {mode === 'set-next-card' && (
            <div className="border-t border-primary pt-3 mt-2">
              <div className="text-xs text-secondary mb-2">
                Current next card:
              </div>
              {gameClient.visualState.drawPile.length > 0 && topCard ? (
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
          )}

          {mode === 'swap-hand-card' &&
            selectedHandPosition !== null &&
            humanPlayer && (
              <div className="border-t border-primary pt-3 mt-2">
                <div className="text-xs text-secondary mb-2">
                  Swapping from position {selectedHandPosition}:
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-14 h-20">
                    <CardComponent
                      card={humanPlayer.cards[selectedHandPosition]}
                      revealed={true}
                      size="auto"
                      selectionState="default"
                    />
                  </div>
                  <div className="text-sm font-semibold text-primary">
                    {humanPlayer.cards[selectedHandPosition].rank}
                    {humanPlayer.cards[selectedHandPosition].actionText && (
                      <span className="text-xs text-success ml-1">
                        ({humanPlayer.cards[selectedHandPosition].actionText})
                      </span>
                    )}
                  </div>
                  <button
                    onClick={() => setSelectedHandPosition(null)}
                    className="ml-auto px-2 py-1 text-xs bg-muted hover:bg-hover rounded"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
        </div>
      </div>
    );
  }
);
