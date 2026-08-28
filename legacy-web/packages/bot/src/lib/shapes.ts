import { Card, GameState, Pile, PlayerState, Rank } from '@vinto/shapes';
import { OpponentModeler } from './opponent-modeler';

export interface BotDecisionContext {
  botId: string;
  botPlayer: PlayerState;
  allPlayers: PlayerState[];
  gameState: GameState;
  discardTop?: Card;
  discardPile: Pile; // Full discard pile history for tracking removed cards
  pendingCard?: Card;
  activeActionCard?: Card;
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
  // Coalition context (for final round)
  coalitionLeaderId?: string | null; // ID of the coalition leader (if in final round)
  isCoalitionMember?: boolean; // True if bot is part of coalition against Vinto caller
  // Opponent modeling - inferred beliefs about opponent hands
  opponentModeler?: OpponentModeler; // Optional service for tracking opponent beliefs
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
  actionDecision?: BotActionDecision; // Pre-computed action plan when taking from discard
}

/**
 * Predicted outcome of a complete turn after swapping
 * Used by the Turn Consequence Simulator
 */
export interface TurnOutcome {
  finalHandSize: number;
  finalKnownCards: number;
  finalScore: number;
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
    discardedRanks: [Rank, ...Rank[]],
    context: BotDecisionContext
  ): boolean;
  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null;

  // Game-ending decision
  shouldCallVinto(context: BotDecisionContext): boolean;
}
