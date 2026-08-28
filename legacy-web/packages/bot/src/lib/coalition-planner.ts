// Coalition planner for the final round (Vinto called).
//
// The coalition (every player except the Vinto caller) wins iff the LOWEST
// coalition hand total is strictly below the caller's total. Coalition members
// share full knowledge of their hands, so the final round is (almost) a
// full-information, single-agent problem: the only hidden information is the
// caller's un-peeked cards and the order of the draw pile.
//
// This module is a pure expectimax planner over the remaining coalition turns:
//   - the acting bot's own options are enumerated exhaustively,
//   - future coalition turns are searched with pruning (top-K options per draw),
//   - draws are integrated over the unseen-card distribution,
//   - toss-ins (including tossed J/Q/K actions) are resolved inside the model,
//   - the caller's total is a distribution built from pooled coalition knowledge.
// It is re-run at every decision point, so the "plan" adapts as cards are drawn
// and as the state changes.

import { Card, GameState, Rank, getCardValue } from '@vinto/shapes';
import { BotActionDecision } from './shapes';

// ============================================================================
// Public types
// ============================================================================

export interface PlanCard {
  id: string;
  rank: Rank;
  value: number;
  played: boolean;
}

export interface CoalitionMember {
  id: string;
  isBot: boolean;
  cards: PlanCard[];
}

export interface CoalitionPlanInput {
  vintoCallerId: string;
  actingPlayerId: string;
  /** All coalition members (every non-caller), in table order */
  members: CoalitionMember[];
  /** Coalition members whose full turns are still to come AFTER the current turn, in order */
  turnQueue: string[];
  /** Values of the caller's cards that the coalition knows */
  callerKnownValues: number[];
  /** Number of caller cards the coalition does not know */
  callerUnknownCount: number;
  /** Composition of cards not seen by the coalition (draw distribution) */
  unseenCounts: Partial<Record<Rank, number>>;
  /** Current top of the discard pile (null if empty) */
  discardTop: PlanCard | null;
}

export interface CoalitionActionTarget {
  playerId: string;
  position: number;
}

export interface CoalitionActionPlan {
  targets: CoalitionActionTarget[];
  shouldSwap?: boolean;
  declaredRank?: Rank;
}

export type CoalitionDrawnCardDecision =
  | { choice: 'discard' }
  | { choice: 'use-action'; action: CoalitionActionPlan }
  | {
      choice: 'swap';
      position: number;
      /** Declare the swapped-out card's rank to immediately play its action */
      declaredRank?: Rank;
    };

// ============================================================================
// Constants
// ============================================================================

const ALL_RANKS_LIST: Rank[] = [
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
  'Joker',
];

const DECK_COUNTS: Record<Rank, number> = {
  '2': 4,
  '3': 4,
  '4': 4,
  '5': 4,
  '6': 4,
  '7': 4,
  '8': 4,
  '9': 4,
  '10': 4,
  J: 4,
  Q: 4,
  K: 4,
  A: 4,
  Joker: 2,
};

/** Only these actions can change hand totals inside the coalition */
const COALITION_ACTION_RANKS: ReadonlySet<Rank> = new Set<Rank>([
  'J',
  'Q',
  'K',
]);

/** How many coalition turns after the current one are searched */
const MAX_LOOKAHEAD_TURNS = 2;
/** Options kept per drawn card at lookahead depth (index = depth) */
const PRUNE_WIDTH: number[] = [Infinity, 2, 1];
/** Root options that get a full lookahead (pre-ranked by immediate value) */
const ROOT_WIDTH = 16;
/** Tie-breaker weight: prefer a lower champion score even when P(win) is equal */
const SCORE_TIE_EPS = 0.001;

type Mode = 'full' | 'greedy';

// ============================================================================
// Input construction from engine state
// ============================================================================

function toPlanCard(card: Card): PlanCard {
  return {
    id: card.id,
    rank: card.rank,
    value: card.value,
    played: !!card.played,
  };
}

/**
 * Build the planner input from the authoritative game state.
 *
 * Coalition members share their hands with each other (the coalition leader UI
 * already reveals every coalition card), so every coalition card is treated as
 * known. The caller's cards are only known where some coalition member has
 * actually seen them.
 *
 * Returns null when not in a coalition final round.
 */
export function buildCoalitionPlanInput(
  state: GameState,
  actingPlayerId: string,
): CoalitionPlanInput | null {
  const callerId = state.vintoCallerId;
  if (state.phase !== 'final' || !callerId || actingPlayerId === callerId) {
    return null;
  }

  const caller = state.players.find((p) => p.id === callerId);
  if (!caller) return null;

  const members: CoalitionMember[] = state.players
    .filter((p) => p.id !== callerId)
    .map((p) => ({
      id: p.id,
      isBot: p.isBot,
      cards: p.cards.map(toPlanCard),
    }));

  // Pool everything the coalition knows about the caller's hand
  const knownCallerCardIds = new Set<string>();
  for (const p of state.players) {
    if (p.id === callerId) continue;
    const knowledge = p.opponentKnowledge?.[callerId]?.knownCards;
    if (!knowledge) continue;
    for (const card of Object.values(knowledge)) {
      if (card) knownCallerCardIds.add(card.id);
    }
  }
  const callerKnownValues: number[] = [];
  let callerUnknownCount = 0;
  const seenCallerIds = new Set<string>();
  for (const card of caller.cards) {
    if (knownCallerCardIds.has(card.id)) {
      callerKnownValues.push(card.value);
      seenCallerIds.add(card.id);
    } else {
      callerUnknownCount++;
    }
  }

  // Everything the coalition has seen is removed from the "unseen" pool
  const unseenCounts: Partial<Record<Rank, number>> = { ...DECK_COUNTS };
  const consume = (card: Card | PlanCard | undefined | null) => {
    if (!card) return;
    unseenCounts[card.rank] = Math.max(0, (unseenCounts[card.rank] ?? 0) - 1);
  };
  for (const m of members) m.cards.forEach(consume);
  for (const card of caller.cards)
    if (seenCallerIds.has(card.id)) consume(card);
  for (const card of state.discardPile) consume(card);
  consume(state.pendingAction?.card);

  // Remaining coalition turns: from the current turn owner forward until the caller
  const turnOwnerIndex =
    state.activeTossIn?.originalPlayerIndex ?? state.currentPlayerIndex;
  const turnQueue: string[] = [];
  const n = state.players.length;
  for (let step = 1; step < n; step++) {
    const p = state.players[(turnOwnerIndex + step) % n];
    if (p.id === callerId) break;
    turnQueue.push(p.id);
  }

  const top = state.discardPile.peekTop();

  return {
    vintoCallerId: callerId,
    actingPlayerId,
    members,
    turnQueue,
    callerKnownValues,
    callerUnknownCount,
    unseenCounts,
    discardTop: top ? toPlanCard(top) : null,
  };
}

// ============================================================================
// Search
// ============================================================================

type Hands = PlanCard[][];

interface Outcome {
  hands: Hands;
  discardTop: PlanCard | null;
}

interface ActionOutcome extends Outcome {
  plan: CoalitionActionPlan;
}

interface DrawnOutcome extends Outcome {
  decision: CoalitionDrawnCardDecision;
}

interface KingOutcome {
  hands: Hands;
  plan: CoalitionActionPlan;
  tossRanks: Rank[];
}

interface DrawOption {
  card: PlanCard;
  p: number;
}

function handScore(hand: PlanCard[]): number {
  let s = 0;
  for (const c of hand) s += c.value;
  return s;
}

function minScore(hands: Hands): number {
  let m = Infinity;
  for (const h of hands) m = Math.min(m, handScore(h));
  return m === Infinity ? 0 : m;
}

function isTakeableAction(card: PlanCard | null): card is PlanCard {
  return !!card && !card.played && COALITION_ACTION_RANKS.has(card.rank);
}

function shouldTossCard(card: PlanCard): boolean {
  // Tossing a positive-value card always lowers the score; a King (0) is
  // worth tossing for its declaration action.
  return card.value > 0 || card.rank === 'K';
}

/** Keeps the best item seen so far by value */
class Best<T> {
  item: T | null = null;
  value = -Infinity;
  offer(item: T, value: number): void {
    if (value > this.value) {
      this.value = value;
      this.item = item;
    }
  }
}

export class CoalitionSearch {
  private readonly memberIds: string[];
  private readonly memberIsBot: boolean[];
  private readonly memberIndexById = new Map<string, number>();
  private readonly queue: number[]; // member indices
  private readonly winProbCache = new Map<number, number>();
  private readonly memo = new Map<string, number>();
  private readonly callerKnownSum: number;
  private readonly callerUnknownDist: Map<number, number>; // sum -> probability

  readonly drawDist: DrawOption[];
  readonly rootHands: Hands;
  readonly rootDiscardTop: PlanCard | null;
  readonly actorIndex: number;

  constructor(input: CoalitionPlanInput) {
    this.memberIds = input.members.map((m) => m.id);
    this.memberIsBot = input.members.map((m) => m.isBot);
    input.members.forEach((m, i) => this.memberIndexById.set(m.id, i));
    this.rootHands = input.members.map((m) => [...m.cards]);
    this.rootDiscardTop = input.discardTop;
    this.actorIndex = this.memberIndexById.get(input.actingPlayerId) ?? -1;
    this.queue = input.turnQueue
      .map((id) => this.memberIndexById.get(id))
      .filter((i): i is number => i !== undefined);

    // Draw distribution
    let total = 0;
    for (const r of ALL_RANKS_LIST) total += input.unseenCounts[r] ?? 0;
    const counts: Partial<Record<Rank, number>> =
      total > 0 ? input.unseenCounts : DECK_COUNTS;
    if (total === 0) total = 54;
    this.drawDist = [];
    for (const r of ALL_RANKS_LIST) {
      const c = counts[r] ?? 0;
      if (c <= 0) continue;
      this.drawDist.push({
        card: {
          id: `draw-${r}`,
          rank: r,
          value: getCardValue(r),
          played: false,
        },
        p: c / total,
      });
    }

    // Caller total distribution
    this.callerKnownSum = input.callerKnownValues.reduce((a, b) => a + b, 0);
    this.callerUnknownDist = this.buildUnknownSumDistribution(
      input.callerUnknownCount,
    );
  }

  get hasActor(): boolean {
    return this.actorIndex >= 0;
  }

  // ---------------------------------------------------------------- caller model

  private buildUnknownSumDistribution(k: number): Map<number, number> {
    let dist = new Map<number, number>([[0, 1]]);
    for (let i = 0; i < k; i++) {
      const next = new Map<number, number>();
      for (const [sum, p] of dist) {
        for (const d of this.drawDist) {
          const s = sum + d.card.value;
          next.set(s, (next.get(s) ?? 0) + p * d.p);
        }
      }
      dist = next;
    }
    return dist;
  }

  /** P(caller total > m) — the coalition needs a strictly lower total */
  winProb(m: number): number {
    const cached = this.winProbCache.get(m);
    if (cached !== undefined) return cached;
    let p = 0;
    for (const [sum, prob] of this.callerUnknownDist) {
      if (this.callerKnownSum + sum > m) p += prob;
    }
    this.winProbCache.set(m, p);
    return p;
  }

  evaluate(hands: Hands): number {
    const m = minScore(hands);
    return this.winProb(m) - SCORE_TIE_EPS * m;
  }

  // ---------------------------------------------------------------- primitives

  private swapCards(
    hands: Hands,
    aM: number,
    aP: number,
    bM: number,
    bP: number,
  ): Hands {
    const next = hands.slice();
    const handA = next[aM].slice();
    const handB = next[bM].slice();
    const cardA = handA[aP];
    handA[aP] = handB[bP];
    handB[bP] = cardA;
    next[aM] = handA;
    next[bM] = handB;
    return next;
  }

  private removeCard(hands: Hands, m: number, p: number): Hands {
    const next = hands.slice();
    const hand = next[m].slice();
    hand.splice(p, 1);
    next[m] = hand;
    return next;
  }

  private replaceCard(
    hands: Hands,
    m: number,
    p: number,
    card: PlanCard,
  ): Hands {
    const next = hands.slice();
    const hand = next[m].slice();
    hand[p] = card;
    next[m] = hand;
    return next;
  }

  /**
   * Jack/Queen swap options: two cards from two different coalition members
   * (the caller can never be targeted). Includes the "no swap" option.
   * In greedy mode only the best option by immediate value is returned.
   */
  private enumerateSwaps(hands: Hands, mode: Mode): ActionOutcome[] {
    const results: ActionOutcome[] = [];
    const best = new Best<ActionOutcome>();
    const noSwap: ActionOutcome = {
      hands,
      discardTop: null,
      plan: { targets: [], shouldSwap: false },
    };
    if (mode === 'full') results.push(noSwap);
    else best.offer(noSwap, this.evaluate(hands));

    for (let i = 0; i < hands.length; i++) {
      for (let j = i + 1; j < hands.length; j++) {
        for (let a = 0; a < hands[i].length; a++) {
          for (let b = 0; b < hands[j].length; b++) {
            if (hands[i][a].value === hands[j][b].value) continue; // no-op swap
            const swapped = this.swapCards(hands, i, a, j, b);
            const outcome: ActionOutcome = {
              hands: swapped,
              discardTop: null,
              plan: {
                targets: [
                  { playerId: this.memberIds[i], position: a },
                  { playerId: this.memberIds[j], position: b },
                ],
                shouldSwap: true,
              },
            };
            if (mode === 'full') results.push(outcome);
            else best.offer(outcome, this.evaluate(swapped));
          }
        }
      }
    }

    if (mode === 'full') return results;
    return best.item ? [best.item] : [];
  }

  /**
   * King options: pick any coalition card, declare its (known) rank, the card
   * leaves that hand; if it is a J/Q its swap is played as well.
   * The resulting toss-in covers both 'K' and the declared rank.
   */
  private enumerateKingTargets(hands: Hands, mode: Mode): KingOutcome[] {
    const results: KingOutcome[] = [];
    const bestTarget = new Best<{ m: number; p: number; removed: Hands }>();

    for (let m = 0; m < hands.length; m++) {
      for (let p = 0; p < hands[m].length; p++) {
        const target = hands[m][p];
        const removed = this.removeCard(hands, m, p);
        if (mode === 'greedy') {
          // Stage 1: best card to remove by immediate value (incl. its toss-in)
          const v = this.evaluate(
            this.resolveTossIn(removed, ['K', target.rank]).hands,
          );
          bestTarget.offer({ m, p, removed }, v);
          continue;
        }
        const plan: CoalitionActionPlan = {
          targets: [{ playerId: this.memberIds[m], position: p }],
          declaredRank: target.rank,
        };
        const tossRanks: Rank[] = ['K', target.rank];
        const variants: Hands[] =
          target.rank === 'J' || target.rank === 'Q'
            ? this.enumerateSwaps(removed, 'full').map((o) => o.hands)
            : [removed];
        for (const variant of variants) {
          results.push({ hands: variant, plan, tossRanks });
        }
      }
    }

    if (mode === 'full') return results;
    if (!bestTarget.item) return [];

    // Stage 2 (greedy): if the removed card is a J/Q, play its swap greedily
    const { m, p, removed } = bestTarget.item;
    const target = hands[m][p];
    let finalHands = removed;
    if (target.rank === 'J' || target.rank === 'Q') {
      const [swap] = this.enumerateSwaps(removed, 'greedy');
      if (swap) finalHands = swap.hands;
    }
    return [
      {
        hands: finalHands,
        plan: {
          targets: [{ playerId: this.memberIds[m], position: p }],
          declaredRank: target.rank,
        },
        tossRanks: ['K', target.rank],
      },
    ];
  }

  /**
   * Resolve the toss-in window after card(s) of the given rank(s) hit the
   * discard pile: every coalition bot sheds matching cards; tossed J/Q/K
   * actions are played (greedily) and may cascade into further toss-ins.
   */
  private resolveTossIn(
    hands: Hands,
    ranks: Rank[],
  ): { hands: Hands; tossed: boolean } {
    const active = new Set<Rank>(ranks);
    let current = hands;
    let tossedAny = false;

    for (let round = 0; round < 6; round++) {
      const tossedActions: Rank[] = [];
      let next = current;

      for (let m = 0; m < next.length; m++) {
        if (!this.memberIsBot[m]) continue;
        const hand = next[m];
        if (!hand.some((c) => active.has(c.rank) && shouldTossCard(c)))
          continue;
        const kept: PlanCard[] = [];
        for (const c of hand) {
          if (active.has(c.rank) && shouldTossCard(c)) {
            if (COALITION_ACTION_RANKS.has(c.rank)) tossedActions.push(c.rank);
          } else {
            kept.push(c);
          }
        }
        if (next === current) next = current.slice();
        next[m] = kept;
      }

      if (next === current) break; // nothing tossed this round
      tossedAny = true;
      current = next;

      for (const rank of tossedActions) {
        if (rank === 'K') {
          const [king] = this.enumerateKingTargets(current, 'greedy');
          if (king) {
            current = king.hands;
            king.tossRanks.forEach((r) => active.add(r));
          }
        } else {
          const [swap] = this.enumerateSwaps(current, 'greedy');
          if (swap) current = swap.hands;
        }
      }
    }

    return { hands: current, tossed: tossedAny };
  }

  /** A card lands on the discard pile → toss-in window → resulting outcome */
  private afterDiscard(
    hands: Hands,
    discarded: PlanCard,
    played: boolean,
    tossRanks: Rank[],
  ): Outcome {
    const { hands: h, tossed } = this.resolveTossIn(hands, tossRanks);
    return {
      hands: h,
      // Tossed cards cover the discarded card, so it is no longer takeable
      discardTop: tossed ? null : { ...discarded, played },
    };
  }

  /** Outcomes of playing an action card (J/Q/K) by the acting member */
  enumerateActionUse(
    hands: Hands,
    card: PlanCard,
    mode: Mode,
  ): ActionOutcome[] {
    if (card.rank === 'K') {
      return this.enumerateKingTargets(hands, mode).map((o) => ({
        plan: o.plan,
        ...this.afterDiscard(o.hands, card, true, o.tossRanks),
      }));
    }
    if (card.rank === 'J' || card.rank === 'Q') {
      return this.enumerateSwaps(hands, mode).map((o) => ({
        plan: o.plan,
        ...this.afterDiscard(o.hands, card, true, [card.rank]),
      }));
    }
    return [];
  }

  /** Every way the acting member can finish the turn after drawing `card` */
  enumerateDrawnOptions(
    hands: Hands,
    actor: number,
    card: PlanCard,
    mode: Mode,
  ): DrawnOutcome[] {
    const options: DrawnOutcome[] = [];

    // 1. Discard the drawn card (unplayed → next member could take it)
    options.push({
      decision: { choice: 'discard' },
      ...this.afterDiscard(hands, card, false, [card.rank]),
    });

    // 2. Use its action (J/Q/K only — peeks/force-draw cannot help the coalition)
    if (COALITION_ACTION_RANKS.has(card.rank)) {
      for (const o of this.enumerateActionUse(hands, card, mode)) {
        options.push({
          decision: { choice: 'use-action', action: o.plan },
          hands: o.hands,
          discardTop: o.discardTop,
        });
      }
    }

    // 3. Swap it into own hand; the swapped-out card is discarded and,
    //    if it is a J/Q/K, declared so its action is played immediately.
    const hand = hands[actor];
    for (let p = 0; p < hand.length; p++) {
      const out = hand[p];
      const swapped = this.replaceCard(hands, actor, p, card);
      if (COALITION_ACTION_RANKS.has(out.rank)) {
        for (const o of this.enumerateActionUse(swapped, out, mode)) {
          options.push({
            decision: { choice: 'swap', position: p, declaredRank: out.rank },
            hands: o.hands,
            discardTop: o.discardTop,
          });
        }
      }
      options.push({
        decision: { choice: 'swap', position: p },
        ...this.afterDiscard(swapped, out, false, [out.rank]),
      });
    }

    return options;
  }

  /** Outcomes of taking the discard top (must be an unplayed J/Q/K) */
  enumerateTakeDiscard(
    hands: Hands,
    discardTop: PlanCard | null,
    mode: Mode,
  ): ActionOutcome[] {
    if (!isTakeableAction(discardTop)) return [];
    return this.enumerateActionUse(hands, discardTop, mode);
  }

  // ---------------------------------------------------------------- lookahead

  private memoKey(
    hands: Hands,
    discardTop: PlanCard | null,
    qi: number,
  ): string {
    let key = `${qi}#`;
    for (const h of hands) {
      key += h
        .map((c) => c.rank)
        .sort()
        .join(',');
      key += '|';
    }
    key += isTakeableAction(discardTop) ? discardTop.rank : '-';
    return key;
  }

  /**
   * Value of the coalition position at the start of turn `qi` in the queue,
   * `depth` turns after the acting bot's own turn.
   */
  private valueAtTurnStart(
    hands: Hands,
    discardTop: PlanCard | null,
    qi: number,
    depth: number,
  ): number {
    if (qi >= this.queue.length || depth > MAX_LOOKAHEAD_TURNS) {
      return this.evaluate(hands);
    }

    const member = this.queue[qi];
    if (!this.memberIsBot[member]) {
      // Human coalition members decide for themselves; assume no change.
      return this.valueAtTurnStart(hands, discardTop, qi + 1, depth);
    }

    const key = this.memoKey(hands, discardTop, qi);
    const cached = this.memo.get(key);
    if (cached !== undefined) return cached;

    const width = PRUNE_WIDTH[Math.min(depth, PRUNE_WIDTH.length - 1)];
    let best = -Infinity;

    // Take the discard
    for (const o of this.enumerateTakeDiscard(hands, discardTop, 'greedy')) {
      best = Math.max(
        best,
        this.valueAtTurnStart(o.hands, o.discardTop, qi + 1, depth + 1),
      );
    }

    // Draw from the deck (expectation over the unseen distribution)
    let drawValue = 0;
    for (const d of this.drawDist) {
      const options = this.pruneOptions(
        this.enumerateDrawnOptions(hands, member, d.card, 'greedy'),
        width,
      );
      let bestOption = -Infinity;
      for (const o of options) {
        bestOption = Math.max(
          bestOption,
          this.valueAtTurnStart(o.hands, o.discardTop, qi + 1, depth + 1),
        );
      }
      drawValue += d.p * bestOption;
    }
    best = Math.max(best, drawValue);

    this.memo.set(key, best);
    return best;
  }

  pruneOptions<T extends Outcome>(options: T[], width: number): T[] {
    if (options.length <= width) return options;
    return options
      .map((o) => ({ o, v: this.evaluate(o.hands) }))
      .sort((a, b) => b.v - a.v)
      .slice(0, width)
      .map((x) => x.o);
  }

  /** Value of an outcome of the acting bot's current turn (lookahead starts after it) */
  valueOfOutcome(o: Outcome): number {
    return this.valueAtTurnStart(o.hands, o.discardTop, 0, 1);
  }

  /** Best root option: pre-rank by immediate value, then full lookahead on the top few */
  pickBest<T extends Outcome>(
    options: T[],
  ): { option: T; value: number } | null {
    const best = new Best<T>();
    for (const o of this.pruneOptions(options, ROOT_WIDTH)) {
      best.offer(o, this.valueOfOutcome(o));
    }
    return best.item ? { option: best.item, value: best.value } : null;
  }
}

// ============================================================================
// Decision API (called by the bot adapter at each decision point)
// ============================================================================

/** Turn start: draw from the deck, or take an unplayed J/Q/K from the discard? */
export function planCoalitionTurnStart(input: CoalitionPlanInput): {
  action: 'draw' | 'take-discard';
} {
  const search = new CoalitionSearch(input);
  if (!search.hasActor) return { action: 'draw' };

  const take = search.pickBest(
    search.enumerateTakeDiscard(
      search.rootHands,
      search.rootDiscardTop,
      'full',
    ),
  );
  if (!take) return { action: 'draw' };

  // Expected value of drawing = integrate over the unseen distribution
  let drawValue = 0;
  for (const d of search.drawDist) {
    const best = search.pickBest(
      search.enumerateDrawnOptions(
        search.rootHands,
        search.actorIndex,
        d.card,
        'greedy',
      ),
    );
    drawValue += d.p * (best?.value ?? search.evaluate(search.rootHands));
  }

  return take.value > drawValue
    ? { action: 'take-discard' }
    : { action: 'draw' };
}

/** After drawing: use the action, swap it in (optionally declaring), or discard */
export function planCoalitionDrawnCard(
  input: CoalitionPlanInput,
  drawnCard: Card,
): CoalitionDrawnCardDecision {
  const search = new CoalitionSearch(input);
  if (!search.hasActor) return { choice: 'discard' };

  const best = search.pickBest(
    search.enumerateDrawnOptions(
      search.rootHands,
      search.actorIndex,
      toPlanCard(drawnCard),
      'full',
    ),
  );
  return best?.option.decision ?? { choice: 'discard' };
}

/**
 * Should the pending action card be played at all? (Only J/Q/K can help.)
 * Used for take-discard follow-ups and queued toss-in actions.
 */
export function shouldCoalitionUseAction(
  input: CoalitionPlanInput,
  card: Card,
): boolean {
  if (!COALITION_ACTION_RANKS.has(card.rank)) return false;
  const search = new CoalitionSearch(input);
  if (!search.hasActor) return false;

  const best = search.pickBest(
    search.enumerateActionUse(search.rootHands, toPlanCard(card), 'full'),
  );
  if (!best) return false;
  const skipValue = search.valueOfOutcome({
    hands: search.rootHands,
    discardTop: null,
  });
  return best.value > skipValue;
}

/**
 * Targets for the pending action card of the acting bot:
 *  - K → one target + the (known) rank to declare
 *  - J/Q → two targets from two different coalition members + shouldSwap
 *  - anything else → no targets (peeks/force-draw are skipped)
 */
export function planCoalitionActionTargets(
  input: CoalitionPlanInput,
  actionCard: Card,
): BotActionDecision {
  if (!COALITION_ACTION_RANKS.has(actionCard.rank)) return { targets: [] };
  const search = new CoalitionSearch(input);
  if (!search.hasActor) return { targets: [] };

  const best = search.pickBest(
    search.enumerateActionUse(search.rootHands, toPlanCard(actionCard), 'full'),
  );
  if (!best) return { targets: [] };

  const plan = best.option.plan;
  return {
    targets: plan.targets.map((t) => ({
      playerId: t.playerId,
      position: t.position,
    })),
    shouldSwap: plan.shouldSwap,
    declaredRank: plan.declaredRank,
  };
}

/** Positions the acting bot should toss in for the active toss-in ranks */
export function planCoalitionTossIn(
  input: CoalitionPlanInput,
  ranks: Rank[],
): number[] {
  const me = input.members.find((m) => m.id === input.actingPlayerId);
  if (!me) return [];
  const rankSet = new Set(ranks);
  const positions: number[] = [];
  me.cards.forEach((card, position) => {
    if (rankSet.has(card.rank) && shouldTossCard(card))
      positions.push(position);
  });
  return positions;
}
