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

### D7 — Sheds and nods go to the seat plates; the header keeps only the countdown

Neither is a turn. A shed belongs to a seat ("Tide will throw in a 3 if one lands"), and a nod is
about a member, not about a step — the plates already carry per-seat badges. The header line the
retired rows free is not refilled: it holds the countdown and the plan control, and nothing else
(D12).

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

- Whether "play it through" — the whole plan animated end to end, as `rehearse()` returns it
  today — is worth a control of its own beside stepping, or is redundant once stepping exists.
