# When the app fails: crashes, errors as values, and reports that reached nobody

The crash reporter that was installed too late to catch a startup crash, the network failures
that reached players as JSON parser errors, and the two reasons a reported crash produced
nothing to look at.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 6m. Crashes, and what a failed network call is allowed to look like

Two things the app claimed to have and did not, both reported from a phone.

### The crash reporter existed and was never installed in time

`installCrashHandler` was called from a `LaunchedEffect` inside a composable inside `App()`.
So the handler came into existence **after the first composition** — and the crash worth having
most is the one that stops the app on the launcher, which happens while the vault is being
opened, the deep link is being read and the resources are being resolved. All of that is before
`App()` draws anything. Nothing failed; there was simply nobody listening.

Worse, `SENTRY_DSN` was `private const val SENTRY_DSN = ""` in source, so **every build there
has ever been** reported nowhere, the ones that shipped included.

Both are fixed and the shape is worth knowing:

- `Crashes` (`composeApp/.../crash/Crashes.kt`) is a process-level object with an idempotent
  `install`, called by each of the four entry points **before** the call that composes —
  `MainActivity.onCreate` before `setContent`, `main()` before `application {}` and
  `ComposeViewport`, `MainViewController()` before `ComposeUIViewController {}`. `App()` still
  calls `install` as a last resort, for a host that embeds it directly, and its real remaining
  job is `Crashes.watching { … }`: *where* the app is, read at the moment of a crash.
- The DSN is a **build input**, generated into `BuildInfo.kt` by `:composeApp:generateBuildInfo`
  from `-Pvinto.sentryDsn=` or `VINTO_SENTRY_DSN`. It defaulted to empty for one commit and now
  defaults to **the project's own DSN**, at the product owner's direction: defaulting to empty
  meant every build any of us made still reported nowhere, which is how a crash on opening an
  online game came and went with nothing to look at. The trade is small and real — a DSN's key
  is write-only, so what a stolen one buys is the ability to spend the project's quota — and it
  stays overridable, with an empty string switching reporting off entirely.
- **`App()` does not install the reporter**, and that is now load-bearing. It used to, as a
  fallback for "a host that embeds `App()` directly", and the only such host is the test suite:
  with a real DSN that fallback would arm a live reporter inside every Compose test and post a
  CI runner's failures into the project's Sentry.
- The app scope carries `Crashes.handler()`, so a coroutine that fails on it is **reported**
  rather than printed to a console nobody is reading. That is the failure players describe as
  "it just sat there": the app is alive, the room never loads, and no fatal handler will ever
  see it.
- The per-process report budget went from **one** to five distinct failures. One was right while
  the fatal handler was the only caller and wrong the moment background failures started
  arriving too — the first thing to go wrong would have silenced the crash that ended the app.
  Repeats are still deduplicated by type and message, so a retry loop cannot run up a bill.

`CrashInstallTest` reads the four entry points and asserts the *ordering*, which is the only way
to check it: a runtime test composes `App()` and so installs the handler either way, and the
window that matters is the one before a harness has control. It failed on its own first run —
`import androidx.activity.compose.setContent` is a mention of `setContent` above every line of
the body, so a naive search finds it at character zero and every ordering check passes.

**Still not covered**, and recorded rather than done: a genuine native crash on iOS (a signal, a
Swift trap) is what the Sentry SDK would add; `setUnhandledExceptionHook` catches a Kotlin
exception reaching the top and nothing below it. Task 8.2 and `design.md` §A9 carry it.

### A network call that failed said so in a serialization error

All four connectors **discarded the HTTP status**. The room service answers 404 for a code it
never issued, 503 when it is closed, 429 for a room that is full — and every one of those bodies
went straight into a JSON parser, so a player who mistyped a room code was shown *"Unexpected
JSON token at offset 0"*. That is not a cosmetic fault: it is the difference between retyping
the code and deciding the app is broken.

And `RemoteRoom`'s socket loop caught every exception and backed off, for ever. A mistyped code,
a closed service and a phone in a tunnel produced the same screen — "Reaching the room…",
indefinitely — with nothing that could say which, or whether waiting would help.

What replaces it:

| | |
| --- | --- |
| `RoomTrouble` | Six things that can go wrong, in one vocabulary for four transports |
| `RoomServiceException.permanent` | Whether trying again can change the answer |
| `requireOk(status, body)` | Every connector's REST calls go through it, so a status means the same thing everywhere. The service's own words are carried through; an HTML error page is not |
| `RemoteRoom` | A permanent trouble closes the room at once; a room that has **never** answered gives up after three tries. A socket that drops *mid-game* still reconnects for as long as the app is open — the seat is held by its token |
| `LobbyWord.UNREACHABLE`, `LobbyUi.canRetry` | The lobby tells "this room ended" from "we never got there", and offers another go at the second |
| `RemoteRoom.notices` | Now actually read. It carried every lobby refusal the room sent and **nothing consumed it**, so a refused "add a bot" spun a seat for five seconds and then said nothing at all |

Two platforms can name the refusal and two cannot. Android's OkHttp hands the refusing response
to `onFailure` and the JDK wraps it in a `WebSocketHandshakeException`, so a 404 becomes "no such
room"; a **browser deliberately hides** the HTTP response of a failed WebSocket upgrade from the
page, and `NSURLSessionWebSocketTask` reports it through a session delegate this connector does
not have. Giving up after three tries is what turns those two into a sentence rather than a
spinner — a vaguer sentence, and a sentence.

Held by `RoomTroubleTest` (in `commonTest`, so the mapping is identical on all four targets) and
`UnreachableRoomTest`, which drives the real `RemoteRoom` against a refusing connector on virtual
time.

## 6o. Errors as values, after a crash nobody could look at

An online game was opened and the app died. There was no report, because reporting had never
been switched on in any build — which is the first thing §6m is about and the reason the DSN is
now the project's own by default rather than an empty string. So the honest answer to *why*
that crash happened is: **nobody knows, and that was the bug behind the bug.**

What could be done was to go and find every way that path can end the app. Two were real, and
both are the same mistake in different clothes.

### The model handled it and the screen crashed on it

`tableFor` opens with `players.firstOrNull { it.id == viewerId } ?: return Table(Ask.Watching)`.
One function later, `FeltTable` reached the same seat with `players.first { it.id == viewerId }`
— which throws. So a view whose viewer has no seat produced a considered "you are watching" from
the model and a `NoSuchElementException` from the felt, with nothing between it and the launcher.

A solo game always seats you. It could only ever have fired online, where a room decides who is
seated, and online is the one place nothing catches it.

The fix is the type: `PlayerView.mySeat` is nullable, so the compiler asks the question at every
call site. The felt now draws four seats either way — a watcher's fourth player takes the chair
the viewer's own hand would have used, because the felt has exactly four places and a player
with nowhere to sit disappears from the game.

### The catch listed the exceptions somebody had thought of

`RemoteGameSession.dispatch` caught `TimeoutCancellationException` and `IllegalStateException`.
That covers a socket that is gone and a room that does not answer. It does not cover the write
*failing on a socket that is there* — an `IOException` on Android, a `CompletionException` on
the JVM, a wrapped `NSError` on iOS, a `DOMException` in a browser. Three of those four reach
the top of a coroutine and end the app.

### So the boundary answers instead of throwing

| | |
| --- | --- |
| `RoomConnector` | Returns `RoomAnswer<T>` — `Ok` or `Failed(trouble, reason)`. Nothing in the interface throws |
| `answering { }` | The one place allowed to catch broadly. Each connector wraps its own transport in it, so nothing above the seam sees an exception |
| `SendOutcome` | `Sent` or `Failed(reason)`, for a message handed to the wire |
| `permanent(trouble)` | A `when` with no `else`, so a seventh trouble cannot be added without somebody deciding whether it is worth retrying |

The point is not tidiness. A `when` over a sealed type is **exhaustive or it does not compile**,
so a call site that forgets the failure is a build error rather than a crash a player finds.
That was proved on the way in: changing `RoomConnector`'s return types broke `OnlineScreen` and
`DiscoverScreen` immediately, at exactly the two places that had been `catch (e: Exception)` and
would have gone on compiling for ever if either had been deleted.

Deliberately **not** `kotlin.Result`: it carries a `Throwable`, which is the thing being got rid
of, and `getOrNull` makes ignoring the failure a character shorter than handling it.

### And where the type system cannot help, the build does

`List.first {}` returns `T`, not `T?`, and throws. Kotlin has nothing to say about it, so
`PartialFunctionTest` does: it reads the files that read wire data — the online screens, the
session, the lobby model — and fails the build on `first {}`, `first()`, `last()`, `single()`,
`getValue(` and `!!`. It caught a fresh one on its own first run, in code committed an hour
earlier, whose defence was that the caller had already done a `firstOrNull` two frames up. That
is precisely the reasoning that put a `first {}` on the felt.

Scope is the client's view of the wire and stops there. The engine is not covered and should not
be: it owns its own state, `first {}` on a list it has just built is total in fact, and a rule
that cried wolf there would be switched off within a week.

## 6p. Why a crash on the online screen reached nobody

Reported twice: open the online screen, the app exits, and Sentry has nothing. The second half
of that turned out to have two causes, both certain, and both invisible to every gate this
repository has.

### The app had no `INTERNET` permission

`androidApp/src/main/AndroidManifest.xml` declared no permissions at all. Android then refuses
every socket the process opens — so **online play could not work** and **no crash report could
ever leave the device**: the handler fired, the envelope was built, and the platform denied the
POST. Two failures wearing one face, from a line nobody had written.

Nothing in the build could have caught it. `assembleDebug` produces a well-formed APK, every
JVM suite passes, and the Compose tests run in a process with no permission model at all. It
took a phone, and then it took reading the merged manifest. `ManifestTest` reads the manifest
now — and also asserts the list stays *short*, because a permission is a question asked of a
player and this game has no business asking most of them.

### And the report was fire-and-forget on a process being killed

`CrashReporter.report` did `scope.launch { post(...) }` and returned. The handler then chained
to the platform's, which on Android ends the process at once. A DNS lookup, a TLS handshake and
a POST do not fit in the microseconds between those two lines, so a correct reporter with a
correct envelope delivered nothing.

Two changes, and the second is the one that actually guarantees it:

- **The crashing thread waits.** `awaitCrashReport` is `runBlocking` with a four-second ceiling
  on the JVM, Android and Apple; in a browser it is a no-op, because an unhandled rejection
  does not tear the page down and there is nothing to block for. Short on purpose: an app that
  has already crashed must not sit there because a network is not answering.
- **The envelope is written down before the network is touched.** `Crashes` stores it in the
  vault under one key and clears it only when a send actually succeeds, so a POST cut off
  halfway is retried by the next launch. One slot, because a phone in a crash loop would
  otherwise fill the vault with copies of one bug and the newest is the one worth having.

`CrashReporter` is split into `envelopeFor` and `send(onSent)` to make that ordering possible,
and `CrashReporterTest` pins it: a failed send leaves the stored copy alone, a successful one
clears it.

### And the reason online play does not work at all is a flag

Checked against the deployment rather than reasoned about:

```
$ curl https://vinto-room.kupalinka.app/health
{"ok":true,"service":"vinto-room","engine":"kotlin","roomOpen":false}
$ curl -o/dev/null -w '%{http_code}' https://vinto-room.kupalinka.app/rooms          # 503
$ curl -X POST .../rooms -d '{"isPublic":false,"hostNickname":"probe"}'
The room is closed: server-side action validation is not implemented yet (see
ActionValidator, task 4.4). POST /replay to exercise the engine.                     # 503
```

So browsing and creating both fail at the service, exactly as designed — `ROOM_OPEN` is
`"false"` in `wrangler.jsonc` and ROOM.md §6i step 4 has never been walked. Two things follow,
and the second is the one nobody would notice:

- **The flag's own comment is now out of date.** It says "flip this to `true` in the same commit
  that lands the validator, never before"; the validator landed in phase 4 and the flag did not
  move. Opening the room is a deliberate act with credentials, so it stays a maintainer's step —
  but it is no longer *blocked* on anything in the code.
- **The deployment is stale.** Its refusal names task 4.4 as unfinished, which dates it to
  before most of this branch. Flipping the flag on what is deployed would open an old room; the
  Worker has to be rebuilt and redeployed either way.

What the client does about it: the **trouble** picks the sentence and the service's words go
underneath in small type. A player who taps Browse now reads "Online play is not open yet.
Single player and the lesson work as normal" rather than "server-side action validation is not
implemented yet (see ActionValidator, task 4.4)" — which is true, is addressed to somebody who
works on this, and tells a player nothing. `troubled()` is a `when` with no `else`, so a seventh
`RoomTrouble` is a compile error rather than a screen that says nothing.

### What is still unknown

**Why the app exits.** That is not diagnosed, and saying otherwise would be a guess dressed up.
What is now true is that the next one reports itself: the permission is there, the report
blocks until it is away, and an envelope survives the process dying. A crash during composition
reaches the default handler like any other, so this covers it.
