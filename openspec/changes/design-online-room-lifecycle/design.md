# Design: room lifecycle, identity and abuse limits

## Context

The room is a Cloudflare Durable Object per game (design D9), running the shared Kotlin
engine and bot compiled to JavaScript. It works: it deals, validates, redacts and plays bots.
Everything below is about the shell around that — who may create one, who a player is, and
when the object stops existing.

Two measurements shape the whole design and are worth stating before the decisions:

- **A single action can cost 1.6 s of CPU** when it hands three bots a turn each
  (`PLATFORM-GATE.md` 2a.1b: p50 4 ms, p95 590 ms, max 1,591 ms). CPU is what is billed and
  what an attacker can spend on your behalf. Creating rooms is the cheap attack; sending
  actions is the expensive one.
- **The whole game is 244 KB gzipped** in the Worker, so nothing here is constrained by
  bundle size.

## Goals / Non-Goals

**Goals**

- No unauthenticated action can take somebody's seat, choose their deal, or read their hand.
- A bounded, predictable cost per unit of abuse, without a human-verification step.
- Rooms that clean themselves up, so an abandoned game is not a permanent storage row.
- A session of rounds, as the rules describe, rather than a single deal.

**Non-Goals**

- Preventing a determined attacker from *playing badly on purpose*, or from occupying a
  public seat. Those are social problems, not budget ones.
- Perfect fairness under packet loss. A dropped player's seat is played by a bot; that is a
  gameplay decision, not a consistency guarantee.

## Decisions

### R1. Single-player never touches the network

One human against three bots runs entirely in the app: the shared engine and `BotRunner` are
Kotlin Multiplatform and already run on Android, iOS and Wasm. A local game costs nothing,
works on a plane, and has no latency.

This is the single largest lever on the hosting bill, and it is free: the code is the same
code. The only thing a room adds to a solo game is expense.

**Consequence**: the app has two session implementations behind one interface —
`LocalGameSession` (phase 6.2) and `RemoteGameSession`. The UI must not know which it has.

### R2. A room is a session of rounds, not a deal

`VINTO_RULES.md` describes a session: rounds are scored cumulatively (`Vinto +3 / Coalition
−1`, or the reverse), play continues to a time limit, and game points are awarded by final
rank. A room that dies at `scoring` cannot express any of that.

So the room holds a **session**: a list of completed round results plus the current
`GameState`. When a round reaches `scoring` the room moves to `BETWEEN_ROUNDS`, holds the
scoreboard, and deals again on agreement.

**States**: `LOBBY → STARTING → PLAYING ⇄ BETWEEN_ROUNDS → FINISHED`, then deletion. The
lobby rules that govern the first two are R2a.

**Consequence**: the seed is per *round*, derived from a session seed, so a session replays
end to end from one number. `initializeGame(seed)` stays exactly as it is.

### R2a. A game needs two humans; bots fill the rest, and anyone may add one

A room has four seats. A game **SHALL NOT start with fewer than two humans** — one person
against three bots is R1's case, and hosting it buys nothing that the device does not already
give away for free. A room may *contain* one human, briefly, between its creation and the
second person arriving; it simply cannot start.

Two, three or four humans are all valid tables. Empty seats are filled by bots, and **any
seated player may add or remove one** — not only the host. That is a deliberate flattening:
a lobby where only the creator can act stalls whenever the creator is the one who wandered off.

**Filling the fourth seat starts a 10-second countdown**, whoever or whatever fills it. One
rule for a human arriving and for a bot being added, so nobody can start a game the rest of
the table had no moment to react to. The countdown is public: it appears in the room and in
the registry's listing, so a stranger browsing sees "starting in 6s" rather than a room that
vanishes as they reach for it.

**The countdown is cancellable, and cancelling is how "anyone may add a bot" stays safe.**
Removing a bot, or a human leaving, empties a seat and returns the room to `LOBBY`. Refilling
restarts the full ten seconds rather than resuming a partial one — a countdown that could be
nudged along by rapid add/remove would be a way to deny the others their moment.

**A human displaces a bot** while the room is in `LOBBY` or `STARTING`. The friend you were
waiting for still gets in at t=8s. Once play begins the table is fixed, and a bot that has
taken over a *disconnected human's* seat is not displaceable by a stranger — that seat belongs
to its token (R3).

**Consequence**: `STARTING` is a real state, not a client-side animation. The countdown is an
alarm; a `setTimeout` would be lost the moment the object hibernates, which is precisely when
a lobby with nobody typing is most likely to be evicted.

### R3. Identity is a server-issued capability token

The room generates a random 32-byte `playerToken` when a client first takes a seat, returns
it **once** in the `joined` message, and stores only its SHA-256. Every later message carries
the token; the room compares hashes.

Why a token rather than a client-chosen id: a client-chosen id is a bearer credential that
its owner also gives to the server, the log, and anything that reads either. A server-issued
one is never guessable, and storing the hash means a leaked storage dump does not hand out
seats.

Why not accounts: they solve a different problem — *recovering* an identity on another
device — and that is a plausible thing to charge for later. Nothing here needs them.

**The seam**: `Seat.ownerId: String?`, null for every anonymous player. An account system
later maps an account to an `ownerId` and lets it reclaim seats; nothing else changes.

**Consequence**: losing the device loses the games. Accepted, and stated in the UI.

### R4. Rooms are created through a registry, never by naming one

Today `?room=<anything>` reaches `idFromName` and a Durable Object exists. The fix is that a
**room code must exist before a room does**.

A single `Registry` Durable Object owns the namespace. `POST /rooms` asks it for a code; it
mints one, records the room, and only then does anyone call `idFromName`. A join with an
unknown code is refused *by the registry*, so no object is created.

**Codes** are 6 characters from a 32-symbol alphabet with the ambiguous glyphs removed
(no `0/O`, `1/I/L`) — about 10⁹ combinations, short enough to read aloud. That is not enough
entropy to be a secret on its own, which is why scanning is answered by rate limiting (R6)
rather than by making codes longer and unspeakable.

**Consequence**: the registry is a single-object bottleneck for creation. That is acceptable
— creation is rare and the object is tiny — and it is also exactly where a global cap
belongs.

### R5. Timers, all on Durable Object alarms

Conflating "a player dropped" with "the room is over" either kills games during a tunnel or
leaves dead rooms billing, so each concern gets its own timer.

| timer | starts when | fires after | effect |
| --- | --- | --- | ---: |
| **countdown** | the fourth seat fills | 10 s | the game starts |
| **seat grace** | a seat's last socket closes | 30 s | a bot plays that seat; the seat stays reserved by its token |
| **lonely grace** | humans in the room drop below two, mid-session | 60 s | the room ends and is deleted |
| **room TTL** | the last human socket closes | 2 min | the room is deleted |
| **lobby TTL** | the room is created | 10 min | deleted if the game never started |
| **finished TTL** | the session ends | 10 min | deleted, after the scoreboard has been readable |

**Seat grace and lonely grace coexist and mean different things.** If three of four humans
drop, each of their seats is taken over by a bot after 30 s — that keeps the remaining game
playable — while the lonely grace runs in parallel and ends the room after 60 s, because a
lone human against three bots is R1's case and does not belong on the server. The seat timers
make the game survivable; the lonely timer decides whether it should.

A reconnect within the grace period resumes the seat with no bot takeover. A reconnect after
it resumes a seat a bot has been playing — the hand will have changed, and the client is told
so it can show that rather than look broken.

**Why alarms**: a Durable Object with no alarm and no socket is not running, so it cannot
time anything out by itself. `ctx.storage.setAlarm()` is the only mechanism that wakes it.

**Consequence**: every state transition must reschedule the alarm. A missed reschedule is a
room that never dies, which is why the gate asserts deletion rather than assuming it.

### R6. Rate limits in two places, because there are two costs

**At the edge** — Cloudflare Rate Limiting rules, keyed on IP:

- `POST /rooms`: a few per minute. Bounds room creation.
- WebSocket upgrades: bounds connection floods.

**In the registry** — a global cap on live rooms, and a per-IP cap on rooms owned
concurrently. The edge limits the *rate*; the registry limits the *total*, which is what
actually protects the budget over an hour.

**In the room** — a token bucket per socket over actions. This is the one that matters most,
because an action is up to 1.6 s of CPU and an edge rule cannot see inside a WebSocket. A
burst of 10 with a sustained rate of 1/s is far above human play and far below anything that
costs money.

**Deliberately not doing**: Turnstile on room creation. It is the strongest control and it
taxes every honest host on every game. The limits above are reversible; if evidence says they
are not enough, Turnstile goes in then.

### R7. Public and private rooms

- **Private** (default): the code is the invitation. Not listed anywhere.
- **Public**: listed by the registry, joinable by anyone until full.

A public room is the only place a stranger can occupy a seat, so it is the only place that
needs a policy on being abandoned — covered by R5's timers, which do not care why a seat
emptied.

**Consequence**: the registry's public list must be pruned when rooms die. A room that
deletes itself tells the registry; a registry entry with no live room is swept on read.

### R8. Nicknames are cosmetic, bounded and not unique

1–16 characters, letters, digits, spaces and a small punctuation set, trimmed and collapsed.
No uniqueness: two players may both be "Bob", and the UI disambiguates by seat.

They are **not** identity — R3 is. A nickname changing mid-session is a display change and
nothing else.

### R9. The seed is the server's

The room derives a session seed from `crypto.getRandomValues` at creation and never accepts
one from a client. Per-round seeds derive from it.

This is not a lifecycle concern but it lives here because it is the same class of bug as R3:
a value the client should not control, which the current code takes from a query string.

**Consequence**: `?seed=` is removed. The gate harnesses that used a fixed seed pass one
through a test-only path guarded by an environment variable, never a request parameter.

## Risks / Trade-offs

- **The registry is a single point of failure for creation.** Existing rooms are unaffected;
  only new ones fail. Accepted: sharding it is easy later and premature now.
- **A 6-character code is guessable at scale** if nothing rate-limits joins. R6 is therefore
  load-bearing, not defence in depth — the gate must test it.
- **Bot takeover after 30 s can lose a player their round.** The alternative — holding the
  seat indefinitely — stalls the other three. Thirty seconds is a guess; it is a constant in
  one place so it can be re-tuned on evidence.
- **Storing only the token hash means a lost token cannot be recovered.** That is the point,
  and it is what accounts would later fix.
- **Two humans can be one person with two devices.** Nothing detects it and nothing should
  try: it costs the attacker two connections to obtain a game they could have played offline
  for free, so it is not a budget attack. The limits in R6 bound it like any other traffic.
- **Ending a room when humans drop below two will occasionally annoy the survivor**, who
  wanted to finish against bots. The answer is that they can — locally, instantly, for free —
  and that hosting it is the one thing that costs money for no gain.

## Open Questions

- Does a session end on a round count, a wall-clock limit (the rules say ~30 minutes), or when
  players stop agreeing to another round? A wall clock in a room is a scheduling question, not
  an engine one, so it does not threaten determinism — but it needs a decision.
- Should the countdown be visible to a player who has not joined — that is, does the registry
  listing carry `startsAt`, or only a "starting" flag? Carrying the timestamp is friendlier
  and leaks nothing, but it does mean the registry is written to on every countdown.
