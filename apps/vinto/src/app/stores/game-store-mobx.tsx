// stores/game-store-mobx.ts
'use client';

import { makeAutoObservable, runInAction, reaction } from 'mobx';
import {
  shuffleDeck,
  createDeck,
  getAIKnowledgeByDifficulty,
} from '../lib/game-helpers';
import { OracleVintoClient } from '../lib/oracle-client';
import { GameToastService } from '../lib/toast-service';
import {
  GameState,
  Difficulty,
  TossInTime,
  Card,
  Rank,
  Player,
  AIMove,
} from '../shapes';

export class GameStore implements GameState {
  // Game State Properties
  players: Player[] = [];
  currentPlayerIndex = 0;
  drawPile: Card[] = [];
  discardPile: Card[] = [];
  phase: 'setup' | 'playing' | 'final' | 'scoring' = 'setup';
  gameId = '';
  roundNumber = 1;
  turnCount = 0;
  finalTurnTriggered = false;

  // Extended Store Properties
  oracle: OracleVintoClient;
  aiThinking = false;
  currentMove: AIMove | null = null;
  sessionActive = false;
  pendingCard: Card | null = null;
  isSelectingSwapPosition = false;
  isChoosingCardAction = false;
  isAwaitingActionTarget = false;
  actionContext: {
    action: string;
    playerId: string;
    targetType?:
      | 'own-card'
      | 'opponent-card'
      | 'swap-cards'
      | 'peek-then-swap'
      | 'declare-action'
      | 'force-draw';
    declaredCard?: Rank;
  } | null = null;
  selectedSwapPosition: number | null = null;
  swapTargets: { playerId: string; position: number }[] = [];
  peekTargets: { playerId: string; position: number; card?: Card }[] = [];
  isDeclaringRank = false;
  swapPosition: number | null = null;
  setupPeeksRemaining = 2;
  waitingForTossIn = false;
  tossInTimer = 0;
  tossInTimeConfig: TossInTime = 7;
  difficulty: Difficulty = 'moderate';
  canCallVintoAfterHumanTurn = false;

  private tossInInterval: NodeJS.Timeout | null = null;
  private aiTurnReaction: (() => void) | null = null;

  constructor() {
    // Make this store observable
    makeAutoObservable(this);

    // Initialize Oracle client
    this.oracle = new OracleVintoClient();

    // Set up AI turn handling
    this.setupAITurnReaction();
  }

  // Set up MobX reaction to handle AI turns
  private setupAITurnReaction() {
    this.aiTurnReaction = reaction(
      () => ({
        currentPlayerIndex: this.currentPlayerIndex,
        currentPlayer: this.players[this.currentPlayerIndex],
        sessionActive: this.sessionActive,
      }),
      ({ currentPlayer }) => {

        // Update vinto call availability whenever turn state changes
        this.updateVintoCallAvailability();

        // Clear temporary card visibility on each turn change
        this.clearTemporaryCardVisibility();

        if (
          currentPlayer &&
          !currentPlayer.isHuman &&
          !this.aiThinking &&
          this.sessionActive
        ) {
          // Schedule AI move after delay
          setTimeout(() => {
            if (
              this.players[this.currentPlayerIndex] === currentPlayer &&
              !this.aiThinking &&
              this.sessionActive
            ) {
              this.makeAIMove(this.difficulty);
            }
          }, this.tossInTimeConfig * 1000);
        }
      }
    );
  }

  // Cleanup method
  dispose() {
    if (this.aiTurnReaction) {
      this.aiTurnReaction();
      this.aiTurnReaction = null;
    }
    if (this.tossInInterval) {
      clearInterval(this.tossInInterval);
      this.tossInInterval = null;
    }
  }

  // Helper method to update vinto call availability
  private updateVintoCallAvailability() {
    const humanPlayer = this.players.find((p) => p.isHuman);
    if (!humanPlayer) {
      this.canCallVintoAfterHumanTurn = false;
      return;
    }

    const humanPlayerIndex = this.players.indexOf(humanPlayer);
    const previousPlayerIndex =
      (this.currentPlayerIndex - 1 + this.players.length) % this.players.length;
    const currentPlayer = this.players[this.currentPlayerIndex];

    // Vinto can ONLY be called:
    // 1. After human player's turn (previousPlayerIndex === humanPlayerIndex)
    // 2. Current player must be a bot (not human)
    // 3. Bot must not be thinking yet (there's a delay window)
    // 4. Game must be in playing phase and not in final turn
    // 5. Not during any special states (waiting for toss in, selecting positions, etc.)
    this.canCallVintoAfterHumanTurn =
      this.phase === 'playing' &&
      !this.finalTurnTriggered &&
      !currentPlayer?.isHuman &&
      !this.aiThinking &&
      !this.waitingForTossIn &&
      !this.isSelectingSwapPosition &&
      !this.isChoosingCardAction &&
      !this.isAwaitingActionTarget &&
      !this.isDeclaringRank &&
      previousPlayerIndex === humanPlayerIndex;
  }

  // Helper method to clear temporary card visibility
  private clearTemporaryCardVisibility() {
    this.players.forEach((player) => {
      player.temporarilyVisibleCards.clear();
    });
  }

  // Action: Initialize game
  async initGame() {
    try {
      const deck = shuffleDeck(createDeck());

      runInAction(() => {
        this.players = [
          {
            id: 'human',
            name: 'Leornardo',
            cards: deck.splice(0, 5),
            knownCardPositions: new Set(),
            temporarilyVisibleCards: new Set(),
            isHuman: true,
            position: 'bottom',
            avatar: '👤',
            coalitionWith: new Set(),
          },
          {
            id: 'bot1',
            name: 'Michelangelo',
            cards: deck.splice(0, 5),
            knownCardPositions: new Set(),
            temporarilyVisibleCards: new Set(),
            isHuman: false,
            position: 'left',
            avatar: '🤖',
            coalitionWith: new Set(),
          },
          {
            id: 'bot2',
            name: 'Donatello',
            cards: deck.splice(0, 5),
            knownCardPositions: new Set(),
            temporarilyVisibleCards: new Set(),
            isHuman: false,
            position: 'top',
            avatar: '🤖',
            coalitionWith: new Set(),
          },
          {
            id: 'bot3',
            name: 'Raphael',
            cards: deck.splice(0, 5),
            knownCardPositions: new Set(),
            temporarilyVisibleCards: new Set(),
            isHuman: false,
            position: 'right',
            avatar: '🤖',
            coalitionWith: new Set(),
          },
        ];

        this.drawPile = deck;
        this.discardPile = [];
        this.phase = 'setup';
        this.gameId = `game-${Date.now()}`;
        this.roundNumber = 1;
        this.turnCount = 0;
        this.finalTurnTriggered = false;
        this.currentPlayerIndex = 0;
        this.setupPeeksRemaining = 2;
        this.sessionActive = true;

        // Give bots some initial knowledge based on difficulty
        this.players.forEach((player) => {
          if (!player.isHuman) {
            const aiKnowledge = getAIKnowledgeByDifficulty(this.difficulty);
            Array.from({ length: player.cards.length }, (_, i) => i)
              .filter(() => Math.random() < aiKnowledge)
              .forEach((pos) => player.knownCardPositions.add(pos));
          }
        });
      });

      GameToastService.success('New game started! Memorize 2 of your cards.');
    } catch (error) {
      console.error('Error initializing game:', error);
      GameToastService.error('Failed to start game. Please try again.');
    }
  }

  // Action: Update difficulty
  updateDifficulty(diff: Difficulty) {
    this.difficulty = diff;
  }

  // Action: Update toss-in time
  updateTossInTime(time: TossInTime) {
    this.tossInTimeConfig = time;
  }

  // Action: Peek at a card during setup
  peekCard(playerId: string, position: number) {
    const player = this.players.find((p) => p.id === playerId);
    if (!player || this.setupPeeksRemaining <= 0) return;

    if (!player.knownCardPositions.has(position)) {
      player.knownCardPositions.add(position);
      this.setupPeeksRemaining--;

      const card = player.cards[position];
      GameToastService.success(
        `Peeked at position ${position + 1}: ${card.rank} (value ${card.value})`
      );
    }
  }

  // Action: Finish setup phase
  finishSetup() {
    if (this.setupPeeksRemaining > 0) {
      GameToastService.warning(
        `You still have ${this.setupPeeksRemaining} peeks remaining!`
      );
      return;
    }

    this.phase = 'playing';
    this.currentPlayerIndex = 0; // Human starts
    this.clearTemporaryCardVisibility(); // Clear any temporary visibility from setup
    this.updateVintoCallAvailability();

    GameToastService.success('Game started! Draw a card or take from discard.');
  }

  // Action: Draw a card from the draw pile
  drawCard() {
    if (this.drawPile.length === 0) {
      GameToastService.error('No cards left in draw pile!');
      return;
    }

    const drawnCard = this.drawPile[0];
    this.drawPile = this.drawPile.slice(1);
    this.pendingCard = drawnCard;
    this.isChoosingCardAction = true;

    GameToastService.success(`Drew ${drawnCard.rank}`);
  }

  // Action: Take from discard pile
  takeFromDiscard() {
    if (this.discardPile.length === 0) return;

    const topCard = this.discardPile[0];
    // Can only take action cards (7-K) whose action hasn't been used
    if (topCard.played) return;

    this.pendingCard = topCard;
    this.discardPile = this.discardPile.slice(1);

    const currentPlayer = this.players[this.currentPlayerIndex];
    if (currentPlayer && currentPlayer.isHuman) {
      // For taking from discard, we use the action immediately, no swapping
      if (topCard.action) {
        this.executeCardAction(topCard, currentPlayer.id);
      } else {
        // Non-action card (shouldn't happen since we check for action cards)
        this.pendingCard = null;
        // Move to next player
        this.advanceTurn();
      }
    }
  }

  // Action: Choose to swap the drawn card
  chooseSwap() {
    this.isChoosingCardAction = false;
    this.isSelectingSwapPosition = true;
  }

  // Action: Choose to play/discard the drawn card directly
  choosePlayCard() {
    const currentPlayer = this.players[this.currentPlayerIndex];
    const pendingCard = this.pendingCard;

    if (!currentPlayer || !pendingCard) return;

    if (pendingCard.action) {
      this.executeCardAction(pendingCard, currentPlayer.id);
    } else {
      // For non-action cards, discard them directly
      this.discardPile = [pendingCard, ...this.discardPile];
      GameToastService.info(`Discarded ${pendingCard.rank}`);

      // Clean up and advance turn for non-action cards
      this.pendingCard = null;
      this.isChoosingCardAction = false;

      // Start toss-in period
      this.startTossInPeriod();
    }
  }

  // Action: Swap card at position
  swapCard(position: number) {
    const currentPlayer = this.players[this.currentPlayerIndex];
    const pendingCard = this.pendingCard;

    if (!currentPlayer || !pendingCard) return;

    // Store the swap position and move to declaration phase
    this.swapPosition = position;
    this.isSelectingSwapPosition = false;
    this.isDeclaringRank = true;

    GameToastService.info(
      'Choose to declare the card rank or skip declaration'
    );
  }

  // Action: Execute card action
  executeCardAction(card: Card, playerId: string) {
    if (!card.action) return;

    const player = this.players.find((p) => p.id === playerId);
    if (!player) return;

    GameToastService.success(
      `${player.name} played ${card.rank} - ${card.action}`
    );

    switch (card.rank) {
      case '7':
      case '8':
        // "Peek 1 of your cards"
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.discardPile = [card, ...this.discardPile];
        this.actionContext = {
          action: card.action,
          playerId: playerId,
          targetType: 'own-card',
        };
        this.isAwaitingActionTarget = true;
        break;

      case '9':
      case '10':
        // "Peek 1 opponent card"
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.discardPile = [card, ...this.discardPile];
        this.actionContext = {
          action: card.action,
          playerId: playerId,
          targetType: 'opponent-card',
        };
        this.isAwaitingActionTarget = true;
        break;

      case 'J':
        // "Swap any two facedown cards on the table"
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.discardPile = [card, ...this.discardPile];
        this.actionContext = {
          action: card.action,
          playerId: playerId,
          targetType: 'swap-cards',
        };
        this.isAwaitingActionTarget = true;
        this.swapTargets = [];
        break;

      case 'Q':
        // "Peek any two cards, then optionally swap them"
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.discardPile = [card, ...this.discardPile];
        this.actionContext = {
          action: card.action,
          playerId: playerId,
          targetType: 'peek-then-swap',
        };
        this.isAwaitingActionTarget = true;
        this.peekTargets = [];
        break;

      case 'K':
        // "Declare any card action and execute it"
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.discardPile = [card, ...this.discardPile];
        this.actionContext = {
          action: card.action,
          playerId: playerId,
          targetType: 'declare-action',
        };
        this.isAwaitingActionTarget = true;
        break;

      case 'A':
        // "Force opponent to draw"
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.discardPile = [card, ...this.discardPile];
        this.actionContext = {
          action: card.action,
          playerId: playerId,
          targetType: 'force-draw',
        };
        this.isAwaitingActionTarget = true;
        break;

      default:
        // All card actions should now be implemented
        GameToastService.info(`${card.rank} action not yet implemented`);

        // For now, just clean up and advance turn
        this.pendingCard = null;
        this.isChoosingCardAction = false;
        this.startTossInPeriod();
        break;
    }
  }

  // Action: Discard drawn card without using its action
  discardCard() {
    if (this.pendingCard) {
      this.discardPile = [this.pendingCard, ...this.discardPile];
      this.pendingCard = null;
      this.isSelectingSwapPosition = false;
      this.isChoosingCardAction = false;
      this.isAwaitingActionTarget = false;
      this.actionContext = null;
      this.selectedSwapPosition = null;

      // Start toss-in period after discarding
      this.startTossInPeriod();
    }
  }

  // Helper method to advance turn
  private advanceTurn() {
    // Don't advance turn if an AI move is currently in progress
    if (this.aiThinking) {
      return;
    }

    this.turnCount++;
    this.currentPlayerIndex =
      (this.currentPlayerIndex + 1) % this.players.length;

    // Ensure clean state for the new player's turn
    this.waitingForTossIn = false;
    this.tossInTimer = 0;
    this.aiThinking = false;

    // Clear any existing toss-in interval
    if (this.tossInInterval) {
      clearInterval(this.tossInInterval);
      this.tossInInterval = null;
    }


    this.updateVintoCallAvailability();

    // Scoring begins only after a called Vinto final round completes
    if (this.finalTurnTriggered && this.currentPlayerIndex === 0) {
      this.phase = 'scoring';
    }
  }

  // Action: Start toss-in period
  startTossInPeriod() {
    try {
      this.waitingForTossIn = true;
      this.tossInTimer = this.tossInTimeConfig;

      // Clear any existing interval
      if (this.tossInInterval) {
        clearInterval(this.tossInInterval);
      }

      // Combined interval for countdown timer, bot participation, and end detection
      this.tossInInterval = setInterval(() => {
        if (!this.waitingForTossIn || this.tossInTimer <= 0) {
          if (this.tossInInterval) {
            clearInterval(this.tossInInterval);
            this.tossInInterval = null;
          }

          // If timer expired, advance turn
          if (this.tossInTimer <= 0) {
            runInAction(() => {
              this.waitingForTossIn = false;
              this.tossInTimer = 0;
              this.advanceTurn();
            });
          }
          return;
        }

        // Decrement timer and handle bot participation
        runInAction(() => {
          this.tossInTimer = Math.max(0, this.tossInTimer - 1);

          // Bot participation logic
          const discardedRank = this.discardPile[0]?.rank;
          if (discardedRank) {
            this.players.forEach((player) => {
              if (!player.isHuman && Math.random() < 0.3) {
                // 30% chance bot tosses in
                player.cards.forEach((card, position) => {
                  if (card.rank === discardedRank && Math.random() < 0.5) {
                    this.tossInCard(player.id, position);
                  }
                });
              }
            });
          }
        });
      }, 1000);
    } catch (error) {
      console.error('Error starting toss-in period:', error);
    }
  }

  // Action: Toss in a card
  tossInCard(playerId: string, position: number) {
    if (!this.waitingForTossIn) return;

    const player = this.players.find((p) => p.id === playerId);
    const topDiscard = this.discardPile[0];

    if (
      !player ||
      !topDiscard ||
      position >= player.cards.length ||
      position < 0
    )
      return;

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
      this.discardPile = [tossedCard, ...this.discardPile];

      GameToastService.success(`${player.name} tossed in ${tossedCard.rank}!`);
    } else {
      // Incorrect toss-in: penalty card
      if (this.drawPile.length > 0) {
        const penaltyCard = this.drawPile[0];
        player.cards.push(penaltyCard);
        this.drawPile = this.drawPile.slice(1);

        GameToastService.error(
          `${player.name}'s toss-in failed - penalty card drawn`
        );
      }
    }
  }

  // Action: Select target for card action
  selectActionTarget(targetPlayerId: string, position: number) {
    if (!this.isAwaitingActionTarget || !this.actionContext) return;

    const { playerId, targetType } = this.actionContext;
    const actionPlayer = this.players.find((p) => p.id === playerId);
    const targetPlayer = this.players.find((p) => p.id === targetPlayerId);

    if (!actionPlayer || !targetPlayer) return;

    // Handle "Peek 1 of your cards" (7 and 8)
    if (targetType === 'own-card' && targetPlayerId === playerId) {
      if (position >= 0 && position < targetPlayer.cards.length) {
        const peekedCard = targetPlayer.cards[position];
        // Add to temporary visibility instead of permanent knowledge
        targetPlayer.temporarilyVisibleCards.add(position);

        GameToastService.success(
          `${actionPlayer.name} peeked at position ${position + 1}: ${
            peekedCard.rank
          } (value ${peekedCard.value})`
        );
      }
    }

    // Handle "Peek 1 opponent card" (9 and 10)
    if (targetType === 'opponent-card' && targetPlayerId !== playerId) {
      if (position >= 0 && position < targetPlayer.cards.length) {
        const peekedCard = targetPlayer.cards[position];

        GameToastService.success(
          `${actionPlayer.name} peeked at ${targetPlayer.name}'s position ${
            position + 1
          }: ${peekedCard.rank} (value ${peekedCard.value})`
        );
      }
    }

    // Handle "Swap any two facedown cards on the table" (J)
    if (targetType === 'swap-cards') {
      // Check if this card is already selected (to allow deselection)
      const existingIndex = this.swapTargets.findIndex(
        (target) =>
          target.playerId === targetPlayerId && target.position === position
      );

      if (existingIndex !== -1) {
        // Card is already selected, remove it (deselect)
        this.swapTargets.splice(existingIndex, 1);
        GameToastService.info(
          `${actionPlayer.name} deselected ${targetPlayer.name}'s card ${
            position + 1
          }`
        );
        return;
      }

      // Don't allow more than 2 selections
      if (this.swapTargets.length >= 2) {
        GameToastService.info(
          'Already have 2 cards selected. Deselect one to choose a different card.'
        );
        return;
      }

      // Check if trying to select second card from same player
      if (this.swapTargets.length === 1 && this.swapTargets[0].playerId === targetPlayerId) {
        GameToastService.warning('Cannot swap two cards from the same player!');
        return;
      }

      // Add this target to the swap targets list
      const newTarget = { playerId: targetPlayerId, position };
      this.swapTargets.push(newTarget);

      // If we have both targets, perform the swap
      if (this.swapTargets.length === 2) {
        const [target1, target2] = this.swapTargets;
        const player1 = this.players.find((p) => p.id === target1.playerId);
        const player2 = this.players.find((p) => p.id === target2.playerId);

        if (
          player1 &&
          player2 &&
          target1.position >= 0 &&
          target1.position < player1.cards.length &&
          target2.position >= 0 &&
          target2.position < player2.cards.length
        ) {
          // Perform the swap
          const card1 = player1.cards[target1.position];
          const card2 = player2.cards[target2.position];

          player1.cards[target1.position] = card2;
          player2.cards[target2.position] = card1;

          GameToastService.success(
            `${actionPlayer.name} swapped ${player1.name}'s card ${
              target1.position + 1
            } with ${player2.name}'s card ${target2.position + 1}`
          );
        }

        // Clear swap targets after performing swap
        this.swapTargets = [];
      } else {
        // Still need one more target
        GameToastService.info(
          `${actionPlayer.name} selected first card. Choose second card to swap with.`
        );
        return; // Don't clean up yet, still need second target
      }
    }

    // Handle "Peek any two cards, then optionally swap them" (Q)
    if (targetType === 'peek-then-swap') {
      // Check if this card is already selected (to allow deselection)
      const existingIndex = this.peekTargets.findIndex(
        (target) =>
          target.playerId === targetPlayerId && target.position === position
      );

      if (existingIndex !== -1) {
        // Card is already selected, remove it (deselect)
        this.peekTargets.splice(existingIndex, 1);
        GameToastService.info(
          `${actionPlayer.name} deselected ${targetPlayer.name}'s card ${
            position + 1
          }`
        );
        return;
      }

      // Don't allow more than 2 selections
      if (this.peekTargets.length >= 2) {
        GameToastService.info(
          'Already have 2 cards selected. Deselect one to choose a different card.'
        );
        return;
      }

      // Check if trying to select second card from same player
      if (this.peekTargets.length === 1 && this.peekTargets[0].playerId === targetPlayerId) {
        GameToastService.warning('Cannot peek two cards from the same player!');
        return;
      }

      // Add this target to the peek targets list with card info
      if (position >= 0 && position < targetPlayer.cards.length) {
        const peekedCard = targetPlayer.cards[position];
        const newTarget = {
          playerId: targetPlayerId,
          position,
          card: peekedCard,
        };
        this.peekTargets.push(newTarget);

        GameToastService.success(
          `${actionPlayer.name} peeked at ${targetPlayer.name}'s position ${
            position + 1
          }: ${peekedCard.rank} (value ${peekedCard.value})`
        );

        // If we have both peek targets, move to swap decision phase
        if (this.peekTargets.length === 2) {
          const [peek1, peek2] = this.peekTargets;
          GameToastService.info(
            `Cards peeked: ${peek1.card!.rank} (${peek1.card!.value}) and ${
              peek2.card!.rank
            } (${peek2.card!.value}). Choose to swap them or skip.`
          );
          // The UI will show swap/skip buttons at this point
          return; // Don't clean up yet, still in swap decision phase
        } else {
          // Still need one more peek
          GameToastService.info(
            `${actionPlayer.name} peeked at first card. Choose second card to peek at.`
          );
          return; // Don't clean up yet, still need second target
        }
      }
    }

    // Handle "Force opponent to draw" (A)
    if (targetType === 'force-draw') {
      // For Ace action, targetPlayerId is the opponent to force draw, position is ignored
      if (targetPlayerId !== playerId && this.drawPile.length > 0) {
        const drawnCard = this.drawPile.shift();
        if (drawnCard) {
          targetPlayer.cards.push(drawnCard);

          GameToastService.success(
            `${actionPlayer.name} forced ${targetPlayer.name} to draw a card. ${targetPlayer.name} now has ${targetPlayer.cards.length} cards.`
          );
        }
      }
    }

    // Clean up and advance turn
    this.isAwaitingActionTarget = false;
    this.actionContext = null;
    this.swapTargets = [];
    this.peekTargets = [];

    // Clear temporary card visibility since action is complete
    this.clearTemporaryCardVisibility();

    // Start toss-in period
    this.startTossInPeriod();
  }

  // Action: Execute Queen's optional swap
  executeQueenSwap() {
    if (this.peekTargets.length !== 2) return;

    const [target1, target2] = this.peekTargets;
    const player1 = this.players.find((p) => p.id === target1.playerId);
    const player2 = this.players.find((p) => p.id === target2.playerId);

    if (
      player1 &&
      player2 &&
      target1.position >= 0 &&
      target1.position < player1.cards.length &&
      target2.position >= 0 &&
      target2.position < player2.cards.length
    ) {
      // Perform the swap
      const card1 = player1.cards[target1.position];
      const card2 = player2.cards[target2.position];

      player1.cards[target1.position] = card2;
      player2.cards[target2.position] = card1;

      GameToastService.success(
        `Queen action: Swapped ${player1.name}'s ${card1.rank} with ${player2.name}'s ${card2.rank}`
      );
    }

    // Clean up and advance turn
    this.isAwaitingActionTarget = false;
    this.actionContext = null;
    this.peekTargets = [];

    // Start toss-in period
    this.startTossInPeriod();
  }

  // Action: Skip Queen's optional swap
  skipQueenSwap() {
    GameToastService.info('Queen action: Chose not to swap the peeked cards');

    // Clean up and advance turn
    this.isAwaitingActionTarget = false;
    this.actionContext = null;
    this.peekTargets = [];

    // Start toss-in period
    this.startTossInPeriod();
  }

  // Action: Declare rank during card swap
  declareRank(rank: Rank) {
    const currentPlayer = this.players[this.currentPlayerIndex];
    const pendingCard = this.pendingCard;
    const swapPosition = this.swapPosition;

    if (!currentPlayer || !pendingCard || swapPosition === null) return;

    // Get the actual card being replaced
    const actualCard = currentPlayer.cards[swapPosition];

    // Check if declaration is correct
    const isCorrectDeclaration = actualCard && actualCard.rank === rank;

    if (isCorrectDeclaration) {
      GameToastService.success(
        `Correct declaration! ${rank} matches the card. You can use its action.`
      );

      // Perform the swap
      currentPlayer.cards[swapPosition] = pendingCard;
      this.discardPile = [actualCard!, ...this.discardPile];

      // If the declared card has an action, execute it
      if (actualCard!.action) {
        this.actionContext = {
          action: actualCard!.action,
          playerId: currentPlayer.id,
          targetType: (() => {
            switch (actualCard!.rank) {
              case '7':
              case '8':
                return 'own-card' as const;
              case '9':
              case '10':
                return 'opponent-card' as const;
              case 'J':
                return 'swap-cards' as const;
              case 'Q':
                return 'peek-then-swap' as const;
              case 'K':
                return 'declare-action' as const;
              case 'A':
                return 'force-draw' as const;
              default:
                return 'own-card' as const;
            }
          })(),
        };
        this.isAwaitingActionTarget = true;
      } else {
        // No action to execute, just advance turn
        this.startTossInPeriod();
      }
    } else {
      GameToastService.error(
        `Wrong declaration! ${rank} doesn't match the card. Drawing penalty card.`
      );

      // Perform the swap
      currentPlayer.cards[swapPosition] = pendingCard;
      this.discardPile = [actualCard!, ...this.discardPile];

      // Add penalty card if available
      if (this.drawPile.length > 0) {
        const penaltyCard = this.drawPile[0];
        currentPlayer.cards.push(penaltyCard);
        this.drawPile = this.drawPile.slice(1);
      }

      // Advance turn
      this.startTossInPeriod();
    }

    // Clean up declaration state
    this.isDeclaringRank = false;
    this.swapPosition = null;
    this.pendingCard = null;
  }

  // Action: Skip rank declaration during card swap
  skipDeclaration() {
    const currentPlayer = this.players[this.currentPlayerIndex];
    const pendingCard = this.pendingCard;
    const swapPosition = this.swapPosition;

    if (!currentPlayer || !pendingCard || swapPosition === null) return;

    GameToastService.info('Skipped declaration. Card swapped without action.');

    // Perform the swap without declaration
    const replacedCard = currentPlayer.cards[swapPosition];
    currentPlayer.cards[swapPosition] = pendingCard;

    if (replacedCard) {
      this.discardPile = [replacedCard, ...this.discardPile];
    }

    // Clean up declaration state
    this.isDeclaringRank = false;
    this.swapPosition = null;
    this.pendingCard = null;

    // Advance turn
    this.startTossInPeriod();
  }

  // Action: Make AI move
  async makeAIMove(difficulty: string) {
    try {
      const currentPlayer = this.players[this.currentPlayerIndex];


      if (!currentPlayer || currentPlayer.isHuman) return;

      // Prevent multiple simultaneous AI moves
      if (this.aiThinking) {
        return;
      }

      this.aiThinking = true;
      this.currentMove = null;
      this.updateVintoCallAvailability();

      try {
        const move = await this.oracle.requestAIMove(
          this,
          currentPlayer.id,
          difficulty as Difficulty
        );

        runInAction(() => {
          this.currentMove = move;
          this.aiThinking = false;

          // Apply AI move logic
          if (this.drawPile.length > 0) {
            const drawnCard = this.drawPile[0];
            this.drawPile = this.drawPile.slice(1);

            // Find worst known card to potentially swap
            let worstPosition = 0;
            let worstValue = -10;

            currentPlayer.cards.forEach((card, index) => {
              if (
                currentPlayer.knownCardPositions.has(index) &&
                card.value > worstValue
              ) {
                worstValue = card.value;
                worstPosition = index;
              }
            });

            // Smart AI decision: swap if drawn card is better
            if (drawnCard.value < worstValue && worstValue > 3) {
              const discardedCard = currentPlayer.cards[worstPosition];
              currentPlayer.cards[worstPosition] = drawnCard;
              this.discardPile = [discardedCard, ...this.discardPile];
              currentPlayer.knownCardPositions.add(worstPosition);
            } else {
              // Just discard the drawn card
              this.discardPile = [drawnCard, ...this.discardPile];
            }
          }

          // Start toss-in period after AI move (turn will advance when toss-in ends)
          this.startTossInPeriod();
        });
      } catch {
        runInAction(() => {
          this.aiThinking = false;
          this.advanceTurn();
        });
      }
    } catch (error) {
      console.error('Error in makeAIMove:', error);
      runInAction(() => {
        this.aiThinking = false;
      });
    }
  }

  // Action: Declare King action - choose any card's action to execute
  declareKingAction(rank: Rank) {
    if (!this.actionContext) return;

    const actionPlayer = this.players.find(
      (p) => p.id === this.actionContext!.playerId
    );
    if (!actionPlayer) return;

    const declaredAction = (() => {
      switch (rank) {
        case '7':
          return 'Peek 1 of your cards';
        case '8':
          return 'Peek 1 of your cards';
        case '9':
          return 'Peek 1 opponent card';
        case '10':
          return 'Peek 1 opponent card';
        case 'J':
          return 'Swap any two facedown cards on the table';
        case 'Q':
          return 'Peek any two cards, then optionally swap them';
        case 'A':
          return 'Force opponent to draw';
        default:
          return 'Unknown action';
      }
    })();

    GameToastService.success(
      `${actionPlayer.name} declared King as ${rank} - ${declaredAction}`
    );

    // Update the action context with the declared card
    this.actionContext.declaredCard = rank;

    // Execute the declared card's action by updating target type
    if (rank === '7' || rank === '8') {
      this.actionContext.targetType = 'own-card';
    } else if (rank === '9' || rank === '10') {
      this.actionContext.targetType = 'opponent-card';
    } else if (rank === 'J') {
      this.actionContext.targetType = 'swap-cards';
      this.swapTargets = [];
    } else if (rank === 'Q') {
      this.actionContext.targetType = 'peek-then-swap';
      this.peekTargets = [];
    } else if (rank === 'A') {
      this.actionContext.targetType = 'force-draw';
    }

    this.actionContext.action = declaredAction;
    // Keep isAwaitingActionTarget = true since we now need to execute the declared action
  }

  // Action: Call Vinto
  callVinto() {
    this.finalTurnTriggered = true;
    GameToastService.success('VINTO called! Final round begins.');
  }

  // Action: Calculate final scores
  calculateFinalScores(): { [playerId: string]: number } {
    const scores: { [playerId: string]: number } = {};

    this.players.forEach((player) => {
      const totalValue = player.cards.reduce(
        (sum, card) => sum + card.value,
        0
      );
      scores[player.id] = totalValue;
    });

    return scores;
  }
}

// Create and export the store instance
export const gameStore = new GameStore();
