# Spec delta: mobile-app

## ADDED Requirements

### Requirement: The coalition plan is entered from a control

During a final round the app SHALL offer a labelled, persistently visible control that opens the
plan, and SHALL NOT rely on a status line being tappable to make the plan reachable. The control
SHALL meet the same 44×44 dp/pt target every other control does, and SHALL be operable by touch
and by pointer without hovering.

Closing the plan SHALL be offered by a control of the same standing, so that entering the plan is
never a state a player has to guess their way out of.

The final-round header SHALL carry only what changes — the turns remaining and the plan's
standing — and SHALL NOT repeat which seats are on which side, because the seat plates already
say so. While the plan is open the app SHALL spend nothing above the felt on it: the plan's own
rail under the felt carries its pages, and the felt keeps the height four hands need on a phone.

#### Scenario: A coalition member opens the plan

- **WHEN** a final round is in progress and the viewer is in the coalition
- **THEN** a labelled plan control is visible on the table, and activating it opens the plan

#### Scenario: Nothing has been planned yet

- **WHEN** the plan is empty
- **THEN** the control is still present and still opens the plan, rather than being replaced by a
  sentence describing the emptiness

### Requirement: The Vinto caller may watch the plan and may not change it

The plan SHALL be visible to every seat at the table, including the Vinto caller, because a
coalition at a real table confers within earshot of the player it is planning against.

The caller SHALL be able to open the plan, step through it and read where it leads, and SHALL NOT
be offered any means of editing it, agreeing to it, or contributing to it. An attempt to do so
SHALL be refused, as it already is.

#### Scenario: The caller opens the plan

- **WHEN** the Vinto caller opens the plan during the final round
- **THEN** they see the same turns, in the same order, ending in the same final state as a
  coalition member sees

#### Scenario: The caller cannot edit

- **WHEN** the Vinto caller has the plan open
- **THEN** no card responds to an edit gesture, no agree control is present, and no plan edit is
  accepted from that seat

### Requirement: The plan is a replay of the cards it would move

While the plan is open the app SHALL present it as a replay: the cards each turn moves, travelling
between the seats that hold them, in the order the turns come. The table SHALL be marked
unmistakably as showing something that has not happened.

The replay SHALL be driven by a transport the viewer controls, and the transport SHALL be a pager:
one page per coalition turn, in the order the turns come, and a last page where the plan lands. A
page SHALL show the table its turn starts from, and — once that turn's film has been watched — the
table it leaves. Moving between pages, by a swipe or by touching a stop, SHALL be a jump with no
cards in flight; what plays the film SHALL be two controls beside the stops: this turn again, from
the table it starts on, and every turn from the page on screen to where the plan lands, which
SHALL read as a stop while it runs. Halting SHALL come to rest on the page reached and never
between two.

Between two turns of the film the app SHALL say, over the felt and for a beat before the turn's
cards move, whose turn has just played and whose comes next; and a ghost's every movement and
pause SHALL be slower than a real move's, because the film is watched to see where a card goes.

At the end of the replay the app SHALL show the state the plan arrives at: every hand as the plan
would leave it, the caller's total as the coalition believes it, and how many of the caller's
cards nobody has spoken about. It SHALL NOT state whether the plan wins. The comparison is the
players' to make, and a plan is judged by where it lands rather than by reading its steps.

A turn whose step is undecided SHALL keep its place in the order as an empty, numbered turn, so
that the same number names the same turn for every member reading it.

Intentions that are not turns — a member's declared intent to throw a rank in — SHALL be shown
against the seat that holds them rather than in the sequence of turns.

#### Scenario: Running the plan through

- **WHEN** the viewer runs the replay from the start
- **THEN** every planned turn plays in order, and the table remains marked as a rehearsal
  throughout

#### Scenario: Coming to rest

- **WHEN** the viewer halts the replay part-way through
- **THEN** the replay comes to rest on the page it had reached, showing a settled table rather
  than cards in flight

#### Scenario: Turning the page

- **WHEN** the viewer swipes to the next turn or touches its stop
- **THEN** the page changes, the felt shows the table that turn starts from, and no card flies

#### Scenario: Whose turn is next

- **WHEN** the film passes from one coalition turn to the next
- **THEN** a card over the felt names the turn and the seat it passes to, and holds for a beat
  before that turn's cards move

#### Scenario: Reaching the end

- **WHEN** the viewer steps past the last planned turn
- **THEN** the hands the plan produces are shown, with the caller's believed total and the count
  of their cards nobody has spoken about, and no statement of who wins

#### Scenario: An undecided turn

- **WHEN** a plan has a step for the first turn and none for the second
- **THEN** the second turn is shown in its place as an empty numbered turn rather than omitted

### Requirement: The plan replaces the table's controls while it is open

While the plan is open the app SHALL NOT present any control that acts on the round — no draw, no
discard, no claim, no toss-in, no call. The only controls present SHALL be those that move through
the plan, those that change it, and the one that closes it.

No interaction available while the plan is open may change the state of the round.

#### Scenario: The round's controls are gone

- **WHEN** the plan is open on the viewer's own turn, with a draw available
- **THEN** no control that would draw, discard, claim or call is present or reachable

#### Scenario: Nothing dispatched

- **WHEN** every card and every control on screen is activated in turn while the plan is open
- **THEN** no game action is dispatched and the round's state is unchanged

### Requirement: A human changes the plan by dragging a card

A member SHALL change the plan by dragging a card to where the plan should put it: onto another
seat's card to swap the two, onto the discard to put it down. Editing SHALL be available only
while the replay is at rest on a turn boundary — a table mid-movement has no position to edit —
and the app SHALL NOT present an edit affordance while the replay is running. The drag SHALL show what it is over
before it is released, and releasing somewhere no step could be made SHALL leave the plan
unchanged.

Because a drag is unavailable to a screen reader, a keyboard and a switch device, the app SHALL
offer the same edits through a non-dragging sequence — selecting a card and then its destination —
producing an identical change to the plan. This is the same edit reached another way, not a
reduced one.

#### Scenario: Dragging a swap

- **WHEN** a member drags one of their own cards onto a teammate's card
- **THEN** the selected turn's step becomes a swap of those two cards, and neither card moves on
  the real table

#### Scenario: No editing while it runs

- **WHEN** the replay is running
- **THEN** no card offers an edit gesture, and the plan cannot be changed until it comes to rest

#### Scenario: A drag that lands nowhere

- **WHEN** a drag is released over the felt rather than over a card or a pile
- **THEN** the plan is unchanged and the card returns to where it started

#### Scenario: The same edit without dragging

- **WHEN** the same swap is made by selecting a card and then its destination
- **THEN** the resulting plan is identical to the one the drag produces

### Requirement: A card that can be claimed is marked where it lies

When the table asks a member to say what they hold, the app SHALL mark the cards that can be
claimed, on the table, for as long as the question stands. An instruction to tap a card SHALL NOT
be shown without the cards it refers to being distinguishable from the cards it does not.

#### Scenario: The table asks what you know

- **WHEN** the coalition is invited to share what its members hold
- **THEN** the viewer's own claimable cards are visibly marked, and activating one offers the
  claim for that card

#### Scenario: Nothing is claimable

- **WHEN** no card of the viewer's can be claimed
- **THEN** no instruction to tap a card is shown

### Requirement: A claim is built, and may name more than one rank

A member saying what they believe about a card SHALL be able to name **several ranks for one
card**, meaning that the card is one of them, as well as a single rank meaning that it is that
card. The ranks SHALL therefore behave as toggles over the whole rail, and the claim SHALL NOT
be sent until the member says it is finished — a rail that sends on the first rank touched
cannot express an uncertain memory at all, and uncertain memory is the common case.

Taking back what was said SHALL reach only the cards the member is pointing at, leaving
everything else they have said about that hand standing.

Whether a rank is currently named SHALL be conveyed to a viewer who cannot see the screen, and
SHALL be distinguishable from a rail whose ranks are not toggles at all.

#### Scenario: A card that could be one of two

- **WHEN** a member names one card and two ranks, and says so
- **THEN** one claim is recorded holding both ranks, and the card is believed to be either

#### Scenario: A card the member is sure of

- **WHEN** a member names one card and one rank, and says so
- **THEN** one claim is recorded naming that rank exactly

#### Scenario: A pair whose order is still open

- **WHEN** a member names two cards and two ranks
- **THEN** the order is asked for as three answers of equal standing — each arrangement, and
  "not sure" — and the ranks named remain changeable while that question stands

#### Scenario: Correcting one card

- **WHEN** a member takes back what they said about one card of a hand they have spoken about
  more than once
- **THEN** only that card's claim is withdrawn, and the rest of what they said about that hand
  still stands

### Requirement: The plan is legible without scrolling, with the past behind it

The plan SHALL present the turns still to come without requiring the viewer to scroll to read
them, on a phone in portrait. Turns already played SHALL remain reachable from the same place,
and SHALL be visibly distinct from the turns still to come.

The numbers a plan is judged by SHALL live with the plan rather than on the table behind it, so
that the round is not played beside a running score.

#### Scenario: A full plan on a phone

- **WHEN** all three turns of a final round are planned and the plan is opened on a phone in
  portrait
- **THEN** every turn still to come is readable without scrolling

#### Scenario: Looking back at what was played

- **WHEN** the viewer moves back past the current turn
- **THEN** the turns already played are shown, marked as past rather than planned

### Requirement: The plan says when a belief under it has been disproved

A step rests on what somebody said they hold. When a public reveal contradicts one of those
claims, the app SHALL mark the turn that rests on it, for as long as the plan holds that step,
and SHALL NOT quietly repair or substitute it — the coalition is owed the news that something it
planned around was not true.

When a card a step was made about merely **moves** and the step still follows it, the app SHALL
say nothing: the table watched the card go, so nothing has been learned.

#### Scenario: A claim is disproved

- **WHEN** a reveal contradicts a claim that one of the plan's steps was built on
- **THEN** that turn is marked as resting on a disproved claim, and the step is left as it was

#### Scenario: A card moves and the step follows

- **WHEN** a Jack moves a card one of the plan's steps was made about, and the step follows it
- **THEN** the plan is unchanged in the reading and nothing is announced

### Requirement: The plan can say every coalition action, throw-ins in order included

The plan SHALL be able to express any action the rules allow a coalition member: taking either
pile, playing, swapping in or letting the card go, a look at a card, a trade of two cards, a King
pointed at a card and what that card then does, an Ace naming who draws — and, for each turn, who
throws in on it, **in the order they throw**, each with what the thrown card then does. A throw-in
SHALL be part of the turn it lands on rather than a promise beside the plan.

Every such action SHALL be held to the rank that plays it and SHALL NOT touch the Vinto caller's
hand: no look at, trade of, pointing at or lengthening of the caller's cards, and no throw-in by
the caller.

#### Scenario: You play this Queen, then I throw in mine and play it

- **WHEN** a member plans that a teammate takes the Queen off the pile and trades two cards, then
  that they themselves throw a Queen in and trade two more
- **THEN** the plan carries one turn with one throw-in after its step, the replay shows the
  put-down, the throw and both trades in that order, and the readout prices all of it

#### Scenario: Two throws on one turn

- **WHEN** two members say they will throw in on the same turn
- **THEN** the plan carries them in the order they were said, and the order can be changed

### Requirement: The turn is built and read as a sentence

While the plan is open the app SHALL present the turn being built as a sentence of words, one per
decision, in the order the turn happens: where the card comes from, what becomes of it, what it
names, then each throw-in and what its card does. Touching a word SHALL open the question that
word answers, and the word the rail is waiting on SHALL be marked as such and not by colour alone.

**Boxed means touchable.** A decision SHALL be drawn as a boxed word; a fact — the pile it draws
from when there is only one, the card it drew, "and we'll see" for nothing decided — SHALL be
drawn plain and SHALL NOT respond to a touch; the next decision SHALL be on offer as a dashed
box at the end of the line rather than asked before anybody wants it. The caller SHALL read every
word plain. A question with one answer SHALL NOT be asked.

A card the sentence names SHALL be drawn as the card on the felt is — its back, its owner's face,
the rank the table says it is, its place along the row — and a card that is not on the table yet
SHALL be rose, with the turn it arrives on, in the sentence and on the felt alike, from the page
its turn lands on to the last. A throw-in of a card the table cannot vouch for SHALL say so
("blind") and SHALL rehearse as the card coming back with a penalty card beside it.

The rail SHALL be five rows that never move — the page, the answers, the stops, the standing
button, and the page's two rows of sentence and throw-ins — at one height, whatever the turn
says and whether or not a rank is being asked for; at a doubled font a long sentence SHALL
scroll sideways inside its own row rather than move anything under it.

Each turn SHALL offer its own replay from the table it starts on, and the whole plan SHALL be
playable from wherever the transport is parked.

#### Scenario: Building by touching words

- **WHEN** a member touches "+ and then…" in a turn's sentence
- **THEN** the row under the sentence offers put a card down, let it go and we'll see — and play
  it, once the card is face up — and the answer takes its place in the sentence with the plan
  still open at that turn

#### Scenario: A fact is not a control

- **WHEN** the pile holds no card the turn could take
- **THEN** "draws" is drawn plain, nothing happens when it is touched, and a screen reader is
  given no action for it

#### Scenario: A card nobody has seen

- **WHEN** turn 1 puts a card down and the viewer reads turn 3
- **THEN** the card that took its place is rose on the felt and in any sentence that names it,
  tagged with turn 1

#### Scenario: Throwing in blind

- **WHEN** a member picks a card nobody has named as a throw-in
- **THEN** the sentence reads it as thrown blind, and the film shows it coming back with a penalty
  card

#### Scenario: Watching one turn again

- **WHEN** a member activates the replay at the head of a turn's sentence
- **THEN** the felt returns to the table that turn starts on and plays that turn to its end

### Requirement: The plan is information on the live table, never a control

On a member's own turn the app SHALL present the ordinary turn's controls, unchanged by the plan,
and SHALL show what the plan says about that turn as one line of information. The app SHALL NOT
offer a control that performs the plan's step and SHALL NOT compare the draw against the plan.

When conferring ends the app SHALL open the plan, on the member's own turn while it can still be
built and otherwise on the first turn that can, because the plan is what the talking was for; the
switch closes it, and the ordinary controls are there whenever it is closed.

A turn already played SHALL be locked. The turn on play SHALL stay open to editing, with the card
its seat has drawn entering the sentence as a fact, because that card is the news the plan turns
on. When a toss-in window opens that the member may throw in on, an open plan SHALL step aside so
the round's controls are on screen, and SHALL reopen where it was when the member asks; the turn
coming round to the member SHALL NOT close it.

The switch that opens the plan SHALL say, while the plan is closed, that a teammate has changed
the member's own turn since they last looked at it, wearing that teammate's face.

#### Scenario: Ready opens the plan

- **WHEN** a member finishes conferring
- **THEN** the plan opens on their own turn, and closing it leaves the ordinary controls with
  nothing withheld

#### Scenario: Your turn, with the plan open

- **WHEN** the turn comes round to a member who has the plan open
- **THEN** the plan stays open, their turn is still editable, and the card they draw reads in the
  sentence as a fact

#### Scenario: A card lands while the plan is open

- **WHEN** the plan is open and a card lands that the member could throw in on
- **THEN** the plan closes and the toss-in controls are on screen
