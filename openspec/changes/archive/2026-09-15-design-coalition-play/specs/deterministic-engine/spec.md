# Spec delta: deterministic-engine

## ADDED Requirements

### Requirement: A claim may be about any card on the table

A coalition member SHALL be able to claim a rank for **any** card position — one of their own,
a teammate's, or the Vinto caller's — and the claim SHALL be attributed to the seat that spoke
it, not to the seat that owns the card.

A claim SHALL NOT be compared against the real card when it is made. Being wrong is a memory
problem, not a rules problem, and the engine SHALL treat a claim as speech rather than as
knowledge.

The seat boundary SHALL apply: a claim names its speaker, and a seat SHALL only speak for
itself. The Vinto caller SHALL be able to claim only their own cards, having no coalition to
inform.

Claims SHALL be visible to every seat, the caller included — the coalition confers out loud.

#### Scenario: A member reports what they saw of the caller's hand

- **WHEN** a coalition member who peeked the caller's third card claims a rank for it
- **THEN** the claim is recorded against the speaker, rides on every seat's view, and the real
  card is unchanged and still hidden

#### Scenario: A claim is spoken for another seat

- **WHEN** an action claims a rank while naming a speaker other than the sending seat
- **THEN** `ActionValidator` refuses it, on the same rule that refuses every other
  impersonation

#### Scenario: The corpus is unmoved

- **WHEN** all 50 recordings in `fixtures/recordings/` are replayed after the claim shape
  changes
- **THEN** every per-action state hash matches, because no recorded state materialises the
  field: it is `@EncodeDefault(NEVER)` and normalised back to `null` when emptied

### Requirement: A claim says only as much as the speaker knows

A claim SHALL be able to carry partial knowledge, and SHALL NOT force a speaker to state more
than they believe.

A claim SHALL name a set of positions and a set of ranks, and SHALL say how the two pair up. It
SHALL support, at minimum:

- one position holding one rank — the exact claim;
- **two positions holding two ranks with the pairing unknown** — "these are a King and an Ace,
  and I no longer remember which is which";
- one position holding one of several ranks, including a named class such as the low cards or
  the action cards;
- a hand holding a rank at an unnamed position — "there is a Joker somewhere in mine";
- a position the speaker has stopped claiming — which is information in itself, and SHALL clear
  any standing claim there rather than being ignored.

An unassigned claim SHALL be treated as **known in value and unknown in rank**: the coalition
may plan around what the cards are worth, and SHALL NOT be able to declare a rank or match a
toss-in from a pairing nobody knows.

No claim SHALL be compared against the real cards when it is made, whatever its shape.

#### Scenario: A pair is claimed without a pairing

- **WHEN** a member claims that two of their positions hold a King and an Ace without saying
  which holds which
- **THEN** both positions carry both candidate ranks, and neither is treated as a card of a
  named rank

#### Scenario: An unassigned pair is not a declarable rank

- **WHEN** a plan or a bot would declare a rank or match a toss-in using a position whose claim
  is unassigned
- **THEN** it is refused the rank, the pairing being unknown, while the position's value stays
  available for planning

#### Scenario: A claim is withdrawn

- **WHEN** a member says they no longer know what one of their cards is
- **THEN** the standing claim at that position is cleared, and teammates' plans built on it are
  no longer built on it

#### Scenario: The corpus is unmoved by the richer claim

- **WHEN** all 50 recordings are replayed after the claim shape is widened
- **THEN** every per-action state hash matches, the field never being materialised in a recorded
  state

### Requirement: Every claim carries the seat that made it

A claim SHALL be stored and shown against the seat that **spoke** it, not against the seat that
owns the card, and every seat SHALL be able to see who said what.

The storage SHALL follow: a card's standing claims are a set of attributed statements, not one
rank belonging to its owner. A claim SHALL keep its speaker when the card it describes moves.

A seat SHALL be able to replace or withdraw its **own** claim about any card, and SHALL NOT be
able to alter or withdraw another seat's.

#### Scenario: One member speaks about another's card

- **WHEN** a coalition member claims a rank for a teammate's card
- **THEN** the claim is shown as that member's statement, and the card's owner is not recorded as
  having said anything

#### Scenario: A claim survives the card moving

- **WHEN** a claimed card is swapped into another hand
- **THEN** the claim arrives with it, still attributed to whoever made it

#### Scenario: A seat withdraws only its own

- **WHEN** a seat withdraws a claim
- **THEN** only its own statement is cleared, and any other seat's claim about that card stands

### Requirement: Claims combine where they agree and are flagged where they do not

Standing claims about the same card SHALL be combined into one candidate set, and the way they
combine SHALL depend on whether they agree.

Where the claims are consistent, the candidate set SHALL be their **intersection**, so that two
partial claims make a narrower statement than either alone. Where the intersection is empty, the
card SHALL be marked **disputed** and its candidate set SHALL be the union of the claims.

A disputed card SHALL be treated as unknown in rank, on the same terms as an unassigned pair,
while remaining usable by value where its candidates permit.

A second claim by the **same** seat SHALL replace that seat's earlier claim rather than dispute
it: a player changing their mind is not a disagreement.

The system SHALL NOT decide which claimant is right, SHALL NOT compare either against the real
card, and SHALL NOT silently drop either.

#### Scenario: Two partial claims narrow a card

- **WHEN** one member claims a card is an action card and another claims it is a King or a Queen
- **THEN** the card's candidates become King and Queen, and no dispute is raised

#### Scenario: Two members disagree

- **WHEN** one member claims a King and another claims a 7 for the same card
- **THEN** the card is marked disputed, both claims stand with their speakers, and no rank may
  be declared or toss-in matched from it

#### Scenario: A member corrects themselves

- **WHEN** a member claims a rank for a card they have already claimed
- **THEN** their earlier claim is replaced and no dispute is raised

### Requirement: The coalition leader is retired as a live decision

`GameAction.SetCoalitionLeader` SHALL be refused at every **live door** — the room's
`applyAction` and the local session's `dispatch` — and `GameState.coalitionLeaderId` SHALL
remain `null` for the whole of any game dealt after this change.

Both SHALL be **retained as shape**: the field is inside every recorded state's canonical hash
and 42 recordings carry the action, so the type SHALL stay decodable, `ActionValidator` SHALL
keep accepting it, and `GameEngine.reduce` SHALL continue to apply it.

The refusal SHALL NOT live in `ActionValidator`. `GameEngine.reduce` validates before it
dispatches, so a rule there is a rule on the replay path, and it rejects the corpus. Both doors
SHALL read one shared predicate, so that a room and a solo game cannot disagree about which
moves exist.

Nothing SHALL read `coalitionLeaderId` to decide anything. The final round SHALL begin as soon
as Vinto is called, with no leader to wait for.

#### Scenario: A client sends the retired action

- **WHEN** any seat — the caller included — sends `SET_COALITION_LEADER` to a live game
- **THEN** the door refuses it, no state changes, and nothing is written to the room's log

#### Scenario: The refusal does not reach the engine

- **WHEN** `ActionValidator` is asked about a `SET_COALITION_LEADER` on a final-round state
- **THEN** it answers as it always did, because `GameEngine.reduce` consults it on the replay
  path and 42 recordings depend on that answer

#### Scenario: The corpus still replays it

- **WHEN** the 42 recordings carrying `SET_COALITION_LEADER` are replayed
- **THEN** each one reduces exactly as before and every state hash matches, because replay
  reduces without validating

### Requirement: Table talk is unlocked by the Vinto call

A coalition member SHALL be able to speak from the moment Vinto is called until the round is
scored, on their own turn and on anybody else's.

Speaking SHALL NOT be a turn: it SHALL cost no turn, consume no window, and be repeatable as
often as the speaker likes.

#### Scenario: The seat after the caller speaks on its own turn

- **WHEN** the seat immediately after the caller is on play and claims one of its own cards
- **THEN** the claim is accepted and the seat's turn is unaffected
### Requirement: Claims follow the card they describe

A claim describes one physical card, and SHALL move with it.

When the whole table watches a card move to another position — a Jack or a Queen swap — the
claim SHALL travel with it, because table talk about a card everyone tracked moving is still
about that card. When a card leaves the table face up, the claim about it SHALL be dropped.
When a removal renumbers the positions above it, claims SHALL be renumbered with their cards.

These rules SHALL be no-ops on a hand that has never claimed anything, so that no recorded
state materialises the field.

#### Scenario: A claimed card is swapped between two hands

- **WHEN** a Jack swaps a claimed card into another player's hand
- **THEN** the claim arrives with it, and the position it left carries whatever claim came the
  other way

#### Scenario: A claimed card is discarded

- **WHEN** a claimed card is swapped out and lands face up on the discard pile
- **THEN** the claim is dropped rather than left pointing at a card that is no longer there
