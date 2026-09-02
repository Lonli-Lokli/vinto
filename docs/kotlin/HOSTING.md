# Hosting on kupalinka.app

The website: why it is a Worker rather than a Pages project, what a link to it says about
itself, the caching rule that corrupted the app's own text, and the CORS gate that nothing
browser-shaped had ever asked for.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 6c. Hosting on kupalinka.app

Vinto is hosted alongside the portfolio games in `~/sources/gulnya/games-portfolio-brief.md`.
Two hostnames, two deploy targets:

| host                       | what                                            | how                                            |
| -------------------------- | ----------------------------------------------- | ---------------------------------------------- |
| `vinto.kupalinka.app`      | the Compose/Wasm client                         | Cloudflare **Pages** project `vinto`           |
| `vinto-room.kupalinka.app` | the room Worker + Durable Object, and `/replay` | `wrangler deploy` from `worker/cloudflare` |

The Worker gets its own hostname rather than a path under `vinto.kupalinka.app`, because that
host is a Pages project and layering a Worker route over a Pages custom domain is a precedence
puzzle nobody should re-derive during an incident. It is still **same-site**, so the client's
CSP needs one `connect-src` entry on its own site. `px.kupalinka.app` is a separate Worker for
the same reason.

**Compose for Web is an exception to the portfolio convention**, which says plain DOM over
`:engine` and never Compose. Taken deliberately, with the 3.7 MB measurement in hand; the
reasoning is in design **D1a** and must not be copied to another game without reading it.

### What the exception does not excuse

These are properties of the zone and of the visitor, not of the module shape, and they apply to
`vinto.kupalinka.app` exactly as they do to every other game:

- **Every script the page reaches carries a content hash.** The `kupalinka.app` zone's Browser
  Cache TTL _overrides_ a weaker origin `Cache-Control`, so under fixed names a stale script
  keeps naming a wasm binary the next deploy replaced — a 404 and a dead page, not a stale one.
  This has taken portfolio sites down before; treat it as a hard invariant, not a preference.
- **A newly-deployed asset fetched at its canonical URL before the edge has it** returns the
  Pages SPA fallback: `index.html`, **200**, `text/html` — then cached `immutable` for a year by
  a path-matched `_headers` rule. Content-addressing makes that permanent rather than momentary.
  Probe with `?cb=`, and never point a headless browser at a fresh deploy.
- **Usage counting from the loader**, not the bundle, so a visitor whose browser cannot run
  WasmGC still counts. No cookie banner, an opt-out control, GPC/DNT honoured.
- **The §3b gate**: responsive at 1440px as well as 380px, keyboard-complete, focus and scroll
  surviving re-render, both themes at WCAG AA, `prefers-reduced-motion` honoured.

### The shared machinery does not currently reach Vinto

The first three of those live in `~/sources/gulnya/web-template/`, which exists precisely
because copying them by hand went wrong: the brief records Niva shipping a deploy script with
**no chain verification at all**, printing a green tick over a dead site, months after Vodar's
grew that check. Nothing was wrong with either file — "copy this verbatim" is an instruction to
a person, and people copy things once.

`sync.mjs` mirrors a `web/` module layout. Vinto's web build is `composeApp` with a Gradle
root one directory down, so it cannot participate as-is, and hand-copying `content-hash.js` and
`web-deploy.sh` into this repo would make Vinto the **third** copy — exactly the outcome the
brief warns about, and it names that as the moment to stop copying and move the verification
into versioned tooling instead.

Unresolved on purpose: it means editing shared tooling that two shipped games depend on, which
is not a change to make casually or as a side effect of hosting Vinto.

**Resolved differently, and the resolution is worth reading before touching the shared
template.** `.github/workflows/deploy-web.yml` is a `workflow_dispatch` deploy that does the
two things the shared scripts do — content-address the entry script, then poll until the edge
is serving *this* build rather than merely answering — without being a third copy of them. It
is a workflow rather than a script for the same reason `deploy-room.yml` is: "copy this
verbatim" is an instruction to a person, and a check that lives in CI is one nobody has to
remember to run. The shared template is untouched; if it is ever reworked to reach a Gradle
repository, this is the caller to point at it.

### The site is a Worker, and that is why it needs no dashboard step

Reported: `vinto.kupalinka.app` answers `DNS_PROBE_FINISHED_NXDOMAIN`. Confirmed — the host
had no record at all, while `vinto-room.kupalinka.app` and `kupalinka.app` both resolved. Not
a broken deploy: nothing had ever been published there.

It was published as a **Pages** project first, and that worked. It is a **Worker serving
static assets** now, and the reason is worth stating because it is the only one:

> A Pages custom domain can only be attached in the dashboard. `wrangler pages` has no
> command for it. So the hostname — the entire point of the exercise — stayed behind
> "somebody has to open a browser", on a project whose deploy story is otherwise a button in
> the GitHub mobile app. A Worker claims its hostname from `routes` in
> `composeApp/cloudflare/wrangler.jsonc`, exactly as the room already did.

Three things came with the move, none of which were the motive:

- **`not_found_handling: "404-page"`** states the 404 behaviour in configuration instead of
  depending on whether a `404.html` happens to be present. See below for why that matters.
- **One deployment model** in the repository instead of two. `deploy-room.yml` and
  `deploy-web.yml` are now the same shape, and `worker/cloudflare/wrangler.jsonc`'s comment
  about not layering a route over a Pages domain no longer describes a real constraint.
- **`workers_dev: false`**, so the site has exactly one address. A `*.workers.dev` fallback
  would be a second reachable copy — indexable, shareable, and destined to be pasted into a
  chat by whoever found it first. A canonical tag tells a crawler which one counts and does
  nothing about the person who bookmarked the other.

`_headers` and `_redirects` are read from the asset directory exactly as they were on Pages,
so those files are unchanged; redirects are applied before the not-found handling, which is
what keeps `/r/<code>` reaching the app rather than the 404 page.

The Pages project still exists, serving an older copy at `vinto-6dr.pages.dev`. Nothing links
to it; deleting it is a dashboard step with no deadline (DEPLOYMENT.md §6c).

Note what this blocks besides the website: §6b of DEPLOYMENT.md asks for `assetlinks.json` and
`apple-app-site-association` to be served from `/.well-known/`, and README.md §1f lists them
as blocked on credentials. They are also blocked on this — there has been no site to serve
them from.
They belong in `composeApp/src/wasmJsMain/resources/.well-known/`, where every deploy carries
them.

### What a link to the game says about itself

The page was a title, a description and a `theme-color`. That is enough for a browser tab and
nothing else: a link posted to a group chat unfurled into a bare URL, because there was no
`og:image`, no `og:title`, and a body that Compose empties on the first composition — so a
crawler that will not execute four megabytes of WebAssembly found an empty document.

It now carries the full Open Graph and Twitter sets, a canonical link, a `VideoGame` JSON-LD
block, a web app manifest, `robots.txt` and a sitemap. Three things in there are decisions
rather than boilerplate:

- **The body is content, not a placeholder.** It says what the game is and it is replaced a
  moment later for anybody whose browser can run it. The two visitors who read it are a
  crawler and somebody without WasmGC — for whom the alternative was a dark rectangle reading
  "Dealing…" for ever, with no way to tell a slow connection from an unsupported browser.
- **`robots.txt` disallows `/r/`.** A room code is an invitation: it resolves for as long as
  that room exists and to nothing afterwards, so an indexed one is a dead link outliving its
  game by months — and it is somebody's invitation, which a search result is not where they
  meant it to be read.
- **`_redirects` scopes the SPA fallback to `/r/` instead of `/*`, and a `404.html` is
  committed.** The blanket rule is the obvious one and it is the trap this section already
  warns about: with it, a missing asset is answered with `index.html`, 200, `text/html`, and
  cached. Scoping it turned out to be **necessary and not sufficient** — probed against the
  live deployment, `/no-such-file.js` still returned `200 text/html`, because the fallback is
  Pages' own default for an unmatched path rather than anything the redirect rules were
  doing. A committed `404.html` is what makes Pages answer 404; the scoped rule is what keeps
  an invitation reaching the app rather than that page. Both, or neither works.
- **`<base href="/">`, and every invitation was broken without it.** The third act of the same
  trap, and the one that actually shipped. The shell is served at `/r/ABC123` with a 200 so
  the address bar keeps the code for `Main.kt` to read — which means the page is *at* that
  path, and a relative script src asks the browser for `/r/composeApp.<hash>.js`. The scoped
  fallback answers that with the shell, 200, `text/html`. The browser loads an HTML document
  as a classic script, dies on `Unexpected token '<'`, and the page sits on "Dealing…" for
  ever.

  Reported from a phone. Confirmed against the live site with one `curl`
  (`/r/composeApp.<hash>.js` → `200 text/html`, 10,843 bytes — the shell), then reproduced in
  Chromium against a server that mimics the fallback: at `/` the page boots, at `/r/CODE` it
  does not, with exactly that SyntaxError. Fixed the same way and the fix verified in the same
  harness.

  A `<base>` rather than a leading slash on each tag, because the script is not the only
  casualty: the icons and the manifest resolve the same way, and so does anything the wasm
  bundle fetches for itself at runtime, which no amount of editing `index.html` reaches.
  Held by `everyRelativeUrlResolvesAgainstTheRoot`.

  Two things worth keeping. **A link to `/` always worked**, which is why this survived a
  deploy, a probe of the live site and seven cases in this file — every check anybody had
  written asked for the root. And the fix nearly broke the deploy: `deploy-web.yml` rewrites
  `src="composeApp.js"` to the hashed name with a `sed` that has no `/g`, and the comment
  explaining all of the above originally quoted that exact string, giving the file two
  matches on two lines. The comment is worded around it now.

`WebShellTest` (8 cases, `composeApp:jvmTest`) holds all of it: every tag present, the image
URLs absolute, the three descriptions identical, every file the shell names really on disk,
the share card really 1200x630, and the redirect and robots rules agreeing with `INVITE_PATH`.
It asserts no wording, because copy changes and the failures worth catching are the silent
ones.

Two things about it are worth keeping. It is **non-vacuous**, checked by breaking the page
four ways — a relative `og:image`, a drifted description, a deleted favicon, a blanket
fallback — and watching each one fail. And `wasmJsMain/resources` is declared an **input to
the test task**, because without that the first three probes passed in 766 ms: nothing else in
this build reads that directory, so editing the page left `jvmTest` UP-TO-DATE and the gate
silently did not run. A green tick over an unread file is worse than no check at all.

### The browser could not read the room service, and nothing had ever asked it to

The site went live, online play failed on it, and the second failure was the more interesting
one. With the `RequestInit` bug fixed the error changed from a specific complaint to
`TypeError: Failed to fetch` — which in a browser almost always means CORS, and did.

`vinto.kupalinka.app` and `vinto-room.kupalinka.app` are different **origins**. Every `fetch`
the browser client makes is cross-origin, so the browser performed each request, received a
perfectly good `200`, and then refused to hand it to the page because no
`access-control-allow-origin` came back. What a player saw was "No connection to the room
service" about a call that reached the service and returned data.

**Same-site is not same-origin**, and this is where the earlier reasoning misled. The room has
its own hostname deliberately, and `wrangler.jsonc` records why: it keeps the socket's origin
first-party so the page's CSP needs one `connect-src` entry on its own site. All true, and
CORS does not care — it compares scheme, host and port exactly. Being same-site bought
nothing here.

It had never come up because nothing browser-shaped had ever called this service. Android, the
JVM and iOS do not enforce CORS, and the web client had no deployment to be called from until
the site existed. Twelve room gates, all in Node, all sending no `Origin`.

`gate-cors.mjs` is that gate now, and two things about it are worth keeping:

- **A `*` here would not have been a vulnerability**, and the gate says so where it checks
  against one. This API carries no cookies and no ambient credentials — a seat is proved by a
  token in the message — so a hostile page reading a room listing learns what it could have
  learned from its own server. The boundary is `ActionValidator` and the seat token. The
  origins are named because "who is this for" is worth writing down, not because a wildcard
  would let anybody in.
- **The first version of it broke the gate next to it.** It checked the header on
  `POST /rooms`, which mints a room every time and has no malformed body that gets refused
  first — so it consumed the registry's per-source allowance and `gate-room-codes`, running
  after it, failed on "creating a room returns a code". Diagnosed by re-running that gate
  first on clean state, where it passed. The cross-origin POST now goes to `/replay`, which is
  a pure function of its argument and holds no state.

The WebSocket path is returned untouched, which is not a nicety: a 101 carries a live
`webSocket` on the response object, and copying it through `new Response(...)` both loses that
and throws, because 101 is not a status a `Response` may be constructed with. Sockets need no
CORS anyway — a browser does not apply it to a WebSocket handshake.

### What the first publish taught, that no local run could

It worked, and reported failure. The upload was clean — 43 files, `_headers` and `_redirects`
accepted — and then the verification step polled `https://vinto.pages.dev` for five minutes
and gave up, because it had **built that address out of the project name**. A `pages.dev`
subdomain is globally unique and `vinto` was already somebody else's, so Cloudflare suffixed
ours: the site is `vinto-6dr.pages.dev`. wrangler prints the address it deployed to, and that
is now the only one the workflow believes.

Worth stating plainly, because the shape recurs: the failure was in the *check*, not in the
thing checked, and a check that constructs the identity of what it is verifying is not
verifying it. The same reasoning is why the poll asks whether the page names *this* build's
script rather than whether the site answers at all.

Two things were then true of the live site that no local gate could have shown, and both are
now held by `WebShellTest`: the 404 behaviour above, and that `/r/ABC123` really does reach
the shell. The deploy workflow re-checks both after every publish and warns rather than
failing — by then the publish has happened, and a warning naming the status is more use than
a red run that hides it.

**Link previews stay blank until the custom domain is attached.** `og:image` names
`https://vinto.kupalinka.app/share-card.png`, which is the right canonical answer and a dead
URL today. It was left pointing at the canonical host rather than rewritten to
`vinto-6dr.pages.dev`, because the rewrite would be correct for about a day and the canonical
host is one dashboard step away.

The icons and the share card are generated by `tools/make-web-icons.py` and committed, the
same arrangement as the launcher icons. The card is the app's own material — the felt under
its lamp, five real cards from the deck, and the name in Cinzel, the face `theme/Type.kt`
reserves for it.
