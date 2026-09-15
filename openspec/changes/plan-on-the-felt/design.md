## Context

See `proposal.md` — Why. The short version is that nothing in the model is missing: `lanes` is
already an ordered list of turns, and `rehearse(view, plan)` already returns one ghost `Frame`
per turn, in order, built by transforming the `PlayerView` rather than by reducing a
`GameState`. That last detail is a hard constraint on everything below — a client holds no
`GameState` and must not acquire one (design R1), so every ghost this change draws is still a
view transformation.

Two other constraints shape the approach:

- **The stage owns the felt.** `CardStage` plays `Frame`s from an `AnimationQueue`, drains a
  backlog rather than queueing it, and already carries `rehearsing` plus `unlessRehearsing()`,
  which drops every tap while ghosts are up. The plan mode is that flag, promoted.
- **`Table` is the rail's vocabulary.** `TableModel` returns a `Table` (prompt, choices, taps,
  detail, aim) per state, and `Board` is a mode inside it. Moving the board to the felt means the
  felt needs the vocabulary the rail had — which is `taps: Map<CardRef, Move>`, already the shape
  the felt uses for every other question the table asks.

## Goals / Non-Goals

**Goals:**

- One surface for the plan: the table, with the rail reduced to words and buttons about the
  selected turn.
- An interaction contract precise enough to test: what a gesture on a card means is a function of
  the mode and nothing else.
- Reuse `rehearse()` frame-for-frame rather than growing a second way to draw a hypothetical.

**Non-Goals:**

- No new step kinds, no conditionals, no plan history/undo beyond what `PlanEdit` already allows.
- No second rendering path for "ghost" cards: the same `CardFace` and the same choreography, with
  the stage marking them.
- No change to how a plan is agreed, or to the room's copy of it.

## Decisions

### D1 — Plan mode is an explicit mode, not an overlay

The felt cannot show live cards and ghosts at once without lying about one of them, and the
stage already has to know which it is drawing (taps, the rehearsal banner, the animation queue).
So the plan is a mode with a visible control and a visible way out, rather than a sheet floating
over a live table.

*Alternative considered:* a bottom sheet over the felt, as the score sheet does. Rejected — the
plan's whole content is *where cards go*, and a sheet covers the seats the cards are going
between, which is the half that makes it readable.

### D2 — `unlessRehearsing()` becomes a router, not a mute

Today: `if (LocalStage.current.rehearsing) ::noMove else this` — every tap dropped. The guard
exists because "a tap on one would be a move on a table that does not exist", and that reasoning
survives; what changes is that a tap in plan mode now has a legitimate meaning.

The replacement routes by mode rather than dropping:

| stage mode | a tap on a card resolves through |
| --- | --- |
| live | `Table.taps` → `Move.Send(GameAction…)` |
| plan | `Board`'s composer → `Move.Plan(PlanEdit…)` |

`Move` is already a sealed type with both `Send` and `Plan` cases, and `PlanEdit` already goes
through `CoalitionPlan.edited` — the one door both sessions call. So no new path into the engine
is created, and the invariant "a tap in plan mode never dispatches a `GameAction`" is checkable
by construction: the plan branch cannot produce a `Move.Send`.

*Alternative considered:* keep the mute and put the composer in the rail. Rejected — that is
today's design, and it is what the report is about.

### D3 — The selected turn is client state, not plan state

Which turn a member is looking at is not part of the plan and must not travel: two members
reading different turns of the same plan is normal, and a shared cursor would fight. `Board`
gains a selected index supplied by the screen; `CoalitionPlan` is untouched.

### D4 — The timeline is two sources joined at "now"

Behind the cursor: the session's `log` (`List<Say>`), which already narrates what happened and is
already translated. Ahead of it: `lanes`. Nothing new is recorded to build it, and the past half
stays correct for a spectator or a rejoining client because the log is what the session replays.

*Alternative considered:* a dedicated plan history in `CoalitionPlan`. Rejected — it would put a
UI concern on the wire and in the room's state, and it would duplicate the log.

### D5 — A drag is the edit; select-then-select is the same edit for those who cannot drag

**Decided by the product owner, against the first draft of this document**, which argued for
tap-then-tap on parity grounds. The argument for dragging is that the plan is a picture of cards
moving, and the gesture that says "this card goes there" is carrying it there. Two conditions
make it work here that would not hold on the live table:

- **Nothing scrolls in plan mode** (D9), so a drag has no scrolling parent to fight — which is
  the usual reason drag-and-drop is a poor fit on a phone.
- **A drop can be validated before release.** The composer already knows which destinations are
  legal for the selected turn, so an illegal drop is refused by not highlighting, not by an error.

What a drag must not cost is reachability. A drag is unavailable to a screen reader, to a
keyboard and to a switch device, and `mobile-app`'s shipped accessibility requirement holds every
card and control to exposing semantics. So the same edits are also reachable as select-then-select
— **the same `PlanEdit`, not a reduced one** — and the tests assert both paths produce identical
plans. This is not a fallback that may lag behind; it is the same door.

*Alternative considered:* drag only, on the reasoning that the accessible path is unused. Rejected
— it would knowingly break a requirement this app already ships, in the one round that cannot be
skipped.

### D5a — Long-press is not required to start a drag

On the felt in plan mode there is no competing gesture: no scroll, no tap-to-act, no swipe. So a
drag begins on movement rather than after a hold. A long-press delay is the tax paid for
disambiguating against scrolling, and there is nothing here to disambiguate against.

### D6 — A step that cannot be drawn is not drawn

`rehearse()` already returns no frame for a lane whose step names a card that is not there
("a rehearsal of a broken plan would be a picture of something that cannot happen"). The felt
keeps that rule and shows the turn as unplayable rather than inventing a picture — which is also
what a `Lane` whose `step` is null gets, one empty numbered turn.

### D7 — Sheds and nods go to the seat plates; the header keeps only the plan control

Neither is a turn. A shed belongs to a seat ("Tide will throw in a 3 if one lands"), and a nod is
about a member, not about a step — the plates already carry per-seat badges. The header line the
retired rows free is not refilled (D12).

**Amended 2026-09-15: the countdown goes with them.** This decision kept it as "the one thing up
here that moves", and moving is exactly what made it a problem: it exists only while the round is
final, so the band under the header appeared when somebody called Vinto and disappeared when the
hands went over, shifting the felt under the player's thumb both times. Reported from a phone, and
the product owner's call is that the count is not worth a row that comes and goes. The header is
the plan control and the three glyphs; there is no band at all.

### D8 — `Lane.suggestion` shows only on the selected turn

A bot's "would rather" is a second, dimmer ghost. Drawing every lane's suggestion at once puts up
to three alternative futures on one felt; scoped to the selected turn it is a legible "or this",
adopted with one tap as an ordinary edit.

### D14 — A transport with detents, not a free scrubber

**The product owner asked for a movie: rewind, with pause.** The first draft of this document
had a stepper (previous/next turn), which is a different object, and the transport is the better
instinct — a stepper never shows the plan as one motion, and after changing a step the thing you
want is "run that again", which a stepper has no gesture for.

What the transport must not be is *continuous*. The film is a few seconds long and has four
frames worth stopping on — the table now, and after each of at most three turns. A free scrubber
over four states is a control with more resolution than its data: on a phone every drag
overshoots, and nearly every position it can reach shows cards in mid-air, which is the one
moment a table cannot be read *or edited* — a card halfway between two seats is at no position,
so there is nothing to drop onto and nothing to drag.

So: run, halt, and move in both directions, with the track detented at the turn boundaries and
coming to rest on one. Editing wakes when it is at rest (D5's drag needs a settled table under
it) and sleeps while it runs. The viewer gets the movie; the composer gets states.

*Alternative considered:* a true free scrubber, faithful to the movie idea. Rejected for the
resolution mismatch above — the fidelity buys positions nobody wants to stop on.

*Alternative considered:* no transport at all, the plan looping continuously while you edit.
Rejected: a table that never settles is hard to read and harder to aim a drag at, and it would
make D5's drop targets move under the finger.

### D9 — Plan mode presents no control that acts on the round

The plan is a replay of something that has not happened; a control that would act on the real
round has no meaning while it is on screen, and leaving one there is how a player ends up drawing
a card they meant to plan. So the rail's game controls are absent in plan mode, not disabled —
a disabled control still says "this is where you would do that", which is the wrong sentence.

What remains is the scrubber, the composer, and the way out. This is also what makes D5's drag
cheap: with nothing to scroll and nothing else to tap, a drag is unambiguous.

### D10 — The replay ends in the state the plan arrives at

Stepping past the last turn shows the hands the plan produces and the verdict over them. The
data is already there — `rehearse()`'s last frame carries the view the plan leaves behind, and
`PlanOutcome` scores it — so this is a screen for a value that already exists rather than new
arithmetic. It is the reason to read a plan at all: a plan is judged by where it lands.

### D11 — The caller sees the plan and cannot touch it

Two facts settle this. The plan is **already sent to every seat** — `Envelopes.kt` says "the
board sent back to every seat" and does no per-seat redaction — so the caller's client holds it
today and only declines to draw it. And at a real table the coalition confers **within earshot**
of the player it is planning against; the rules license them to "work together and share
information", not to do it in secret.

So the caller's screen draws the plan read-only. The distinction is between the window and the
door: `editPlan` already refuses the caller ("the caller has no coalition to plan with"), and
`inACoalitionFinalRound()` already excludes them from the confer window. Nothing about seeing it
opens either.

*Consequence worth stating:* the coalition can no longer plan a bluff that depends on the caller
not knowing. That is the physical game's constraint too, and the bots already plan without
modelling a hidden channel.

*Alternative considered:* redact the plan out of the caller's envelope. Rejected — it makes the
app less like the game it is a client for, and it would be a wire change in a change whose whole
claim is that it touches no wire.

### D12 — The app shows the numbers and does not pronounce the verdict

`PlanOutcome` carries three values — the coalition's best hand under the plan, the caller's
believed total, and how many of the caller's cards nobody has spoken about. The plan view shows
all three, at the end of the replay, beside the hands themselves. It does **not** say "wins" or
"falls short" anywhere.

This reverses `PlanOutcome`'s own reasoning, and the reversal is narrow enough to state exactly.
That comment — "a readout that made the player do that comparison themselves would be a readout
that gets ignored at the one moment it matters" — was written for a **one-line strip on the live
table**, where a player is doing something else and a bare pair of numbers would slide past. It is
right about that strip, and this change deletes that strip.

In a plan view the situation is inverted: comparing is the act the player came for, every hand's
total is already on screen, and the comparison is two numbers. Stating the verdict there would do
something worse than being ignored — it would **grade every candidate plan**, and a coalition
round whose whole pleasure is three people arguing about what to do becomes dragging cards until
the app says WINS. The numbers inform the argument; a verdict ends it.

The `unseen` count travels with them for the reason `PlanOutcome` gives: "a believed total stated
without saying how much of it is a guess is a number pretending to be information."

*Alternative considered:* keep the verdict but only inside the plan view. Rejected by the product
owner — anyone can do the comparison, and the app doing it is the part worth losing.

### D13 — A broken step is news on the felt; a re-anchored one is silence

`PlanHealth` already draws the distinction and this change must not lose it: `REANCHORED` (the
card moved, the step followed) is repaired without a word, because the table watched it happen;
`BROKEN` (a reveal contradicted a claim the step stands on) is never repaired and must be said.

Today that signal rides on the rail rows this change retires — `ControlPanel` marks a broken lane
and `PlanBoard` sets a detail line when any lane is broken. Both disappear with the rows, so the
felt takes it over: the affected **turn** carries the mark, in the replay and on the scrubber's
stop, so that it is visible from wherever the plan is being read.

Agreement is deliberately **not** reset by a break. `CoalitionPlan.agreed` resets on an edit,
because an edit makes the agreed plan a different plan — but a claim being disproved changes the
world, not the plan, and invalidating everyone's yes every time the round teaches the table
something would make agreement unholdable in the round that needs it most. The mark is the signal;
what to do about it is the coalition's business.

### D15 — The plan says the whole of the rules, and a throw-in is part of its turn

**Decided by the product owner, against the first draft of this section**, which proposed
keeping the shed as a standing promise and giving it an action. The plan stands in for what a
coalition says at a physical table, and what it says is a sequence of everybody's actions in
the order they happen: *"you play this Queen, then I throw in my Queen and play mine"*. A
promise beside the turns can say neither *then* nor *and play mine*, and the order of two
throws on one turn is the order their actions resolve — which the engine takes from the order
people throw, so it is the coalition's to plan.

So a lane carries its throw-ins ([Lane.tossIns]), each with what the thrown card does, and the
step vocabulary covers every action a card has: a look, a trade, a King that points at a card
and plays that card's action, an Ace that names who draws. The door holds each to the rank that
plays it and keeps every one of them off the caller's hand, as the validator does live.

The cost is on the record: a step is a polymorphic tag inside a message, an older build cannot
skip one it does not know, and the wire went to version 4 with the floor at 4 (`PROTOCOL.md`).
The plan's vocabulary is frozen per version beside the messages' so the next growth is a bump
by construction rather than by memory.

*Alternative considered:* `Shed.then` on the standing promise. Rejected — it could not say
order, could not say *then*, and left two ways to say a throw.

### D16 — The turn is a sentence

The rail draws the turn as the words a person would say it in, one chip per word that names a
decision, and touching a word opens the question that word answers. The open questions of a
turn are words too — *which pile?*, *and then?*, *which two cards?*, *who draws?* — drawn where
their answer will go, so the shape of a turn shows before it is decided. A throw-in is a clause
of its own, *then Tide throws in a five, and looks at …*; what is on offer — another throw, a
call — is a dimmer word at the end.

Nineteen languages are the reason each word is its own resource and the order of words is the
model's: a translator sees whole phrases, and the sentence reads in the order a turn happens
in every locale rather than in one locale's syntax. A row of six drawn marks was tried first
and two people could not say what any of it meant.

### D17 — The plan is information, never a control

The live rail keeps the ordinary turn's buttons whatever the plan says, with one line under
the prompt saying what the coalition agreed about this turn. Nothing is pre-armed and nothing
re-plans: a good draw is the player's to judge, as it would be at a table. "I'm ready" lands on
the live table, and a table that plays straight on from what it has heard declared, never
opening the plan, is using the app as intended.

Plan mode and the round's controls are never on screen together (D9), which has one cost: a
card lands that this seat could throw in on, or the turn comes round, and the plan hides the
buttons for it. So the plan steps aside for exactly those two moments — closes, parked where it
was — and is one switch away again after.

*Alternative considered:* keep "Do as planned" as a one-tap convenience. Rejected by the product
owner: a button the plan arms is the plan acting, and the plan must only ever say.

*Amended by D18:* "I'm ready" opens the plan after all, and the turn coming round no longer
closes it. The reasoning above about a button the plan arms stands whole; what changed is where
a member lands when they have finished talking.

### D18 — Five rows that never move, and a turn a page

**Decided by the product owner, from a design page of mockups** ("Plan as Table Talk", five
revisions) before any code was rebuilt. The rail under the felt is a pager — one page per
coalition turn, in the order the seats play, and a last page where the plan lands — and under
the page three rows that are always there at one height: the answers to the word being asked
for, the stops, and the one standing button. Nothing above the felt is spent on the plan any
more; the stops moved down beside the two buttons that play the film, ▶ for this turn again and
▶▶ for every turn to the end, which reads ■ while it runs.

**Boxed means touchable.** A decision is a boxed word, the one the rail is asking for wears the
coalition's blue, the next decision is a dashed box at the end of the line, and a fact —
"draws" with one pile to draw from, the card that was drawn, "and we'll see" — is plain and
answers no touch. A question with one answer is not asked. A card the sentence names is drawn
as the card on the felt is, so the two are read as one thing; a card that is not on the table
yet is rose, tagged with the turn it arrives on, on the felt and in the sentence, from the page
its turn lands on to the last. The turn on play stays editable, because the drawn card is the
news the plan turns on; a played turn locks. "I'm ready" opens the plan on the member's own
turn, since the plan is what the talking was for and the switch was the tap nobody found.

*Alternative considered:* all three turns on one rail, the transport in the band above the felt.
Rejected on a phone: three turns at once did not fit without scrolling, the band took a strip
of the four hands' height, and "which turn am I reading" was answered nowhere.

### D19 — The film is watched to see where a card goes

Between two turns of the film a card over the felt names the turn and the seat it passes to,
with an arrow from the seat before, and holds for a beat before the cards move; a ghost's every
movement and pause is half again slower than a real move's. Both asked for from a phone. A card
nobody has seen — a put-down's replacement, an Ace's forced draw, a blind throw's penalty card —
is rose on the felt while the film plays exactly as on the parked page, because the ghost frame
carries its own account of them. A throw-in of a card the table cannot vouch for is said to be
blind and rehearses as the card coming back with a penalty card: the fault real tables sometimes
choose on purpose, because a wrong throw shows the card.

Every colour is measured in both schemes: the rose at 6.2:1 off the felt with its ink at 8.5:1
on it, the asked word's blue at 5.4:1 under slate ink and a deeper blue at 7.5:1 under white on
paper, a boxed word's outline at 3:1 on its box. `ContrastTest` holds every pair.

### D20 — A reveal is a position, and it follows its card

A card turned face up for the whole table — the throw that missed, the card a King pointed at —
is public for as long as it lies where it was shown, and `PublicReveal` says where: a seat and a
position. Positions move. A trade carries the card to another seat, a throw or a swap-out takes
it away, and a hand a card has left closes up behind it. A reveal left where it was described
whatever card took the place next, and D13's "a claim under this turn has been proved wrong"
then fired against a card that was no longer there — which is what the phone showed, and what
nobody at the table could account for.

A client never learns a hidden card's identity, so it cannot follow the card the way the engine
could. It follows the flights instead: the same `Beat.Move`s the felt animates say where every
card went, and `Reveals.following()` moves each reveal with them — to the seat a trade lands it
in, away for anything that leaves a seat for the pile or the deck, and one place down for every
card thrown from below it out of a hand that shrank. Both sessions keep their reveals this way,
and `RevealsFollowTheCardTest` holds the local one against the engine's own answer by card
identity over whole games, so the choreography and the bookkeeping cannot quietly disagree.

The alternative — the engine carrying reveals by card identity in `PlayerView` — was rejected:
it would put a card id into a view whose whole point is to redact card ids, and it would be a
wire change for a defect that lies entirely on the client's side.

### D21 — The plan is read front to back, and an undecided turn still draws

A page shows the table its turn starts from, and that table is the one the turn before it
leaves. So a turn nobody has decided is the end of what can honestly be drawn: every page after
it would be a picture of a table whose making nobody has agreed. Reported from a phone, where
the first turn was empty, the second was planned, and stepping between them changed nothing on
the felt — because with no film for turn ①, positions 0, 1, 2 and 3 were all the same table.

Two halves, and the second is what makes the first bearable.

**A turn nobody has decided is not a hole.** The seat draws, and something goes on the pile; what
the plan cannot say is what either card is. So the honest picture is every hand exactly as it
was, with a card nobody can name on the pile — which is `Step.Bin`'s own effect, so no new
transform was needed, only the decision to play it (`Ghost.blindly`). The turn therefore has a
film, its ▶ is live, and its `pileUnknown` tells the turn after it there is nothing there to
take. And the card it draws is *on the felt*: rose in the slot under the deck, tagged with the
turn, like every other card the plan deals rather than finds (`Board.drawing`). "Draws, and
we'll see" is a card waiting to be aimed, not an absence — which is the whole of why an
undecided turn is still plannable. Any of the seat's own cards may be carried to the pile, known
or not, and the drawn card takes its place; no rank is claimed and none is assumed.

**And the pages after it are closed until it is decided.** `Transport.reach` is the first
undecided turn, or where the plan lands once none is left open; a stop past it is drawn dim
rather than dropped — the turn exists, it is simply not readable yet — the pager will not swipe
to it, and `Composing.at` clamps, so a lane a teammate opens under a reader brings them back
rather than leaving them on a page the plan no longer reaches. A turn already **played** counts
as settled by having happened, whatever the plan said about it, because the table it leaves is
the table as it is.

This is what closed **"we'll see"** as an answer. It cleared the lane, which is how a turn got
back to undecided — so the one deliberate "I don't know yet" was also the only control that could
throw away the rest of the board, the reader's own turn included. A decision is changed by
answering it again, which was always true; there is no longer a way to un-answer one. The
sentence still *says* "and we'll see" for a turn nobody has decided, because that is what is
true of it.

*Alternative considered:* every page reachable, on the grounds that a blind draw makes an
undecided turn well-defined and the chain is therefore never short. Rejected by the product
owner: reading the plan front to back is the point, and a page whose turn depends on a turn
nobody has agreed is a page about nothing. The cost is named — you cannot look ahead, and you
cannot plan your own turn before the turns before it — and accepted. It puts weight on the
bots seeding turn ①, which `BoardProposals` skips for the seat on play.

## Risks / Trade-offs

- **Drag on a small phone is fiddly**, and a mis-drop that silently edits the plan is worse than
  no drag. → A drop only lands on a destination the composer has highlighted; anything else
  returns the card and changes nothing, which is a stated scenario in the spec.
- **The accessible path rots** if only the drag is exercised. → Every composer test runs both
  paths and asserts an identical `PlanEdit`, so the non-dragging path cannot quietly diverge.
- **Dropping the verdict may leave players unsure whether a plan is any good**, which is the
  risk `PlanOutcome`'s comment names. → The numbers sit beside the hands they describe rather
  than in a strip elsewhere, and `unseen` is shown with them; if it turns out to be too little,
  adding the verdict back is one line in one view, whereas removing it later is a fight.
- **The caller now sees the coalition's intent**, which changes final-round play. → It is the
  physical game's behaviour and it is what the wire already does; what changes is that it stops
  being an accident. Called out here so it is a decision on the record rather than a surprise.
- **The mute becomes a router, and a routing bug dispatches a real move from a hypothetical
  table.** → The plan branch is typed so it cannot construct a `Move.Send`; a test asserts that no
  `GameAction` is dispatched for any tap while the stage is in plan mode, walked over a whole
  final round.
- **Ghosts and live frames racing.** A plan edit arriving from another member while ghosts are up
  must not be animated as a move. → The queue already distinguishes `ghost` frames; entering plan
  mode drains the live queue first, and an incoming plan change re-renders the ghosts rather than
  enqueuing them.
- **Three turns of ghosts is a lot of felt.** → One turn at a time is the mitigation, and it is
  also what was asked for; the whole-plan animation stays available as "play it through".
- **The felt is smaller than the rail was wide.** A four-card swap between two side seats on a
  small phone may not have room for its arrows. → Fall back to the seat plates as endpoints
  rather than the card positions; `LandscapeTableTest` and `CrowdedTableTest` cover the geometry
  that already exists.
- **19 locales follow every string change.** → The strings retired here are fewer than those
  added; `tools/check-translations.mjs` now runs at commit time and in `kmp-web`, so a missed
  locale fails before review rather than after.

## Migration Plan

In-app only: no persisted state, no wire field, and no room change, so there is nothing to
migrate and nothing to roll back but the build. A saved game mid-final-round reopens with the
same plan, because `CoalitionPlan` is unchanged.

The rail's board and the retired header rows go in the same commit as the felt board — leaving
both surfaces alive would mean two ways to edit one plan, which is the state most likely to
produce a divergence nobody notices.

## Open Questions

- ~~Whether "play it through" is worth a control of its own beside stepping.~~ Settled: the
  band's Play runs the whole plan from wherever the head is parked, and each turn carries its
  own replay at the head of its sentence (D16).
- ~~Whether a Queen's look-only — two cards looked at and not traded — needs a word in the builder,
  or whether two touches making a trade is what every coalition means by a Queen.~~ Settled as
  built: the builder asks "which two cards?" once and the answer is a trade, and the **arrow**
  between the two cards is where the plan says which — lit she trades, dim she only looks, and a
  touch flips the two. A look-only costs a touch rather than a question of its own, which is the
  right price for the rarer reading; a Jack has no arrow, because its action *is* the trade and a
  control that could only be refused is not offered. Held by
  `PlanAsTalkTest.aQueensArrowFlipsBetweenTradingAndOnlyLookingAndAJackHasNone` (both flips, and
  the Jack) and `PlanAsTalkScreenTest.touchingAQueensArrowTurnsHerTradeIntoALook` (the felt).
  Neither half had a test before, which is why this read as unbuilt.
