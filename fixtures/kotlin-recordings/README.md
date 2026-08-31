# Recordings this engine wrote

Empty, and that is fine. This directory exists so that a recording produced by the **Kotlin**
engine can never be mistaken for one produced by the TypeScript engine — which is a distinction
worth a directory even before there is anything in it, because the two are indistinguishable as
files and worth very different amounts as evidence.

## What a recording in here proves, and what it does not

**Proves:** that the engine's behaviour has not *changed*. Drop a recording in here, replay it
in CI, and a handler that starts behaving differently is caught at the action where it diverges.
That is a real regression gate and it is worth having.

**Does not prove:** that the behaviour is *right*. These are written by the engine under test,
so they agree with it by construction. A handler that has been wrong since the day it was
written produces a recording that confirms it, for ever.

The corpus next door in `fixtures/recordings/` is the other thing: 50 games whose hashes were
computed by a **second implementation**, written from the rules rather than from this code.
That is frozen and cannot be extended — see its README for why the generator was deliberately
not ported.

So: this directory is for regression coverage. That one is evidence. Do not merge them, and do
not move a file between them.

## Adding one

`Recorder` produces a `Recording`; `session.report(...)` is the same document a player's bug
report carries, and it drops straight into `replayRecording` (README §6l — that round trip goes
through *text*, deliberately, which is how a missing `formatVersion` was found). Name the file
for the behaviour it pins rather than for a seed, and say in the commit what would make it go
red, because a recording nobody can interpret gets deleted the first time it fails.

`CorpusIsFrozenTest` does not look in here. Nothing about this directory is frozen.
