# Vinto — architecture

What the system is, why it is shaped this way, and which properties must not be broken.

`README.md` in this directory is the *state* of the work — what is done, what is blocked, what
to run. This is the *shape*: it changes when a decision changes, not when a task is ticked.

---

## 1. One rule set, four clients, one authority

```
                        ┌──────────────────────────────┐
                        │  shared/shapes               │
                        │  Card, GameState, GameAction │
                        │  canonical JSON, SHA-256     │
                        │  Prng (seeded, portable)     │
                        └──────────────┬───────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
          ┌─────────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐
          │ shared/engine    │ │ shared/protocol│ │ shared/bot     │
          │ GameEngine.reduce│ │ the wire,      │ │ MCTS, coalition│
          │ ActionValidator  │ │ declared once  │ │ BotRunner      │
          └─────────┬────────┘ └───────┬────────┘ └───────┬────────┘
                    │                  │                  │
          ┌─────────▼──────────────────▼──────────────────▼────────┐
          │ shared/client            │        shared/room          │
          │ GameSession:             │  RoomCore, RegistryCore     │
          │  LocalGameSession (solo) │  envelopes, pacing,         │
          │  RemoteGameSession (net) │  recordings                 │
          └─────────┬────────────────┴──────────────┬──────────────┘
                    │                               │
          ┌─────────▼─────────┐          ┌──────────▼──────────────┐
          │ composeApp        │          │ worker                  │
          │ one commonMain →  │◄────────►│ Cloudflare Worker +     │
          │ Android, iOS, web │ WebSocket│ one Durable Object/room │
          │ + a desktop run   │          │ (Kotlin/JS + a JS shim) │
          └───────────────────┘          └─────────────────────────┘
```

The single most important line on that diagram is that **`shared/engine` appears once**. The
same `GameEngine.reduce` runs on a phone, in a browser and inside a Durable Object. A rule is
not "implemented on the server and mirrored on the client"; there is one implementation and
two places that call it.

---

## 2. The invariants

These are the properties everything else is arranged to protect. Each one is held by a test,
named here so that the claim can be checked rather than believed.

| Invariant | Held by |
| --- | --- |
| `reduce` is pure, total and deterministic — no clock, no ambient randomness, no I/O | `CorpusReplayTest`, and the seeded `Prng` in `GameState.rngState` |
| The same actions produce the same state, byte for byte, on every target | `CorpusReplayTest` (50 recordings, 13,900 actions, hashed per action) |
| A seat can only act for itself | `ValidatorImpersonationTest` — 18,066 re-attributed actions, none accepted |
| A client is never sent another seat's hidden cards | `projectView`, `PeekPrivacyTest`, and the per-socket envelopes in `shared/room` |
| A solo game touches no network | `NoNetworkGuardTest`, which installs a `SecurityManager` and proves it bites first |
| A bot follows the rules, and games end | `SelfPlayGateTest` — every proposed action through `ActionValidator`, every game to `scoring` |
| Nothing identifying is ever counted or reported | `AnalyticsPrivacyTest` (types), `AnalyticsPrivacyUiTest` (the app), `gate-analytics.mjs` (the wire), `CrashReportTest` (crashes) |
| Every screen's text clears WCAG AA, in both themes | `ContrastTest` (every declared pair of colours), `ScreenContrastTest` (every screen, measured from its own pixels) |
| The public list cannot outlive its rooms | `gate-delisting.mjs` (the real Room's forget meeting the real Registry handler), `RegistryLeaseTest` (silence hides, then sweeps, a row whose forget was lost) |
| A card's `actionText` is *data*, not copy — it is in the hash, so it cannot be translated | `CardCopyIsDataTest` |
| Nothing a player **types** reaches another player's screen — there is no text input in the app | `NicknameTest`, and `looksMinted` applied at the room's door (`LobbyRefusalsTest`, `RegistryCapsTest`) |
| A card's *name, description and help* ARE copy and do translate — only `actionText` is data | `CardWords` reads them from `strings.xml`; `CardCopyIsDataTest` guards the one that must not move |

**The engine's purity is not a style preference.** It is what lets the same code be the
authority in a Durable Object and the simulator inside MCTS, and it is what makes a recording
a complete description of a game. Break it and three unrelated things stop working at once.

---

## 3. Why the reducer is the centre

Every interaction — a human tap, a bot's decision, a message off a socket — becomes a
`GameAction`, and every `GameAction` goes through the same two steps:

```
ActionValidator.validate(state, action)   →  Valid | Invalid(reason)
GameEngine.reduce(state, action)          →  a new state, or the same one
```

There is no second path. A bot does not "apply its move directly"; it proposes an action that
is validated exactly as a stranger's would be. This is the property that made
`SelfPlayGateTest` able to find five real defects that no unit test would have, and it is what
makes the room's anti-cheat boundary one line rather than an audit.

**The handlers mutate, on purpose.** `MutableGameState` is a working copy that each handler
mutates the way its TypeScript counterpart did, and `reduce` freezes it on the way out. That
is a worse-looking Kotlin and a better migration: the parity corpus cannot tell a faithful
restructuring from a subtly wrong one, so the port stayed literal while the gate was the only
thing standing between the two engines. Rewriting a handler idiomatically is safe *now*,
precisely because the corpus holds the behaviour still.

---

## 4. The seams, and what each one is for

A seam here means an interface with more than one life, introduced because the alternative was
teaching one of the callers a habit that fails elsewhere.

**`GameSession` — solo and online are indistinguishable to the UI.** `LocalGameSession` runs
the engine and `BotRunner` in-process; `RemoteGameSession` runs them over a socket. Both hand
the screen the same redacted `PlayerView` and both enforce the seat boundary from the same
`GameAction.actorId`. A local game that let the UI act for a bot would be teaching the UI
something that breaks the moment there is a network.

**`RoomSocket` / `RoomConnector` — the network is shape, not implementation.**
`shared/client` is proven network-free, so what lives there is the *form* of a connection and
the platform actuals live in `composeApp`: `java.net.http`, OkHttp, `NSURLSessionWebSocketTask`,
the browser's `WebSocket`. Deliberately not Ktor — the protocol is JSON text over one socket,
every platform ships a client for that, and a multiplatform HTTP framework is a large
dependency in a bundle with no headroom.

**`postBeacon` — one fire-and-forget POST.** Analytics and crash reports use it. It is
deliberately not part of `RoomConnector`, because every method of that interface matters and
is awaited, and this is the opposite: nothing waits on it and a failure is a lost count.

**`Vault` — storage without a platform.** Settings, the saved game and the seat token.

**`LocalPacing`, `LocalCounting`, `LocalSurface`, `LocalFeedback`, `LocalSounds`** — composition
locals for the things that every screen touches and no screen owns. `LocalPacing` exists so a
caller with nobody watching (a test, a headless run) can drop the dwells without the animation
code knowing it is being hurried.

---

## 5. The room is authoritative, and it is one object

One Cloudflare Durable Object per room. It deals from a seed, validates every action, runs the
bots server-side, and sends **each socket its own redacted view**.

Three consequences worth knowing before changing anything there:

- **Bots run on the server** because a client-side bot would need the other seats' hidden
  cards. That is not a performance decision; it is the same decision as the redaction.
- **A Durable Object gets 30 s of CPU per request; a plain Worker gets ~10 ms.** This is why
  `/replay` had to move into the object — production answered `error code: 1102` while
  `wrangler dev`, which enforces no CPU limit at all, was perfectly happy.
- **The room's own state is not the wire.** `RoomState` holds token hashes and, once dealt,
  every hand. `LobbyView`, `PublicSeat` and `PublicRoom` are allow-lists that say what may
  leave — never the internal record with a field removed, because a projection that strips
  named fields publishes the next field somebody adds, silently.

---

## 6. What is shared, and what is deliberately not

**Shared:** the rules, the wire, the bot, the session interface, the room's core logic, the
whole UI.

**Not shared, on purpose:**

- **Anything that needs a platform**: sockets, storage, clipboards, share sheets, haptics,
  sound, the crash hook. Each is an `expect` with four small actuals.
- **The room's runtime**. `shared/room` is testable on the JVM; `worker/` is a thin
  `@JsExport` layer plus a JavaScript shim. The split exists so the room's rules can be tested
  without workerd.
- **Crash reporting and analytics**. Same shape, different pipes, and never merged: a count is
  about what people chose to do, a crash is the app failing at something it promised. Neither
  carries an identifier.

---

## 7. The cross-implementation contract, and what it is now

`fixtures/recordings` holds 50 games and 13,900 actions, each carrying the canonical state hash
**TypeScript computed**. The Kotlin engine reproduces every one, per action.

Read that in the present tense with one correction. The corpus was *generated* from
`legacy-web/`, which is frozen and going. After it goes, the corpus stays — it is committed,
and `CorpusReplayTest` still replays it — but it becomes a **frozen artefact**: still a real
gate against the Kotlin engine drifting, no longer evidence that two implementations agree
today, and impossible to extend.

So the old policy ("a rules change lands in both engines and regenerates the corpus") is
currently true and will not be for much longer. What replaces it is already in place: the same
`commonTest` suites run on JVM, JS and Wasm, which is the property that still matters once one
engine ships — a `Long` is two `Int`s on Kotlin/JS, and a recording that round-trips on the JVM
does not therefore round-trip in a browser.

Until `legacy-web/` is actually deleted, a rules change still belongs in both engines.

---

## 8. Things that look like accidents and are not

- **`composeApp` has a desktop target with a `main()`.** It is the fastest way to look at a UI
  change, with no emulator to boot, and it is how the four sounds get listened to.
- **Screenshot goldens are not committed from CI and not generated here.** A fresh runner would
  write its own and assert nothing, and glyph rasterisation differs by JVM. They are a
  maintainer's artefact, accepted by a human looking at the images.
- **`ROOM_OPEN` defaults to `"false"`.** A deployed room with the flag off creates nothing and
  discloses nothing. `/health` and `/replay` stay above the gate: one is a liveness answer, the
  other a pure function of its own argument.
- **Every telemetry path is absent-safe.** No analytics binding, no `SENTRY_DSN`, no dashboard
  secrets — everything runs identically and emits nothing. Telemetry must never be a thing you
  need credentials to develop against.
- **detekt runs at `maxIssues: 0` with a baseline of seven pre-existing findings.** Fix one and
  delete its line. Regenerating the file to silence a *new* violation is the one use that makes
  a baseline a lie.

---

## 9. Where to look next

| Question | File |
| --- | --- |
| What is done, what is blocked, what to run | `README.md` (this directory) — and its §0, which says which file holds every other section |
| The six CI checks, and the web client whose CI went first | `CI.md` |
| How each port is gated: shapes, the engine, the validator, the bot | `GATES.md` |
| The website: its shell, its caching, its CORS | `HOSTING.md` |
| The room Worker, the solo game, and the runbook for opening it | `ROOM.md` |
| The screens: the phone, the menu and lesson, the lobby, the endgame | `UI.md` |
| Where every string a player sees lives | `WORDS.md` |
| Crashes, and why errors cross the wire as values | `RELIABILITY.md` |
| The traps that have each cost somebody an afternoon | `TRAPS.md` |
| The wire, message by message | `PROTOCOL.md` |
| What each action reveals, and to whom | `../game-engine/VISIBILITY.md` |
| The rules, and where the engines depart from the PDF | `../game-engine/VINTO_RULES.md` |
| How a move becomes an animation | `CHOREOGRAPHY.md`, `ANIMATIONS.md` |
| The measurements behind the platform choices | `PLATFORM-GATE.md` |
| Why the bot is verified by rule-following and not decision parity | `GATES.md` §6e |
