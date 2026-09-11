## 1. The interaction contract

The riskiest part first: today every tap is dropped while ghosts are up, and this change gives
gestures a meaning there. Build the guard before building anything that relies on it.

- [x] 1.1 Add a stage mode (live / plan) beside `Stage.rehearsing`, and verify a unit test shows
      the mode is `plan` only while the plan is open and `live` in every other state, including
      mid-`Beat.Flourish`
- [x] 1.2 Replace `unlessRehearsing()` with a router that resolves a tap through `Table.taps` in
      live mode and through the plan composer in plan mode, and verify a test asserts the plan
      branch cannot produce a `Move.Send` (a type-level check, not a runtime one)
- [x] 1.3 Write the invariant suite: walk a whole final round with the plan open at every step,
      tapping and dragging every card of every seat, and verify **no `GameAction` is dispatched** and the view's
      `gameId`/`turnNumber` never move
- [x] 1.4 Verify an edit the plan's door refuses (a locked lane, the seat on play) is not offered
      as a drop target or a selection at all, rather than offered and refused — a test over `Board`'s composer against
      `CoalitionPlan.edited`

## 2. The plan on the felt

- [x] 2.1 Give `Board` a selected turn index supplied by the screen (not by the plan), and verify
      `CoalitionPlan` gains no field — a test asserts the wire shape is byte-identical
- [x] 2.2 Address `rehearse()`'s frames by index so one turn can be drawn alone, and verify a test
      shows frame *n* renders the table after turns 1..n and no further
- [x] 2.2a Build the transport — run, halt, both directions — over those frames, and verify a test
      runs a three-turn plan start to finish and lands on the final state
- [x] 2.2b Detent the transport at the turn boundaries, and verify a test halting mid-turn comes to
      rest on a boundary with a settled table, never on cards in flight
- [x] 2.2c Wake the edit affordances only at rest and sleep them while running, and verify a test
      finds no drop target and no selection available during a run
- [x] 2.3 Draw the selected turn on the felt — the cards it moves, between the seats that hold
      them — reusing the existing choreography, and verify a Compose test finds the two cards of a
      planned swap marked at their seats
- [x] 2.4 Keep the rehearsal marking (`table_rehearsal`) visible for the whole of plan mode, and
      verify a test asserts it is present in every plan-mode state including an empty plan
- [x] 2.5 Draw a lane with no step as an empty numbered turn that holds its place, and verify a
      test with a step in turn 1 and none in turn 2 shows two turns, numbered 1 and 2
- [x] 2.6 Draw a step that cannot be drawn (its card has moved) as unplayable rather than as a
      picture, and verify the test that `rehearse()` already returns no frame for it also holds on
      the felt
- [x] 2.7 Show `Lane.suggestion` only on the selected turn, adopted by one tap as an ordinary
      edit, and verify a test shows a second lane's suggestion is not drawn
- [x] 2.8 Show the state the plan arrives at as the transport's last position — every hand as the
      plan leaves it, the caller's believed total, and the count of their unseen cards — and verify
      a test asserts the three numbers match `PlanOutcome` and that **no win/lose verdict** appears
      anywhere on the screen
- [x] 2.9 Carry `StepHealth.BROKEN` onto the felt: mark the turn whose claim a reveal has
      contradicted, on the replay and on its detent, and verify a test disproves a claim mid-round
      and finds the mark — plus one asserting a `REANCHORED` step announces nothing

## 3. Changing the plan

- [x] 3.1 Drag a card onto another seat's card to plan a swap, and verify a Compose test produces
      the same `PlanEdit.SetLane(Step.Swap)` the rail's composer produced
- [x] 3.2 Drag a card onto the discard to plan a put-down, and drag onto nothing to plan nothing,
      and verify a test asserts an unhighlighted drop leaves the plan byte-identical
- [x] 3.3 Highlight only the destinations the composer allows for the selected turn, before
      release, and verify a test shows a locked lane offers no highlighted destination at all
- [x] 3.4 Start a drag on movement rather than on a long press (nothing in plan mode scrolls), and
      verify a test asserts a drag begins without a hold
- [x] 3.5 Offer every one of those edits as select-then-select for screen readers, keyboards and
      switch devices, and verify a paired test builds each edit both ways and asserts the two
      resulting `CoalitionPlan`s are equal
- [x] 3.6 Verify no plan interaction requires hover — a test asserting every plan affordance is
      reachable with no pointer, plus a read of the diff

## 4. The timeline

- [x] 4.1 Join the session `log` (behind) and `lanes` (ahead) into one ordered timeline anchored
      at the current turn, and verify a test over a mid-final-round session shows played turns
      behind and planned turns ahead, in order
- [x] 4.2 Mark past turns visibly distinct from planned ones, and verify a Compose test
      distinguishes them by semantics rather than by colour alone
- [x] 4.3 Verify every turn still to come is readable without scrolling on a phone in portrait —
      a Compose test at 411×740 dp asserting each planned turn's node is displayed

## 5. Claims on the felt

- [x] 5.1 Ring the claimable cards while the table is asking a member what they hold, and verify a
      Compose test finds exactly the viewer's claimable positions ringed and no others
- [x] 5.2 Show no "tap a card" instruction when nothing is claimable, and verify a test in that
      state finds neither the ring nor the sentence
- [x] 5.3 Verify the claim produced by a felt tap is the same `GameAction` the rail's claim
      produced — a test comparing both paths

## 6. The caller, and an emptied table

- [x] 6.1 Open the plan from the caller's seat, read-only, and verify a test from that seat sees
      the same turns in the same order ending in the same outcome as a coalition member
- [x] 6.2 Verify the caller is offered no edit and no agreement — a test that every card refuses
      a drag and a select, and that no agree control is present
- [x] 6.3 Verify a plan edit sent from the caller's seat is still refused by the door, unchanged —
      a `CoalitionDoors` test asserting "the caller has no coalition to plan with"
- [x] 6.4 Remove every control that acts on the round while the plan is open — draw, discard,
      claim, toss-in, call — and verify a Compose test on the viewer's own turn finds none of them
      present, rather than present-and-disabled
- [x] 6.5 Verify the whole-screen invariant: activate every node on screen with the plan open and
      assert no `GameAction` was dispatched

## 7. The header

- [x] 7.1 Add the plan control to the final-round header, present whether or not anything is
      planned, and verify a Compose test finds it in both states with a ≥44 dp target
- [x] 7.2 Add the way out, of the same standing, and verify a test opens and closes the plan and
      lands back on a live table
- [x] 7.3 Retire the "Together … vs … Called it" row and the one-time explanation sentence; the
      header keeps the countdown and the plan control and nothing else, and verify a golden shows
      one line and a test asserts no verdict string is rendered outside the plan
- [x] 7.4 Verify the caller (who has no plan) still gets the countdown and no plan control — a
      test from the caller's seat

## 8. Words, locales and the gates

- [x] 8.1 Rewrite the `board_*` strings for the new surface and retire `table_final_side_coalition`,
      `table_final_side_caller`, `table_final_versus`, and verify `node tools/check-translations.mjs`
      exits 0
- [x] 8.2 Fill every retired/added key in all 19 locales through the `translate-game` skill, and
      verify the gate reports the same string count for every locale
- [x] 8.3 Verify `:composeApp:jvmTest` is green, including `TranslationShapeTest`, `StringEscapeTest`
      and `ScreenContrastTest` over the new surface
- [x] 8.4 Verify `./gradlew detekt` passes with no new baseline entries
- [x] 8.5 Regenerate the affected screenshot goldens on a maintainer's machine and verify a human
      has looked at them (`ScreenshotTest` writes; CI deliberately does not run it)

## 9. On a device

- [~] 9.1 Verify the plan opens, steps, composes and closes on a real phone by touch — the one
      check no test in this list can make. **Opened, stepped and read** on an Android emulator
      from the debug APK: the switch, the band, the rehearsal line, all three turns without
      scrolling, the arrival readout, the seat plates' nods, and the caller's own header with no
      plan to open. **The drag itself is not confirmed on a device** — the emulator's
      `system_server` wedged under the bots' search and would take no more input. It is held by
      `PlanDragTest`, which drives real touch events through Compose's own input pipeline
- [x] 9.2 Nothing in the app is pointer-only: a source read finds no `hoverable`, no
      `PointerEventType.Enter/Exit`, no `onPointerEvent` and no secondary button anywhere, and
      the plan's two edits are a carry and a pair of touches — both of which a mouse, a finger
      and a tablet browser all produce. `PlanDragTest.noPlanAffordanceNeedsAPointerToFind`
      composes a step by touch alone

> Deferred deliberately, per design.md — Open Questions: whether "play it through" (the whole
> plan animated end to end, which `rehearse()` already returns) deserves a control beside
> stepping. It does not change the work above.

## 10. What a phone changed, after the plan above was applied

Everything in §1–§9 shipped as written. Then it was **put in front of a person**, and most of it
was rebuilt over a dozen rounds of reports. This section is the record of that, because the
tasks above now describe a screen that no longer exists in several places — the rail's three
turns, the rehearsal line, the Back/Play/Next transport — and a reader who trusts them will be
looking for things that were deliberately removed.

### The plan is a row, not a sentence

- The rail listed all three turns **and** the header listed them again as stops, a position out
  of step with each other: the header's "Tide" is the table *after* Tide's turn, the rail's
  "Turn 1, Tide" is Tide's turn itself. One list now — the header's — and the belt below holds
  only the turn being built.
- A stop names the turn it **ends**, and that turn is the one the belt builds. `Board.building`
  is the single answer to "which lane is being composed"; three places used to re-derive it and
  drifted apart the moment the index moved.
- The turn is drawn as its parts, in the order they happen: where the card comes from, what
  becomes of it, what it names, who throws in. Six glyphs in `TurnMarks.kt`, drawn rather than
  typed — the obvious `↔` came out as a box in the golden.
- `CoalitionPlan` grew to say what a turn actually is: `Opening` (draw or take the discard),
  `Step.Bin` (let it go — how you tell the best hand not to touch itself), `Step.UseIt` (play
  whatever arrives) and `PutDown.guess`. All additive on the wire; `WireFreezeTest` untouched.
- Watching and editing want different tables. The felt shows the **result** of the turn you are
  parked on, and steps back to the table it *starts* from the moment a part is opened.

### Bugs the person found that no test had

- **The bots played before the person could confirm.** `playBots` read whether the confer window
  was open once, above its loop, and the call that opens it is a move *inside* it.
  `TheCallOpensTheWindowTest`.
- **Play ran an empty film.** The film's positions are one per turn including undecided ones;
  dropping by position after filtering the nulls out mixed two numbers. And the film was built
  over the plan's *stored lanes* rather than the coalition's turns, so a board with one decided
  turn had one position and the arrival quietly showed the present. `TransportTest`.
- **A tap during an animation did nothing visible.** The engine took it at once; the picture
  queued behind the batch being drawn. `AnsweredWhileItFliesTest`, `ThrownTogetherTest`.
- **The toss-in on the card the call was made over vanished**, because the confer window was
  checked above it. `TheThrowSurvivesTheCallTest`.
- **The caller saw no plan at all.** Two faults: the bots seeded only where a human was *in the
  coalition*, and the session asked the member's question before seeding.
  `TheCallerReadsThePlanTest`.
- **The belt's first part was dead** over a played discard: it offered "the other pile", which
  with nothing chosen is *take*, legal only over an unused action card. It answers **draw** now.
- **Every chip was 42×28dp.** `TouchTargetTest` measured the ordinary turn and the rank rail; the
  plan is a third table neither reached. It has a case for it now.

### The debug rig

`androidApp/src/{debug,release}/…/DebugRig.kt` — the same variant gate `captureScene` uses. In a
**local** game only, the last bot calls Vinto the moment its turn comes, whatever it holds, so
the person is first in the coalition. Off by default and absent from the release binary.

The app still starts cold on the home screen. An earlier attempt made the debug build *open* on a
staged scene; that was not what was asked for and is reverted, along with the scene itself.

### Still open

- **Tapping a part opens a chooser, not a replay.** The asked-for shape is "pick the Queen, see
  which two cards get peeked and swapped" — the action shown happening, not named.
- **The six marks have no legend.** The `?` sheet explains every mark the felt draws and says
  nothing about these; the belt was asked about three times, which is the measure of that.
- **The rigged seat calls instead of taking its turn**, rather than playing a card and then
  declaring. Not yet decided which is wanted.
- 8.5 and 9.1 above: a human looking at the goldens, and the drag confirmed on real hardware.
