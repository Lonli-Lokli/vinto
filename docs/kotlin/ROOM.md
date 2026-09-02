# The room: deploying it, opening it, and the game that needs none of it

The Durable Object from its first deployment to the day it opened, what a solo game
deliberately does not touch, and the runbook for taking the whole thing live.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 6d. Deploying the engine, with no UI

The Worker carries the real engine and exposes `POST /replay`: send a `GameRecording`, get
back `ok` or the exact action that diverged. That is enough to verify the engine on a real
deployment before any UI exists.

```bash
cd worker/cloudflare
(cd ../.. && ./gradlew :worker:jsProductionExecutableCompileSync)
npx wrangler dev --port 8787 --local
node gate-engine-replay.mjs            # 50/50, 13,900 actions

# against a deployment
npx wrangler deploy                    # needs `wrangler login` first
GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs
```

**The first deploy needs zone permissions**, not just Workers ones: `custom_domain: true`
makes wrangler create the `vinto-room.kupalinka.app` DNS record in the `kupalinka.app` zone.

### A deployment today is a self-test, and the code enforces that

`ROOM_OPEN` defaults to `"false"`, and a WebSocket upgrade against a closed room is refused
with **503** and the reason. This is a gate in `index.mjs`, not a note here, because the
consequence of forgetting is a deployed Durable Object accepting _any_ action from _any_
client — `ActionValidator` permits everything, and design D9 puts server-side validation at the
centre of the anti-cheat model. Flip it to `"true"` in the same commit that lands the
validator, never before.

What a deployment does answer:

| endpoint          |                                                                             |
| ----------------- | --------------------------------------------------------------------------- |
| `GET /health`     | `{"ok":true,"service":"vinto-room","engine":"kotlin","roomOpen":false}`     |
| `POST /replay`    | replays a `GameRecording` through the real engine; bodies over 1 MB refused |
| WebSocket upgrade | 503, with the reason                                                        |

`/replay` is a pure function of the posted document — it holds no state and mutates nothing —
which is what makes it safe to expose while the validator is missing. The 1 MB cap is there
because it is public and CPU-bound at roughly 1 ms per action; the largest recording in the
corpus is 141 KB.

Tearing it down is `npx wrangler delete`, so this is a cheap thing to try and an easy thing to
undo.

### What the first real deployment taught, that local could not

Deployed 2026-08-19 to `vinto-room.kupalinka.app`. All 50 recordings and 13,900 actions replay
on the live edge. Three things surfaced that `wrangler dev` had no way to show:

- **`/replay` belonged in the Durable Object, not the Worker.** A plain Worker gets ~10 ms of
  CPU per invocation; a Durable Object gets 30 s per request. Replaying one game costs ~250 ms,
  so production answered `error code: 1102` — the exact limit design D9 cites as the reason the
  room is a Durable Object at all. `wrangler dev` enforces no CPU limit whatsoever, and the
  production limit is applied on a rolling average, so single requests passed while a batch did
  not. D9 was right; the endpoint was in the wrong place.
- **A plain `GET /?room=anything` created a Durable Object and wrote it to storage**, for any
  name a stranger cared to invent, and read it back. Locally that was a reasonable inspection
  aid for the 2a.3 harness. The `ROOM_OPEN` gate now sits above it, so a closed deployment
  creates nothing and discloses nothing. The only thing that had changed about the code was
  that it was on the internet.
- **Propagation is not atomic, and it caught me twice.** Verifying seconds after `wrangler
deploy` returned the _old_ behaviour and sent me chasing a second bug that did not exist; then
  one probe seeing the new behaviour did not mean every edge node had it. Poll until the
  behaviour changes, then keep checking. Same hazard the portfolio brief documents for Pages
  deploys, in a different costume.

Run `GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs` after any deploy.
It backs off on 503 and **counts** the retries, so throughput pressure stays visible instead of
being quietly absorbed.

Why this is worth having rather than trusting the JVM gate: Kotlin/JS represents `Long` as a
pair of `Int`s and uses a different serialiser backend, so passing on the JVM does not imply
passing on Cloudflare. It now passes on both.

**What must be true before a deployment takes real client input.** `/replay` is safe to
expose — it is a pure function of the posted document. The WebSocket room is not yet, but the
reason has changed: `ActionValidator` is now ported in full and is the boundary design D9
depends on, so what is missing is that the `Room` Durable Object still runs placeholder logic
instead of calling `GameEngine.reduce`. `ROOM_OPEN` stays `"false"` until it does.

Deploying needs a Cloudflare account and `wrangler login`; `wrangler deploy` should be a
deliberate decision rather than a side effect of a build.

## 6r. Single-player runs on the device

> Renumbered. This was a **second §6d** — the letter was used twice, once for deploying the
> engine and once for this — and moving both into one file made the collision unreadable. An
> older reference to "§6d" that is about the validator's phase gate or the no-network guard
> means this section.

A solo game creates **no room, no token and no socket**. `shared:client` holds a `GameSession`
interface that a local game and an online one both implement, so a screen cannot tell which it
has — which is what keeps the free single-player mode free to host, rather than a Durable
Object running three MCTS searches a turn for one person.

`LocalGameSession` is the engine and `BotRunner` in-process. It reads the same redacted
`PlayerView` the server sends, validates through the same `ActionValidator`, and enforces the
same seat boundary from the same `GameAction.actorId` the Durable Object uses. A local game
that let the UI act for a bot would be teaching the UI a habit that fails online.

The claim is gated rather than asserted: `NoNetworkGuardTest` plays a whole round with a
`SecurityManager` installed that throws on any connect, listen or accept, and proves the guard
bites — three deliberate calls that must fail — before trusting the round that follows.

Two things that gate turned up, both faithful ports of TypeScript behaviour that only a UI was
keeping shut, and both fixed:

- the validator had no **phase** gate, only turn and sub-phase checks, so `DRAW_CARD` passed
  during setup and again after scoring. Never reachable from a button; entirely reachable from
  a socket.
- `PEEK_SETUP_CARD` validated the player it *named* rather than the one acting, so one player
  could spend another's setup peeks.

## 6q. The room is open

Deployed and opened on 2026-08-30, from a phone, through `deploy-room.yml`. §6i step 4 is done.

```
$ curl https://vinto-room.kupalinka.app/health
{"ok":true,"service":"vinto-room","engine":"kotlin","roomOpen":true}
$ curl https://vinto-room.kupalinka.app/rooms
{"rooms":[]}
$ curl -X POST .../rooms -d '{"isPublic":false,"hostNickname":"probe"}'
{"code":"TBPHAY","roomId":"room-TBPHAY"}
```

Two gates then ran **against the live deployment** rather than against `wrangler dev`, which is
the difference §6d exists to record — production applies a CPU limit and a local runtime applies
none:

| | |
| --- | --- |
| `GATE_URL=… node gate-engine-replay.mjs` | 50/50 recordings, 13,900 actions, 46.3 s. **PASS** |
| `GATE_URL=… node gate-two-clients.mjs` | Two sockets joined one room, exchanged actions, each was sent only its own seat's view, a token reclaimed its seat after a disconnect, a nickname did not, resync returned only what was missed, and the room rebuilt from storage after every socket closed. **PASS** |

The second is the scripted half of §6i step 5, now true of the real thing: two clients played
through a Durable Object on the edge, including hibernation and reconnect. What is left of that
step is the part that cannot be scripted — two devices in two hands, and then four humans.

**The probes left rooms behind**, deliberately noted rather than tidied away: `TBPHAY` and
whatever `gate-two-clients` minted are real private rooms on the live registry. They cost
nothing, nobody has their codes, and `gate-lifecycle` is the gate for the sweep that removes
them; a room that outlived its own expiry would be a bug worth seeing.

## 6i. Taking the room live — the maintainer's runbook

The online client is code-complete: protocol, room cores with JVM tests, per-event views,
recordings, pacing, `RemoteGameSession`, lobby screens, and a two-client harness that plays a
full round through all of it (`TwoClientGameTest`). What remains is the part only a person
with credentials and hardware can do, in this order:

**1. Verify locally, on the machine that can.** This container cannot compile `composeApp`
(androidx lives behind dl.google.com), so the UI-adjacent work ships verified by
`:composeApp:detekt` plus everything the shared modules prove. Run the rest:

```sh
./gradlew :shared:shapes:jvmTest :shared:engine:jvmTest :shared:bot:jvmTest \
          :shared:client:jvmTest :shared:protocol:jvmTest :shared:room:jvmTest
./gradlew :composeApp:jvmTest       # the compose suites, FullGameUiTest included
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest          # writes goldens
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest --rerun  # proves them stable
```

Commit the eight goldens `ScreenshotTest` writes (`composeApp/src/jvmTest/goldens/`). CI does
**not** run that suite — on a fresh runner it would write its own goldens and pass, asserting
nothing, and a maintainer's images would not survive a different JVM's glyph rasterization
anyway (the exclusion, and its reasoning, is on the test task in `composeApp/build.gradle.kts`;
`-Pscreenshots` forces it back on). Run the desktop app once and listen: four sounds — a deal, a landing, a thud on a penalty, a chime at
the round's end — and none anywhere else.

**2. Exercise the rewired worker against `wrangler dev`.** `index.mjs` now sends prebuilt
per-seat envelopes and files recordings; the gate scripts import the *unchanged* exports and
still pass, but `gate-real-room.mjs` and `gate-sessions.mjs` walk the rewired paths:

```sh
./gradlew :worker:compileKotlinJs
cd worker/cloudflare && npx wrangler dev &   # then, against it:
node gate-real-room.mjs && node gate-sessions.mjs && node gate-lobby.mjs \
  && node gate-lifecycle.mjs && node gate-limits.mjs && node gate-room-codes.mjs
```

**3. Land the analytics gate — before the deploy, not after it.**
`openspec/changes/archive/add-live-analytics`, phases 1–4. This is a blocking step and the reason is
arithmetic rather than principle: an event not collected on launch day is a question that can
never be asked about launch day. There is no backfill for "how many people who opened it ever
pressed Play online", and that number is the one that says whether phase 9 was worth building.

Two of the four phases are cheap because the server already knows the answers — the room is
authoritative, so its lifecycle and round events cost nothing over the wire — and one of them
(2.3) is what finally answers what a room *costs*, which is the number that decides whether
online play can stay free. The fourth is the privacy gate: HOSTING.md §6c already binds this
zone to no cookies, no identifiers and GPC honoured, and task 4.4 turns that paragraph into a
test that plays three rounds and asserts nothing identifying left the device.

```sh
cd worker/cloudflare && npx wrangler dev &
node gate-analytics.mjs      # the event sequence, and the empty-binding case
./gradlew :shared:client:jvmTest --tests '*Analytics*'
```

Phase 5 (the dashboard, the Web Analytics beacon, revisiting sampling against real volume) is
deliberately **not** blocking — it reads data that does not exist until this has shipped.

**4. Deploy and open the room — one deploy, both halves.** `ROOM_OPEN` stays `"false"` until
the client that speaks to it ships; flip it in the same deploy that publishes the client
builds, never before (the flag's comment in `wrangler.jsonc` says the same):

```sh
npx wrangler deploy          # then poll:
curl https://vinto-room.kupalinka.app/health   # expect roomOpen: true after the flip
```

**Or from a phone.** `.github/workflows/deploy-room.yml` is the same deploy run by GitHub —
*Actions → Deploy room → Run workflow* — which works in the GitHub mobile app. It runs the
room's gates first, deploys with `--var ROOM_OPEN:<your answer>` rather than editing
`wrangler.jsonc`, and then **polls `/health` until the edge agrees**, failing if it never does:
propagation is not atomic and §6d records that catching the maintainer out twice. It is
`workflow_dispatch` only and the flag defaults to `false` on every run, because deploying is a
decision and opening a room to strangers is a bigger one. Setting it up is two web pages and no
computer — DEPLOYMENT.md §6a.

**5. Prove it with people.** Two devices (or a device and the desktop app): create a room,
join by code, add two bots, play a round through — kill one app mid-round and watch the seat
go bot and come back on relaunch. Then the four-human table (9.7's second verification),
which needs four hands and cannot be scripted.

**Still open by design**: 9.9 (Sentry on the worker, a load test with 100 rooms) and 9.10
(store releases with multiplayer enabled) — operational work that starts after this runbook
has been walked once. Sentry stays separate from step 3 on purpose: crash reporting and
analytics answer different questions and should not share a pipe.
