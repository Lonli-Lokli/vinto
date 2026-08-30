# Tasks: live analytics

Phases 1–4 are the **release gate** — the Kotlin branch does not merge and §6i step 3 is not
walked until they are ticked. Phase 5 is the follow-up that only becomes answerable once real
traffic exists, and is explicitly *not* blocking.

Each phase is independently verifiable without the Compose UI, in the way the room's own
phases were: the Worker half is exercised by a gate script through `wrangler dev`, and the
client half by JVM tests against a fake sink.

## 1. The sink, and what it is allowed to hold

- [ ] 1.1 **BLOCKED** (§1f): needs the Cloudflare dashboard for this account. Confirm the account's **current** Workers Analytics Engine allowances — writes/day,
      reads, retention, and whether the plan covers it — against the live dashboard, and
      record the real numbers in `design.md` §A1. Do not build on the figures in this file
- [x] 1.2 `ANALYTICS` binding in `wrangler.jsonc`, absent-safe: the Worker and the room must
      run identically with no binding, so local `wrangler dev` and every gate script keep
      working without a Cloudflare account
- [x] 1.3 `AnalyticsEvent` as a sealed type in **`shared/protocol`** (moved: both the room and the clients need the vocabulary, which is what that module is for), with the `blobs`/`doubles`/
      `indexes` mapping in one place. No field on it may carry a free string from a player
- [x] 1.4 Gate: a test that asserts the event surface holds no room code, no
      nickname, no token and no seat identifier — the check that keeps §A2 true as fields
      are added

## 2. The server measures the server

- [x] 2.1 Room lifecycle events — all wired. `room_created` on a successful mint, `seat_filled`/`reconnected` on join (a returning token is a comeback, not a new player), `seat_vacated` on socket close, and `bot_took_over`/`session_ended`/`round_start`/`round_end` derived in `#observe` by comparing the state a request read with the state it produced. Verified by `gate-analytics.mjs` driving a real room. Original: `room_created`, `seat_filled`, `seat_vacated`, `bot_took_over`,
      `reconnected`, `session_ended` — with the reason each ended
- [x] 2.2 Round events. `actions` rather than `turns` — the room can count its slice of the log exactly (`roundStartLogIndex`) and cannot count turns without guessing how actions group. `durationMs` needed a new `roundStartedAtEpochMs` on `RoomState` (additive, defaulted, so a stored room still decodes); `callerWon` comes from the caller's own points being positive, which is the rule in VINTO_RULES.md. Was: carrying turns, duration, how it ended
      (Vinto called, deck exhausted) and whether the caller won
- [x] 2.3 **Cost dimensions on every room event** — `#emit` stamps wall time since the instance woke and a per-instance request count on every server event, so cost per room is a division rather than an estimate. Was: Durable Object wall time and request count
      for the invocation that produced it. This is the number that decides whether online play
      stays free, and it is free to collect here
- [x] 2.4 `gate-analytics.mjs`, 41 checks: the closed-vocabulary rule on every rendered point, a smuggled room code dropped, the empty-binding case, and a real room driven through the Kotlin core asserting that a deal emits `round_start` **and nothing else**, that a request changing nothing emits nothing, and that the deal time recorded is the clock it was dealt on. Caught its own bug first: dealing inside the ten-second countdown is not a deal plays a full room and asserts the exact event
      sequence against a fake sink — including that a room played with no binding emits
      nothing and still finishes the round

## 3. The client reports only what the server cannot see

- [x] 3.1 `Analytics` sink in `shared/client` over the `Net` seam: batched, capped,
      fire-and-forget, dropping the newest, never on the hot path (§A4)
- [x] 3.2 Solo and lesson events. Counted from the *screen* rather than the model, because a round the engine finished and the player walked out of is a different fact from one they watched end — `DisposableEffect` fires however the screen goes away, so abandonment is the default and finishing overwrites it. The lesson carries how many chapters it reached. Verified by `AnalyticsPrivacyUiTest` walking both surfaces with the sink recording, and by `:composeApp:jvmTest` staying green. Was: started, finished, abandoned-at-stage, difficulty, duration
- [x] 3.3 Menu funnel — `APP_OPENED` once settings are read, `PLAY_PRESSED` (solo and lesson), `ONLINE_PRESSED`. `ROOM_REQUESTED`/`INVITE_SHARED` follow with the room screens' own wiring. Was: app opened, play pressed, online pressed, room create attempted,
      invite shared — the steps that happen before a room exists
- [x] 3.4 Client failure events, all three. **Stalled stage**: `reportStalls` watches progress rather than elapsed time — a batch of eleven bot moves at the calm pace is slow and healthy, one that has finished no move in thirty seconds has stopped — and `collectLatest` restarts the window on every sign of life. **Lost socket**: `RemoteRoom` never gives up, so there is no moment the code declares defeat; `looksLost()` names the moment the *player* has, at the backoff's fifth attempt. **Refused move**: every refusal is a defect by construction, since the controls are drawn from the same `Table` the validator judges. Each reported once per screen. Verified by `FailureCountingTest` — 5 tests on virtual time, proven non-vacuous by dropping the idle guard, which fails `anIdleStageIsNeverCounted`
- [x] 3.5 Gate: a JVM test proving the cap holds, that a flood is dropped rather than queued,
      that nothing is emitted when opted out, and that emission never blocks a move

## 4. Consent, and the proof that it binds

- [x] 4.1 GPC and Do-Not-Track — `platformObjectsToTracking()`, four actuals. The browser is the only platform where these signals exist, so it is the only one that reads them (`navigator.globalPrivacyControl`, then `doNotTrack`); Android's nearest equivalent needs Play Services for a signal this app would honour anyway, and iOS retired its app-level flag in favour of ATT, which is about cross-app tracking Vinto does not do. Both recorded in the actuals
- [x] 4.2 Opt-out in Settings beside sound and haptics, titled "Anonymous counts" and saying plainly that off means nothing is sent rather than less. `Settings.analytics`, default-added so an older file still decodes; a change applies immediately via `consentChanged`, which discards the buffer rather than flushing it
- [x] 4.3 `POST /e` on the Worker: size-capped, rate-limited, refusing anything that is not a
      known event name, and never trusting a client-supplied timestamp
- [x] 4.4 **Gate: nothing identifying leaves the device** — `AnalyticsPrivacyUiTest` drives the real `App` across menu, online and lesson with the sink recording every payload, and asserts no nickname, token, room code, playerId or seat, plus nothing *shaped* like a room code. Two more cases: a platform signal silences everything, and an opted-out session sends nothing. Third place this is enforced, and deliberately independent of the type check and the Worker's. Was: A test that plays a solo round, a
      lesson and an online round with the sink recording every payload, and asserts no room
      code, nickname, token, seat id, IP or persistent identifier appears in any of them.
      This is the requirement §6c already binds the portfolio to, held by a test rather than
      by a paragraph
- [x] 4.5 Privacy note in the help sheet — `help_counts_title`/`help_counts_body`, last on the sheet, saying what is counted, that there is *nowhere* in what is sent to put anything identifying, and that Settings and a browser's own signal both switch it off. In the app's own words rather than a link to a policy, because the whole claim fits in a paragraph, and on the sheet a player already opens to ask what something means

## 5. Reading it (not blocking the release)

- [ ] 5.1 **BUILT, NOT TICKED** (§1f): `GET /counts?key=…` on the Worker, rendered server-side from the WAE SQL API, with `ANALYTICS_TOKEN`, `ANALYTICS_ACCOUNT_ID` and `DASHBOARD_KEY` as secrets. Absent-safe: missing any of the three answers 404, the same answer a path that does not exist gets, so a prober cannot tell the two apart. `gate-dashboard.mjs` (51 checks) covers the refusals, the escaping and the queries' shape; it cannot cover a single number, because the Analytics Engine SQL API is the one part of WAE `wrangler dev` does not emulate. Ticking it needs a deployment with traffic
- [x] 5.2 The six queries, in `dashboard.mjs` as data rather than spread through the renderer so they can be read and gated without a network: acquisition, activation (finished against abandoned, by difficulty), the online funnel by step, sessions by how they ended, failures by kind and surface, and cost per round. Every aggregate weights by **both** samplings — WAE's own `_sample_interval` and this app's declared rate in `double1` (§A8) — because dropping either under-reports silently; `gate-dashboard.mjs` asserts that on every `sum()`, and fails when one is removed
- [ ] 5.3 **BLOCKED** (§1f): a Cloudflare dashboard toggle, not code. For a Pages project, Web Analytics is enabled per-site in the dashboard and Cloudflare injects the beacon itself — there is nothing in this repository to change, and nothing here can be verified without the account. DEPLOYMENT.md §7b says where the toggle is. What *was* missing and is now fixed is the page it would be injected into: `wasmJsBrowserDistribution` produced two `.wasm` files, a `.js` and **no `index.html`**, so the web client compiled and could not be served at all
- [ ] 5.4 **BLOCKED** (§1f): needs a week of real traffic against a deployed room. Once a week of real traffic exists: revisit the sampling rates in §A8 against actual
      volume, and the cost model in 2.3 against the actual bill

## What "done" means for the gate

Phases 1–4 ticked, `kmp-worker`'s gate scripts green with the new one added, and the privacy
test in 4.4 passing. Phase 5 can follow the release; the others cannot, because an event not
collected on launch day is a question that can never be asked about launch day.
