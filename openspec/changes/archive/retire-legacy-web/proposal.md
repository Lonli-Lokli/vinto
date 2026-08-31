# Change: Retire `legacy-web/`

## Why

The rules live twice. That was the point: one engine in TypeScript, one in Kotlin, held
identical by a 50-recording corpus, so a porting mistake had somewhere to show up. The port is
finished, the Kotlin engine is the one that ships on four targets, and the second copy has
stopped being a cross-check and started being a cost.

What is already true, and is why this change is worth opening rather than deferring again:

- **Nothing ships from it.** Android, iOS and the web client are all `composeApp`; the room is
  the Worker. `nx build @vinto/game` has been broken since before the Kotlin branch, on a
  frozen workspace, and no CI job builds it.
- **Its CI is already gone** (README §1d). `legacy-web/package.json` asks for Node 24 while the
  workflows installed 22, so `npm ci` died before a test ran and both checks were red on every
  pull request. The three workflows were removed rather than bumped, because making a frozen
  app's suite green buys nothing.
- **It is 347 files and 4.6 MB** in a repository whose build does not read a line of it. The
  one mention in the Gradle build is a comment saying where the card art came from;
  `composeApp` carries its own copies.
- **Nobody can honestly claim the two engines agree today.** The TypeScript suite has not run
  in CI since §1d, so "the parity gate keeps both engines identical" — which is the sentence
  task 10.1 was written under — is no longer true. What holds is weaker and should be said
  plainly: the corpus is a record of what TypeScript computed *once*, and Kotlin still
  reproduces it.

## What Changes

- **Delete `legacy-web/`**, its Nx workspace, its lockfile and its two recording CLIs.
- **Keep `fixtures/recordings` exactly as it is, forever**, and say in the directory itself
  that it is a fossil rather than a corpus that gets regenerated. This is the decision worth
  recording and it is set out in `design.md` — the temptation is to port the generator to
  Kotlin first so the corpus stays extensible, and that is the wrong trade.
- **Add new coverage as Kotlin-generated recordings in a separate directory**, clearly labelled,
  so a self-generated recording is never mistaken for an independently-generated one.
- **Correct every document that describes a two-engine repository** — `README.md`, `CLAUDE.md`,
  `CONTRIBUTING.md`, `openspec/project.md`, and the three `.claude/commands/*.md` that tell a
  model to run `npm test`.
- **Leave the ~79 source comments** that cite a TypeScript file as the origin of a port. They
  are provenance, they are accurate, and a comment naming a file that no longer exists is
  better than a port with no stated source. `design.md` §D3 says why.

## Impact

- **Affected**: `legacy-web/` (deleted), `fixtures/recordings` (frozen by policy, unchanged on
  disk), documentation across the repository, `.claude/commands/`.
- **Not affected**: the Gradle build, every gate in `docs/kotlin/RELEASE-GATE.md`, the corpus
  replay, the Worker.
- **Risk**: one, and it is irreversible in practice — after this, no new recording can be
  produced by an implementation that has not read the Kotlin engine. That is the whole subject
  of the design note.
