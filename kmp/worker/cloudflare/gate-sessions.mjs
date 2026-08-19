/**
 * A session: several rounds, thirty minutes, and what the buzzer does to the one in progress.
 *
 *   node kmp/worker/cloudflare/gate-sessions.mjs
 *
 * The clock is driven by passing `now` forward — which is also the point. The engine has no
 * clock at all, and a session ending is expressed to it as "no further rounds"; every question
 * below is therefore about the *room*, and can be asked without one second passing.
 */
import {
  newRoom, joinRoom, addBot, startGame, applyAction, onAlarm, readyForNextRound,
  viewForSeat, countdownMs, sessionMs, updatePresence,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};
const parse = JSON.parse;

const T0 = 1_000_000;
const A = 'token-ada', B = 'token-bo';
const SESSION = sessionMs();

/** Two humans, two bots, dealt. Returns the room the moment play begins. */
function dealtRoom(now = T0) {
  let json = newRoom('sess', 555, 'moderate', now);
  json = JSON.stringify(parse(joinRoom(json, A, 'Ada', now)).state);
  json = JSON.stringify(parse(joinRoom(json, B, 'Bo', now)).state);
  json = JSON.stringify(parse(addBot(json, A, now)).state);
  json = JSON.stringify(parse(addBot(json, A, now)).state);
  json = JSON.stringify(parse(startGame(json, now + countdownMs())).state);
  // Both humans are here. Without this the room is on the empty-room clock from the moment it
  // was made, and the buzzer never gets a look in — it is deleted first, correctly.
  return JSON.stringify(parse(updatePresence(json, '0,1', now)).state);
}

const tokens = [A, B];
/** Plays until the game reaches scoring, or gives up. Returns the room. */
function playToScoring(json, startClock) {
  let clock = startClock;
  const act = (state, seat, action) => {
    clock += 1000;
    return parse(applyAction(state, tokens[seat], JSON.stringify(action), clock));
  };

  for (let step = 0; step < 400; step++) {
    const room = parse(json);
    if (!room.game || room.game.phase === 'scoring' || room.phase !== 'PLAYING') break;

    const toss = room.game.activeTossIn;
    if (toss) {
      const owed = room.seats.find(
        (s) => s.tokenHash && s.index < 2 && !toss.playersReadyForNextTurn.includes(s.playerId),
      );
      if (owed) {
        const out = act(json, owed.index, {
          type: 'PLAYER_TOSS_IN_FINISHED', payload: { playerId: owed.playerId },
        });
        if (out.error) break;
        json = JSON.stringify(out.state);
        continue;
      }
    }

    const pending = room.game.pendingAction;
    if (pending) {
      const owner = room.seats.find((s) => s.playerId === pending.playerId && s.tokenHash && s.index < 2);
      if (owner) {
        const out = act(json, owner.index, { type: 'CONFIRM_PEEK', payload: { playerId: pending.playerId } });
        if (out.error) break;
        json = JSON.stringify(out.state);
        continue;
      }
    }

    const turnId = room.game.players[room.game.currentPlayerIndex].id;
    const seat = room.seats.findIndex((s) => s.playerId === turnId && s.tokenHash && s.index < 2);
    if (seat < 0) break;

    // Call Vinto when we can, since a round has to *end* for a session to have rounds in it.
    let out = act(json, seat, { type: 'CALL_VINTO', payload: { playerId: turnId } });
    if (out.error) {
      out = act(json, seat, { type: 'DRAW_CARD', payload: { playerId: turnId } });
      if (out.error) break;
      const drawn = act(JSON.stringify(out.state), seat, {
        type: 'DISCARD_CARD', payload: { playerId: turnId },
      });
      if (drawn.error) break;
      out = drawn;
    }
    json = JSON.stringify(out.state);
  }
  return { json, clock };
}

console.log('\nSessions of rounds\n');

// --- the clock ------------------------------------------------------------------------------
let json = dealtRoom();
let room = parse(json);
check('a session lasts thirty minutes', SESSION === 30 * 60 * 1000, `${SESSION}`);
check(
  'measured from the first deal, not from when the room was made',
  room.session.endsAtEpochMs === T0 + countdownMs() + SESSION,
  `${room.session.endsAtEpochMs - T0}`,
);
check('and it starts with no rounds behind it', room.session.rounds.length === 0);

// --- the players can see the deadline they are playing against ---------------------------------
const view = parse(viewForSeat(json, 0, T0 + countdownMs())).view;
check(
  'the remaining time is in the view',
  view.sessionMsRemaining === SESSION,
  `${view.sessionMsRemaining}`,
);
check(
  'and it counts down',
  parse(viewForSeat(json, 0, T0 + countdownMs() + 60_000)).view.sessionMsRemaining === SESSION - 60_000,
);
check(
  'never past zero',
  parse(viewForSeat(json, 0, T0 + countdownMs() + SESSION + 5_000)).view.sessionMsRemaining === 0,
);

// --- a round finishing, inside the session ------------------------------------------------------
let played = playToScoring(json, T0 + countdownMs());
room = parse(played.json);
check('a round that finishes is recorded', room.session.rounds.length === 1, `${room.session.rounds.length}`);
check('and the room waits between rounds', room.phase === 'BETWEEN_ROUNDS', room.phase);
check('with the round scored', Object.keys(room.session.rounds[0].points).length === 4);
check(
  'and the standings are the points so far',
  JSON.stringify(room.session.rounds[0].points) !== '{}',
);

// --- another round ------------------------------------------------------------------------------
let next = parse(readyForNextRound(played.json, A, played.clock));
check('one player agreeing is not enough', parse(JSON.stringify(next.state)).phase === 'BETWEEN_ROUNDS');
next = parse(readyForNextRound(JSON.stringify(next.state), B, played.clock));
const secondRound = JSON.stringify(next.state);
check('both agreeing deals another', parse(secondRound).phase === 'PLAYING', parse(secondRound).phase);
check('the round count carries', parse(secondRound).session.rounds.length === 1);
check(
  'and the second deal is a DIFFERENT one, from a seed derived from the session',
  JSON.stringify(parse(secondRound).game.drawPile) !== JSON.stringify(parse(json).game.drawPile),
);
check(
  'the session clock is untouched by a new round',
  parse(secondRound).session.endsAtEpochMs === room.session.endsAtEpochMs,
);

// --- the buzzer, with no Vinto declared ----------------------------------------------------------
const quiet = dealtRoom();
const buzzed = parse(onAlarm(quiet, T0 + countdownMs() + SESSION + 1));
check('the buzzer ends the session', buzzed.state.phase === 'FINISHED', buzzed.state.phase);
check(
  'the round in progress is discarded, not scored',
  buzzed.state.session.rounds.length === 0 && buzzed.state.session.discardedRound === 1,
  `rounds ${buzzed.state.session.rounds.length}, discarded ${buzzed.state.session.discardedRound}`,
);
check(
  'and a session whose first round never finished ends with no winner',
  Object.keys(buzzed.state.session.rounds).length === 0,
);
check('the game is put away', buzzed.state.game === null);

// --- the buzzer, with Vinto declared --------------------------------------------------------------
let declared = dealtRoom();
const seat0 = parse(declared).seats[0].playerId;
const called = parse(applyAction(declared, A, JSON.stringify({
  type: 'CALL_VINTO', payload: { playerId: seat0 },
}), T0 + countdownMs() + 1000));
if (called.error) {
  console.log(`  skip  could not call Vinto in this deal (${called.error})`);
} else {
  declared = JSON.stringify(called.state);
  check('a Vinto was declared', parse(declared).game.vintoCallerId === seat0);

  const heldOpen = parse(onAlarm(declared, T0 + countdownMs() + SESSION + 1));
  check(
    'the buzzer does NOT discard a round somebody called Vinto in',
    heldOpen.state.phase !== 'FINISHED',
    `${heldOpen.state.phase}`,
  );
  check('the game is still there to be finished', heldOpen.state.game !== null);
  check('and nothing has been discarded', heldOpen.state.session.discardedRound === null);

  // Playing it out past the buzzer ends the session, and the round counts.
  const finished = playToScoring(JSON.stringify(heldOpen.state), T0 + countdownMs() + SESSION + 2);
  const done = parse(finished.json);
  check(
    'when it finishes, the session ends and the round is scored',
    done.phase === 'FINISHED' && done.session.rounds.length === 1,
    `${done.phase}, ${done.session.rounds.length} rounds`,
  );
}

console.log(`\n${failures === 0 ? 'SESSIONS GATE PASS' : `SESSIONS GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
