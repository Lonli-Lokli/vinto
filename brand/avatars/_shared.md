# The seat portraits

The four classical elements, one per seat, each engraved as three lines — the way the elements
are marked on stone rather than the way a triangle diagrams them. Fire is Ember, water is Tide,
earth is Dune, air is Gale.

Four masters, one per seat, and `tools/svg-to-drawable.mjs` turns each into the Android vector
drawable the app actually loads (`composeResources/drawable/avatar_*.xml`). SVG is the source;
the XML is generated and committed, exactly as `card_*.xml` are.

## Why these are not illustrations

The seats used to be four illustrated humanoid turtles named Leo, Raph, Mikey and Don. Nobody
drew them to infringe anything, but four green anthropomorphic turtles carrying those four names
is the Teenage Mutant Ninja Turtles cast however it was arrived at — one background read "SHELL
SHOCK" in case there were any doubt. App Store Review 5.2 and Play's IP policy both refuse that,
screenshots of the table would have published it, and the liability sits with the developer
rather than with the store. `vydanne.config.mjs` could not answer `contentRights` while they were
in the build, and that one field blocks submission.

## What they have to do

* **Read at 44 dp.** A seat plate on the felt is a small circle (`SeatPlate.PlateTap`), and most
  of the time that is the only size anyone sees. A silhouette survives that; a scene does not —
  which was true of the turtles too, and is why they were mostly a green smudge in play.
* **Not be told apart by colour alone.** `vydanne.config.mjs` claims
  `differentiateWithoutColorAlone` to Apple, and that claim has to hold here as much as in the
  deck. Each emblem is three engraved lines, and what differs is how the lines *run*: fire stands
  them up and wavers them, water lies them down and rolls them, earth lays them dead straight, air
  curls them away at the end. Direction first, then waver, then curl — none of it colour. Each
  seat's name says which element is its own.
* **Not sit on the felt in the felt's own colour.** `FELT` is #1B5E43, so the green seat is teal
  instead: near enough to belong to the deck's green family, far enough to hold an edge against
  the table it is drawn on.

## The parts every master shares

A disc in the seat's colour, the deck's ink ring around it, and the court cards' gold hairline
just inside — the same pair `card_q.xml` draws, so a seat plate and a Queen look like they come
from one game. Circles are written as arc paths because a vector drawable has no `<circle>`.
