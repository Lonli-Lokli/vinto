# The release gate

Task 8.3 asked for a checklist. Most of it is a Gradle task instead, because a checklist in a
document is a list of things somebody forgets one of:

```sh
./gradlew releaseGate
```

Everything a Linux machine can check, in one command: detekt over every module and source set
at `maxIssues: 0`, the six shared modules' JVM suites, the same `commonTest` suites on
Kotlin/JS and Kotlin/Wasm, the Compose screens headless, and the Worker bundle compiling.
**3m 03s** on the machine this was written on — 108 Compose tests, 165 client, 217 bot, and the
corpus replay among them.

What follows is the rest: the gates that need a machine, a browser, credentials or a person.
Each is here rather than quietly left out of the task, because the difference between "the gate
does not cover this" and "nobody remembered this" is the whole point of writing it down.

---

## 1. The one command

| Covers | Where it is |
| --- | --- |
| Static analysis, every module and source set | `detekt`, `maxIssues: 0`, baseline at `config/detekt/baseline.xml` |
| The rules, on the JVM | `:shared:*:jvmTest` — the 50-recording corpus replay, the validator's 18,066 impersonation attempts, the self-play legality gate |
| The rules, where a `Long` is two `Int`s | `:shared:*:jsNodeTest`, `:shared:*:wasmJsNodeTest` — including the whole-game round trip from GATES.md §6l |
| The screens | `:composeApp:jvmTest`, headless. Goldens excluded — see the note on the test task |
| The Worker compiles | `:worker:jsProductionExecutableCompileSync` |

## 2. What the one command deliberately leaves out

### The self-play tournament — a manually-run gate (GATES.md §6k)

```sh
./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament
```

6m 39s of MCTS against 1m 26s for the whole of the rest of `:shared:bot:jvmTest`. Folding it in
would make the one command something nobody runs. **Run it whenever a bot heuristic changes**,
and if the numbers move, say in the commit which way the bot got better.

8.3 originally read "tournament within 5 pp", which was a tolerance for comparing against
TypeScript. There is no TypeScript to compare against (CI.md §1d), and the comparison that
replaced it is against the bot's own committed numbers — where an *exact* match is both
achievable and stricter, so there is no tolerance to set. Every committed figure is an integer
for that reason; latency is printed and never committed.

### The room, which needs Node and workerd

```sh
./gradlew :worker:jsProductionExecutableCompileSync
cd worker/cloudflare && npx wrangler dev --port 8787 --var ROOM_OPEN:true &
node gate-real-room.mjs && node gate-sessions.mjs && node gate-lobby.mjs \
  && node gate-lifecycle.mjs && node gate-limits.mjs && node gate-registry.mjs \
  && node gate-room-codes.mjs && node gate-two-clients.mjs && node gate-engine-replay.mjs \
  && node gate-analytics.mjs && node gate-sentry.mjs && node gate-dashboard.mjs
npx wrangler deploy --dry-run --outdir /tmp/w    # and check the gzipped size budget
```

CI runs these as `kmp-worker`, each gate its own named step.

### Apple — needs macOS

`kmp-ios` covers it on CI: simulator tests for the five Apple-target modules, `composeApp`
compiling for the simulator, and the framework Xcode embeds. **A `commonMain` change that breaks
iOS cannot fail on a non-Mac host** (§5), and this has caught three separate Objective-C interop
mistakes that nothing on Linux could have (TRAPS.md §7).

### Android device parity — task 4.8, blocked

The iOS half is done: `kmp-ios` runs `:shared:client:iosSimulatorArm64Test`, so a whole game is
generated and replayed through the real harness on Kotlin/Native every run. The Android emulator
half wants an instrumented test reading the corpus from an asset, and is blocked on a machine
that can resolve androidx — §1f.

### The goldens, the sounds, the deploy, and four humans

ROOM.md §6i, unchanged, and none of it is unblocked by a faster machine:

- the eight golden screenshots, written on a maintainer's machine and looked at by a person
- the four sounds, through `./gradlew :composeApp:run`
- `wrangler deploy`, and the deliberate decision to flip `ROOM_OPEN`
- two devices, then the four-human table (9.7's second verification)

## 3. The order to run them in

1. `./gradlew releaseGate` — everything cheap, and the thing most likely to be red
2. The tournament, **if a bot heuristic changed**
3. The room gates, **if anything under `worker/` or `shared/room/` changed**
4. Push, and read `kmp-ios` — the only leg that can fail for a reason Linux cannot see
5. ROOM.md §6i steps 1, 4 and 5, on the maintainer's machine, for a release rather than a change
