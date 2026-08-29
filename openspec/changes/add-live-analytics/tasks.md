# Tasks: live analytics

Phases 1–4 are the **release gate** — the Kotlin branch does not merge and §6i step 3 is not
walked until they are ticked. Phase 5 is the follow-up that only becomes answerable once real
traffic exists, and is explicitly *not* blocking.

Each phase is independently verifiable without the Compose UI, in the way the room's own
phases were: the Worker half is exercised by a gate script through `wrangler dev`, and the
client half by JVM tests against a fake sink.

## 1. The sink, and what it is allowed to hold

- [ ] 1.1 Confirm the account's **current** Workers Analytics Engine allowances — writes/day,
      reads, retention, and whether the plan covers it — against the live dashboard, and
      record the real numbers in `design.md` §A1. Do not build on the figures in this file
- [ ] 1.2 `ANALYTICS` binding in `wrangler.jsonc`, absent-safe: the Worker and the room must
      run identically with no binding, so local `wrangler dev` and every gate script keep
      working without a Cloudflare account
- [ ] 1.3 `AnalyticsEvent` as a sealed type in `shared/room`, with the `blobs`/`doubles`/
      `indexes` mapping in one place. No field on it may carry a free string from a player
- [ ] 1.4 Gate: a compile-time test that asserts the event surface holds no room code, no
      nickname, no token and no seat identifier — the check that keeps §A2 true as fields
      are added

## 2. The server measures the server

- [ ] 2.1 Room lifecycle events: `room_created`, `seat_filled`, `seat_vacated`, `bot_took_over`,
      `reconnected`, `session_ended` — with the reason each ended
- [ ] 2.2 Round events: `round_start`, `round_end` carrying turns, duration, how it ended
      (Vinto called, deck exhausted) and whether the caller won
- [ ] 2.3 **Cost dimensions on every room event**: Durable Object wall time and request count
      for the invocation that produced it. This is the number that decides whether online play
      stays free, and it is free to collect here
- [ ] 2.4 Gate: a script through `wrangler dev` plays a full room and asserts the exact event
      sequence against a fake sink — including that a room played with no binding emits
      nothing and still finishes the round

## 3. The client reports only what the server cannot see

- [ ] 3.1 `Analytics` sink in `shared/client` over the `Net` seam: batched, capped,
      fire-and-forget, dropping the newest, never on the hot path (§A4)
- [ ] 3.2 Solo and lesson events: started, finished, abandoned-at-stage, difficulty, duration
- [ ] 3.3 Menu funnel: app opened, play pressed, online pressed, room create attempted,
      invite shared — the steps that happen before a room exists
- [ ] 3.4 Client failure events: a stage that stopped draining, a socket that gave up
      reconnecting, a refused move the UI could not explain
- [ ] 3.5 Gate: a JVM test proving the cap holds, that a flood is dropped rather than queued,
      that nothing is emitted when opted out, and that emission never blocks a move

## 4. Consent, and the proof that it binds

- [ ] 4.1 GPC and Do-Not-Track read before the first event on every platform that exposes them
- [ ] 4.2 Opt-out in Settings, beside sound and haptics, worded as what it does
- [ ] 4.3 `POST /e` on the Worker: size-capped, rate-limited, refusing anything that is not a
      known event name, and never trusting a client-supplied timestamp
- [ ] 4.4 **Gate: nothing identifying leaves the device.** A test that plays a solo round, a
      lesson and an online round with the sink recording every payload, and asserts no room
      code, nickname, token, seat id, IP or persistent identifier appears in any of them.
      This is the requirement §6c already binds the portfolio to, held by a test rather than
      by a paragraph
- [ ] 4.5 Privacy note in the help sheet saying what is counted and what is not, in the app's
      own words

## 5. Reading it (not blocking the release)

- [ ] 5.1 Dashboard route on the Worker, rendered server-side from the WAE SQL API, token as a
      secret (§A6)
- [ ] 5.2 The six queries worth having: acquisition, activation (first round finished),
      the online funnel, rounds per session, failure rate, cost per room
- [ ] 5.3 Cloudflare Web Analytics on the Pages project (§A7)
- [ ] 5.4 Once a week of real traffic exists: revisit the sampling rates in §A8 against actual
      volume, and the cost model in 2.3 against the actual bill

## What "done" means for the gate

Phases 1–4 ticked, `kmp-worker`'s gate scripts green with the new one added, and the privacy
test in 4.4 passing. Phase 5 can follow the release; the others cannot, because an event not
collected on launch day is a question that can never be asked about launch day.
