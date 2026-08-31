# Design: what is left when the code is finished

## D1. One change, not two

The obvious split is by blocker: store credentials in one change, Cloudflare and traffic in
another. Rejected, because the blocker is not what a reader is looking for. Somebody picking
this up has a keystore, or a dashboard login, or a phone in their hand, and wants to know what
they can do with it — and the answer crosses that boundary constantly. Opening the room
unblocked the load test *and* the analytics dashboard *and* the deep links, because the deep
links needed a website to serve their association files from.

So it is one change, phased by what each phase produces rather than by what it waits on, and
every task names its own blocker.

## D2. Retired-and-carried, not left open

`add-game-recording-replay` archived at 22 done and 4 retired: the four were browser-side work
in a client that no longer exists, and the requirements they served are held by the Kotlin
engine instead. That is the precedent, and this is the same shape — the work is not abandoned,
it has moved to where it belongs.

The alternative is to leave `migrate-to-kotlin-multiplatform` open until somebody has a Mac, an
upload key, four willing humans and a week of traffic. That is not a migration any more. It is
a release, and calling it a migration keeps 35 requirements filed as proposals for however long
that takes.

**What this is not**: an excuse to tick things. Every carried item keeps its `[ ]` and its
blocker. A change that archives by lowering the bar teaches the next reader that the bar moves.

## D3. §7b of DEPLOYMENT.md sends the maintainer to the wrong project

Found while reading analytics 5.3 against the tree rather than against its own text, and it is
a real defect in an instruction rather than a stale nuance.

The task says Web Analytics is "a per-site switch in the dashboard… there is nothing in this
repository to change", and DEPLOYMENT.md §7b says to open **Workers & Pages → the `vinto`
Pages project → Settings → Web Analytics**. Both were written when the site was a Pages
project. It is not one. README §6c records the move to a Worker — a Pages custom domain can
only be attached in the dashboard, which is the one thing this project's deploy story refuses
to require — and DEPLOYMENT.md §6c says in as many words that the `vinto` Pages project is a
**leftover**, serving an older copy at `vinto-6dr.pages.dev` that nothing links to.

So following §7b today switches on page-load counting for a dead site, and reports nothing, and
looks like it worked. The instruction is corrected in this change (task 4.2); the switch itself
stays blocked on the dashboard, which is what 5.3 always was.

## D4. Deep links are the one missing requirement

Everything else carried here is an *act* — sign a build, press a switch, watch a phone. Acts do
not belong in `openspec/specs/`, which says what the game is held to.

An invitation link is different. `roomCodeFrom` parses an https link, a `vinto://` link, a bare
path and a bare code, and refuses anything the registry could not have issued — sharing the
same `looksLikeRoomCode` the Worker applies, so the client and the room cannot disagree. Both
Android intent filters are declared, both iOS handlers exist, the browser reads its own path,
and an invitation shares a link with the code underneath it for reading aloud. Five tests.

And none of it is written down as a requirement anywhere. `mobile-app` does not mention it;
neither does `online-multiplayer`. So the half that is built has no spec, and the half that is
missing — `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association`, each
naming a real credential — has nothing saying it is required. Until both files exist the https
links open the website instead of the app, which is a silent degradation: the link works, it
just does the wrong thing.

That is worth a requirement, and it is the only one this change adds.

## D5. What "done" means

This change archives when every task is either ticked or retired with a reason — the same bar
the other three are being held to. It is explicitly **not** a release gate: the game ships to
the stores when phase 2 is done, and phases 3–5 continue afterwards.

The one thing that would make it wrong to archive is ticking an item nobody verified on the
hardware it is about. Six of these have never run on a real phone, a Mac, or a store account,
and no amount of work in a container changes that.
