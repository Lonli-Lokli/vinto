# Spec delta: online-multiplayer

## ADDED Requirements

### Requirement: A confer window opens the final round

When Vinto is called and at least one person is in the coalition, the room SHALL open a bounded
window in which talk and planning happen and no turn is played.

The window SHALL close early when every connected coalition member says they are ready, and
SHALL close on its own deadline otherwise, so that the caller is never held indefinitely by a
coalition that will not decide.

The window SHALL be skipped entirely when no person is in the coalition, so that a game against
three bots does not stall.

Closing the window SHALL NOT require anyone to have said anything.

#### Scenario: Three humans confer

- **WHEN** Vinto is called against a coalition of three connected people
- **THEN** the room opens the window, relays their talk, and starts the first coalition turn
  when all three are ready or the deadline passes, whichever is first

#### Scenario: An all-bot coalition

- **WHEN** the coalition contains no person
- **THEN** no window opens and the final round proceeds without a pause

#### Scenario: The window is never open-ended

- **WHEN** a coalition member stops responding during the window
- **THEN** the deadline closes it and the round proceeds

### Requirement: Talk crosses the wire as typed values

The room SHALL relay coalition talk to every seat as typed messages carrying no
natural-language text, and SHALL apply the same seat boundary it applies to actions: a message
names its speaker, and a socket SHALL only speak as its own seat.

The room SHALL cap how much a seat may say in a window, refusing the excess rather than
relaying it.

Talk that is not a claim SHALL NOT enter `GameState` and SHALL NOT appear in a recording; a
claim SHALL, being information about the world that the engine already carries.

#### Scenario: A socket speaks for another seat

- **WHEN** a message names a speaker other than the seat holding the token
- **THEN** the room refuses it, on the same rule that refuses an action naming another player

#### Scenario: A recording of a talkative round

- **WHEN** a final round with a full conversation is recorded and replayed
- **THEN** the replay reproduces every state hash, the transient talk having never been state

### Requirement: A plan is room state, not game state

The room SHALL hold at most one coalition plan per final round, SHALL accept edits only from
coalition members — never from the Vinto caller — and SHALL send every seat the same plan, the
caller's seat included.

An edit SHALL name one part of the plan, and the room SHALL merge it into the standing plan;
an edit naming a locked lane SHALL be refused. The room SHALL record who has agreed to the plan
and who last edited it, SHALL reset agreement to the editor on every edit, and SHALL treat a
member's agreement as that member being done conferring.

The room SHALL send the standing plan on every `events`, `sync` and `joined` message, so that
a lane locking on an ordinary action, a reconnect and a restarted app all land on the present
plan. An edit that moves no card SHALL be answered as `more-time` is: an empty `events`
message per seat carrying the plan and the bots' answers. A plan edit SHALL spend from the same
budget table talk does.

The plan SHALL be discarded when the round is scored, and SHALL NOT appear in the round's
recording.

#### Scenario: A member reconnects mid-final-round

- **WHEN** a coalition member's socket returns during the final round
- **THEN** they receive the plan as it currently stands, along with their view

### Requirement: A seat a bot has taken over is actually played

When a seat's grace expires and a bot takes it over, the room SHALL play that seat — its turns,
its toss-in windows and its coalition talk — until its owner returns.

The room SHALL NOT record the takeover in the game state. `isHuman` and `isBot` are inside the
canonical state hash, so moving them out of band would leave the round's own recording unable to
replay to the state it ended in.

Instead the room SHALL present the game to its **driver** as it is actually being played — a
seat a bot is playing reads as a bot — and SHALL leave the state the driver's moves are
validated and reduced against untouched. What the room submits SHALL be the same action the
absent person's own client would have sent.

The bot driver SHALL NOT decline to move a seat merely because it still holds a token, that
being exactly what a held seat looks like. It SHALL decline only for a seat whose person is
present.

The seat SHALL be visibly marked as bot-played while it is, so that nobody is negotiating with
somebody who has left.

#### Scenario: A coalition member drops in the final round

- **WHEN** a coalition member's socket goes away and their grace expires with their turn still
  to come
- **THEN** a bot takes the seat, plays its one remaining turn, and the round reaches scoring

#### Scenario: The owner comes back

- **WHEN** the seat's owner reconnects
- **THEN** the seat is theirs again, the room stops playing it, and they are told a bot played
  while they were away

#### Scenario: The round the bot played still replays

- **WHEN** a round in which a bot played a dropped person's seat is recorded and replayed
- **THEN** it reproduces every state hash, the takeover having changed no state

#### Scenario: A taken-over seat in the coalition

- **WHEN** a taken-over seat is in the coalition during the final round
- **THEN** it is shown as bot-played to every other seat, and it participates in talk as a bot

## MODIFIED Requirements

### Requirement: Per-seat redacted views

`shared/engine` SHALL provide a pure `projectView(state, playerId): PlayerView` that exposes to
a seat only: its own cards at `knownCardPositions` during setup, cards the action in progress
has revealed to it, the discard pile, draw-pile size, standing claims, and the public flags
(phase, sub-phase, current player, pending action metadata, toss-in state, Vinto caller, scores
when in `scoring`). The server SHALL send each seat only its own view; the local session SHALL
use the same projection for the human seat.

**No coalition role SHALL widen a view.** Coalition knowledge SHALL travel as declared claims
and nothing else, so that a client which is trusted with nothing extra is the only client that
exists.

#### Scenario: Hidden cards stay hidden

- **WHEN** a seat's view is serialised
- **THEN** it contains no rank or value for any card the seat is not entitled to see, and this
  is asserted by a test over random recorded states

#### Scenario: No view is widened by the coalition

- **WHEN** any seat's view is projected during the final round
- **THEN** it contains no other seat's hidden cards, whatever the coalition has claimed or
  agreed
