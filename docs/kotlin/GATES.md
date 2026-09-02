# The ports, and what each one is gated by

How `shapes`, the engine, the validator and the bot were each proved — and why the bot needed
a different kind of proof from the engine. Also the round trip that replaced the
cross-language one, now run across *targets* rather than across languages.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 6a. How the ported `shapes` is verified

Three layers, weakest to strongest:

1. `Sha256Test`, `CanonicalJsonTest`, `PrngVectorsTest` — unit tests in `commonTest`, so they
   run on **all five targets** (jvm, android, js, wasmJs, iOS simulator). They pin the rules
   that the corpus cannot reach: the `botMemory` exclusion is never present in a real
   recording, so it is asserted directly.
2. `RecordingParityTest` (JVM only, it reads the 4.5 MB corpus from disk) — decodes each of
   the 50 recordings' `finalState` into the Kotlin model with `ignoreUnknownKeys = false`,
   so an unmodelled field is an error rather than a silent drop. It also round-trips all
   **13,900** recorded actions through `GameActionSerializer`, comparing canonical forms.
3. The same test re-encodes that state, canonicalises and hashes it, and compares against the
   `finalStateHash` **TypeScript wrote**. One number covers lossless decode, correct
   optional-versus-nullable handling, a byte-identical canonical form, and SHA-256 agreeing
   with WebCrypto. It cannot pass by accident.

Confirmed non-vacuous: reversing the canonical key sort fails it, dropping `turnActions`
from the exclusions fails it, and forcing an unset optional to encode as `null` fails the
action round-trip. (Dropping `botMemory` does **not**, which is why layer 1 exists.)

The corpus reaches 17 of the 25 action types. The other eight, and the `rank: 'A'` variant
of `SELECT_ACTION_TARGET` that appears in no recording, are pinned by layer 1 instead —
which is the general shape of this: the corpus proves agreement on what real games do, and
the unit tests cover what they happen not to.

One deliberate check worth keeping: the optional-field rule is carried by the
`@EncodeDefault(NEVER)` annotations alone, not by `VintoJson`'s configuration. Flipping
`encodeDefaults` to `true` leaves every parity test green, so a call site that builds its own
`Json` cannot start emitting `"declaredRank":null` where TypeScript writes nothing.

**Deviation from design D1**, recorded deliberately: canonical JSON, SHA-256 and `Prng` live
in `shared/shapes` rather than a `shared/recording` module, because that is where TypeScript
keeps them and the port is file-for-file (D3). Revisit when the `GameRecording` model lands.

## 6b. The engine port, and how it is being driven

The harness came first, not last. `CorpusReplayTest` replays TypeScript's recordings through
the Kotlin engine and compares the canonical state hash **after every action**, so a wrong
handler is localised to one action rather than showing up as "the final state differs".

While the port was in progress this ran as a ratchet that named the frontier on every run,
so the next handler to write was never a guess. It is now a **hard gate**: all 50 recordings,
all 13,900 actions, plus final-state verification.

Confirmed non-vacuous — deleting one line of knowledge tracking from the Jack swap (the owner
must _lose_ knowledge of a blind-swapped position) fails it.

### The validator needed a different kind of test

The corpus cannot check `ActionValidator` at all: every action in it was legal when recorded,
so a validator that returned `Valid` unconditionally replays all 13,900 identically. Replaying
with the real validator live therefore proves one direction only — that nothing legal is
rejected.

The other direction is the one that matters, since this is the anti-cheat boundary D9 rests on.
`ValidatorImpersonationTest` gets it from the corpus anyway: replay every recording and, at
each step, re-attribute the action that genuinely happened to **every other player at the
table**. Each is an attempt to act out of turn in a position that actually arose.
**18,066 attempts, none accepted.**

Rule-specific cases the sweep cannot reach — the coalition may not target the Vinto caller, a
failed toss-in ends participation for the round, setup peek limits — are posed against corpus
states in `ValidatorRulesTest` rather than hand-built ones: a fabricated state proves a branch
is reachable, a real one proves the rule bites where it matters. Deleting either rule fails its
test.

**Why the handlers mutate.** The TypeScript handlers deep-copy the state and then mutate it
freely. Rewriting each into immutable `copy()` chains would be better Kotlin and a worse
migration — the parity gate cannot tell a faithful restructuring from a subtly wrong one. So
`MutableGameState` is a working copy that handlers mutate exactly as their TypeScript
counterparts do, and `reduce` freezes it on the way out. `reduce` stays a pure function; the
mutation never escapes the call. Rewriting handlers idiomatically later is safe precisely
because the gate holds the behaviour still.

## 6e. The bot, and what a self-play gate is for

**The search was rewritten on 2026-09-02**, after a review found that the ported tree never
applied its moves — the belief state has no hidden cards until a rollout samples them, so every
Jack in the tree traded nothing and the choice among candidates was a seeded coin flip. It is
an information-set MCTS now: each iteration samples a world from the bot's memory and plays
the tree's moves on that world, the whole turn is in the tree (draw, then play or swap or
discard, then the Vinto question), rollouts play by card values, and the reward is the
round's own points per seat. The swap weights, the evaluator, the Vinto thresholds and the
Q/7/8 heuristics went with it. `docs/bot/MCTS-REVIEW.md` has the finding, the measurements
and what replaced each constant; `MctsDiscriminationTest` holds the search to positions with
one right answer. What follows describes the port and its gates, and still applies.

The bot is ported in full, and the verification is worth explaining because it is not the one
the other phases use.

The engine had a corpus: 50 recorded games with per-action hashes, so "did the port work" has
an exact answer. **A bot has no such thing.** Its output is a decision, and two reasonable
bots disagree constantly without either being wrong. Demanding decision parity would have
meant transcribing every heuristic literally including its bugs, and would still not have
produced it — MCTS is stochastic, and the two implementations sample different random streams.

So the requirement was set differently: *the bot need not follow the TypeScript exactly, but
it must follow the rules.* That is checkable. `SelfPlayGateTest` plays whole games with four
Kotlin bots through the real `GameEngine`, and every action a bot proposes goes through
`ActionValidator` before it is reduced — the same boundary a Durable Object runs, so anything
rejected here would be rejected in a live room.

Three things it asserts, each for a different failure:

| assertion            | catches                                                             |
| -------------------- | ------------------------------------------------------------------- |
| every action is legal | a bot that cheats, or that gets stuck holding an action the engine refuses |
| every game reaches `scoring` | a game where each action is legal but two states hand back and forth forever |
| some game ends on a Vinto call | an endgame that is unreachable in practice, so games only end when the deck dries up |

It earned its keep immediately, finding five defects that no unit test would have:

- **Memories outlive the hands they describe.** A toss-in removes a card and renumbers
  everything after it; the memory keeps its old index. A shrunken hand therefore "remembered"
  a card past its own end, and the move generator offered it as a target the engine rejects.
- **A tossed-in Jack or Queen was a dead end.** The validator allows `selecting` — where a bot
  sits while resolving a tossed-in action — for `CONFIRM_PEEK`, `DECLARE_KING_ACTION` and
  `SELECT_ACTION_TARGET`, and forbade it for exactly these two swaps. A bot could choose both
  targets and then had no legal move at all. The same hole is in
  `legacy-web/packages/engine/src/lib/action-validator.ts`.
- **Cached action plans could go stale**, since they are read a ply deep in the search tree.
- **Target selection answered the wrong question** once the engine had already committed a
  card: "would I rather swap?" is no longer on the table at that point.
- **The search could not see the deck run out** — `deckSize` was hardcoded to a full deck.

**One dead end is deliberately left open.** The draw pile is refilled when a turn ends with
one card on it, but a forced draw or a wrong-declaration penalty takes a card *without* ending
a turn, so it can reach zero — and a turn that starts with no deck and nothing takeable on the
discard has no legal move. Refilling at zero as well looks like a one-word fix and is not:
`reshuffleFrom` advances `rngState` whether or not it moves anything, so recording
`selfplay-moderate-18` diverges at action 322. Corpus parity is worth more than closing a rare
dead end; the engine is untouched and `BotRunner` reports the position rather than proposing an
illegal draw.

**Three validator rules are deliberately stricter than TypeScript's**, all around the final
round, all invisible to the parity corpus because no recorded game ever hits them:

- **The coalition may not target the Vinto caller even before a leader is chosen.** The
  TypeScript guard (and the first Kotlin port of it) only fired once `coalitionLeaderId` was
  set, but choosing a leader is optional — every recording happens to pick one immediately
  after the call, which is the only reason the hole never showed. The caller's protection now
  starts at the call itself.
- **The Vinto caller cannot toss in.** `getAutomaticallyReadyPlayers` always treated the
  caller as done and the docs said the caller "may not participate", but nothing rejected the
  action; now `PARTICIPATE_IN_TOSS_IN` from the caller is invalid.
- **Vinto during a toss-in window belongs to the turn owner.** After a toss-in queue drains,
  `currentPlayerIndex` can rest on the last toss-in actor, who then passed `requireTurn` for
  `CALL_VINTO` — an out-of-seat call the UI and `BotRunner` never produce but the engine
  accepted, and whose advance path could hand the round straight back to the caller. The
  validator now requires the caller to be `activeTossIn.originalPlayerIndex`'s seat.

**`DECLARE_CARDS` is Kotlin-only**, like `END_ROUND` above it: coalition table talk, where a
member claims out loud what they believe their own cards are (see `VISIBILITY.md`). It never
appears in a parity recording and its state (`PlayerState.declaredCards`) serialises as
absent until first used, so all fifty recordings hash unchanged. A Kotlin recording that
*does* contain it replays fine here — `Replay.kt` is action-agnostic — but cannot be fed to
the TypeScript replayer, which has no such action.

## 6k. How good is the bot, and did that just change?

**The baseline moved on 2026-09-02**, with the search rewrite (§6e): rounds are shorter because
the bot calls Vinto on expected value, and `hard` went from the worst mean hand to the best.
The file is version 2 and carries a second table — easy, moderate, hard, hard in rotating
chairs — because that is the only table that can *rank* the difficulties, which the paragraph
below on homogeneous tables explains. `docs/bot/MCTS-REVIEW.md` §6 has both tables and what
they do and do not say.

`SelfPlayGateTest` asks whether the bot follows the rules. It says nothing about whether it
plays *well*, and until now nothing did — the original task 5.6 compared against a TypeScript
baseline generated by `npm run recordings:generate`, which went with `legacy-web/` (CI.md §1d).
Comparing an engine against a copy of itself that is being deleted is not a measurement
anybody can repeat next year.

So the bot is now measured against **itself**. Twelve seeds, played out at all three
difficulties, tallied into integers, and the table committed at
`fixtures/bot/self-play-baseline.json`. A heuristic change that moves any of those numbers has
to move them deliberately: regenerate the file, look at the diff, and say in the commit which
way the bot got better. The value is not that 13.20 is a good mean hand — it is that nobody
can change it by accident.

```sh
./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament        # compare
./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament=write  # regenerate
```

**It is a manually-run gate, and that is a decision rather than an oversight.** Thirty-six
whole MCTS games take **6m 39s** on this machine, against 1m 26s for the whole of the rest of
`:shared:bot:jvmTest`. That is worth paying when a heuristic changes and is not worth paying on
every push, so it is excluded from `jvmTest` unless `-Ptournament` is passed — the same shape
as `-Pscreenshots`. The legality gate keeps running every time, because that is the one a
release depends on.

Two rules about what goes in the file, both of them about a baseline being worth having:

- **Every committed number is an integer.** A mean carried as a `Double` differs in its last
  digit between JVMs and the gate becomes a coin toss; means are carried in hundredths, so
  `meanHandTotalCentis = 1320` reads as 13.20 and compares as an `Int`.
- **Latency is measured, printed, and never committed.** A millisecond figure is a fact about
  the machine, not about the bot, and pinning it would turn a busy runner into a red build.

The run as it stands, which is the first time any of this has been measured:

```
difficulty  games  vinto  caller  coalition   mean hand  mean actions  best  worst   ms/decision
easy           12     12       5          7       13.60        377.66    -1     35          4.13
moderate       12     12       8          4       13.20        268.41    -1     36         19.58
hard           12     12       7          5       14.68        248.50    -2     41        100.81
```

Reproduced exactly across three runs; only `ms/decision` moved, which is the argument for
where the line was drawn. Two things in it are worth somebody's attention, and neither is a
defect to fix blind:

- **`hard` costs 24× `easy` per decision** and does not obviously buy 24× anything. It reaches
  the same place in fewer actions (248 against 378), which is what more search should look
  like, but its mean hand is the *worst* of the three.
- **A homogeneous table cannot rank the difficulties.** All four seats play at the same level
  in every game here, so a higher mean hand says the table was harder to sit at, not that the
  bot was worse — three good opponents take the good cards. Ranking them means playing them
  *against each other*, which is a different tournament and a bigger one. Recorded rather than
  guessed at.

The loop itself lives in `Tournament.kt`, shared with `SelfPlayGateTest`: legality and strength
are two questions about one table and should not be asked of two subtly different games.

## 6l. The round trip, now across targets

The corpus round trip used to be across *languages*: TypeScript generated `fixtures/recordings`
and Kotlin replayed them. Once one engine ships, that check has nothing on the other side of it
— the corpus becomes a frozen artefact rather than evidence that two implementations agree
today (CI.md §1d). What replaces it is a round trip across **targets**, and it is not the weaker
property. It is the one that can still fail:

- a `Long` is a pair of `Int`s on Kotlin/JS, and `seed` and `rngState` are `Long`s
- the serializer backend differs between JVM, JS and Wasm
- canonical JSON and SHA-256 are hand-rolled and have to agree byte for byte everywhere, or a
  recording made on a phone cannot be replayed on a server

`RecordingRoundTripTest` lives in `shared/client`'s **`commonTest`**, so it plays a whole game
to `scoring`, exports the report, and replays it through the real `replayRecording` harness
**reached through text** — on whichever target is running. `kmp-jvm` runs it on the JVM and
`kmp-web` on Kotlin/JS and Wasm, so task 6.7's "CI job" needed no new CI job: the three legs
already existed and the test was the missing half. Measured: 9.2 s on the JVM, 24.5 s on Wasm.

Nothing is committed and nothing goes stale, because the recording is **generated on the target
that replays it**. Three assertions, and the second and third are what make the first worth
having: a corrupted hash has to be caught *at the action that carries it* (or the harness is
accepting anything), and one seed has to produce one document byte for byte (or two targets
cannot be compared at all).

### The one thing 6.5 turned out to be

The TypeScript `BotAIAdapter` is 1,500 lines because it drives a UI. Split in two here: the
*deciding* is `BotRunner`, a pure function of the state shared with the Durable Object, and the
*pacing* reaches the UI as frames rather than as `await delay(...)` inside the bot driver. What
was left that is genuinely a coroutine question is one line — `LocalGameSession` runs the search
on an injected `botDispatcher`, `Dispatchers.Default` in the app, null in tests — and nothing
checked it was live. A new path reaching the runner without going through `onBotDispatcher`
would move up to 1.6 s of search back onto the drawing thread *silently*, because the game would
still be perfectly correct; it would just stutter on a device, in a build nobody tests.

`BotDispatcherTest` asks the dispatcher, from inside the block that does the thinking, and also
pins that the whole run of bot turns rides on **one** hop rather than one per bot. It compiles
for JS and Wasm, which is where `java.lang.Runnable` was caught: `CoroutineDispatcher.dispatch`
takes `kotlinx.coroutines.Runnable` in common code, and the import is the whole difference.

### What it found on its first run

**A player's exported bug report could not be replayed by anything.** `Recording.formatVersion`
carries a default, `VintoJson` has `encodeDefaults` off — which is right, and is what keeps an
unset optional absent rather than `null` where TypeScript writes nothing — so the field was
silently missing from every report the table's bug-report control produced. And
`GameRecording.formatVersion` is **required**: `CorpusReplayTest` refused to parse one, and so
did the Worker's `POST /replay`. `Recorder.kt`'s own comment promised a report "can be dropped
straight into" that harness, and it could not.

It is one `@EncodeDefault(ALWAYS)`. What is worth keeping is why nothing caught it: `RecorderTest`
replays the recorder's output too, and passes, because it replays *the object it just built in
memory*. A bug report arrives as bytes. Reaching the harness through text is the difference,
and it is the reason this test does the JSON hop rather than calling `replayRecording(report)`.
