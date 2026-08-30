# Tasks: retire `legacy-web/`

Not a release gate. Nothing ships from `legacy-web/` and its CI is already gone (README §1d),
so this is repository hygiene plus one decision worth writing down before it becomes
irreversible. Phase 1 is the decision; phase 4 is the deletion; the deletion goes last on
purpose (design D5).

## 1. Freeze the corpus, on the record

- [ ] 1.1 `fixtures/recordings/README.md`: the directory is **frozen**, what produced it, why it
      is never regenerated (design D2), and what to do instead when new coverage is wanted
- [ ] 1.2 A new directory for Kotlin-generated recordings, with its own README saying which
      engine wrote them and therefore what they can and cannot prove. Empty until something
      needs it — the point is that the two can never be confused, not that it has contents
- [ ] 1.3 `docs/game-engine/RECORDING.md`: stop describing a cross-language contract in the
      present tense. It becomes the format's specification plus a note on where the corpus came
      from
- [ ] 1.4 A test that fails if `fixtures/recordings` changes. The freeze is a policy and a
      policy nobody enforces is a preference — a committed manifest of the fifty files and their
      hashes, checked by `CorpusCoverageTest` or beside it

## 2. Correct the documents that describe two engines

- [ ] 2.1 `README.md`: the tech stack, "the rules live twice", and the `legacy-web` half of the
      project structure
- [ ] 2.2 `CLAUDE.md`: the same, plus the "Adding a New Game Action — both engines, one change"
      procedure, which becomes one engine and one corpus that is not regenerated
- [ ] 2.3 `CONTRIBUTING.md` and `openspec/project.md`
- [ ] 2.4 `.claude/commands/engine-test.md`, `bot-debug.md`, `check-game-rules.md` — these tell
      a model to run `npm test` in a directory that will not exist, which is the one class of
      stale reference that actively misleads (design D3)
- [ ] 2.5 `docs/kotlin/README.md` §1d becomes past tense, and §4's "TypeScript" command block goes

## 3. Check nothing else reaches for it

- [ ] 3.1 `grep -rn "legacy-web"` over the Gradle build, the workflows and the Worker: expect
      the one comment in `composeApp/build.gradle.kts` about card art, and nothing else
- [ ] 3.2 `./gradlew releaseGate` green, and the twelve room gates green, **before** the
      deletion — so a later failure cannot be blamed on it
- [ ] 3.3 Leave the ~79 "Ported from `legacy-web/…`" comments alone, deliberately (design D3)

## 4. Delete it

- [ ] 4.1 `git rm -r legacy-web/`, in a commit that does nothing else, so it reverts cleanly
- [ ] 4.2 `lefthook.yml`, `codecov.yml` and `.gitignore`: anything left pointing into it
- [ ] 4.3 `./gradlew releaseGate` green again, and the room gates, on the commit after
- [ ] 4.4 A line in `docs/kotlin/README.md` §1d saying it is done and on which commit, because
      "the corpus can no longer be regenerated" is a fact somebody will want dated
