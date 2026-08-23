# What moves, and how

Every animation on the table: what sets it off, where it starts, where it ends, and what it
looks like when it gets there.

**The first table is generated, not written.** `AnimationMapTest` plays each move through a real
session and reports the flights the choreography produced, so it cannot describe an app that
does not exist. To regenerate it:

```sh
cd kmp && ./gradlew :shared:client:jvmTest --tests "*AnimationMapTest*" -i
```

The rows most easily got wrong are asserted in that case as well, so a change that moves a card
somewhere else fails the build rather than the eye.

## What each move looks like

| What you do | What flies, and where | What it looks like |
| --- | --- | --- |
| Draw a card | deck → drawn slot | flight |
| Play the drawn card's action | drawn slot → discard | **the card swells and glows where it lies**, then a lit flight |
| Throw the drawn card away | drawn slot → discard | flight |
| Swap it in, saying nothing | drawn slot → your hand, your hand → discard | two flights, together |
| Swap it in, calling the rank right | drawn slot → your hand, your hand → discard | two flights, the second lit; a green ring on the pile |
| Swap it in, calling the rank wrong | drawn slot → your hand, your hand → discard, deck → your hand | three flights; a red ring on the pile, the hand flinches, your seat rings, the seat says a line |
| Aim a peek (7, 8, 9, 10) | nothing moves | the card lifts where it lies and glows |
| Finish looking | nothing moves | the lifted card returns to the hand |
| Name a rank with a King, correctly | your hand → discard | the borrowed rank is held up, then a lit flight; a green ring on the pile |
| Name a rank with a King, wrongly | deck → your hand | the borrowed rank is held up; a red ring, the hand flinches, a penalty card flies in |
| Throw a card in | your hand → discard | flight |
| Take the top of the discard to play it | nothing moves | the card swells and glows **on the pile**, where it is already lying |
| A thrown card's action begins | nothing moves | the same, on the pile it was thrown onto |
| Decline a Jack or Queen swap | nothing moves | the two cards jolt where they lie |

Two cards in one row fly **together**, not one after the other: a swap is one gesture.

## Why three of those rows say "nothing moves"

They are the cases where a card is already where it is going.

- **Aiming a peek** does not move the card it looks at. It lifts where it lies, which is a
  different beat — everybody sees *which* card was looked at, and only the looker sees the face.
- **Finishing an action** — the last tap of a peek, a skip, a King's declaration — is the engine
  writing the card into the discard pile. It has been lying on the pile since the moment it was
  played: that is what makes its toss-in window legitimate, and it is drawn there throughout.
- **Taking the top of the discard** to play its action never lifts it off the pile at all.

## The vocabulary

A flight is one word of several. These are all of them, and what drives each.

| Animation | What it looks like | How long | What sets it off |
| --- | --- | --- | --- |
| **Flourish** | the card swells to 2.4× where it lies, lit green, and settles | 900ms | a `Flourish` beat — a card played for its action, and nothing else |
| **Flight** | a card crosses the table, lifting to 1.2× at the top of its arc over a soft shadow. It grows or shrinks to the size of the place it is landing in, and turns to the angle it lies at there — the seats at the sides lie their cards sideways, so a card going to one arrives already turned | 1100ms | a `Move` beat |
| **Lit flight** | the same, lifting half again and carrying a green light | 1600ms | a `Move` beat the table is being *shown* — a played card, a correct call |
| **Flip** | a card turns over on the spot, face to back or back to face | 420ms | **no beat at all** — `CardFace` animates it whenever a card becomes visible or hidden, so a card revealed by a peek, a wrong call, or the end of a round opens by itself |
| **Lift** | a card rises towards the middle of the table and glows where it lies | the scene | a `Peek` beat. Everyone sees *which* card; only the entitled player sees the face |
| **Flinch** | a card jolts sideways and settles | 420ms | a `Flinch` beat — on the hand a penalty just landed in, or on a pair of cards somebody has decided *not* to swap |
| **Ring** | a green or red ring on the pile | the scene | a `Verdict` beat — a declaration answered |
| **Seat ring** | a coloured ring flashes round a plate: green for the turn, gold for Vinto, red for a penalty, blue for the coalition | the scene | an `Attend` beat |
| **Sweep** | the discard pile gathers itself back into the deck | with the count | a `Reshuffle` beat, when the deck runs dry |
| **Held up** | the rank a King borrowed is shown beside it | the scene | a `Borrowed` beat |
| **Line** | a seat says something short | 1400ms | a `Say` beat |
| **Breath** | a ring pulses slowly round a card that can be touched | continuous | not a beat: the card is tappable right now |

Everything above except the **flip** and the **breath** is choreographed — the engine says what
happened, `choreograph` turns it into beats, and the table plays them in order. Those two are
the table reacting to what it is drawing: a card that becomes visible turns over, and a card
that can be tapped breathes for as long as that is true.

## Playing a card against putting one down

These are two different moves and they must not look alike, because they leave the pile in
different states: a card **played** for its action has spent it, while one **discarded** still
carries it — and the next player may take that card and play it instead of drawing.

The table says so three times over:

* **as it happens** — a played card swells and glows where it lies before it travels, and
  travels lit; a discarded one simply flies. While it is being shown off it is drawn *only*
  by the flourish: the slot it is sitting in and the pile it is going to both leave it alone,
  or the same card is in two places at once;
* **afterwards** — the pile draws a gold ring round a card whose action nobody has used;
* **in the log** — "Raph plays the 9" against "Raph throws away the 9".

The web app does the first of those and the third. The ring is ours: the web only tells you by
enabling or disabling the button on your own turn, which is no use to the other three players
and no use at all until it is your turn again.

## The rules the table obeys

1. **Nothing teleports.** If a card is in one place and then in another, the player watched it
   go. `EveryMoveIsSeenTest` plays a game out and fails if any frame changes where the cards are
   without carrying a flight.
2. **A flight ends where the card is drawn.** A correct call was animated to the drawn slot for
   a while, on the reasoning that its action was about to be played from there — but a card in
   play is drawn *on the pile*, so it arrived somewhere it was not.
3. **A hand keeps its shape while one of its cards is in the air.** The table steps to the new
   position before the cards fly, so a hand that has lost a card has already closed up; it holds
   the gap open until the flight lands (`HandGapTest`).
4. **A card the table is being shown travels lit** — a correct call, a played action — which is
   the web app's green "play action" glow.
