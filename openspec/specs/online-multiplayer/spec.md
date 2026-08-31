<!--
  The canonical spec for online play: what a room *is*, as opposed to what any one change did
  to it. Synced from `design-online-room-lifecycle` when that change was archived (42/42).

  Deltas land here on archive, not before — a requirement in a change folder is a proposal,
  and a requirement here is the thing the game is held to.

  The eight requirements this file used to promise are now below it:
  `migrate-to-kotlin-multiplatform` archived on 2026-08-31, after the Kotlin rewrite merged to
  `master`. Where the first fifteen say what a *room* is, these say what the game played in one
  is — server-authoritative state, per-seat redaction, resync, pacing, and the contract that a
  solo game is the same session interface with the network taken out.
-->
# online-multiplayer

## Requirements

### Requirement: Player identity is a server-issued capability token

A room SHALL generate a cryptographically random token when a client first occupies a seat,
return it exactly once to that client, and store only a hash of it. Every subsequent message
from that client SHALL carry the token, and the room SHALL bind an action to a seat by
comparing hashes. A room SHALL NOT accept a client-supplied identifier as proof of identity.

A seat SHALL carry a nullable `ownerId` which is null for anonymous players, reserved for a
future account system; nothing in this change populates it.

#### Scenario: A stolen nickname does not take a seat

- **WHEN** a second client joins with the same nickname as a seated player but without their token
- **THEN** it is given a different free seat, and the seated player's socket is unaffected

#### Scenario: An action without a valid token is refused

- **WHEN** a client sends an action carrying no token, or one whose hash matches no seat
- **THEN** the room refuses it, the state is unchanged, and no view is sent

#### Scenario: The token is never broadcast

- **WHEN** any message is sent to any socket other than the one that first claimed the seat
- **THEN** the message contains no token for any seat

### Requirement: The server owns the deal

A room SHALL derive its session seed from a cryptographic random source at creation and SHALL
NOT accept a seed from a client under any circumstance. Per-round seeds SHALL be derived from
the session seed so that a whole session replays from one number.

#### Scenario: A client-supplied seed is ignored

- **WHEN** a client requests a room with a seed parameter
- **THEN** the room is dealt from a server-chosen seed and the parameter has no effect

### Requirement: Rooms exist only when the registry has issued a code

A room SHALL be created only through a registry that mints its code first. A request naming a
code the registry does not know SHALL be refused without creating a Durable Object. Codes
SHALL be six characters from an alphabet that excludes visually ambiguous glyphs.

#### Scenario: An invented code creates nothing

- **WHEN** a client connects with a code the registry never issued
- **THEN** the connection is refused and no Durable Object is created or billed

#### Scenario: A public room is discoverable and a private one is not

- **WHEN** a public and a private room both exist
- **THEN** the registry lists the public one and does not list the private one, and both are joinable by code

### Requirement: A game needs two humans, and the fourth seat starts a countdown

A room SHALL have four seats and SHALL NOT start a game with fewer than two human players.
Empty seats SHALL be fillable by bots, and any seated player — not only the creator — SHALL be
able to add or remove a bot while the room has not started.

Filling the fourth seat, by a human joining or by a bot being added, SHALL begin a
ten-second countdown that is visible in the room and in the registry's public listing.
Emptying any seat during the countdown SHALL cancel it and return the room to its lobby state;
refilling SHALL begin a fresh countdown rather than resume the cancelled one. The countdown
SHALL be held on a Durable Object alarm so that it survives hibernation.

A human joining while the room has not started SHALL displace a bot if no seat is free. Once
play has begun the table SHALL be fixed, and a bot playing a disconnected human's seat SHALL
NOT be displaceable, because that seat belongs to its token.

#### Scenario: A lone player cannot start

- **WHEN** one human is in a room and adds bots to every other seat
- **THEN** no countdown begins and the game does not start, because a game needs two humans

#### Scenario: Two humans start by filling the table with bots

- **WHEN** two humans are in a room and either of them adds bots to the remaining two seats
- **THEN** a ten-second countdown begins, and at its end the game starts with two humans and two bots

#### Scenario: A forced start can be undone

- **WHEN** a player adds the fourth seat's bot and another player removes it before the countdown expires
- **THEN** the countdown is cancelled, the room returns to its lobby state, and no game starts

#### Scenario: Refilling restarts the full countdown

- **WHEN** a seat empties at t=7s and is refilled immediately
- **THEN** a fresh ten seconds begins rather than the remaining three

#### Scenario: A late friend takes a bot's seat

- **WHEN** a human joins during the countdown and every seat is filled, one of them by a bot
- **THEN** the human takes the bot's seat, the countdown continues, and the table now has one more human

#### Scenario: The countdown survives eviction

- **WHEN** the Durable Object is evicted during the countdown and the countdown expires
- **THEN** the game starts, because the countdown was an alarm rather than an in-memory timer

### Requirement: A session lasts thirty minutes of wall clock

A session SHALL end thirty minutes after its first deal. The room SHALL own that clock; the
engine SHALL NOT be given access to wall-clock time, because a clock in the reducer would make
a recording unreplayable.

When the limit is reached, if Vinto has been declared in the round in progress that round
SHALL play to its end and be scored; otherwise the round in progress SHALL be discarded and
the final standings SHALL be computed from completed rounds only. This SHALL apply uniformly,
including when no round has completed, in which case the session ends with no winner.

A new round SHALL be dealt whenever the session is live, regardless of how little time
remains. The remaining time SHALL be present in every player's view.

The room's log SHALL record which round was discarded, because the standings cannot be
recomputed from the round recordings alone.

#### Scenario: A declared Vinto is allowed to finish

- **WHEN** the thirty minutes elapse in a round where Vinto has been called
- **THEN** the final round plays out, that round is scored, and only then does the session end

#### Scenario: A round with no Vinto is discarded

- **WHEN** the thirty minutes elapse in a round where nobody has called Vinto
- **THEN** the round is discarded, and the standings shown to every player come from the completed rounds only

#### Scenario: A session can end with no winner

- **WHEN** the thirty minutes elapse during the first round and no Vinto has been called
- **THEN** the session ends with no completed rounds and no winner, and every player is told so

#### Scenario: A round is dealt even with a minute left

- **WHEN** a round finishes with one minute of the session remaining and the players agree to another
- **THEN** a new round is dealt, and it is discarded at the buzzer unless somebody calls Vinto first

#### Scenario: Players can see the deadline they are playing against

- **WHEN** any player receives their view during a live session
- **THEN** it carries the time remaining, so calling Vinto before the buzzer is a decision rather than a guess

#### Scenario: The engine never sees the clock

- **WHEN** the engine sources are scanned by the purity guard
- **THEN** no wall-clock or ambient time source appears in the reducer path, and every recorded round still replays

### Requirement: A room does not host a game for one human

When the number of connected humans in a running session falls below two, the room SHALL
allow a grace period for one to return and SHALL then end the session and delete itself.
Seat-level takeover by bots SHALL continue independently during that period, so that a game
which recovers a second human remains playable.

#### Scenario: The last player standing does not keep the server busy

- **WHEN** three of four humans leave a running game and none returns within the grace period
- **THEN** the room ends the session and deletes itself, and the remaining player is told the room closed

#### Scenario: A recovered game continues

- **WHEN** humans drop to one and a second reconnects within the grace period
- **THEN** the session continues, and the seats that passed their own grace are played by bots

### Requirement: Rooms clean themselves up

A room SHALL schedule an alarm on every state change and SHALL delete itself when: no human
socket has been connected for the room TTL; the game never started within the lobby TTL; or
the session finished and the finished TTL elapsed. A seat whose last socket closes SHALL be
played by a bot after a grace period, and SHALL remain reserved for its token.

#### Scenario: An abandoned room disappears

- **WHEN** every human socket closes and the room TTL elapses
- **THEN** the room deletes its storage, tells the registry, and a later request for that code is refused

#### Scenario: A brief disconnect does not lose a seat

- **WHEN** a player's socket closes and reconnects with the same token inside the grace period
- **THEN** they resume the same seat, no bot took a turn for them, and they receive their view

#### Scenario: A long disconnect is played by a bot

- **WHEN** a player's socket closes and the grace period elapses while the game continues
- **THEN** a bot plays that seat, and a later reconnect with the same token resumes it and is told the hand changed

### Requirement: Abuse limits bound the cost of a hostile client

Room creation SHALL be rate-limited per source at the edge, and the registry SHALL enforce a
global cap on live rooms and a per-source cap on concurrently owned rooms. A room SHALL
rate-limit actions per socket, because a single action may trigger several bot searches and is
therefore the most expensive thing a client can ask for.

#### Scenario: An action flood is throttled, not served

- **WHEN** a socket sends actions faster than the sustained rate allows
- **THEN** the room refuses the excess with a retry signal and performs no bot search for them

#### Scenario: The global room cap holds

- **WHEN** the number of live rooms reaches the cap
- **THEN** further creation requests are refused with a reason, and existing rooms are unaffected

### Requirement: Nicknames are display-only metadata carried per token

A nickname SHALL be 1–16 characters after trimming, restricted to letters, digits, spaces and
a small punctuation set, and SHALL NOT be required to be unique. A nickname SHALL NOT be used
to identify, authorise or seat a player. A seat SHALL carry its nickname inside a player
profile record rather than as a bare field, so that further per-player metadata can be added
without changing the shape of the wire format or of stored rooms.

#### Scenario: Two players share a nickname

- **WHEN** two clients join the same room with the same nickname
- **THEN** both are seated, each with their own token, and the view distinguishes them by seat

#### Scenario: A nickname in a non-Latin script survives

- **WHEN** a client joins with a nickname written in a non-Latin script
- **THEN** it is preserved rather than stripped, and only markup characters are removed

#### Scenario: An empty nickname still names a player

- **WHEN** a client joins with a blank or whitespace-only nickname
- **THEN** the seat is given a fallback name derived from its seat number

### Requirement: An action may only be taken by the player it names

The seat boundary SHALL be checked before the engine sees an action: an action whose payload
names a player other than the one holding the socket's token SHALL be refused, whether or not
it would have been legal for that player. The same check SHALL be used by every session,
online or local, from a single definition, so that the two cannot disagree about who may act.

#### Scenario: A client cannot act for another seat

- **WHEN** a socket sends an action naming another player
- **THEN** it is refused before validation, and the game state does not change

#### Scenario: A local game refuses the same action

- **WHEN** a single-player session dispatches an action naming a bot
- **THEN** it is refused with a reason, exactly as the room would refuse it

### Requirement: Actions are refused outside their phase

The validator SHALL reject any action that does not belong to the current game phase: during
setup only the setup actions, and once a round has been scored none at all. Turn-level and
sub-phase checks alone are insufficient, because a socket can send what a user interface would
never offer a button for.

#### Scenario: A client cannot draw before the deal is finished

- **WHEN** a socket sends a draw action while the game is still in setup
- **THEN** it is refused, and no card leaves the draw pile

#### Scenario: A scored round cannot be played on

- **WHEN** a socket sends any game action after the round has reached scoring
- **THEN** it is refused, and the final scores are unchanged

### Requirement: A round starts only when every player has peeked

Every player SHALL see two of their own cards before the round begins, and the action that ends
setup SHALL be refused while any player still has peeks outstanding. Bots SHALL be dealt their
peeks. A room SHALL therefore hold a newly dealt round in setup until the last human has looked
at their cards, for every round of the session and not only the first.

#### Scenario: One player cannot start the round over another

- **WHEN** one player has taken both peeks and finishes setup while another has taken none
- **THEN** it is refused, and the round stays in setup until the second player has looked

#### Scenario: A later round has its own setup

- **WHEN** the players agree to another round and it is dealt
- **THEN** it begins in setup, and every human peeks again before any turn action is accepted

### Requirement: A single-player game uses no server

A single-player game SHALL run entirely on the device: it SHALL NOT create a room, obtain a
token, or open a socket, and it SHALL present the same session interface as an online game so
that the user interface cannot distinguish them. This SHALL be verified by a test that fails
if any network call is attempted, rather than by inspection.

#### Scenario: A solo round is played offline

- **WHEN** a player starts a single-player game and plays a round to scoring
- **THEN** no network call of any kind is made, and the round scores normally

#### Scenario: The interface is the same one online play uses

- **WHEN** the user interface is given a local session
- **THEN** it reads the same redacted player view and dispatches the same actions as it would online

## MODIFIED Requirements

### Requirement: Game modes with humans and bots

Every game SHALL have exactly 4 players, without exception. The system SHALL support:
(a) one human vs 3 bots **offline and entirely on-device, with no server involvement**;
(b) an online 4-seat room with **two, three or four** humans on their own devices and the
remaining seats filled by bots; (c) all-human online tables of 4. An online room SHALL NOT
host a game with fewer than two humans, at the start or at any point afterwards. Every mode SHALL use the same
shared engine, validator, bot engine and recording format.

A single-player game SHALL NOT create a room, open a socket, or contact the server.

#### Scenario: Single player costs nothing to host

- **WHEN** a player starts a game against three bots
- **THEN** the engine and bots run in the app, no room is created, and the game is playable with no network

#### Scenario: Two humans and two bots

- **WHEN** a host creates a 4-seat room, one friend joins, and the host starts the game
- **THEN** the two empty seats are bots, the game plays to scoring, and each human sees only their own permitted information

#### Scenario: All-human table

- **WHEN** four humans join a 4-seat room
- **THEN** no bots are created and turn order follows seat order

### Requirement: An online room hosts a session of rounds

A room SHALL hold a session rather than a single deal: when a round reaches scoring the room
SHALL retain the result, present cumulative standings, and deal a further round on agreement,
until the session ends. Round scoring SHALL follow the rules — the caller against the lowest
coalition total, with a tie going to the caller — and the session SHALL award game points by
final rank.

#### Scenario: A second round keeps the standings

- **WHEN** a round finishes and the players agree to another
- **THEN** a new deal begins from a seed derived from the session seed, and the previous round's points are carried

#### Scenario: A session replays from one seed

- **WHEN** a finished session is replayed from its session seed and action log
- **THEN** every round deals identically and every state hash matches

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
