# Tasks: retire `legacy-web/`

Not a release gate. Nothing ships from `legacy-web/` and its CI is already gone (README §1d),
so this is repository hygiene plus one decision worth writing down before it becomes
irreversible. Phase 1 is the decision; phase 4 is the deletion; the deletion goes last on
purpose (design D5).

## 1. Freeze the corpus, on the record

- [x] 1.1 `fixtures/recordings/README.md`: the directory is **frozen**, what produced it, why it
      **Done** — `fixtures/recordings/README.md` — frozen, why the generator was not ported, what to do instead
      is never regenerated (design D2), and what to do instead when new coverage is wanted
- [x] 1.2 A new directory for Kotlin-generated recordings, with its own README saying which
      **Done** — `fixtures/kotlin-recordings/` with a README on what a self-generated recording can and cannot prove
      engine wrote them and therefore what they can and cannot prove. Empty until something
      needs it — the point is that the two can never be confused, not that it has contents
- [x] 1.3 `docs/game-engine/RECORDING.md`: stop describing a cross-language contract in the
      **Done** — `RECORDING.md` is the format spec now; the integer rule keeps its reasoning, aimed at targets rather than languages
      present tense. It becomes the format's specification plus a note on where the corpus came
      from
- [x] 1.4 A test that fails if `fixtures/recordings` changes. The freeze is a policy and a
      **Done** — `CorpusIsFrozenTest` + `MANIFEST.sha256`; non-vacuous four ways (edited, removed, added, manifest truncated)
      policy nobody enforces is a preference — a committed manifest of the fifty files and their
      hashes, checked by `CorpusCoverageTest` or beside it

## 2. Correct the documents that describe two engines

- [x] 2.1 `README.md`: the tech stack, "the rules live twice", and the `legacy-web` half of the
      **Done** — badges, opening claim, tech stack, structure, diagrams (GameClient/MobX are gone), and 'Future Enhancements' which described shipped features
      project structure
- [x] 2.2 `CLAUDE.md`: the same, plus the "Adding a New Game Action — both engines, one change"
      **Done** — `CLAUDE.md`: the 'both engines' procedure, the conventions section, deployment
      procedure, which becomes one engine and one corpus that is not regenerated
- [x] 2.3 `CONTRIBUTING.md` and `openspec/project.md`
      **Done** — `CONTRIBUTING.md` rewritten about this repository; `openspec/project.md` corrected
- [x] 2.4 `.claude/commands/engine-test.md`, `bot-debug.md`, `check-game-rules.md` — these tell
      **Done** — the three `.claude/commands/*.md` no longer send a model to a deleted directory
      a model to run `npm test` in a directory that will not exist, which is the one class of
      stale reference that actively misleads (design D3)
- [x] 2.5 `docs/kotlin/README.md` §1d becomes past tense, and §4's "TypeScript" command block goes
      **Done** — §1d is past tense; §4's TypeScript block and five Nx/tsc traps removed

## 3. Check nothing else reaches for it

- [x] 3.1 `grep -rn "legacy-web"` over the Gradle build, the workflows and the Worker: expect
      **Done** — found two real dependencies the plan did not anticipate: both icon generators read the mark from `legacy-web/`, and `make-launcher-icons.py` wrote to a pre-AGP-9 path
      the one comment in `composeApp/build.gradle.kts` about card art, and nothing else
- [x] 3.2 `./gradlew releaseGate` green, and the twelve room gates green, **before** the
      **Done** — detekt + every JVM suite green on the commit before the deletion
      deletion — so a later failure cannot be blamed on it
- [x] 3.3 Leave the ~79 "Ported from `legacy-web/…`" comments alone, deliberately (design D3)
      **Done** — left alone, deliberately (design D3)

## 4. Delete it

- [x] 4.1 `git rm -r legacy-web/`, in a commit that does nothing else, so it reverts cleanly
      **Done** — 347 files, 4.6 MB, its own commit
- [x] 4.2 `lefthook.yml`, `codecov.yml` and `.gitignore`: anything left pointing into it
      **Done** — `.gitignore`, `.vscode/tasks.json`, `.claude/settings.json`; the explanatory comments in lefthook/codecov/dependabot/kmp stay
- [x] 4.3 `./gradlew releaseGate` green again, and the room gates, on the commit after
      **Done** — detekt + all seven JVM suites green with `--rerun-tasks`
- [x] 4.4 A line in `docs/kotlin/README.md` §1d saying it is done and on which commit, because
      **Done** — README §1d records it
      "the corpus can no longer be regenerated" is a fact somebody will want dated
