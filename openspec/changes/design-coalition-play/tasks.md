# Tasks: coalition play

> **Reviewed 2026-09-05.** Three independent reviews (design, implementation, coverage) went
> over this change. Nineteen ticks came off: the shared layer is built, corpus-neutral and
> tested, and almost none of it is *reachable by a player* — the talk channel reaches no
> screen, proposals reach no holder, the plan has no wire, and the confer window is a
> twenty-second stall with no button on it.
>
> Six confirmed defects were found and fixed: the worker never exported `sayEnvelopes` or
> `doneConferringEnvelopes` (online talk was dead); a caller's own bluff fed the coalition's
> planner; the confer window was bypassed whenever a **bot** called Vinto; a speaker's
> overlapping claims disputed with themselves; a reconnect minted a fresh window
> indefinitely; and `answerTo` could be used to route a move for a third seat.
>
> Three known-wrong things are deliberately left, because phase 3's future is undecided:
> `PlanOutcome.applied` and `Rehearsal.after` shift positions without renumbering claims, and
> `PlanHealth.follow` can retarget to the wrong card when one speaker has two identical
> claims. All three are in code with no caller.
>
> **Second pass, same day.** The screens the first pass said were untested now have seven
> Compose tests (`CoalitionScreenTest`, plus two in `RailFitsTest`), each verified by breaking
> the feature and watching it go red. And a seventh defect: **nothing outside a test ever
> constructs a `TableTalk.Proposal`**, so the accept/decline path — built, wired and now
> drawn — cannot be reached in the shipping app. `heard()` *is* called (a `LaunchedEffect` in
> `rememberHolder` collects `session.talk`); what is missing is anything that speaks one.
> Bots emit `GiveMe` by design (2.5), and no screen lets a person compose a move for somebody
> else, because that was phase 3's composer. See 2.2.
>
> What that left was a **one-way channel**: bots talk, people only declare cards. `Standing`
> closes the worst of it — it was written, rendered and translated into nineteen locales with
> no button anywhere producing one, and it answers the coalition's only real question (whose
> hand are we pushing?) in one tap. See 1.35.
>
> **Third pass, same day — the plan's wire, decided and built to the session.** The open
> question 3.2 left was settled in discussion rather than by the first plausible option: the
> plan is a **board of parts agreed as a whole** (design D7a) — anyone but the caller sets or
> clears any lane, adds or removes any shed, the room merges the part, and the coalition says
> yes to the board, with every edit resetting the yeses. Agreeing counts as done conferring.
> One pure door in `shared/shapes` decides what a legal edit is for the room and the solo
> session alike; the bots answer for their own lanes; the board rides on every `events`,
> `sync` and `joined` and there is no new server message. Built through to `GameSession`
> and held by five new suites. The screen followed in the same pass — 3.2f: the plan line on
> the felt's final-round banner, the board as a mode with "Agree" beside "Back", the three-step
> composer over the rail's own controls, and the viewer's lane written under the prompt and
> pre-armed as "Do as planned" — in twenty-seven strings across every locale. That is what
> closes 2.2. Two placements of the board failed existing tests before the third held, which
> is recorded on 3.2f and in D7a.
>
> **Fourth pass, same day — phase 1 closed.** The seven leftovers, each with a test: the
> reveal marks every claim right or wrong at scoring, the caller's included; a badge shows its
> speakers' faces and a dispute is drawn in the warning colours with both; the two halves of a
> pair wear a link; bots answer a contradiction from their confidence; a bot caller's silence
> has a test; the lesson explains the first dispute it sees and says a King cannot help the
> caller. Three whole-game suites stalled on the way and taught two rules, now in D3b: whether
> a bot acts must follow from state alone, and a claim is taken back by saying the card could
> be any rank, never by re-declaring the hand without it. The fallback that had a bot with no
> memory declare its real cards is gone.
>
> **Fifth pass, same day — phase 2 closed by adoption.** 2.5 as written cannot be done: the
> search plans as a seat with that seat's read cards as ground truth, so a line for a
> teammate's turn would read the teammate's private cards. What a bot can honestly say about a
> teammate's turn is a card movement, and the board is where a card movement goes. The bots now
> **seed** the board: the trade that most lowers the coalition's lowest hand, one lane at a
> time in turn order on one pooled picture, filling empty lanes only and stopping the moment a
> person has edited anything. Online, the bots' declarations now arrive *during* the confer
> window rather than after it, which the solo session had always allowed and the room had not
> — the planning happens in the window, and it needs the picture the bots are about to speak.

Three phases, ordered so each is usable on its own (design D14). Phase 1 improves local play by
itself — today a person watches three silent bots pool information they cannot hear. Phase 2 is
usable without phase 3, and answers whether people talk to their coalition at all before a
composer is built for them.

Every phase ends with the same verification block, because the corpus is frozen and the way to
find out you moved it is to run it (design D1).

## 0. The bug that has to go first

Not coalition-specific, but the final round is where it hurts most: exactly one turn per member,
so one dropped teammate stops the round. Test first, per the repository's rule on reported bugs.

- [x] 0.1 A **failing** room test: a coalition member's socket goes away, grace expires with
      their turn still to come, and the round does not reach `scoring`
- [x] 0.2 Make the engine's record agree with the room's on takeover — a seat a bot is playing
      is a bot to the engine — and reverse it on reclaim
- [x] 0.3 Stop the bot driver on *a seat a person is actually playing* rather than on *a seat
      holding a token* (`RoomCore.kt:1479`); a held seat is what a disconnected person's seat
      looks like
- [x] 0.4 The taken-over seat is marked as bot-played to every other seat while it is, live
      rather than only on reconnect
- [x] 0.5 0.1 goes green, and `RoomCoreTest` / `SessionClockTest` still pass

## 1. Talk

### 1a. Retire the leader

- [x] 1.1 Both live doors refuse `SET_COALITION_LEADER` through one shared `GameAction.retired`;
      the type stays decodable, `ActionValidator` keeps accepting it and `GameEngine.reduce`
      keeps applying it. **Not the validator** — `reduce` validates before dispatching, so a
      rule there refuses the 42 recordings that carry the action
- [x] 1.2 `GameState.coalitionLeaderId` documented as replay-only shape, and always `null` in a
      game dealt after this change
- [x] 1.3 Delete `BotRunner.coalitionLeaderAction`, so bot play is never held pending a human's
      nomination
- [x] 1.4 Delete `LEADER_MS`, `awaitingLeader` and the synthesized appointment from `RoomCore`;
      delete `leaderMsRemaining` from `PlayerView`. `leaderDeadlineEpochMs` is **kept**, always
      null: it is a persisted `RoomState` field and `VintoJson` sets `ignoreUnknownKeys = false`,
      so dropping it would make a deploy fail to read a live room's stored state
- [x] 1.5 Delete `coalitionTable`, `Ask.WhoPlaysOurHand` and the leader badge; the seat grid
      keeps its Ace use
- [x] 1.6 `mayDeclare` unlocks on the Vinto call rather than on a leader existing
- [x] 1.7 Rewrite the lesson's coalition beats — `Teaches.CoalitionLeader` goes; what replaces
      it says the lowest hand counts, so say what you have and push the low cards toward
      whoever is lowest
- [x] **Verify** 1.8 `CorpusReplayTest`: 50 recordings, 13,900 actions, every hash — including
      the 42 recordings that carry a `SET_COALITION_LEADER`, which must still reduce
- [x] **Verify** 1.9 A test that a live `SET_COALITION_LEADER` is refused whoever sends it,
      the Vinto caller included

### 1b. The claim widens

- [x] 1.10 `DeclareCards` claims `(seat, position)` rather than a position of the speaker's own
      hand; the speaker stays the `actorId`
- [x] 1.11 The caller may claim only their own cards, having no coalition to inform
- [x] 1.12 `ActionUtils`' three helpers cover the widened claim — travel on a watched swap, drop
      on a face-up exit, renumber after a removal — still `null`-safe so no recorded state
      materialises the field
- [x] 1.13 The claim UI: tap any card in the final round to say what you believe it is
- [x] **Verify** 1.14 `CorpusReplayTest` again — the field must stay unmaterialised
- [x] **Verify** 1.15 A claim naming a speaker other than the sender is refused

### 1b′. Partial knowledge (design D3a)

- [x] 1.15a The claim shape carries a set of positions, a set of ranks and a pairing: exact,
      unassigned, one-of-several (classes included), somewhere-in-hand, withdrawn
- [x] 1.15b Split `PlanCard.known` into `valueKnown` and `rankKnown`, and re-check **every**
      rank-consuming site in `CoalitionSearch` — an unassigned pair must never supply a King
      declare or a toss-in match
- [x] 1.15c `CoalitionPlanner` prices an unassigned pair from its candidate ranks rather than
      from `expectedUnseenValue`, and consumes both ranks from `unseenCounts`
- [x] 1.15d The "which way round?" control: two cards and two ranks give three answers — each
      pairing and **not sure** — with "not sure" one tap and not a mode; offered for exactly
      two cards
- [x] 1.15e The felt draws an unassigned claim as both ranks on both positions, visibly linked
      — **audit:** both positions get the same string; **nothing links them**
      — **done:** `Badge.paired` and a link mark on both halves (`CoalitionScreenTest.theTwoHalvesOfAPairAreVisiblyLinked`)
- [x] 1.15f Withdrawing a claim clears it, and teammates can see that it is gone
- [x] 1.15g Bots declare from `CardMemory.confidence` rather than from a flattened
      `believedOwnCards`: exact where sure, unassigned where the pairing is not, silent where
      memory has decayed past use
- [x] **Verify** 1.15h A test that an unassigned pair cannot be declared as a rank or matched in
      a toss-in, while its value still reaches the plan
- [x] **Verify** 1.15i `CorpusReplayTest` — the widened claim must still never materialise in a
      recorded state
- [x] **Verify** 1.15j A low-difficulty bot with decayed memory declares less rather than
      declaring wrongly
      — `CoalitionHumanMemberTest.aDecayedMemoryDeclaresLessRatherThanWrongly`, with the memory's
      grade pinned rather than hoped for: the real memory re-reads a bot's own known cards every
      time it thinks, so decay by turn count alone cannot be arranged. It also removed the
      **oracle fallback**: a bot whose memory had nothing declared its real cards off the
      engine's record, so the bot with the worst memory had the claims that were always right.
      It now says every card it read could be any rank — a vacuous claim belief reads past —
      which leaves the trace that it has spoken without saying anything

### 1b″. Attribution and disagreement (design D3b)

- [x] 1.15k `PlayerState.declaredCards` becomes a set of **attributed** statements rather than one
      rank per position owned by the card's holder; `@EncodeDefault(NEVER)` and null-when-empty
      unchanged
- [x] 1.15l `ActionUtils`' three helpers carry the speaker through a swap, a face-up exit and a
      renumbering
- [x] 1.15m A seat may replace or withdraw **its own** claim about any card and no other seat's
- [x] 1.15n Combining: intersect where consistent, mark **disputed** and union where the
      intersection is empty; a second claim from the same seat replaces rather than disputes
- [x] 1.15o A disputed card is unknown in rank on the same terms as an unassigned pair
- [x] 1.15p The table shows each claim's speaker without a tap, and draws a dispute distinctly
      — **audit:** **no speaker is shown anywhere** — `Table.badges` is a string per card and `composeApp` never reads `claims` or `sources`
      from a merely partial claim
      — **done:** `Badge` carries text, speakers, dispute, pairing and verdict; the felt draws the
      speakers' faces before the ranks, a dispute in the warning colours with both faces, and
      reads the whole thing to a screen reader (`CoalitionScreenTest.aClaimWearsItsSpeakersFaceAndADisputeWearsBoth`)
- [x] 1.15q Bots declare what they have seen of **any** seat's cards, and answer a contradiction
      — **audit:** bots declare about any seat; **nothing reads `Believed.disputed` or withdraws on contradiction**
      from `CardMemory.confidence` — withdraw where decayed, hold where not
      — **done, with a rule learned the hard way.** `BotRunner.contradictionAction` answers each
      new contradiction on the bot's own card once: standing is saying the same thing again,
      letting go is saying the card could be any rank. Both are declarations, because *whether*
      a bot acts has to follow from the state alone — a second runner with a different memory
      drives the human seat in `FinishesTest`, and the room rebuilds its runner every request —
      and "my hand minus that card" looped, since a declaration replaces only the claims it
      overlaps. Three whole-game suites stalled until both were true. Held by
      `aContradictedBotStandsWhereItsMemoryHoldsAndLetsGoWhereItHasDecayed`
- [x] **Verify** 1.15r Two partial claims about one card narrow it; two inconsistent ones dispute
      it and neither is dropped
- [x] **Verify** 1.15s No control anywhere resolves a dispute on a player's behalf
- [x] **Verify** 1.15t A disputed card cannot supply a King declare or a toss-in match
- [x] **Verify** 1.15u `CorpusReplayTest` — attributed claims must still never materialise in a
      recorded state

### 1c. The phrasebook

- [x] 1.16 The vocabulary as typed values in `shared/protocol`: claims, proposals,
      announcements, answers, assessments, `PlayFor`
- [x] 1.17 The talk channel alongside `GameSession`'s frames — `BotRunner` emits locally, the
      — **wired:** both sessions put talk into the log strip a screen already draws, and `rememberHolder` collects the flow into `GameHolder.heard`
      room relays remotely, so a screen still cannot tell which session it has
- [x] 1.18 Room-side: seat boundary on every message, a cap per window, refusal rather than
      relay for the excess
- [x] 1.19 Rendering through `Said.kt`, one string per message type
      — **wired:** `spoken()` converts talk to `Say` at the session, so it lands in the existing strip
- [x] 1.20 Bots speak: claims from memory, one unprompted sentence per turn plus answers
      — **wired both ways:** the room's driver collects `nextTalk` and the batch carries it in `ServerMessage.Events.said`; the once-per-turn mark lives in `RoomState.botTalkTurns`, because the runner is rebuilt every request
- [x] 1.21 Bots declare what they have seen of the caller, and the `knownCallerCardIds` pooling
      out of `opponentKnowledge` is **deleted** from `CoalitionPlanner`
- [x] 1.22 A bot caller says nothing
      — **audit:** implemented in `nextTalk`; **no test holds it**
      — `CoalitionHumanMemberTest.aBotCallerSaysNothing`: the caller holds the lowest hand, the
      one seat that would otherwise say "mine is low", and says nothing across every sentence
      the runner offers
- [x] **Verify** 1.23 `node tools/check-translations.mjs` — every new string in every locale
      — 532 keys, 19 locales, all ok. Four of the nineteen came back describing the speaker's
      *height* rather than their hand (`Sono basso`, `Ich bin niedrig`), one said "I use"
      where it meant "use me", and one named the discard **pile** — corrected against each
      locale's own already-translated `talk_*` sentence
- [x] **Verify** 1.24 A test that no talk message carries a natural-language string
- [x] **Verify** 1.25 A coalition plan built for a table with a human member reads the human's
      undeclared cards as placeholders, exactly as an undeclared bot's

### 1d. The confer window

- [x] 1.26 A bounded window after the call and before the first coalition turn: talk only
      — **wired:** `PlayerView.conferMsRemaining` says the window is open, `Ask.SayWhatYouKnow` puts the coalition's own table up with every card tappable, and `Move.Done` ends it. Solo has a window too, with no clock: online a *person* is being held, and locally the caller is a bot
- [x] 1.27 Closes early when every connected coalition member is ready
      — **wired:** both sessions can end it, and **acting ends it too** — a player who has started playing has finished talking, so the button is for the other case: ending it without acting, so the bots may go first
      (`playersReadyForNextTurn`'s pattern), on its deadline otherwise
- [x] 1.28 Skipped entirely when no person is in the coalition, so a solo game does not acquire
      a pause it never had
- [x] **Verify** 1.29 An all-bot coalition opens no window; a silent coalition is not held past
      the deadline

### 1g. Two-way at last

- [x] 1.35 A person can say where their hand stands — three buttons in the confer window,
      sending the `Standing` the phrasebook already had
      — the sentence, its `Say`, its renderer and nineteen translations of it all existed
      before anything could produce the value. The coalition is scored on its **lowest** hand,
      so which hand that is has to be settled before any of the rest is worth planning:
      declaring cards implies it, but only for somebody who has looked at enough of their own
      hand to add it up. Held by three tests in `HumanCoalitionMemberTest` — including one
      that every sentence the rail offers is spoken in the viewer's **own** name, which is the
      seat boundary inside the talk channel
- [x] **Verify** 1.36 The confer window's four choices are whole on screen at the doubled
      system font and on a tall phone (`RailFitsTest`) — four is one more than the rail had
      ever had to fit

### 1h. The screens, tested

- [x] 1.37 `CoalitionScreenTest`: the confer window says what it is for and how to leave it;
      every seat's cards are claimable while it is open; a standing claim is worn on the card;
      an away seat is marked; a suggestion addressed to the viewer draws both buttons and one
      addressed elsewhere draws neither; and **every** sentence the phrasebook can produce
      lands in the log strip as words the silent table did not already show
      — each verified by removing the feature and watching the test go red, because a Compose
      assertion that passes against an empty screen is the usual way this layer lies

### 1e. The reveal is the referee

- [x] 1.30 At scoring, standing claims are shown true or false — the caller's included
      — **audit:** `Table.brokenClaims` is computed at scoring and **read by nobody in `composeApp`**
      — **done:** `Badge.verdict`, set only where the card is face up at scoring, drawn as a tick
      or a cross in the warning colours (`HumanCoalitionMemberTest.theRevealRefereesEveryClaimTheCallersIncluded`,
      `CoalitionScreenTest.atScoringEveryClaimIsShownRightOrWrongTheCallersIncluded`)

### 1f. What the rules review turned up (design D13a)

- [x] 1.31 The Ace question in the final round says what an Ace does to a teammate and offers
      putting it down first, without removing a legal target
- [x] 1.32 The lesson teaches the coalition as it now is: claims (including "not sure"),
      — **audit:** claims, "not sure", shedding and the untouchable caller are taught; **disagreement is not, and nothing says a King cannot help the caller**
      — **done:** `Teaches.Disagreement`, said once the first time a dispute is on the table and
      pointed at the seat wearing it (`TeachScriptTest.aDisputeIsExplainedOnceAndPointedAt`); and the
      King's final-round beat now says the caller may not throw anything in, so a King only ever
      empties the coalition's own hands. Seven strings in every locale
      disagreement, shedding by toss-in, and that a King cannot help the caller — replacing the
      leader beats retired in 1.7
- [x] 1.33 Nothing in the channel blocks or nags: a player who says nothing plays the final
      round exactly as before
- [x] **Verify** 1.34 A silent coalition member's turn waits on nothing beyond the confer
      window's own deadline

## 2. Proposals

- [x] 2.1 A proposal names a move and the seat that would make it, and is never reduced
- [x] 2.2 A proposal addressed to the viewer is a one-tap move; accepting dispatches an ordinary
      — **corrected:** `heard()` *is* called — `rememberHolder`'s `LaunchedEffect` collects `session.talk` into it — and the two buttons are now drawn and tested (`CoalitionScreenTest`). What is missing is upstream: **nothing outside a test constructs a `Proposal`**, so `offered` is never set in the shipping app. Bots emit `GiveMe` by design (2.5); a person has no way to compose a move for somebody else, which was phase 3's composer. The receiving half is finished; the speaking half is the open question
      `GameAction` from the viewer's own seat
      — **and the answer (design D7a):** the plan is the speaking half. On a member's turn their
      own lane is what the offered slot shows, pre-armed when the draw makes the step legal —
      3.2f. Nothing has to construct a `Proposal` for that
- [x] 2.3 Declining is possible and visible to the proposer
      — declining is reachable and a bot's answer is broadcast. A bot still never *originates* a `Proposal` — it asks for cards with `GiveMe`, which is the only thing its plan can honestly say about somebody else's turn
- [x] 2.4 Bots evaluate a proposal with their own decision service, perform or decline, and say
      — **wired both ways:** `LocalGameSession.say` and the room's `sayEnvelopes` both route a proposal to the addressed bot, which answers with its own planner and makes its own move
      which
- [x] 2.5 Bots address proposals to teammates, read out of the line `CoalitionSearch` already
      — **audit:** bots emit `GiveMe` from a two-line heuristic — **not read off the search's line**, which the review showed is not possible without reading a teammate's private cards
      plans for that member's turn
      — **adopted (design D5, D7a).** The literal item cannot be done: the search plans *as* a
      seat, with that seat's read cards as ground truth, so a line for a teammate's turn would
      read the teammate's private cards. What a bot can honestly say about a teammate's turn is
      a **card movement** from the shared picture, and the board is where that goes: the bots
      **seed** the board — the trade that most lowers the coalition's lowest hand, one lane at
      a time in turn order, each built on the lanes before it — proposing their own lanes from
      their own picture and the first bot proposing a person's lane. They fill empty lanes only,
      stop the moment a person has edited anything, and only bother while a person is in the
      coalition, so nothing can loop and nothing is taken over. `GiveMe` now comes off the same
      evaluation. Held by `BoardProposalsTest`, a room test and a session test
- [x] 2.6 An accepted proposal pre-arms its owner's turn — aimed, one action to commit, every
      — **corrected:** pre-arming works in the model and is now drawn; unreachable because nothing speaks a `Proposal` (2.2), not because nothing listens for one
      — **reachable now (3.2f):** the viewer's own lane of the shared plan is what pre-arms the turn, as "Do as planned", found among the moves the table itself built
      other move still available
- [x] **Verify** 2.7 No control anywhere acts for another seat, in any configuration,
      single-player included — a test in the spirit of `ValidatorImpersonationTest`
- [x] **Verify** 2.8 A proposal never reaches `GameEngine.reduce`

## 3. The plan

- [x] 3.1 The plan shape: ordered steps, each naming the seat whose turn it belongs to, at most
      — **done (bf71c37).** The shape existed and the length rule was only a comment: `editPlan` accepted any lanes for any seat, the caller included. A lane now belongs to a coalition seat and a seat has one lane, which makes the length rule a consequence rather than a number — so there is no second place to update when the table size changes. Held by `PlanDoorTest`
      as many lanes as there are turns left
- [x] 3.2 One shared draft per final round, editable by any coalition member and by bots, last
      edit standing; room-side, never in `GameState`, discarded at scoring
      — **decided (design D7a) and split.** The plan is a board of parts, agreed as a whole:
      an edit names one lane or one shed and the room merges it, so two people working on
      different lanes cannot overwrite each other; agreement is to the combined plan, reset to
      the editor by every edit; a plan not agreed when the window closes stands as a
      suggestion. "Whole draft or patches" was the wrong question — a whole draft sent on every
      gesture loses the other person's lane whenever two messages cross, and the first cut of
      this item was exactly that
  - [x] 3.2a `PlanEdit` in `shared/shapes` — set a lane, clear a lane, add a shed, remove a
        shed — and one pure `edited` that both doors call: the caller and any seat outside the
        coalition are refused, a locked lane cannot be the target, one lane per seat, agreement
        reset to the editor. The `GameAction.retired` shape (design D2): a rule both doors must
        answer identically lives in neither. Held by `PlanEditTest`, fourteen cases on JVM, JS,
        Wasm and the iOS simulator. The door is also told who is on play, and refuses a fresh
        lane for that seat: locking is pacing's doing and runs after the fact
  - [x] 3.2b The room's door takes a `PlanEdit`, not a whole plan; `agreePlan` records a yes or a
        no, and a yes counts as done conferring — when the last connected member agrees, the
        window closes and the bots play
        — `PlanDoorTest` rewritten around parts: two members on different lanes both land, a
        locked lane and a turn in progress refuse, an edit resets the others' yes, the first yes
        marks a seat done and the last closes the window, a no is only a no, an unagreed board
        survives the deadline as a suggestion, and the edit spends the talk budget
  - [x] 3.2c Bots answer for their own lane on every edit that touches it, with the within-reach
        test `answerTo` already applies to a single proposal, on the shared picture plus their
        own cards; an empty lane is a yes; the answer goes out as talk
        — `answerForLane` and `botsAnswering` in `shared/bot`, held by `LaneAnswerTest`: a
        swap that lowers the lowest hand is a yes, one that raises it "leaves us worse", a step
        naming a card that is not there is a no, only the bot whose lane was set speaks and
        every bot's agreement is recorded regardless
  - [x] 3.2d The wire: `edit-plan` and `agree-plan` in; `plan` on every `events`, `sync` and
        `joined` whenever one stands; **no new server message** — an edit is answered the way
        `more-time` is, with an empty `events` per seat carrying the board and the bots' answers.
        Worker exports and the shim's two cases, which is where 1.17's first defect lived
        — `PROTOCOL.md` now lists them, and the four talk messages it had never listed either;
        its clocks table named the retired `leaderMsRemaining` and names `conferMsRemaining`
  - [x] 3.2e `GameSession.plan`, `editPlan` and `agreePlan` on both sessions; the local session
        calls the same `edited` and has its bots answer in-process
        — `RemotePlanTest` (the board comes off events, sync and a mid-round join; an edit goes
        out as one part and the copy waits for the room) and `SharedPlanTest` (a bot answers
        for its lane, the caller is refused, agreeing ends the window, the plan dies with the
        round)
  - [x] 3.2f The screens: the board with each part's last editor, the agreement row, "Agree"
        beside "Done" in the confer window, and the viewer's own lane in the offered slot on
        their turn — which is what closes 2.2
        — **built, with one correction to the wording above.** The board is a **mode**, not a
        fixture of the rail: drawn beside the prompt it starved the log strip, where the bots'
        answers land (`everySentenceTheCoalitionCanSpeakIsDrawnInTheLog` caught it), and on
        the foot it pushed the four confer buttons under the edge of the screen
        (`RailFitsTest`). So every final-round table carries one line on the felt's
        final-round banner — "No plan yet — tap to plan together", or how much is set and how
        many have nodded — and tapping it opens the board the way a claim opens the rank
        picker: lanes in turn order with their step in words and the last editor, the nods,
        "Agree" beside "Back" on the foot. A lane is a button where the door would accept an
        edit and a line of text where it would not, so nothing can be tapped and then refused.
        The composer is three kinds of step and no widget of its own: a swap is two taps on two
        hands with the caller's cards never on offer and the first card shown in the aim
        column, a declare is one rank off the rail, taking the discard is a button only while
        there is an action card to take. The viewer's own lane is written under the prompt on
        their turn and put first as "Do as planned" when the table's own controls already
        offer the move — found among them, so legal by construction. Held by `PlanBoardTest`
        (eleven cases), four screen tests in `CoalitionScreenTest` and three in
        `RailFitsTest`. Twenty-seven strings, in every locale
- [x] 3.3 A lane locks when its owner's turn begins; later lanes stay editable
      — **done (bf71c37).** The refusal only looked at the lanes that were *present*, so a locked lane could be deleted by omitting it — locking defeated by sending less. Every standing locked lane had to come back unchanged; with 3.2a the rule is simpler still, because an edit names one part: a locked lane cannot be the target of one. `ConferWindowTest.aLaneLocksWhenItsOwnersTurnBegins` had been proving this on a lane belonging to the **caller**: straight out of a call the caller is still the current player, because the window opens before the turn moves, so the test was encoding the hole. It winds on to a coalition member's turn now
- [~] 3.4 The composer's palette: declared claims, own read cards, the discard top — and the
      caller's cards structurally absent
      — **half, with 3.2f:** the caller's cards are never tappable and the door refuses a step
      that names them; a swap's cards are anchored to the standing claim where there is one.
      What is *not* done is narrowing the palette to spoken-about cards — today any coalition
      card can be named, which is honest (the plan is worth what the claims behind it are worth)
      and is what the readout prices as unknown
- [x] 3.5 The composer reuses the existing per-action targeting (taps, rank rail, seat grid)
      against a hypothetical state
      — with 3.2f: the same card taps a Jack is aimed with, the same rail a King declares on,
      the same aim column; against the view rather than a state, which is the correction D8
      made for the rehearsal and holds here too
- [x] 3.6 The ghost table after each step, by **transforming the view** — not by reducing. A
      client has no `GameState` and must not acquire one (design R1), so a composer that
      reduced would work locally and break online, which is the class of bug the `GameSession`
      seam exists to prevent
- [x] 3.7 Rehearsal: play the plan back as an animation via `choreograph(action, before, after)`
- [x] 3.8 The value readout — **computed in `shared/client` from standing claims, not from
      `CoalitionSearch.evaluate`**: the client does not depend on `shared/bot`, and the two
      agree because both read `believedAt`. `planOutcome` is done; the live update as the plan
      is edited lands with the composer
      — **on screen:** the open board carries `Board.outcome` and says it as one sentence —
      the two totals, whether that wins, level said as losing, and how many of the caller's
      cards nobody has seen — recomputed from the board on every edit; the felt's plan line
      carries the verdict word. `PlanBoardTest`, `CoalitionScreenTest`
- [x] 3.9 Decay — a step whose card moved re-anchors silently; a step whose claim a public
      reveal contradicts is marked broken, with everything downstream of it
      — **on screen now:** `GameSession.reveals` keeps what the round has turned face up, both
      sessions feed it, and the board reads the plan against it: a broken lane is drawn in the
      warning ink with a cross. A step naming an unspoken card — one's own, which the composer
      allows — used to read as broken from the moment it was made; it stands as long as its
      position does
- [x] 3.10 Detection off the `PublicReveal` stream, in the client; the engine never compares a
      claim to a card
- [ ] 3.11 A draw that beats the plan offers a re-plan rather than insisting on the agreed step
      — the re-plan is an ordinary edit through the same door (3.2a): unlocked lanes stay
      editable mid-round and any edit resets agreement, so this needs no second mechanism
- [x] 3.12 Copy for a broken plan that treats a wrong claim as the game working
      — `Detail.AClaimWasWrong` under the board's prompt whenever a lane is broken: "the game
      working, not a mistake"
- [~] 3.13 Bots edit the plan, replacing the step they own and saying why
      — **deliberately after 3.2c.** A bot that replaced a lane the moment a person set it would
      reset agreement in a loop; the bound on that is a decision, not a default
      — **half, with 2.5:** bots *seed* every empty lane, their own included. What is left is
      replacing a step after a person has set it, and saying why — the part that needs the bound
- [~] 3.14 **Toss-in intents** in the plan beside the turn lanes: "I hold a 7 and will shed it",
      and the proposal that sets it up — "put down a 7". No conditional language (design D13a)
      — **the intent half:** "Throw in" on the open board opens the rank rail and puts a shed in
      the viewer's own name; sheds are drawn on the board and only their owner takes one back;
      the readout prices them. The proposal that sets one up — a lane step "put down a 7" — is
      not a `Step` yet
- [x] 3.15 The toss-in risk is shown to the hand the coalition is pushing: a wrong one costs a
      card and bars that seat for the rest of the round
      — `Detail.ShedRisk(pushed)` under the shed rail: sharper for the viewer whose hand is the
      coalition's lowest as far as the table has been told
- [x] 3.16 The readout states an **outcome** — the coalition's best hand, the caller's believed
      total, how much of it is unseen, and whether the plan wins — with a level result shown as
      losing, since a tie pays the caller
- [ ] **Verify** 3.14 `ScreenContrastTest` — the rehearsal felt clears WCAG AA in both themes
- [x] **Verify** 3.15 The plan never appears in a round's recording; a talkative final round
      replays to identical hashes
      — `ConferWindowTest.aTalkativeFinalRoundLeavesNoPlanInItsRecording`: planned, agreed, played
      to scoring, and the recording's bytes carry none of the plan's fields; the recording's
      own replay is `RoomRecordingTest`'s
- [x] **Verify** 3.16 The composer offers no tap that would target the Vinto caller's cards
      — `PlanBoardTest.aSwapIsTwoTapsOnTwoHandsAndNeverOnTheCallers`, and the door refuses the step besides (`PlanEditTest.aStepMayNotNameTheCallersCards`)
- [x] **Verify** 3.17 The strip is usable in phone portrait, at the largest supported font scale
      — `RailFitsTest`: the confer window's four choices stay whole with a full board standing, and the open board's own two, at a doubled font on the test phone

## Every phase ends with

- [x] `./gradlew :shared:engine:jvmTest` — the parity gate, 50 recordings and 13,900 actions
- [x] `./gradlew detekt` — every module, `failOnSeverity = Info`, no baseline regeneration
- [x] `./gradlew :composeApp:jvmTest` and `:shared:client:jvmTest`
      — 282 → 291 tests, and the suite's **two long-standing reds are gone**, neither of which
      was drift. `VersionTest` read `versionName` with a regex that matched only a string
      literal; `b3c26e7` moved the value into a `val`, so it found nothing and failed with
      `expected:<null> but was:<1.0>` — a message that reads like the version drifted when
      what drifted was the test's ability to find it. It resolves one hop through the `val`
      now, refuses rather than returning null when it recognises neither shape, and covers
      `iosApp/project.yml` too, since a release ships both stores. `ScreenshotTest.theHomeScreen`
      drew the build number, which is `git rev-list --count HEAD` — so the golden gained a
      wrong digit with **every commit** and could never be green twice. `HomeScreen` takes the
      number as a parameter defaulting to the real one, and the goldens are regenerated pinned
- [x] `node tools/check-translations.mjs`
- [x] `:shared:bot:jvmTest` including `SelfPlayGateTest` — every proposed action through
      `ActionValidator`, every game to `scoring`
      — 189 tests green after the plan's wire landed
