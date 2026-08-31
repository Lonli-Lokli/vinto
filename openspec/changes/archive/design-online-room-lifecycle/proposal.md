# Change: Room lifecycle, identity and abuse limits for online play

## Why

The Durable Object now runs the real game — it deals, validates, redacts per seat and plays
the bots. What it has no notion of is a *room*: how one is created, who is allowed to make
one, how a player proves they are who they say, when a room dies, and what stops a script
from turning a free-tier budget into someone's afternoon.

Three of those are open holes today rather than missing features:

- **`clientId` is whatever the client sends.** `joinRoom` is idempotent by it and nothing
  verifies it, so sending somebody else's takes their seat — and the room then sends that
  socket their redacted view, which is their hand.
- **The client chooses the seed.** `?seed=42` picks the deal; reload until it is a good one.
- **Any string creates a room.** `?room=<anything>` maps to a Durable Object, which then
  exists and is billed. Nothing bounds how many.

There is also no lifecycle at all: no alarm, no cleanup, no TTL. A room created once lives
until Cloudflare is told otherwise.

## What Changes

- **Single-player never touches the network.** One human against three bots runs entirely
  in the app on the shared engine and bot. Rooms exist for playing with other people.
- **A room is a session of rounds**, not one deal — matching the written rules, which score
  cumulatively and award game points by rank.
- **A game needs two humans.** Two, three or four are valid tables; bots fill the rest, and
  any seated player may add or remove one. Filling the fourth seat starts a public ten-second
  countdown, which any player can cancel by emptying a seat again. A room that drops below two
  humans mid-session ends, because a lone player against bots belongs on the device.
- **Identity becomes a server-issued capability token.** The room generates it on first join,
  returns it once, and every later message carries it. Nicknames are cosmetic and separate.
  A nullable `ownerId` on the seat leaves the seam for accounts later.
- **Rooms are created through a registry**, never by naming one. A code must exist before a
  Durable Object does, which closes the create-by-URL hole and gives public rooms somewhere
  to be listed.
- **Two timers, not one**: a short grace period before a bot takes over a dropped seat, and a
  separate TTL before the room itself is deleted. Both on Durable Object alarms.
- **Rate limits in two places**: at the edge for room creation and connections, inside the
  room for actions — because an action that triggers three bot turns costs up to 1.6 s of
  CPU, which is far more expensive than creating a room.
- **The seed is the server's**, and is never accepted from a client.

## Non-goals

- Accounts, login, or any paid identity tier. The seam is left; nothing is built.
- Spectators, hot-seat on one device, or tables of other than four players.
- Moderation tooling, reporting, or a nickname blocklist beyond a character-class rule.
- Matchmaking or skill rating. Public rooms are a list, not a queue.
- Cross-device continuation of a game. Losing the device loses the token, and that is
  accepted until accounts exist.

## Dependencies

Depends on `migrate-to-kotlin-multiplatform` (phases 4 and 5 — the ported engine, validator
and bot, and the room that runs them). Does not depend on the Compose UI: every requirement
here is verifiable through the gate harnesses without a client.
