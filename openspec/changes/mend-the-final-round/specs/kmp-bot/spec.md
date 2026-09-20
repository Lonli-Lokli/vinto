# Spec delta: kmp-bot

## ADDED Requirements

### Requirement: A bot does not call Vinto holding cards it could throw away

A bot SHALL NOT call Vinto while it believes it holds a card it could still throw into an open
window. Calling ends the round with points in hand that the window would have taken off it, and
the decision SHALL be made where the call is decided rather than left to the rollout to price.

#### Scenario: A window is open and the bot holds a match

- **WHEN** a bot's turn ends with a toss-in window open for a rank it believes it holds
- **THEN** it throws the card rather than calling Vinto on that turn

### Requirement: A bot's line is no longer than the outcome needs

Where two lines reach the same position for the same cost, a bot SHALL prefer the shorter one.
A line that takes cards from opponents, or spends more of the table's time, SHALL be chosen only
where it reaches a position the shorter line does not.

#### Scenario: A drawn King can be swapped in or played at length

- **WHEN** playing a King's action reaches the same hand total as swapping it into hand
- **THEN** the search does not prefer the longer line on the strength of its length

