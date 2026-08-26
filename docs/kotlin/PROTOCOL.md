# The room protocol

What travels between a client and a room, declared once in `kmp/shared/protocol`
(`game.vinto.protocol`) and used by both ends: the worker serializes with it, the app's
`RemoteGameSession` parses with it, and `ProtocolWireTest` pins the shapes with literals
copied from `index.mjs` — which is the contract, because the JavaScript was serving the gate
harnesses before this module existed and the module transcribes it.

## Transport

- **WebSocket** at `wss://<host>/?room=<code>`. One JSON text per message, client and server
  alike. Messages over 8 KB are refused before parsing.
- **REST**, for what happens before a socket exists:
  - `POST /rooms` `{isPublic?, hostNickname?}` → `{code, roomId}` — the only way a room comes
    into existence.
  - `GET /rooms` → the public lobby list (private rooms are simply absent; they are
    reachable by code).
  - `GET /health` → `{ok, service, engine, roomOpen}`.
  - `POST /replay` → replays a `GameRecording` through the deployed engine.

## Serialization

`ProtocolJson` — built from `VintoJson`, plus `classDiscriminator = "type"` and
`ignoreUnknownKeys = true`. The discriminator rides beside the payload fields
(`{"type":"join","nickname":"Ann"}`), which is what `switch (msg.type)` has always read.
Payloads inside a message (`GameAction`, `PlayerView`) encode exactly as the engine's
canonical form does.

Number caveat: JavaScript writes `1500` where a Kotlin `Double` writes `1500.0` (or
`1.5E12`). Both are JSON numbers and both ends read either; nothing may compare wire text
byte-for-byte.

## The token

`joined` is the **only** message that ever carries the raw seat token; the room stores its
SHA-256 and nothing else. The token is the sole thing that authorises a message — a seat
number is always *derived* from it, never sent beside it — and holding it is what lets a
client reconnect to its seat after a drop, including a seat a bot has been playing in the
meantime.

## The cursor

The room's action log index is the sync cursor. Every `events`, `joined`, `started` and
`between-rounds` carries `nextIndex`; a reconnecting client sends `resync{sinceIndex}` with
the last index it saw and receives `sync` with the log from there. Actions rather than
states, because an action is small and a state is not.

## Messages

Client → server:

| type | fields | meaning |
| --- | --- | --- |
| `join` | `token?`, `nickname?` | Take a seat, or return to the one this token holds |
| `action` | `token?`, `action` | One engine `GameAction`, authorised by the token |
| `resync` | `sinceIndex` | The log from this cursor, please |
| `add-bot` | `token?` | Fill the first empty seat with a bot (any seated player may) |
| `remove-bot` | `token?`, `seat` | Take a filler bot back out; cancels a countdown |
| `next-round` | `token?` | Agree to another round; the last connected human deals it |

Server → client:

| type | fields | notes |
| --- | --- | --- |
| `joined` | `seat`, `token`, `seats`, `nextIndex`, `lobby`, `view` | To that socket alone; `view` null in a lobby |
| `events` | `events`, `nextIndex`, `view` | Accepted actions incl. the sender's echo and bot moves; per-seat view |
| `sync` | `events`, `nextIndex` | Answer to `resync` |
| `lobby` | `lobby` | Broadcast on any seat change |
| `started` | `view`, `nextIndex`, `standings?` | A deal; `standings` only on the next-round path |
| `between-rounds` | `view`, `standings`, `nextIndex` | Round done, session live, awaiting agreement |
| `ended` | `reason` | Session over; the room (and scoreboard) still stands |
| `closed` | `reason` | The room is going away; socket closes after |
| `error` | `message`, `retryAfterMs?` | `retryAfterMs` present exactly when rate-limited |

A **view is per-seat and never broadcast**: two seats are entitled to different cards, so any
message carrying a `PlayerView` is built once per socket. The events beside it are public
and identical for everyone.

## Compatibility rule

**The protocol only ever grows, additively.** New message types and new optional fields are
fine — `ignoreUnknownKeys` means an older client skips what it does not know — but a field
never changes meaning or type, and a message type is never removed while any client sends
it. A change that cannot be made additively is a new message type, not a new shape for an
old one.
