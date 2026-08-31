---
name: vydanne
description: Prepare an App Store Connect / Google Play submission — write AND push the localized store listing. Crafts the ASO copy (app-store name, subtitle, the 100-char keyword field, description, promo text), then fills the listing + screenshots + previews, age rating, review contact, accessibility & App Privacy labels, IAP fields, and export-compliance docs; verifies with a preflight gate and diffs local-vs-live. Native Node (ES256 JWT + fetch, no fastlane/Ruby). One vydanne.config.mjs per app. Use when writing or shipping any App Store / Play listing. Uploads builds to testers (TestFlight internal / Play closed track) and points the prepared version at them — but never submits for review and never ships to the public.
license: MIT
compatibility: Requires Node >=20.9 and `npx vydanne`. Credentials resolve from the environment or ~/.appstoreconnect — never from the config.
metadata:
  author: Lonli-Lokli
  version: "0.9.0"
  source: https://github.com/Lonli-Lokli/vydanne
---

# vydanne

The App Store Connect / Google Play half of a two-part release pipeline. Native Node — no
fastlane/Ruby/Python. Two jobs: **(A) write the listing well (ASO)**, **(B) push it + the declarations**.

**Companion tool — [zdymak](https://www.npmjs.com/package/zdymak)** makes the *media* (screenshots, App
Preview videos, Play feature graphic); vydanne pushes that media plus all the *text and paperwork*. If the
user needs screenshots or a preview video produced, that's zdymak's job, not vydanne's — vydanne only
uploads files that already exist. **The two do NOT line up on disk** (they did before zdymak 0.15):
zdymak writes one root shaped `store-assets/<locale>/<target>/NN-name.png`, while vydanne reads several
hardcoded roots, with Apple's locale codes and a device-prefix filename convention. **`vydanne bridge`
is the glue** — run it after `zdymak screenshots`/`zdymak build` and before `fill`, every time.
Skipping it is silent and dangerous: zdymak reports success, vydanne re-uploads whatever was bridged
LAST time, and the store quietly keeps stale art.

**Never submits for review; never ships to the public.** It *does* upload builds — `prerelease` sends
the `.ipa` to TestFlight (internal groups) or the `.aab` to a Play closed track, and points the version
being prepared at it. What stays human: pressing Submit, Play `production`, and external TestFlight
(which needs Beta App Review). Those are refusals rather than flags, so there is no argument
combination that reaches the public. Don't try to work around that.

## Setup

`npm i -D vydanne`, then **`npx vydanne <cmd>` from the app's repo root** — the config and every relative
path in it resolve against the working directory. Running from a subfolder silently reads the wrong paths.

One `vydanne.config.mjs` per app (schema: `vydanne.config.example.mjs`; type
`import('vydanne').VydanneConfig`). Node ≥20.9. Cross-platform. On Windows PowerShell the `VAR=1 cmd`
form does **not** exist; set `$env:VAR = "1"` first.

**Auth resolves automatically — NEVER put credentials in the config.** That file is committed; vydanne
refuses a keyId/issuerId found there and warns. The signing key stays at
`~/.appstoreconnect/private_keys/AuthKey_<keyId>.p8`. The ids resolve highest-priority-first from: the
environment (`ASC_KEY_ID` / `ASC_ISSUER_ID` / `PLAY_JSON_KEY_FILE`) → the **`.env` cascade** → the **user
config file**.

The cascade is the standard one (dotenv-parsed, later file wins): `.env` → `.env.<mode>` → `.env.local` →
`.env.<mode>.local`, `mode` from `VYDANNE_ENV`/`NODE_ENV`. So `.env` and `.env.<mode>` stay COMMITTABLE
for shared non-secret defaults and only `*.local` holds secrets — never tell a user to gitignore `.env`
itself. Files are parsed, never merged into `process.env`, so a real env var always wins.

The user config file is the answer for a portfolio: write `{"keyId","issuerId","playJsonKeyFile"}` once
and every app picks it up with no per-repo setup. Looked up first-hit-wins —
`$VYDANNE_CONFIG_HOME/config.json` → `%APPDATA%\vydanne\config.json` (Windows) or
`$XDG_CONFIG_HOME/vydanne/config.json` (default `~/.config/vydanne/`) → `~/.appstoreconnect/config.json`
(beside the keys, where Apple's tooling and fastlane already keep the `.p8`). Several accounts → named
`profiles` + `VYDANNE_PROFILE`, or pin one per app with `asc: {profile}` (a label, not a secret).
**Run `vydanne auth` before debugging any 401** — it prints what resolved, from which source, masked,
which user file was used, and whether the `.p8` is on disk.

**Config fields:** `bundleId` · `primaryLocale` (the fallback — must be populated) · `asc` (optional
`{profile}` — selection only, never secrets) · `platforms` (iOS and macOS are SEPARATE) · `uiLocales`
(auto-mapped to ASC codes) · `localeMap` · `metadataDir` · `screenshots` · `rating` · `ageRating` ·
`privacy` · `iaps` · `previews` · `export` · `accessibility` · `ios` · `google` (Google Play) ·
`bridge` · `push` · `reviewContact` · `allowCrossStoreTerms`.

**Paths are defaults, not laws.** `metadataDir` (default `fastlane/metadata`), `screenshots`
(`{IOS, MAC_OS}`, default `fastlane/screenshots` + `-macos`) and `google.images` (Play image type →
local path) all follow fastlane's supply convention out of the box and are all overridable. Point them
at the repo you have rather than reshaping the repo around the tool.

**Four blocks the tool will NOT fill in for you.** Each publishes a CLAIM rather than a fact, so silence
is refused instead of defaulted — that is deliberate, and re-adding a default is the bug, not the fix:

- **`accessibility`** — Accessibility Nutrition Labels. Declare every feature true/false from what was
  actually verified. No block → the command errors.
- **`export.algorithms` + `export.statement`** — the cryptography inventory and statement in the US
  export-compliance PDF (`compliance`). Required when `export.encryption` is `"standard"`. Never invent
  these; ask what the app actually ships. `export.filed` stays **false** until the report has really
  been emailed to BIS and the NSA, and while it is false the PDF does not claim it was submitted.
- **`ageRating`** — needed for any `rating` above `"4+"`. Describe the CONTENT
  (`{ violenceCartoonOrFantasy: "INFREQUENT_OR_MILD" }`, merged over an all-NONE base); Apple computes
  the band. `"4+"` alone needs nothing else.
- **`reviewContact` / demo account** — whether App Review needs a login is inferred from
  `<metadataDir>/review_information/demo_user.txt` + `demo_password.txt` (both gitignored). An app with
  a sign-in wall and no demo account is a guaranteed rejection.

For a non-technical user asking how to set this up from scratch, walk them through
**`GETTING_STARTED.md`** (accounts → API key → config → folders → push) rather than improvising.

## File layout — read this before writing any file

vydanne reads plain `.txt` files from fixed locations. **These conventions are not configurable beyond
`metadataDir`; do not invent paths.**

**Apple listing text** — `<metadataDir>/<ASC-locale>/*.txt` (default `metadataDir`: `fastlane/metadata`):

| File | Field | Limit |
|---|---|---|
| `name.txt` | app name (on **AppInfo**, shared across platforms) | 30 |
| `subtitle.txt` | subtitle (on AppInfo) | 30 |
| `description.txt` | description (on the **version**) | 4000 |
| `keywords.txt` | keyword field (version) | 100 |
| `promotional_text.txt` | promo text (version) | 170 |
| `release_notes.txt` | what's new (version) | — |
| `marketing_url.txt`, `support_url.txt` | optional URLs (version) | — |

Folder names must be **Apple's exact ASC codes** (`de-DE`, `ar-SA`, `zh-Hans`, `en-GB`…). A folder whose
name isn't in the valid set is skipped; run `vydanne locales` to get the mapping from the config's
`uiLocales`. A language with no App Store equivalent (e.g. Belarusian `be`) must **not** get a folder — it
falls back to `primaryLocale`.

**App Review contact** — `<metadataDir>/review_information/{first_name,last_name,phone_number,email_address,notes}.txt`.
This is PII: keep it gitignored.

**Apple screenshots** — `fastlane/screenshots/<ASC-locale>/<prefix>_<anything>.png`, and
`fastlane/screenshots-macos/<ASC-locale>/…` for Mac. **These two base paths are hardcoded.** The token
before the **first underscore** selects the device slot; files upload in sorted order, so number them:

| Prefix | Slot |
|---|---|
| `iphone69_` | `APP_IPHONE_67` (6.9″) |
| `iphone65_` | `APP_IPHONE_65` |
| `ipad13_` | `APP_IPAD_PRO_3GEN_129` |
| `watch_` | `APP_WATCH_ULTRA` |
| `macos_` | `APP_DESKTOP` (in `screenshots-macos/`) |

A file with an unknown prefix is not uploaded, and `fill` names it. A set that **already has
screenshots is skipped** (and says so), never duplicated — `VYDANNE_REPLACE=1` deletes the store's set
and uploads yours, the same flag `previews` uses. PNGs must be **RGB with no alpha**.

**Play listing text** — `<google.metadataDir>/<PLAY-locale>/{title,short_description,full_description}.txt`
(30 / 80 / 4000). Play uses its **own** codes (`de-DE`, `zh-CN`, `iw-IL`, `ar`, `be`) — *not* Apple's
`zh-Hans`/`he`/`ar-SA`.

**Play images** — DEFAULT source paths (override any of them with `google.images`), each pushed only
when the file exists, so a missing local set never deletes the live one:
`brand/icons/play/icon-512.png` (512², from znachok) · `marketing/out/play-feature-graphic.png`
(1024×500) · `marketing/out/play-phone-plain/` · `marketing/out/play-tablet7-plain/` (7″) ·
`marketing/out/play-tablet-plain/` (10″) · `marketing/out/play-wear/`. `wearScreenshots`, `tvScreenshots`
and `tvBanner` are also understood, so a Wear OS or Android TV release is a config line, not a code
change. An image dir that exists but is EMPTY is reported (by `fill` and `diff`) as a live set only
Play Console can remove.

Play holds graphics **per language**. The default uploads one untranslated set at
`google.defaultLocale`; `google.imageLocales` (a list, or `"*"` for every local listing folder) opts
into localized art, and a `<source>/<lang>/` subdirectory overrides the shared source for that language.
`diff --store google` compares exactly the locales `fill` would write, so the two never disagree.

**`bridge` populates both screenshot layouts from zdymak's output.** It maps
`store-assets/<locale>/<dir>/NN-name.png` onto the Apple and Play paths above: locale codes via the
same `toAsc` table `fill` uses (`de` → `de-DE`; a code with no App Store language is skipped and falls
back to the primary listing), the device-slot prefix prepended (`iphone69_01-fresh.png`), and the Play
sets into their configured destinations. A locale with screenshots but NO listing text is held back
(uploading pictures alone would create the localization and break its fallback to the primary
language), and every bridged image is checked for an alpha channel — which Apple rejects — on the
SOURCE, before anything is copied, so a refusal leaves the destinations untouched and `--dry-run`
catches it too. Local files only; `--dry-run` previews what would be written *and removed*. See the
directory-vs-target note under Commands for why the source folder name is the thing that matters.

## A. Writing the listing (the ASO craft — the durable value)

**Write the English master first, then localize.**

### The fields, what they're FOR, and the hard limits

| Field | Limit | Indexed for search? | Job |
|---|---|---|---|
| `name` | 30 | **Yes (highest weight)** | Brand + the single strongest keyword. Must be globally UNIQUE. |
| `subtitle` | 30 | **Yes** | A second keyword-bearing benefit line — NOT a repeat of the name. |
| `keywords` (App Store only) | 100 | **Yes** | The biggest lever. Comma-separated, **no spaces**. |
| `promotional_text` | 170 | No | Rotating hook (sale/seasonal); editable anytime with no review. |
| `description` | 4000 | **App Store: NO** / **Play: YES** | Conversion copy for the human. On Play it's also indexed → bake keywords in naturally. |
| Play `title`/`short`/`full` | 30/80/4000 | short + full **Yes** | Play has NO keyword field → keywords go in title + short + full. |

### The keyword field — the rules people get wrong (App Store)

- **No spaces** after commas (`a,b,c` not `a, b, c`) — every char counts toward 100. **Fill all 100.**
- **Don't repeat** any word already in `name` or `subtitle` — Apple already indexes those; repeating wastes the field.
- **Singular OR plural, never both** — Apple matches both stems; pick one (usually singular).
- **Omit** "app", "game", "free", and the app's own name — Apple indexes those automatically.
- Apple **auto-combines** keywords into phrases (kw+kw), so prefer **single words** to maximize combinations — no multi-word phrases unless the phrase is the exact search term.
- **No competitor trademarks** (rejection risk, especially for games).
- Prioritize by **relevance × search volume × achievable rank**: generic head terms are hard to rank; mid-tail terms convert AND rank. Order best-first (leading keywords weigh more).

### Positioning — ground it in the app's real audience, not generic hype

Before writing, establish what actually motivates *this* app's users (ask the user, or read whatever
audience research/positioning docs the repo has). Then:

- **Lead with the strongest shared motivation**, stated concretely — not a feature list.
- **Name the genuine differentiator plainly.** If there's one thing competitors can't claim, say it in
  the subtitle, not buried in paragraph four.
- **Match the audience's temperature.** A calm, premium audience reacts badly to hype punctuation and
  competitive framing; a competitive audience finds understatement flat. Mirror the in-app voice.
- **Be honest in the close** — "no ads", "no account", "buy once" only if true. Claims here are checkable.
- Don't lead with mechanics the audience doesn't care about (leaderboards, streaks) just because they exist.

### Description shape (App Store & Play)

Hook (1–2 lines: the promise) → **WHY \<APP\>** (3–5 benefit bullets, each benefit-first) → what it is /
who it's for → honest close. Keep it scannable; lead each bullet with the payoff, not the feature.

### Localizing the listing (transcreation, not translation)

- **App name**: the brand is **never** translated; the DESCRIPTOR may be localized per store locale.
- **Keywords**: use the target market's **actual search terms**, not a dictionary translation. Research
  per market — the literal translation of a category name is often not what people type.
- **Subtitle / promo / description**: **transcreate** — adapt benefit + tone naturally; never word-for-word.
- **Fan out one agent per locale** (a Sonnet-tier translator/copywriter is the right size). Give each:
  the master English copy, the target locale, its `<metadataDir>/<ASC-locale>/` directory, the brand name
  (untranslated), the character limits, and the positioning above. Validate limits after the merge —
  translations routinely blow the 30-char fields.

## B. Commands (push it)

`fill` (metadata + screenshots, native PATCH/chunked upload — works even at READY_FOR_REVIEW) ·
`previews` (App Preview videos) · `appinfo` (category + content rights) · `age-rating` · `review-contact` · `accessibility` (draft; publish once
live) · `privacy` (prints answers for the UI — the API can't reach Apple's iris host) · `iap` (validate +
RGB flatten) · `compliance` (US self-classification PDF) · `bridge` (zdymak's output → the folders
`fill` reads) · `diff` (what differs vs live, text AND media by checksum) · `preflight`
(completeness gate + cross-store lint + stale-screenshot check) · `inspect` · `auth` (what credentials
resolved, and from where) · `locales` · `version`.

**`bridge` maps by DIRECTORY, not by target.** zdymak writes each shot to `<dir || target>`, so a
`dir:` override makes the folder name differ from the target name — and Play's 7" slot can *only* exist
that way (`{ target: 'play-tablet', dir: 'play-tablet7-plain' }`; there is no `play-tablet7` target).
Defaults prefer the `-plain` convention Google asks for on listings and fall back to the bare target
name; `bridge.apple` / `bridge.play` override per slot. It **owns its destinations per store**: any
Apple output means both Apple roots are rebuilt (so art dropped upstream stops being uploaded), while
an app that bridges only Play never has its hand-managed Apple screenshots touched. It plans before it
writes — a failure (alpha channel, empty source) leaves every destination untouched, and `--dry-run`
reports exactly what a real run would write *and remove*.

**Cross-store lint.** `preflight` and `fill` refuse listing text that names the other mobile platform
— App Review 2.3.10 for Apple, the Store Listing and Promotion policy for Google — scanning every
locale and field of the LOCAL metadata before upload, including localized spellings of Android/Apple.
A store's own platform is never flagged, nor the bare word "Play". Ambiguous words warn instead of
blocking; `allowCrossStoreTerms: [...]` in the config silences a specific one, and
`VYDANNE_ALLOW_CROSS_STORE=1` overrides a single run. This is a rejection that surfaces days later in
one locale out of twenty, so it is checked where it is free to fix.

**The pipeline has ONE working order** — `prepare` → `fill` → `previews` → `age-rating` →
`review-contact` → `accessibility` → `preflight` → **a human submits**. `prepare` must be first (until
the draft version exists, nothing has anywhere to write) and `preflight` last (green must be measured
after the writes it blesses). `push` runs exactly that sequence — each step the same `run` as the
standalone command, on the same client, stopping at the first failure — so prefer `vydanne push` over
re-deriving the order; the near-miss that motivated it was `fill` pointed at a live-only app out of
order. The two flows differ only at step one and `push` absorbs it: a FIRST release already has a
PREPARE_FOR_SUBMISSION version (creating the app record made it), so `prepare` is a find-and-reuse
no-op; an UPDATE has only the read-only live version until `prepare` creates the next one. On a live
app a DRY `push` stops at `fill` — the draft the later steps target doesn't exist until `prepare` is
applied — and says so up front; `prepare --apply` (a draft, not a submission) then a dry `push`
previews the whole plan. `prepare` creates a version for EVERY declared platform, so an iOS+macOS app
gets both drafts. `prerelease` is deliberately not a step (macOS-only, shells out to altool); run it
whenever the build is ready — `prepare` attaches the newest build either way.

**`push --skip <step>[,<step>]`** (or `push: { skip: [...] }`) drops a step that doesn't apply — the
usual case being an app with no audited `accessibility` block, which the command correctly refuses to
guess. `prepare` and `preflight` cannot be skipped. Every skip is reported on its own line AND again
after the final green, because the whole value of that last line is that green means green: never let
a skipped run read like a complete one.

`prepare` creates the version to write INTO, and is REQUIRED as the first push step on any app that
already has a version on sale. Every other Apple command finds its target through
`client.editVersion()`, which returns the first version Apple has not marked dead; when the only
version is live it has no editable record to return and falls back to the live one — so `fill` aims
its `description`/`whatsNew` PATCHes at the listing customers are reading, and `prerelease` declines
to attach the build it just uploaded. `prepare` POSTs `/v1/appStoreVersions` with `releaseType:
MANUAL` (so approval still doesn't release), sets `copyright` from `<metadataDir>/copyright.txt`
(nothing else in vydanne writes that version-level field), and attaches the newest build. The version
number is read off that build's `preReleaseVersion` — the archive's own
`CFBundleShortVersionString`, so it cannot drift from the binary — or `VYDANNE_VERSION=<x>` when the
version is being prepared before its build exists. It is find-or-create, so re-running is safe; a
version Apple has locked (`IN_REVIEW`, `READY_FOR_SALE`, …) is refused rather than edited, because
withdrawing a submission is the operator's call. It pre-checks the number against the version on sale,
turning Apple's 409 into a sentence. **Creating a draft is not submitting** — Add to Review and Submit
stay manual.

`prerelease` uploads the BUILD. On Apple it validates and uploads the `.ipa` to **TestFlight** via
`xcrun altool` — the one command that shells out, because the ASC REST API has never carried a binary,
which also makes it macOS-only. `.ipa` comes from `ios.ipa` / `VYDANNE_IPA` (a directory takes its
newest), the build number from the archive's own `CFBundleVersion`, and `ios.testFlightGroup` may add
it to an **internal** group. External groups are REFUSED — they need Beta App Review, a submission by
another name — and App Store review is never submitted, mirroring the Play side refusing `production`.
It then points the version being prepared AT that build, so the fix-and-re-upload loop is one command:
re-running re-points (the relationship holds one build), while a version that is `IN_REVIEW` or
`READY_FOR_SALE` is left alone, since re-pointing it would mean withdrawing a submission.

With `--store google` it uploads an `.aab` to a **closed testing track** with release notes, inside one
edit transaction. `production` is REFUSED — not flag-gated — so no argument combination ships to the
public; promoting the tested build stays a human's job, mirroring the Apple side never submitting. Track
comes from `google.track` / `VYDANNE_TRACK`, default `internal`; the bundle from `google.aab` /
`VYDANNE_AAB` (a directory takes its newest `.aab`). **For a PAID app use `internal`** — it's the only
track where testers install without buying. Notes follow supply's layout, per locale, first match wins:
`<google.metadataDir>/<play-locale>/changelogs/<versionCode>.txt` → `next.txt` → `default.txt`, capped
at Play's 500 chars. **Write the upcoming release's notes as `next.txt`** when the versionCode is not
knowable in advance (derived from the commit count, say): after a real publish vydanne renames it to
`<versionCode>.txt` so the next release can't inherit it, and a `default.txt` fallback is WARNED rather
than silent. The versionCode is read out of the `.aab` locally and the changelog resolution reported
BEFORE the upload; re-uploading a used code fails loudly instead of silently replacing. DRY by default;
`--apply` publishes.

`--store google` routes `inspect` · `diff` · `preflight` · `fill` · `prerelease` to the Play Developer **Edits** API
(OAuth2 service account; **scoped to the config's `packageName`** — a shared key can't touch another app).
The AAB binary and the (YouTube-URL) promo video stay outside vydanne.

## `--apply` — writes are opt-in

**Every store-mutating command is a DRY RUN without `--apply`**: `prepare` · `push` · `fill` ·
`previews` · `appinfo` · `age-rating` · `review-contact` · `accessibility` · `prerelease` (marked `✎` in
the usage text, printed by `vydanne` with no arguments). They read the store, print each write they would make, and send nothing. Read-only
commands ignore the flag.

Never reach for `--apply` to "check whether it works" — the dry run IS the check, and it walks the whole
plan rather than stopping at the first locale. Its closing count is what you compare against `diff`.

Enforcement differs per store, on purpose: **Play** builds the Edit and validates it against Google for
real, then discards it (nothing is live until commit). **Apple** has no transaction, so the gate is at the
HTTP layer in `src/client.mjs` — no `POST`/`PATCH`/`PUT`/`DELETE` leaves the process, and each is recorded
in `client.planned`. `prerelease` needs its own guard because the `altool` binary upload does not go
through that client. **A new command that touches the store must be marked `writes: true` in
`src/registry.mjs`** — that flag is the whole opt-in, not a label.

**Env toggles:** `VYDANNE_CONFIG` · `VYDANNE_SKIP_METADATA` / `VYDANNE_SKIP_SCREENSHOTS` (fill) ·
`VYDANNE_REPLACE` (fill screenshots + previews: replace populated slots) · `VYDANNE_VERSION` (prepare) ·
`VYDANNE_IPA` / `VYDANNE_AAB` / `VYDANNE_TRACK` / `VYDANNE_RELEASE_NAME` (prerelease) ·
`VYDANNE_FLATTEN=<png>` (iap) · `VYDANNE_A11Y_PUBLISH` (accessibility) · `VYDANNE_ALLOW_CROSS_STORE`
(one run past the cross-store lint) · `VYDANNE_COMMIT=1` (legacy alias for `--apply`; prefer the flag).

## Flow

config → **write the English master listing (ASO, research-grounded)** → fan out one copywriter agent
per locale → media from zdymak → **`bridge`** (zdymak's output into vydanne's folders — every time) →
build via `prerelease --apply` whenever it is ready → `push` (read the dry run, then re-run with
`--apply` — it is prepare → fill → previews → age-rating → review-contact → accessibility → preflight,
stopping at the first failure) → `diff` to confirm → **a human submits**.

## Gotchas it encodes (don't re-derive)

ASC locale folder codes must be exact (`de`→`de-DE`; a bad one aborts the upload) · `name`/`subtitle` on
AppInfo vs `description`/`keywords`/`promo` on the version · macOS is a separate platform · list endpoints
return sparse/empty text (read each localization by id) · primary locale must be populated · App Privacy is
on the `iris` host (JWT 401s — UI only) · accessibility can't publish until live (409) · edit-version /
app-info / deliver all break at READY_FOR_REVIEW (fetch by id; native PATCH still works) ·
screenshots/IAP images must be RGB no-alpha · IAP has two image slots (tall review screenshot vs 1024²
promo) · char limits (name/subtitle 30, keywords 100, promo 170; IAP name 30 / desc 45; Play title 30 /
short 80 / full 4000) · **Play uses its OWN locale codes** · Play images push only when the local file
exists, so a missing set never wipes the live one · Apple requires `name` when *creating* an app-info
localization (409 otherwise).

## The blockers that only appear on the Add for Review screen

Apple checks these last, so a release can be green everywhere — metadata filled, screenshots up,
build attached, `preflight` clean — and still stop dead with a list nobody can act on from a
terminal. Four of the five are now vydanne's; the fifth is not reachable by any API.

| blocker | who sets it |
|---|---|
| Privacy Policy URL | `fill` — `privacy_url.txt` per locale (an `appInfoLocalizations` field, so **every** locale needs it) |
| Copyright | `prepare` — `<metadataDir>/copyright.txt`, on create **and** on reuse |
| Primary category | `appinfo` — `categories` |
| Content Rights | `appinfo` — `contentRights` |
| **Price tier** | **App Store Connect UI.** Pricing is a separate agreement-bound surface; vydanne does not touch money. |

`preflight` will NOT catch these. It verifies submission-completeness of the things it writes; a
category it never sets is not a gap it knows to look for. Run `appinfo` once per app and the list
shortens to the price.
