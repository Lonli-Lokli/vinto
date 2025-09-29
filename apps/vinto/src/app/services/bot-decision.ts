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
    const { botId, allPlayers } = context;
    const opponents = allPlayers.filter((p) => p.id !== botId);

    if (opponents.length === 0) {
      return { playerId: botId, position: 0 }; // Fallback
    }

    const targetPlayer =
      opponents[Math.floor(Math.random() * opponents.length)];
    const targetPosition = Math.floor(
      Math.random() * targetPlayer.cards.length
    );

    return { playerId: targetPlayer.id, position: targetPosition };
  }

  private selectSwapTargets(context: BotDecisionContext): BotActionTarget[] {
    const { allPlayers } = context;

    // Collect all possible targets
    const allTargets: BotActionTarget[] = [];
    allPlayers.forEach((player) => {
      player.cards.forEach((_, position) => {
        allTargets.push({ playerId: player.id, position });
      });
    });

    if (allTargets.length < 2) return [];

    // Select two different targets
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
    const { botId, allPlayers } = context;
    const opponents = allPlayers.filter((p) => p.id !== botId);

    if (opponents.length === 0) {
      return { playerId: botId, position: -1 }; // Fallback
    }

    // Target player with fewest cards
    const targetPlayer = opponents.reduce((min, player) =>
      player.cards.length < min.cards.length ? player : min
    );

    return { playerId: targetPlayer.id, position: -1 };
  }

  shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean {
    if (peekedCards.length !== 2) return false;

    const { difficulty } = context;
    const [card1, card2] = peekedCards;

    // Simple heuristic: swap if significant value difference
    const valueDiff = Math.abs(card1.value - card2.value);

    const swapThreshold = {
      easy: 2,
      moderate: 3,
      hard: 5,
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
      if (botPlayer.knownCardPositions.has(index) && card.value > worstValue) {
        worstValue = card.value;
        worstPosition = index;
      }
    });

    return worstPosition;
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
