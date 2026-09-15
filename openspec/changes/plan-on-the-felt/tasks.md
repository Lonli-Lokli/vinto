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
- [x] 5.4 Make the rank rail a multiple choice: every rank a toggle, the claim sent by a confirm,
      and one card able to carry several ranks — and verify one card plus two ranks records a
      single non-covering claim, tapping a named rank takes it out, and nothing sends until a
      rank is named
- [x] 5.5 Ask the pair's "which way round" with the rail still live under it, so a member who
      changes their mind about one of the two ranks does not start again — and verify the three
      answers and the fourteen plaques are on the same table
- [x] 5.6 Scope "take it back" to the cards under the finger, as the vacuous claim the model
      already carries — and verify a correction to one card leaves the speaker's other claims
      about that hand standing
- [x] 5.7 Retire the three standing buttons from the confer window and rename its way out, and
      verify the window's only choice is `Label.Ready` and no screen still draws "I am low"
- [x] 5.8 Carry a toggle's state to a screen reader, distinguishing a rank that is off from a
      rail whose ranks are not toggles — and verify the claim rail says which and the King's
      rail says neither

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

**Removed on 2026-09-14** at the product owner's request ("remove hardcoded vinto call from bot 3"):
every debug build had the last bot call Vinto on its first turn, which was the rig doing exactly
what it was for, and it had outlived the purpose — the final round is reached by playing now.
What follows is the record of what it was.

`androidApp/src/{debug,release}/…/DebugRig.kt` — the same variant gate `captureScene` uses. In a
**local** game only, the last bot calls Vinto the moment its turn comes, whatever it holds, so
the person is first in the coalition. Off by default and absent from the release binary.

The app still starts cold on the home screen. An earlier attempt made the debug build *open* on a
staged scene; that was not what was asked for and is reverted, along with the scene itself.

### What the claim rail was, and why it changed

Reported from a phone: the confer window "does not say what to do". Two findings behind it.

**The rail sent on the first rank touched**, so one card could only ever carry one rank. `Claim`
has carried partial knowledge since the day it was written — "it is a 7 or an 8" is one position,
two ranks, not covering — and `believedAt` already *pools* partial claims, its own example being
"it is an action card" ∩ "it is a King or a Queen" = {K, Q}. None of it was reachable. The rail is
fourteen toggles and a confirm now; the exact claim costs one extra touch and every partial one
becomes sayable.

**Three of the window's four buttons had no consequence.** `TableTalk.Standing` — "I am low", "I
am high", "Bin me" — is produced by `BotRunner`, rendered into the log by `Say.Standing`, and read
by no planner, no bot decision and no view. So the coalition's own screen offered three sentences
that changed nothing, while the claims the plan *does* read had no button at all and lived on the
felt. The three are gone; the phrasebook value stays and the bots go on saying it, until something
consumes one.

Two smaller things fell out of reading that code. **"Take it back" wiped the whole hand** — it sat
in a picker scoped to one card and sent the empty claim list, so correcting one word silently cost
a player every other thing they had told the coalition about that seat. It is the per-card vacuous
claim now, which is what the bots have always used. And **the felt's instruction said "one of your
cards"** while `declareTaps` has always offered every seat's — a member who peeked the caller's
third card was being told they could not say so.

### Asks are not answered, and are not meant to be

`TableTalk.GiveMe` — "Ember asks You for their card 1" — has no control anywhere that answers
it, and **that is settled rather than missing.** Decided against building one, for three reasons
read off the tree:

- **Nothing consumes an ask.** `BotRunner.askForACard` produces it, `Say.GiveMe` renders it into
  the log, and no planner, no bot decision and no view reads one. The same shape as the standings
  this pass retired.
- **The plan is the channel for it.** "Which cards should end up where" is a lane's swap step —
  a thing a member can build, agree to and watch replayed — and an ask is a weaker duplicate of
  it that carries no authority and leaves no trace.
- **The information an ask would trade on is already shared.** Claims go round at the start of
  the round and may be added at any time; the card in play is public by rule (`projectView`:
  "a player draws the top card and *reveals it publicly*", and every other way a card becomes
  pending is public too), and the discard is face up. What is private is what sits face down in
  a hand, which is exactly what a claim is for.

The sentence stays in the phrasebook and the bots go on saying it, because unlike a standing it
is **playable by hand**: a member holding a Jack or a Queen can grant it on their own turn. It is
a hint in the log, not a prompt the screen owes an answer to.

### Still open

- **Tapping a part opens a chooser, not a replay.** The asked-for shape is "pick the Queen, see
  which two cards get peeked and swapped" — the action shown happening, not named.
- **The six marks have no legend.** The `?` sheet explains every mark the felt draws and says
  nothing about these; the belt was asked about three times, which is the measure of that.
- **The rigged seat calls instead of taking its turn**, rather than playing a card and then
  declaring. Not yet decided which is wanted.
- 8.5 and 9.1 above: a human looking at the goldens, and the drag confirmed on real hardware.

## 11. The turn builder, after the second report

Reported from a phone, with the plan open: *"how should I create plan myself eg to show that I
want to toss now, then swap with my jack and move some cards? And how to design moves for
them?"* Two wants — **a turn as a sequence**, and **somebody else's turn** — and an ask for an
"excellent ergonomic mobile-focused UI action builder". `TurnBuilderTest` is the model's half,
`PlanBuilderTest` the screen's.

### The shape chosen: a builder that walks the turn's own grammar

A turn in this game has a fixed shape — take a card from one of two piles, do one of three
things with it, and if that plays an action the action names something — and toss-ins hang off
whatever lands. So the builder does not let a person assemble steps in any order; it **asks the
next open part of the turn and offers the answers as buttons**, and the felt takes the answers
that are cards:

1. *Where does the card come from?* — only while the pile holds an unplayed action card;
   with a plain card on it the deck is the only pile and nothing is asked.
2. *What becomes of it?* — **Play it**, **Swap it in**, **Let it go**. Each is a whole plan.
3. *Which card goes out?* — touched on the felt, or carried to the pile: the same edit.
4. *Call it: Jack* — an **offer**, once the put-down reads whole, only for a card the table can
   name and whose action a right call would play.
5. *Which two cards does the Jack swap?* — touched on the felt, one then the other.
6. *Throw in* — a rank off the rail, lit where the seat is known to hold it.

The belt under the prompt draws the turn built so far as a row of parts in the order they
happen, and touching a part reopens its own question. Every edit leaves the plan open at the
turn it was made on. The reporter's example — *toss now, then swap with my jack and move some
cards* — is a shed (**Throw in · 7**) plus one lane: **Draw · Swap it in · card 1 = J · call J ·
Nina 1 ⇄ Don 1**, six taps, and the film plays it back.

**Somebody else's turn** is reached from the person it belongs to: every coalition plate is a
control while the plan is open ("Plan Tide's turn"), and each stop on the band wears the seat's
face. The same builder, the same door.

### What was rejected, and why

- **A free-form step list** ("+ add step" → toss / draw / take / swap in / call / swap two /
  declare / discard), which is the literal shape of the sentence reported. It would let a plan
  say things the rules forbid (toss, then draw, then toss again on nothing), so the client would
  need a second copy of the turn's grammar to validate it; it would widen `Lane` to a list of
  steps, which is a wire change every room and every client has to agree on at once; and a
  vertical list scrolls, which is the one thing a phone's plan must not do (§4.3). Most of the
  freedom it offers is illegal freedom.
- **A palette of whole plays** — one-tap recipes such as "put the Jack down, call it, trade X
  for Y". Fast for the common case and the closest thing to the reporter's sentence, but the
  targets vary per table, so the palette is either a scrolling menu or a menu that still has to
  ask for the cards — at which point it is this builder with a longer first step.
- **A form with every slot open at once** (from / do / name / then / throw-in, all editable
  from the start). A row of five empty boxes reads as paperwork, and four of the five have no
  legal answer until the one before them is chosen. The belt keeps the *shape* visible — the
  parts appear in order as they get content — without asking for them out of order.

### The model was widened once, and the cost is on the record

`Step.PutDown` gained `then: Step?` — what the called card's action does: a `Swap` for a Jack or
a Queen, a `Declare` for a King. On the put-down rather than beside it because it is one turn
and the trade is what the call is *for*; a lane holding two steps would let a plan say the trade
without the call that makes it possible. **Additive on the wire**, exactly as `guess` was: an
older build reads the put-down and the call and loses the trade, which is a plan that says less
rather than one it cannot read. `WireFreezeTest` is untouched. The door (`edited`) holds what a
call may go on to do: only a Jack or a Queen swaps, only a King declares, and the card put down
is on the pile by then and cannot be traded. The bots price it (`answerForLane`), the film plays
it, `PlanHealth` follows it and breaks with it, and the live rail pre-arms it.

`Opening`, `Step.Bin`, `Step.UseIt` and `PutDown.guess` had already been added by §10 on the
same additive terms. Nothing else on the wire changed.

### What this pass added on top

- **The plan opens on your own turn** (`openingStop`), not on the first turn of the round —
  which online is somebody else's two times out of three. Your own question on the rail, your
  own plate lit; the stops and the plates are the way to the others. With nothing of yours left
  to build it opens on the first turn that can be, then on where the plan lands; the caller,
  who came to watch, lands on the table now, where Play starts from. `Move.Done` lands in the
  same place, so "I'm ready" puts a member straight on their own turn.
- **The throw-in rail lights the ranks the seat is known to hold** — said to the table, or read
  in the viewer's own hand — and mutes the rest, as the King's rail does. A wrong throw in the
  final round costs a card and bars the seat, and the rail now says which throws are guesses
  before one is made.
- **A promise is read where it pays off.** Nina's turn puts her five down and you have said you
  will throw a five in: the chip is on *her* row, wearing your face — the same face her stop
  wears — beside her own promises. A King's declare and a played pile card land a rank the same
  way. It was only ever on the row of the seat that made the promise, where it said nothing
  about whose card it was waiting for.
- **"Keep it" is "Swap it in".** The live turn's rail says "Swap Cards" for the same move and the
  reporter's word was *swap*; the question that follows asks which card goes out to make room,
  rather than presuming the toss-in was the reason.
- `Said.kt` was using nine `Res.string` accessors it never imported — the previous pass died
  before `composeApp` compiled — and four functions had grown past detekt's complexity bar.
  Both fixed; the complexity by extraction, not by baseline.

### Still open, from this pass

- The caller's plan lands on "Now" so that Play is one press away; a member with nothing left
  to build lands on the arrival, where there is no Play — the transport only runs *forward*
  from a stop before the end. Whether the arrival should also carry "watch it again" is a
  question for the next report.
- A live toss-in window that opens while the plan is open is hidden by design D9, and nothing
  on the plan says "close the plan to throw in". Reading *toss now* literally, that is the one
  way the sentence could still fail.

## 12. The whole of the rules, as a sentence — after the third report

Reported with two screenshots, of the belt: *"not satisfied about current state … we must support
rich clever UI for creating turn actions, visible and available for edit by coalition … plan each
turn in the bottom and be able to replay it and whole round by button in top … think about it as a
way of transforming offline talk into visible UI where everyone can tell verbally like — I want
you to play this Q card, then I will toss in mine Q and play mine."* Options were put with their
costs and three decisions came back, each of which reshaped a layer.

### The decisions

- **A throw-in is part of the turn it lands on, in the order people throw, with what its card
  does** — and the plan must be able to say *any* coalition action the rules allow, order of
  players included. Not a standing promise beside the plan (the shed), which could say neither
  "then" nor "and play mine".
- **The turn is a sentence of tappable words**, not a row of six marks. Two people had looked at
  the marks and could not say what any of them meant.
- **The plan is information only.** No "Do as planned", no "Keep it instead": the live rail is
  the ordinary turn's, and one line under the prompt says what the coalition agreed. The plan
  stands in for the talk at a physical table; the person on play picks up the cards themselves.
  Plan mode and the round's controls are never on screen together, and a table that never opens
  the plan is using the app as intended.

### The model (`shared/shapes`) — additive, and a floor

- [x] 12.1 `Lane.tossIns: List<TossIn>` — who throws, what rank, what the thrown card then does,
      in the order they throw. `PlanEdit.SetTossIns` sets the whole ordered list, so adding,
      removing and reordering are one edit and two members naming the order cannot cross.
      `PlanEditTest.aTurnCarriesItsThrowInsInTheOrderTheyAreThrown`
- [x] 12.2 `Step.Peek(card, also)` for a 7 to 10 and a Queen's look; `Step.ForceDraw(seat)` for
      an Ace; `Step.Declare` gains the card the King points at and what that card does. The door
      holds every action to the rank that plays it — a 7 looks at one of your own, a 9 at
      somebody else's, a Queen at two hands, only a King declares, only an Ace makes somebody
      draw, a five has no action to plan — and nothing in a plan touches the caller: not a look,
      not a pointed card, not a forced draw, and the caller throws nothing in.
      `aThrowInSaysWhatItsCardDoesAndOnlyWhatItCan`, `nothingInAPlanTouchesTheCaller…`,
      `aKingPointsAtACardAndSaysWhatThatCardDoes`
- [x] 12.3 **Protocol 4, floor 4.** A step is a polymorphic tag inside `edit-plan`, `joined`,
      `events` and `sync`; a build that does not know `peek` drops the whole message, mid-game.
      So the plan's vocabulary is frozen per version beside the messages' (`WireFreezeTest.
      planShapes`, with a test that a version growing it raises the floor), and `fixtures/protocol/
      v4/` holds every shape. The `bin`, `use-it`, `open-lane` and `then` that §10–§11 had called
      additive were the same kind of break and go out under 4 with the rest. The room deploys
      before the clients.

### The film, the numbers, the health, the bots (`shared/client`, `shared/bot`)

- [x] 12.4 The rehearsal plays a turn as the seat's step **and** its throw-ins in order, each from
      the table the one before leaves; a look is drawn at its cards; a forced draw lengthens a hand
      with an unseen card; a pointed King takes one card out and plays its action. **What was said
      follows the card**: a trade moves the claims, a card leaving takes its claim and slides the
      rest down, a dealt card arrives unspoken — and a later turn names a card by its claim and is
      drawn from wherever the film has put it. `WholeRulesFilmTest`
- [x] 12.5 The readout is priced **off the table the plan arrives at**, the same picture as the
      film and the felt, so the three cannot disagree. A put-down alone sweeps nothing now: what
      leaves a teammate's hand is what a throw-in says leaves it. `PlanOutcomeTest`
- [x] 12.6 A turn is as well as the worst thing in it: a throw by a seat no longer known to hold
      the rank is broken, a thrown card's trade follows its cards and breaks with them, a look at
      a disproved card is broken, a forced draw names a seat and cannot break. `PlanHealth`
- [x] 12.7 The bots price the whole turn — `answerForTurn` — a throw the table has no grounds for
      is a no, a look is a yes, an Ace on the lowest hand leaves us worse. `LaneAnswerTest`

### The sentence (`PlanBoard`, `PlanFelt`)

- [x] 12.8 `TurnSentence`: one clause for the turn's own move, one per throw-in, one of offers;
      every word a `Slot` with what touching it opens; the open questions of a turn — which pile,
      and then, which card, which two, which rank, who draws — are words too, drawn where the
      answer will go and lit while the rail asks for them. `Part` names where in a turn a question
      is about — the step, a called card, a thrown card, a pointed-at card, at any depth — so one
      composer writes every answer into the right place. `TurnBuilderTest`
- [x] 12.9 The questions: `Doing`, `PuttingDown`, `Naming(part)`, `Aiming(part)`, `Forcing(part)`,
      `Throwing(index, thrower)`. The thrower defaults to the viewer and the plates say who else;
      the rank rail lights what the thrower is known to hold. `PlanModeTest` walks every one of
      them and finds nothing loud.
- [x] 12.10 The rail is three zones and no scroll: the heading; the sentence, which gives way
      first — collapsing to the clause being asked about while a rank rail or a row of seats
      needs the room, hidden while both do; and the foot, which is the answers while a question
      is open and Clear / Agree otherwise. A tappable word is a control's size; a read word is a
      word. `PlanBuilderTest`, `RailFitsTest`, `TouchTargetTest`
- [x] 12.11 **Replay.** Each turn carries its own — back to the table it starts on and run to its
      end — at the head of its sentence; the band's Play runs the whole plan from wherever the
      head is parked, the arrival included. `eachTurnCanBeWatchedAgainAndTheWholePlanFromAnywhere`
- [x] 12.12 The live rail: `Table.planned` sets one `Detail.ThePlanAsksYouTo` line from the
      sentence's said words and arms nothing; `KeepItInstead`, `DoAsPlanned`,
      `YourDrawBeatsThePlan` and `keepingBeatsThePlan` are gone. "I'm ready" lands on the live
      table, not in the plan. The plan steps aside — closes, parked where it was — when a toss-in
      window opens that this seat may throw in on, or when the turn comes round to this seat
      (`GameHolder.noticed`): the two moments the round needs the buttons the plan hides.
- [x] 12.13 The shed rail, the six marks and their words are gone; `TurnMarks` keeps the trade's
      arrow. Seventeen strings retired from every locale, thirty-nine added to English.

### Still open, from this pass

- ~~The builder does not yet offer a Queen's look-only (`Peek` of two cards with no trade): two
  touches on a Queen make a trade. The wire and the film say it; the sentence only reads it.~~
  **It does offer one, and it had no test** — see §14's list for what writing that test turned up.
  The arrow between the two cards is the word: lit she trades, dim she only looks, and touching it
  flips the two. The design's open question is settled there.
- ~~The `?` sheet says nothing about the plan's rail. With the words in the sentence there is no
  legend to give, but "a plan is talk, not a move" is worth a line there.~~ **Done**, as one
  paragraph under More: what the last round is, that the rail becomes a plan written together one
  turn a page, and the line the whole design turns on (D17) — *a plan is talk, not a move: nothing
  in it presses a button for you*. Two strings, in nineteen locales.
  It also **moved the deck count off the end of the phone** the first time, because More is a lazy
  column and the paragraph went in above it. The tab is ordered by what it is for now — the round,
  the plan, the deck, then the two reference items, the count and the rules — so the three things a
  player looks up mid-turn are the three at the top. `HelpTabsTest` caught it and holds both.
- 9.1 stands: the sentence, the throw-in clause and the plan stepping aside for a live window are
  held by tests and have not been touched on a phone.

## 13. The plan as table talk, on five rows — after the fourth report

Reported with five numbered corrections across a design page of mockups ("Plan as Table Talk",
five revisions, approved with *"implement and remember to use colors/sizes acceptable from wcag
for both light and dark themes"*): no band above the felt in plan mode; one turn at a time in a
swiper; the header's switch says the plan was edited while playing; a Queen's look-and-swap on
one line, reusing the card images; fixed rows so nothing jumps, one height, vertically aligned;
never offer taking the pile when the rules do not allow it; a card drawn during the plan
visualised wherever it goes, in a WCAG-acceptable rose; turn transitions as an overlay with
arrows, slower; the final state after all three turns; "draws" and "and we'll see" visibly
untouchable; turn order as on the board; ▶ and ▶▶ icons; the rare blind throw of an unknown card;
a rank picker that fits.

### The model (`shared/shapes`, `shared/client`, `shared/bot`)

- [x] 13.1 `TossIn.card` and `TossIn.blind` — a throw names a card, and one nobody has named
      is blind; `Step.Declare.rank` is nullable so a King points first and names second; a
      played turn locks and the turn on play stays open (`lockingLaneOf`). Protocol 4 is
      unreleased on this branch, so its shapes grew in place and `fixtures/protocol/v4/` was
      regenerated. `PlanEditTest`, `WireSamplesTest`
- [x] 13.2 A plan speaks only of cards the table can see or has been told about: no action on a
      blind draw — the film refuses it and the sentence reads plain; a question with one answer
      is not asked; the pile's unknown card can never be taken. `PlanAsTalkTest`,
      `WholeRulesFilmTest`
- [x] 13.3 `Rehearsal.fresh` and `pileUnknown` tag every card the plan draws or deals with
      the turn it arrives on; a blind throw rehearses as the card coming back with a penalty
      card; a ghost `Frame` carries its own `fresh`/`pileUnknown` and its `turn`, and merged
      throws keep them (`tossedTogether`). `TossedTogetherTest`
- [x] 13.4 `Board` is a pager: `pages` (a sentence per turn), `transport` with a stop per
      turn and "lands", stops that jump and two films (replay, play-all), `watchable` for the
      runner, `answers` for the row under the sentence, `fresh`/`pileUnknown` for the felt.
      `Slot` says whether a word is open, asked or on offer; plain is none of them.
      `TransportTest`, `TurnBuilderTest`, `PlanBoardTest`
- [x] 13.5 The bots propose only rules-true steps and never a blind throw. `BoardProposalsTest`,
      `LaneAnswerTest`

### The screen (`composeApp`)

- [x] 13.6 `PlanFelt`: five fixed rows — a `HorizontalPager` of turns and the lands page, the
      answers row (answers, seats, the turn's news, or the hint), the stops with ▶ ▶▶ ■, Agree —
      at one height; boxed words on `Rail.chip` with a 3:1 outline, the asked word on
      `Rail.asked`, offers dashed, facts plain and untouchable; mini cards drawn as the felt
      draws them; the rank grid inside the page while a rank is asked. `PlanAsTalkScreenTest`,
      `PlanBuilderTest`, `PlanOnTheFeltTest`, `TouchTargetTest`, `RailFitsTest`
- [x] 13.7 The felt: no band above it while the plan is open; a card the plan has not dealt yet
      is rose (`CardState.arrived`) with the turn in its corner and a question mark on its
      face, on the felt and on the pile; the switch wears whoever changed your turn while the
      plan was closed (`PlanSummary.changedBy`, held by the holder's `seen` lane).
      `CoalitionLineTest`, `PlanAsTalkScreenTest`, `ContrastTest`, `ScreenContrastTest`
- [x] 13.8 The film: a banner between turns naming the seat the turn passes to, held for
      `BANNER_MS`; ghosts at `GHOST_SLOW`; the felt follows the frames while the film plays and
      the rail keeps reading the live table (`tableAsShown(rehearsing)`); the runner parks the
      head as the last turn's cards land (`ghostPlayed`) so the end of a film never blinks back
      to the start table.
- [x] 13.9 `GameHolder`: "I'm ready" opens the plan on the member's own turn; the turn coming
      round no longer closes it; the film is asked for in pages. `PlanBuilderTest`
- [x] 13.10 Words: `saysWords`/`chipWords` for the new sentence, thirty strings retired from
      every locale and forty-four added and translated into nineteen. The live rail's plan row
      is one line of the plan's own words rather than a row of chips, so the log keeps its last
      line on a phone (`everySentenceTheCoalitionCanSpeakIsDrawnInTheLog`).

### Still open, from this pass

- ~~9.1 stands: everything above is held by tests and the goldens, and has not been touched on a
  phone.~~ It has now — §14 is what the phone said.
- ~~The bots do not yet plan a King's point-then-name or an Ace's victim; they propose put-downs,
  calls, trades and vouched throws.~~ **Done, and the ace half is settled the other way.** A bot
  now proposes a King it is known to hold — put down, called, pointing at the card the table can
  name whose leaving lowers the coalition's lowest hand most, with the trade attached when that
  card is a Jack or a Queen — and the pile's unplayed King on the round's first turn, which is
  the cheapest declaration there is. Both are costed against the same lowest hand as a trade and
  the lower wins, so a King is not preferred for being dramatic; the toss-in window it opens
  covers King *and* the named rank, which is what the engine does.
  **An ace has no victim to plan.** Once Vinto is called the caller's hand is frozen and out of
  reach, so every seat an ace can name is a teammate and all it can do is lengthen a hand the
  coalition is keeping short — so no proposal names one, whether as a turn's card or as the card
  a King points at. `BoardProposalsTest.noProposalEverAimsAnAceAtATeammate` holds it, with the
  same board and a nine in the ace's place to show the silence is about the ace.
  The report behind it — *"bots when they play in coalition use an ace against each other"* — did
  **not** reproduce: over 120 whole games (60 seeds × moderate and hard) no coalition seat aimed
  an ace in a final round and no coalition hand grew in one. The runner was already putting one
  down unplayed and swapping or discarding one it drew. What changed is that the rule is now
  keyed on the round (`hasNoVictimForAnAce`) rather than on a coalition plan happening to have
  been built, and `SelfPlayGateTest` fails on an aimed ace, so it cannot drift back.
- ~~The builder has no Queen look-only: two touches on a Queen make a trade.~~ **It has one, and
  it had no test.** The arrow between the two cards is the word: lit she trades, dim she only
  looks, and touching it flips the two (`Slot.toggle` → `TradeArrow`). The design's open question
  is settled there. Three things were wrong around it, each found by writing the test:
  the arrow was a switch with a **state and no name**, so a screen reader landing on it heard
  "on" and nothing about what was on — it says the clause it controls now, from the words the
  sentence already had, so no string was added; `TouchTargetTest` measured a planned turn with an
  **empty** board, where ▶▶ is disabled and therefore not a tap target at all, so the one state
  the button can be pressed in was never measured; and in that state it was **19dp across** on a
  411dp phone, because a row out of width takes it out of its last child. The stops take what the
  two buttons leave and scroll sideways inside it now, the way a long sentence does a row above.
  The plan goldens moved with it and were regenerated.
- ~~Reveals following their cards is proven for the local session; the remote session uses the
  same helper and has no test through the wire.~~ **Done.**
  `RemoteRevealsFollowTheCardTest` plays whole games through a real engine, sends each step over
  `ScriptedWire` as the room sends one — the watching seat's projected view and the reveals off
  `ReduceResult` — and checks the session's standing reveals against the engine's own answer by
  card identity. The helper was never the question: both sessions call `following`, and what
  differs is the wiring, which is what this holds.
- ~~The design page's legend still names the rose edge as `#B8607F`; it shipped as `#A04C6A` to
  clear 3:1 on the card.~~ **Corrected on the page** ("Plan as Table Talk", revision 5): the token
  is `#a04c6a` and the rose bullet now says why it is deeper than the one the page first drew.

## 14. Five reports from the phone

The build above went onto a phone and came back with five numbered reports and two screenshots:
*"1) during vinto round when I said I'm ready we must switch to plan mode 2) remove hardcoded
vinto call from bot 3 3) I cannot switch to turn 2 in plan mode 4) bot tide name jumped when
their cards became for some reason two rows - it should stay aligned to edge plus we should not
have jump at all as 5 cards must be displayed one row 5) a claim under this turn error displayed
in plan mode, why do we have some errors there"*. Each was reproduced by a test first, watched
go red on the code as it stood, and only then fixed.

- [x] 14.1 **Ready opens the plan, even over an open window.** The call's own card opens a
      toss-in window, and `noticed()` closed the plan for every open window in which the member
      could throw — including the one already open when they pressed Ready, so the plan shut
      the moment it opened. The holder now remembers which card was on the pile when the plan
      opened (`openedOn`) and steps aside only for a card that lands *after* that.
      `PlanBuilderTest.aWindowAlreadyOpenWhenThePlanOpensLeavesItOpen`
- [x] 14.2 **The rig is gone.** A debug-build rig made the last bot call Vinto on its first turn
      so the plan could be reached quickly; it shipped in the APK the phone had. Removed from
      `LocalGameSession`, `LocalGame`, `App` and `MainActivity`, with its two `DebugRig`
      twins and its test. The bots call Vinto when the search says so, and nothing else.
- [x] 14.3 **A stop turns the page and nothing turns it back.** The pager's collector was keyed
      on the board, so every new board restarted it; the restart read the page still on screen
      against the page the board had just moved to, and sent the head straight back. It is keyed
      on the pager alone now and reads the latest transport through `rememberUpdatedState`.
      `PlanAsTalkScreenTest.touchingAStopTurnsThePageAndNothingTurnsItBack`
- [x] 14.4 **A badge is worn, not laid out.** Two faces and "Joker" on one card are wider than
      the card, and a badge laid out inside the card's box widened the box until five cards no
      longer fitted the row — the hand wrapped and the whole seat re-pitched, plate and all. The
      badge sits in a `matchParentSize` layer and overflows to the trailing edge unbounded, so
      the card's own width is what the row measures.
      `ClaimsOnTheFeltTest.aWideClaimDoesNotWrapAHandOntoASecondRow`
- [x] 14.5 **A reveal follows its card.** A card turned face up for the table — a throw that
      missed, the card a King pointed at — is a `PublicReveal` at a *position*, and both
      sessions kept it there after the card had gone. A claim about whatever card slid into the
      place next then read as contradicted by a card no longer there, and the turn naming it
      wore "a claim under this turn has been proved wrong" for no reason anyone could see.
      `Reveals.following()` moves a reveal with the flights the move drew — to the seat a trade
      lands it in, away for a throw or a swap-out, and one place down for every card thrown from
      below it in a hand that closed up. `RevealsFollowTheCardTest` holds the local session
      against the engine's own answer, by card identity, over whole games; the line itself is
      by design (D13) and now appears only while the shown card still lies where it was shown.

## 15. The stops say the order instead of spelling it

Asked from a phone, after reading the row: *"instead of duplicating word turn I think better to
show beautiful arrows between turns"*, with a second proposal to drop the lands page since the
final table is on screen once turn three has played.

- [x] 15.1 **The lands page stays.** A turn’s page shows the table that turn *starts* on
      (`feltPosition()` returns `turn`), because those are the cards the composer aims at — so
      swiping to turn three cold shows the board before it, not the result. `focus.landed` flips
      it after the film, which is why the premise holds there and nowhere else. The arrival table
      has no other page it could live on, and the caller’s believed total is the one figure that
      cannot be counted off the felt, since their cards are face down and what is known comes from
      claims. Decided by the product owner on that reading.
- [x] 15.2 **The stops carry a numeral and an arrow, not the word.** A stop wears the turn’s digit
      in a circle — the mark a rose card already wears for the turn it arrives on (`RoseBack`), so
      a stop and the cards naming it read as one thing — and the stops are joined by the arrow the
      film draws between seats (D19). It is still “Turn 2, Dune” to a screen reader.
      This **reverses** `PlanBuilderTest`’s recorded reason for the word: *“‘1’ alone on a header
      row is a mark a player stopped to ask the meaning of”*. It is not alone — circle, face, and
      an arrow to the next — and the localisation half of that reason was already moot, since the
      rose tag draws a bare digit in every locale. The test carries the new rule and the why.
- [x] 15.3 **And it fixes 14.x’s width properly.** The scroll added when ▶▶ measured 19dp is gone:
      four stops and two buttons fit a 411dp phone once the word does, “Lands” is whole again, and
      `TouchTargetTest` still measures both buttons at a thumb. Treating the symptom was the
      wrong fix; the row was simply saying one word three times.
- [x] 15.4 **The settings golden could never be green twice.** It draws the build number, which is
      `git rev-list --count HEAD`, so it gained a digit with every commit — the same defect fixed
      for `HomeScreen` and missed here. `SettingsScreen` takes `build` as a parameter now and
      `ScreenshotTest` pins it, as the home screen’s already was.

