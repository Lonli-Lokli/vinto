# Change: The plan on the felt

## Why

The final round is the one cooperative part of Vinto, and the plan is how it is played. It is
currently drawn as rows in the rail — so on a phone it is read through a letterbox, its only
entry is a sentence that reads as status, and the turn order the model carries is thrown away
by the rendering. Reported from a phone, with screenshots: *"it's not clear what I should do to
start planning"*, *"not clear how I should declare mine or other cards"*, *"vertical scroll?!"*,
and *"I thought the plan would be displayed as several connected actions, like in a real game,
from now till the final."*

Five findings, each read off the tree rather than off a document.

**The model is already the timeline the report asks for.** `CoalitionPlan.lanes` is "one per
turn still to come, **in the order they come**"; `sheds` sit beside it because a toss-in is not
a turn; `PlanOutcome` says where the plan lands. Nothing in the data is missing. The rendering
flattens three ordered turns into three identical rows — `You: your call`, `EMBER: YOUR CALL`,
`TIDE: YOUR CALL` — with no order, no consequence, and no cards.

**The animation built for exactly this already exists and is unreachable.** `rehearse(view,
plan)` returns **one ghost `Frame` per turn, in order**, each carrying the table that step
leaves behind and its choreography, built by transforming the view rather than by reducing —
which is what makes it work online as well as solo. Its own header states the intent: "your
example of a plan is four sentences that take a paragraph to write and are hard to read in any
language; the same plan is a few taps and one animation… two players with no language in common
can agree a four-step line that way." It is reached through `label_plan_rehearse` ("Watch the
plan"), which sits below the fold in a scrolling rail and only appears once a step is set. So
it is invisible twice over.

**The board fixed one crowding problem by creating another.** `PlanBoard.kt` records why it is a
rail mode: "drawn beside the prompt it starved the log strip, and the strip is where the bots'
answers land". The rail is a scrolling column, and the plan is the one thing in this round that
has to be read *whole* — the reporter's screenshot is cut mid-sentence at "decides.".

**Claiming a card names no target.** The table says "Tap one of your cards to say what you think
it is" while the primary button reads **DRAW CARD**, and nothing on the felt marks which cards
that sentence is about. The instruction is in the rail; the thing it points at is on the table.

**Two of the four header rows are decoration.** `table_final_side_coalition` / `table_final_versus`
/ `table_final_side_caller` ("Together … vs … Called it") repeat what the seat plates already
show — the caller wears a crown — and `table_final_coalition` is a one-time explanation. Only
`table_final_turns_left` (a live countdown) and the plan summary change, and both are buried
among the static rows.

## What Changes

- **A `Plan` button in the header**, replacing the static block. It is the entry point, it is a
  real hit target for a finger and a pointer alike, and it makes the mode explicit.
- **The plan becomes a mode on the table.** In plan mode the felt draws the plan as ghost cards
  and arrows between the real seats, stepped one turn at a time (① ② ③) rather than played
  straight through. `Stage.rehearsing` already is this mode internally; it becomes user-driven.
- **A timeline anchored at now**: the turns already played are behind it and reachable, the
  turns still to come are ahead and legible without scrolling. Past comes from the session's
  `log`, future from `CoalitionPlan.lanes`.
- **BREAKING (in-app):** the board leaves the rail. `Table.board` and the rail's plan rows
  retire; the rail carries the selected step's words and the agree/act buttons only.
- **The plan is a replay with a transport, and it ends in a final state.** Play, pause and rewind
  over the whole plan, with the scrubber snapping to the turn boundaries; editing wakes only when
  the head is parked on one. Stepping past the last turn shows the hands the plan produces, the
  caller's believed total and how much of it is a guess — **and no verdict**: the comparison is
  the players' to make, and an app that grades every candidate plan replaces the argument the
  round is made of.
- **The caller watches too.** The plan is already sent to every seat (`Envelopes.kt`: "the board
  sent back to every seat"); only the screen withholds it. That becomes a stated rule rather
  than an accident, because a coalition at a real table confers within earshot of the player it
  is planning against. The caller may **read** it and may not edit or agree — a door `editPlan`
  already shuts.
- **Plan mode has no controls that act on the round.** No draw, no discard, no claim, no call:
  the only controls are moving through the plan, changing it, and closing it.
- **Claims are made on the felt.** While the table is asking a seat to say what it knows, the
  cards that can be claimed wear a ring, so the instruction has something to point at.
- **An interaction in plan mode edits the plan and can never be a move.** `unlessRehearsing()` currently
  drops *every* tap while ghosts are on the felt, deliberately — "a tap on one would be a move
  on a table that does not exist". That guard is replaced by a routed one rather than removed.
- **A human edits the plan by dragging a card** onto the card it should swap with, or onto the
  discard to put it down. No hover-only affordance anywhere. Because a drag is unreachable with
  a screen reader, a keyboard or a switch device, the same edits are also available as
  select-then-select — the same edit, another way, not a lesser one.
- **A lane with no step ("your call") keeps its place** in the sequence as an empty numbered
  slot, so ② means the same turn to everyone reading it.
- **Sheds and nods move to the seat plates**; the header keeps the countdown and the plan
  control and nothing else.
- **A disproved claim stays visible.** `PlanHealth` already separates a card *moving* (repaired
  in silence) from a belief being *wrong* (never repaired, and news) — but the broken marker
  currently rides on the rail rows this change retires, so the felt takes it over.

## Capabilities

### New Capabilities

None. The behaviour already exists; this changes where it is drawn and how it is reached.

### Modified Capabilities

- `mobile-app`: how the coalition plan is entered, read and edited — the plan as a replay on
  the table rather than rows in the rail, who may see it and who may change it, the contract
  that keeps an edit from becoming a move, and drag with a non-dragging equivalent.

## Impact

**Code**

- `composeApp/.../game/TableScreen.kt` — the final-round header block, the felt in plan mode,
  ringed claimable cards, seat-plate badges for sheds and nods.
- `composeApp/.../game/ControlPanel.kt` — the rail loses the board and keeps the step's words.
- `composeApp/.../game/CardStage.kt` — `rehearsing` becomes stepped and user-driven;
  `unlessRehearsing()` becomes a router rather than a mute.
- `shared/client/PlanBoard.kt` — `Board` gains a selected step; `PlanLine` retires with the rail
  rows.
- `shared/client/Rehearsal.kt` — already one frame per turn; gains addressing by index.
- `shared/client/TableModel.kt` — the final-round tables.

**Strings** — `board_*` rewritten for the new surface; `table_final_side_*` and
`table_final_versus` retire. Every locale follows (19 files, `tools/check-translations.mjs`).

**Tests** — `CoalitionScreenTest`, `CoalitionLineTest`, `PlanBoardTest`, `RehearsalTest`, and a
new suite for the tap contract. `ScreenshotTest`'s goldens change.

**Not affected** — the engine, the wire, the room, and the bots. No `GameState`, no
`PlayerView`, no `CoalitionPlan` field changes.

## Non-goals

- The caller may read the plan and may never contribute to it: no editing, no agreeing, and
  no speaking into the coalition’s channel.
- No change to `CoalitionPlan` / `PlanEdit` on the wire, to what a `Step` can express, or to how
  bots plan and answer.
- No hover-only interaction, on any platform — and no edit reachable *only* by dragging.
- Not a redesign of the ordinary (non-final) turn, the log strip, or the score sheet.
- Not the online protocol: a plan edit travels exactly as it does today.

## Dependencies

- `design-coalition-play` (96/96 complete, unarchived) — this change reshapes the surface that
  change built, and depends on its model, its `PlanEdit` door and its rehearsal.
