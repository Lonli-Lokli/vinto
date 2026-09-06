# Change: Coalition play, for a table of people and a table of bots

## Why

The final round is the only part of Vinto that is **cooperative**, and it is the part the app
does least with. The rules license it in as many words — the coalition "may work together and
share information" — and today that amounts to two things: a vote that changes nothing, and a
one-word claim about one of your own cards.

Four findings, each read off the tree rather than off a document.

**The leader vote is inert.** `coalitionLeaderId` is plumbed into `BotContext` and
`MctsContext` and **never read by any bot decision**. `CoalitionSearch.evaluate` scores
`winProb(minScore(hands)) - eps * minScore(hands)` — the best hand, whoever holds it — and
scoring uses the same minimum. So the app stops the final round for up to twenty seconds to
ask a question whose answer changes no decision and no score. Its only real effect is that
`mayDeclare` gates table talk behind it, so the vote is what *unlocks the ability to speak* —
and `docs/kotlin/UI.md` already records a player reading the nomination as a promise the app
broke.

It is inert for a reason worth writing down, because it is the reason not to fix it by making
it bind: `coalitionDeclarationAction` runs before any final-round turn, so by the time anyone
plays, every bot's read cards are public claims. Three planners then run the same `min` over
the same declared picture and reach the same target. **The declarations already coordinate the
coalition; the vote is a step that settles nothing.** With four humans it is plainer still —
they read the same badges and each decide on their own turn.

**`SET_COALITION_LEADER` has no seat boundary.** Its `actorId` is `null`, so the room's door
check is skipped entirely and `ActionValidator` only refuses naming the *caller* as leader.
Online this means **the Vinto caller can nominate the coalition's leader**, and anyone can
overwrite it for the rest of the round. It is the one hole in a boundary
`docs/kotlin/ARCHITECTURE.md` describes as "one line rather than an audit".

**The coalition's channel is not the same for people and for bots.** A member may claim a rank
for one of their **own** positions and nothing else. So a player who peeked the caller's card
with a 9, or a teammate's with a Queen, has nowhere to put it — while `CoalitionPlanner`
pools `knownCallerCardIds` straight out of every coalition seat's private `opponentKnowledge`.
The bots share something the people cannot say. `projectView` already carries the principle
this violates: coalition knowledge travels as *declared claims*, and a claim is only as good
as the claimant's memory.

**A coalition member who drops hangs the round.** The takeover flips the *seat's* `isBot` and
`botPlayedWhileAway`, but nothing rewrites the **engine player's** `isHuman`/`isBot`, and the
bot driver stops on any seat holding a `tokenHash` (`RoomCore.kt:1479`); `BotRunner.turnAction`
returns `null` for a seat the engine records as human. So the takeover is a flag, not a player.
The final round is exactly one turn per member, so one dropped teammate stops the round the
coalition is trying to win.

## What Changes

- **Retire the leader vote.** No seat-grid question, no twenty-second stall, no auto-pick
  holding every bot's play. `GameState.coalitionLeaderId` and `GameAction.SetCoalitionLeader`
  **stay as replay-only shape** — the field is inside all 50 corpus hashes and 42 recordings
  carry the action — and `ActionValidator` refuses the action at every live door, which closes
  the actorless hole by making it unsendable rather than by securing it. Table talk unlocks at
  the moment Vinto is called instead of at the vote.
- **Widen the claim.** A claim becomes about **any** seat's card — mine, a teammate's, the
  caller's — spoken by the sender, still never checked when it is said. Bots then declare their
  caller knowledge through that channel and the private `opponentKnowledge` pooling in
  `CoalitionPlanner` is deleted, so the coalition's channel is identical for people and bots.
- **Add a talk channel that is a phrasebook, not a text box.** Claims, proposals,
  announcements, answers and assessments, each a typed value rendered per-locale by the
  machinery `Said.kt` already is. This keeps the invariant *"nothing a player types reaches
  another player's screen"* literally true, and it buys something free text could not: a
  Belarusian and a Japanese player can agree a Jack swap with no shared language.
- **Propose, never command.** A proposal is a `GameAction` the proposer cannot legally send,
  addressed to the seat that can. It is never applied; the recipient decides, and their
  acceptance is their own action through `ActionValidator` as usual. A human never drives a
  bot's seat, and the workflow is identical whether the recipient is a person or a bot.
- **A shared, revisable coalition plan.** One draft per final round, editable by any coalition
  member and by the bots, at most three lanes because the round is at most three turns. It is
  **rehearsed rather than described**: `choreograph(action, before, after)` is pure, so a
  hypothetical before/after pair animates a plan that has not happened. The strip reads the
  plan's value from `CoalitionSearch.evaluate`, and an agreed step pre-arms its owner's turn.
- **Plans decay honestly.** A step whose card *moved* re-anchors itself — `ActionUtils` already
  carries claims through a watched swap, drops them when a card leaves face up, and renumbers
  them after a removal. A step whose *claim was contradicted* by a public reveal is marked
  broken and never silently repaired. Detection lives in the client, off the `PublicReveal`
  stream, because the engine deliberately never compares a claim to a card and doing so in
  state would move the hashes.
- **A confer window**, reusing the slot the vote vacates: talk only, closable early when every
  present human is ready, skipped entirely when no human is in the coalition so local play
  against three bots does not stall.
- **Fix the taken-over seat**, so a dropped coalition member's turn is actually played.

## Non-goals

- **No free-text chat, in this change or any other.** The invariant is held by `NicknameTest`
  and by `looksMinted` at the room's door, and a text box would change the content rating on
  both stores and leave two players unable to read each other.
- **No conditional plans** ("if you draw an action card, then…"). A turn begins with a draw
  nobody can predict, so every plan is stale after one turn — and the round is three turns, so
  re-planning is cheaper than a conditional language.
- **No change to scoring, to the rules, or to `CoalitionSearch.evaluate`.** The lowest coalition
  hand still decides the round. `min` was right.
- **No regeneration of `fixtures/recordings`.** Nothing here moves recorded state; every new
  field follows the `declaredCards` discipline (`@EncodeDefault(NEVER)`, null when empty).
- **Talk outside the final round** is out of scope. The rules license conferring in the final
  round; mid-game claims and bluffs are a later question.
- **A human controlling a bot's seat**, in any configuration, local included.

## Dependencies

- No open change is a prerequisite. This builds on three archived ones —
  `design-online-room-lifecycle` (the room, seats, pacing and takeover),
  `design-client-choreography` (`choreograph`, the animation queue) and
  `migrate-to-kotlin-multiplatform` (the engine, the validator, the bot).
- Independent of `ship-and-operate`, which is store and operations work and touches none of
  this.
- The parity corpus is frozen and cannot be regenerated (`fixtures/recordings/README.md`); the
  constraints that follow from that are in design D1 and are load-bearing throughout.
