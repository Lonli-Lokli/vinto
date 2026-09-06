# Spec delta: kmp-game-client

## ADDED Requirements

### Requirement: The coalition talks in a phrasebook, never in typed text

The talk channel SHALL carry **typed values only**, drawn from a closed vocabulary, and SHALL
be rendered in the reader's own locale by the same machinery every other sentence in the app
uses. There SHALL be no text input anywhere in it.

The vocabulary SHALL cover, at minimum: claims about a card, proposals of a move,
announcements of one's own intent, answers (yes, no, wait, that leaves us worse off), and
assessments of one's own hand.

Two players with no language in common SHALL be able to agree a move, because nothing crosses
the wire as words.

#### Scenario: Two locales confer

- **WHEN** one player's app is in Belarusian and another's is in Japanese
- **THEN** each reads every message of the conversation in their own language, and the wire
  carries no natural-language string

#### Scenario: Every sentence is translated

- **WHEN** the locale check runs over the phrasebook
- **THEN** every message type resolves to a string in every shipped locale, on the same terms
  as the rest of the app's copy

### Requirement: Saying what you know, without typing it

The client SHALL let a player state partial knowledge as easily as exact knowledge, building a
claim by **selection** rather than by composing a sentence.

Selecting one card and one rank SHALL make an exact claim. Selecting **two** cards and two ranks
SHALL present the only question left — which way round — as three answers of equal standing:
each of the two pairings, and **"not sure"**. "Not sure" SHALL be reachable in one tap and SHALL
NOT be presented as an advanced or secondary option.

The pairing question SHALL be offered for exactly two cards. For more than two, a claim SHALL be
unassigned by construction, so that a player is never asked to choose between more pairings than
they can read.

A claim SHALL be composable about any seat's cards, on the same terms, and a player SHALL be
able to withdraw a claim they no longer stand behind.

The table SHALL draw an unassigned claim as its candidate ranks worn by every position it
covers, visibly linked, so that it reads as one statement about several cards rather than as
several statements.

No part of this SHALL involve typed text.

#### Scenario: A player remembers the pair but not the order

- **WHEN** a player selects two of their own cards and picks King and Ace
- **THEN** they are offered both pairings and "not sure", and choosing "not sure" claims the pair
  without an assignment

#### Scenario: Three cards are claimed at once

- **WHEN** a claim covers more than two positions
- **THEN** no pairing question is asked and the claim stands as unassigned

#### Scenario: The claim is read back on the felt

- **WHEN** an unassigned claim about two positions is standing
- **THEN** both positions show both candidate ranks, linked, and neither reads as a card of a
  single named rank

#### Scenario: A player stops being sure

- **WHEN** a player withdraws a claim
- **THEN** the badge leaves those cards and teammates can see that the claim is gone

### Requirement: The table shows who said what, and where they disagree

A claim SHALL be legible as somebody's statement rather than as a property of the card: the
table SHALL show which seat made it, without requiring the reader to open anything.

A **disputed** card SHALL be visibly marked as disputed, showing both claims and both speakers,
and SHALL be distinguishable at a glance from a card whose claim is merely partial — two people
disagreeing reads differently from one person being unsure.

The client SHALL NOT present either claimant as correct, and SHALL offer no control that
resolves a dispute on a player's behalf. A dispute SHALL be resolvable only by a claimant
withdrawing or replacing their own claim, or by the reveal at scoring.

A player SHALL be able to see, for any claim, whether its speaker had actually read that card —
which the public `knownCardPositions` already says — so that a guess is distinguishable from a
reading.

#### Scenario: A claim is read as somebody's statement

- **WHEN** a claim is standing on a card
- **THEN** the seat that made it is visible on the table, not behind a tap

#### Scenario: A dispute is drawn as a dispute

- **WHEN** two seats claim different ranks for the same card
- **THEN** the card is marked as disputed with both claims and both speakers shown, and it does
  not read the same as a card carrying one uncertain claim

#### Scenario: Nobody is declared right

- **WHEN** a card is disputed
- **THEN** no control offers to resolve it, and the dispute stands until a claimant changes their
  own claim or the hands are revealed

### Requirement: A proposal is a suggestion, never a move

A proposal SHALL name a move and the seat that would make it, and SHALL never be applied by
anyone but that seat.

The client SHALL present a proposal addressed to the viewer as a one-tap move, and accepting it
SHALL dispatch an ordinary `GameAction` from the viewer's own seat through the usual
validation. Declining SHALL be possible and SHALL be visible to the proposer.

The client SHALL NOT offer any control that acts for another seat, in any configuration,
single-player included.

#### Scenario: A proposal is accepted

- **WHEN** the viewer accepts a proposal addressed to them
- **THEN** the resulting action is dispatched from their own seat and is indistinguishable from
  the same move made by hand

#### Scenario: A proposal is addressed elsewhere

- **WHEN** a proposal names a seat other than the viewer's
- **THEN** the viewer can read it but has no control that would perform it

### Requirement: The coalition shares one plan

The coalition SHALL have at most one plan per final round, readable by every seat and editable
by every coalition member — bots included. An edit SHALL name **one part** of the plan — a lane
set, replaced or cleared, or a shed added or removed — and SHALL be merged into the standing
plan, so that two members editing different parts each see both edits. Two edits to the same
part SHALL resolve as the most recent standing.

A plan SHALL be a sequence of steps, each naming the seat whose turn it belongs to. It SHALL
have at most as many lanes as there are turns left in the round.

The plan SHALL carry who has **agreed** to it as a whole. Every edit SHALL reset agreement to
the editor alone. A plan SHALL count as agreed when every connected coalition human and every
coalition bot has agreed. Agreeing SHALL count as being done conferring. A plan that is not
agreed when the confer window closes SHALL stand as a suggestion showing who agreed, and SHALL
NOT be discarded.

A coalition bot SHALL answer for its own lane whenever an edit touches it — agreeing where the
step leaves the coalition's lowest hand no worse on the shared picture, declining with its
reason otherwise — and SHALL agree silently to an empty lane. A bot SHALL NOT evaluate another
seat's lane with that seat's private cards.

On a member's own turn, the client SHALL show that member's own lane as the offered move, with
the targets pre-armed when the drawn card or the discard top makes the step legal, and as words
alone otherwise. Nothing SHALL require the member to take it.

Solo play SHALL hold a plan under the same rules, so that the same screens work in both lives
of `GameSession`; the rules for a legal edit SHALL live in one shared function both sessions
call.

A lane SHALL lock when its owner's turn begins, so that a plan cannot change under the hand of
the person executing it; later lanes SHALL stay editable.

A plan SHALL be composed only from material the table has been told about — declared claims,
the composer's own read cards, and the discard top — and SHALL NOT offer the Vinto caller's
cards as a target for any move, the coalition being forbidden to touch them.

#### Scenario: Two members edit the same plan

- **WHEN** two coalition members each edit a different lane during the confer window
- **THEN** both see one plan carrying both edits, not two competing plans

#### Scenario: An edit unsettles an agreement

- **WHEN** every member has agreed and one of them replaces a lane
- **THEN** the plan shows only the editor as agreed, and the others are asked again

#### Scenario: The last yes starts the round

- **WHEN** the last connected coalition member agrees to the plan during the confer window
- **THEN** the window closes and the first coalition turn is played, with nobody having to say
  "done" separately

#### Scenario: A bot is asked to do something it would not

- **WHEN** a member sets a bot's lane to a step that leaves the coalition's lowest hand higher
- **THEN** the bot declines with its reason, is not counted as agreed, and the plan stands

#### Scenario: A lane locks

- **WHEN** a member's turn begins
- **THEN** their own step stops changing, while the steps after it can still be revised

#### Scenario: The caller's hand is not a target

- **WHEN** a member composes a step
- **THEN** no tap on the Vinto caller's cards can enter the plan

### Requirement: The plan covers shedding, not only turns

A plan SHALL be able to express what the coalition intends to do in a **toss-in window** as well
as on its turns, the final round being three turns plus every window they open.

A member SHALL be able to announce that they hold a rank and will shed it if it lands, and a
member SHALL be able to propose that a teammate **discard a named rank** so that another member
can shed theirs into it. Both halves of that play SHALL be sayable.

The client SHALL make the cost of being wrong legible where it matters most: a failed toss-in
takes a penalty card and bars that seat for the rest of the round, which is ruinous for the hand
the coalition is pushing.

The client SHALL NOT require a conditional plan language for this: an announced intent is a
statement about what its speaker holds, not a branch.

#### Scenario: A shed is set up

- **WHEN** a member proposes that a teammate put down a 7 so they can shed their own
- **THEN** both the request and the intent are visible to the coalition, and neither is an action

#### Scenario: The risk is shown to the hand being pushed

- **WHEN** the member whose hand the coalition is pushing considers a toss-in
- **THEN** the client shows that a wrong one costs a card and bars them for the rest of the
  round

### Requirement: A tossed-in Ace is not aimed at a teammate by accident

In the final round every legal target for an Ace is a coalition member, the caller being off
limits — so an Ace hands a penalty card to a teammate and can hit the one hand still able to win
the round.

When a member is asked where to point an Ace in the final round, the client SHALL say what it
does and SHALL make putting it down the readiest answer, without removing any legal choice.

#### Scenario: A human tosses in an Ace

- **WHEN** a coalition member is asked where to aim an Ace during the final round
- **THEN** the consequence is stated and putting it down is offered first, with every legal
  target still available

### Requirement: The plan says whether it wins, not what it totals

The round is decided by the caller's total against the **lowest** coalition hand, and a tie goes
to the caller — so the coalition must finish strictly below.

The client SHALL show the plan's standing as an outcome rather than as a bare number: what the
coalition's best hand would be, what the caller is believed to hold, and whether that wins. A
level result SHALL NOT be presented as a draw, because it pays the caller.

The caller's believed total SHALL be built from what the coalition has actually seen and
claimed, and SHALL show how much of that hand is unknown rather than implying a total nobody
knows.

#### Scenario: The plan draws level

- **WHEN** a plan would leave the coalition's best hand equal to the caller's believed total
- **THEN** it is shown as losing, not as level

#### Scenario: Most of the caller's hand is unseen

- **WHEN** the coalition has seen only one of the caller's cards
- **THEN** the readout says so rather than stating a total as though it were known

### Requirement: The coalition channel is optional

A player who never claims, never proposes and never opens the plan SHALL be able to play the
final round exactly as they would without any of it.

No talk, claim, proposal or plan SHALL be required to take a turn, and nothing SHALL block,
delay or repeatedly prompt a player who ignores the channel.

#### Scenario: A silent coalition member

- **WHEN** a player takes their final-round turn having said nothing
- **THEN** their turn proceeds normally and nothing has waited on them beyond the confer
  window's own deadline

### Requirement: A plan is rehearsed, not described

The client SHALL be able to play a plan as an animation of the moves it proposes, ending on the
arrangement the plan produces, using the same choreography that animates real moves against
hypothetical states.

The rehearsal SHALL be unmistakably distinct from the live table, and SHALL meet the same
contrast standard as every other screen in both themes.

The client SHALL show what the plan is worth — the coalition's best hand were it carried out —
and SHALL update it as the plan is edited.

#### Scenario: A plan is previewed

- **WHEN** a coalition member plays the plan back
- **THEN** the cards move through each step in order and settle on the resulting hands, and no
  game state has changed

#### Scenario: The value is read off the plan

- **WHEN** a step is added or removed
- **THEN** the plan's stated best hand updates to match

### Requirement: An agreed step arms its owner's turn

When a plan's step belongs to the viewer and the viewer has agreed to it, the client SHALL open
their turn with that move already aimed, committable in one action, and SHALL leave every other
move available.

#### Scenario: The turn opens on the plan

- **WHEN** the viewer's turn begins and an agreed step names it
- **THEN** the table presents that move ready to commit, and the viewer can still do anything
  else the rules allow

### Requirement: A plan decays honestly

The client SHALL distinguish a step whose **card moved** from a step whose **claim was wrong**,
and SHALL treat them differently.

A step whose card the table watched move SHALL be re-anchored silently, following the claim to
its new position. A step whose claim is contradicted by a public reveal SHALL be marked broken,
together with every later step that depended on it, and SHALL never be repaired silently.

Detection SHALL be a client concern, computed from the public reveals the client already
receives. The engine SHALL NOT compare a claim against a card.

A broken plan SHALL be revisable without leaving the turn, and the copy SHALL treat a wrong
claim as the game working rather than as a player's failure.

#### Scenario: A claimed card is swapped away

- **WHEN** a plan's step names a card that a Jack then moves to another hand
- **THEN** the step follows the card and stays live

#### Scenario: A claim turns out to be wrong

- **WHEN** a card publicly revealed as a 7 was claimed to be a Jack, and a step depended on it
- **THEN** that step and everything downstream of it are shown broken, the coalition is offered
  the chance to re-plan, and nothing is silently substituted

#### Scenario: A draw beats the plan

- **WHEN** the viewer draws a card that makes a better line available than their planned step
- **THEN** the client offers to re-plan rather than insisting on the agreed step

### Requirement: Claims are settled at the reveal

When every hand is turned over at scoring, the client SHALL show which standing claims were
true and which were not, for the caller's claims as much as the coalition's.

#### Scenario: A wrong claim is exposed

- **WHEN** a player claimed a Joker and the hand reveals a King
- **THEN** the score screen shows that claim as having been wrong

### Requirement: The client drives the coalition without a leader

The client SHALL NOT ask any player to nominate a coalition leader, and SHALL NOT render a
leader. The final round SHALL begin as soon as Vinto is called.

The seat-selection control SHALL remain in use for the one move that still names a player: an
Ace, which makes somebody draw.

#### Scenario: Vinto is called against a human

- **WHEN** a bot calls Vinto and the viewer is in the coalition
- **THEN** the viewer is taken into the confer window rather than asked to nominate anybody

## MODIFIED Requirements

### Requirement: Player views hide other seats' cards

`LocalGameSession` SHALL expose the human seat's state through the shared
`projectView(state, playerId)` projection so that single-player and online play render from the
same `PlayerView` type; the full `GameState` SHALL NOT be observable by the UI.

No seat SHALL be shown another seat's hidden cards for any reason, and **no coalition role SHALL
grant that visibility**. Coalition knowledge SHALL travel as declared claims, which are worth
exactly what the claimant's memory is worth.

#### Scenario: Human never sees bot hands

- **WHEN** the human's `view` is inspected during play
- **THEN** bot cards appear face-down unless the human has knowledge of them through a peek, a
  public reveal, or scoring

#### Scenario: A coalition member's hand is not turned over to a teammate

- **WHEN** the final round is in progress
- **THEN** no member's view contains another member's hidden cards, whatever either has claimed

### Requirement: Bot adapter driven by visual state

The bot driver SHALL react to visual (post-animation) state — current player, sub-phase, turn
number, active toss-in, Vinto caller, phase, difficulty — process reactions strictly
sequentially, use injectable delays, and drive all bot phases: turn start, choosing, selecting,
action targets, toss-in participation, the Vinto call, coalition talk, and final-round decisions
through the coalition planner.

**Coalition leader auto-selection SHALL NOT be among them.** No bot phase SHALL wait on a
nomination, and bot play SHALL NOT be held pending a human's coalition decision.

#### Scenario: Ported adapter integration tests pass

- **WHEN** the TypeScript `bot-tossin` and `coalition-final-round` integration tests are ported to `runTest` with virtual time
- **THEN** they pass unchanged in intent (same dispatch sequences and outcomes)

#### Scenario: Never acts mid-animation

- **WHEN** logical state advances but the animation layer has not synced visual state
- **THEN** no bot action is dispatched until the sync

#### Scenario: A human is in the coalition

- **WHEN** Vinto is called and a person is among the coalition
- **THEN** the bots declare and take their turns without waiting for any nomination
