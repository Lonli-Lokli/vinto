# Spec delta: kmp-shared-engine

## ADDED Requirements

### Requirement: A seat with no cards still takes its turn

A seat holding no cards SHALL still be given the turn the rules give it. It SHALL be able to draw
from the deck and keep the drawn card, since there is no card in hand to swap it against and
discarding it would end the turn having done nothing.

`ActionValidator` SHALL be the place this is decided, so that a client cannot offer a turn the
engine refuses and cannot refuse a turn the engine allows.

#### Scenario: An empty hand is on play

- **WHEN** a seat with no cards is on play
- **THEN** drawing is legal, and keeping the drawn card is legal

### Requirement: A correct King declaration takes the card and plays its action

A King naming the rank of a card correctly SHALL remove that card from its holder's hand and give
its action to the declarer, and SHALL NOT be resolved as a peek. A peek with a penalty card is
the *wrong*-declaration branch, and reaching it after a correct naming is a defect.

#### Scenario: A King names a card correctly

- **WHEN** a King is pointed at a card and the rank named is that card's rank
- **THEN** the card leaves its holder's hand, its action is the declarer's to play, and the
  declarer draws no penalty card
