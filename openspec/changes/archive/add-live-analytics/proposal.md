# Change: Live analytics for every client, and unit economics for the room

## Why

Nothing in this game is measured. Not one number exists for how many people open it, how many
reach a second round, how many ever press "Play online", or what a room costs to run — and
the app is one deploy away from being in front of people who will answer all of those
questions by leaving.

Three specific holes, each of which costs something real:

- **The online funnel is entirely unknown.** Phase 9 built rooms, codes, invites and a lobby.
  Whether anybody gets from "Play online" to a second human in the room is the question that
  decides if that work was worth doing, and there is no way to ask it.
- **The unit cost of a room is unknown.** A room is a Durable Object with a 30-second CPU
  budget per request and three MCTS searches a turn. `PLATFORM-GATE.md` measured *one* worst
  case at 1.6 s. Nobody knows the distribution, so nobody knows what a thousand rooms costs —
  which is the number that decides whether online play can stay free.
- **Failures are invisible.** A wedged animation queue, a socket that never reconnects, a bot
  takeover that should not have happened: today these are things a player experiences and
  nobody hears about. §6i's answer is "prove it with people", which does not scale past four.

The constraint is that this must be nearly free. Cloudflare's free tier is the budget, the
client is a 3.7 MB wasm bundle that cannot afford an analytics SDK, and the phones are
someone else's battery.

## What Changes

- **The server measures the server.** The room already sees every join, action, disconnect,
  reconnect, bot takeover and round end — it is the authority, that is the whole design. It
  writes those to Workers Analytics Engine directly, at zero bytes over the wire and zero
  client cost. Clients are never asked to report what the server already knows.
- **Clients report only what the server cannot see**: solo games (which by design never touch
  the network — §6d), the menu funnel *before* a room exists, and client-side failures. That
  is a small, bounded event stream, not a telemetry pipe.
- **One beacon, four platforms, no SDK.** A `shared/client` sink over the existing `Net` seam
  — batched, fire-and-forget, capped per session, dropping rather than queueing. No
  dependency is added to any client.
- **Cost is a measured dimension, not a hope.** Every room event carries the Durable Object
  wall time and request count that produced it, so "what does a round cost" is a query rather
  than an estimate.
- **Privacy is the design, not a setting on it.** No cookies, no device identifiers, no IP
  storage, no nicknames, no room codes. GPC and Do-Not-Track are honoured before anything is
  sent, and an in-app opt-out sits with the other settings. This is not a courtesy: §6c
  already binds the portfolio to it, and a room code in an analytics store is a shared secret
  in a place a dashboard reads.
- **A dashboard nobody has to log into Cloudflare to read**: one Worker route rendering
  server-side from the Analytics Engine SQL API, with the API token held as a Worker secret
  and never reaching a browser.

## Non-goals

Per-user tracking, cohorts followed across days, advertising identifiers, session replay,
heatmaps, or any third-party analytics service. No A/B testing framework. No product
telemetry on the *lesson's* individual beats — the tutorial is measured as
started/finished/abandoned-at-stage, not keystroke by keystroke. Sentry (9.9) stays a separate
concern: crash reporting and analytics answer different questions and should not share a
pipe.

## Depends on

- `design-online-room-lifecycle` — rooms, codes and the registry are what the funnel measures,
  and the seat/token model is what makes anonymity enforceable rather than promised.
- `migrate-to-kotlin-multiplatform` phase 9 — the online client is what emits the funnel.

This change is a **release gate**: `docs/kotlin/README.md` §6i step 3 must not be walked, and
the Kotlin branch must not merge, until phases 1–4 below are done. A launch that cannot
measure its own funnel or its own cost is a launch that learns nothing from being launched.
