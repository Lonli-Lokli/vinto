# online-multiplayer

## ADDED Requirements

### Requirement: Game modes with humans and bots

Every game SHALL have exactly 4 players, without exception. The system SHALL support:
(a) one human vs 3 bots offline; (b) an online 4-seat room where 2–4 seats are humans on
their own devices and the remaining seats are filled by bots; (c) all-human online tables
of 4. Every mode SHALL use the same
shared engine, validator, bot engine and recording format.

#### Scenario: Two humans and two bots

- **WHEN** a host creates a 4-seat room, one friend joins, and the host starts the game
- **THEN** the two empty seats are bots, the game plays to scoring, and each human sees only their own permitted information

#### Scenario: All-human table

- **WHEN** four humans join a 4-seat room
- **THEN** no bots are created and turn order follows seat order

### Requirement: Server-authoritative game state

An online game SHALL be owned by a single authoritative room process running the shared
Kotlin modules, which holds the full `GameState`, validates and applies every action with
the shared `GameEngine.reduce`, runs bot seats with the shared `BotAIAdapter`, records the
game as a `GameRecording` v1 (authoritative log), and rejects any action that the validator
rejects. Clients SHALL never hold hidden information of other seats.

Bot seats SHALL be computed by the room process, never delegated to a client: a client
cannot decide a bot's move without being given that seat's hidden cards, which would defeat
the redaction rule above.

(The chosen host is a Cloudflare Durable Object per room — see design D9 — but this
requirement is deliberately platform-neutral: what matters is that exactly one authoritative
process owns a room's state and hidden information.)

#### Scenario: Invalid action rejected server-side

- **WHEN** a client sends an action that is not legal in the current state (e.g. acting out of turn or targeting the Vinto caller in the final round)
- **THEN** the server rejects it with the validator reason, the state and recording are unchanged, and other clients receive nothing

#### Scenario: Server recording replays

- **WHEN** an online game finishes
- **THEN** the server-side recording replays without divergence in both engines

### Requirement: Per-seat redacted views

`shared/engine` SHALL provide a pure `projectView(state, playerId): PlayerView` that
exposes to a seat only: its own cards at `knownCardPositions`, opponents' cards it has
knowledge of (`opponentKnowledge` and cards temporarily revealed by the current action),
the discard pile, draw-pile size, and all public flags (phase, sub-phase, current player,
pending action metadata, toss-in state, Vinto caller, coalition leader, scores when in
`scoring`). The server SHALL send each seat only its own view; the local session SHALL
use the same projection for the human seat.

#### Scenario: Hidden cards stay hidden

- **WHEN** a seat's view is serialised
- **THEN** it contains no rank/value for any card the seat is not entitled to see, and this is asserted by a test over random recorded states

#### Scenario: Coalition leader visibility

- **WHEN** a human is coalition leader in the final round
- **THEN** their view reveals all coalition members' cards, exactly as the web app does today

### Requirement: Real-time protocol with resync

Clients and server SHALL communicate over WebSocket with JSON messages (`join`, `action`,
`event`, `resync`, `error`) reusing the shared `GameAction`/`PlayerView` serialisers.
Every accepted action SHALL be broadcast as an `event` carrying the recording index and
the seat's new view; a client that reconnects SHALL send its last index and receive the
missing events (or a full view) so it converges to the current state.

#### Scenario: Reconnect mid-game

- **WHEN** a client loses connectivity for 20 seconds during another player's turn and reconnects
- **THEN** it receives all missed events in order and its view equals the server's projection for that seat

#### Scenario: Duplicate delivery

- **WHEN** a client re-sends an action with an already-applied (seat, index)
- **THEN** the server treats it as idempotent and does not apply it twice

### Requirement: Human pacing rules

Toss-in readiness and coalition-leader selection SHALL keep the existing mechanics with
server-side timeouts (configurable per room, default 15 s) that auto-ready / auto-select;
a human seat disconnected beyond a grace period (default 60 s) SHALL be played by a bot
until the human returns.

#### Scenario: Toss-in timeout

- **WHEN** a human does not press "continue" within the toss-in timeout
- **THEN** the server marks them ready and play proceeds

#### Scenario: Disconnected seat

- **WHEN** a human seat exceeds the grace period
- **THEN** a bot takes its turns through the same action path until the human reconnects, and the recording shows only ordinary actions

### Requirement: Rooms and guest identity

The system SHALL let a host create a 4-seat room (difficulty only), share a join code
or link, see joined players, and start; players SHALL be identified by a device-bound
guest id and nickname. No accounts are required in this change.

#### Scenario: Join by code

- **WHEN** a friend enters the room code
- **THEN** they appear in the host's lobby with their nickname and are assigned the next free seat

### Requirement: Local single-player uses the same session contract

`LocalGameSession` (in-process engine + bots) and `RemoteGameSession` (server) SHALL
implement the same `GameSession` interface so the game UI is identical in both modes.

#### Scenario: UI mode-agnostic

- **WHEN** the game screen is given a `GameSession`
- **THEN** it renders and plays without knowing whether the session is local or remote

### Requirement: Observability

The server SHALL report errors to Sentry with the game id and action index, and SHALL
persist recordings of finished games so a reported issue can be replayed.

#### Scenario: Crash pairing

- **WHEN** a server exception occurs while applying action #37 of a game
- **THEN** the Sentry event carries the game id and index 37 and the recording up to that point is retrievable
