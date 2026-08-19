/**
 * The room's lifecycle: who is held, what ends, and what is deleted.
 *
 *   node kmp/worker/cloudflare/gate-lifecycle.mjs
 *
 * Every deadline is driven by passing `now` forward. That is deliberate and it is stricter
 * than waiting: a Durable Object has ONE alarm and five things that can be due, so the only
 * way to be sure is to arrive at an arbitrary time and ask what expired. A harness that slept
 * would only ever test the deadline it expected.
 */
import {
  newRoom, joinRoom, addBot, startGame, updatePresence, onAlarm, lobbyView, countdownMs,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};
const parse = JSON.parse;

const T0 = 1_000_000;
const SECOND = 1000;
const MINUTE = 60 * SECOND;

const A = 'token-ada', B = 'token-bo';

/** Two humans, two bots, dealt and playing. The state most of these questions are about. */
function playingRoom(now = T0) {
  let json = newRoom('life', 99, 'moderate', now);
  json = JSON.stringify(parse(joinRoom(json, A, 'Ada', now)).state);
  json = JSON.stringify(parse(joinRoom(json, B, 'Bo', now)).state);
  json = JSON.stringify(parse(addBot(json, A, now)).state);
  json = JSON.stringify(parse(addBot(json, A, now)).state);
  json = JSON.stringify(parse(startGame(json, now + countdownMs())).state);
  return JSON.stringify(parse(updatePresence(json, '0,1', now)).state);
}

console.log('\nRoom lifecycle\n');

// --- one alarm, whichever is soonest --------------------------------------------------------
let json = playingRoom();
let result = parse(updatePresence(json, '0,1', T0));
// With everyone present the only thing still due is the session buzzer — thirty minutes from
// the deal, which happened when the countdown expired.
check(
  'with everyone present, the session clock is the only thing pending',
  result.nextAlarmAtEpochMs === parse(json).session.endsAtEpochMs,
  `${result.nextAlarmAtEpochMs} vs ${parse(json).session.endsAtEpochMs}`,
);
check('and that is thirty minutes from the deal',
  parse(json).session.endsAtEpochMs === T0 + countdownMs() + 30 * 60 * 1000);

// --- seat grace: the seat is held, then played, and it stays yours ----------------------------
result = parse(updatePresence(json, '0', T0));              // Bo drops
json = JSON.stringify(result.state);
check('a dropped seat schedules a grace', result.nextAlarmAtEpochMs === T0 + 30 * SECOND,
  `${result.nextAlarmAtEpochMs - T0}ms`);

let early = parse(onAlarm(json, T0 + 20 * SECOND));
check('and nothing happens before it expires', early.tookOver.length === 0);
check('the seat is still a person', !parse(json).seats[1].isBot);

result = parse(onAlarm(json, T0 + 31 * SECOND));
const takenOver = JSON.stringify(result.state);
check('when it expires a bot takes the seat', result.tookOver.includes(1), `${result.tookOver}`);
check('the seat is a bot now', parse(takenOver).seats[1].isBot);
check(
  'but it still belongs to its token — it was held, not given away',
  parse(takenOver).seats[1].tokenHash !== null,
);
check('and a stranger cannot take it', parse(joinRoom(takenOver, 'token-stranger', 'Zed', T0)).error !== null);

// --- coming back ------------------------------------------------------------------------------
result = parse(joinRoom(takenOver, B, 'Bo', T0 + 40 * SECOND));
check('the owner reconnects to the same seat', result.seat === 1, `seat ${result.seat}`);
check(
  'and is told a bot played while they were away',
  result.botPlayedWhileAway === true,
  'the hand changed silently',
);
check('the seat is a person again', !parse(JSON.stringify(result.state)).seats[1].isBot);
check(
  'and the notice is not repeated',
  parse(joinRoom(JSON.stringify(result.state), B, 'Bo', T0 + 41 * SECOND)).botPlayedWhileAway === false,
);

// --- lonely grace: a game for one person ends -------------------------------------------------
json = playingRoom();
result = parse(updatePresence(json, '0', T0));   // Bo leaves; Ada alone with two bots
const lonely = JSON.stringify(result.state);
check(
  'dropping below two humans starts the lonely clock',
  parse(lonely).lonelyUntilEpochMs === T0 + 60 * SECOND,
);
check(
  'and the seat grace runs in parallel, not instead',
  parse(lonely).seatGrace['1'] === T0 + 30 * SECOND,
  JSON.stringify(parse(lonely).seatGrace),
);
check(
  'the earliest of the two is what gets scheduled',
  result.nextAlarmAtEpochMs === T0 + 30 * SECOND,
  `${result.nextAlarmAtEpochMs - T0}ms`,
);

result = parse(onAlarm(lonely, T0 + 61 * SECOND));
check('the session ends when the lonely clock runs out', result.state.phase === 'FINISHED',
  result.state.phase);
check('and it is not deleted immediately — the scoreboard is worth a moment',
  result.deleted === false);

// A second human returning in time saves it.
const saved = parse(updatePresence(lonely, '0,1', T0 + 30 * SECOND));
check('a returning human cancels the lonely clock', saved.state.lonelyUntilEpochMs === null);
check('and the session carries on', parse(onAlarm(JSON.stringify(saved.state), T0 + 61 * SECOND))
  .state.phase === 'PLAYING');

// --- deletion ---------------------------------------------------------------------------------
json = playingRoom();
const abandoned = JSON.stringify(parse(updatePresence(json, '', T0)).state);
check('with nobody connected the room is on the clock', parse(abandoned).emptyUntilEpochMs === T0 + 2 * MINUTE);
check('and is deleted when it runs out', parse(onAlarm(abandoned, T0 + 3 * MINUTE)).deleted === true);
check('but not before', parse(onAlarm(abandoned, T0 + 1 * MINUTE)).deleted === false);

const emptyLobby = newRoom('life', 99, 'moderate', T0);
check(
  'a lobby nobody ever joined is deleted too',
  parse(onAlarm(emptyLobby, T0 + 3 * MINUTE)).deleted === true,
);

// A lobby with somebody in it survives the room TTL but not the lobby TTL.
let waiting = JSON.stringify(parse(joinRoom(emptyLobby, A, 'Ada', T0)).state);
waiting = JSON.stringify(parse(updatePresence(waiting, '0', T0)).state);
check('a lobby with somebody waiting is not deleted at two minutes',
  parse(onAlarm(waiting, T0 + 3 * MINUTE)).deleted === false);
check('but is at ten, because a game that never starts is still a storage row',
  parse(onAlarm(waiting, T0 + 11 * MINUTE)).deleted === true);

const finished = parse(onAlarm(JSON.stringify(parse(updatePresence(playingRoom(), '0', T0)).state),
  T0 + 61 * SECOND)).state;
check('a finished room is deleted ten minutes later',
  parse(onAlarm(JSON.stringify(finished), T0 + 61 * SECOND + 11 * MINUTE)).deleted === true);

// --- the alarm never assumes which deadline woke it ---------------------------------------------
json = playingRoom();
const both = JSON.stringify(parse(updatePresence(json, '', T0)).state);
const late = parse(onAlarm(both, T0 + 10 * MINUTE));
check(
  'arriving late, everything overdue is resolved rather than the one expected',
  late.deleted === true,
  'a late alarm did the wrong thing',
);

console.log(`\n${failures === 0 ? 'LIFECYCLE GATE PASS' : `LIFECYCLE GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
