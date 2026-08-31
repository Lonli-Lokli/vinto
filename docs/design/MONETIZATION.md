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
   in `docs/kotlin/README.md` is what answers "what does a room cost"). Cosmetics-only keeps
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
| **Supporter pack** | One purchase that unlocks everything above plus anything future | Also the honest "tip jar" for people who just want to pay |

The "paid personal profiles" idea reframed: the *data* half of a profile (stats, streaks) is
already built, free, and local (`Stats` in the vault) — selling it would mean taking something
away. The *identity* half — avatar, card back, felt, a title on your seat plate — is the
sellable part, and it needs no account: it is a cosmetic id, not a person.

## How it fits the architecture

- **A `Store` seam, same shape as `Vault` and `shareText`**: an `expect` in `composeApp` with
  per-platform actuals — Play Billing on Android, StoreKit on iOS. The web and desktop actuals
  answer "not available here" and the shop UI says so plainly (the §6p rule: a trouble picks
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
