// services/bot-memory.ts
import { Card, Rank, Difficulty } from '../shapes';

/**
 * Memory entry for a single card observation
 */
export interface CardMemory {
  card: Card | null; // Actual card if known
  confidence: number; // 0-1, decays over time
  lastSeen: number; // Timestamp
  observations: number; // How many times seen
}

/**
 * Configuration for memory system based on difficulty
 */
export interface DifficultyMemoryConfig {
  memoryAccuracy: number; // 0-1: Chance to correctly remember a seen card
  memoryDecayRate: number; // How fast confidence decays over time (per second)
  maxMemorySize: number; // How many cards can be remembered
  forgetChance: number; // Chance to forget a card per turn
  observationRequired: number; // How many observations needed for high confidence
}

/**
 * Difficulty-based memory configurations
 */
export const DIFFICULTY_CONFIGS: Record<Difficulty, DifficultyMemoryConfig> = {
  easy: {
    memoryAccuracy: 0.4, // Often misremembers cards
    memoryDecayRate: 0.00015, // Fast decay (0.015% per second)
    maxMemorySize: 4, // Only remembers 4 cards
    forgetChance: 0.3, // 30% chance to forget per turn
    observationRequired: 3, // Needs to see multiple times
  },
  moderate: {
    memoryAccuracy: 0.75, // Usually correct
    memoryDecayRate: 0.00008, // Moderate decay
    maxMemorySize: 8, // Remembers 8 cards
    forgetChance: 0.1, // 10% chance to forget
    observationRequired: 2, // Needs 2 observations
  },
  hard: {
    memoryAccuracy: 0.95, // Almost perfect memory
    memoryDecayRate: 0.00002, // Slow decay
    maxMemorySize: 16, // Remembers almost everything
    forgetChance: 0.02, // Rarely forgets
    observationRequired: 1, // One observation enough
  },
};

/**
 * Bot memory system - tracks what the bot knows about cards in the game
 */
export class BotMemory {
  private config: DifficultyMemoryConfig;
  private botId: string;

  // Own cards knowledge (from setup peeks and actions)
  private ownCards: Map<number, CardMemory>;

  // Opponent cards knowledge (from peek actions)
  private opponentCards: Map<string, Map<number, CardMemory>>;

  // Deck tracking (cards seen in discard pile, etc.)
  private seenCards: Map<string, Card>; // cardId -> Card

  // Probabilistic beliefs about unknown cards
  private cardDistribution: Map<Rank, number>;

  constructor(botId: string, difficulty: Difficulty) {
    this.botId = botId;
    this.config = DIFFICULTY_CONFIGS[difficulty];
    this.ownCards = new Map();
    this.opponentCards = new Map();
    this.seenCards = new Map();
    this.cardDistribution = this.initializeCardDistribution();
  }

  /**
   * Initialize card distribution with standard deck composition
   */
  private initializeCardDistribution(): Map<Rank, number> {
    const distribution = new Map<Rank, number>();
    const ranks: Rank[] = [
      'A',
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
    ];

    ranks.forEach((rank) => {
      // Standard deck: 4 of each rank
      distribution.set(rank, 4);
    });

    return distribution;
  }

  /**
   * Observe a card (from peeking, seeing in discard pile, etc.)
   */
  observeCard(card: Card, playerId: string, position: number): void {
    // Check if bot correctly remembers (based on memory accuracy)
    const isCorrect = Math.random() < this.config.memoryAccuracy;

    if (!isCorrect) {
      // Bot misremembers or fails to record
      console.log(
        `[BotMemory] ${this.botId} failed to remember card at ${playerId}[${position}]`
      );
      return;
    }

    // Update seen cards tracking
    this.seenCards.set(card.id, card);

    // Update card distribution
    const currentCount = this.cardDistribution.get(card.rank) || 0;
    if (currentCount > 0) {
      this.cardDistribution.set(card.rank, currentCount - 1);
    }

    // Store or update memory
    const targetMap =
      playerId === this.botId
        ? this.ownCards
        : this.getOrCreatePlayerMemory(playerId);
    const existing = targetMap.get(position);

    const newMemory: CardMemory = {
      card,
      confidence: existing ? Math.min(1.0, existing.confidence + 0.3) : 0.7,
      lastSeen: Date.now(),
      observations: existing ? existing.observations + 1 : 1,
    };

    targetMap.set(position, newMemory);

    console.log(
      `[BotMemory] ${this.botId} remembered ${
        card.rank
      } at ${playerId}[${position}] (confidence: ${newMemory.confidence.toFixed(
        2
      )})`
    );

    // Enforce memory size limit
    this.enforceMemoryLimit();
  }

  /**
   * Forget a card at a specific position (when card is removed or replaced)
   */
  forgetCard(playerId: string, position: number): void {
    if (playerId === this.botId) {
      const memory = this.ownCards.get(position);
      if (memory) {
        // Card was removed/replaced, update distribution
        const count = this.cardDistribution.get(memory.card!.rank) || 0;
        this.cardDistribution.set(memory.card!.rank, count + 1);
      }
      this.ownCards.delete(position);
    } else {
      const playerMemory = this.opponentCards.get(playerId);
      if (playerMemory) {
        const memory = playerMemory.get(position);
        if (memory) {
          const count = this.cardDistribution.get(memory.card!.rank) || 0;
          this.cardDistribution.set(memory.card!.rank, count + 1);
        }
        playerMemory.delete(position);
      }
    }
  }

  /**
   * Get memory for a specific card position
   */
  getCardMemory(playerId: string, position: number): CardMemory | undefined {
    if (playerId === this.botId) {
      return this.ownCards.get(position);
    } else {
      return this.opponentCards.get(playerId)?.get(position);
    }
  }

  /**
   * Get all known cards for a player
   */
  getPlayerMemory(playerId: string): Map<number, CardMemory> {
    if (playerId === this.botId) {
      return new Map(this.ownCards);
    } else {
      return new Map(this.opponentCards.get(playerId) || new Map());
    }
  }

  /**
   * Get confidence about a card at a position (0-1)
   */
  getConfidence(playerId: string, position: number): number {
    const memory = this.getCardMemory(playerId, position);
    return memory ? memory.confidence : 0;
  }

  /**
   * Decay all memories over time
   */
  decayMemory(): void {
    const now = Date.now();
    const decayRate = this.config.memoryDecayRate;

    // Decay own cards
    this.decayMemoryMap(this.ownCards, now, decayRate);

    // Decay opponent cards
    for (const [, playerMemory] of this.opponentCards) {
      this.decayMemoryMap(playerMemory, now, decayRate);
    }
  }

  /**
   * Helper to decay a memory map
   */
  private decayMemoryMap(
    memoryMap: Map<number, CardMemory>,
    now: number,
    decayRate: number
  ): void {
    const toDelete: number[] = [];

    for (const [position, memory] of memoryMap) {
      const timeSinceLastSeen = now - memory.lastSeen;
      const decay = Math.exp(-decayRate * timeSinceLastSeen);

      memory.confidence *= decay;

      // Forget if confidence too low
      if (memory.confidence < 0.1) {
        toDelete.push(position);
      }
    }

    // Remove forgotten cards
    toDelete.forEach((pos) => memoryMap.delete(pos));
  }

  /**
   * Random forgetting at turn boundaries
   */
  processTurnBoundary(): void {
    // Random chance to forget cards
    this.randomForget(this.ownCards);

    for (const [, playerMemory] of this.opponentCards) {
      this.randomForget(playerMemory);
    }

    // Decay all memories
    this.decayMemory();
  }

  /**
   * Randomly forget some cards
   */
  private randomForget(memoryMap: Map<number, CardMemory>): void {
    const toDelete: number[] = [];

    for (const [position] of memoryMap) {
      if (Math.random() < this.config.forgetChance) {
        toDelete.push(position);
      }
    }

    toDelete.forEach((pos) => {
      const memory = memoryMap.get(pos);
      if (memory) {
        // Return card to distribution
        const count = this.cardDistribution.get(memory.card!.rank) || 0;
        this.cardDistribution.set(memory.card!.rank, count + 1);
      }
      memoryMap.delete(pos);
    });

    if (toDelete.length > 0) {
      console.log(`[BotMemory] ${this.botId} forgot ${toDelete.length} cards`);
    }
  }

  /**
   * Enforce memory size limit - forget lowest confidence cards
   */
  private enforceMemoryLimit(): void {
    const allMemories: Array<{
      playerId: string;
      position: number;
      confidence: number;
      map: Map<number, CardMemory>;
    }> = [];

    // Collect all memories
    for (const [position, memory] of this.ownCards) {
      allMemories.push({
        playerId: this.botId,
        position,
        confidence: memory.confidence,
        map: this.ownCards,
      });
    }

    for (const [playerId, playerMemory] of this.opponentCards) {
      for (const [position, memory] of playerMemory) {
        allMemories.push({
          playerId,
          position,
          confidence: memory.confidence,
          map: playerMemory,
        });
      }
    }

    // Sort by confidence (ascending)
    allMemories.sort((a, b) => a.confidence - b.confidence);

    // Forget lowest confidence cards if over limit
    const toForget = allMemories.length - this.config.maxMemorySize;
    if (toForget > 0) {
      for (let i = 0; i < toForget; i++) {
        const item = allMemories[i];
        const memory = item.map.get(item.position);
        if (memory) {
          // Return to distribution
          const count = this.cardDistribution.get(memory.card!.rank) || 0;
          this.cardDistribution.set(memory.card!.rank, count + 1);
        }
        item.map.delete(item.position);
      }
      console.log(
        `[BotMemory] ${this.botId} enforced memory limit, forgot ${toForget} cards`
      );
    }
  }

  /**
   * Get or create memory map for a player
   */
  private getOrCreatePlayerMemory(playerId: string): Map<number, CardMemory> {
    if (!this.opponentCards.has(playerId)) {
      this.opponentCards.set(playerId, new Map());
    }
    return this.opponentCards.get(playerId)!;
  }

  /**
   * Get probability distribution for unknown cards
   */
  getCardDistribution(): Map<Rank, number> {
    return new Map(this.cardDistribution);
  }

  /**
   * Sample a card from the current distribution
   */
  sampleCardFromDistribution(): Rank | null {
    const available: Rank[] = [];

    for (const [rank, count] of this.cardDistribution) {
      for (let i = 0; i < count; i++) {
        available.push(rank);
      }
    }

    if (available.length === 0) return null;

    return available[Math.floor(Math.random() * available.length)];
  }

  /**
   * Get total number of cards in memory
   */
  getMemorySize(): number {
    let size = this.ownCards.size;

    for (const [, playerMemory] of this.opponentCards) {
      size += playerMemory.size;
    }

    return size;
  }

  /**
   * Get memory statistics for debugging
   */
  getStats() {
    return {
      ownCardsKnown: this.ownCards.size,
      opponentCardsKnown: Array.from(this.opponentCards.values()).reduce(
        (sum, map) => sum + map.size,
        0
      ),
      totalMemoryUsed: this.getMemorySize(),
      maxMemorySize: this.config.maxMemorySize,
      seenCardsCount: this.seenCards.size,
    };
  }

  /**
   * Clear all memory (for testing/reset)
   */
  clear(): void {
    this.ownCards.clear();
    this.opponentCards.clear();
    this.seenCards.clear();
    this.cardDistribution = this.initializeCardDistribution();
  }
}
