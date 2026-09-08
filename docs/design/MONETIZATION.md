# Monetization — cosmetics only, no ads, ever

The decision this document starts from, stated by the product owner: **no advertising of any
kind, at any point, in any build**. Everything below is arranged around that, and around three
properties this codebase already has and must not lose:

1. **There are no accounts.** A seat is a token, a player is a nickname, and the privacy
   invariant is a test (`AnalyticsPrivacyTest`): nothing identifying is ever counted or
   reported. Any monetization that needs a server-side profile needs accounts first, which is
   a much bigger decision than a price tag — it is deliberately out of scope here.
2. **Gameplay is never for sale.** The engine is one pure reducer and the bots go through the
   same `ActionValidator` as everyone else. A purchase must never change what a player can
   *do* or *know* at the table — no pay-to-win, no extra peeks, no undo, no hints.
3. **Online play stays free** until the room-cost numbers exist (analytics 2.3 / §6i step 3
   in `docs/kotlin/ROOM.md` is what answers "what does a room cost"). Cosmetics-only keeps
   that promise cheap to keep.

## What is for sale

Cosmetics, as **one-time purchases**. No subscriptions, no consumables, no loot boxes, no
timers, no currencies. A card game bought once should feel bought once.

| Item | What it is | Notes |
| --- | --- | --- |
| **Card face decks** | A full re-skin of the 14 faces (see `CARD-IMAGERY.md` — the meaning-based deck is the first premium candidate) | The default deck stays free and good. A paid deck is style, never information: same rank indices, same legibility |
| **Card backs** | The face-down side, which is what everyone stares at most of the game | Cheapest to produce, most visible in play |
| **Table felts** | The felt colour/texture and the lamp | One drawable + theme tokens |
| **Avatars / portraits** | Replaces `avatar_you.png`; a set of alternates | The one "personal profile" item that needs no profile |
| **Supporter pack** | One purchase that unlocks everything above plus anything future | Also the honest "tip jar" for people who just want to pay. **Shipped ahead of the rest**, as `vinto.support.five` — see "The products, by name" below. It unlocks nothing today because there is nothing yet to unlock |

The "paid personal profiles" idea reframed: the *data* half of a profile (stats, streaks) is
already built, free, and local (`Stats` in the vault) — selling it would mean taking something
away. The *identity* half — avatar, card back, felt, a title on your seat plate — is the
sellable part, and it needs no account: it is a cosmetic id, not a person.

## The products, by name

One product exists today. Both consoles must use **the same id**, because `SUPPORT_PRODUCT` in
`composeApp/src/commonMain/.../Support.kt` is a single constant asked of both stores — a
mismatch is a store that answers "no such product" and a button that silently reports
unavailable, which looks exactly like a network problem.

| | |
| --- | --- |
| **Product id** | `vinto.support.five` |
| **Type** | Consumable (Play: "in-app product", one-time; Apple: **Consumable**) |
| **Price** | The store's five-unit tier, which is **4.99** rather than a flat 5.00 in most currencies — that is how both stores' price points are cut, and neither lets a seller name an arbitrary figure. Elsewhere it is whatever local tier each store maps that to. Never formatted by this app: `formattedPrice` on Play, `NSNumberFormatter` against `priceLocale` on Apple |
| **Name shown** | "Support the game" |
| **What it grants** | **Nothing.** No deck, no felt, no advantage, and nothing removed |
| **Repeatable** | Yes, and only because it grants nothing — Play consumes it on receipt, Apple finishes the transaction |
| **Where it appears** | Settings, first screen. The header's coffee cup is a *different* thing: an outside link, web and desktop only, never a store build |

**Consumable and not one-time on purpose.** A non-consumable is owned forever, so somebody who
wanted to say thanks twice could not — and an entitlement that unlocks nothing is a receipt for
a thing that does not exist. Consuming it immediately is what makes it repeatable, and that is
only honest *because* nothing is unlocked. A repeatable purchase that did grant something would
be a currency, and this document is the reason there is no currency.

### It is a tip, and it must never be called a donation

The wording is a store rule, not a preference. **Apple's Guideline 3.2.2 reserves the word for
registered nonprofits**: an app that collects *donations* must be a charity, or must collect them
outside in-app purchase entirely. A tip jar for the developer of a free app is the ordinary,
approved shape — thousands of apps ship one — and the only thing that turns the second into the
first is the label on it.

So "Support the game", "Say thanks", "a tip jar" in the review note, and **the word "donation"
appears in no store field on either console**. Play is less pointed about it (charitable
donations are merely exempt from Play billing rather than forbidden it), but one vocabulary
across both stores is one thing to remember, and Apple is the one that reviews.

### What has to exist before the button can work

Neither store is set up, and the code reports that honestly rather than pretending
(`Support.Unavailable` → "Not available here yet"). What is outstanding — the first two are
console work, because **neither tool can create a product**: vydanne never calls App Store
Connect's `inAppPurchases` API, and Play's `inappproducts` API is not wired either. `vydanne iap`
(`npm run store:iap`) validates the declaration and prints the fields to type.

1. **Play Console** → Monetise → Products → In-app products → Create.

   | Field | Value |
   | --- | --- |
   | Product ID | `vinto.support.five` — **permanent**, and Play never lets it be reused |
   | Name (≤ 55) | Support the game |
   | Description (≤ 200) | A thank you to the developer. It unlocks nothing — there is nothing locked. |
   | Price | the 4.99 tier, with "set prices in other currencies" left to Play's conversion |
   | Status | **Active**. A product left inactive answers exactly like one that does not exist |

   No screenshot: Play asks for none, and there is no review step for an in-app product.
   It will not appear to a build that is not on a track, so an `alpha` upload comes first.

2. **App Store Connect** → the app → In-App Purchases → Create → **Consumable**.

   | Field | Value |
   | --- | --- |
   | Reference Name (≤ 64) | Support the game — internal, never shown to a buyer |
   | Product ID | `vinto.support.five` — the same string, and also permanent |
   | Display Name (≤ 30) | Support the game |
   | Description (≤ 45) | A thank you. Unlocks nothing. |
   | Price | the 4.99 point, availability all territories |
   | **Review Screenshot** | **required** — `marketing/captures/iap/support-review-iap.png` |
   | Review Notes | the `reviewNote` in `vydanne.config.mjs` |

   Every one of those is the `iaps` entry in `vydanne.config.mjs`, which is where they are kept
   in step; the two length limits are what `vydanne iap` checks.

   **The screenshot is the only artefact here that had to be made rather than typed**, and it is
   made by the app: `npm run capture-iap` renders the real settings screen with the offer the
   console is about to make and flattens it to RGB, because App Store Connect rejects an alpha
   channel. `IapShotTest` carries the whole argument, including why a device screenshot is not
   available until after the thing it is required for has been submitted. There is a second,
   **optional** slot — a 1024×1024 Promotional Image, used only to promote the purchase on the
   App Store page itself; it is not needed to submit.

   The purchase is reviewed **with an app version**, not on its own: attach it to the 1.0
   submission, or it sits at "Ready to Submit" indefinitely.

3. **Agreements** — Apple's Paid Applications agreement and Play's merchant account, both with
   tax and banking details. Nothing sells before these are active, on either store.
4. **A signed build on a track.** Play Billing answers `queryProductDetails` only for a build
   signed with the upload key and published to at least one track; a debug APK always sees
   nothing, which is the correct behaviour and not a bug to chase.

**One id, and nothing may drift from it.** `SupportProductTest` fails if `vydanne.config.mjs`
stops naming `SUPPORT_PRODUCT`, or if this document stops naming it — because the Play record is
typed from here by hand, and a mismatch is invisible: the store answers "no such product", the
app reports `Unavailable`, and that is the same sentence a player sees with no network.

## How it fits the architecture

- **A `Store` seam, same shape as `Vault` and `shareText`**: an `expect` in `composeApp` with
  per-platform actuals — Play Billing on Android, StoreKit on iOS. The web and desktop actuals
  answer "not available here" and the shop UI says so plainly (the RELIABILITY.md §6p rule: a trouble picks
  the sentence). Web payments (Stripe/Paddle) are a later, separate decision — they carry tax
  and receipt obligations the stores handle for you.
- **Entitlements live in the vault**, restored through the platform's own restore-purchases
  API. No server verification, because there is nothing server-side to protect: a tampered
  client showing itself a skin it didn't buy costs nobody anything and confers no advantage.
- **Online, opponents see your cosmetics** — that means a cosmetic id travels on the wire.
  Per the room's own rule, it goes in as a new **allow-listed** field on `PublicSeat` /
  `PlayerView` (never "the record minus a field"), and it is a small enum-like id, not a
  blob and not anything identifying. The room does not verify entitlement either — same
  reasoning as above.
- **Analytics may count purchases as a funnel step** (SHOP_OPENED, PURCHASE_COMPLETED with
  the item id) under the existing privacy gate: an item id is a thing chosen, not a person.
  No prices, no receipts, no store account ids ever leave the device.

## What is deliberately not for sale, and why

| Not this | Because |
| --- | --- |
| Ads, "watch to unlock", offerwalls | The standing decision this file exists to record |
| Anything affecting play (peeks, undos, hints, bot difficulty) | Invariant 2 above; also the fastest way to lose the players who tell their friends |
| Subscriptions | A card game is not a service; churn management would eat the project |
| Loot boxes / random packs | Regulatory exposure (varies by country) and it converts trust into suspicion |
| Server-side profiles / cloud sync as a paid feature | Requires accounts; collides with the privacy invariant. If accounts ever happen it is an architecture change, not a SKU |
| "Remove ads" | There is nothing to remove — and there never will be, which is itself worth a line on the store page |

## Sequencing

1. **9.10 first.** Nothing can be sold before store releases exist (upload key, Play track,
   Apple account — `openspec/changes/ship-and-operate`). Purchases are a store feature.
2. **Ship the first premium deck with the shop**, not after it — an empty shop is worse than
   no shop. The meaning-based deck in `CARD-IMAGERY.md` is the candidate: it demonstrates the
   category and the default deck remains untouched.
3. **Measure before pricing online anything.** If rooms turn out to cost real money at scale,
   the answer is still not ads — it is a supporter pack people already like, or capacity
   features (bigger private rooms) that are honest to charge for because they cost the host.

## The one line for the store page

*"Bought once, yours forever. No ads — not now, not ever. Nothing you can buy changes the
game; it only changes how your table looks."*
