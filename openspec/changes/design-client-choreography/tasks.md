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

## 4. The rest of the vocabulary

Audited against the web app's `CardAnimationStore` and `AnimationService`:

- [x] 4.1 A peek is **public** — everyone sees which card was looked at, only the entitled
      player sees its face. This was the largest gap: another player's peek was invisible, and
      it is real information (it is how you know a bot has just learned one of its own cards)
- [x] 4.2 An Ace points at whoever has to draw, since it names a player and not a card
- [x] 4.3 A played action card is held up in the middle before it goes down, so the table can
      read it — the web's two-stage "play action"
- [x] 4.4 A declaration is answered in green or red, derived from whether a penalty followed:
      the penalty *is* the verdict, so one check covers the swap call and the King
- [x] 4.5 Somebody else's draw turns over on the way, so it reads as theirs rather than yours
- [x] 4.6 Seats are pointed at, in a colour per reason: turn, Vinto, penalty, coalition

## 5. Missing from the web app too

Found by walking the game rather than the code. None of these exist in either client:

- [ ] 5.1 **The deal.** Twenty cards appear at once. It is the first thing anybody sees and
      the moment the table is established
- [ ] 5.2 **The reshuffle.** When the discard pile goes back into the deck, everyone's memory
      of what has been played becomes stale — a real information event, currently silent
- [ ] 5.3 **The turn moving.** Three bots act in under a second between one tap and the next;
      a static ring on the active seat is easy to lose track of
- [ ] 5.4 **The reveal at scoring.** Every hand turns over at once. Seat by seat is the payoff
      of the whole round
- [ ] 5.5 **How much of the final round is left.** Each player takes exactly one more turn and
      there is no way to see how many remain
- [ ] 5.6 **What a King borrowed.** It declares another rank's action; nothing shows which
- [ ] 5.7 **Toss-in lockout.** A failed toss-in bars you for the rest of the round, and nothing
      says so
- [ ] 5.8 **Why a round ended** when the deck ran out rather than because somebody called

## 6. Wiring it to a room (not in this change)

- [ ] 4.1 `RemoteGameSession` submits the room's log to the same queue — the point of the design

## Found while building

- [x] The stage's drain loop asked for a frame forever, so the composition was never idle and
      `waitForIdle` in the UI test never returned. It now drains per batch and goes quiet in
      between — the same behaviour, and a screen that tooling can tell has settled
