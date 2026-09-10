## 1. The interaction contract

The riskiest part first: today every tap is dropped while ghosts are up, and this change gives
gestures a meaning there. Build the guard before building anything that relies on it.

- [ ] 1.1 Add a stage mode (live / plan) beside `Stage.rehearsing`, and verify a unit test shows
      the mode is `plan` only while the plan is open and `live` in every other state, including
      mid-`Beat.Flourish`
- [ ] 1.2 Replace `unlessRehearsing()` with a router that resolves a tap through `Table.taps` in
      live mode and through the plan composer in plan mode, and verify a test asserts the plan
      branch cannot produce a `Move.Send` (a type-level check, not a runtime one)
- [ ] 1.3 Write the invariant suite: walk a whole final round with the plan open at every step,
      tapping and dragging every card of every seat, and verify **no `GameAction` is dispatched** and the view's
      `gameId`/`turnNumber` never move
- [ ] 1.4 Verify an edit the plan's door refuses (a locked lane, the seat on play) is not offered
      as a drop target or a selection at all, rather than offered and refused — a test over `Board`'s composer against
      `CoalitionPlan.edited`

## 2. The plan on the felt

- [ ] 2.1 Give `Board` a selected turn index supplied by the screen (not by the plan), and verify
      `CoalitionPlan` gains no field — a test asserts the wire shape is byte-identical
- [ ] 2.2 Address `rehearse()`'s frames by index so one turn can be drawn alone, and verify a test
      shows frame *n* renders the table after turns 1..n and no further
- [ ] 2.2a Build the transport — run, halt, both directions — over those frames, and verify a test
      runs a three-turn plan start to finish and lands on the final state
- [ ] 2.2b Detent the transport at the turn boundaries, and verify a test halting mid-turn comes to
      rest on a boundary with a settled table, never on cards in flight
- [ ] 2.2c Wake the edit affordances only at rest and sleep them while running, and verify a test
      finds no drop target and no selection available during a run
- [ ] 2.3 Draw the selected turn on the felt — the cards it moves, between the seats that hold
      them — reusing the existing choreography, and verify a Compose test finds the two cards of a
      planned swap marked at their seats
- [ ] 2.4 Keep the rehearsal marking (`table_rehearsal`) visible for the whole of plan mode, and
      verify a test asserts it is present in every plan-mode state including an empty plan
- [ ] 2.5 Draw a lane with no step as an empty numbered turn that holds its place, and verify a
      test with a step in turn 1 and none in turn 2 shows two turns, numbered 1 and 2
- [ ] 2.6 Draw a step that cannot be drawn (its card has moved) as unplayable rather than as a
      picture, and verify the test that `rehearse()` already returns no frame for it also holds on
      the felt
- [ ] 2.7 Show `Lane.suggestion` only on the selected turn, adopted by one tap as an ordinary
      edit, and verify a test shows a second lane's suggestion is not drawn
- [ ] 2.8 Show the state the plan arrives at as the transport's last position — every hand as the
      plan leaves it, the caller's believed total, and the count of their unseen cards — and verify
      a test asserts the three numbers match `PlanOutcome` and that **no win/lose verdict** appears
      anywhere on the screen
- [ ] 2.9 Carry `StepHealth.BROKEN` onto the felt: mark the turn whose claim a reveal has
      contradicted, on the replay and on its detent, and verify a test disproves a claim mid-round
      and finds the mark — plus one asserting a `REANCHORED` step announces nothing

## 3. Changing the plan

- [ ] 3.1 Drag a card onto another seat's card to plan a swap, and verify a Compose test produces
      the same `PlanEdit.SetLane(Step.Swap)` the rail's composer produced
- [ ] 3.2 Drag a card onto the discard to plan a put-down, and drag onto nothing to plan nothing,
      and verify a test asserts an unhighlighted drop leaves the plan byte-identical
- [ ] 3.3 Highlight only the destinations the composer allows for the selected turn, before
      release, and verify a test shows a locked lane offers no highlighted destination at all
- [ ] 3.4 Start a drag on movement rather than on a long press (nothing in plan mode scrolls), and
      verify a test asserts a drag begins without a hold
- [ ] 3.5 Offer every one of those edits as select-then-select for screen readers, keyboards and
      switch devices, and verify a paired test builds each edit both ways and asserts the two
      resulting `CoalitionPlan`s are equal
- [ ] 3.6 Verify no plan interaction requires hover — a test asserting every plan affordance is
      reachable with no pointer, plus a read of the diff

## 4. The timeline

- [ ] 4.1 Join the session `log` (behind) and `lanes` (ahead) into one ordered timeline anchored
      at the current turn, and verify a test over a mid-final-round session shows played turns
      behind and planned turns ahead, in order
- [ ] 4.2 Mark past turns visibly distinct from planned ones, and verify a Compose test
      distinguishes them by semantics rather than by colour alone
- [ ] 4.3 Verify every turn still to come is readable without scrolling on a phone in portrait —
      a Compose test at 411×740 dp asserting each planned turn's node is displayed

## 5. Claims on the felt

- [ ] 5.1 Ring the claimable cards while the table is asking a member what they hold, and verify a
      Compose test finds exactly the viewer's claimable positions ringed and no others
- [ ] 5.2 Show no "tap a card" instruction when nothing is claimable, and verify a test in that
      state finds neither the ring nor the sentence
- [ ] 5.3 Verify the claim produced by a felt tap is the same `GameAction` the rail's claim
      produced — a test comparing both paths

## 6. The caller, and an emptied table

- [ ] 6.1 Open the plan from the caller's seat, read-only, and verify a test from that seat sees
      the same turns in the same order ending in the same outcome as a coalition member
- [ ] 6.2 Verify the caller is offered no edit and no agreement — a test that every card refuses
      a drag and a select, and that no agree control is present
- [ ] 6.3 Verify a plan edit sent from the caller's seat is still refused by the door, unchanged —
      a `CoalitionDoors` test asserting "the caller has no coalition to plan with"
- [ ] 6.4 Remove every control that acts on the round while the plan is open — draw, discard,
      claim, toss-in, call — and verify a Compose test on the viewer's own turn finds none of them
      present, rather than present-and-disabled
- [ ] 6.5 Verify the whole-screen invariant: activate every node on screen with the plan open and
      assert no `GameAction` was dispatched

## 7. The header

- [ ] 7.1 Add the plan control to the final-round header, present whether or not anything is
      planned, and verify a Compose test finds it in both states with a ≥44 dp target
- [ ] 7.2 Add the way out, of the same standing, and verify a test opens and closes the plan and
      lands back on a live table
- [ ] 7.3 Retire the "Together … vs … Called it" row and the one-time explanation sentence; the
      header keeps the countdown and the plan control and nothing else, and verify a golden shows
      one line and a test asserts no verdict string is rendered outside the plan
- [ ] 7.4 Verify the caller (who has no plan) still gets the countdown and no plan control — a
      test from the caller's seat

## 8. Words, locales and the gates

- [ ] 8.1 Rewrite the `board_*` strings for the new surface and retire `table_final_side_coalition`,
      `table_final_side_caller`, `table_final_versus`, and verify `node tools/check-translations.mjs`
      exits 0
- [ ] 8.2 Fill every retired/added key in all 19 locales through the `translate-game` skill, and
      verify the gate reports the same string count for every locale
- [ ] 8.3 Verify `:composeApp:jvmTest` is green, including `TranslationShapeTest`, `StringEscapeTest`
      and `ScreenContrastTest` over the new surface
- [ ] 8.4 Verify `./gradlew detekt` passes with no new baseline entries
- [ ] 8.5 Regenerate the affected screenshot goldens on a maintainer's machine and verify a human
      has looked at them (`ScreenshotTest` writes; CI deliberately does not run it)

## 9. On a device

- [ ] 9.1 Verify the plan opens, steps, composes and closes on a real phone by touch — the one
      check no test in this list can make
- [ ] 9.2 Verify the same on the desktop target with a mouse (`./gradlew :composeApp:run`) —
      that a drag composes a step with a pointer, and that nothing needed hover

> Deferred deliberately, per design.md — Open Questions: whether "play it through" (the whole
> plan animated end to end, which `rehearse()` already returns) deserves a control beside
> stepping. It does not change the work above.
