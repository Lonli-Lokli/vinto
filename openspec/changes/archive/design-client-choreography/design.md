# Design: client choreography

## Context

The room is server-authoritative (D9). It holds `GameState`, validates every action, plays the
bots, and sends each seat a redacted `PlayerView`. It keeps an ordered log of what happened,
with contiguous indices, and answers `resync` from a cursor.

A client therefore has three things and only three: the view it last held, the view it holds
now, and the actions in between. Everything the player sees has to be derivable from those.

## How online card games solve this

The pattern is consistent across the literature and matches what the room already does:

- **Event deltas with sequence numbers**, not state snapshots, are what drive presentation. A
  rolling buffer on the server plus a "replay from sequence N" call fills gaps after a
  disconnect. (`developersvoice.com`, on real-time card games in .NET; the same shape appears
  in PlayFab's and MPL's guidance on server-authoritative card games.)
- **The server does not wait for animation.** Clients render optimistically and reconcile:
  a play animates immediately, and if the server disagrees the client snaps to the truth.
- **A state hash accompanies authoritative events** so a client can tell whether it has
  diverged rather than guessing.

We are well placed for all three: the log is already indexed and contiguous, `resync` already
exists, and `hashGameState` already produces a canonical hash — it is what the 50-recording
parity corpus is checked with.

## Decisions

### C1. Choreography is a pure function of two views

`choreograph(action: GameAction, before: PlayerView, after: PlayerView): List<Scene>`

**Why the view and not the state.** A client never has `GameState` — that is the whole point
of redaction. Deriving animation from state works locally and is unportable to the room, which
is the mode it is actually for. Deriving it from the view has a second property worth as much:
the function *cannot* animate a card the player is not entitled to see, because it has never
been given one. A face-down card flies face-down because that is all there is to fly.

**Why the action and not a diff of the two views.** A diff can see that the discard pile grew
and a hand shrank; it cannot tell which card went where when two moved at once. A swap moves
one card in and another out, and animating them in the wrong order is worse than not animating
them. The action says exactly what was asked for; the views supply the cards.

### C2. Scenes are sequential, beats within a scene are parallel

The web app's store had `parallel | sequential` as a property of each animation. Making it a
property of the *grouping* instead removes the question "parallel with what?" — a scene is the
things that happen together, and scenes happen in order.

A swap is one scene of two beats: the cards cross. A King is three scenes: aim, declare, and
the declared card's own action resolving.

### C3. The queue collapses rather than catching up move by move

A client that is twelve events behind has a player who is not watching twelve animations. The
queue has a budget; past it, everything pending is dropped and the table jumps to the current
view. This is the *normal* path after a reconnect, not an error path.

The alternative — speeding animations up until the backlog clears — was rejected: it produces
a screen that is fastest exactly when it is least comprehensible.

### C4. No animation gates the game

The server does not wait. Neither does a bot, a turn timer, or another player. A client's
queue is entirely its own business and drains at its own pace. What this costs is that a card
can arrive at its destination in the state before it has visibly finished travelling; that is
a fair price and is what every online card game pays.

The consequence to accept deliberately: **a beat is never required for correctness.** If the
queue is dropped entirely, the game is still right — only less legible.

### C5. Beats describe intent, not pixels

`Move(from, to, card)` names *anchors* — the deck, the discard, a seat's slot, the pending
slot — not coordinates. Where those are on screen is the screen's business and changes with
the layout; which of them a card moved between is a fact about the game. Keeping them apart is
what lets the movement be computed here, where it is testable, and drawn there, where it is
visible.

## Risks

- **The action is not always enough.** A Queen's swap resolves inside the action rather than as
  a move of its own, so its endpoints come from the pending targets rather than the payload.
  Handled case by case; where a case is not handled, the answer is no beat, which is a
  degraded picture rather than a wrong one.
- **Two clients may animate differently** if one is behind and collapses. That is intended.
  They converge on state, not on presentation.
