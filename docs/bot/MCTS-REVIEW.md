# MCTS review — what the search decides, and what it does not

**Date:** 2026-09-02
**Scope:** `shared/bot`, both branches: the ordinary round (`MctsBotDecisionService` and the
modules under it) and the final round (`CoalitionPlanner` / `CoalitionSearch`).
**Question asked:** is the search correct and well-built, and can it choose the best move without
hard-coded weights or special cases?

## 1. Summary

**The ordinary-round MCTS does not choose moves.** The tree applies every move to the bot's
*belief* state, in which the hidden cards are absent — determinization only happens when a
rollout starts — so every action move in the tree is a no-op. A Jack aimed at a Joker and a Jack
aimed at a 9 produce identical child states, and the search then picks between identical children
by whichever the seeded random happened to visit more. Measured across 40 seeds on a position with
one obviously right answer, the bot took the Joker 24 times, declined its own swap 8 times, and
gave a 10 for a 9 the rest. Draw-versus-take and the King's declaration flip the same way.

Everything that makes the bot play as well as it does comes from the parts *around* the search:
the move generator's shortlist (which only offers sensible candidates), the heuristics that answer
the peeks, `OutcomeSimulator`'s one-ply swap arithmetic, and the Vinto rule. Those are exactly the
parts built from hard-coded weights and special cases, and they are carrying the weight because the
search is not. The 5,000 iterations `hard` spends per decision cost 24× what `easy` spends and buy
nothing, which the committed baseline already shows: `hard`'s mean final hand is 9.45 against
`easy`'s 5.43.

**The coalition branch is sound and is the model to copy.** Its objective is the rule itself —
the probability that the caller's total exceeds the coalition's best hand — computed exactly over
the unseen cards, and its only constants are search budgets. It has four smaller defects, one of
which (a tossed-in Ace aimed at a teammate) is worth fixing before anything else.

The way to "best moves with no hard-coded weights" is not to tune the weights. It is to make the
search real — determinize per iteration and apply moves to the determinized state, deal the drawn
card, let the tree hold swap/discard/declare, score by the round's own outcome — after which the
evaluator, the swap weights, the rollout priorities and the Vinto thresholds can all go, in that
order. §5 sets it out.

## 2. Who answers what

Every question the engine asks a bot, and what actually answers it today.

| Decision | Solo | Coalition (final round) |
| --- | --- | --- |
| Draw or take the discard | Heuristic for Q/7/8 (`shouldAlwaysTakeDiscardPeekCard`); MCTS for everything else — **a coin flip, §3.2** | Expectimax (`planCoalitionTurnStart`) |
| Use the drawn action or not | Heuristic for Q/7/8; rule for A; MCTS for J/9/10/K — **always "use", §3.3** | Expectimax (`planCoalitionDrawnCard`) |
| Which of my cards to swap out | `OutcomeSimulator`, weights 100 / 50 / 15 (§3.8) | Expectimax |
| Where to aim a peek/swap | MCTS — **noise among the shortlist, §3.1** | Expectimax; **Ace by seat order, §4.4** |
| Swap after a Queen's peek | Rule: swap if own card is worth more | Expectimax |
| King: which rank to declare | MCTS — **noise, §3.1** | Expectimax |
| Toss in | Rule: any believed match | Rule: any known card worth > 0, or a King |
| Call Vinto | Score rule + worst-case solver + confidence thresholds + late-game escape (§3.9) | n/a |
| What to declare to the coalition | Memory | n/a |

The memory model (`BotMemory`) is the difficulty knob and is not in question here: making a weak
bot remember badly rather than choose badly is the right design, and it is orthogonal to the
search.

## 3. The ordinary round

### 3.1 The tree applies moves to a state with no hidden cards

`constructGameState` builds the root with `hiddenCards = emptyMap()` and the comment "left empty on
purpose: determinization deals them, once per simulation". `expand` then calls
`StateTransition.applyMove(node.state, move)` on that root. Every action handler reads the card it
acts on out of `hiddenCards` and returns when it is absent:

- `applyPeek` — `state.hiddenCards[key] ?: return`
- `applySwapAction` (Jack) — both cards `?: return`
- `applyPeekAndSwap` (Queen) — both lookups null, nothing written, no exchange
- `applySwap` (swap a drawn card in) — `displaced ?: return`
- King — `applyPeek`, same
- `removeCardsAt` (toss-in) — subtracts `hiddenCards[...]?.value ?: 0.0` from the score, so the
  card leaves the count and takes no points with it

Only the Ace (`applyForcedDraw`) and the turn bookkeeping do anything. So the children of any
target-choosing root — a Jack's twelve candidate pairs plus the skip, a King's declarations, a
9/10's targets — differ only in the discard-pile top and whose turn it is. `simulate` then
determinizes the *child*, i.e. a world in which the move was never made, and rolls it out. The
rewards backed up to sibling children are draws from the same distribution.

Measured (JVM, `Difficulty.HARD` so memory is exact, one seed per run):

| Position | Ideal | 40 seeds |
| --- | --- | --- |
| Jack in hand; own known 10, 10, 2; opponent's known Joker and 9 | give a 10, take the Joker, swap | 24 Joker (8 of them then *declined* the swap), 16 took the 9 |
| Unplayed Jack on the pile; own known 10, 10, 10; opponent's known Joker | take it | took it 21 times |
| King in hand; own known 10, 10, 3; opponent's known Queen | declare 10 (a pair) | 10 ×19, Queen ×21 |

Applying the same Jack move to a *determinized* copy of the root does what it should (bot's
estimate 22 → 11, memory at 0 becomes Joker), which is the whole finding in one line: the
transition is fine, it is being fed the wrong state.

**Why nobody noticed.** `SwapTargetSelectionTest` asserts that the two targets come from different
players and that the bot is one of them — true of every candidate the generator emits. No test
asserts that the *best* candidate wins. `SelfPlayGateTest` asks whether moves are legal, and they
are. `TournamentTest` pins the numbers but does not say whether they are good.

### 3.2 Draw and take-discard are indistinguishable

`applyDraw` decrements the deck and passes the turn; no card is dealt and no `pendingCard` is set.
`applyTakeDiscard` nulls the pile top and passes the turn; the action is never played. At the start
of a turn the search therefore compares two children that are the same position one seat later.
The plan `planFor` tries to read from the take-discard child is always null, because after
`advanceTurn` the most-visited grandchild belongs to the next seat and `extractActionPlan` rejects
it — so a Jack or King taken from the pile is aimed by a second, equally blind search later.

### 3.3 The search is never offered the alternative to "use"

`generateMoves` returns only `generateActionMoves(...)` when `pendingCard` is an action card. In
`shouldUseAction` the root's move list is therefore the targets and nothing else, so
`result.move.type != USE_ACTION` can only be true when there are no targets — which the code has
already checked one line earlier. Measured: a drawn Jack with a hand of known 2, 2, A and only a
King and a Queen known opposite — a swap that can only lose points — was "used" in 20 of 20 seeds.
The only thing that stops a bad Jack is the skip candidate, chosen by the same coin (§3.1).

`generateSwapPositionMoves` exists for the swap/discard alternative and is never called from
production code.

### 3.4 Rollouts in which nothing happens

Once the first ply is behind it, a rollout's move set is `DRAW`, sometimes `TAKE_DISCARD` once, and
`CALL_VINTO` when `assessVintoThreat` passes. Draw deals nothing, so a 15–30 ply rollout is a loop
that decrements the deck until it reshuffles. The reward is `evaluateNormalState` of the position
as it stood after the root move — unless somebody is five or more points ahead of the average, in
which case `selectGameEndingMove` calls Vinto and the reward collapses to 1 or 0 for "was the bot
lowest". Most of `RolloutPolicy` is unreachable:

- `certainTossIn` needs `TOSS_IN` moves, which only exist when the *root* is a toss-in window, and
  the window is answered by a rule, not the search;
- `selectScoreReductionMove` needs `SWAP` moves, which are never generated (§3.3);
- `selectDefensiveMove` needs `pendingCard == ACE`, which only the root can have, and the Ace is
  answered by a rule.

Dead outside tests: `generateSwapPositionMoves`, `getMovePriority`, `isLegalMove`,
`wouldMoveEndGame`, `updateScoreEstimates`, `evaluateTerminal`, `MctsNode.depth`,
`shouldExtractActionPlan`, `getMostLikelyVintoCaller`.

### 3.5 Every node maximises the root bot's reward

`backpropagate` adds one scalar reward to every node on the path and `selectBestChildUcb1` picks
the child with the highest mean, regardless of whose turn it is at that node. Opponents are modelled
as playing to help the bot. With the current transitions that mostly means an opponent "chooses"
not to call Vinto when the call would beat the bot; once moves have effects (§5) it would mean an
opponent choosing to hand over their Joker. A multi-player search either keeps a reward per seat and
maximises the mover's own (max^n), or treats the root's opponents as one adversary (paranoid). Either
is standard; neither is a weight.

### 3.6 Scores mix two bases

Root scores are `estimatePlayerScore` — known cards plus the average unseen value. After
determinization `hiddenCards` holds a concrete deal, and `removeCardsAt`/`exchange` subtract
concrete values from the expected-value score. `updateScoreEstimates` exists to rebase the scores on
the deal and is not called. Once the tree works on determinized states this matters: a rollout's
terminal comparison would be between hands priced in different currencies.

### 3.7 Ties and terminal states

`applyCallVinto` awards the round to `minByOrNull { score }`, which on a tie is the earlier seat;
the rule gives a tie to the caller. `evaluateTerminalState` in coalition mode counts any non-caller
"winner" as a coalition win, so a tied final total is scored as a loss for the caller. Small, but it
is the one place the search's objective touches the actual rule, and it gets it wrong.

### 3.8 The hard-coded weights, and which are doing the search's job

Constants that are honest search budgets — iterations, exploration constant, rollout depth,
`SHORTLIST`, `MAX_SEARCH_TURNS` — are fine and every MCTS has them. The rest stand in for judgement
the search should be producing:

| Where | What | What it is standing in for |
| --- | --- | --- |
| `StateEvaluator` | 0.3 / 0.25 / 0.2 / 0.15 / 0.1 over five components; spans of 30, 25, 40; per-rank action values 15 / 10 / 6 / 4 / 3; cascade 3, pair bonuses 5 and 10 | An outcome. The game has one: round points, +3 / −1 / 0 |
| `OutcomeSimulator` + `SwapWeights` + `CardProtection` | knowledge 100, hand size 50, score 15; Joker ×3 ×100, King ×2.5 ×100; general ×10; peek bonus 3, Queen bonus 2, King 4 + 3 + 2 | Which card to swap out — a one-ply choice the tree can make once it holds swap moves |
| `RolloutPolicy` | 5-point margin, 0.75 peek probability, hand ≤ 2, ≥ 9 expensive, ≤ 3 cheap | A rollout policy; mostly unreachable (§3.4) |
| `MoveGenerator.assessVintoThreat` | 5, +3, +5, −3; threat 1 / +2 / +1 per action | Whether to offer `CALL_VINTO` to the search at all |
| `VintoCallWiring` + `VintoRoundSolver` | confidence 0.55 / 0.6, max score 4, 12 laps; swap benefit 5, top-30 % percentile, confidence 0.3 + 0.65 | P(win | call), §3.9 |
| `BotHeuristics` | ≥ 8 worth swapping for, hand ≤ 3 vulnerable, gap 3 | Whether to play an Ace |
| `Determinization.getStrategicProbabilityWeight` | 2.0 … 0.5 per rank | A prior over unseen cards |
| `OpponentModeler` | readiness ±0.1 / 0.15 / 0.05 / 0.7; score 10 / 30; blend 0.7 / 0.3; initial 25 | Nothing consumes `vintoReadiness` outside its own tests |

Two of these deserve a specific note.

**`SwapWeights.KNOWLEDGE = 100` makes the bot swap any drawn card into an unread slot.** Swapping
into an unknown position scores +100 for knowledge and −15 × (drawn value − expected unseen value)
for points, with no protection penalty because the displaced card is unbelieved. Discarding scores
0. So the swap wins whenever the drawn card is worth less than about the expected value plus 6.7,
i.e. for every card in the deck: a drawn 10 that reaches this decision (one the search did not
"use" as a peek) goes face-down into a blind slot rather than onto the pile. The comment defends knowledge-first because "a bot that does not know its own hand cannot
call Vinto" — but that is a constraint the Vinto rule imposes on itself (§3.9), not a property of
the game, and the weight is paying for it in points.

**The determinization prior is a guess dressed as an inference.** "Opponents keep good cards" is
plausible mid-game and wrong at the deal, and the weights (Joker 2.0, Queen 1.8, 2–4 at 0.5) were
not fitted to anything. The honest prior is the card-count distribution the memory already
maintains (`getCardDistribution`), with the `OpponentModeler`'s min/max constraints on top; that is
what the coalition branch uses and it needs no table.

### 3.9 Calling Vinto: a worst case with an escape hatch

`shouldCallVinto` requires the whole hand believed, then a believed total ≤ 0 unless a solver with
enough "confidence" vetoes, or ≤ 4 with more confidence and no veto, or — after twelve laps — merely
the lowest expected hand at the table. The solver assumes every unseen opponent card is drawn from
the best 30 % of the pool and every swap action saves 5 points. The twelve-lap rule exists because
that rule was too strict to end games; the ≤ 4 rule exists because ≤ 0 was too strict to end games.
Each special case is a patch on the previous one.

The rule's own asymmetry says what the decision actually is. A call scores +3 if the caller finishes
at or below the coalition's best hand and −1 otherwise, so against "nothing happens" the break-even
is P(win) = 0.25, and the real comparison is against the expected value of *not* calling — waiting
a turn, or an opponent calling first. That is a probability the code already knows how to compute:
`CoalitionSearch.buildUnknownSumDistribution` convolves unseen cards into a distribution over
totals, and `winProb` reads it off. Run from the prospective caller's side — my believed total,
against each opponent's known cards plus a distribution over their unseen ones, with their one
remaining turn modelled the way the coalition search models it — it yields P(win) with no
percentile, no swap-benefit constant and no confidence scale. The full-hand-known gate then becomes
unnecessary too: an unread own card is one more card in the convolution.

### 3.10 The Ace

Solo, `aceTarget` takes the search's target (noise) or, failing that, the first player in seat order
who is not the bot. Whether to play it at all is `shouldUseAceAction`'s three thresholds. The Ace is
the one action whose value is genuinely about the opponents' state — who is closest to calling — and
a working search would price it from the rollouts.

## 4. The final round

`CoalitionSearch` is the part of this module that answers the question the way it should be
answered, and it is worth saying why before listing what is wrong with it.

- **Its objective is the rule.** `evaluate(hands) = P(caller's total > coalition's best) − ε·best`.
  The caller's unseen cards are convolved into an exact distribution, the coalition's hands are
  shared, the deck is a distribution over draws. There is no evaluator, no weights, and the only
  constants are budgets (`ROOT_WIDTH`, `PRUNE_WIDTH`, `MAX_LOOKAHEAD_TURNS`, `MAX_TOSS_IN_ROUNDS`)
  and a tie-break epsilon.
- **The caller's protection is structural.** The caller is not in `rootHands`, so no target can
  name their card. `CoalitionFinalRoundTest` and `CoalitionHardScenariosTest` then test outcomes —
  "the coalition moves a Joker into a teammate", "a worthless swap is made purely to open the
  champion's window" — which is the kind of test §3.1 was missing.
- **It trusts claims and pays for wrong ones honestly** (`DECLARE_CARDS`, `known = false`
  placeholders), which is the correct model of table talk.

What is wrong:

### 4.1 A tossed-in Ace is aimed at a teammate — possibly the champion

`shouldTossCard` sheds any known card worth more than zero, an Ace included. The engine queues a
tossed action card and later asks its owner for a target (`TossInHandlers`, the queued-action
branch). `planCoalitionActionTargets` returns no targets for an Ace, so `BotRunner.aceTarget` falls
through to "the first player who is not me and not the caller" — a coalition member, chosen by seat
order, who then draws a penalty card. If that member is the champion the coalition has just added a
card to the one hand that counts. `MoveGenerator.generateForceDrawMoves` knows to avoid the
champion, but the coalition never consults it. Either the Ace should not be tossed in the final
round when its action would land on a teammate, or the queued Ace should be abandoned rather than
aimed; the planner should decide, since it already knows which hand is the champion.

### 4.2 Peeks are never used, though the plan has unread cards

`COALITION_ACTION_RANKS` is Jack, Queen, King, on the reasoning that "a peek reveals a card the
coalition already shares". It does not: the plan carries `known = false` placeholders for the acting
member's own unread positions and for teammates' undeclared ones. A 7 or 8 turns one of the actor's
placeholders into a card that can then be tossed or declared; a 9 or 10 does the same for a
teammate's. Both are legal (the caller's cards are the only ones off limits) and both can be worth
more than discarding the card, and neither is in the option set.

### 4.3 Take is searched in full, draw is searched greedily

`planCoalitionTurnStart` values taking the discard with `SearchMode.FULL` and each drawn card's
reply with `SearchMode.GREEDY`, then compares the two. A maximum over more options is never smaller,
so the comparison leans toward taking. Same mode on both sides, or the draw side widened to
`ROOT_WIDTH`, removes the bias at the cost of a few milliseconds.

### 4.4 Approximations worth knowing, not fixing yet

- The caller's unseen total is convolved with replacement from the draw distribution, so two
  unseen cards can both "be" the last Joker. Exact sampling without replacement is a small change
  to `buildUnknownSumDistribution` and only matters when the pool is nearly exhausted.
- Human members are assumed to do nothing on their turn. Conservative, and the right default.
- Swaps that move no points are skipped (`swapsBetween`), which is correct for the objective and
  would need revisiting only if a swap's *information* value were modelled (§4.2).

## 5. What "best moves, no hard-coded weights" looks like

In order. Each step removes a family of constants because the search now produces what the
constants were standing in for, and each is gated by a test that would have caught §3.1.

**0. Gate first.** Turn the three probe positions in §3.1 into tests that assert the right answer
in ≥ 38 of 40 seeds (`aJackTakesAKnownJokerOverAKnownNine`, `anUnplayedJackIsTakenWhenAJokerIsOnOffer`,
`aKingDeclaresItsOwnPair`). They fail today. Add a mixed-difficulty table to `TournamentTest` so the
difficulties can be ranked at all (`docs/kotlin/README.md` §6k notes a homogeneous table cannot).

**1. Make the tree search a determinized world.** This is the standard information-set MCTS shape
(Cowling, Powley & Whitehouse 2012): at the start of each iteration, determinize the *root*, then
select down the tree replaying each node's move on that determinized state, expand, roll out, back
up. Tree nodes stay keyed on the belief (the information set); only the state carried down an
iteration is concrete. `StateTransition` needs no change for this — it already does the right thing
on a determinized state, as the last line of Probe A shows. `updateScoreEstimates` gets called
after determinization so §3.6 goes away. This step alone makes §3.1 pass.

**2. Deal the drawn card, and put the whole turn in the tree.** `DRAW` becomes a chance node over
`getCardDistribution` (the memory's own count of what is unaccounted for — no strategic-weight
table), and its children are the real choices: use the action, swap into position *p* (with or
without declaring the displaced card), discard. `generateSwapPositionMoves` already generates them.
`TAKE_DISCARD` sets `pendingCard` and lets the action be aimed one ply deeper. Now the search is
asked "draw or take", "use or swap", and "swap where" as one question with one answer, and
`OutcomeSimulator`, `SwapWeights`, `CardProtection` and the Q/7/8 heuristics are deleted rather than
tuned. The Joker-protection property is then a test of the search, which is what
`JokerProtectionTest` already claims to be.

**3. Score by the game's outcome.** Roll out to a Vinto call (the rollout policy can keep the
"call when clearly ahead" rule; it is a policy, not an evaluation) and reward the round points the
rule awards, mapped to [0, 1], per seat, with the tie to the caller. Where the depth budget runs out
first, fall back to the expected hand-total gap — one term, no weights. `StateEvaluator` and
`EvaluationHelpers` go. Back up a reward vector and let each node maximise its mover's entry (§3.5).

**4. Call Vinto by expected value.** P(win | call now) from the convolution machinery in
`CoalitionSearch`, run from the caller's side with one modelled turn per opponent; compare
3p − (1 − p) with the value of waiting from the same search. `VintoCallWiring`, the percentile, the
swap benefit, the confidence scale and the full-hand gate go. Keep the "not before everyone has had
two turns" rule if the product wants it; that is a pacing decision, and it should say so.

**5. Fix the coalition's four items** (§4.1 first — it is a real bug, not a strength issue).

**6. Then re-tune the budgets** against the tournament, because at that point `hard`'s 5,000
iterations will be buying something and the three difficulties can be set by search effort as well
as by memory.

What stays, and should: the memory model and its difficulty table (it is the difficulty), the move
generator's *rules* (legal targets, two different players, never the caller), the shortlists as a
progressive-widening order rather than a hard cap, the seeded `Random`, the iteration-only budget
for replayability, and the coalition planner as it is.

### Cost and risk

Steps 1–3 are inside `shared/bot` and touch no rule: `ActionValidator` still checks every proposed
action and `SelfPlayGateTest` still proves games end. The corpus is untouched (the bot is verified by
rule-following, not decision parity — `docs/kotlin/README.md` §6e). The baseline in
`fixtures/bot/self-play-baseline.json` will move, deliberately, and the commit that moves it should
say which way. Per-decision cost will rise with step 2's chance node; the deck distribution has at
most fourteen ranks, so it is a fourteen-way branch at draw nodes, and the iteration budgets are
there to absorb it.

## Appendix A — the probes

Run on the JVM against `MctsBotDecisionService(Difficulty.HARD, Random(seed))`, seeds 1–40, a
two-seat table with a 30-card deck and turn 9. Positions are built with the `commonTest` helpers
(`testPlayer`, `testState`, `botContext`) exactly as `SwapTargetSelectionTest` builds its own; the
bot's own cards are all known and the opponent's known cards are passed through
`opponentKnowledge`.

| Probe | Setup | Result |
| --- | --- | --- |
| A | `StateTransition.applyMove` with a Jack swapping own position 0 (known 10) for the opponent's position 0 (known Joker), on a root with no `hiddenCards` | bot score 22.0 → 22.0, memory at 0 still 10, opponent 11.0 → 11.0. On `determinize(root)`: 22.0 → 11.0, memory at 0 → Joker |
| B | Jack pending, own known 10, 10, 2; opponent's known Joker at 0 and 9 at 1 (plus two unread); `selectActionTargets` | 11× (10 ↔ Joker, swap), 8× (10 ↔ Joker, **no swap**), 5× (other 10 ↔ Joker, swap), 9× and 7× (a 10 ↔ 9, swap) |
| C | Jack drawn (`CHOOSING`), own known 2, 2, A; opponent's known King and Queen; `shouldUseAction` | true in 20 / 20 |
| D | Unplayed Jack on the pile, own known 10, 10, 10; opponent's known Joker; `decideTurnAction` | `TAKE_DISCARD` in 21 / 40 |
| E | King pending, own known 10, 10, 3; opponent's known Queen; `selectKingDeclaration` | 10 ×19, Queen ×21 |

## Appendix B — code that only tests reach

`MoveGenerator.generateSwapPositionMoves`, `MoveGenerator.getMovePriority`,
`MoveGenerator.isLegalMove`, `StateTransition.wouldMoveEndGame`,
`StateTransition.updateScoreEstimates`, `StateTransition.evaluateTerminal`, `MctsNode.depth`,
`shouldExtractActionPlan`, `OpponentModeler.getMostLikelyVintoCaller` and the `vintoReadiness`
machinery behind it, and the three unreachable branches of `RolloutPolicy` (§3.4). Steps 1–3 above
either use them or delete them; none should survive as-is.

## 6. What was done — 2026-09-02

Steps 0–5 above landed the same day, in two commits on this branch. The order was kept; each
step is described by what it deleted.

**The search is an information-set MCTS.** `MctsBotDecisionService.search` samples one world
per iteration (`determinize`: hands *and* a deck order, uniform over what the memory has not
accounted for, narrowed by the opponent modeler's bounds), descends the shared tree applying
each node's move to that world, expands once, plays the rest out and backs up a reward vector.
`MctsNode` keys children on the move, keeps an availability count per child, and selects by
UCB over the children legal in the current world from the *mover's* entry of the reward
(§3.5). `hiddenCards` is never empty when a move is applied, which is the whole of §3.1.

**The whole turn is in the tree.** `DRAW` deals the top of the sampled deck and waits; the
replies carry the rank they were about (`MctsMove.cardInPlay`), so a drawn 10 and a drawn 2 are
different information sets. A drawn card may be played (with its targets, or a King's target and
declaration, in one move), swapped into any position — a known action card swapped out is
declared and borrowed, as the runner plays it — or discarded. `TAKE_DISCARD` sets the card in
play as committed. The turn ends with the Vinto question, a two-way node. Deleted:
`OutcomeSimulator`, `SwapWeights`, `CardProtection`, `BotHeuristics` (the Q/7/8 and Ace rules),
`ActionPlanning`; the generator's dead `generateSwapPositionMoves` became the live swap moves.

**Rollouts play by card values** (`RolloutPolicy`): trade the card in play for the dearest card
the mover can name when that sheds points, otherwise play an action worth playing, otherwise
put a cheap card into a blind slot or discard; call Vinto when the hand is lowest at the table
and its owner knows it. Opponents are assumed to know their own hands; the bot acts only on
what it remembers, which is where a peek's value comes from.

**The reward is the round's points** (`Outcome.kt`): once somebody has called, +3 / 0 / −1
per seat mapped onto 0–1 with the tie to the caller; before a call, where the hand stands
between the lowest and highest at the table. Deleted: `StateEvaluator`, `EvaluationHelpers`.

**Vinto is a move.** `shouldCallVinto` searches the end-of-turn node and calls when the call's
child is the most visited. Deleted: `VintoCallRule`, `VintoRoundSolver`, `VintoCallWiring`, the
full-hand gate. What stays is the opening rule (no call before everyone has had two turns),
which is pacing and says so.

**Coalition.** A tossed-in Ace is put down rather than aimed at a teammate
(`BotRunner.actionTargetAction`); 7/8/9/10 are in the planner's option set as a chance node over
what the placeholder turns out to be (`CoalitionSearch.enumeratePeeks`); take-versus-draw is
searched in full on both sides; the caller's unseen total is convolved without replacement.

### What the gates say

`MctsDiscriminationTest` (JVM, twelve seeds each, at least eleven must agree):

| Position | Old search | New search |
| --- | --- | --- |
| Jack: give a 10 for a known Joker rather than a known 9, and swap | 11 / 40 | 12 / 12 |
| Jack that can only lose points: not traded | 0 / 20 | 12 / 12 |
| Unplayed Jack on the pile with a Joker on offer: take it | 21 / 40 | 12 / 12 |
| King: declare the own pair rather than a rival's single Queen | 19 / 40 | 12 / 12 |
| A drawn 2 goes in over the known 10, never the known Joker | — | 12 / 12 |
| A drawn 10 never goes into a hand of 2, 3, 4 | — | 12 / 12 |
| The most visited root child is also the best-scoring one | — | 12 / 12 |

One of the review's own probes was wrong, and the search said so: with three known 10s, a
declared swap-out and the toss-in it opens shed all three, which beats trading one for the
Joker. The test now holds a hand with no pair.

`SelfPlayGateTest`, `CoalitionFinalRoundTest`, `CoalitionHardScenariosTest` and the client's
`FinishesTest` and `RecordingRoundTripTest` are green unchanged. Two toss-in integration windows
moved into the opening, because a bot on twelve points against an unread hand now calls Vinto
in them, and that is the right call.

### The baseline, regenerated

`fixtures/bot/self-play-baseline.json` is version 2 and was regenerated with
`-Ptournament=write`. The homogeneous tables, before and after:

| Difficulty | Mean final hand | Mean actions | Caller won |
| --- | --- | --- | --- |
| easy | 5.43 → 8.20 | 347 → 249 | 9 → 9 of 12 |
| moderate | 5.47 → 9.04 | 282 → 248 | 8 → 8 of 12 |
| hard | 9.45 → 7.83 | 280 → 267 | 7 → 10 of 12 |

Read the first column with care, because it is the one that looks like a regression and is
not. Rounds are a quarter to a third shorter: the caller now calls when the call's expected
value beats playing on, and it is still winning three times in four — against a break-even of
one in four under +3 / −1. The old bot only ever called on a hand it could prove safe, or
after twelve laps, so the table had another hundred actions to shed cards into before anybody
ended it. A homogeneous table's mean hand says how long the round ran, not who played better
(`docs/kotlin/README.md` §6k said as much), which is why the baseline now has a second table.

**The mixed table** — easy, moderate, hard, hard in rotating chairs, twelve seeds — is the one
that ranks the difficulties, on the rule's own verdict:

| Difficulty | Seats | Mean round points | Mean final hand | Finished lowest |
| --- | --- | --- | --- | --- |
| easy | 12 | 0.75 | 14.50 | 1 |
| moderate | 12 | 1.75 | 6.58 | 7 |
| hard | 24 | 0.54 | 9.91 | 5 |

Easy is last by every measure, which is the memory model doing its job: a bot that records
less than half of what it sees finishes with twice the hand. Moderate and hard are not
separated the way the search budgets would predict — moderate's 2,000 iterations do better
than hard's 5,000 here — and on twelve games that gap is about two standard errors, which is
suggestive and not a result. It is recorded rather than tuned: setting budgets against twelve
games would be the same mistake this whole change exists to remove, in a different costume.
Two things are worth checking with a bigger run before anything is changed: whether it holds at
all, and whether the two `hard` seats — which sit in coalition against each other more often
than any other pair — are paying for each other's calls.

### What is left

- **Rollout policy is a policy.** It plays by card values and has no weights, but it is still
  hand-written; a search that reached terminal states without it would need none, and that is
  the next thing to try if the budget allows.
- **Opponents are assumed to know their own hands** in the sampled world. Modelling what an
  opponent has actually seen — the engine records it — would sharpen the coalition's play in
  particular.
- **Difficulty budgets** (iterations 500 / 2,000 / 5,000, exploration 0.7, rollout depth
  15 / 20 / 30 plies) are the constants that remain, and they are search budgets rather than
  judgement. The mixed table is the tool for setting them.
