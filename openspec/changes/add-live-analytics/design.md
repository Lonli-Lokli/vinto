# Design: live analytics

## A1. The sink is Workers Analytics Engine

**Decision.** Events land in Workers Analytics Engine (WAE) via `env.ANALYTICS.writeDataPoint`
from the Worker and the Durable Object. Nothing else stores analytics.

**Why not the alternatives**, each rejected for a specific reason rather than taste:

| Option | Why not |
| --- | --- |
| D1 | A row per event is a write per event, and the free tier is a daily row budget. It is a database, so it needs a schema, a migration story and a retention job — three things to maintain for data nobody edits |
| KV | Eventually consistent with a write-per-key ceiling; counters in KV are a well-known way to lose counts |
| Durable Object storage | Serialises every write through one object. An analytics sink that is a single point of contention in front of the game's own hot path is a bad trade |
| A third-party SDK | Bytes in a 3.7 MB wasm bundle, a second privacy policy, and a vendor between us and our own numbers |
| Logpush | Paid plan only |

WAE is the right shape because `writeDataPoint` is **non-blocking and does not count against
the invocation's CPU time** — which matters more here than anywhere, since the thing being
measured is a Durable Object with a CPU budget the game is already spending on MCTS. It
samples and aggregates on the way in, so cost does not scale linearly with players, and it is
queried with SQL rather than through a vendor console.

**Verify before building**: WAE's free-tier write and read allowances, and whether the account
plan covers it, change over time. Task 1.1 is to confirm the current numbers against the live
account and write them into this file — not to trust this paragraph.

## A2. The schema is three columns, and the shape is forced

WAE gives every data point `blobs` (strings), `doubles` (numbers) and `indexes` (one
high-cardinality key). That is the whole schema, so the discipline is in what goes where:

- `indexes[0]` — the **event name** (`round_end`, `room_created`, `seat_filled`). One index is
  all WAE allows and the event name is what every query groups by.
- `blobs` — low-cardinality strings only: platform, app version, surface (`solo`/`online`/
  `lesson`), outcome, error kind. Anything with more than a few dozen values is a mistake here.
- `doubles` — counts and durations: round length, turns, DO wall-time, request count, hand
  totals.

**Nothing identifying goes anywhere.** Not the room code, not the nickname, not the seat
token, not an IP. The rule is enforced by construction rather than by review: the emit helper
takes a closed `AnalyticsEvent` sealed type, and there is no field on it that can carry a
free string from a player.

## A3. The server measures the server

The room is authoritative — it deals, validates, redacts and plays the bots (§6f). It
therefore already holds every fact worth counting about an online game, and asking clients to
report those facts would be paying twice for a worse answer: client reports can be lost,
delayed, duplicated or forged, and the server's cannot.

So the split is by **who can know**, not by what is convenient:

| Question | Measured by | Because |
| --- | --- | --- |
| Rooms created, seats filled, rounds played, bot takeovers, reconnects, session length | Worker / DO | It is the authority and it is free to it |
| What a room cost (DO wall time, requests) | DO | Only it can see this |
| Solo games — started, finished, difficulty, round length | Client | §6d: a solo game creates no room, no token and no socket |
| The lesson — started, finished, abandoned at which stage | Client | Same: it never touches the network |
| Menu funnel before a room exists | Client | There is no server yet at that point |
| Client failures — a wedged stage, a socket that gave up, a render error | Client | The server cannot see a client that broke |

This is what keeps the client stream small enough to be free. An online round produces **one**
client event (it started) and a handful of server ones.

## A4. The client beacon is a sink, not a pipeline

In `shared/client`, over the `Net` seam that already exists — so no new dependency, and it
works on wasm, Android, iOS and JVM without an `expect`/`actual` of its own.

Four rules, each answering a way this normally goes wrong:

- **Batched and coalesced.** Events buffer and flush on a timer or at a size cap, whichever
  comes first, and on app background where the platform allows it.
- **Fire-and-forget.** No retry, no persistence, no queue that survives a restart. A lost
  analytics event is worth nothing; a client that spends battery re-sending one is worth less
  than nothing.
- **Capped per session, dropping the newest.** A bug that emits in a loop must cost a bounded
  amount, and dropping the newest keeps the beginning of the session — which is the part that
  explains what happened.
- **Never on the hot path.** Emission cannot block a move, an animation frame, or a socket
  write. It is a `trySend` to a buffer and nothing more.

`LocalPacing` set this precedent already: the table must not be made slower by something that
is not the game.

## A5. Anonymity is structural

- **No identifier is stored or sent.** Not a device id, not an install id, not a hashed IP.
- **A session is a random value that lives in memory** for the life of the process and is
  never persisted — enough to group events within one sitting, impossible to follow across
  two.
- **Retention counting is deliberately given up.** Cookieless day-over-day retention needs
  something stable per device, and every trick for it (rotating salted IP hashes, fingerprints)
  is tracking with extra steps. Vinto counts *sessions*, not people. This is a real
  measurement loss and it is accepted on purpose; the alternative is a promise in §6c that the
  code does not keep.
- **GPC and Do-Not-Track are checked before the first event**, not filtered later.
- **An opt-out sits in Settings** beside sound and haptics, worded as what it does.

## A6. The dashboard renders on the server

A route on the room Worker queries the WAE SQL API with an API token held as a Worker secret
and returns rendered HTML. The token never reaches a browser, there is no client-side
querying, and there is no second app to deploy.

Access: a bearer secret in the URL for now, because the alternative is an auth system this
project does not otherwise need. It is a read-only view of aggregate counts with nothing
identifying in it, which is what makes that proportionate — and it is written down here so
that adding accounts later is a decision rather than a discovery.

## A7. The web client keeps Cloudflare Web Analytics as well

Free, cookieless, and it answers what the beacon deliberately cannot: page loads, referrers
and Core Web Vitals for the Pages-hosted wasm client. §6c already requires counting from the
loader rather than the bundle so that a visitor whose browser cannot run WasmGC still counts —
that is exactly what this gives, and the two do not overlap.

## A8. Sampling is per-event and declared

Not one global rate. Rare, decision-shaped events (`room_created`, `round_end`, any error) are
never sampled. High-frequency ones are, at a rate written next to the event, and every sampled
event carries its rate as a double so a query can weight it correctly rather than quietly
under-reporting.
