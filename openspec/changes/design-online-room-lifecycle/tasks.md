# Tasks: room lifecycle, identity and abuse limits

Ordered so that the three open holes close first. Each phase is independently shippable and
independently verifiable through the gate harnesses; none of it needs the Compose UI.

## 1. Close what is open (security, before anything else)

- [ ] 1.1 Server-issued `playerToken`: minted on first seat, returned once in `joined`, stored
      as a SHA-256 hash, required on every later message. `clientId` from the client is
      removed entirely rather than kept alongside
- [ ] 1.2 `Seat.ownerId: String?` added and left null — the account seam, populated by nothing
- [ ] 1.3 Server-chosen session seed; `?seed=` removed from the request surface. A test-only
      seed path stays, guarded by an env var, so the gate harnesses remain deterministic
- [ ] 1.4 Gate: a socket that sends another seat's token, no token, or a wrong token is refused
      and is never sent that seat's view. Negative controls confirm each check bites

## 2. Registry and room codes

- [ ] 2.1 `Registry` Durable Object: mint a 6-character code from the unambiguous alphabet,
      record the room, resolve a code to a room, forget a room when it dies
- [ ] 2.2 `POST /rooms` creates through the registry; a WebSocket upgrade for an unknown code
      is refused **without** creating a Durable Object
- [ ] 2.3 Public/private flag; the registry lists public rooms and sweeps entries whose room
      is gone
- [ ] 2.4 Gate: an invented code creates nothing (asserted by the object not existing, not by
      the response text); a private room is joinable by code and absent from the list

## 3. Lifecycle

- [ ] 3.1 Room states `LOBBY → PLAYING ⇄ BETWEEN_ROUNDS → FINISHED`, with the alarm
      rescheduled on every transition
- [ ] 3.2 Seat grace (30 s) → bot takes over, seat stays reserved by token
- [ ] 3.3 Room TTL (2 min with no human), lobby TTL (10 min unstarted), finished TTL (10 min)
- [ ] 3.4 Deletion tells the registry, so the public list cannot outlive its rooms
- [ ] 3.5 Reconnect after a takeover tells the client its hand changed while it was away
- [ ] 3.6 Gate: an abandoned room is actually gone — asserted by a later join being refused,
      with the alarm driven rather than waited for

## 4. Abuse limits

- [ ] 4.1 In-room token bucket over actions per socket (burst 10, sustained 1/s), refusing
      with a retry signal rather than serving. This is the expensive path: one action can be
      1.6 s of CPU
- [ ] 4.2 Registry caps: global live rooms, per-source concurrent rooms
- [ ] 4.3 Edge rate limiting rules for `POST /rooms` and WebSocket upgrades, recorded in
      `wrangler.jsonc` or as documented dashboard configuration
- [ ] 4.4 Message size cap and a maximum socket count per room
- [ ] 4.5 Gate: an action flood is throttled and performs no bot search; the room cap holds

## 5. Sessions of rounds

- [ ] 5.1 `SessionState`: completed round results, cumulative points, current `GameState`
- [ ] 5.2 Round scoring per the rules (caller vs lowest coalition, tie to the caller) and game
      points by final rank
- [ ] 5.3 Per-round seeds derived from the session seed; a whole session replays from one number
- [ ] 5.4 Between-rounds agreement flow, and what happens when somebody declines
- [ ] 5.5 Gate: a two-round session replays from its session seed with every state hash matching

## 6. Single-player stays off the network

- [ ] 6.1 `LocalGameSession` behind the same interface as the remote one, so the UI cannot
      tell them apart (migrate-to-kotlin-multiplatform task 6.2)
- [ ] 6.2 Guard: a single-player game opens no socket and creates no room. Asserted by a test
      that fails if any network call is attempted, not by inspection

## 7. Nicknames

- [ ] 7.1 Validation: 1–16 characters, restricted character class, trimmed and collapsed
- [ ] 7.2 Seat-based disambiguation in the view, since nicknames are not unique
