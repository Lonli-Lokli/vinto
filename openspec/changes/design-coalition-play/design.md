# Design: coalition play

Decisions, and what each one is instead of. The requirements are in `specs/`; this is why they
are shaped that way.

---

## D1. The frozen corpus decides most of the shapes

Everything below is constrained by one fact: `fixtures/recordings` holds 50 games and 13,900
actions carrying state hashes a second implementation computed, it is **frozen**, and
`CorpusIsFrozenTest` fails if a recording changes.

Read off the tree rather than assumed:

| Fact | Consequence |
| --- | --- |
| `GameState.coalitionLeaderId` is a plain nullable with no `@EncodeDefault(NEVER)` and no default | It is emitted in **every** canonical state and sits inside all 50 hashes. **It cannot be deleted.** |
| 42 recordings carry a `SET_COALITION_LEADER`, and `Replay.kt` reduces without validating | The action must stay decodable and reducible. But the **validator** may refuse it, because replay never consults the validator. |
| `ValidatorImpersonationTest` asserts refusals, never acceptances, and skips actorless actions | Refusing `SET_COALITION_LEADER` outright breaks no gate. |
| `PlayerState.declaredCards` is `@EncodeDefault(NEVER)` and normalised back to `null` when emptied | The pattern for every new field: never materialised in a recorded state, so the hashes cannot move. |

So "delete the leader" is unavailable, and what is available is better anyway — see D2.

The other standing constraint is that `commonTest` runs on JVM, JS, wasmJs and the iOS
simulator. A `Long` is two `Int`s on Kotlin/JS. Nothing here needs one, and nothing here should
acquire one.

## D2. Retire the leader by refusal, not by deletion

`GameState.coalitionLeaderId` stays, always `null` in a new game, carrying a comment that says
it is replay-only shape. `GameAction.SetCoalitionLeader` stays decodable and reducible, and
`ActionValidator` keeps accepting it. The **doors** refuse it: `RoomCore.applyAction` and
`LocalGameSession.dispatch`, both reading one shared `GameAction.retired` that lives beside
`actorId` — the same kind of rule, and one both doors must answer identically.

**Refusing it in the validator was tried first and is wrong.** D1's table said the replay path
does not consult the validator, which was read off `Replay.kt` and is false one level down:
`GameEngine.reduce` validates before it dispatches, so a rule there refuses all 42 recordings
that carry the action. `CorpusReplayTest` said so on the first run. The frozen corpus is not
only a constraint on shapes; it is a constraint on where a rule may live.

This closes the seat-boundary hole **by making the action unsendable** rather than by securing
it. That is strictly better than the alternative considered, which was to add a `nominatedBy`
field so the action could carry an actor: 42 recordings fix the payload's shape, so the field
would have to be optional, so a crafted message could still omit it, so the hole would still be
there behind a longer piece of code.

**Rejected: make the nomination bind** (have `CoalitionSearch.evaluate` score the nominated
hand). The argument for it was that three independent members need a shared target or their
turns undo each other. That argument does not survive the code: `coalitionDeclarationAction`
runs before any final-round turn — `nextAction` will not let a bot play while any bot still owes
a declaration — so by the time anyone acts, every bot's read cards are public claims. The three
planners then run the same `min` over the same declared picture and reach the same target. The
declarations already coordinate the coalition. With four people it is plainer still: they read
the same badges and each decide on their own turn.

`CoalitionSearch.evaluate` is therefore **unchanged**. `min` was right.

## D3. A claim is about a card, not about a hand

`DeclareCards` currently claims a rank for a position **of your own hand**. It becomes a claim
about `(seat, position)` — yours, a teammate's, or the caller's — attributed to the seat that
spoke it.

This closes an asymmetry that is otherwise permanent: `CoalitionPlanner.buildInput` pools
`knownCallerCardIds` out of every coalition seat's private `opponentKnowledge`, so bots share
what they have seen of the caller while a person who peeked it has nowhere to put it. Once
claims cover any card, the bots declare that knowledge and the private pooling is deleted. One
channel, the same for both.

It also stocks the plan composer (D7): you can only plan with material somebody has said out
loud.

The seat boundary is unchanged — a claim names its speaker, and `actorId` is the speaker — so
this adds no new impersonation surface.

## D3a. A claim carries partial knowledge, because that is what a player has

An exact claim — "my third is a King" — is the *least* common thing a person actually knows ten
turns after setup. What they have is the pair without the order: "those two are a King and an
Ace, and I have lost which is which." A vocabulary that cannot say that forces a guess and makes
the coalition plan on a coin-flip presented as a fact.

So a claim is a **set of positions, a set of ranks, and how they pair up**: exact, unassigned,
one-of-several (including classes like the low cards), somewhere-in-my-hand, or withdrawn.

This is not a new concept in the codebase — it is the one already there. `CardMemory` carries a
`confidence: Double` and `BotMemory.getConfidence` reads it; `believedOwnCards` then flattens it
to `Map<Int, Rank>`. **The bots already hold graded belief and throw it away at the moment they
speak.** Widening the claim lets a bot say what it believes, and makes a weak bot honestly
unsure rather than confidently wrong — a better opponent and better copy.

Where the uncertainty comes from is worth knowing, because it tells you what to build: swaps are
public in *position* terms, so watching a Jack move two cards you knew never loses you the
mapping. What loses it is time. This models human forgetting, which is the game.

**It makes the planner sharper, not harder.** `PlanCard` is already a value plus a `known` flag,
with unknowns priced at the deck-wide `expectedUnseenValue`. An unassigned King/Ace pair is a
*tighter* distribution — both positions worth about 0.5 rather than about 6 — and both ranks
come out of `unseenCounts`. A claim that admits ignorance improves the plan.

**One flag has to split.** `CoalitionSearch` guards every rank-consuming site with `known =
false`, on the rule that the rank is never read. An unassigned pair breaks that binary: its
*values* are known and its *ranks* are not. So the flag becomes `valueKnown` and `rankKnown`, or
the search will declare a King it has no pairing for and match a toss-in it cannot make.

**Confidence is already half public.** `knownCardPositions` rides on every view, so the table can
see which positions a seat has read; claiming a position you never read is visibly a guess. The
explicit hedge therefore only has to cover what state cannot show — a card you read and have
since forgotten.

### The UI: one question, three answers

There is no text, so a claim is built by **selection** and finished by the only question left.
Pick two cards, pick two ranks, and the app asks *which way round* with three answers of equal
standing: each pairing, and **"not sure"**.

`Not sure` being one tap and not a mode is the whole design. It is what the player came to say.

The question is offered for **exactly two** cards — three would be six pairings, which is more
than anybody reads — so a larger claim is unassigned by construction.

**Rejected: a confidence slider**, and rejected: a free "notes" affordance. The first asks people
to quantify a feeling and gives the planner a number it cannot trust; the second is a text box
with a different name (D4).

## D3b. Attribution is forced, and a contradiction is not a new model

**Attribution is not a nicety here; the widening in D3 requires it.** `declaredCards` hangs off
`PlayerState` — the *owner of the card* — which was sound while a claim could only be about your
own hand. The moment one seat can claim another's card, storing it on the owner makes it read as
the owner's statement. So the claim carries its speaker, and `Map<Int, Rank>` on the owner
becomes a set of attributed statements. The `@EncodeDefault(NEVER)` and null-when-empty
discipline is unchanged, so the hashes still cannot move.

Three things attribution buys beyond correctness: a claim can be **weighed** (and
`knownCardPositions` is already public, so the table can see whether the speaker ever read that
card); the reveal can **cost** somebody something, which is what D12 depends on; and online it is
the **accountability** — an anonymous claim channel attached to a shared prize is an invitation
to claim low cards for yourself so the coalition pushes you.

**A contradiction needs no new machinery.** It is D3a's candidate set with a combining rule:

| Standing claims | Result |
| --- | --- |
| "an action card" + "a King or a Queen" | **intersect** → {King, Queen} |
| "low" + "a 2 or a 7" | **intersect** → {2} |
| "a King" (Nina) + "a 7" (Don) | intersection empty → **disputed**, candidates {King, 7} |

So consistent claims *narrow* — which is what pooling knowledge is supposed to do, and the case
worth designing for, since it is far commoner than a dispute — and inconsistent ones flag. A
dispute is then a candidate set too wide to name a rank, which `rankKnown` already handles: you
cannot declare a King the table is arguing about.

Three rules, all of them the same rule:

- **The app never adjudicates.** It shows the disagreement and both speakers. A claimant may
  withdraw or replace their own; the reveal settles the rest. Consistent with a claim never being
  checked when it is made.
- **Changing your own mind is not a dispute.** A second claim from the same seat replaces its
  first. Only different speakers conflict.
- **A contradicted bot answers from its confidence** — withdrawing where its memory has decayed,
  standing where it has not, and never consulting the real card. It reads as a teammate rather
  than as an oracle, and `CardMemory.confidence` is already there to drive it.

**Rejected: last-claim-wins.** It is the cheapest thing to build and it destroys the most
interesting event in the channel — two people who both saw a card and remember it differently is
information, and one of them can usually work out which is stale.

## D4. A phrasebook, not a chat

Free text is barred by an invariant with tests behind it: *"Nothing a player types reaches
another player's screen — there is no text input in the app"* (`ARCHITECTURE.md`, held by
`NicknameTest` and by `looksMinted` at the room's door). A text box would also change the
content rating on both stores and bring moderation with it.

The phrasebook is not a workaround for that. It is better here, and the reason is in the repo
already: `Said.kt` is a several-hundred-line map from typed messages to localized strings, and
twenty locales ship. A typed vocabulary is rendered in **the reader's own language** — so two
players with no language in common can agree a Jack swap. A text box would leave them staring
at each other.

Vocabulary: claims, proposals, announcements (`IWill`), answers (`Yes`, `No`, `Wait`,
`ThatLeavesUsWorse`), assessments (`MyHandIsLow`, `ImTheBin`). `PlayFor(seat)` is in it — the
nomination returns as **something you can say** rather than something the state records, which
gives back the beat of agency D2 removes at no mechanical cost.

## D5. Propose, never command

A proposal is a `GameAction` the proposer cannot legally send, addressed to the seat that can.
It is never applied. The recipient decides; their acceptance is their own action through
`ActionValidator`.

Three reasons a human must not drive a bot's seat, and none is squeamishness:

- It is exactly what the anti-cheat boundary refuses. `ValidatorImpersonationTest` re-attributes
  18,066 corpus actions and asserts none is accepted. A local-only exception would teach the UI
  a habit that breaks the moment there is a network — the thing the `GameSession` seam exists to
  prevent.
- It is a different game per configuration: drive two bots and you play three hands; online
  against three people you play one.
- A bot that must obey is not a teammate, and the cooperation stops being cooperation.

The payoff is that **the workflow is identical whether the recipient is a person or a bot**, so
a mixed coalition needs no second design.

Bot suggestions are nearly free, but not in the shape first assumed. `CoalitionSearch` does plan
over `turnQueue`, and the intent was to read a teammate's step straight off the principal
variation — but the search exposes no PV, and the only way to ask it "what should *they* do"
is `buildCoalitionPlanInput(state, them)`, which hands the planner **that teammate's own read
cards as ground truth**. A bot doing that would be planning with its teammate's eyes, which is
exactly the back channel D3 closed.

So a bot's suggestion is a **card movement**, computed from the shared picture: "give me your
third card". That is not a lesser version of the intent, it is the only honest one — a
teammate's coming turn opens with a draw nobody can predict, so a concrete move named for it
would be a move that ignores what they draw. What the plan knows is which cards should end up
where, and that is exactly what `GiveMe` and `TakeThis` say. Concrete `Proposal`s remain what
they always were: a move somebody can see is legal *now*, which in practice means one addressed
to a seat already on play.

## D6. What is state, and what is only a message

| | Where | Why |
| --- | --- | --- |
| **Claims** | `GameState` (as now) | World information. `CoalitionPlanner` consumes them, views carry them, `ActionUtils` moves them with their cards. |
| **Proposals, answers, assessments, the plan** | a message channel, never `GameState` | Transient. They should not mutate the game, should not land in a recording, and must not touch a hash. |

The channel rides alongside `GameSession`'s frames so that local and remote are again
indistinguishable to a screen: locally `BotRunner` emits into it, in a room the Durable Object
relays it.

## D7. The plan is bounded by two things, which is what makes it buildable

**The spine is fixed.** The final round is one turn per coalition member, in table order after
the caller — so a plan has **at most three lanes** and nobody chooses the order, only what
happens in each.

**The palette is bounded by what has been said.** You can plan with declared claims, your own
read cards and the discard top. "Nina swaps with her second Jack" is expressible only because
Nina claimed a Jack there. That is what makes declaring worth doing.

The caller's cards are simply absent from the palette — the coalition may not touch them — which
follows `CoalitionSearch`'s own approach of excluding them from `rootHands` structurally rather
than checking for them.

**Rejected: conditional steps** ("if you draw an action card, then…"). A turn begins with a draw
nobody can predict, so every plan is stale after one turn — and the round is three turns. Making
re-planning cheap costs a fraction of what a conditional language costs and covers the same
ground.

One shared plan, not one per member: three competing plans is not a coalition deciding together.
A lane locks when its owner's turn starts, so a plan cannot change under the hand of the person
executing it.

### D7a. A board of parts, agreed as a whole

**The plan is a shared board, and an edit names one part of it.** Any coalition member — never
the caller — may set, replace or clear any lane, and add or remove any shed, whoever the part
belongs to. The room merges the part into the standing plan. That is the grain the lock has
(per lane) and the grain the UI has (one seat's turn at a time), and it is what stops two
people working on different lanes overwriting each other: the first design here was
"last edit stands over one whole draft", and a whole draft sent on every gesture loses the
other person's lane every time two messages cross. The same lane edited by two people *is*
last edit stands, which is the one case that rule was ever for.

**Agreement is to the combined plan, not to a part.** The lanes only pay off together — a swap
in one lane is worth something because of what the next lane does with it — so the coalition
says yes to the board, not to a step. Every edit resets agreement to the editor alone, since a
yes to a plan that no longer exists is not a yes; making the edit is agreeing to it, so the
editor is not asked twice. The plan is **agreed** when every connected coalition human and
every coalition bot has said yes. It is a recorded fact the person on play can see, which is
what "we decide together" turned out to mean: last-edit-wins was a mechanism standing in for
it.

**Agreeing is how you finish talking.** Whoever has more to say says it first, then agrees;
when the last member agrees the confer window closes and the first coalition turn starts.
"Done" without agreeing still exists, for the member who has finished talking and does not
agree. A plan that is not agreed when the window closes **stands as a suggestion**, showing who
agreed. It is not voided: propose, never command (D5) applies to a plan as much as to a move,
and the person on play decides.

**Bots answer for their own lane and nothing else.** On an edit that touches a bot's lane, the
bot runs the step through the same within-reach test `answerTo` applies to a single proposal,
on the shared picture plus its own cards, and its yes or its refusal with a reason goes out as
talk. An empty lane is a yes. A bot's no is D5 working, not a defect. A bot *editing* a lane is
a later item: a bot that replaced a lane the moment a person set it would reset agreement in a
loop, and that needs a bound designed rather than assumed.

**The plan feeds the on-turn suggestion.** When a member's turn comes, their own lane is what
the slot built for a proposal (tasks 2.2, 2.6) shows: the step as words, and the targets
pre-armed when the draw or the discard top makes the step legal — a Jack or a Queen for a swap,
a King for a declare, the unused action card for a take. Otherwise the ask shows as words and
the person plays freely. The plan strip and the on-turn suggestion are separate pieces of UI,
joined only by one feeding the other. This is what closes 2.2 without anything speaking a
`Proposal`: the plan is the speaking half, and the receiving half was already built.

**Edits stay open mid-round** for unlocked lanes, through the same door, and reset agreement
the same way. That is the re-plan 3.11 asks for, with no second mechanism. The lane of the seat
on play is refused whether or not pacing has stamped it locked yet — a fresh lane for a turn in
progress is the same turn a moment earlier — which is why the door is told who is on play.

**The wire.** Two client messages — one plan edit, and agree or disagree with the standing
plan — and **no new server message**: the room answers an edit the way it answers `more-time`,
with an empty `events` message per seat whose `plan` field carries the whole resulting board
(three lanes at most) and whose `said` carries the bots' answers. Every `events`, `sync` and
`joined` carries `plan` whenever one stands, so a lane locking on an ordinary action, a
reconnect and an app restart all land on the present plan — the case D11 was fixed for. The
broadcast is to **every seat, the caller included**: talk is public (D12), the plan is built
only from public claims, and the caller cannot act on it. A plan edit spends from the same
budget talk does, because it is broadcast to four sockets.

**Solo holds a plan.** The rehearsal and the readout are the plan's value, and a person alone
against three bots can still rehearse a line and watch the outcome; a composer reachable only
online cannot be tried by a maintainer with one phone, and every Compose test runs on
`LocalGameSession`. So the merge, the lock and the refusals are one pure function in
`shared/shapes`, and both doors — the room's and the local session's — call it, exactly as both
read one `GameAction.retired` (D2). The two cannot disagree about what a legal edit is.

**Rejected: the plan as talk.** Sending each edit as a phrasebook sentence through the say
door costs no new wire and puts the plan's history in the log strip for free, and it is the
wrong shape for a stored, lockable draft: the room could refuse nothing, every client would
re-derive the plan from sentence order, and a reconnect would lose it, because `said` is
fire-and-forget and a sync carries no talk.

**Rejected: agreement per lane by its owner.** Closer to how a single proposal works — the
plan is settled when each executor has accepted their own part — and it is the fallback if
unanimity proves too slow for a twenty-second window. Whole-plan agreement is what the lanes'
interdependence wants.

## D8. Rehearse the plan; do not describe it

`choreograph(action, before, after)` is pure, so a hypothetical before/after pair animates a
move that has not happened, using the same code that animates one that has. The coalition
**watches the plan run** instead of reading four sentences.

**The ghost table is built by transforming the view, not by reducing** — and the difference
matters more than it looks. The first sketch had `GameEngine.reduce` on a hypothetical
`GameState`; a client has no `GameState` and must not acquire one. It holds only a redacted
`PlayerView`, which is the whole of design R1 and the reason a solo game and an online one are
the same screens. A composer that reduced would work locally, where `LocalGameSession` happens
to hold the state, and break the first time somebody played online.

Transforming the view is also *enough*: a swap is two cards changing places, which is what a
view can express, and it cannot invent information — a card the seat could not see before the
plan cannot be seen after it, which a test asserts by counting hidden cards either side.

One wrinkle worth knowing: the choreography draws a swap from the **pending action's targets**,
because that is where a real Jack keeps the two cards it is about. So the ghost view stages the
card as if it were in play. Staged on the `before` view only, and never dispatched.

The strip's number is computed in `shared/client` from standing claims rather than by calling
`CoalitionSearch.evaluate`. The client does not depend on `shared/bot` and should not start:
what the readout needs is a sum over what the table has been *told*, which is `believedAt` —
the same source the planner's own `PlanCard` values come from, so the two cannot disagree.

It states an **outcome**, not a total. A tie pays the caller (`caller <= bestCoalition`), so
"our best: 4" beside a caller on 4 looks level and is a loss; and a believed total stated
without saying how much of the caller's hand nobody has seen is a number pretending to be
information. `PlanOutcome` carries both.

Watch: `ScreenContrastTest` measures WCAG AA from each screen's own pixels, so the "not real"
treatment of the rehearsal felt has to be designed against that from the start rather than
discovered failing it.

## D9. Repair silently when the card moved; break loudly when the belief was wrong

`ActionUtils` already carries a claim through a watched Jack or Queen swap, drops it when the
card leaves face up, and renumbers claims after a removal — all `null`-safe, so no recorded
state materialises the field. A step pointing at a claimed card therefore re-anchors itself for
free.

What is new is noticing a claim was **contradicted** rather than moved. That belongs in the
client, off the `PublicReveal` stream it already receives — never in the engine, which
deliberately never compares a claim to a card, and where the comparison would land in the hash.

So: three step states — **live**, **re-anchored**, **broken** — plus a plan-level "a better line
is available now", which `CoalitionSearch.evaluate` can answer by comparing the plan's value to
the best line it can find.

Bots need none of this. `CoalitionPlanner` recomputes at every decision point rather than
storing a line, "so it adapts as cards are drawn instead of committing to a line that the draw
has already invalidated". The shared human plan is the only stored one, so it is the only one
that can go stale — which makes staleness a UI problem, not a bot problem.

The copy matters here: a wrong claim is the game working, not a player failing. It is the
consequence of a rule the app is built on — that the engine knows your hand and you are supposed
to.

## D10. The confer window reuses the slot the vote vacates

`LEADER_MS` is twenty seconds in which the room waits for a nomination and appoints one if
nobody speaks. The vote goes; the twenty seconds become a window for the thing people actually
want to do.

Closable early on every connected coalition member being ready — the pattern
`playersReadyForNextTurn` already establishes for the toss-in window — and skipped entirely
when no person is in the coalition, so a solo game against three bots does not acquire a pause
it never had.

It stays bounded for the same reason the old one was: the final round is the one part of the
game the caller is entitled to see played out.

## D11. A taken-over seat must be a bot to the engine too

Today the takeover flips the **seat's** `isBot` and `botPlayedWhileAway` while the engine's
player keeps `isHuman = true` (it is set once, at deal time, from `tokenHash != null`), and the
bot driver stops on any seat holding a `tokenHash` (`RoomCore.kt:1479`). `BotRunner.turnAction`
returns `null` for a seat the engine records as human. So the takeover is a flag and nothing
plays the seat.

The obvious fix — make the engine's record agree with the room's — is wrong, and implementing it
is how that was found. **`isHuman` and `isBot` are inside the canonical state hash.** Flipping
them when somebody drops would leave the round's own recording unable to replay to the state it
ended in: the recording is `(initialState, actions[])`, and a takeover is not an action.

So the room does not tell the *engine* that somebody left. It tells the **driver**, through a
lens — `asPlayed(game, seats)` — where a seat a bot is playing reads as a bot. `BotRunner` is a
decision-maker rather than an authority: it needs only to know whose move to propose, and what
it proposes is validated and reduced against the untouched state, exactly as the absent person's
own client would have sent it. Nothing recorded moves.

The driver's stop condition changes with it, and it has now been wrong in both directions:
`occupied` made the room refuse to move its own filler bots, and `tokenHash` alone made it wait
for people who had gone — a held seat being precisely what a disconnected person's seat looks
like. It stops for a seat whose person is **present**.

The same lens covers the reclaim for free: the reclaim path already clears the seat's `isBot`,
and the lens reads the seat.

This is not coalition-specific, but the final round is where it hurts most: exactly one turn per
member, so one dropped teammate stops the round.

It gets a failing test first, at the room layer, per the repository's rule on reported bugs.

## D12. Talk is public, and the reveal is the referee

Every claim rides on every seat's view, the caller's included. That is faithful — the coalition
confers out loud at a table — and it costs the coalition nothing, because **the caller cannot
act again**: they called at the end of their own turn.

Which is also why a bot caller says nothing. The caller's register can only be information or a
bluff, and a bot has no model of when to bluff; a bot claiming a 4 while holding 12 is lying to
the player, a different contract from a bot's declarations to its own coalition, which are
honest-but-fallible memory. Silence is the only honest default, and it reads correctly: the
caller has said their piece by calling.

A human caller may claim their own cards, and bluffing is legitimate — because at scoring every
hand turns over, so a claim that was wrong can be shown to have been wrong. **The reveal is the
referee**, for the coalition's claims as much as the caller's. That gives table talk a cost over
a session without a single rule changing.

## D13. A bot says one thing per turn

Unbounded, three planning bots would narrate every turn and the player would stop reading any of
it. One unprompted sentence per bot per turn, plus answers when addressed. A feel judgement, and
the number is expected to move once somebody plays it.

## D13a. What a review of the actual rules turned up

Six things the design missed, found by reading the final round's mechanics rather than its
description. The first changes the plan model.

**The final round is not three turns.** `TossInUtils` says it in as many words: *"Coalition
members keep their toss-in rights during the final round — shedding matching cards is one of the
coalition's main tools against the caller."* A toss-in sheds a card **without costing a turn**,
which is the cheapest way to lower a hand in the game — so the round is three turns *plus every
window they open*, and a plan of three lanes cannot say the coalition's best play.

The play it cannot say is a two-person one: a member **deliberately discards a rank a teammate
has claimed** so the teammate can shed theirs into it. Both halves are already sayable
(`IWill`, and a proposal), so this needs a slot in the plan rather than a new vocabulary — and
notably **not** a conditional: "I hold a 7 and will shed it" is a statement about what its
speaker holds, not a branch, so D7's refusal of conditionals stands.

**The caller cannot toss in at all**, so a King is safe. `getAutomaticallyReadyPlayers` marks the
caller ready automatically — "who may not participate in toss-in afterwards" — which means a
coalition King forces every *coalition* hand to dump a rank and cannot let the caller shed a high
card. The King is a pure coalition tool, and both the lesson and the composer should treat it as
one.

**The Ace is a trap that only the bots know about.** `CoalitionAceTest`: the planner never plays
an Ace from hand and puts a tossed-in one down rather than aiming it, because every legal target
is a teammate and it "could hand a penalty card to the one hand still able to win the round." A
person is asked the same question with no guidance and three bad answers.

**A tie pays the caller** (`callerWins = caller <= bestCoalition`), so the coalition must finish
*strictly* below — and a plan readout of "our best: 4" beside a caller on 4 looks level and is a
loss. The strip states an outcome, not a number, and `winProb` is already the right source.

**A number without the caller's is meaningless.** `callerKnownValues` and `callerUnknownCount`
exist in the planner; the widened claims are what let people contribute to them. The readout
carries both, and says how much of the caller's hand nobody has seen rather than implying a
total.

**Ignoring all of it stays a complete way to play.** No claim, proposal or plan is ever required
to take a turn, and nothing nags a player who is not interested.

**Rejected: a cross-round honesty record** ("Nina's claims: 4 of 5 right this session"). It is
tempting once the reveal is the referee, and it is the wrong game: it turns a memory game into a
scoreboard about whether strangers lie, and it punishes exactly the forgetting D3a exists to
model. Being caught out at a single reveal is enough.

## D14. Staging, because this is large

1. **Talk** — the widened claim, the phrasebook, bots speaking, the leader retired, the confer
   window, the taken-over-seat fix.
2. **Single proposals** — one step, one recipient, one tap to accept, pre-armed on the
   recipient's turn.
3. **The plan** — lanes, direct manipulation, the ghost table, the rehearsal, the value readout,
   decay.

Phase 2 is usable without phase 3, and it answers a question worth having the answer to before
building a composer: whether people talk to their coalition at all. If they do not, the strip is
the wrong thing to have built.

## Risks

- **Nobody uses it.** The mitigation is the staging above, and the fact that phase 1 improves
  local play on its own: today a human watches three silent bots pool information they cannot
  hear.
- **The composer is too rich to learn.** Mitigated by the two bounds in D7 — three lanes, and a
  palette limited to what has been said — and by refusing conditionals.
- **The rehearsal reads as a real move.** A player who thinks a plan happened is worse off than
  one who never planned. This needs the strongest visual separation in the app, and it needs to
  survive `ScreenContrastTest`.
- **Bot chatter becomes noise.** D13, and a willingness to lower the number.
- **The room grows state that outlives its round.** The plan is per-round and discarded at
  scoring; it must not reach a recording, and a test should say so.
