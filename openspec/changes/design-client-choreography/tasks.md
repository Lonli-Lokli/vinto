# Tasks

## 1. Choreography

- [x] 1.1 `Beat` and `Scene`; `Anchor` moves here from `CardFlight.kt`
- [x] 1.2 `choreograph(action, before, after)` over `PlayerView`, replacing `flightsFor`
- [x] 1.3 Beats beyond movement: turning over, a penalty flinch, a seat highlight, a reaction
- [x] 1.4 Tests: every action that moves a card, and the ones that deliberately move none

## 2. The queue

- [x] 2.1 `AnimationQueue` — submit scenes, take the next, report what is pending
- [x] 2.2 Collapse past a budget, and prove it: a client twelve events behind plays none of them
- [x] 2.3 Tests, including that dropping the whole queue leaves the game correct

## 3. The stage

- [x] 3.1 Play scenes rather than flights; beats within a scene run together
- [x] 3.2 Non-movement beats drawn: flinch, highlight, reaction
- [x] 3.3 The local session feeds the queue through the same path a socket will

## 4. Wiring it to a room (not in this change)

- [ ] 4.1 `RemoteGameSession` submits the room's log to the same queue — the point of the design

## Found while building

- [x] The stage's drain loop asked for a frame forever, so the composition was never idle and
      `waitForIdle` in the UI test never returned. It now drains per batch and goes quiet in
      between — the same behaviour, and a screen that tooling can tell has settled
