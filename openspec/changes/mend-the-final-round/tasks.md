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
- [x] 2.3 ~~Make a two-seat selection reachable from every pair of seats~~ — **not a selection
      fault**, and fixed by 3.3a. `tradeComposer` built from `spokenCards`, "every coalition card
      the table has been told about", so an unclaimed card was never offered and the felt could
      not answer a touch on it. Reversing that is 3.3a; this line closes with it
- [ ] 2.4 Draw an already-chosen card as chosen — lifted the way the table lifts a card, not a
      border — and verify `ScreenContrastTest` still clears AA in both themes
- [ ] 2.5 Keep the plan's sentence on screen: scroll it into view as it grows rather than
      letting it run under the edge, and verify `RailFitsTest` covers the longest sentence the
      composer can build at a doubled system font

## 3. The plan can say what the rules allow

- [x] 3.1 + 3.2 **One window, two reports — and no wire change after all.** Measured rather
      than guessed: a probe built the first coalition turn and the second and compared them. The
      first turn is *not* missing the toss-in row; `AddThrow` is offered on both. What is missing
      is the window the throws would answer. `Rehearsal.playTurn` plays a lane's step and *then*
      its `tossIns`, and `Lane.landing` returns what **that turn's own move** puts on the pile —
      so every throw in lane *n* answers lane *n*'s discard, and the one window with no lane at
      all is the one **already open when the plan is composed**: the caller's last card, still
      face up, which §1.1 proved a member may still answer during the confer. That is "toss-in
      for the first step", and it is also where a throw **queued before the call** belongs.
      The maintainer placed it on the **first player's turn**, shown only where such throws have
      actually been made. Which makes it smaller than the `Lane`/`CoalitionPlan` field I had
      assumed: a throw already made is a **fact**, not a plan step, so `Words.standingThrows`
      reads it off `view.activeTossIn.queuedActions` and says it. Nothing about `CoalitionPlan`
      moves, nothing can be edited away, and a member who throws while the plan is open sees it
      appear. Never the caller's own. The thrown card has already left the hand — it lives only
      in the queue until it is played — so the clause names who and what rank and no position.
      `PlanAsTalkTest.aThrowMadeBeforeTheCallIsSaidOnTheFirstTurnAndNowhereElse`
- [x] 3.4 The wire is unmoved by §3 after all, so there is nothing to verify: §3.3c added a
      `Question`, which is client state, and §3.1/3.2 turned out to need no plan field at all
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
- [x] 3.3c + 3.3d **The put-down's guess was the real gap, and it needed a question of its
      own.** `callOffer` returned null unless the table already knew the rank, so a card nobody
      had read could not be called on the way out at all. The first attempt reused
      `Question.Naming` at `Part.Called` and broke the King: **that path is already taken** —
      `stepAt(Part.Called)` is what the *called card* does, and a called King naming a rank is
      exactly that, while `rankAt(Part.Called)` is the guess. Two different things at one path.
      So the guess got `Question.Calling(seat, at)`, which is honest about what it is: a word on
      the put-down, not a step at a part. `callingTable` + `callRanks`, a `Says.CallIt(rank?)`
      whose null opens the rail, and a `Says.Called` for the call made — said **only** where the
      card is unnamed, because "puts down your Jack, calls it a Jack" is the same word twice.
      `PlanAsTalkTest.aCardNobodyHasReadIsCalledOffTheRankRailWithoutTheRanksThatDoNothing`.
      The filter is 3.3d and it lives in `callRanks` alone: a guess buys the card's action, and
      2-6 and the Joker have none, so offering them is offering a penalty for nothing. The
      **King** is the other way round — declaring your own 2 correctly *sheds* it, which is the
      whole point of naming a low card — so `declareRanks` keeps every rank, and each says so
      in its own doc comment. Four new words, translated into all 19 locales

## 4. Four smaller faults

- [x] 4.1 **The caller and their cards inside one gold edge.** The crown said who called; it did
      not say what that *means*, which is that the hand beside it is out of reach for the rest of
      the round. Read off the **view's** `vintoCallerId` rather than the seat's own flag, because
      that is the field the rest of the final round is decided from — `coalitionCards` skips the
      caller by it — so the ring can never disagree with what the plan will offer.
      A **colour** rather than a border that comes and goes, with the room reserved on every
      seat: a border that appears is a seat that grows, and a seat that grows re-pitches the hand
      beside it, which is the fault `SteadyPlateTest` exists for and was reported once already.
      `TheCallerIsDrawnUntouchableTest` on `callersEdge`; six goldens regenerated and looked at
      in both themes. The staged plan scene turned out to set `vintoCallerId` on the view and
      **not** `isVintoCaller` on the seat, so it had been photographing a final round whose caller
      wore no crown either; fixed, so the picture is now of the thing it claims to be
- [ ] 4.2 Stop drawing a declared King enlarged as though it had been played, and verify the
      case from the reports renders the card in its seat
- [ ] 4.3 Stop the plate moving when its seat throws a card in: the thinking mark comes and goes
      and the plate is meant to hold still (`SteadyPlateTest`), so extend that test to a
      toss-in rather than writing a second one
- [x] 4.4 **A swap into the empty place, and no new action for it.** The engine had no answer at
      all: `ActionValidator` asks for a position inside `cards.indices`, empty on an empty hand,
      so every swap was refused — and `handleSwapCard` reads `player.cards[position]` first, so
      one that got through would have thrown rather than refused. Position 0 and no other, and
      **no declaration**: a guess names the card that goes out, nothing goes out, so there is
      nothing to be right or wrong about and a penalty for it would punish nothing. Nothing lands
      either, so no window opens — the turn ends the moment the card is placed, said by marking
      every seat ready so `shouldAdvanceTurn` moves it on inside the same `reduce`; the window's
      *ranks* are left untouched, because they describe the pile's top and that has not moved.
      No new `GameAction`, so the wire's vocabulary is unmoved, and the parity corpus is green
      without a fixture changing — a validator that refuses *less* can never reject a recorded
      action, and the handler's new branch is unreachable for a hand that has cards.
      `AnEmptyHandStillTakesItsTurnTest`, then the table above it: "Swap Cards" opened a question
      whose answer was an empty list of taps, a screen with nothing on it and no way forward, so
      with no cards the question is skipped and one button says what happens — `Label.KeepIt`,
      `AnEmptyHandKeepsWhatItDrawsTest`, one more word in 19 locales

## 5. The two King lines

Explanations first. Neither is a fix until it is understood, and one of them may be the
engine rather than the bot.

- [x] 5.1 **Explained by replaying it, and the engine was right.** The recording was read into
      `shared/bot`'s reports as `a-kings-won-jack-was-put-down.json` and replayed with the state
      printed either side of the declaration. Tide threw a King into a window, pointed it at the
      person's third card and declared "Jack" — **correctly**: the Jack left that hand, five
      cards became four, and the engine handed Tide the Jack to play (`pendingAction` a JACK,
      `AWAITING_ACTION`, a target type set). So `DECLARE_KING_ACTION` followed by `CONFIRM_PEEK`
      is not the wrong-declaration branch after all; it is the bot answering "put it down
      unplayed" to a card it had just won.
      `BotRunner` abandons a pending card at `choosing-action` outside a toss-in, because a card
      stranded by a window that opened over it arrives looking exactly like that and cannot be
      played from where it sits. A card a King has won is the opposite — and `setupKingTossIn`
      has already taken that King off the queue, so `resolvingATossIn` is false and the guard
      fired. `PendingCardOrigin` tells them apart: stranded cards come off the **deck**, won ones
      out of a **hand**. `ReportedGamesTest.aJackWonByAKingsCorrectDeclarationIsPlayed`, red on
      all five seeds before the fix
- [x] 5.2 **Explained, and it was not merely long — it cost Ember the round.** Replayed from
      `a-king-declared-an-opponents-three.json`. Ember held `[3]`; Tide held `[3, K]` and
      **knew only the King**, so Tide could never have thrown that three itself. Ember's King
      named it, correctly, and Tide went from three points to nil for nothing. Ember then threw
      its own three and called Vinto into a tie it had just created — and a tie pays the caller
      **+2** where a win pays +3. The maintainer's instinct that the long way round was pointless
      was right, and it was worse than pointless.
      The rule is a domination rather than a preference, and it lives in `MoveGenerator.kingMoves`
      where a move that can never be better simply is not offered. A correct declaration does two
      things — removes the named card and hands over its action — so for a **two through a six**,
      whose action is nothing, naming an opponent's copy differs from naming your own in exactly
      one way: they shed instead of you. The window opens on the same rank either way. Two
      exclusions carry their weight: an **action card** stays on offer in any hand, because its
      action is real value and is how a King wins somebody's Jack (5.1); and a **Joker** is the
      rule backwards — worth minus one, so removing it *raises* the total it sits in and an
      opponent's is the one to name.
      `ReportedGamesTest.aKingNamesItsOwnLowCardRatherThanHandingAnOpponentTheDiscard`, red on
      three of five seeds before the fix
- [x] 5.3 **It is the queue, and it took an engine rule, a bot rule and four corpus tails.**
      The first reading was wrong and a change was written and reverted: `tossInAction` does
      return the throw before it reaches `shouldCallVinto`, and the one card it holds back — a
      Joker, worth **minus one** — is held back on purpose. The maintainer then said exactly
      what they saw: *"bot played card, then toss in, then called vinto and then played toss in
      cards he tossed in before"*. The gap is one pass later. Once the bot **has** thrown, its
      card is out of its hand and in `queuedActions`, `positions` is empty, and nothing stopped
      it calling with its own throw still unplayed.
      A thrown action card lives nowhere but that queue until it is played, so the caller has a
      card in flight and a hand that has not finished moving — and the final round's rule that
      nobody may touch the caller's cards starts biting halfway through the caller's own action.
      Vinto is declared at the *end* of a turn and the window is that end.
      `ActionValidator.requireOwnThrowsSpent` refuses it, `BotRunner` applies the same test so a
      bot never proposes a call the engine would refuse, and `TheCallWaitsForTheCallersOwnThrow`
      holds both halves: refused while owed, and landing once the throw has played. Only the
      **caller's own** throws — another seat's are owed their action too and still get it.
      Four corpus recordings hold that exact sequence, and all four are among the six whose
      tails were already regenerated on 2026-09-15, so **no TypeScript evidence was at stake**.
      Retailed: 13,988 actions now, 13,781 TypeScript's and 207 this engine's, with the first
      differing byte in each file being the `CALL_VINTO` itself — stronger than last time, where
      the call's own hash moved. `RegenerateCorpusTailsTest` is the tool the last two
      regenerations did not leave behind (`-Pcorpus` to report, `-Pcorpus=write` to rewrite); it
      copies the head rather than re-encoding it, recomputes every kept hash first, and refuses
      a file whose every action is still legal.
- [x] 5.4 **Regenerated, and the ladder got sharper.** The mixed table is the one that ranks the
      difficulties — a homogeneous table's mean hand says the table was easier to sit at, not that
      the bot was better — and there **moderate's mean hand went 10.33 → 6.91 and hard's
      9.54 → 8.33**, both better, while easy's went 13.66 → 15.58. Easy forgets on purpose, so it
      has the fewest known cards for 5.2's rule to bite on and now loses more ground to the seats
      above it, which is what a difficulty ladder is for. Games got shorter at every difficulty
      (−20.7, −8.1, −19.4 actions), which is the reported complaint showing up as a number. The
      round-points column moved both ways and twelve games is too few to read it, so nothing is
      claimed from it
