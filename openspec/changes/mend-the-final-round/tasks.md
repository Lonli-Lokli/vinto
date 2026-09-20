## 1. The window, and the way out of it

Both were reported as the round being unplayable, and both are done. They are here because
they belong to this batch and because each is now held by a test that fails on the old code.

- [x] 1.1 Let a coalition member answer the throw the call left standing without ending their
      own confer window, and verify `HumanCoalitionMemberTest` asserts the declaring table
      comes up behind the throw rather than the round running
- [x] 1.2 Hold the bots to the same line, so a window only the person may answer is not a
      window that never closes, and verify the suite still drives whole final rounds
- [x] 1.3 Route `Move.Done` out of plan mode so the start button reaches the session, and
      verify the assertion sits on **`routed`** rather than on the table's choices —
      `PlanRoutesTheStartTest` fails on the old router

## 2. The plan's felt tells the truth about which seat it is about

The riskiest of what is left: a plan that draws one seat's hand under another's name is a
plan the coalition agrees to and cannot play. Model faults first, appearance second.

- [x] 2.1 + 2.2 **One fault, in `Rehearsal.locate`.** A step's card carries an *anchor* — the
      claim made about it — so a later turn can still name a card an earlier turn moved.
      `locate` followed it by walking **every** seat and taking the first claim that matched,
      and `sameClaim` compares who spoke, the ranks and covering — never whose card it was
      about. So one seat saying "King" about two hands leaves two claims it cannot tell apart,
      and `players` order decided. The caller was in that walk, which a plan may never touch.
      Both reports follow: the trade is applied to the caller instead of the top seat, so the
      top seat keeps its King *and* the claim lands on the side seat — a King on both.
      Fixed: the card's own seat first, then the coalition only, and only where the answer is
      unambiguous. Verified by `APlanNamesTheSeatItMeansTest` against `locate` itself, which is
      `internal` for the purpose the way `believedOnView` and `cardAt` already are
- [ ] 2.3 ~~Make a two-seat selection reachable from every pair of seats~~ — **not a selection
      fault.** `tradeComposer` builds from `spokenCards`, "every coalition card the table has
      been told about", so an unclaimed card was never offered. That is the decided rule the
      plan speaks only of known cards, and reversing it is §3.3; this line folds into it
- [ ] 2.4 Draw an already-chosen card as chosen — lifted the way the table lifts a card, not a
      border — and verify `ScreenContrastTest` still clears AA in both themes
- [ ] 2.5 Keep the plan's sentence on screen: scroll it into view as it grows rather than
      letting it run under the edge, and verify `RailFitsTest` covers the longest sentence the
      composer can build at a doubled system font

## 3. The plan can say what the rules allow

- [ ] 3.1 Offer a toss-in step on the coalition's **first** turn, as every later turn already
      has, and verify a test asserts the first lane's steps are the same set as the second's
- [ ] 3.2 Carry throws already queued when Vinto was called into the plan, and verify a test
      builds a plan from a state with a queued throw and finds it in the lane that plays it
- [x] 3.3a **A Jack's palette is every coalition card.** `tradeComposer` read `spokenCards` —
      only what the table had been told about — which is right for a step that *claims*
      something and wrong for a Jack, because a Jack is blind: what it moves is decided by where
      the cards are, not by what anybody said. So the commonest Jack of all, two unknown cards
      traded on position, could not be planned and the felt did not answer the cards a person
      tried to pick. Reverses design D7's "saying what a card is puts it on the palette", which
      `PlanBoardTest` now records as reversed and why; declaring still pays, for what it always
      paid — a named card is one the plan can price and can claim a rank for
- [x] 3.3b **The King already allows it.** `Asking.Point` offers any coalition card ("the King
      may name a card nobody has spoken about, which is a guess, and the rank comes afterwards")
      and `declareRanks` offers every rank, `muted` only changing the tone. Nothing to fix
- [ ] 3.3c **The put-down's guess is the real gap.** `callOffer` returns null unless the table
      already knows the rank, so a card nobody has read cannot be declared on the way out. Needs
      the rank rail `Question.Naming` already gives the King, with `declareRanks` generalised to
      write `Step.PutDown.guess` as well as `Step.Declare.rank`
- [ ] 3.3d **"Skipping 2-6" belongs to the put-down only.** A guess buys the card's action, and
      2-6 have none, so offering them is offering a penalty for nothing. A **King** is the other
      way round: declaring your own 2 correctly *sheds* it, which is the whole point of naming a
      low card. Filter the put-down's ranks; leave the King's alone
- [ ] 3.4 Verify the wire is unmoved by §3: a test asserts `CoalitionPlan`'s serialized shape is
      byte-identical to the committed sample

## 4. Four smaller faults

- [ ] 4.1 Draw the Vinto caller as untouchable — the seat and its cards inside one border — and
      verify a test asserts the border is present exactly for the caller and that the caller's
      cards offer no plan target
- [ ] 4.2 Stop drawing a declared King enlarged as though it had been played, and verify the
      case from the reports renders the card in its seat
- [ ] 4.3 Stop the plate moving when its seat throws a card in: the thinking mark comes and goes
      and the plate is meant to hold still (`SteadyPlateTest`), so extend that test to a
      toss-in rather than writing a second one
- [ ] 4.4 Let a seat with no cards take the turn the rules still give it — draw, and keep the
      card — and verify at the **lowest layer that exhibits it**: `ActionValidator` first, and
      only then the plan, since the plan cannot offer a turn the engine refuses

## 5. The two King lines

Explanations first. Neither is a fix until it is understood, and one of them may be the
engine rather than the bot.

- [ ] 5.1 Explain, from `2026-09-19-2200-report2.json`, the King that declared a card correctly
      and then moved nothing: the recording shows `DECLARE_KING_ACTION` with the right rank
      followed by `CONFIRM_PEEK`, which is the wrong-declaration branch. Verify against
      `ActionValidator` and the King handler before touching the bot
- [ ] 5.2 Explain the King played the long way round — drawn, played, an opponent's 3 declared,
      then its own 3 thrown in — to a total reachable by swapping the King in. Verify whether
      the search prices the line correctly or the rollout misprices the declaration
- [ ] 5.3 Stop the caller calling Vinto while holding cards it could still throw in, and verify
      the rule at the point the call is decided rather than in the rollout
- [ ] 5.4 Verify `TournamentTest` after §5: unchanged, or regenerated with the numbers and the
      reason in the commit
