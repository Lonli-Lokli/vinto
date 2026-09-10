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
say so.

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

The replay SHALL be driven by a transport the viewer controls — running it, halting it, and moving
in both directions — and the transport's positions SHALL be the turn boundaries: the table now,
and the table after each turn. Moving through the plan SHALL come to rest on one of those
positions and never between them.

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

- **WHEN** the viewer halts the replay part-way through a turn, or moves the transport to a
  position between two turns
- **THEN** the replay comes to rest on a turn boundary, showing a settled table rather than cards
  in flight

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
