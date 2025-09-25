// stores/game-store.ts
'use client';

import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { enableMapSet } from 'immer';
import { shuffleDeck, createDeck, getAIKnowledgeByDifficulty } from '../lib/game-helpers';
import { OracleVintoClient } from '../lib/oracle-client';
import { GameToastService } from '../lib/toast-service';
import { GameStore, Difficulty, TossInTime,  Card } from '../shapes';

// Enable Immer MapSet plugin for Set objects
enableMapSet();

const oracleClient = new OracleVintoClient();

export const useGameStore = create<GameStore>()(
  devtools(
    immer((set, get) => ({
      // Game State
      players: [],
      currentPlayerIndex: 0,
      drawPile: [],
      discardPile: [],
      phase: 'setup',
      gameId: '',
      roundNumber: 1,
  turnCount: 0,
      finalTurnTriggered: false,
      
      // UI State
      oracle: oracleClient,
      aiThinking: false,
      currentMove: null,
      sessionActive: false,
      pendingCard: null,
      isSelectingSwapPosition: false,
      isChoosingCardAction: false,
      isAwaitingActionTarget: false,
      actionContext: null,
      selectedSwapPosition: null,
      swapTargets: [],
      setupPeeksRemaining: 2,
      waitingForTossIn: false,
      tossInTimer: 0,
      tossInTimeConfig: 7,
      difficulty: 'moderate',

      // Actions
      initGame: async () => {
        try {
          const gameId = await oracleClient.createGameSession('human');
          const deck = shuffleDeck(createDeck());
          const aiKnowledge = getAIKnowledgeByDifficulty('moderate'); // Default difficulty

          set((state) => {
            state.players = [
              {
                id: 'human',
                name: 'You',
                cards: deck.splice(0, 5),
                knownCardPositions: new Set(), // Start with no known cards
                isHuman: true,
                position: 'bottom',
                avatar: '👤',
                coalitionWith: new Set()
              },
              {
                id: 'oracle-alpha',
                name: 'Player α',
                cards: deck.splice(0, 5),
                knownCardPositions: new Set(),
                isHuman: false,
                position: 'left',
                avatar: '🤖',
                coalitionWith: new Set()
              },
              {
                id: 'oracle-beta',
                name: 'Player β',
                cards: deck.splice(0, 5),
                knownCardPositions: new Set(),
                isHuman: false,
                position: 'top',
                avatar: '🎯',
                coalitionWith: new Set()
              },
              {
                id: 'oracle-gamma',
                name: 'Player γ',
                cards: deck.splice(0, 5),
                knownCardPositions: new Set(),
                isHuman: false,
                position: 'right',
                avatar: '⚡',
                coalitionWith: new Set()
              }
            ];

            state.drawPile = deck;
            state.discardPile = [];
            state.phase = 'setup'; // Start in setup phase for initial card memorization
            state.gameId = gameId;
            state.currentPlayerIndex = 0;
            state.sessionActive = true;

            // All players start with no known cards - they must memorize during setup
            // This matches official rules: peek at 2 cards once, memorize, then hide
            state.players.forEach(p => {
              if (!p.isHuman) {
                const knowledge = new Set<number>(
                  Array.from({ length: p.cards.length }, (_, i) => i).filter(
                    () => Math.random() < aiKnowledge
                  )
                );
                p.knownCardPositions = knowledge;
              }
            });
          });

        } catch (error) {
          GameToastService.error('Failed to start game', 'Please try again');
        }
      },

      updateDifficulty: (newDifficulty: Difficulty) => set((state) => {
        state.difficulty = newDifficulty;

        const aiKnowledge = getAIKnowledgeByDifficulty(newDifficulty);

        // Update AI knowledge based on new difficulty
        state.players.forEach((player) => {
          if (!player.isHuman) {
            // Get current known positions
            const currentKnown = Array.from(player.knownCardPositions);

            // Generate new knowledge set based on difficulty
            const newKnownSet = new Set(
              Array.from({ length: player.cards.length }, (_, i) => i)
                .filter(() => Math.random() < aiKnowledge)
            );

            // Keep some previously known cards for consistency (70% chance)
            currentKnown.forEach(pos => {
              if (pos < player.cards.length && Math.random() < 0.7) {
                newKnownSet.add(pos);
              }
            });

            player.knownCardPositions = newKnownSet;
          }
        });

        GameToastService.difficultyChanged(newDifficulty);
      }),

      updateTossInTime: (newTossInTime: TossInTime) => set((state) => {
        state.tossInTimeConfig = newTossInTime;
        GameToastService.info(`Toss-in time set to ${newTossInTime} seconds`);
      }),

      peekCard: (playerId: string, position: number) => set((state) => {
        const player = state.players.find(p => p.id === playerId);
        if (player && position >= 0 && position < player.cards.length) {
          player.knownCardPositions.add(position);

          if (player.isHuman && state.phase === 'setup') {
            state.setupPeeksRemaining--;
            GameToastService.cardPeeked(position);
          }
        }
      }),

      finishSetup: () => set((state) => {
        // AI players automatically "memorize" some cards based on difficulty
        const aiKnowledge = getAIKnowledgeByDifficulty('moderate');

        state.players.forEach(player => {
          if (!player.isHuman) {
            // Reset and give AI knowledge based on current hand size
            player.knownCardPositions.clear();
            Array.from({ length: player.cards.length }, (_, i) => i)
              .filter(() => Math.random() < aiKnowledge)
              .forEach(pos => player.knownCardPositions.add(pos));
          }
        });

        // Move to playing phase and start first discard
        state.phase = 'playing';
      }),

      drawCard: () => set((state) => {
        if (state.drawPile.length === 0) return;

        const drawnCard = state.drawPile[0];
        state.pendingCard = drawnCard;
        state.drawPile = state.drawPile.slice(1);

        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer && currentPlayer.isHuman) {
          state.isChoosingCardAction = true;
        }
      }),

      takeFromDiscard: () => set((state) => {
        if (state.discardPile.length === 0) return;

        const topCard = state.discardPile[0];
        // Can only take action cards (7-K) whose action hasn't been used
        if (topCard.played) return;

        state.pendingCard = topCard;
        state.discardPile = state.discardPile.slice(1);

        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer && currentPlayer.isHuman) {
          // For taking from discard, we use the action immediately, no swapping
          // This matches the rules: "immediately play its action"
          state.pendingCard = null;
          // Move to next player
          state.turnCount++;
          state.currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.length;
        }
      }),

      // Choose to swap the drawn card with an existing card
      chooseSwap: () => set((state) => {
        state.isChoosingCardAction = false;
        state.isSelectingSwapPosition = true;
      }),

      // Choose to play/discard the drawn card directly
      choosePlayCard: () => set((state) => {
        const currentPlayer = state.players[state.currentPlayerIndex];
        const pendingCard = state.pendingCard;

        if (!currentPlayer || !pendingCard) return;

        // Directly discard the drawn card
        state.discardPile = [pendingCard, ...state.discardPile];

        if (pendingCard.action) {
          // Execute the card action
          get().executeCardAction(pendingCard, currentPlayer.id);
        } else {
          GameToastService.info(`Discarded ${pendingCard.rank}`);

          // Clean up and advance turn for non-action cards
          state.pendingCard = null;
          state.isChoosingCardAction = false;

          // Start toss-in waiting period
          state.waitingForTossIn = true;
          state.tossInTimer = state.tossInTimeConfig;

          // Advance turn after toss-in period
          setTimeout(() => {
            set((draft) => {
              draft.waitingForTossIn = false;
              draft.tossInTimer = 0;
              draft.turnCount++;
              draft.currentPlayerIndex = (draft.currentPlayerIndex + 1) % draft.players.length;

              // Scoring begins only after a called Vinto final round completes
              if (draft.finalTurnTriggered && draft.currentPlayerIndex === 0) {
                draft.phase = 'scoring';
              }
            });
          }, state.tossInTimeConfig * 1000);
        }
      }),

      swapCard: (position: number) => set((state) => {
        const currentPlayer = state.players[state.currentPlayerIndex];
        const pendingCard = state.pendingCard;

        if (!currentPlayer || !pendingCard) return;

        // Perform the swap immediately
        const discardedCard = currentPlayer.cards[position];
        currentPlayer.cards[position] = pendingCard;
        currentPlayer.knownCardPositions.add(position);
        state.discardPile = [discardedCard, ...state.discardPile];

        // Clean up
        state.pendingCard = null;
        state.isSelectingSwapPosition = false;

        // Start toss-in waiting period (configurable seconds for others to play matching cards)
        state.waitingForTossIn = true;
        state.tossInTimer = state.tossInTimeConfig;

        // Advance turn and check game phase transitions after toss-in period
        setTimeout(() => {
          set((draft) => {
            draft.waitingForTossIn = false;
            draft.tossInTimer = 0;
            draft.turnCount++;
            draft.currentPlayerIndex = (draft.currentPlayerIndex + 1) % draft.players.length;

            // Scoring begins only after a called Vinto final round completes
            if (draft.finalTurnTriggered && draft.currentPlayerIndex === 0) {
              draft.phase = 'scoring';
            }
          });
        }, state.tossInTimeConfig * 1000);
      }),

      // Execute card actions (7, 8, 9, 10, J, Q, K, A)
      executeCardAction: (card: Card, playerId: string) => set((state) => {
        if (!card.action) return;

        const player = state.players.find(p => p.id === playerId);
        if (!player) return;

        GameToastService.success(`${player.name} played ${card.rank} - ${card.action}`);

        // Handle different card actions
        if (card.rank === '7' || card.rank === '8') {
          // "Peek 1 of your cards"
          state.actionContext = {
            action: card.action,
            playerId: playerId,
            targetType: 'own-card'
          };
          state.isAwaitingActionTarget = true;
          state.pendingCard = null;
          state.isChoosingCardAction = false;
        } else if (card.rank === '9' || card.rank === '10') {
          // "Peek 1 opponent card"
          state.actionContext = {
            action: card.action,
            playerId: playerId,
            targetType: 'opponent-card'
          };
          state.isAwaitingActionTarget = true;
          state.pendingCard = null;
          state.isChoosingCardAction = false;
        } else if (card.rank === 'J') {
          // "Swap any two facedown cards on the table"
          state.actionContext = {
            action: card.action,
            playerId: playerId,
            targetType: 'swap-cards'
          };
          state.isAwaitingActionTarget = true;
          state.pendingCard = null;
          state.isChoosingCardAction = false;
        } else {
          // TODO: Implement other actions (Q, K, A)
          GameToastService.info(`${card.rank} action not yet implemented`);

          // For now, just clean up and advance turn
          state.pendingCard = null;
          state.isChoosingCardAction = false;

          // Start toss-in waiting period
          state.waitingForTossIn = true;
          state.tossInTimer = state.tossInTimeConfig;

          // Advance turn after toss-in period
          setTimeout(() => {
            set((draft) => {
              draft.waitingForTossIn = false;
              draft.tossInTimer = 0;
              draft.turnCount++;
              draft.currentPlayerIndex = (draft.currentPlayerIndex + 1) % draft.players.length;

              // Scoring begins only after a called Vinto final round completes
              if (draft.finalTurnTriggered && draft.currentPlayerIndex === 0) {
                draft.phase = 'scoring';
              }
            });
          }, state.tossInTimeConfig * 1000);
        }
      }),

      // Select target for card action (e.g., which card to peek)
      selectActionTarget: (targetPlayerId: string, position: number) => set((state) => {
        if (!state.isAwaitingActionTarget || !state.actionContext) return;

        const { playerId, targetType } = state.actionContext;
        const actionPlayer = state.players.find(p => p.id === playerId);
        const targetPlayer = state.players.find(p => p.id === targetPlayerId);

        if (!actionPlayer || !targetPlayer) return;

        // Handle "Peek 1 of your cards" (7 and 8)
        if (targetType === 'own-card' && targetPlayerId === playerId) {
          if (position >= 0 && position < targetPlayer.cards.length) {
            const peekedCard = targetPlayer.cards[position];
            targetPlayer.knownCardPositions.add(position);

            GameToastService.success(
              `${actionPlayer.name} peeked at position ${position + 1}: ${peekedCard.rank} (value ${peekedCard.value})`
            );
          }
        }

        // Handle "Peek 1 opponent card" (9 and 10)
        if (targetType === 'opponent-card' && targetPlayerId !== playerId) {
          if (position >= 0 && position < targetPlayer.cards.length) {
            const peekedCard = targetPlayer.cards[position];

            // The action player learns about the opponent's card, but we don't track this
            // in knownCardPositions since that's for their own cards only

            GameToastService.success(
              `${actionPlayer.name} peeked at ${targetPlayer.name}'s position ${position + 1}: ${peekedCard.rank} (value ${peekedCard.value})`
            );
          }
        }

        // Handle "Swap any two facedown cards on the table" (J)
        if (targetType === 'swap-cards') {
          // Check if this card is already selected (to allow deselection)
          const existingIndex = state.swapTargets.findIndex(
            target => target.playerId === targetPlayerId && target.position === position
          );

          if (existingIndex !== -1) {
            // Card is already selected, remove it (deselect)
            state.swapTargets.splice(existingIndex, 1);
            GameToastService.info(`${actionPlayer.name} deselected ${targetPlayer.name}'s card ${position + 1}`);
            return; // Don't continue with swap logic
          }

          // Don't allow more than 2 selections
          if (state.swapTargets.length >= 2) {
            GameToastService.info('Already have 2 cards selected. Deselect one to choose a different card.');
            return;
          }

          // Add this target to the swap targets list
          const newTarget = { playerId: targetPlayerId, position };
          state.swapTargets.push(newTarget);

          // If we have both targets, perform the swap
          if (state.swapTargets.length === 2) {
            const [target1, target2] = state.swapTargets;
            const player1 = state.players.find(p => p.id === target1.playerId);
            const player2 = state.players.find(p => p.id === target2.playerId);

            if (player1 && player2 &&
                target1.position >= 0 && target1.position < player1.cards.length &&
                target2.position >= 0 && target2.position < player2.cards.length) {

              // Perform the swap
              const card1 = player1.cards[target1.position];
              const card2 = player2.cards[target2.position];

              player1.cards[target1.position] = card2;
              player2.cards[target2.position] = card1;

              GameToastService.success(
                `${actionPlayer.name} swapped ${player1.name}'s card ${target1.position + 1} with ${player2.name}'s card ${target2.position + 1}`
              );
            }

            // Clear swap targets after performing swap
            state.swapTargets = [];
          } else {
            // Still need one more target
            GameToastService.info(`${actionPlayer.name} selected first card. Choose second card to swap with.`);
            return; // Don't clean up yet, still need second target
          }
        }

        // Clean up and advance turn
        state.isAwaitingActionTarget = false;
        state.actionContext = null;
        state.swapTargets = [];

        // Start toss-in waiting period
        state.waitingForTossIn = true;
        state.tossInTimer = state.tossInTimeConfig;

        // Advance turn after toss-in period
        setTimeout(() => {
          set((draft) => {
            draft.waitingForTossIn = false;
            draft.tossInTimer = 0;
            draft.turnCount++;
            draft.currentPlayerIndex = (draft.currentPlayerIndex + 1) % draft.players.length;

            // Scoring begins only after a called Vinto final round completes
            if (draft.finalTurnTriggered && draft.currentPlayerIndex === 0) {
              draft.phase = 'scoring';
            }
          });
        }, state.tossInTimeConfig * 1000);
      }),

      // Cancel action selection
      cancelAction: () => set((state) => {
        state.isAwaitingActionTarget = false;
        state.actionContext = null;
        state.swapTargets = []; // Clear any partially selected swap targets

        // Advance turn without executing action
        state.turnCount++;
        state.currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.length;

        GameToastService.info('Action cancelled');
      }),

      tossInCard: (playerId: string, position: number) => set((state) => {
        if (!state.waitingForTossIn) return;

        const player = state.players.find(p => p.id === playerId);
        const topDiscard = state.discardPile[0];

        if (!player || !topDiscard) return;

  if (position < 0 || position >= player.cards.length) return;
  const tossedCard = player.cards[position];

        // Check if cards match rank (correct toss-in)
        if (tossedCard && tossedCard.rank === topDiscard.rank) {
          // Correct toss-in: remove card from hand and adjust known positions
          const updatedKnown = new Set<number>();
          player.knownCardPositions.forEach((idx) => {
            if (idx === position) return;
            updatedKnown.add(idx > position ? idx - 1 : idx);
          });
          player.knownCardPositions = updatedKnown;

          // Remove the card from the hand
          player.cards.splice(position, 1);

          // Place the card on the discard pile
          state.discardPile = [tossedCard, ...state.discardPile];

          // TODO: Perform the card's action
          GameToastService.success(`${player.name} tossed in ${tossedCard.rank}!`);
        } else {
          // Incorrect toss-in: penalty card
          if (state.drawPile.length > 0) {
            const penaltyCard = state.drawPile[0];
            player.cards.push(penaltyCard); // Add penalty card
            state.drawPile = state.drawPile.slice(1);

            GameToastService.error(`${player.name}'s toss-in failed - penalty card drawn`);
          }
        }
      }),

      makeAIMove: async (difficulty: string) => {
        const state = get();
        const currentPlayer = state.players[state.currentPlayerIndex];
        
        if (!currentPlayer || currentPlayer.isHuman) return;

        set((draft) => {
          draft.aiThinking = true;
          draft.currentMove = null;
        });

        try {
          const move = await oracleClient.requestAIMove(
            state, 
            currentPlayer.id, 
            difficulty as Difficulty
          );
          
          set((draft) => {
            draft.currentMove = move;
            draft.aiThinking = false;
            
            // Apply AI move logic
            if (draft.drawPile.length > 0) {
              const drawnCard = draft.drawPile[0];
              draft.drawPile = draft.drawPile.slice(1);
              
              // Find worst known card to potentially swap
              let worstPosition = 0;
              let worstValue = -10;
              
              currentPlayer.cards.forEach((card, index) => {
                if (currentPlayer.knownCardPositions.has(index) && card.value > worstValue) {
                  worstValue = card.value;
                  worstPosition = index;
                }
              });
              
              // Smart AI decision: swap if drawn card is better
              if (drawnCard.value < worstValue && worstValue > 3) {
                const discardedCard = currentPlayer.cards[worstPosition];
                currentPlayer.cards[worstPosition] = drawnCard;
                draft.discardPile = [discardedCard, ...draft.discardPile];
                currentPlayer.knownCardPositions.add(worstPosition);

                // Start toss-in waiting period (configurable seconds for others to play matching cards)
                draft.waitingForTossIn = true;
                draft.tossInTimer = draft.tossInTimeConfig;
              } else {
                // Just discard the drawn card
                draft.discardPile = [drawnCard, ...draft.discardPile];

                // Start toss-in waiting period (configurable seconds for others to play matching cards)
                draft.waitingForTossIn = true;
                draft.tossInTimer = draft.tossInTimeConfig;
              }
            }

            // Advance turn after toss-in period
            setTimeout(() => {
              set((draftTimeout) => {
                draftTimeout.waitingForTossIn = false;
                draftTimeout.tossInTimer = 0;
                draftTimeout.turnCount++;
                draftTimeout.currentPlayerIndex = (draftTimeout.currentPlayerIndex + 1) % draftTimeout.players.length;
                // Scoring begins only after a called Vinto final round completes
                if (draftTimeout.finalTurnTriggered && draftTimeout.currentPlayerIndex === 0) {
                  draftTimeout.phase = 'scoring';
                }
              });
            }, draft.tossInTimeConfig * 1000);
          });
        } catch {
          set((draft) => {
            draft.aiThinking = false;
            draft.turnCount++;
            draft.currentPlayerIndex = (draft.currentPlayerIndex + 1) % draft.players.length;
            // If we've completed final turn, move to scoring
            if (draft.finalTurnTriggered && draft.currentPlayerIndex === 0) {
              draft.phase = 'scoring';
            }
          });
        }
      },

      // Coalition management
      formCoalition: (playerId1: string, playerId2: string) => set((state) => {
        const player1 = state.players.find(p => p.id === playerId1);
        const player2 = state.players.find(p => p.id === playerId2);

        if (player1 && player2) {
          player1.coalitionWith.add(playerId2);
          player2.coalitionWith.add(playerId1);
        }
      }),

      breakCoalition: (playerId1: string, playerId2: string) => set((state) => {
        const player1 = state.players.find(p => p.id === playerId1);
        const player2 = state.players.find(p => p.id === playerId2);

        if (player1 && player2) {
          player1.coalitionWith.delete(playerId2);
          player2.coalitionWith.delete(playerId1);
        }
      }),

      // Game ending
      callVinto: () => set((state) => {
        state.finalTurnTriggered = true;
        state.phase = 'final';
      }),

      calculateFinalScores: () => {
        const state = get();
        const scores: { [playerId: string]: number } = {};

        state.players.forEach(player => {
          scores[player.id] = player.cards.reduce((total, card) => total + card.value, 0);
        });

        return scores;
      },

      // Cancel card swap and put card back in discard pile
      cancelSwap: () => set((state) => {
        if (state.pendingCard) {
          state.discardPile = [state.pendingCard, ...state.discardPile];
          state.pendingCard = null;
          state.isSelectingSwapPosition = false;
          state.isChoosingCardAction = false;
          state.isAwaitingActionTarget = false;
          state.actionContext = null;
          state.selectedSwapPosition = null;
          // Move to next player
          state.turnCount++;
          state.currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.length;
        }
      })
    }))
  )
);