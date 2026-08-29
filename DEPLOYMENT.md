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

---

## 7. The stats page

Anonymous counts only: how many games were played, how many people got as far as pressing
"Play online", how long a round takes, what a room costs us to run. **No names, no room
codes, no way to identify anybody** — the code is built so those cannot be recorded even by
accident, and there is an automatic check that fails the build if anyone tries.

To let the stats page read the counts, you need a token:

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
3. Publish the web version and submit the phone apps
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
The analytics design is `openspec/changes/add-live-analytics/`.
