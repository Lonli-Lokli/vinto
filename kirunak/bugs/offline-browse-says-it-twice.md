---
id: vinto/offline-browse-says-it-twice
title: Offline Public rooms says it could not reach the service twice
written: 2026-11-01
quad-damage: yes
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

Found by the agent: Public rooms offline, in a local web build with `fetch` refusing.

## Why
With the room service out of reach, Public rooms shows "No connection to the room service. Check the network and try
again." and under it "Could not reach the room service: could not reach the room service". The detail line is
`discover_failed` wrapped around the generic reason `answering` gives every offline failure, so it says the same
thing twice, the second time to itself. To see it: Play online, Browse public rooms, with the network off or the
room service unreachable.

## What was found
- the detail line is `discover_failed` with `refused.reason`, under `troubled(refused.trouble)` [file composeApp/src/commonMain/kotlin/game/vinto/app/DiscoverScreen.kt]
- `discover_failed` is "Could not reach the room service: %1$s" [file composeApp/src/commonMain/composeResources/values/strings.xml]
- the reason for every failure that is not the service's own is `UNREACHABLE_SERVICE`, "could not reach the room service" [file shared/client/src/commonMain/kotlin/game/vinto/client/RoomTrouble.kt]
- the online screen's `Trouble` shows the bare reason under the same headline, so its offline case repeats the idea too, in other words [file composeApp/src/commonMain/kotlin/game/vinto/app/OnlineScreen.kt]
- seen on the screen in a local web build after the browser-failure fix, where the offline answer now arrives [commit 79559db]

## Questions
1. Drop the detail line when the failure is the generic offline one (`RoomTrouble.OFFLINE` with `UNREACHABLE_SERVICE`), and keep it for a reason the service itself gave?
   Proposed: yes. The headline already says what to do; the service's own words (a full room, a refusal) are still worth the small line.
2. The same rule for the online screen's `Trouble` (Open a room, Join with a code)?
   Proposed: yes, one rule in one place, both screens reading it.
3. Translations?
   Proposed: no string changes; `discover_failed` stays for the service's own reasons in all its locales.
4. Criteria?
   Proposed: a Compose test with a connector answering `Failed(OFFLINE, UNREACHABLE_SERVICE)`: the sentence about the room service appears once; with a service reason it appears under the headline as now. Seen red first.
5. Aspects?
   Proposed: every platform, common code; light and dark themes unchanged; screen readers read one sentence fewer.
