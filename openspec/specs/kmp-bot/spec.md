<!--
  Canonical spec, synced from `migrate-to-kotlin-multiplatform` when it was archived.

  The bot, verified by rule-following rather than by decision parity — the reasoning is in
  docs/kotlin/GATES.md §6e.

  Deltas land here on archive, not before — a requirement in a change folder is a
  proposal, and a requirement here is what the game is held to.
-->
# kmp-bot

## Purpose

The three seats a person is not sitting in. The bot proposes actions through the same
validator a stranger would, reads only what its own seat has been shown, and speaks to its
coalition through the one channel a person can also speak on. It is verified by
rule-following and by whole games finishing, never by agreeing with another implementation.

## Requirements

### Requirement: Exactly one bot engine, chosen by tournament

The project SHALL have exactly one bot decision engine. Before any bot code is ported,
`tools/tournament.ts` SHALL run seeded self-play tournaments between the current `v1`
(MCTS) and `v2` (strategic) TypeScript bots (mixed 4-player tables, every difficulty,
at least 500 games per configuration) and report win rate, mean final score,
coalition win rate and decision latency; the losing bot SHALL be deleted from the
TypeScript codebase and only the winner SHALL be ported. `botVersion` SHALL be removed
from settings and `GameState`; recording readers SHALL ignore it if present.

#### Scenario: Tournament decides

- **WHEN** the tournament report is produced
- **THEN** the PR that removes the losing bot links the report and the remaining bot is the only `BotDecisionService` implementation in both stacks

#### Scenario: No version switch remains

- **WHEN** the settings UI (web or mobile) is inspected
- **THEN** there is no bot version selector; difficulty is the only bot setting

### Requirement: Deterministic bot components are ported exactly

The heuristics (`mcts-bot-heuristics`), evaluation helpers, opponent modeler, Vinto round
solver and the coalition planner SHALL be ported to `shared/bot` with identical
behaviour, and their TypeScript unit tests SHALL be ported 1:1 and pass.

#### Scenario: Coalition planner decisions match

- **WHEN** the coalition-planner scenario tests (Jack concentrates low cards, King removes the champion's high card, swap-and-declare, take-discard, toss-in sacrifice, never targets the caller) run against the Kotlin planner
- **THEN** every scenario yields the same decision as in TypeScript

#### Scenario: Toss-in heuristic parity

- **WHEN** `shouldParticipateInTossIn` is evaluated on the ported test cases
- **THEN** results are identical

### Requirement: Search with injectable randomness and budget

If the selected bot uses randomised search (MCTS), the Kotlin implementation SHALL accept an injected `Random` and a budget expressed as iterations
and/or wall-clock time; tests SHALL use a fixed seed and iteration budget so runs are
reproducible; production SHALL keep a wall-clock cap per difficulty as today.

#### Scenario: Reproducible test search

- **WHEN** MCTS runs twice with the same seed, budget and root state
- **THEN** it returns the same move

#### Scenario: Difficulty configs preserved

- **WHEN** the bot is created for `easy`/`moderate`/`hard`
- **THEN** iteration/time/exploration/rollout parameters equal the TypeScript `MCTS_DIFFICULTY_CONFIGS`

### Requirement: Search never blocks the UI thread

Bot decisions SHALL be `suspend` functions executed on a background dispatcher; the UI
SHALL remain responsive (frame time unaffected) while a bot thinks.

#### Scenario: Thinking off main

- **WHEN** a hard-difficulty bot performs a decision on device
- **THEN** the decision runs on `Dispatchers.Default` and the compose UI continues rendering animations

### Requirement: Same decision-service contract as TypeScript

`BotDecisionServiceFactory.create(difficulty)` SHALL return the single bot engine behind the
same `BotDecisionService` interface as the TypeScript one (turn decision, use action,
action targets, swap-after-peek, King declaration, toss-in participation, best swap
position, call Vinto), usable both inside `LocalGameSession` and on the server.

#### Scenario: Difficulty switch at runtime

- **WHEN** the player changes difficulty in settings mid-game
- **THEN** the adapter recreates the decision service exactly as the TypeScript adapter does

#### Scenario: Server-side bots

- **WHEN** an online room has bot seats
- **THEN** the server drives them with the same `BotAIAdapter` and decision service as the local session

### Requirement: Bots and people share one coalition channel

A coalition bot SHALL say everything it contributes to the coalition through the same typed
channel a person uses, and SHALL derive nothing about a teammate's hand from anything a person
could not have said.

`CoalitionPlanner` SHALL build its input from **declared claims and its own read cards alone**.
The pooling of `knownCallerCardIds` out of every coalition seat's private `opponentKnowledge`
SHALL be removed; a bot that has seen one of the caller's cards SHALL declare it, and the plan
SHALL then read it as a claim like any other.

A bot's claims SHALL come from its own memory rather than from the engine's record, so that on
lower difficulties they can be wrong.

#### Scenario: A bot has seen the caller's card

- **WHEN** a coalition bot holds knowledge of one of the Vinto caller's cards at the start of
  the final round
- **THEN** it declares that card publicly, and its own plan reads it as a claim rather than
  from `opponentKnowledge`

#### Scenario: A human teammate is not read through a back channel

- **WHEN** a coalition plan is built for a table with a human member
- **THEN** the human's undeclared cards are placeholders, exactly as an undeclared bot's are

### Requirement: A bot declares the belief it actually holds

A bot SHALL declare uncertainty it holds rather than collapsing it to a confident answer.

`BotMemory` already carries a per-card `confidence`, and `believedOwnCards` flattens it to a
single rank per position. A bot SHALL be able to say what that confidence supports: an exact
claim where it is sure, an unassigned pair where it holds two ranks without a reliable pairing,
and no claim at all where its memory has decayed past use.

A bot SHALL declare what it has seen of **any** seat's cards on the same terms it declares its
own — a teammate's as much as the Vinto caller's — so that nothing it knows reaches its plan
through a channel a person could not have spoken through.

When a bot's claim is contradicted by another seat, it SHALL answer from its own confidence:
withdrawing where its memory has decayed, and standing where it has not. It SHALL NOT defer
merely because the other speaker is a person, and SHALL NOT consult the real card to settle it.

A bot SHALL NOT read its own real cards to decide what to claim. Where memory is empty for a
position the table watched it read, the seat's public record SHALL remain the fallback, and a
partial memory SHALL stay partial — which is where a weaker bot's declarations go honestly
wrong.

#### Scenario: A bot is unsure which way round

- **WHEN** a bot's memory holds two ranks for two of its positions with low confidence in the
  pairing
- **THEN** it declares the pair unassigned rather than guessing an assignment

#### Scenario: A bot is contradicted

- **WHEN** a person claims a different rank for a card a bot has claimed
- **THEN** the bot withdraws if its own confidence there has decayed and holds its claim if it
  has not, without looking at the real card

#### Scenario: A bot reports a teammate's card

- **WHEN** a bot has seen one of its teammates' cards
- **THEN** it declares it publicly, rather than using it only inside its own plan

#### Scenario: A bot has forgotten

- **WHEN** a bot's confidence in a position has decayed past use
- **THEN** it claims nothing there rather than claiming the card it actually holds

### Requirement: Bots propose, and are never commanded

A bot SHALL evaluate any proposal addressed to it with its own decision service and SHALL
either perform it as its own action or decline it, and SHALL say which.

No seat SHALL be able to cause another seat to act. A proposal SHALL NOT be an action: it
carries no authority, is never reduced, and the only thing that reaches `GameEngine.reduce` is
the recipient's own accepted move through `ActionValidator`.

A bot SHALL be able to address a suggestion to any coalition member, person or bot, drawn from
its own plan — **as a card movement rather than as a move**.

A concrete move for a teammate's *coming* turn is not expressible, and that is a fact about the
game rather than a limit of the bot: their turn opens with a draw nobody can predict, so any
move named for it would be one that ignores what they draw. What the plan does know is which
cards should end up where, so that is what a bot asks for.

A bot SHALL compute such a suggestion from the **shared** picture alone — standing claims and
its own read cards. It SHALL NOT build a plan "as" a teammate, which would read that teammate's
private knowledge and reopen the back channel the claim model exists to close.

#### Scenario: A person proposes a swap to a bot

- **WHEN** a human coalition member proposes that a bot swap a named card
- **THEN** the bot evaluates it against its own best line, performs it or declines it, and
  answers either way — and in neither case does the human's proposal reach the engine

#### Scenario: A bot addresses the human

- **WHEN** a coalition bot's plan wants a card out of a human teammate's hand
- **THEN** it asks for that card out loud rather than waiting silently for it, and the
  teammate's move remains their own

#### Scenario: A bot does not plan with a teammate's eyes

- **WHEN** a bot works out what it wants a teammate to do
- **THEN** it uses only what the table has been told and what it has read itself, never the
  teammate's own record of their cards

### Requirement: A bot's speech is bounded

A bot SHALL speak at most once unprompted per turn, plus answers to anything addressed to it.

#### Scenario: Three bots in one coalition

- **WHEN** a final round runs with three coalition bots
- **THEN** no turn carries more than one unprompted sentence per bot

### Requirement: The coalition plans without a leader

`BotRunner` SHALL NOT nominate a coalition leader, SHALL NOT wait for one, and SHALL NOT read
`coalitionLeaderId` for any decision.

`CoalitionSearch.evaluate` SHALL continue to score the **lowest** coalition hand, whoever holds
it, which is the hand the round is scored against. Coordination between members SHALL come from
the declared picture they share: every bot declares before any coalition turn is played, so
each planner reaches the same target from the same public claims.

Bot play SHALL NOT be held pending any human decision at the start of the final round.

#### Scenario: A human is in the coalition

- **WHEN** Vinto is called and a person is among the coalition
- **THEN** the bots declare and play their turns without waiting for a nomination

#### Scenario: The coalition's target is agreed without a vote

- **WHEN** every coalition bot has declared and each plans its turn
- **THEN** each computes the same target hand from the same public claims, and the round's
  scoring compares the caller against the lowest coalition hand as before
