# Spec delta: mobile-app

## ADDED Requirements

### Requirement: A coalition member can always reach the declaring step

Answering a window SHALL NOT end the coalition's confer window. A coalition member SHALL be able
to answer the toss-in window the Vinto call left standing — by throwing a matching card or by
saying they will not — and still have their own window open to declare what they know in.

The same rule SHALL hold for every seat, human or bot: a window only one side may answer is a
window that never closes, and the round stops.

Only taking a **turn** SHALL end the talking.

#### Scenario: The call's own card leaves a throw open

- **WHEN** Vinto is called and the caller's last card leaves a toss-in window open
- **AND** a coalition member answers that window
- **THEN** the confer window is still open and the declaring table is what the rail shows next

#### Scenario: The bots are held to the same line

- **WHEN** the confer window is open and a toss-in window is open with it
- **THEN** bots answer the toss-in window while their turns stay held

### Requirement: Every control the plan offers reaches its destination

A control drawn on the plan SHALL be routed, not dropped. Plan mode SHALL go on refusing any move
that would act on the real round from a touch on a card — that is what plan mode is for — and
SHALL pass the one control that exists to start the coalition's turns.

A test asserting that the plan *offers* a control SHALL NOT be taken as evidence that pressing it
does anything; the assertion SHALL sit on the router.

#### Scenario: The start control is pressed

- **WHEN** the coalition's window is open and the plan's start control is pressed
- **THEN** the session is told the seat is ready

#### Scenario: A ghost card is touched

- **WHEN** any card on the plan's felt is touched
- **THEN** no action on the real round is dispatched

### Requirement: The plan draws the seat it is about

Every page of the plan SHALL draw each seat's own cards under that seat's own name, and a
rehearsal SHALL name the seats its lanes name. A plan that shows one seat's hand under another's
is refused as a defect rather than as a rendering preference: the coalition agrees to what it can
see.

A card already chosen for a step SHALL read as chosen without relying on a border alone, and the
plan's sentence SHALL stay on screen as it grows rather than running under the edge of the
display.

#### Scenario: A two-seat trade is planned

- **WHEN** a trade is planned between any two seats on screen, in either order
- **THEN** both cards can be selected, and each page draws each seat's cards under its own name

#### Scenario: The plan is replayed

- **WHEN** a planned trade is replayed
- **THEN** the cards move between the two seats the lane names

### Requirement: The Vinto caller is drawn as untouchable

The caller SHALL be drawn so that the seat and its cards read as one thing that is out of bounds,
because the rule that nobody may interact with the caller's cards is otherwise only discoverable
by trying. The mark SHALL be legible at a glance rather than a badge among badges.

#### Scenario: The final round is on screen

- **WHEN** a final round is in progress
- **THEN** the caller's seat and cards are drawn as a single bounded, untouchable group

### Requirement: Nothing on the table moves that has not happened

A card declared by a King SHALL NOT be drawn as though it had been played. A seat's plate SHALL
hold its position when its owner throws a card in, as it does through every other change of mark.

#### Scenario: A King declares a card

- **WHEN** a seat declares a card with a King
- **THEN** the declared card is drawn in the seat that holds it, at the size it is drawn there

#### Scenario: A seat throws a card in

- **WHEN** a seat throws a card into an open window
- **THEN** that seat's plate does not change size or position
