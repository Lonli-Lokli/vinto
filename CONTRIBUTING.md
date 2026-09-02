# Contributing to Vinto

Thanks for wanting to. This is a Kotlin Multiplatform project: the Gradle build is the
repository root, and one rule set ships to Android, iOS, the browser and a Cloudflare Durable
Object.

The documents worth reading before anything else:

| | |
| --- | --- |
| [`docs/kotlin/ARCHITECTURE.md`](docs/kotlin/ARCHITECTURE.md) | The **shape** — what the invariants are and why. Changes when a decision changes |
| [`docs/kotlin/README.md`](docs/kotlin/README.md) | The **state** — setup, module map, commands, and an index (§0) of the files beside it, one per area |
| [`docs/kotlin/TRAPS.md`](docs/kotlin/TRAPS.md) | The long list of traps that have each cost somebody an afternoon. Read it when something fails in a way that makes no sense |

---

## Getting set up

**JDK 17.** Not newer. Every module pins `jvmTarget = 17` and none declares a toolchain, so a
newer JDK fails with a Java/Kotlin target mismatch before it compiles anything. This is the
single most common first-run failure.

```sh
git clone <repo> && cd vinto
./gradlew --version          # bootstraps Gradle via the committed wrapper
```

For the Android target, point Gradle at your SDK — `local.properties` is gitignored:

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

iOS needs macOS and Xcode. The Apple targets are declared behind a host check, so a non-Mac
build skips them and says so rather than failing.

Also useful: Node 22 for the Worker tooling, and `./gradlew :composeApp:run` — a desktop window
running the real app, which is the fastest way to look at a UI change with no emulator to boot.

## What to run before you push

```sh
./gradlew detekt                        # static analysis, every module and source set
./gradlew :shared:engine:jvmTest        # the parity gate: 50 recordings, 13,900 actions
./gradlew :composeApp:jvmTest           # the Compose suites, headless
```

The full command list — iOS, the Worker, the room gates, the bot tournament — is
`docs/kotlin/README.md` §4.

## The things that will fail your PR

Six checks run in CI (`kmp-detekt`, `kmp-jvm`, `kmp-web`, `kmp-android`, `kmp-worker`,
`kmp-ios`). They are separate jobs on purpose: a detekt violation says nothing about whether
the engine still replays the corpus, and chaining them would hide the second failure behind the
first.

Four rules are worth stating outright, because each protects something a test cannot re-derive:

**The engine stays pure.** `GameEngine.reduce` has no clock, no ambient randomness and no I/O.
That is not a style preference — it is what lets the same code be the authority inside a Durable
Object *and* the simulator inside MCTS, and what makes a recording a complete description of a
game. Break it and three unrelated things stop working at once.

**`fixtures/recordings` is frozen.** Those 50 games carry state hashes computed by a *second
implementation*, written from the rules rather than from this code — which is the whole of
their value, and that implementation no longer exists. If `CorpusReplayTest` goes red, the
corpus is almost certainly the thing that was right. `CorpusIsFrozenTest` will stop you
rewriting it; `fixtures/recordings/README.md` says what to do instead.

**The detekt baseline shrinks.** Fix an entry and delete its line. Do not regenerate a baseline
file to make a *new* violation go away — that is the one use that turns a debt list into a mute
button. There is one baseline per module, because a shared one cannot be generated at all (the
last module to finish overwrites the rest).

**Nothing identifying is ever counted or reported.** Analytics and crash reports carry no
identifier, no room code and no nickname, and there are tests that play whole rounds and assert
it. If a change needs to know *who* did something, it needs a conversation first.

## Adding a game action

1. Add the type to `shared/shapes/.../GameAction.kt`
2. Add a handler under `shared/engine/src/commonMain/.../cases/`, wired into `GameEngine.reduce`
3. Teach `ActionValidator` when it is legal — **including who may send it**; that check is the
   whole anti-cheat boundary
4. Dispatch it from the UI

## Style

detekt is the arbiter, and it runs with every rule the tool ships. Where this project disagrees
with a rule, the disagreement is written down in `config/detekt/detekt.yml` next to the rule it
turns off — so if a rule is bothering you, the right move is to argue there rather than to add
a `@Suppress`. A suppression is fine when it is about *this* line and says why.

Two conventions detekt cannot express:

- **Comments explain decisions, not signatures.** "Why is it this way" and "what was rejected"
  are worth a paragraph; "sets the player id" is worth nothing.
- **Test names are sentences.** Backticked names with spaces are JVM-only and much of the suite
  is `commonTest`, so it is `theWholeFinalRoundIsHandedToTheTable` rather than `testFinalRound`.

## Pull requests

Branch from `kotlin`. Keep the change and its test in the same commit, and say in the message
what would make the test fail — a test whose failure nobody can interpret gets deleted the
first time it goes red.

If you change something a document describes, change the document in the same commit. Most of
the cost in this repository has come from documents that were true when written.
