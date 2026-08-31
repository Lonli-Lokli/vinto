<!--
  Canonical spec, synced from `add-live-analytics` when it was archived.

  What is counted, and the far longer list of what is not. Phases 1-4 were the release gate;
  phase 5 (reading it) moved to `ship-and-operate`, because it needs a dashboard and traffic.

  Deltas land here on archive, not before — a requirement in a change folder is a
  proposal, and a requirement here is what the game is held to.
-->
# analytics

## Requirements

### Requirement: Nothing identifying is collected

The app SHALL NOT send, store or derive any value that identifies a person, a device or an
installation. Specifically it SHALL NOT emit a room code, a nickname, a seat token, a seat
identifier, an IP address, an advertising identifier, or any identifier that persists across
process restarts.

A session identifier MAY exist to group events within one sitting. It SHALL be random, held
in memory only, and never written to the vault or to any storage that survives the process.

The event surface SHALL be a closed type whose fields cannot carry a free string supplied by
a player.

#### Scenario: A whole session's payloads carry nothing identifying

- **WHEN** a solo round, a lesson and an online round are played with every emitted payload recorded
- **THEN** no payload contains the room code, any nickname, any token, any seat identifier, or
  any value that is equal between two runs of the app in separate processes

#### Scenario: A session identifier does not survive a restart

- **WHEN** the app emits events, is restarted, and emits again
- **THEN** the two runs share no identifier, and nothing links them

### Requirement: Consent is checked before the first event

The app SHALL read the platform's Global Privacy Control and Do-Not-Track signals, where the
platform exposes them, before emitting any event. When either is set, the app SHALL emit
nothing at all rather than emitting a reduced set.

Settings SHALL carry an analytics opt-out. When it is off, no event SHALL be emitted from any
surface, including failures.

#### Scenario: Do-Not-Track suppresses everything

- **WHEN** the platform reports Do-Not-Track and a full round is played
- **THEN** no payload is sent, and the round plays identically

#### Scenario: Opting out mid-session takes effect immediately

- **WHEN** the player turns analytics off while a round is in progress
- **THEN** no further event is emitted, and any buffered event is discarded rather than flushed

### Requirement: Analytics never affects the game

Emitting an event SHALL NOT block a move, an animation frame, a socket write, or a reducer.
The client sink SHALL be bounded: it SHALL cap the number of events buffered per session and
discard new events when full rather than growing, blocking or persisting. It SHALL NOT retry.

The Worker and the Durable Object SHALL run identically when no analytics binding is
configured, so that local development and every gate script work without a Cloudflare account.

#### Scenario: A flood is dropped, not queued

- **WHEN** more events are emitted than the per-session cap allows
- **THEN** the earliest events are kept, the newest are discarded, and no unbounded buffer grows

#### Scenario: A room with no binding still plays

- **WHEN** the room runs with the analytics binding absent
- **THEN** a full round completes with the same states and hashes, and nothing is emitted

#### Scenario: A failing sink does not fail a move

- **WHEN** the analytics endpoint is unreachable or returns an error
- **THEN** the move completes normally and nothing is retried

### Requirement: The server measures what only the server can know

The room SHALL emit lifecycle and round events from the authoritative side — room created,
seat filled, seat vacated, bot takeover, reconnect, session ended with its reason, round
started, round ended with its turn count, duration and outcome.

Every room event SHALL carry the Durable Object wall time and request count of the invocation
that produced it, so that the cost of a room is a query rather than an estimate.

Clients SHALL NOT be asked to report facts the room already holds.

#### Scenario: A full room produces the expected event sequence

- **WHEN** two clients create a room, add bots, play a round and disconnect
- **THEN** the recorded events are exactly the lifecycle and round events for that sequence,
  each carrying wall time and request count, and no client duplicate of any of them

### Requirement: Sampling is declared and weighted

An event that is sampled SHALL carry the sampling rate it was recorded at, so that a query can
weight it. Room creation, round end and every failure event SHALL NOT be sampled.

#### Scenario: A sampled event carries its rate

- **WHEN** a high-frequency event is recorded under sampling
- **THEN** the payload carries the rate applied, and a count derived from it is weighted rather
  than raw
