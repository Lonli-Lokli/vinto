# Change: Ship the apps, and operate the live service

## Why

Three changes are finished as engineering and cannot be closed, because what is left in each
is not code. `migrate-to-kotlin-multiplatform` is 60 done and 6 waiting on an upload key, a
physical phone, a Mac or a store account. `add-live-analytics` has its release gate (phases
1–4) complete and four phase-5 items waiting on a Cloudflare dashboard and a week of traffic
that has only just started. Nothing in either is blocked on a decision, and both have been
carrying those items long enough that the changes read as unfinished work when they are not.

Leaving them there costs something specific: **`openspec/specs/` is not canonical while they
are open.** A requirement in a change folder is a proposal; only archiving merges it. So the
eight further `online-multiplayer` requirements, all of `mobile-app`, `kmp-shared-engine`,
`kmp-bot`, `kmp-game-client`, `cross-implementation-parity` and `analytics` — 40 requirements
describing the game that actually ships — are still filed as things somebody proposed.

The Kotlin rewrite merged to `master` on 2026-08-31 (52dcc20). Its CI is green, and the push
triggers that were inert on a feature branch are live: that commit deployed both the room and
the website. The migration is over. What is left is a different kind of work, and it deserves
its own change rather than a footnote in the one it outlived.

## What Changes

- **Carry the blocked work here**, item by item, each with what would unblock it and how far
  it already got. Nothing is re-planned and nothing is re-decided: this is a move, not a
  rewrite. `docs/kotlin/README.md` §1f stays the maintainer-facing version of the same list.
- **Retire those items from the two changes they came from**, with a pointer, and archive all
  three open changes — including `retire-legacy-web`, which is 16/16 and needs nothing.
  Retiring rather than deleting follows `add-game-recording-replay`, archived at 22 done and
  4 retired.
- **Add the one requirement none of the specs carry**: an invitation link must reach the app.
  `roomCodeFrom`, both Android intent filters, both iOS handlers and the browser's own path
  are all built and tested; the two association files that make an https link open the app
  rather than the website are not, and nothing anywhere says they are required. That is a
  requirement, and it is the only genuinely missing one — everything else here is an act.

## What does not change

- **No task is being marked done that is not done.** Six of these have never run on the
  hardware they are about, and saying otherwise in a file that is meant to be the record
  would be worse than leaving them open.
- **No new engineering is proposed.** Where something turned out to be doable after all it is
  done in this change and ticked; where it did not, it is carried with its blocker named.

## Impact

- Affected specs: `app-distribution` (new — deep links)
- Affected code: none by this change itself, beyond the correction in
  `DEPLOYMENT.md` §7b noted in tasks 4.2
