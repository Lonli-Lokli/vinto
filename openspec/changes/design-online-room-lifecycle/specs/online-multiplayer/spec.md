# online-multiplayer

## ADDED Requirements

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

### Requirement: Nicknames are display-only

A nickname SHALL be 1–16 characters after trimming, restricted to letters, digits, spaces and
a small punctuation set, and SHALL NOT be required to be unique. A nickname SHALL NOT be used
to identify, authorise or seat a player.

#### Scenario: Two players share a nickname

- **WHEN** two clients join the same room with the same nickname
- **THEN** both are seated, each with their own token, and the view distinguishes them by seat

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
