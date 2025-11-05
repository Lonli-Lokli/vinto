// services/strategic-bot-decision.ts

import {
  Card,
  Difficulty,
  getCardAction,
  getCardValue,
  Rank,
  CardAction,
} from '@vinto/shapes';
import { BotMemory } from './bot-memory';
import {
  BotActionDecision,
  BotDecisionContext,
  BotDecisionService,
  BotTurnDecision,
} from './shapes';

/**
 * Strategic Evaluation Bot
 *
 * Instead of MCTS tree search, directly evaluates each move using expert heuristics.
 * This is faster and strategically stronger for Vinto's large branching factor.
 *
 * Key principles:
 * 1. Information is power (peek actions are extremely valuable)
 * 2. Pairs/triples enable toss-in cascades (multiplicative value)
 * 3. Action cards are options (keep them for the right moment)
 * 4. Score relative to opponents matters, not absolute score
 */
export class StrategicBotDecisionService implements BotDecisionService {
  private botMemory: BotMemory;
  private botId: string;
  private difficulty: Difficulty;

  // Difficulty-based parameters
  private readonly thinkingDepth: number;
  private readonly evaluationAccuracy: number;

  constructor(difficulty: Difficulty) {
    this.difficulty = difficulty;
    this.botId = '';
    this.botMemory = new BotMemory('', difficulty);

    // Difficulty scaling
    switch (difficulty) {
      case 'easy':
        this.thinkingDepth = 1; // Only consider immediate consequences
        this.evaluationAccuracy = 0.7; // 70% accurate evaluation
        break;
      case 'moderate':
        this.thinkingDepth = 2; // 1-ply lookahead
        this.evaluationAccuracy = 0.85; // 85% accurate
        break;
      case 'hard':
        this.thinkingDepth = 3; // 2-ply lookahead
        this.evaluationAccuracy = 1.0; // Perfect evaluation
        break;
    }

    console.log(
      `[Strategic Bot] Initialized with difficulty: ${this.difficulty}, thinkingDepth: ${this.thinkingDepth}, evaluationAccuracy: ${this.evaluationAccuracy}`
    );
  }

  // ========== Main Decision Entry Points ==========

  decideTurnAction(context: BotDecisionContext): BotTurnDecision {
    this.initializeIfNeeded(context);

    // Evaluate: Should we take from discard or draw?
    const takeDiscardValue = this.evaluateTakeDiscard(context);
    const drawValue = this.evaluateDrawFromDeck(context);

    console.log(
      `[Strategic Bot] Take discard value: ${takeDiscardValue.toFixed(2)}, ` +
        `Draw value: ${drawValue.toFixed(2)}`
    );

    if (takeDiscardValue > drawValue) {
      return { action: 'take-discard' };
    }

    return { action: 'draw' };
  }

  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    if (!drawnCard.actionText) return false;

    // Evaluate: Use action vs swap vs discard
    const useActionValue = this.evaluateUseAction(drawnCard, context);
    const swapValue = this.evaluateBestSwap(drawnCard, context);
    const discardValue = this.evaluateDiscard(drawnCard, context);

    console.log(
      `[Strategic Bot] ${drawnCard.rank} - Use action: ${useActionValue.toFixed(
        2
      )}, ` +
        `Swap: ${swapValue.toFixed(2)}, Discard: ${discardValue.toFixed(2)}`
    );

    return useActionValue > swapValue && useActionValue > discardValue;
  }

  selectActionTargets(context: BotDecisionContext): BotActionDecision {
    this.initializeIfNeeded(context);

    const actionCard = context.pendingCard || context.currentAction?.card;
    if (!actionCard) return { targets: [] };

    const action = getCardAction(actionCard.rank);
    if (!action) return { targets: [] };

    return this.selectBestActionTargets(action, context);
  }

  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null {
    this.initializeIfNeeded(context);

    let bestPosition = 0;
    let bestValue = -Infinity;

    for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
      const value = this.evaluateSwapAtPosition(drawnCard, pos, context);

      if (value > bestValue) {
        bestValue = value;
        bestPosition = pos;
      }
    }

    console.log(
      `[Strategic Bot] Best swap position: ${bestPosition} (value: ${bestValue.toFixed(
        2
      )})`
    );

    return bestPosition;
  }

  shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean {
    this.initializeIfNeeded(context);

    // Update memory with peeked cards
    if (context.currentAction?.peekTargets) {
      context.currentAction.peekTargets.forEach((target, index) => {
        if (peekedCards[index]) {
          this.botMemory.observeCard(
            peekedCards[index],
            target.playerId,
            target.position
          );
        }
      });
    }

    // For Queen: evaluate swap vs no-swap
    if (peekedCards.length !== 2) return false;

    const [card1, card2] = peekedCards;
    const [target1, target2] = context.currentAction?.peekTargets || [];

    if (!target1 || !target2) return false;

    // Calculate value difference
    const valueBeforeSwap =
      (target1.playerId === this.botId ? card1.value : -card1.value) +
      (target2.playerId === this.botId ? card2.value : -card2.value);

    const valueAfterSwap =
      (target1.playerId === this.botId ? card2.value : -card2.value) +
      (target2.playerId === this.botId ? card1.value : -card1.value);

    const swapBenefit = valueBeforeSwap - valueAfterSwap;

    console.log(`[Strategic Bot] Peek swap benefit: ${swapBenefit.toFixed(2)}`);

    return swapBenefit > 2; // Swap if benefit > 2 points
  }

  selectKingDeclaration(context: BotDecisionContext): Rank {
    this.initializeIfNeeded(context);

    let bestRank: Rank = 'Q'; // Default to Queen (most powerful)
    let bestValue = -Infinity;

    // Evaluate declaring each known card in our hand
    for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
      if (!context.botPlayer.knownCardPositions.includes(pos)) continue;

      const card = context.botPlayer.cards[pos];
      const value = this.evaluateKingDeclaration(card.rank, context);

      if (value > bestValue) {
        bestValue = value;
        bestRank = card.rank;
      }
    }

    console.log(
      `[Strategic Bot] Best King declaration: ${bestRank} (value: ${bestValue.toFixed(
        2
      )})`
    );

    return bestRank;
  }

  shouldParticipateInTossIn(
    discardedRanks: [Rank, ...Rank[]],
    context: BotDecisionContext
  ): boolean {
    this.initializeIfNeeded(context);
    const ranksToCheck: Rank[] = discardedRanks.filter(
      (rank) => getCardValue(rank) >= 0 // never toss in Joker
    );

    // Simple: always toss in if we have matching cards
    // (Tossing in is always beneficial: reduces hand size and score)
    return context.botPlayer.cards.some(
      (card, index) =>
        context.botPlayer.knownCardPositions.includes(index) &&
        ranksToCheck.includes(card.rank)
    );
  }

  shouldCallVinto(context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    const botScore = this.calculateScore(context.botPlayer.cards);
    const opponentScores = context.allPlayers
      .filter((p) => p.id !== this.botId)
      .map((p) => this.calculateScore(p.cards));

    const minOpponentScore = Math.min(...opponentScores);
    const avgOpponentScore =
      opponentScores.reduce((a, b) => a + b, 0) / opponentScores.length;

    // Call Vinto if:
    // 1. We have a significant lead over average (5+ points)
    // 2. We're better than the best opponent (2+ points)
    // 3. Not too early in the game
    const hasLead = botScore < avgOpponentScore - 5;
    const beatsBest = botScore < minOpponentScore - 2;
    const notTooEarly =
      context.gameState.turnNumber >= context.allPlayers.length * 3;

    console.log(
      `[Strategic Bot] Vinto check - Bot: ${botScore}, Min opp: ${minOpponentScore}, ` +
        `Avg opp: ${avgOpponentScore.toFixed(1)} - Call: ${
          hasLead && beatsBest && notTooEarly
        }`
    );

    return hasLead && beatsBest && notTooEarly;
  }

  // ========== Evaluation Functions ==========

  /**
   * Evaluate taking from discard pile
   */
  private evaluateTakeDiscard(context: BotDecisionContext): number {
    const discardTop = context.discardTop;
    if (!discardTop || !discardTop.actionText || discardTop.played) {
      return -Infinity; // Can't take
    }

    const action = getCardAction(discardTop.rank);
    if (!action) return -Infinity;

    // Evaluate the action card's value
    return this.evaluateActionCardValue(action, discardTop, context);
  }

  /**
   * Evaluate drawing from deck
   */
  private evaluateDrawFromDeck(_context: BotDecisionContext): number {
    // Drawing is always an option, baseline value
    // Expected value: average card value is ~5.5, but we have options (swap/discard)
    return 3.0; // Moderate positive value
  }

  /**
   * Evaluate using an action card
   */
  private evaluateUseAction(card: Card, context: BotDecisionContext): number {
    const action = getCardAction(card.rank);
    if (!action) return -Infinity;

    return this.evaluateActionCardValue(action, card, context);
  }

  /**
   * Evaluate best swap option for drawn card
   */
  private evaluateBestSwap(
    drawnCard: Card,
    context: BotDecisionContext
  ): number {
    let bestValue = -Infinity;

    for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
      const value = this.evaluateSwapAtPosition(drawnCard, pos, context);
      bestValue = Math.max(bestValue, value);
    }

    return bestValue;
  }

  /**
   * Evaluate discarding drawn card
   */
  private evaluateDiscard(card: Card, context: BotDecisionContext): number {
    // Discarding removes the card from play
    // Value: potential toss-in cascade + keeping current hand
    let value = 0;

    // Check toss-in potential
    const matchingCards = context.botPlayer.cards.filter(
      (c, i) =>
        context.botPlayer.knownCardPositions.includes(i) && c.rank === card.rank
    );

    if (matchingCards.length > 0) {
      // Toss-in cascade value
      const cascadeValue = matchingCards.reduce((sum, c) => sum + c.value, 0);
      value += cascadeValue * 2; // High value for cascades
    }

    // Penalty: we're not improving our hand
    value -= 1;

    return value;
  }

  /**
   * Evaluate swapping drawn card at specific position
   */
  private evaluateSwapAtPosition(
    drawnCard: Card,
    position: number,
    context: BotDecisionContext
  ): number {
    const oldCard = context.botPlayer.cards[position];
    const isKnown = context.botPlayer.knownCardPositions.includes(position);

    let value = 0;

    // Component 1: Direct value difference
    if (isKnown) {
      const scoreDiff = oldCard.value - drawnCard.value;
      value += scoreDiff * 3; // 3x weight for score improvement
    } else {
      // Unknown card: assume average value (5.5)
      const expectedScoreDiff = 5.5 - drawnCard.value;
      value += expectedScoreDiff * 1.5; // Lower weight due to uncertainty
    }

    // Component 2: Toss-in cascade potential
    const cascadeValue = this.evaluateTossInCascade(oldCard.rank, context);
    value += cascadeValue;

    // Component 3: Action card value retention
    if (getCardAction(oldCard.rank) && isKnown) {
      // Penalty for swapping away action cards (they have option value)
      value -= 2;
    }

    if (getCardAction(drawnCard.rank)) {
      // Bonus for keeping action cards
      value += 1;
    }

    // Component 4: Special card handling
    if (oldCard.rank === 'Joker' || oldCard.rank === 'K') {
      // Never swap away Jokers or Kings unless replacing with another
      if (drawnCard.rank !== 'Joker' && drawnCard.rank !== 'K') {
        value -= 10;
      }
    }

    // Component 5: Information gain
    if (!isKnown) {
      // Swapping unknown card gives us knowledge
      value += 1;
    }

    return value;
  }

  /**
   * Evaluate toss-in cascade potential
   */
  private evaluateTossInCascade(
    discardedRank: Rank,
    context: BotDecisionContext
  ): number {
    let value = 0;

    // Count matching cards in bot's hand
    const matchingCards = context.botPlayer.cards.filter(
      (c, i) =>
        context.botPlayer.knownCardPositions.includes(i) &&
        c.rank === discardedRank
    );

    if (matchingCards.length > 0) {
      // Cascade value: sum of card values
      const cascadeScore = matchingCards.reduce((sum, c) => sum + c.value, 0);

      // Multiplicative bonus for multiple cards
      const multiplier = 1 + matchingCards.length * 0.5;

      value = cascadeScore * multiplier * 2;
    }

    return value;
  }

  /**
   * Evaluate action card's strategic value
   */
  private evaluateActionCardValue(
    action: CardAction,
    card: Card,
    context: BotDecisionContext
  ): number {
    switch (action) {
      case 'peek-own':
        return this.evaluatePeekOwnValue(context);

      case 'peek-opponent':
        return this.evaluatePeekOpponentValue(context);

      case 'swap-cards':
        return this.evaluateJackSwapValue(context);

      case 'peek-and-swap':
        return this.evaluateQueenValue(context);

      case 'force-draw':
        return this.evaluateAceValue(context);

      case 'declare-action':
        return this.evaluateKingValue(context);

      default:
        return 0;
    }
  }

  /**
   * Evaluate peek-own (7, 8) value
   */
  private evaluatePeekOwnValue(context: BotDecisionContext): number {
    const unknownCards =
      context.botPlayer.cards.length -
      context.botPlayer.knownCardPositions.length;

    if (unknownCards === 0) return 0; // No value if we know everything

    // Information is extremely valuable
    // Base value: 5 points per unknown card (reduced by total unknowns for diminishing returns)
    const baseValue = 5 + unknownCards * 0.5;

    // Bonus: If we have few cards (close to end-game), information is critical
    if (context.botPlayer.cards.length <= 3) {
      return baseValue * 1.5;
    }

    return baseValue;
  }

  /**
   * Evaluate peek-opponent (9, 10) value
   */
  private evaluatePeekOpponentValue(context: BotDecisionContext): number {
    // Opponent information is valuable but less than own information
    const avgOpponentUnknown =
      context.allPlayers
        .filter((p) => p.id !== this.botId)
        .reduce((sum, p) => {
          const known = this.botMemory.getPlayerMemory(p.id).size;
          return sum + (p.cards.length - known);
        }, 0) /
      (context.allPlayers.length - 1);

    if (avgOpponentUnknown === 0) return 1; // Low value if we know everything

    // Base value: 3 points (less than peek-own)
    return 3 + avgOpponentUnknown * 0.3;
  }

  /**
   * Evaluate Jack (swap cards) value
   */
  private evaluateJackSwapValue(context: BotDecisionContext): number {
    // Jack is powerful: can swap our high cards for opponent's low cards
    let bestSwapValue = 0;

    // Find our highest known card
    let maxOwnValue = 0;
    for (let i = 0; i < context.botPlayer.cards.length; i++) {
      if (context.botPlayer.knownCardPositions.includes(i)) {
        maxOwnValue = Math.max(maxOwnValue, context.botPlayer.cards[i].value);
      }
    }

    // Find opponent's lowest known card
    let minOpponentValue = 20;
    for (const player of context.allPlayers) {
      if (player.id === this.botId) continue;

      const knownCards = this.botMemory.getPlayerMemory(player.id);
      for (const [_, memory] of knownCards) {
        if (memory.card) {
          minOpponentValue = Math.min(minOpponentValue, memory.card.value);
        }
      }
    }

    if (minOpponentValue < 20) {
      // We found a good swap opportunity
      bestSwapValue = (maxOwnValue - minOpponentValue) * 2;
    }

    // Base value: Jack is always useful for disruption
    return Math.max(4, bestSwapValue);
  }

  /**
   * Evaluate Queen (peek 2 + optional swap) value
   */
  private evaluateQueenValue(context: BotDecisionContext): number {
    // Queen combines information gain + swap potential
    const peekValue = this.evaluatePeekOpponentValue(context);
    const swapValue = this.evaluateJackSwapValue(context) * 0.7; // 70% of Jack value

    // Queen is extremely powerful
    return peekValue + swapValue + 2; // Bonus for flexibility
  }

  /**
   * Evaluate Ace (force draw) value
   */
  private evaluateAceValue(context: BotDecisionContext): number {
    // Ace is defensive: best when opponent is close to winning
    const botScore = this.calculateScore(context.botPlayer.cards);

    let maxThreat = 0;
    for (const player of context.allPlayers) {
      if (player.id === this.botId) continue;

      const oppScore = this.calculateScore(player.cards);
      const threat = botScore - oppScore;

      if (threat > 3 && player.cards.length <= 3) {
        // Opponent is a serious threat
        maxThreat = Math.max(maxThreat, threat);
      }
    }

    if (maxThreat > 0) {
      return 5 + maxThreat; // High value when defending
    }

    // Low value otherwise (Ace = 1 point, better to swap it)
    return 1;
  }

  /**
   * Evaluate King (declare action) value
   */
  private evaluateKingValue(context: BotDecisionContext): number {
    // King is extremely flexible: can use any action
    // Value: best of all action values + cascade potential

    let bestActionValue = 0;

    // Check each action
    const actions: CardAction[] = [
      'peek-own',
      'peek-opponent',
      'swap-cards',
      'peek-and-swap',
      'force-draw',
    ];

    for (const action of actions) {
      const value = this.evaluateActionCardValue(
        action,
        { id: 'temp', rank: 'K', value: 0, actionText: '', played: false },
        context
      );
      bestActionValue = Math.max(bestActionValue, value);
    }

    // Bonus: King can trigger toss-in cascades
    const cascadeBonus = this.evaluateTossInCascade('K', context);

    return bestActionValue + cascadeBonus + 3; // Bonus for flexibility
  }

  /**
   * Evaluate King declaration of specific rank
   */
  private evaluateKingDeclaration(
    rank: Rank,
    context: BotDecisionContext
  ): number {
    // Evaluate declaring this rank
    let value = 0;

    // Component 1: Toss-in cascade
    const cascadeValue = this.evaluateTossInCascade(rank, context);
    value += cascadeValue * 1.5; // High multiplier

    // Component 2: Action value (if declaring action card)
    const action = getCardAction(rank);
    if (action) {
      const actionValue = this.evaluateActionCardValue(
        action,
        {
          id: 'temp',
          rank,
          value: getCardValue(rank),
          actionText: '',
          played: false,
        },
        context
      );
      value += actionValue;
    }

    // Component 3: Card value (higher value cards are better to remove)
    value += getCardValue(rank) * 0.5;

    return value;
  }

  /**
   * Select best targets for action card
   */
  private selectBestActionTargets(
    action: CardAction,
    context: BotDecisionContext
  ): BotActionDecision {
    switch (action) {
      case 'peek-own':
        return this.selectPeekOwnTargets(context);

      case 'peek-opponent':
        return this.selectPeekOpponentTargets(context);

      case 'swap-cards':
        return this.selectJackTargets(context);

      case 'peek-and-swap':
        return this.selectQueenTargets(context);

      case 'force-draw':
        return this.selectAceTarget(context);

      default:
        return { targets: [] };
    }
  }

  /**
   * Select peek-own target (7, 8)
   */
  private selectPeekOwnTargets(context: BotDecisionContext): BotActionDecision {
    // Peek our most important unknown card
    // Priority: middle positions (more likely to be high-value initial peeks)

    const unknownPositions = context.botPlayer.cards
      .map((_, i) => i)
      .filter((i) => !context.botPlayer.knownCardPositions.includes(i));

    if (unknownPositions.length === 0) {
      return { targets: [{ playerId: this.botId, position: 0 }] };
    }

    // Prefer middle positions (heuristic: players tend to peek edge cards first)
    const middlePreference = unknownPositions.map((pos) => {
      const distanceFromMiddle = Math.abs(
        pos - context.botPlayer.cards.length / 2
      );
      return { pos, score: -distanceFromMiddle };
    });

    middlePreference.sort((a, b) => b.score - a.score);

    return {
      targets: [{ playerId: this.botId, position: middlePreference[0].pos }],
    };
  }

  /**
   * Select peek-opponent target (9, 10)
   */
  private selectPeekOpponentTargets(
    context: BotDecisionContext
  ): BotActionDecision {
    // Peek opponent's most valuable unknown card
    let bestTarget = { playerId: '', position: 0 };
    let bestScore = -Infinity;

    for (const player of context.allPlayers) {
      if (player.id === this.botId) continue;

      for (let pos = 0; pos < player.cards.length; pos++) {
        const knownCards = this.botMemory.getPlayerMemory(player.id);
        if (knownCards.has(pos)) continue; // Already know this card

        // Score: prefer opponents with fewer cards (more likely to call Vinto)
        const score = 10 - player.cards.length;

        if (score > bestScore) {
          bestScore = score;
          bestTarget = { playerId: player.id, position: pos };
        }
      }
    }

    return { targets: [bestTarget] };
  }

  /**
   * Select Jack swap targets
   */
  private selectJackTargets(context: BotDecisionContext): BotActionDecision {
    // Find best swap: our highest known card for opponent's lowest known card
    let bestSwap = {
      target1: { playerId: this.botId, position: 0 },
      target2: { playerId: '', position: 0 },
      value: -Infinity,
    };

    // Find our highest known card
    for (let i = 0; i < context.botPlayer.cards.length; i++) {
      if (!context.botPlayer.knownCardPositions.includes(i)) continue;

      const ourCard = context.botPlayer.cards[i];

      // Find opponent's lowest known card
      for (const player of context.allPlayers) {
        if (player.id === this.botId) continue;

        const knownCards = this.botMemory.getPlayerMemory(player.id);
        for (const [pos, memory] of knownCards) {
          if (!memory.card) continue;

          const swapValue = ourCard.value - memory.card.value;

          if (swapValue > bestSwap.value) {
            bestSwap = {
              target1: { playerId: this.botId, position: i },
              target2: { playerId: player.id, position: pos },
              value: swapValue,
            };
          }
        }
      }
    }

    return {
      targets: [bestSwap.target1, bestSwap.target2],
      shouldSwap: true,
    };
  }

  /**
   * Select Queen targets
   */
  private selectQueenTargets(context: BotDecisionContext): BotActionDecision {
    // Queen: peek 2 cards with highest expected swap value
    // Strategy: peek our high card + opponent's unknown card (likely to be good swap)

    let bestPeek = {
      target1: { playerId: this.botId, position: 0 },
      target2: { playerId: '', position: 0 },
      value: -Infinity,
    };

    // Find our highest known card
    let ourHighPos = 0;
    let ourHighValue = -Infinity;

    for (let i = 0; i < context.botPlayer.cards.length; i++) {
      if (!context.botPlayer.knownCardPositions.includes(i)) continue;

      const card = context.botPlayer.cards[i];
      if (card.value > ourHighValue) {
        ourHighValue = card.value;
        ourHighPos = i;
      }
    }

    // Find best opponent unknown card to peek
    for (const player of context.allPlayers) {
      if (player.id === this.botId) continue;

      for (let pos = 0; pos < player.cards.length; pos++) {
        const knownCards = this.botMemory.getPlayerMemory(player.id);
        if (knownCards.has(pos)) continue;

        // Score: unknown opponent cards have high expected value for swapping
        const score = ourHighValue; // Expected swap benefit

        if (score > bestPeek.value) {
          bestPeek = {
            target1: { playerId: this.botId, position: ourHighPos },
            target2: { playerId: player.id, position: pos },
            value: score,
          };
        }
      }
    }

    return {
      targets: [bestPeek.target1, bestPeek.target2],
      shouldSwap: false, // Decide after peeking
    };
  }

  /**
   * Select Ace target (force draw)
   */
  private selectAceTarget(context: BotDecisionContext): BotActionDecision {
    // Target opponent closest to winning
    let bestTarget = { playerId: '', position: 0 };
    let maxThreat = -Infinity;

    const botScore = this.calculateScore(context.botPlayer.cards);

    for (const player of context.allPlayers) {
      if (player.id === this.botId) continue;

      const oppScore = this.calculateScore(player.cards);
      const threat = (botScore - oppScore) * 2 + (5 - player.cards.length);

      if (threat > maxThreat) {
        maxThreat = threat;
        bestTarget = { playerId: player.id, position: 0 };
      }
    }

    return { targets: [bestTarget] };
  }

  // ========== Helper Methods ==========

  private initializeIfNeeded(context: BotDecisionContext): void {
    if (this.botId !== context.botId) {
      this.botId = context.botId;
      this.botMemory = new BotMemory(context.botId, this.difficulty);
    }

    this.updateMemoryFromContext(context);
  }

  private updateMemoryFromContext(context: BotDecisionContext): void {
    context.botPlayer.cards.forEach((card, position) => {
      if (context.botPlayer.knownCardPositions.includes(position)) {
        this.botMemory.observeCard(card, this.botId, position);
      }
    });

    context.opponentKnowledge.forEach((knownCards, opponentId) => {
      knownCards.forEach((card, position) => {
        this.botMemory.observeCard(card, opponentId, position);
      });
    });
  }

  private calculateScore(cards: Card[]): number {
    return cards.reduce((sum, card) => sum + card.value, 0);
  }
}
