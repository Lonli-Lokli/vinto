# Tasks: room lifecycle, identity and abuse limits

Ordered so that the three open holes close first. Each phase is independently shippable and
independently verifiable through the gate harnesses; none of it needs the Compose UI.

## 1. Close what is open (security, before anything else)

- [x] 1.1 Server-issued `playerToken`: minted on first seat, returned once in `joined`, stored
      as a SHA-256 hash, required on every later message. `clientId` from the client is
      removed entirely rather than kept alongside
- [x] 1.2 `Seat.ownerId: String?` added and left null — the account seam, populated by nothing
- [x] 1.3 Server-chosen session seed; `?seed=` removed from the request surface. A test-only
      seed path stays, guarded by an env var, so the gate harnesses remain deterministic
- [x] 1.4 Gate: a socket that sends another seat's token, no token, or a wrong token is refused
      and is never sent that seat's view. Negative controls confirm each check bites

## 2. Registry and room codes

- [x] 2.1 `Registry` Durable Object: mint a 6-character code from the unambiguous alphabet,
      record the room, resolve a code to a room, forget a room when it dies
- [x] 2.2 `POST /rooms` creates through the registry; a WebSocket upgrade for an unknown code
      is refused **without** creating a Durable Object
- [x] 2.3 Public/private flag; the registry lists public rooms. `forgetRoom` is implemented
      and idempotent; the **caller** is task 4.5, when a room learns how to die — sweeping
      needs liveness, and liveness needs the lifecycle
- [x] 2.4 Gate: an invented code creates nothing (asserted by the object not existing, not by
      the response text); a private room is joinable by code and absent from the list

## 3. Lobby and start conditions

- [ ] 3.1 Four seats; any seated player may add or remove a bot while the room has not started
- [ ] 3.2 A game may not start with fewer than two humans, however the seats are filled
- [ ] 3.3 The fourth seat filling begins a 10 s countdown, held on an **alarm** so it survives
      hibernation; emptying a seat cancels it and refilling restarts the full ten seconds
- [ ] 3.4 A human displaces a bot in `LOBBY`/`STARTING`; a bot holding a disconnected human's
      seat is not displaceable, because that seat belongs to its token
- [ ] 3.5 The countdown is public: broadcast to the room and reflected in the registry listing
- [ ] 3.6 Gate: a lone player with three bots never starts; a forced start is undone by removing
      the bot; a seat emptied at t=7s restarts the full countdown; the countdown survives an
      eviction (driven by firing the alarm, not by waiting)

## 4. Lifecycle

- [ ] 4.1 Room states `LOBBY → STARTING → PLAYING ⇄ BETWEEN_ROUNDS → FINISHED`, with the alarm
      rescheduled on every transition
- [ ] 4.2 Seat grace (30 s) → bot takes over, seat stays reserved by token
- [ ] 4.3 Lonely grace (60 s below two humans, mid-session) → the room ends and deletes itself,
      running in parallel with seat grace rather than instead of it
- [ ] 4.4 Room TTL (2 min with no human), lobby TTL (10 min unstarted), finished TTL (10 min)
- [ ] 4.5 Deletion tells the registry, so the public list cannot outlive its rooms
- [ ] 4.6 Reconnect after a takeover tells the client its hand changed while it was away
- [ ] 4.7 Gate: an abandoned room is actually gone — asserted by a later join being refused,
      with the alarm driven rather than waited for; and a game that drops to one human ends

## 5. Abuse limits

- [ ] 5.1 In-room token bucket over actions per socket (burst 10, sustained 1/s), refusing
      with a retry signal rather than serving. This is the expensive path: one action can be
      1.6 s of CPU
- [ ] 5.2 Registry caps: global live rooms, per-source concurrent rooms
- [ ] 5.3 Edge rate limiting rules for `POST /rooms` and WebSocket upgrades, recorded in
      `wrangler.jsonc` or as documented dashboard configuration
- [ ] 5.4 Message size cap and a maximum socket count per room
- [ ] 5.5 Gate: an action flood is throttled and performs no bot search; the room cap holds

## 6. Sessions of rounds

- [ ] 6.1 `SessionState`: completed round results, cumulative points, current `GameState`
- [ ] 6.2 Round scoring per the rules (caller vs lowest coalition, tie to the caller) and game
      points by final rank
- [ ] 6.3 Per-round seeds derived from the session seed; a whole session replays from one number
- [ ] 6.4 Between-rounds agreement flow, and what happens when somebody declines
- [ ] 6.5 Session clock: 30 minutes from the **first deal**, held on an alarm in the room. The
      engine is never given wall-clock time — the purity guard already enforces that and must
      keep passing
- [ ] 6.6 At the buzzer: a round with Vinto declared plays out and is scored; any other round
      is discarded. Uniformly, including when no round has completed — a session may end with
      no winner. A new round is always dealt while the session is live
- [ ] 6.7 Remaining time in `PlayerView`, because the discard rule only makes calling Vinto a
      decision if players can see the deadline
- [ ] 6.8 The room's log records which round was discarded; standings cannot be recomputed from
      the round recordings alone
- [ ] 6.9 Gate: a two-round session replays from its session seed with every state hash
      matching; a buzzer with Vinto declared finishes and scores; a buzzer without one
      discards; a session whose first round is still running ends with no winner

## 7. Single-player stays off the network

- [ ] 7.1 `LocalGameSession` behind the same interface as the remote one, so the UI cannot
      tell them apart (migrate-to-kotlin-multiplatform task 6.2)
- [ ] 7.2 Guard: a single-player game opens no socket and creates no room. Asserted by a test
      that fails if any network call is attempted, not by inspection

## 8. Nicknames

- [ ] 8.1 Validation: 1–16 characters, restricted character class, trimmed and collapsed
- [ ] 8.2 Seat-based disambiguation in the view, since nicknames are not unique
