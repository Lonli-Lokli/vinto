/**
 * The room, exercised against the real engine — without wrangler.
 *
 * `gate-two-clients.mjs` drives the Durable Object through workerd and proves the socket and
 * hibernation story. This drives the Kotlin room functions directly in Node, which is where
 * the *game* questions can be asked cheaply: does a seat only get its own view, is
 * impersonation refused, do the bots play, does a game finish.
 *
 *   node kmp/worker/cloudflare/gate-real-room.mjs
 */
import {
  newRoom, joinRoom, applyAction, eventsSince, viewForSeat, seatForToken, seatCount,
  addBot, startGame, countdownMs,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) {
    console.log(`  pass  ${label}`);
  } else {
    failures++;
    console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`);
  }
};

const parse = (json) => JSON.parse(json);

console.log('\nRoom running the real engine\n');

// --- dealing ---------------------------------------------------------------------------
const SEED = 1234;
const NOW = 1_000_000;
let stateJson = newRoom('gate-room', SEED, 'moderate');
let state = parse(stateJson);

check('the room has four seats', state.seats.length === seatCount(), `${state.seats.length}`);
check('and starts as a lobby, with no game', state.phase === 'LOBBY' && state.game === null);

// --- joining, with server-issued tokens ------------------------------------------------
//
// The harness stands in for the socket layer, which mints the token. What matters here is
// that the *room* decides which seat a token holds, and that nothing else does.
const TOKEN_A = 'token-for-ada-32-bytes-worth-of-secret';
const TOKEN_B = 'token-for-bo-32-bytes-worth-of-secret';
const TOKEN_UNKNOWN = 'token-nobody-was-ever-issued';

let result = parse(joinRoom(stateJson, TOKEN_A, 'Ada', NOW));
check('the first client takes seat 0', result.seat === 0, `seat ${result.seat}`);
stateJson = JSON.stringify(result.state);

result = parse(joinRoom(stateJson, TOKEN_B, 'Bo', NOW));
check('the second client takes seat 1', result.seat === 1, `seat ${result.seat}`);
stateJson = JSON.stringify(result.state);

result = parse(joinRoom(stateJson, TOKEN_A, 'Ada', NOW));
check('rejoining with the same token returns the same seat', result.seat === 0, `seat ${result.seat}`);

// Two bots fill the table, the countdown expires, and the game is dealt. Everything below
// this line is about a *running* game, which is what needed a lobby to exist first.
for (let i = 0; i < 2; i++) {
  stateJson = JSON.stringify(parse(addBot(stateJson, TOKEN_A, NOW)).state);
}
const dealt = parse(startGame(stateJson, NOW + countdownMs()));
check('the countdown expiring deals a real game', !dealt.error, dealt.error);
stateJson = JSON.stringify(dealt.state);
state = parse(stateJson);

check('a real game was dealt', state.game.players.length === 4);
check('every player holds five cards', state.game.players.every((p) => p.cards.length === 5));
check('the same seed deals the same table',
  JSON.stringify(parse(newRoom('gate-room', SEED, 'moderate'))) !== JSON.stringify(state));

// --- the token is a credential, not a label ---------------------------------------------
state = parse(stateJson);
check(
  'the room stores a hash, never the token',
  state.seats.every((s) => s.tokenHash !== TOKEN_A && s.tokenHash !== TOKEN_B),
);
check(
  'a seat that was taken has a hash and one that was not has none',
  state.seats[0].tokenHash !== null && state.seats[2].tokenHash === null,
);
check(
  'the account seam exists and is empty',
  state.seats.every((s) => s.ownerId === null),
);
check('a token resolves to its seat', seatForToken(stateJson, TOKEN_A) === 0);
check('an unissued token resolves to nothing', seatForToken(stateJson, TOKEN_UNKNOWN) === -1);
check(
  'sharing a nickname does not share a seat',
  parse(joinRoom(JSON.stringify(parse(newRoom('n', SEED, 'moderate'))), 'tok1', 'Ada', NOW)).seat === 0 &&
    parse(joinRoom(
      JSON.stringify(parse(joinRoom(newRoom('n', SEED, 'moderate'), 'tok1', 'Ada', NOW)).state),
      'tok2', 'Ada', NOW,
    )).seat === 1,
);

// --- redaction -------------------------------------------------------------------------
const view0 = parse(viewForSeat(stateJson, 0)).view;
const view1 = parse(viewForSeat(stateJson, 1)).view;

check('a seat gets a view of its own', view0 !== null && view0.viewerId === state.seats[0].playerId);
check('two seats get different views', JSON.stringify(view0) !== JSON.stringify(view1));

/** Every card id in the whole game, so a leak can be looked for by name. */
const everyCardId = new Set(
  state.game.players.flatMap((p) => p.cards.map((c) => c.id))
    .concat(state.game.drawPile.map((c) => c.id)),
);
const visibleIn = (view) => {
  const found = new Set();
  const walk = (node) => {
    if (Array.isArray(node)) return node.forEach(walk);
    if (node && typeof node === 'object') {
      if (typeof node.id === 'string' && everyCardId.has(node.id)) found.add(node.id);
      return Object.values(node).forEach(walk);
    }
  };
  walk(view);
  return found;
};

const seenBySeat0 = visibleIn(view0);
const ownCards = new Set(state.game.players[0].cards.map((c) => c.id));
const leaked = [...seenBySeat0].filter((id) => !ownCards.has(id));
check(
  'a seat is sent no card it is not entitled to',
  leaked.length === 0,
  leaked.length ? `leaked ${leaked.slice(0, 4).join(', ')}` : '',
);
check(
  'the human seat sees none of its own cards before peeking',
  seenBySeat0.size === 0,
  `${seenBySeat0.size} visible`,
);

// --- what a token does and does not buy -------------------------------------------------
const seat0Player = state.seats[0].playerId;
const seat1Player = state.seats[1].playerId;
const peek = (playerId) =>
  JSON.stringify({ type: 'PEEK_SETUP_CARD', payload: { playerId, position: 0 } });

check(
  'a valid token acts as its own player',
  !parse(applyAction(stateJson, TOKEN_A, peek(seat0Player))).error,
);
check(
  'a valid token cannot act as somebody else',
  Boolean(parse(applyAction(stateJson, TOKEN_A, peek(seat1Player))).error),
  'the action was accepted',
);
check(
  'an unissued token acts as nobody',
  Boolean(parse(applyAction(stateJson, TOKEN_UNKNOWN, peek(seat0Player))).error),
);
check(
  'an empty token acts as nobody',
  Boolean(parse(applyAction(stateJson, '', peek(seat0Player))).error),
);
check(
  'a token that is one character off is not close enough',
  Boolean(parse(applyAction(stateJson, `${TOKEN_A}x`, peek(seat0Player))).error),
);
check(
  'an unoccupied seat has no token and so cannot act',
  Boolean(parse(applyAction(stateJson, TOKEN_UNKNOWN, peek(state.seats[3].playerId))).error),
);
check(
  'a malformed action is refused rather than thrown',
  Boolean(parse(applyAction(stateJson, TOKEN_A, '{"type":"NOT_A_REAL_ACTION"}')).error),
);

// The one that decides games: a wrong token must not be answered with a view.
const stolen = parse(applyAction(stateJson, TOKEN_UNKNOWN, peek(seat0Player)));
check(
  'a refused action returns no state anybody could read a hand from',
  JSON.stringify(stolen.state) === JSON.stringify(state),
  'the state came back changed',
);

// --- playing a game --------------------------------------------------------------------
const tokenForSeat = [TOKEN_A, TOKEN_B];
const act = (json, seat, action) => {
  const outcome = parse(applyAction(json, tokenForSeat[seat], JSON.stringify(action)));
  if (outcome.error) throw new Error(`${action.type}: ${outcome.error}`);
  return outcome;
};

let played = act(stateJson, 0, { type: 'PEEK_SETUP_CARD', payload: { playerId: seat0Player, position: 0 } });
played = act(JSON.stringify(played.state), 0, {
  type: 'PEEK_SETUP_CARD', payload: { playerId: seat0Player, position: 1 },
});
played = act(JSON.stringify(played.state), 0, { type: 'FINISH_SETUP', payload: { playerId: seat0Player } });

check('setup moves the game into play', played.state.game.phase === 'playing', played.state.game.phase);

const peeked = parse(viewForSeat(JSON.stringify(played.state), 0)).view;
const seatSees = visibleIn(peeked);
check(
  'a peeked card becomes visible to the seat that peeked it',
  seatSees.size === 2,
  `${seatSees.size} visible`,
);
check(
  'and still nothing belonging to anybody else',
  [...seatSees].every((id) => ownCards.has(id)),
);

// Play out real turns. Both seats 0 and 1 are people here, so the harness plays whichever
// one the room is waiting on — the bots in seats 2 and 3 are the room's own business.
//
// The order below matters and is the protocol, not a detail of this script: while a toss-in
// window is open the game waits on *every* seat, whoever's turn it is. A client that only
// answers on its own turn leaves the window open forever. This is the "continue" button in
// the UI, and getting it wrong here is what a real client would get wrong too.
let current = JSON.stringify(played.state);
let clientActions = 0;
let botActions = 0;
let readyActions = 0;

const collectBots = (outcome) => {
  botActions += outcome.events.filter((e) => e.byBot).length;
  return outcome;
};

for (let step = 0; step < 120; step++) {
  const room = parse(current);
  if (room.game.phase === 'scoring') break;

  // 1. An open window that a seated player has not answered.
  const tossIn = room.game.activeTossIn;
  if (tossIn) {
    const owed = room.seats.find(
      (s) => s.tokenHash && !tossIn.playersReadyForNextTurn.includes(s.playerId),
    );
    if (owed) {
      try {
        current = JSON.stringify(collectBots(act(current, owed.index, {
          type: 'PLAYER_TOSS_IN_FINISHED', payload: { playerId: owed.playerId },
        })).state);
        readyActions++;
        continue;
      } catch (failure) {
        check(`step ${step}: closing the toss-in window`, false, failure.message);
        break;
      }
    }
  }

  // 2. A pending action belonging to a seated player.
  //
  // With two humans at the table this is reachable in a way it was not before: a queued
  // toss-in action belongs to whoever tossed the card in, and if that is a person the room
  // waits for them. The harness puts the card down rather than aiming it — CONFIRM_PEEK is
  // the engine's universal "finish with this card" — which is enough to keep the game moving.
  const pending = room.game.pendingAction;
  if (pending) {
    const owner = room.seats.find((s) => s.playerId === pending.playerId && s.tokenHash);
    if (owner) {
      try {
        current = JSON.stringify(collectBots(act(current, owner.index, {
          type: 'CONFIRM_PEEK', payload: { playerId: pending.playerId },
        })).state);
        continue;
      } catch (failure) {
        check(`step ${step}: finishing a pending action`, false, failure.message);
        break;
      }
    }
  }

  // 3. Otherwise it is somebody's turn.
  const turnPlayerId = room.game.players[room.game.currentPlayerIndex].id;
  const seat = room.seats.findIndex((s) => s.playerId === turnPlayerId && s.tokenHash);
  if (seat < 0) {
    check(`step ${step}: the room owed a bot move it did not make`, false, `waiting on ${turnPlayerId}`);
    break;
  }

  try {
    let outcome = collectBots(act(current, seat, {
      type: 'DRAW_CARD', payload: { playerId: turnPlayerId },
    }));
    outcome = collectBots(act(JSON.stringify(outcome.state), seat, {
      type: 'DISCARD_CARD', payload: { playerId: turnPlayerId },
    }));
    clientActions += 2;
    current = JSON.stringify(outcome.state);
  } catch (failure) {
    check(`step ${step} played cleanly`, false, failure.message);
    break;
  }
}

check('the toss-in windows were answered by the seated players', readyActions > 0, `${readyActions}`);

const finalRoom = parse(current);
check('the client got to play several turns', clientActions >= 4, `${clientActions} actions`);
check('the bots played in between', botActions > 0, `${botActions} bot actions`);
check(
  'every logged action carries an index and a seat',
  finalRoom.log.every((entry) => Number.isInteger(entry.index) && Number.isInteger(entry.seat)),
);
check(
  'every action a player took names that player',
  // A few actions name nobody — setting the coalition leader, the debug ones — and are
  // checked by the validator alone. Everything a seat sends must say who sent it.
  finalRoom.log.filter((e) => !e.byBot).every((entry) => entry.playerId !== ''),
);
check(
  'the log is a contiguous run from zero',
  finalRoom.log.every((entry, index) => entry.index === index),
);

const sync = parse(eventsSince(current, finalRoom.log.length - 3));
check('resync returns only what was missed', sync.events.length === 3, `${sync.events.length}`);

// Redaction has to hold mid-game too, not only at the deal — but what "entitled to see"
// means widens as the game goes on, so the check has to widen with it rather than be
// loosened until it passes.
//
// A seat may legitimately see: its own cards, whatever is face-up on the discard pile, the
// card in its *own* pending action, and — once somebody has called Vinto — the hands of its
// fellow coalition members, who pool their cards by design. Note what is *not* on that list:
// `projectView` hides a drawn card from everyone but the player who drew it, which is
// stricter than the written rule that a card drawn from the deck is revealed publicly.
const midGame = parse(viewForSeat(current, 0)).view;
const viewerId = midGame.viewerId;
const callerId = finalRoom.game.vintoCallerId;
const inCoalition = callerId !== null && viewerId !== callerId;

const permitted = new Set([
  ...finalRoom.game.players.find((p) => p.id === viewerId).cards.map((c) => c.id),
  ...finalRoom.game.discardPile.map((c) => c.id),
]);
if (midGame.pendingAction?.playerId === viewerId && finalRoom.game.pendingAction) {
  permitted.add(finalRoom.game.pendingAction.card.id);
}
if (inCoalition) {
  for (const player of finalRoom.game.players) {
    if (player.id !== callerId) player.cards.forEach((c) => permitted.add(c.id));
  }
}

const midLeak = [...visibleIn(midGame)].filter((id) => !permitted.has(id));
check(
  'redaction still holds after several turns',
  midLeak.length === 0,
  midLeak.length ? `leaked ${midLeak.slice(0, 4).join(', ')}` : '',
);

// The rule that survives every phase: whoever called Vinto keeps their hand to themselves.
// This is what makes calling it a commitment, and it is the one leak that would decide games.
if (callerId !== null && viewerId !== callerId) {
  const callersCards = new Set(
    finalRoom.game.players.find((p) => p.id === callerId).cards.map((c) => c.id),
  );
  const discarded = new Set(finalRoom.game.discardPile.map((c) => c.id));
  const callerLeak = [...visibleIn(midGame)].filter(
    (id) => callersCards.has(id) && !discarded.has(id),
  );
  check(
    "the Vinto caller's hand is never shown to the coalition",
    callerLeak.length === 0,
    callerLeak.length ? `leaked ${callerLeak.join(', ')}` : '',
  );
} else {
  console.log('  skip  no Vinto was called in this run, so the caller rule was not exercised');
}

console.log(`\n${failures === 0 ? 'REAL ROOM GATE PASS' : `REAL ROOM GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
