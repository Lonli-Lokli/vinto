# Deploying Vinto

Written for somebody who is not a programmer. You do not need to understand the game's code to
follow this. You do need to be able to copy and paste, and to be careful about which of these
things are secrets.

If any step does something other than what it says here, **stop and ask** rather than
continuing. A half-done deploy is much harder to sort out than a not-started one.

---

## 1. What Vinto is made of

Three separate things, which live in different places and are deployed separately.

| Piece | What it is | Where it lives |
| --- | --- | --- |
| **The room service** | The referee for online games. It deals the cards, checks every move and tells each player only what they are allowed to see. | Cloudflare — a "Worker" at `vinto-room.kupalinka.app` |
| **The web version** | The game you can play in a browser. | Cloudflare Pages at `vinto.kupalinka.app` |
| **The phone apps** | Android and iPhone. | Google Play and the App Store |

Playing on your own does not use the room service at all — the game runs entirely on your own
phone. The room service exists only for playing with other people.

---

## 2. What you need before you start

- **A Cloudflare account** with the `kupalinka.app` domain already added to it.
- **Node.js** installed on your computer (version 22 or newer). Check by opening a terminal
  and typing `node --version`.
- **Java 17** installed. Check with `java -version`.
- The Vinto code on your computer.
- About an hour, and nobody waiting on you.

---

## 3. Every setting there is

This is the complete list. Nothing else needs setting.

### 3a. Settings that live in the code (already correct, listed so you recognise them)

You do not normally change these. They are here so that if you see one, you know what it is.

| Name | What it means | Value now |
| --- | --- | --- |
| `ROOM_OPEN` | Whether online play is switched on. `"false"` means the service answers "we are closed" to anybody trying to play. | `"false"` — **see §6, this is the switch that opens the game** |
| `ANALYTICS` | Where anonymous counts are written, so we can tell how many people play. Writes nothing about any individual person. | `vinto_events` |
| `ROOM` / `REGISTRY` | Internal names for the two pieces of the room service. | fixed, never change |
| `TEST_SEED` | Makes the cards come out in a fixed order, for testing. **Must never be set on the real service** — it would let people replay the same hand. | not set |

### 3b. Secrets — never put these in the code, never paste them into a chat or an email

| Name | What it is | Where you get it |
| --- | --- | --- |
| **Cloudflare login** | Permission to publish the room service. | You log in once with a browser — §5 |
| `ANALYTICS_TOKEN` | Lets the private stats page read the counts. | Cloudflare dashboard — §7 |
| `ANALYTICS_ACCOUNT_ID` | Which Cloudflare account's counts to read. Not really a secret, but it is set the same way as one. | Cloudflare dashboard — §7 |
| `DASHBOARD_KEY` | The password on the end of the stats page's web address. Anyone who has it can read the counts. | You invent it — §7 |
| `SENTRY_DSN` | Where the game reports crashes, so a fault somebody hit at 3am is something we hear about. | Sentry → Settings → Client Keys — §7a |
| **Android signing key** | Proves an Android app update really came from us. | You create it once — §8 |

> **If a secret leaks**, the fix is to replace it, not to hope. Cloudflare tokens can be
> deleted and re-made in a minute. The Android signing key is the one exception — see the
> warning in §8, and treat that file the way you would treat a passport.

### 3c. Files on your own computer that are deliberately not shared

These three are ignored by the code-sharing system on purpose, because they are either
personal to your machine or secret. If you have just downloaded the code, they will not exist
and you may need to create them.

| File | What goes in it | Needed for |
| --- | --- | --- |
| `local.properties` | `sdk.dir=/path/to/android/sdk` | Building the Android app |
| `keystore.properties` | The four lines in §8 | Publishing the Android app |
| `worker/cloudflare/.dev.vars` | Settings for testing on your own machine only | Optional, §4 |

---

## 4. Try it on your own computer first

Always do this before publishing anything. It uses no Cloudflare account and costs nothing.

Open a terminal, go to the Vinto folder, and run these one at a time:

```sh
./gradlew detekt
```
Checks the code is tidy. You want to see **BUILD SUCCESSFUL**.

```sh
./gradlew :shared:shapes:jvmTest :shared:engine:jvmTest :shared:bot:jvmTest \
          :shared:client:jvmTest :shared:protocol:jvmTest :shared:room:jvmTest
```
Checks the rules of the game still work. This is the important one. **BUILD SUCCESSFUL** again.

```sh
./gradlew :composeApp:run
```
Opens the game in a window on your computer. Play a round. Listen for four sounds: a card
being dealt, a card landing, a thud when somebody gets a penalty, and a chime when the round
ends. Close the window when you are done.

Now the room service, on your own machine:

```sh
./gradlew :worker:jsProductionExecutableCompileSync
cd worker/cloudflare
npx wrangler dev --port 8787 --var ROOM_OPEN:true
```

Leave that running. In a **second** terminal window, in the same folder:

```sh
node gate-real-room.mjs && node gate-sessions.mjs && node gate-lobby.mjs \
  && node gate-lifecycle.mjs && node gate-limits.mjs && node gate-registry.mjs \
  && node gate-room-codes.mjs && node gate-two-clients.mjs && node gate-engine-replay.mjs \
  && node gate-analytics.mjs
```

Every one should end with **PASS**. If any says FAIL, stop — that is the system telling you
something is wrong, and publishing it would put the same problem in front of players.

Press `Ctrl+C` in the first window to stop the local service.

---

## 5. Log in to Cloudflare

Once per computer.

```sh
cd worker/cloudflare
npx wrangler login
```

A browser window opens. Approve it. You can close the browser afterwards.

To check it worked: `npx wrangler whoami` — it should print the account name.

---

## 6. Publish the room service

> **Read this before running anything.** The `ROOM_OPEN` setting decides whether online play
> is switched on. It is `"false"` today, and that is correct: the service is published and
> tested with online play *shut*, and opened only in the same release that puts the matching
> app in people's hands. Opening it earlier means people find a game nothing can connect to.

**Step one — publish with online play still shut:**

```sh
cd worker/cloudflare
npx wrangler deploy
```

**Step two — check it is alive.** In a browser, go to:

```
https://vinto-room.kupalinka.app/health
```

You should see something like `{"ok":true,"service":"vinto-room","roomOpen":false}`.

> Cloudflare takes a few minutes to update everywhere in the world. If you see the *old*
> answer, wait a minute and refresh. Do not assume something is broken and deploy again —
> that has caused someone to chase a problem that did not exist. Refresh until it changes,
> then refresh a few more times to be sure it has changed everywhere.

**Step three — check the game rules survived the trip:**

```sh
GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs
```

This replays fifty recorded games through the published service and checks every single move
comes out the same. It should end with **PASS**.

**Step four — open online play.** Only when the web and phone versions are ready to publish
on the same day. Open `worker/cloudflare/wrangler.jsonc` in a text editor, find this line:

```
"vars": { "ROOM_OPEN": "false" },
```

Change `"false"` to `"true"`, save, and deploy again:

```sh
npx wrangler deploy
```

Then check `https://vinto-room.kupalinka.app/health` again and wait until it says
`"roomOpen":true`.

**To close online play again** — if something is going wrong and you need to stop it — change
it back to `"false"` and deploy. That is the emergency brake, and it is safe to use.

### 6a. Doing all of that from a phone

Everything above needs a computer with `wrangler` on it. That is a bad place for the one
operation this project cannot do without: online play can break on a Sunday and stay broken
until somebody is back at a desk. So the same deploy can be run by GitHub instead, from the
**Actions** tab, which works in the GitHub mobile app.

> **Why the workflow lives on `master`.** GitHub only offers *Run workflow* for a workflow
> that exists on the repository's **default branch**. A copy on a feature branch does not
> appear in the Actions list at all. `deploy-room.yml` therefore went to `master` on its own,
> in a one-file pull request, while the rest of the Kotlin work is still on `kotlin` — and
> the two copies have to be kept in step, or the button runs a version nobody edited.

**Set it up once.** Both halves are web pages, so a phone is enough for these too.

1. **Make a Cloudflare API token.** In the Cloudflare dashboard: *My Profile → API Tokens →
   Create Token*, and pick the **"Edit Cloudflare Workers"** template. Under *Account
   Resources* choose the account that owns the Worker; under *Zone Resources* choose
   `kupalinka.app`. Copy the token — Cloudflare shows it once and never again.

   > The very first deploy also creates the DNS record for `vinto-room.kupalinka.app`, because
   > the Worker claims that hostname itself. If a deploy fails saying it cannot touch DNS, edit
   > the token and add **Zone → DNS → Edit** for `kupalinka.app`. Every deploy after the first
   > only needs the template.

2. **Find your account id.** It is in the Cloudflare dashboard under *Workers & Pages →
   Overview*, in the right-hand column, and it is also in the address bar of any account page:
   `dash.cloudflare.com/<account id>/...`.

3. **Give them to GitHub.** In the repository: *Settings → Environments → New environment*,
   named exactly **`room`**. Add two *environment secrets*:

   | Name | Value |
   | --- | --- |
   | `CLOUDFLARE_API_TOKEN` | the token from step 1 |
   | `CLOUDFLARE_ACCOUNT_ID` | the id from step 2 |

   An *environment* rather than plain repository secrets, for two reasons worth the extra
   click: you can add **required reviewers** so a deploy waits for somebody to approve it, and
   every deploy that has ever run is listed in one place afterwards.

**Then, whenever you want to deploy.** Open *Actions → Deploy room → Run workflow*. Two
questions:

- **Open the room to players?** Defaults to **false**, every time, deliberately. `false`
  publishes the current service with online play shut — which is the right thing to do first,
  and the right thing to do if you are only fixing a bug in the engine. Choose `true` when the
  room should actually accept players.
- **Dry run?** Builds the Worker, runs the room's gates and measures the bundle, and publishes
  nothing. Use it if you want to know a deploy *would* work.

The run does the gates first, so a room that cannot deal a hand is never published; then it
deploys; then it polls `/health` until the edge agrees, and fails if it never does. Cloudflare
takes a few minutes to update everywhere, and a deploy that reported success while the old
version was still answering is exactly the trap this waits out for you.

**The emergency brake works from a phone too**: run it again with *Open the room to players?*
set to `false`.

---

## 7. The stats page

Anonymous counts only: how many games were played, how many people got as far as pressing
"Play online", how long a round takes, what a room costs us to run. **No names, no room
codes, no way to identify anybody** — the code is built so those cannot be recorded even by
accident, and there is an automatic check that fails the build if anyone tries.

### Where the page is

`https://vinto-room.kupalinka.app/counts?key=THE-KEY-YOU-CHOSE`

Six tables: people opening the app, rounds finished against rounds walked out of, the online
funnel, how online sessions end, what broke and where, and what a round of online play costs
us. It is built into the room service itself, so there is nothing extra to publish and the
reading token never leaves Cloudflare.

**Until the three things below are set, that address answers "not found"** — exactly as if the
page did not exist. That is on purpose: a service that says "you need a password" is telling a
stranger there is something there.

### Setting it up — three things, once

> **You can do all three from a phone.** The `wrangler secret put` commands below need a
> computer, but they are not the only way: in the Cloudflare dashboard, open **Workers & Pages
> → vinto-room → Settings → Variables and Secrets**, and add each one there with *Encrypt*
> turned on. Same secrets, same service, no terminal. Use the names exactly as written below.


**1. A token that can read the counts.**

1. Go to the Cloudflare dashboard → **My Profile** → **API Tokens** → **Create Token**.
2. Choose **Create Custom Token**.
3. Give it **Account · Account Analytics · Read**. Nothing else. This token cannot change or
   delete anything.
4. Copy the token — Cloudflare shows it **once**.
5. Give it to the service:

```sh
cd worker/cloudflare
npx wrangler secret put ANALYTICS_TOKEN
```

Paste the token when it asks, and press Enter. It is stored by Cloudflare and never appears
in the code.

**2. Which account to read.** On the Cloudflare dashboard, open **Workers & Pages**; the
**Account ID** is on the right-hand side of that page. It is a long string of letters and
numbers. Copy it and run:

```sh
npx wrangler secret put ANALYTICS_ACCOUNT_ID
```

**3. A password for the page.** Make up a long random one — 30-odd characters, letters and
numbers, no words. A password manager will generate one; so will this, if you have a terminal
open anyway:

```sh
openssl rand -hex 20
```

Then:

```sh
npx wrangler secret put DASHBOARD_KEY
```

Keep it wherever you keep passwords. The page is a read-only view of anonymous totals, so this
is a lock on a filing cabinet rather than on a safe — but the address with the key in it will
end up in your browser history, so do not paste it into anything public, and treat a link to
it as the key itself.

### If you want to change the password later

Run `npx wrangler secret put DASHBOARD_KEY` again with the new one. The old address stops
working immediately.

### A note on what "no counts yet" means

The page reads a store that is filled by people playing. On a service nobody has used, or in
the first hour after opening, the tables will say **"Nothing yet."** That is the page working.
Give it a day before concluding anything is wrong.

---

## 6c. Publish the website

`vinto.kupalinka.app` does not load — *DNS_PROBE_FINISHED_NXDOMAIN*, or "can't reach this
page". That is not a fault to diagnose. There is no such address, because the website has
never been published. The room service at `vinto-room.kupalinka.app` is up and open; the
*website* has never existed.

**One step, and no browser needed.**

*Actions → **Deploy web** → Run workflow*, with the branch dropdown set to `kotlin`.

That is the whole thing. It builds the browser version of the game, checks the page,
publishes it, **creates the `vinto.kupalinka.app` address itself**, and then keeps checking
until that address really serves the version it just built.

It uses the same `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` you set up in §6a for the
room, so if the room deploys, this will too. There is a **dry run** tick-box that builds and
measures everything and publishes nothing, which is a safe way to watch it work first.

> **Give it a few minutes on the first run.** A brand-new address needs a DNS record and a
> security certificate, and Cloudflare does not switch every location over at the same moment.
> The workflow waits and re-checks for ten minutes before giving up. If it does give up, look
> at whether the deploy step itself succeeded before assuming anything is broken — this has
> already sent the maintainer chasing a bug that did not exist, twice.

### Why there is no dashboard step any more

The website used to be a Cloudflare **Pages** project, and Pages custom domains can *only* be
attached by a person clicking through the dashboard — there is no command for it. That put
the one thing you actually wanted behind "find a computer".

It is now a **Worker** serving the same files, and a Worker claims its address from a line in
`composeApp/cloudflare/wrangler.jsonc`, exactly the way `vinto-room.kupalinka.app` already
does. Same files, same picture, same everything — it is only *how* it is published that
changed, so that all of it fits in a button.

### What is in the repository already

You do not need to prepare anything. The published site includes the icons, the sharing
picture, `robots.txt`, `sitemap.xml`, the "not found" page and the rules that make invitation
links work — all of them in `composeApp/src/wasmJsMain/resources/`, and all published by the
workflow.

The two files §6b asks you to create belong in that same folder, in a `.well-known`
sub-folder. Put them there and commit them, and every future deploy carries them
automatically; put them anywhere else and the next deploy removes them.

### The old Pages project

There is a leftover Pages project called `vinto`, serving an earlier copy at
`vinto-6dr.pages.dev`. Nothing links to it and it costs nothing. Once you are happy the real
site works, you can delete it in the dashboard — Workers & Pages → vinto → Settings → Delete.
There is no hurry, and nothing breaks if you never do.

---

## 6b. Making invitation links open the app

When somebody shares a game, the invitation now carries a link like
`https://vinto.kupalinka.app/r/7KQ2MP`. Tapping it should open the app straight at that table.
For that to work on a phone, the website has to vouch for the app — otherwise anyone could
claim to handle your links. That means publishing **two small files** on the website, once.

They go in a folder called `.well-known` at the top of the Pages project, so they end up at
`https://vinto.kupalinka.app/.well-known/…`. In this repository that folder is
`composeApp/src/wasmJsMain/resources/.well-known/` — commit them there and each deploy
publishes them.

> **Do §6c first.** There is no website yet, so there is nowhere to put these. Neither
> file does anything until `vinto.kupalinka.app` actually resolves — which is now one
> workflow run away rather than a trip to a computer.

### For Android — `assetlinks.json`

You need one number from the signing key you made in §8. In a terminal:

```sh
keytool -list -v -keystore ~/keys/vinto-upload.jks -alias vinto
```

Look for the line starting **`SHA256:`** and copy the long string of hex pairs. Then create
`.well-known/assetlinks.json` containing:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "game.vinto.app",
    "sha256_cert_fingerprints": ["PASTE THE SHA256 LINE HERE"]
  }
}]
```

> If you publish through Google Play, Play re-signs the app, so the fingerprint you need is
> the one **Play** shows under *Release → Setup → App signing*, not the one from your own
> keystore. Using the wrong one is the single most common reason these links do not work.

### For iPhone and iPad — `apple-app-site-association`

Create `.well-known/apple-app-site-association` — **no file extension** — containing:

```json
{
  "applinks": {
    "details": [{
      "appIDs": ["TEAMID.game.vinto.app"],
      "components": [{ "/": "/r/*" }]
    }]
  }
}
```

`TEAMID` is your Apple Developer Team ID: developer.apple.com → Membership. The file must be
served as `application/json`; Cloudflare Pages does that for `.well-known` files already.

### Checking it worked

Both take a few minutes to be picked up, and Android caches its answer, so reinstall the app
after publishing. Then send yourself a link and tap it: the app should open at the table
rather than the browser opening the website.

**Until these files exist, nothing breaks** — the links simply open the website instead of the
app, and the room code is still printed in the invitation for typing in. There is also a
`vinto://` link that works without any of this, which is what the app falls back on.

---

## 7b. Page-load statistics for the website

Separate from §7, and it answers a different question. The stats page counts what happens
*inside* the game — rounds played, how far people get. This counts what happens *before*:
how many people loaded the page at all, where they came from, and how fast it loaded for
them. Somebody whose browser is too old to run the game never reaches the game's own counter,
and they are exactly the people worth knowing about.

**It is a switch in the Cloudflare dashboard, not something in the code.**

**Do not switch it on for the `vinto` Pages project.** That project is the leftover described
in §6c — it serves an older copy at `vinto-6dr.pages.dev` that nothing links to, so counting
there would count nobody and still look like it had worked. The website is a **Worker** now
(`vinto-web`), for the reason in `docs/kotlin/README.md` §6c: a Pages custom domain can only
be attached by hand in the dashboard, and this project's whole deploy story is a button in the
GitHub mobile app.

So add the **site**, by hostname, rather than opening a project's settings:

1. Cloudflare dashboard → **Analytics & Logs** → **Web Analytics**.
2. **Add a site**, and give it `vinto.kupalinka.app` — the hostname
   `composeApp/cloudflare/wrangler.jsonc` claims in its `routes`.
3. Choose the automatic option if it is offered for this hostname. It is on a zone Cloudflare
   proxies, so Cloudflare can inject the counting script itself and nothing needs rebuilding.

If the automatic option is not offered, Cloudflare gives you a small `<script>` snippet with a
site token in it instead. That one **is** a code change: it belongs in
`composeApp/src/wasmJsMain/resources/index.html`, and the token is not a secret — it is public
by design and identifies the site rather than the account.

It is free, it uses **no cookies**, and it does not follow anybody between sites — which is
why there is no consent banner to add. Results appear under **Analytics & Logs** →
**Web Analytics** in the same dashboard, usually within a few minutes of the first visitor.

---

## 7a. Crash reporting

When something goes wrong for a player, we want to know. Sentry is where those reports go.

Give the room service its key:

```sh
cd worker/cloudflare
npx wrangler secret put SENTRY_DSN
```

Paste the DSN when it asks. It looks like
`https://<a long string>@<something>.ingest.us.sentry.io/<a number>`, and you get it from
Sentry → **Settings** → **Client Keys (DSN)**.

**With no key set, crash reporting is simply off** and everything else works normally. That is
deliberate: nobody should need a Sentry account to work on the game.

### The apps report too, and they need the same key put somewhere else

The room service reads its key from the command above. The phone, tablet and browser apps
cannot — they run on other people's devices, where a Cloudflare secret does not exist — so
their copy is compiled **into** the app.

It lives in `composeApp/src/commonMain/kotlin/game/vinto/app/App.kt`, in the line that reads:

```kotlin
private const val SENTRY_DSN = ""
```

Put the same DSN between those quotes before building a release, and leave it empty otherwise.
Empty means the apps report nothing at all, which is what a development build should do.

> **Is it safe to have the key inside an app anybody can unzip?** Yes, with one caveat, and
> it is the same one as §7a: the key can only *send* reports, never read them. The worst
> somebody can do with a stolen one is send junk and use up the monthly allowance. Do not
> post it publicly; if it gets out, make a new one in Sentry and change the line above.

> **Is the DSN a secret?** Not in the usual sense — it can only *send* reports, never read
> them, and it has to be inside the phone apps for them to report at all. What somebody could
> do with a stolen one is send us junk reports and use up our monthly allowance. So: not a
> disaster, but not something to post publicly either. If it does get out, make a new one in
> Sentry and set it again with the command above; the old one can be deleted in the same
> screen.
>
> **Crash reports never contain a room code, a nickname or an address.** Those are stripped
> before anything is sent, and there is an automatic check that fails the build if the
> stripping stops working.

To check it works, look in Sentry for an event called `VintoSetupCheck` — one was sent while
this was being set up.

## 8. The Android signing key

> **The single most important warning in this document.** Android identifies an app by the key
> it was signed with. If you lose this file or its passwords, you can **never** update the app
> again — you would have to publish a new one, and everybody who installed the old one would
> have to find and install the new one by hand. Back it up somewhere safe that is not only
> your laptop.

Create it once:

```sh
keytool -genkeypair -v -keystore ~/keys/vinto-upload.jks -alias vinto \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password and some details about you. Keep the password somewhere safe — a
password manager, not a note on your desk.

Then create a file called `keystore.properties` in the main Vinto folder, containing four
lines:

```properties
storeFile=/Users/you/keys/vinto-upload.jks
storePassword=the password you just chose
keyAlias=vinto
keyPassword=the password you just chose
```

Replace `/Users/you/...` with wherever the file actually is.

Build the app to upload:

```sh
./gradlew :composeApp:assembleRelease
```

The file appears at `composeApp/build/outputs/apk/release/`.

> If `keystore.properties` does not exist, the build still works, but produces an app that
> **cannot be published** — it is for testing only. That is deliberate: a build that failed
> because a secret was missing would be a build nobody tested until the day it mattered.

---

## 9. The order to do it in on release day

1. §4 — everything passes on your own computer
2. §6 steps 1–3 — publish the room service, still shut, and check the rules survived
3. §6c — publish the website, and give it its address. Then submit the phone apps
4. §6 step 4 — open online play, the **same day** the apps are available
5. Test it for real: two devices, make a room, join with the code, add two bots, play a round.
   Close one app in the middle and watch a robot take over that seat, then reopen it and watch
   the person get their seat back.

---

## 10. If something goes wrong

| What you see | What it means | What to do |
| --- | --- | --- |
| `/health` shows the old answer | Cloudflare has not finished updating everywhere | Wait a minute, refresh. Do not deploy again |
| `/health` says `roomOpen: false` after §6 step 4 | Either it has not updated yet, or the file was not saved before deploying | Refresh a few times; if still wrong, check the file and deploy again |
| "the room service is closed" | Online play is switched off | That is `ROOM_OPEN` — §6 step 4 |
| A gate script says FAIL | Something is genuinely wrong | **Do not publish.** Send the whole output to a developer |
| `wrangler: command not found` | Node.js is not installed, or not on this computer | §2 |
| Anything else unexpected | — | Stop. Nothing here is urgent enough to guess at |

**To undo a publish of the room service**, publish the previous version again from the
Cloudflare dashboard (Workers → vinto-room → Deployments → the older one → Rollback). Online
play can also simply be switched off with §6 step 4 in reverse, which is faster and usually
what you want.

---

## For developers

The technical version of all of this, with the reasoning behind each decision, is
`docs/kotlin/README.md` — §6i is the same runbook written for somebody who will read the code.
The analytics design is `openspec/changes/archive/add-live-analytics/`.
