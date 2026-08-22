# What moves, and where it moves to

Every card movement on the table, with where it starts and where it ends.

**This table is generated, not written.** `AnimationMapTest` plays each move through a real
session and reports the flights the choreography produced, so it cannot describe an app that
does not exist. To regenerate it:

```sh
cd kmp && ./gradlew :shared:client:jvmTest --tests "*AnimationMapTest*" -i
```

The rows most easily got wrong are asserted in that case as well, so a change that moves a card
somewhere else fails the build rather than the eye.

## Flights

| What you do                        | What flies, and where |
| ---------------------------------- | --------------------- |
| Draw a card                        | deck → drawn slot |
| Play the drawn card's action       | drawn slot → discard (lit) |
| Throw the drawn card away          | drawn slot → discard |
| Swap it in, saying nothing         | drawn slot → your hand, your hand → discard |
| Swap it in, calling the rank right | drawn slot → your hand, your hand → discard (lit) |
| Swap it in, calling the rank wrong | drawn slot → your hand, your hand → discard, deck → your hand |
| Aim a peek (7, 8, 9, 10)           | nothing moves |
| Finish looking                     | nothing moves |
| Name a rank with a King            | nothing moves |
| Throw a card in                    | your hand → discard |
| Take the top of the discard to play it | nothing moves |

Two cards in one row fly **together**, not one after the other: a swap is one gesture.

## Why three of those rows say "nothing moves"

They are the cases where a card is already where it is going.

- **Aiming a peek** does not move the card it looks at. It lifts where it lies, which is a
  different beat — everybody sees *which* card was looked at, and only the looker sees the face.
- **Finishing an action** — the last tap of a peek, a skip, a King's declaration — is the engine
  writing the card into the discard pile. It has been lying on the pile since the moment it was
  played: that is what makes its toss-in window legitimate, and it is drawn there throughout.
- **Taking the top of the discard** to play its action never lifts it off the pile at all.

## Movements that are not flights

| What happens | What you see |
| --- | --- |
| Somebody looks at a card | it lifts towards the middle of the table and settles back |
| A penalty lands | the hand flinches, and a red ring marks the seat |
| A declaration is answered | a green or red ring on the pile |
| The deck runs out | the discard sweeps back into the deck |
| The turn passes | the new seat's ring flashes |
| A King borrows a rank | the borrowed card is held up beside the King |

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
