# Change: Choreography — how a server-authoritative game animates on a client

## Why

The engine runs inside a Durable Object. The animation runs on a phone. Those are two
different machines with a network between them, and nearly every hard question about a card
game's presentation turns out to be a question about that gap:

- A bot's move is decided on the server in under a second. The card that moves because of it
  has to travel on the client, and the server has already moved on.
- Three bots take their turns between one tap and the next. Without something to watch, the
  player sees the discard pile change and has to work out what happened.
- A player who reconnects gets the *current* state, not the last twelve moves. Playing twelve
  animations at them would be worse than playing none.
- A player on a slow connection falls behind. Whatever they see has to end up correct.

Today the animation is derived from `GameState` inside `LocalGameSession`, which works for a
solo game and **cannot work online at all** — a client never has `GameState`, only the
redacted `PlayerView`. So the one part of the presentation that most needs to be shared
between local and remote is the one part currently built for local only. That is the hole
this change closes, and it is worth closing before the online client is written rather than
after.

The other reason is that a card game's animation is not decoration. Four hands, two piles and
a pending slot, with cards crossing between them — a player who cannot see *which* card went
*where* cannot follow the game. The web app knew this and grew a real system for it
(`CardAnimationStore`: parallel and sequential sequences, eight movement types, captured
positions, rotation, reveal). The Kotlin client has a single card flight. That is not enough.

## What Changes

- **Choreography is derived from the view, not the state.** `choreograph(action, before,
  after)` takes two `PlayerView`s — exactly what a client has, locally or online — and returns
  what should be seen. It is a pure function, so it is tested rather than watched, and it
  physically cannot animate a card the player is not entitled to see.
- **The unit is a scene, not a flight.** A scene is beats that play together; scenes play in
  order. A swap is two beats in one scene because the cards cross; a King's declaration is
  three scenes because it is three things happening in sequence.
- **Beats cover what the game does**, not just movement: a card moving, a card turning over,
  a hand flinching at a penalty, a seat lighting up, a bot reacting.
- **The queue can always catch up.** A client that is behind — a reconnect, a slow link, a
  backgrounded app — collapses its queue and lands on the authoritative state rather than
  playing a backlog at somebody who has stopped watching.
- **The server never waits.** No animation gates a turn, a timer, or another player. This is
  stated as a rule because the alternative is a table where one person's slow phone holds up
  three other people.

## Impact

- `shared/client`: `Choreography.kt` (pure, tested), `AnimationQueue.kt` (pure, tested).
  `CardFlight.kt` is replaced by them.
- `composeApp`: the stage plays scenes rather than flights.
- The room needs no change. It already broadcasts an ordered log with contiguous indices and
  answers `resync` from a cursor — which is exactly the input this design needs, and the
  reason this can be built now and wired to a socket later.
