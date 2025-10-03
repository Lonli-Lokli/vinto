// services/bot-decision.ts
import { Card, Rank, Difficulty, GameState, Player, NeverError } from '../shapes';

export interface BotDecisionContext {
  botId: string;
  difficulty: Difficulty;
  botPlayer: Player;
  allPlayers: Player[];
  gameState: GameState;
  discardTop?: Card;
  pendingCard?: Card;
  currentAction?: {
    targetType: string;
    card: Card;
    peekTargets?: Array<{
      playerId: string;
      position: number;
      card: Card | undefined;
    }>;
  };
  // Opponent knowledge - what this bot knows about opponents' cards
  opponentKnowledge: Map<string, Map<number, Card>>; // opponentId -> position -> card
}

export interface BotActionTarget {
  playerId: string;
  position: number; // -1 for player-level targeting
}

export interface BotActionDecision {
  targets: BotActionTarget[];
  shouldSwap?: boolean; // For peek-then-swap decisions
  declaredRank?: Rank; // For King declarations
}

export interface BotTurnDecision {
  action: 'draw' | 'take-discard';
  cardChoice?: 'use-action' | 'swap' | 'discard'; // If drawing
  swapPosition?: number; // If swapping
}

export interface BotDecisionService {
  // Main turn decisions
  decideTurnAction(context: BotDecisionContext): BotTurnDecision;

  // Card action decisions
  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean;

  // Action target selections
  selectActionTargets(context: BotDecisionContext): BotActionDecision;

  // Specific action decisions
  shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean;
  selectKingDeclaration(context: BotDecisionContext): Rank;

  // Utility decisions
  shouldParticipateInTossIn(
    discardedRank: Rank,
    context: BotDecisionContext
  ): boolean;
  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null;
}

export class StandardBotDecisionService implements BotDecisionService {
  decideTurnAction(context: BotDecisionContext): BotTurnDecision {
    const { discardTop } = context;

    // Check if should take from discard
    if (this.shouldTakeFromDiscard(discardTop, context)) {
      return { action: 'take-discard' };
    }

    return { action: 'draw' };
  }

  private shouldTakeFromDiscard(
    topCard: Card | undefined,
    context: BotDecisionContext
  ): boolean {
    if (!topCard || topCard.played || !topCard.action) return false;

    const { difficulty } = context;

    // Difficulty-based heuristics
    const baseChance = {
      easy: 0.3,
      moderate: 0.5,
      hard: 0.8,
    }[difficulty];

    // Prefer high-value action cards
    const valueBonus = topCard.value >= 10 ? 0.3 : 0;

    return Math.random() < baseChance + valueBonus;
  }

  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean {
    if (!drawnCard.action) return false;

    const { difficulty } = context;

    // Base probabilities by difficulty
    const difficultyMultiplier = {
      easy: 0.6,
      moderate: 0.7,
      hard: 0.9,
    }[difficulty];

    // Card-specific base chances (only action cards have actions)
    const baseChances: Partial<Record<Rank, number>> = {
      '7': 0.9, // Peek own card - almost always useful
      '8': 0.7, // Peek opponent - useful for information
      '9': 0.5, // Swap two cards - situational
      '10': 0.8, // Peek then swap - powerful action
      J: 0.4, // Blind swap - risky
      Q: 0.9, // Peek then swap - very useful
      K: 0.3, // Declare rank - only if confident
      A: 0.6, // Force draw - tactical
    };
    const baseChance = baseChances[drawnCard.rank] || 0.5;

    return Math.random() < baseChance * difficultyMultiplier;
  }

  selectActionTargets(context: BotDecisionContext): BotActionDecision {
    const { currentAction } = context;
    if (!currentAction) return { targets: [] };

    switch (currentAction.targetType) {
      case 'own-card':
        return { targets: [this.selectOwnCardTarget(context)] };

      case 'opponent-card':
        return { targets: [this.selectOpponentCardTarget(context)] };

      case 'swap-cards':
        return { targets: this.selectSwapTargets(context) };

      case 'peek-then-swap':
        return this.selectPeekThenSwapTargets(context);

      case 'force-draw':
        return { targets: [this.selectForceDrawTarget(context)] };

      case 'declare-action':
        return {
          targets: [],
          declaredRank: this.selectKingDeclaration(context),
        };

      default:
        return { targets: [] };
    }
  }

  private selectOwnCardTarget(context: BotDecisionContext): BotActionTarget {
    const { botPlayer } = context;

    // Prefer unknown cards, fall back to any card
    const unknownPositions = botPlayer.cards
      .map((_, i) => i)
      .filter((i) => !botPlayer.knownCardPositions.has(i));

    const targetPosition =
      unknownPositions.length > 0
        ? unknownPositions[Math.floor(Math.random() * unknownPositions.length)]
        : Math.floor(Math.random() * botPlayer.cards.length);

    return { playerId: botPlayer.id, position: targetPosition };
  }

  private selectOpponentCardTarget(
    context: BotDecisionContext
  ): BotActionTarget {
    const { botId, allPlayers, opponentKnowledge, difficulty } = context;
    const opponents = allPlayers.filter((p) => p.id !== botId);

    if (opponents.length === 0) {
      return { playerId: botId, position: 0 }; // Fallback
    }

    // Try to target known high-value cards for peeking/swapping
    const knownHighValueTargets: BotActionTarget[] = [];

    opponentKnowledge.forEach((knownCards, opponentId) => {
      knownCards.forEach((card, position) => {
        const opponent = allPlayers.find(p => p.id === opponentId);
        // Only target if card still exists at that position
        if (opponent && position < opponent.cards.length && card.value >= 8) {
          knownHighValueTargets.push({ playerId: opponentId, position });
        }
      });
    });

    // High difficulty bots are more likely to use knowledge
    const useKnowledge = knownHighValueTargets.length > 0 && Math.random() < {
      easy: 0.3,
      moderate: 0.6,
      hard: 0.9,
    }[difficulty];

    if (useKnowledge) {
      // Target a known high-value card
      return knownHighValueTargets[Math.floor(Math.random() * knownHighValueTargets.length)];
    }

    // Otherwise, target unknown cards (gather information)
    const unknownTargets: BotActionTarget[] = [];
    opponents.forEach((opponent) => {
      const knownPositions = opponentKnowledge.get(opponent.id);
      opponent.cards.forEach((_, position) => {
        if (!knownPositions || !knownPositions.has(position)) {
          unknownTargets.push({ playerId: opponent.id, position });
        }
      });
    });

    if (unknownTargets.length > 0) {
      return unknownTargets[Math.floor(Math.random() * unknownTargets.length)];
    }

    // Fallback to random
    const targetPlayer = opponents[Math.floor(Math.random() * opponents.length)];
    const targetPosition = Math.floor(Math.random() * targetPlayer.cards.length);
    return { playerId: targetPlayer.id, position: targetPosition };
  }

  private selectSwapTargets(context: BotDecisionContext): BotActionTarget[] {
    const { botId, botPlayer, allPlayers, opponentKnowledge, difficulty } = context;

    // Strategy: Swap own high-value known card with opponent's low-value known card
    // Or swap own high-value known card with unknown opponent card (gamble)

    const ownHighValueCards: BotActionTarget[] = [];
    const opponentLowValueCards: BotActionTarget[] = [];
    const opponentUnknownCards: BotActionTarget[] = [];

    // Find own high-value known cards (value >= 7)
    botPlayer.cards.forEach((card, position) => {
      if (botPlayer.knownCardPositions.has(position) && card.value >= 7) {
        ownHighValueCards.push({ playerId: botId, position });
      }
    });

    // Find opponent cards
    allPlayers.forEach((player) => {
      if (player.id === botId) return; // Skip self

      const knownCards = opponentKnowledge.get(player.id);
      player.cards.forEach((_, position) => {
        if (knownCards && knownCards.has(position)) {
          const card = knownCards.get(position)!;
          if (card.value <= 4) {
            opponentLowValueCards.push({ playerId: player.id, position });
          }
        } else {
          opponentUnknownCards.push({ playerId: player.id, position });
        }
      });
    });

    // Smart swap logic based on difficulty
    const useStrategy = Math.random() < {
      easy: 0.2,
      moderate: 0.5,
      hard: 0.8,
    }[difficulty];

    if (useStrategy && ownHighValueCards.length > 0) {
      const ownCard = ownHighValueCards[Math.floor(Math.random() * ownHighValueCards.length)];

      // Prefer swapping with known low-value opponent card
      if (opponentLowValueCards.length > 0) {
        const oppCard = opponentLowValueCards[Math.floor(Math.random() * opponentLowValueCards.length)];
        return [ownCard, oppCard];
      }

      // Otherwise gamble with unknown opponent card
      if (opponentUnknownCards.length > 0) {
        const oppCard = opponentUnknownCards[Math.floor(Math.random() * opponentUnknownCards.length)];
        return [ownCard, oppCard];
      }
    }

    // Fallback: random swap
    const allTargets: BotActionTarget[] = [];
    allPlayers.forEach((player) => {
      player.cards.forEach((_, position) => {
        allTargets.push({ playerId: player.id, position });
      });
    });

    if (allTargets.length < 2) return [];

    const shuffled = [...allTargets].sort(() => Math.random() - 0.5);
    return [shuffled[0], shuffled[1]];
  }

  private selectPeekThenSwapTargets(
    context: BotDecisionContext
  ): BotActionDecision {
    const { botId, allPlayers } = context;
    const opponents = allPlayers.filter((p) => p.id !== botId);

    if (opponents.length < 2) return { targets: [] };

    // Select two different opponents
    const firstPlayer = opponents[Math.floor(Math.random() * opponents.length)];
    const remainingOpponents = opponents.filter((p) => p.id !== firstPlayer.id);

    if (remainingOpponents.length === 0) return { targets: [] };

    const secondPlayer =
      remainingOpponents[Math.floor(Math.random() * remainingOpponents.length)];

    return {
      targets: [
        {
          playerId: firstPlayer.id,
          position: Math.floor(Math.random() * firstPlayer.cards.length),
        },
        {
          playerId: secondPlayer.id,
          position: Math.floor(Math.random() * secondPlayer.cards.length),
        },
      ],
    };
  }

  private selectForceDrawTarget(context: BotDecisionContext): BotActionTarget {
    const { botId, allPlayers, opponentKnowledge, difficulty } = context;
    const opponents = allPlayers.filter((p) => p.id !== botId);

    if (opponents.length === 0) {
      return { playerId: botId, position: -1 }; // Fallback
    }

    // Strategy: Target player who is doing well (has fewest cards or low total value)
    // Higher difficulty = smarter targeting

    const playerScores = opponents.map((player) => {
      const knownCards = opponentKnowledge.get(player.id);
      let estimatedValue = 0;

      if (knownCards && knownCards.size > 0) {
        // Calculate known value
        knownCards.forEach((card) => {
          estimatedValue += card.value;
        });
        // Estimate unknown cards (assume average value of 6)
        const unknownCount = player.cards.length - knownCards.size;
        estimatedValue += unknownCount * 6;
      } else {
        // No knowledge - estimate all cards at average value
        estimatedValue = player.cards.length * 6;
      }

      return {
        player,
        cardCount: player.cards.length,
        estimatedValue,
      };
    });

    // Sort by card count (ascending) - fewer cards = doing better
    playerScores.sort((a, b) => a.cardCount - b.cardCount);

    // Higher difficulty = more likely to target player doing well
    const targetStrategically = Math.random() < {
      easy: 0.3,    // Often random
      moderate: 0.6, // Usually strategic
      hard: 0.9,     // Almost always strategic
    }[difficulty];

    if (targetStrategically && playerScores.length > 0) {
      // Target one of the top 2 players with fewest cards
      const topPlayers = playerScores.slice(0, Math.min(2, playerScores.length));
      const targetPlayer = topPlayers[Math.floor(Math.random() * topPlayers.length)].player;
      return { playerId: targetPlayer.id, position: -1 };
    }

    // Fallback: random opponent
    const targetPlayer = opponents[Math.floor(Math.random() * opponents.length)];
    return { playerId: targetPlayer.id, position: -1 };
  }

  shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean {
    if (peekedCards.length !== 2) return false;

    const { difficulty, botPlayer } = context;
    const [card1, card2] = peekedCards;

    // Calculate value difference
    const valueDiff = Math.abs(card1.value - card2.value);

    // Check if we can identify card ownership from peek targets in context
    const peekTargets = context.currentAction?.peekTargets;
    if (peekTargets && peekTargets.length === 2) {
      const target1 = peekTargets[0];
      const target2 = peekTargets[1];

      const isBotCard1 = target1.playerId === botPlayer.id;
      const isBotCard2 = target2.playerId === botPlayer.id;

      // If swapping would give bot the lower card, it's good
      // If one is bot's high card and other is opponent's low card, swap
      if (isBotCard1 && !isBotCard2) {
        // card1 is bot's, card2 is opponent's
        // Swap if bot's card is higher (get rid of high card)
        return card1.value > card2.value;
      } else if (!isBotCard1 && isBotCard2) {
        // card1 is opponent's, card2 is bot's
        // Swap if bot's card is higher (get rid of high card)
        return card2.value > card1.value;
      } else if (!isBotCard1 && !isBotCard2) {
        // Both are opponents' cards
        // Strategic: swap opponents' good cards with their bad cards to mess them up
        // Higher difficulty = more likely to sabotage
        const shouldSabotage = Math.random() < {
          easy: 0.1,
          moderate: 0.3,
          hard: 0.6,
        }[difficulty];

        return shouldSabotage && valueDiff >= 2;
      }
    }

    // Fallback: Simple threshold-based decision
    const swapThreshold = {
      easy: 2,
      moderate: 3,
      hard: 4,
    }[difficulty];

    return valueDiff >= swapThreshold;
  }

  selectKingDeclaration(context: BotDecisionContext): Rank {
    const { difficulty } = context;
    const actionRanks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'A'];

    // Higher difficulty bots make smarter choices
    if (difficulty === 'hard') {
      // Prefer more powerful actions
      const preferredRanks: Rank[] = ['Q', '10', '7', 'A'];
      if (Math.random() < 0.7) {
        return preferredRanks[
          Math.floor(Math.random() * preferredRanks.length)
        ];
      }
    }

    return actionRanks[Math.floor(Math.random() * actionRanks.length)];
  }

  shouldParticipateInTossIn(
    discardedRank: Rank,
    context: BotDecisionContext
  ): boolean {
    const { botPlayer, difficulty } = context;

    // Check if bot actually has matching cards
    const matchingPositions = botPlayer.cards
      .map((card, index) => ({ card, index }))
      .filter(({ card }) => card.rank === discardedRank);

    if (matchingPositions.length === 0) return false;

    // Difficulty affects confidence in toss-in
    const participationChance = {
      easy: 0.3,
      moderate: 0.5,
      hard: 0.9,
    }[difficulty];

    return Math.random() < participationChance;
  }

  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null {
    const { botPlayer } = context;

    // Find the worst known card to replace
    let worstPosition: number | null = null;
    let worstValue = drawnCard.value;

    botPlayer.cards.forEach((card, index) => {
      // Only consider cards we know about
      if (botPlayer.knownCardPositions.has(index) && card.value > worstValue) {
        worstValue = card.value;
        worstPosition = index;
      }
    });

    // If we found a known card worse than drawn card, swap it
    if (worstPosition !== null) {
      return worstPosition;
    }

    // No known card is worse - decide whether to swap with unknown card
    // Higher difficulty = more likely to gamble on unknown cards
    const { difficulty } = context;
    const unknownPositions = botPlayer.cards
      .map((_, i) => i)
      .filter((i) => !botPlayer.knownCardPositions.has(i));

    if (unknownPositions.length > 0) {
      const shouldGamble = Math.random() < {
        easy: 0.2,    // Rarely gamble
        moderate: 0.4, // Sometimes gamble
        hard: 0.6,     // Often gamble
      }[difficulty];

      if (shouldGamble) {
        // Select random unknown position
        return unknownPositions[Math.floor(Math.random() * unknownPositions.length)];
      }
    }

    return null;
  }
}

// Specialized bot implementations for different difficulties
export class BasicBotDecisionService extends StandardBotDecisionService {
  override shouldUseAction(
    drawnCard: Card,
    context: BotDecisionContext
  ): boolean {
    if (!drawnCard.action) return false;

    // Basic bots are more conservative and make simpler decisions
    const baseChances: Partial<Record<Rank, number>> = {
      '7': 0.5, // Less likely to peek own cards
      '8': 0.3, // Rarely peek opponents
      '9': 0.2, // Avoid complex swaps
      '10': 0.4, // Sometimes use peek-then-swap
      J: 0.1, // Avoid risky blind swaps
      Q: 0.5, // Moderate use of Queen
      K: 0.1, // Rarely declare ranks
      A: 0.3, // Sometimes force draws
    };

    return Math.random() < (baseChances[drawnCard.rank] || 0.3);
  }

  override shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean {
    if (peekedCards.length !== 2) return false;

    // Basic bots need large value differences to swap
    const [card1, card2] = peekedCards;
    const valueDiff = Math.abs(card1.value - card2.value);
    return valueDiff >= 4; // High threshold
  }

  override shouldParticipateInTossIn(
    discardedRank: Rank,
    context: BotDecisionContext
  ): boolean {
    const { botPlayer } = context;

    // Check if bot actually has matching cards
    const hasMatchingCard = botPlayer.cards.some(
      (card) => card.rank === discardedRank
    );
    if (!hasMatchingCard) return false;

    // Basic bots are very conservative with toss-ins
    return Math.random() < 0.2;
  }
}

export class ModerateBotDecisionService extends StandardBotDecisionService {
  // Uses the standard implementation but with moderate adjustments
  override shouldParticipateInTossIn(
    discardedRank: Rank,
    context: BotDecisionContext
  ): boolean {
    const { botPlayer } = context;

    const hasMatchingCard = botPlayer.cards.some(
      (card) => card.rank === discardedRank
    );
    if (!hasMatchingCard) return false;

    return Math.random() < 0.5; // Moderate participation
  }
}

export class HardBotDecisionService extends StandardBotDecisionService {
  override shouldUseAction(
    drawnCard: Card,
    context: BotDecisionContext
  ): boolean {
    if (!drawnCard.action) return false;

    // Ultimate bots make near-optimal decisions
    const gameState = context.gameState;
    const isEarlyGame = gameState.roundNumber === 1;
    const isLateGame = gameState.finalTurnTriggered;

    // Adapt strategy based on game phase
    let multiplier = 1.0;
    if (isEarlyGame) multiplier = 1.2; // More action usage early
    if (isLateGame) multiplier = 0.8; // More conservative late

    const baseChances: Partial<Record<Rank, number>> = {
      '7': 0.98, // Nearly always peek own cards
      '8': 0.9, // Very high opponent peeking
      '9': 0.8, // Strategic swaps
      '10': 0.95, // High peek-then-swap usage
      J: 0.7, // Calculated risks
      Q: 0.98, // Nearly always use Queen
      K: 0.6, // Confident declarations
      A: 0.9, // Strategic force draws
    };

    const chance = (baseChances[drawnCard.rank] || 0.8) * multiplier;
    return Math.random() < Math.min(0.99, chance);
  }

  override shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean {
    if (peekedCards.length !== 2) return false;

    const [card1, card2] = peekedCards;
    const valueDiff = Math.abs(card1.value - card2.value);

    // Ultimate bots consider game state for optimal swaps
    const gameState = context.gameState;
    const isLateGame = gameState.finalTurnTriggered;

    if (isLateGame) {
      // In late game, any improvement is valuable
      return valueDiff >= 1;
    } else {
      // Early game, look for significant improvements
      return valueDiff >= 3 || Math.random() < 0.4;
    }
  }

  override selectKingDeclaration(context: BotDecisionContext): Rank {
    // Ultimate bots analyze game state for optimal declarations
    const gameState = context.gameState;
    const isLateGame = gameState.finalTurnTriggered;

    if (isLateGame) {
      // Late game: prefer immediate value actions
      const lateGameRanks: Rank[] = ['7', '8', 'A'];
      return lateGameRanks[Math.floor(Math.random() * lateGameRanks.length)];
    } else {
      // Early/mid game: prefer powerful control actions
      const earlyGameRanks: Rank[] = ['Q', '10', '9'];
      if (Math.random() < 0.9) {
        return earlyGameRanks[
          Math.floor(Math.random() * earlyGameRanks.length)
        ];
      }
    }

    // Fallback
    const actionRanks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'A'];
    return actionRanks[Math.floor(Math.random() * actionRanks.length)];
  }

  override shouldParticipateInTossIn(
    discardedRank: Rank,
    context: BotDecisionContext
  ): boolean {
    const { botPlayer } = context;

    const matchingCards = botPlayer.cards.filter(
      (card) => card.rank === discardedRank
    );
    if (matchingCards.length === 0) return false;

    // Ultimate bots consider strategic value of toss-ins
    const actionValues: Partial<Record<Rank, number>> = {
      '7': 0.9,
      '8': 0.8,
      '9': 0.7,
      '10': 0.9,
      J: 0.6,
      Q: 0.95,
      K: 0.5,
      A: 0.8,
    };
    const actionValue = actionValues[discardedRank] || 0.5;

    return Math.random() < 0.8 * actionValue; // Strategic participation
  }
}

// Factory for creating bot decision services
export class BotDecisionServiceFactory {
  static create(difficulty: Difficulty): BotDecisionService {
    switch (difficulty) {
      case 'easy':
        return new BasicBotDecisionService();
      case 'moderate':
        return new ModerateBotDecisionService();
      case 'hard':
        return new HardBotDecisionService();
      default:
        throw new NeverError(difficulty);
    }
  }

  static createCustom(config: {
    aggression?: number;
    riskTolerance?: number;
    memoryAccuracy?: number;
  }): BotDecisionService {
    // Create a customized bot based on personality traits
    class CustomBotDecisionService extends StandardBotDecisionService {
      override shouldUseAction(
        drawnCard: Card,
        context: BotDecisionContext
      ): boolean {
        if (!drawnCard.action) return false;

        const baseResult = super.shouldUseAction(drawnCard, context);
        const aggression = config.aggression || 0.5;

        // Adjust based on aggression level
        const aggressionAdjustment = (aggression - 0.5) * 0.4; // ±0.2 max adjustment
        const adjustedChance = Math.max(
          0,
          Math.min(1, (baseResult ? 0.7 : 0.3) + aggressionAdjustment)
        );

        return Math.random() < adjustedChance;
      }

      override shouldSwapAfterPeek(
        peekedCards: Card[],
        context: BotDecisionContext
      ): boolean {
        if (peekedCards.length !== 2) return false;

        const riskTolerance = config.riskTolerance || 0.5;
        const [card1, card2] = peekedCards;
        const valueDiff = Math.abs(card1.value - card2.value);

        // Risk tolerance affects swap threshold
        const baseThreshold = 3;
        const riskAdjustment = (0.5 - riskTolerance) * 2; // More risk = lower threshold
        const threshold = Math.max(1, baseThreshold + riskAdjustment);

        return valueDiff >= threshold;
      }
    }

    return new CustomBotDecisionService();
  }
}
