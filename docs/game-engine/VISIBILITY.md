# Visibility — what the engine reveals, and what the table animates

The game has a finite, known set of actions, and every one of them either shows somebody a
card or does not. For a long time that was settled one sighting at a time — a card drawn
twice, a card that teleported, a card revealed to the wrong seat — each fix correct in
isolation and blind to the whole. This document is the whole: one row per action, who may
see what, and what the table draws for it. **Where this file and the code disagree, the code
is the bug** — unless `VINTO_RULES.md` says the file is, in which case fix the file and both
engines.

It is held executable by two tests, one per table:

- **Table A** — `kmp/shared/engine/src/commonTest/.../VisibilityMatrixTest.kt` plays every
  row through the real engine and byte-searches every seat's serialised view for the card's
  id, both halves: the seat that must see it, and every seat that must not.
- **Table B** — `kmp/shared/client/src/commonTest/.../TransformationMatrixTest.kt`
  choreographs every row twice, from the actor's seat and from a bystander's, and asserts
  the whole beat script for both. `docs/kotlin/ANIMATIONS.md` is this table's generated
  evidence — `AnimationMapTest` prints it from a live session.

Adding an action to the game means adding a row to each table and each test. A row that
cannot be written is a rule that has not been decided yet.

## The boundary

**The engine decides what is revealed, never the client.** The same action shows a card or
does not depending on the rules, and that decision lives in exactly one place. A client
renders; it holds no card it was not sent, so it *cannot* show one — a screen politely
declining to draw a card it holds is not privacy, because a client we did not write draws
whatever it was sent.

The engine speaks through three channels, and only three:

1. **The per-seat view** (`projectView`) — facts that hold for as long as they hold: the
   card in play, the top of the discard, your own two cards during setup, a peek's target
   while the peek is running. A hidden card in a view carries *nothing*, not even an id —
   card ids contain the rank, so an id is the answer whatever field it rides in.
2. **The reveal event** (`PublicReveal` on `ReduceResult.Success`) — moments: a card turned
   face up where it lies for the whole table, and face-down again afterwards. Exactly two
   actions produce one — a King naming a card wrongly, and a toss-in that missed — because
   in both the card is shown and then *stays in the hand it was in*. A moment carried in
   state would need an expiry, in both engines and in every recorded hash; carried on the
   result it is what a room sends its four clients, and what the local session hands its
   one.
3. **Nothing** — everything a seat has merely *learned*. The engine remembers what every
   player has seen, because the bots and the scoring need it; none of it is ever sent.
   **A client is never sent what it should merely remember.** Remembering your hand is the
   game, and a client that cannot forget has already won it.

The client owns the rest: how a thing moves, how long it takes, what it looks like when it
arrives. `choreograph(action, before, after)` maps each action and a pair of per-seat
*views* — never states — to beats, which is why it cannot animate a card the seat is not
entitled to see: it was never given one. The same action therefore produces different beats
for different seats, and the difference is always and only a withheld face — a peek is a
lifted card with a face for the peeking seat and a blank lift for everybody else, never a
different movement (`TransformationMatrixTest.theColumnsDifferOnlyByWithheldFaces`).

## Table A — what each action reveals

"—" in the Reveal column means no `PublicReveal` is emitted; a card called *public* is in
every seat's view. Rules are quoted from `VINTO_RULES.md`; **DECIDED** marks a cell the
rules are silent on and the project has chosen, with the reasoning beside it.

| Action | The card in question | Actor may see | Other seats | Reveal | Rule |
| --- | --- | --- | --- | --- | --- |
| Setup peek | the card you peeked | yes, during setup only | no | — | "peek at **any 2** of their own cards once, then keep them face-down" |
| The round starting | the same card | no | no | — | **DECIDED**: what a seat remembered is not sent — remembering it is the game |
| Draw from deck | the drawn card | yes | yes | — | "draws top card from draw pile and **reveals it publicly**" |
| Discard the drawn card | the discarded card | yes | yes | — | the discard is face-up; it is the top of the pile |
| Take from discard | the taken card | yes | yes | — | it was the top of the pile and is now the card in play, still lying there |
| Use the drawn card's action | the card in play | yes | yes | — | a played card lies face-up on the pile for the whole of its action |
| Swap — the card going in | the drawn card, landing | no | no | — | "place the drawn card **facedown** in their row"; the table watched it while it was pending, and now must remember it |
| Swap — the card coming out | the displaced card | yes | yes | — | "discard the swapped card **face-up**" |
| Swap, declaring right | the displaced card | yes | yes | — | "If correct → immediately play that card's action" — it is now the card in play |
| Swap, declaring wrong | the penalty card | no | no | — | "take one penalty card **face-down** from deck" |
| Aim a 7/8 | your own card, looked at | yes, while the action runs | no | — | "peek **one of your own** cards" |
| Aim a 9/10 | an opponent's card, looked at | yes, while the action runs | no — not even its owner | — | "peek one card **of another player**" |
| Confirm or skip the peek | the card that was shown | no longer | no | — | **DECIDED**: a peek ends; what you saw is yours to remember, not to be sent again |
| Aim a Queen (each card) | the card looked at | yes, while the action runs | no | — | "**check** two cards belonging to two different players" |
| Execute or skip the Queen swap | both cards | no longer | no | — | **DECIDED**: same as the peek ending — the Queen looked, the look is over |
| Aim a Jack (each card) | the card aimed at | no | no | — | **DECIDED**: a Jack swaps **blind** — that is the entire difference between a Jack and a Queen, and it is worth ten points |
| Execute or skip the Jack swap | both cards | no | no | — | same |
| Aim a King | the card it points at | no | no | — | **DECIDED**: the card is the question the King asks; sending it is sending the answer |
| Declare a King, right | the named card | yes | yes | — | "declare the value of any card and play its action" — the card leaves the hand face-up: onto the pile, or into play if it has an action |
| Declare a King, wrong | the named card | not in the view | not in the view | **yes** | **DECIDED** (matches the web): naming a card wrongly shows the table what it really was — for that moment; afterwards it is face-down and remembered |
| Declare a King, wrong | the penalty card | no | no | — | face-down from the deck |
| Aim an Ace | the card the target must draw | no | no — not even its new owner | — | "choose a player to draw one card from the deck **face-down**" |
| Toss in, right | the thrown card | in flight, face-up; afterwards only as the top of the pile | same | — | "toss it in **face-up** on top"; **DECIDED**: the view carries only the pile's top — a card that slides beneath an unplayed action is memory, like the rest of the pile |
| Toss in, wrong | every card in the attempt | not in the view | not in the view | **yes** | "If wrong → they take the card back", which happens face-up; **DECIDED** (matches the web): the whole attempt is shown, matches included — all of it was put forward |
| Toss in, wrong | the penalty card | no | no | — | "draw **1 penalty card face-down**" |
| A thrown action card starts | the card in play | yes | yes | — | "perform its action **at once**" — it lies face-up on the pile it was thrown onto |
| Call Vinto | — | nothing new | nothing new | — | calling reveals no card |
| Coalition leader chosen | — | nothing new | nothing new | — | **DECIDED (reversed)**: an earlier model showed the leader every member's real hand. "The Coalition **may work together and share information**" is now modelled as *table talk* instead — `DECLARE_CARDS`, below — so nobody's actual cards turn over and remembering a hand stays the game |
| Declare cards (final round) | the claimed ranks, never the cards | every seat, the caller included | every seat | — | **DECIDED**: a coalition member says out loud what they *believe* their own cards are. Claims are public, optional, partial, never checked against the real cards — a wrong memory declares wrongly. A claim is cleared when a swap-in discards its card, shifted when a removal renumbers the hand, and **travels with its card** through a watched Jack or Queen swap |
| Round scored | every card, and the scores | yes | yes | — | "All players **reveal their cards**" |
| Reshuffle | the cards the deck takes back | no | no | — | **DECIDED**: they go back face-down; everything anyone learned from the pile goes stale at that moment |

## Table B — what each action transforms

What moves, what turns over or resizes, what is shown, and what the table draws — in the
beat vocabulary of `Choreography.kt` (`ANIMATIONS.md` holds the visual vocabulary: what a
flight, flourish, lift, ring or flinch actually looks like; `docs/kotlin/CHOREOGRAPHY.md`
holds the design behind both). Where the Shown column says *actor*, the actor's seat is
shown the face and every other seat gets the same movement blank — that is the only way two
seats' scripts are ever allowed to differ.

One thing in this table is a **state rather than a beat**: the lift. A card an action has
taken up stays up for as long as the action runs — the view's `pendingAction.targets` holds
it, `heldUp` reads it, and the table lowers the card only when a flight takes it or the
action resolves. The matrix test writes this as a closing `hold` clause on each script, so a
Queen's aim rows *end* holding two cards and her execute and skip rows end holding none —
which is the fix for her first card lowering itself between taps, made executable.

| Action | What moves (from → to) | Turns over / resizes | Shown | The table draws |
| --- | --- | --- | --- | --- |
| Draw from deck | deck → drawn slot | turns face up leaving the deck | every seat | one flight, face showing |
| Discard the drawn card | drawn slot → pile | stays face up | every seat | one flight |
| Use the drawn card's action | drawn slot → pile | swells 2.4× where it lies, then flies lit | every seat | a flourish, then a lit flight |
| Take from discard | nothing — the card is already on the pile | swells on the pile | every seat | a flourish on the pile |
| Swap, no declaration | drawn slot → your row **and** your row → pile, together | the incoming card turns face down on landing; the outgoing turns face up | every seat, both faces | two flights that cross |
| Swap, declaring right | the same two flights, the second lit | same | every seat | plus a green ring on the pile |
| Swap, declaring wrong | the same two flights, plus deck → your row | the penalty flies face down | every seat but the penalty | plus a red ring, a flinch, the seat ringed red, a line |
| Aim a 7/8/9/10 | nothing | the aimed card lifts and glows, and stays up for as long as the peek runs | *actor* — but *which* card is public | a lift, held |
| Confirm or skip the peek | nothing | the lifted card lowers back into its place, face-down | — | the hold ending — the card comes home; no beat |
| Aim a Queen (each card) | nothing | the same lift — and **both cards stay up together** until the swap is answered | *actor* | a lift, held; after the second, two of them |
| Execute the Queen swap | first target ⇄ second target | neither turns over | *actor* sees both faces in flight | two crossing flights, setting off from where the cards hover |
| Skip the Queen swap | nothing | both cards lower home unswapped | — | both lifted cards flinch where they hover, then come home — "I looked and decided not to" is a decision the table sees |
| Aim a Jack (each card) | nothing | a blank lift, held like the Queen's | nobody, the actor included — the Jack does not look | a lift, held |
| Execute the Jack swap | first target ⇄ second target | neither turns over | nobody | two crossing flights, blank, from where the cards hover |
| Skip the Jack swap | nothing | both cards lower home | — | both lifted cards flinch, then come home |
| Aim a King | nothing | a blank lift, held through the declaration | nobody | a lift, held |
| Declare a King, right | the named card: hand → pile | turns face up as it leaves, lit — the flight takes it from where it hovers | every seat | the borrowed rank held up, a lit flight, a green ring |
| Declare a King, wrong | deck → the declarer's row | the named card turns face up **where it hovers**, still lifted through the verdict, then lowers face-down | every seat, for the moment | the borrowed rank held up, a red ring, the penalty flight and flinch, then the reveal |
| Aim an Ace | deck → the target's row | flies face down | nobody | the target's seat ringed — and still ringed when the card lands, because the naming and the flight are one moment — a line, the flight, a flinch |
| Toss in, right | your row → pile | face up as it flies | every seat | one flight per thrown card, thrown together |
| Toss in, wrong | deck → your row | the attempt turns face up **where it lies**, then back down | every seat, for the moment | the penalty flight, flinch, ring and line, then the reveal |
| A thrown action card starts | nothing — it flew when it was thrown | swells on the pile | every seat | a flourish on the pile |
| Call Vinto | nothing | nothing | — | the caller's seat ringed gold, "Vinto!", then the turn ring moving on |
| Coalition leader chosen | nothing | the hands the leader may now see turn over on that screen alone | leader | the leader's seat ringed blue, and a line above the felt naming who plays for whom until the round ends; both are the table's own, not beats |
| Reshuffle | pile → deck, minus the top card | the returning cards are face down again | nobody | a sweep, with the count, and the turn ring moving on |
| Round scored | nothing | every hand turns over where it lies, **seat by seat in table order** — the same script for every viewer, since two scripts may differ only by a withheld face | every seat | one reveal scene per seat; a card the viewer could already see does not turn again |

## Evidence

| Invariant | Test |
| --- | --- |
| Every row of Table A, positive and negative | `engine/.../VisibilityMatrixTest.kt` |
| Every row of Table B, from two seats | `client/.../TransformationMatrixTest.kt` |
| No view ever carries a card the seat may not see, over the 50-recording corpus | `engine/.../ViewRedactionTest.kt` |
| A peek is private to whoever peeked, at the byte level | `engine/.../PeekPrivacyTest.kt` |
| Nothing on the table teleports — every change of place carries a flight | `client/.../EveryMoveIsSeenTest.kt` |
| What the view holds up, per action and per seat — and that it all comes down when the action is answered | `client/.../HeldUpTest.kt` |
| Both of a Queen's cards hover together until the swap is made or declined, on a real screen | `composeApp/.../QueenAimTest.kt` |
| A card in the air is the card it becomes at rest, to the pixel | `composeApp/.../LandingTest.kt` |
| A wrong King's reveal reaches the table and expires | `client/.../KingRevealTest.kt` |
| A wrong throw's reveal reaches the table — through a player's dispatch and through the bot loop | `client/.../TossInRevealTest.kt` |
| The animation map, generated from a live session | `client/.../AnimationMapTest.kt` → `docs/kotlin/ANIMATIONS.md` |
