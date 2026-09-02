# Choreography — states, moments, and the shape of time on the table

The test of this system is a person watching over a player's shoulder. They cannot read the
move log and nobody is narrating; they must still be able to say what just happened, to whom,
and whether it worked. Every rule below serves that person, and a change that makes the table
faster or prettier but harder for them to follow is a regression whatever else it improves.

Premium means a real card table, not a Material app: unhurried, weighted, deliberate. Nothing
teleports, nothing blinks, nothing changes size as it lands. The pace was set by the web
client, which was tuned by being played; the discipline — what may move, what must hold still,
what two seats may be shown differently — is this document's.

Three documents describe the system, and they divide cleanly:

- `docs/game-engine/VISIBILITY.md` — what the engine reveals to each seat (Table A) and what
  the table draws for each action (Table B). The contract, held executable by two matrix tests.
- `docs/kotlin/ANIMATIONS.md` — the visual vocabulary: what a flight, flourish, lift, ring or
  flinch actually looks like, with its first table generated from a live session.
- this file — the design: why a thing is a state or a moment, how time is organised, and what
  pixel-fidelity requires of every drawing. Where the other two say *what*, this says *why*,
  so that the next animation is added by rule rather than by taste.

## States and moments

Everything the table shows is one of two things, and the whole system follows from telling
them apart.

A **moment** is something that happens: a card travels, a verdict lands, a hand flinches, a
rank is held up. Moments are carried as *beats* — `Beat` in `Choreography.kt` — grouped into
scenes and frames, and the table decides how long each one takes. A moment has a duration
because showing it takes time, and the duration belongs to the table: the player is being
*told* something, and the telling is paced so it can be read.

A **state** is something that holds: whose turn it is, which card can be touched, which cards
an action has taken up, which faces this seat is entitled to see. States are carried by the
per-seat view — `PlayerView`, projected by the engine — and drawn for exactly as long as they
are true. A state has no duration of its own, because its duration is the game's: a player
deciding whether to swap the two cards a Queen looked at may take three seconds or thirty, and
the table's job is to keep showing the truth for the whole of it.

The rule for deciding which is which: **if the table controls how long it lasts, it is a
moment; if the game does, it is a state.** A flight lasts 1100ms because the table says so —
a moment. An aim lasts until the player resolves it — a state.

The defect that forced the distinction: aiming a Jack or a Queen picks two cards, one tap at a
time, and each aim was a beat — so each lift lived exactly one scene, and the first card
lowered itself while the player was still choosing the second. The aim was a state encoded as
a moment, and it expired on the table's clock instead of the game's. The engine had it right
all along: `pendingAction.targets` carries exactly which cards the action has taken up, from
the first tap until the action resolves, and the projection already decides per seat which
faces ride along. The lift now reads its lifetime from that state.

The hypothesis this came from — *beats are for moments, the per-seat view is for states* — is
right, with one amendment. The **onset** of a state is itself a moment: the card rising, the
turn ring flashing as it arrives. The rise stays a beat (`Beat.Peek`) for two reasons. First,
pacing: a bot aims in milliseconds, and a watcher needs the table to dwell on each look —
dwelling is the frame queue's business, and only beats occupy it. Second, the script: Table B
asserts what every action draws from two seats, and a rise that only ever emerged from a view
diff could not be scripted per action. So the division of labour is: **the beat announces the
lift, the state sustains it, and the end is either a flight taking the card away or the state
ending — at which point the card lowers home, turning face-down on the way.** The same
pattern was already on the table before it was named: the turn ring flashes as a beat
(`Attend`) and then burns steadily from `turnHolderId`, a state.

The stage reconciles its lifted cards against the shown view after every frame, which buys a
property the beat-only model cannot have: the animation queue is allowed to drop a backlog
and land on the present, and a lift reconstructed from the present is correct, while a lift
only a dropped beat could have started is simply missing. Anything that must survive a
dropped frame has to be derivable from the view; anything that is not derivable from the view
must be a moment, because it will not survive one.

The full census of state-driven drawings, each active for as long as its state holds and
never started or stopped by a beat:

| Drawing | The state it reads |
| --- | --- |
| The **flip** — a card turning over where it lies | this seat's entitlement to the face changed (`CardView`) |
| The **breath** — a slow pulse on a card | the card can be touched right now |
| The **lift** — a card raised out of its row, glowing | the running action has taken this card up (`pendingAction.targets`) |
| The **turn glow** on a plate | this seat holds the turn (`turnHolderId`) |
| The **gold ring** on the pile | the top discard's action is unused, so it may be taken |
| The **held gap** in a hand | a card of this hand is in the air or lifted |
| The **coalition line** above the felt | who plays for whom, once a leader is chosen |

## The vocabulary: one look, one meaning

Two different meanings never share a look, and one meaning never has two looks — a watcher
learns the vocabulary once and then reads the game with it. `ANIMATIONS.md` holds the full
catalogue; the meanings, and the distinctions that carry rules, are:

- A **flight** is a card changing place. A **lit flight** is a card the table is being
  *shown* — a played action, a call proved right — and it travels slower, higher, in green.
  The difference matters because a played card's action is spent and a discarded one's is
  not, which changes what the next player may do.
- A **flourish** — the card swelling where it lies, lit — means *played*, and nothing else
  ever swells.
- A **lift** means *taken up by an action*: everyone sees which card, and the face rides only
  to the seat the engine entitled. A blank lift on your own screen is not a bug — a Jack
  swaps blind and a King asks before it looks, and the blankness is the rule drawn.
- A **reveal** is the same rise with the face shown to everybody, and it always expires: the
  two moments the rules turn a card over for the table (a wrong King, a failed throw) both
  leave the card in the hand it was in, to be remembered.
- A **verdict ring** — green or red on the pile — answers a declaration, the one move that is
  a gamble on the player's own memory.
- A **flinch** is refusal or cost: the jolt of a hand a penalty landed in, or of a pair of
  lifted cards their holder decided not to swap.
- A **seat ring** points at a person, coloured by why: green for the turn, red for a penalty,
  gold for Vinto, blue for the coalition.
- A **sweep** is the pile going back into the deck, which is the moment everything anybody
  memorised about the pile stops being true.
- A **held-up rank** beside a King names the card it is pretending to be; a **line** is a
  seat saying something short.

Two seats' drawings of one action may differ **only by a withheld face** — a face where the
other seat gets a blank — never by a different movement. The engine decides who is entitled
to what (`VISIBILITY.md`, Table A); the choreography is handed views rather than states, so
it *cannot* draw a card the seat was never sent. `TransformationMatrixTest` holds both
halves: every script from two seats, and that the scripts differ only where a face was
withheld.

## Time

**One easing.** Every movement uses `FastOutSlowInEasing`: a thrown card leaves briskly and
decelerates into its place, which is what thrown things do. Nothing arrives at constant speed
and stops dead, and nothing eases in from a standstill so slowly that it reads as hesitation.
Flights add a `sin(πt)` arc on top — lift and swell peaking mid-journey, zero at both ends —
so a card rises out of the table and settles back into it, and the arc contributes nothing to
the landing geometry.

**Durations are decisions**, in `CardStage.kt`, and they are few: a flight 1100ms, a shown
flight 1600, a flourish 900, a reveal 1800, a flip 420, a flinch 420; a lift rises in 550 and
settles in 420 — the settle matching the flip, so a card is face-down exactly as it lands.
States have no durations, only their onsets and ends do.

**Pauses carry the meaning between the movements** (`Pacing.kt`): a beat of thinking before
somebody else's move, a long dwell after a move worth reading, a short one otherwise, and
nothing before your own — a delay answering the person holding the phone is lag, not pacing.
The player's pace setting multiplies every duration and every pause together, because a table
that quickened its movements but not its silences would read as jerky rather than quick.

**Simultaneity is the scene.** Beats in one scene start together and the scene lasts as long
as its longest beat; scenes play in order; frames — one per action, each carrying the table
that action left behind — play in order at the animated pace while the engine runs ahead. A
swap is one scene because two cards crossing *is* one gesture; a King is several scenes
because naming, judging and paying are three things one after another. Consecutive toss-in
throws are merged so the table scrambles the way a real one does, while each throw's
consequences stay in their own order behind.

## Fidelity: nothing trembles, nothing jumps, nothing changes size on landing

Legibility says what to draw; this says the drawing must not lie about physics. A card that
snaps half its width sideways as it lands, or arrives bigger than the place it lands in, is
the loudest way this table stops being a card table — and each such fault is a geometry
disagreement between two systems drawing the same card, so the rules are about who owns which
truth.

**Every place a card can lie is measured, once, by whatever draws it.** The table reports
each anchor's *berth* as it lays out: where the slot is, where its centre is, how large the
card is drawn there, and which way it lies — the seats at the sides lie their cards sideways.
The card's drawn size is recorded from the scale that draws it, **not** from the measured
box: the box is padded to the 44dp tap target, and a flight that trusted the box landed at
tap-target size on every card drawn smaller than a thumb, which is every opponent's card.

**An overlay drawing converges to the berth, exactly.** A flight, a lift and a flourish all
draw a card over the table, and each ends (or begins) as a card at rest: same centre, same
size, same angle as the resting drawing, read from the same berth — never recomputed. They
align *centres*, not corners, because everything else about the drawing — the scale, the
quarter-turn for a side seat, the resting card centred in its padded box — is symmetric about
the centre; aligning top-left corners of boxes of different sizes is how a landing card used
to jump by half the size difference. `LandingTest` measures a flight's final frame against
the card it becomes at rest, allowing the couple of pixels the last animation frame falls
short by — where the old geometry missed by forty.

**Exactly one drawing per card, with the handoff on a single frame.** While a card flies, the
slot it left and the slot it approaches both draw gaps; while it is lifted, its hand holds
the space; while it is flourished, neither the slot nor the pile draws it. The moment a
flight takes a lifted card, the lift is released in the same call that starts the flight —
the flight begins from the lifted position, so the card is drawn continuously by exactly one
system. Because the flight's final geometry equals the resting geometry, the swap from
overlay to table on the arrival frame moves nothing a pixel can show.

**The layout underneath holds still.** Everything in motion moves in a `graphicsLayer` — the
draw phase, floats, no recomposition per frame — and never by animating layout: a hand keeps
its shape while a card is in the air (`HandGapTest`), the rail is a fixed share of the screen,
and the table has exactly two card sizes chosen once per screen, so nothing reflows under a
moving card or a moving thumb.

**One owner per property.** The flip owns `rotationY`; a flight owns position, scale and
`rotationZ` for the length of the journey; the breath owns a ring's alpha. No property is
ever animated by two systems at once, which is where trembling comes from.

**No transform may depend on when the layer learns its size.** A scale or turn applied about
a layer's centre is applied about `transformOrigin × size` — and on the frame a layer is
born, its transform can be resolved against a pivot the size has not reached yet, so a card
measured on that frame sits somewhere it was never drawn. Every overlay therefore pins its
origin to the top-left and folds the pivot into the translation (`cardTransform` in
`CardStage.kt`): the matrix is the same affine map on the layer's first frame as on its
hundredth, and `QueenAimTest` samples a swap's very first frames to hold it there.

## Reading each action

Table B of `VISIBILITY.md` is the executable, seat-by-seat matrix; what follows is the rule
it implements — for each action, what the shoulder-watcher sees and how they know whether it
worked. Success is always green or lit; failure is always red, a flinch, and a penalty card
they can watch arrive.

- **Drawing** brings the card from the deck to the drawn slot in the middle, face up — the
  rules reveal it publicly, and it is the most informative moment of somebody else's turn.
  **Discarding** it is a plain flight to the pile; **playing** it is a flourish and a lit
  flight, and the difference is the point: a played action is spent, a discarded one is
  there for the taking, and the pile's gold ring keeps saying so after the moment has passed.
- **Swapping** is two flights that cross in one scene: the drawn card in, the old card out
  face-up. A declaration adds the verdict ring — green and the outgoing flight lit when the
  call was right; red, a penalty flight, a flinch and a line when it was wrong.
- **A peek (7/8/9/10)** lifts the aimed card where it lies and holds it up for as long as
  the look runs; everyone sees *which* card, only the peeker sees the face. When the look
  ends the card lowers back into its place, face-down — what a person does with a card they
  have finished reading.
- **A Queen** lifts each card as it is chosen and holds both up together until the player
  decides. Yes is two crossing flights, from where the cards hover; no is both cards
  flinching and lowering home unswapped — "I looked and decided not to" is a decision the
  table shows. **A Jack** is the same shape with every face blank, the actor's included: a
  Jack swaps blind, and that blindness is the whole difference between the two cards.
- **A King** lifts the card it points at, blank — the card is the question — then holds up
  the rank it declares. Right: the named card flies to the pile lit, green ring. Wrong: red
  ring, the penalty arrives, and the named card turns over where it hovers for everyone to
  see before lowering — the table is shown what it really was, once, and then it is memory
  again.
- **An Ace** has no card to show, so it points at the person: their seat rings and keeps
  ringing while their plate says a line and the card flies to them face-down. The naming is
  the only part nobody could reconstruct afterwards, so it is the part the table insists on.
- **A toss-in** is every thrown card flying to the pile together, face up. A wrong throw
  never leaves the hand: the penalty flies in, the hand flinches, the seat rings and says a
  line, and the failed attempt turns face up where it lies before going back to being
  remembered.
- **Vinto** is a gold ring and the word itself; the **coalition leader** a blue ring, the
  hands they may now see turning over on their screen alone, and a line above the felt
  naming who plays for whom for as long as it is true.
- **The reshuffle** sweeps the pile back into the deck with the count — the one moment that
  invalidates everyone's memory at once, and previously the only silent one.
- **Scoring** turns every card over where it lies and hands the screen to the totals.

## Evidence

| Claim | Test |
| --- | --- |
| Every action's script, from two seats, including what stays held up | `client/.../TransformationMatrixTest.kt` |
| Both of a Queen's (and a Jack's) cards stay lifted together until the action resolves, and come home when it is declined | `composeApp/.../QueenAimTest.kt` |
| What the view holds up, per action and per seat | `client/.../HeldUpTest.kt` |
| A flight's final frame is geometrically the card it becomes at rest | `composeApp/.../LandingTest.kt` |
| A lift begins exactly over the resting card | `composeApp/.../LandingTest.kt` |
| A hand's slots do not move while a card is in the air | `composeApp/.../HandGapTest.kt` |
| A peeked card is put back, not taken away | `composeApp/.../PeekSettleTest.kt` |
| Nothing on the table teleports | `client/.../EveryMoveIsSeenTest.kt` |
| The pauses between moves | `client/.../PacingTest.kt` |

## Online is the same choreography

A `RemoteGameSession` builds its frames from the wire exactly the way the local session
builds them from the reducer: each event arrives with the receiving seat's view *after* that
action (see `PROTOCOL.md` — per-event views exist for precisely this), and the session runs
the same `choreograph(action, before, after)` per entry. Catch-up paths — a resync, entries
stored without views — collapse to one landing frame on purpose: nobody wants a replay of
the minute their tunnel ate.

| Claim | Test |
| --- | --- |
| Events become one frame per entry, in order | `client/.../RemoteSessionTest.kt` |
| A whole round over the wire animates one frame per logged action, for both seats | `room/.../TwoClientGameTest.kt` |
| A reconnect costs exactly one landing frame | `room/.../TwoClientGameTest.kt` |
