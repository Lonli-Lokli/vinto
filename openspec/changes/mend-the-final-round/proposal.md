# Change: Mend the final round

## Why

`plan-on-the-felt` put the coalition's plan on the table and the reports that followed are
what a person finds when they actually play with it. Three evenings, five recordings, and
thirteen separate faults — almost all of them in the final round, which is the one part of
Vinto where several things happen at once and the screen has to keep up.

They are not one bug. They fall into four kinds, and the kinds matter because they are fixed
in different places and carry different risk.

**The window could not be used.** A coalition member could not declare their cards at all:
*"I wasn't able to declare my cards! Game immediately started when I pressed share what I
know."* The call's own card leaves a toss-in window standing, and a throw is priced and timed
where talk is neither — so `tableBody` puts that question in front of the declaring table.
That made the toss-in the player's unavoidable **first** tap, and `dispatch` counted any
action but a declaration as "this seat has started playing, so it has finished talking".
Fixed, with the bots held to the same line so the throw can still be answered.

**And then the way out did not work.** *"start round button wasn't working."* The button
added for the two-step window is a `Move.Done`, and `CardStage.routed` passes only a
`Move.Quiet` in plan mode. `PlanModeTest` caught it and was relaxed rather than obeyed — the
model offered a move the router refused. Fixed, with the test moved onto the router.

**The plan cannot be built reliably.** Two cards from two seats cannot always be selected;
an already-chosen card does not read as chosen; the second step shows one seat's cards on
another's; a replay names the wrong exchange partner; the sentence is clipped off the bottom
of the screen. A plan that shows the wrong seat's hand is worse than no plan — it is a plan
the coalition agrees to and then cannot play.

**The plan cannot say things the rules allow.** Its first lane carries no toss-in step;
throws already queued when Vinto is called are missing from it, so it describes a turn
without cards already committed to it; and a card nobody has read cannot be given a rank,
which makes "swap it out and declare it a Jack" — and the whole of the King — unsayable.

**Four smaller faults, three of them presentation.** The caller is marked but not enough to
read at a glance or to say "you may not touch these". A declared King is drawn enlarged as
though it had been played. A seat's plate jumps when its owner throws a card in. And a seat
with no cards left cannot take the turn the rules still give it.

**Two bot lines nobody can explain from the outside**, which is the honest reason to look:
a King played the long way round to reach a total it could have reached by swapping, and a
King that declared a card correctly and then moved nothing. The second reads like an engine
fault rather than a bot one — the recording shows `DECLARE_KING_ACTION` with the right rank
followed by `CONFIRM_PEEK`, which is the *wrong*-declaration branch.

## What changes

- The confer window survives answering the throw the call left open — **done**
- The plan's start button reaches the session — **done**
- The plan's felt selects, marks and redraws the seat it is actually about
- The plan can say a toss-in on its first turn, carries throws already queued at the call,
  and can name a rank for a card nobody has read (King included)
- The caller is drawn as untouchable; a declared King is not drawn as played; a plate does
  not move when its seat throws
- A seat with no cards takes its turn: it draws and keeps
- The two King lines are explained, and whatever is wrong under them is fixed

## Non-goals

- Reworking the plan's shape or its vocabulary: `plan-on-the-felt` decided those and this
  change is its snagging list, not a second design
- Online-only work. Every fault here was reported from a solo game; the room shares the model
  and gets the fixes, but nothing here is about the wire
- Bot strength. `TournamentTest`'s baseline moves only if a fix to a stated fault moves it,
  and then deliberately, with the numbers in the commit

## Depends on

`plan-on-the-felt`, which built the surface every plan fault here is on.
