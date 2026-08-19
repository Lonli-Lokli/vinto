/**
 * The lobby: who may start a game, and when.
 *
 *   node kmp/worker/cloudflare/gate-lobby.mjs
 *
 * The countdown is driven by passing `now` forward rather than by sleeping. That is not only
 * faster — it is the *stricter* test. A harness that waited would pass against an in-memory
 * timer, and the countdown has to be an alarm, because a lobby with nobody typing is exactly
 * when a Durable Object is evicted. Here the clock only ever moves because a call moved it,
 * which is the same thing an alarm firing after an eviction looks like.
 */
import {
  newRoom, joinRoom, addBot, removeBot, startGame, lobbyView, applyAction, countdownMs, seatCount,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};
const parse = JSON.parse;

const T0 = 1_000_000;
const COUNTDOWN = countdownMs();

const fresh = () => newRoom('lobby-room', 4242, 'moderate', T0);
const join = (json, token, name, now) => parse(joinRoom(json, token, name, now));
const bot = (json, token, now) => parse(addBot(json, token, now));
const unbot = (json, token, seat, now) => parse(removeBot(json, token, seat, now));
const lobby = (json, now) => parse(lobbyView(json, now));

console.log('\nLobby\n');

// --- an empty room ------------------------------------------------------------------------
let state = fresh();
let view = lobby(state, T0);
check('a new room is a lobby, not a game', view.phase === 'LOBBY', view.phase);
check('with four empty seats', view.seats.length === seatCount() && view.seats.every((s) => !s.occupied));
check('and no countdown', view.msUntilStart === null);
check('the game is not dealt yet', parse(state).game === null);

// --- one human cannot start, however many bots they add ------------------------------------
let result = join(state, 'token-ada', 'Ada', T0);
state = JSON.stringify(result.state);
check('the first human takes a seat', result.seat === 0);

for (let i = 0; i < 3; i++) {
  result = bot(state, 'token-ada', T0);
  state = JSON.stringify(result.state);
}
view = lobby(state, T0);
check('one human may fill every other seat with bots', view.seats.every((s) => s.occupied));
check(
  'and STILL no countdown, because a game needs two people',
  view.phase === 'LOBBY' && view.msUntilStart === null,
  `${view.phase}, humans ${view.humans}`,
);
check(
  'and starting is refused outright',
  Boolean(parse(startGame(state, T0 + COUNTDOWN)).error),
);

// --- a second human displaces a bot and the countdown begins -------------------------------
result = join(state, 'token-bo', 'Bo', T0);
state = JSON.stringify(result.state);
check('a newcomer takes a filler bot’s seat rather than being turned away', result.seat >= 0);

view = lobby(state, T0);
check('two humans and a full table starts the countdown', view.phase === 'STARTING', view.phase);
check('which is ten seconds', view.msUntilStart === COUNTDOWN, `${view.msUntilStart}`);
check('and the game is still not dealt', parse(state).game === null);

// --- anybody may cancel it -----------------------------------------------------------------
const botSeat = lobby(state, T0).seats.find((s) => s.removable);
check('a filler bot is marked removable', Boolean(botSeat));

// Bo did not add that bot; Bo may still take it out. That is what makes "anyone may add one"
// a proposal rather than a unilateral decision.
result = unbot(state, 'token-bo', botSeat.index, T0 + 7000);
const cancelled = JSON.stringify(result.state);
view = lobby(cancelled, T0 + 7000);
check('a player who did not add the bot can remove it', !result.error, result.error);
check('removing it cancels the countdown', view.phase === 'LOBBY' && view.msUntilStart === null);

// --- refilling restarts the FULL countdown --------------------------------------------------
result = bot(cancelled, 'token-ada', T0 + 7000);
const refilled = JSON.stringify(result.state);
view = lobby(refilled, T0 + 7000);
check('refilling starts a countdown again', view.phase === 'STARTING');
check(
  'and it is the full ten seconds, not the three that were left',
  view.msUntilStart === COUNTDOWN,
  `${view.msUntilStart}ms remaining`,
);

// --- a late human joins mid-countdown without pushing it back --------------------------------
const beforeLate = lobby(refilled, T0 + 8000).msUntilStart;
result = join(refilled, 'token-cal', 'Cal', T0 + 8000);
const withCal = JSON.stringify(result.state);
view = lobby(withCal, T0 + 8000);
check('a late human takes a bot’s seat', result.seat >= 0 && !result.error, result.error);
check('the table now has three humans', view.humans === 3, `${view.humans}`);
check(
  'and the countdown carries on rather than restarting',
  view.msUntilStart === beforeLate,
  `${beforeLate} → ${view.msUntilStart}`,
);

// --- starting ---------------------------------------------------------------------------------
check(
  'the game will not start before the countdown expires',
  Boolean(parse(startGame(withCal, T0 + 9000)).error),
);

// The alarm fires. Note nothing slept: the clock moved because a call moved it, which is
// exactly what an alarm firing after an eviction looks like.
result = parse(startGame(withCal, T0 + 7000 + COUNTDOWN));
const playing = JSON.stringify(result.state);
check('the countdown expiring deals the game', !result.error, result.error);

const started = parse(playing);
check('the room is playing', started.phase === 'PLAYING');
check('and the game exists now, not before', started.game !== null);
check('every seat has a player', started.seats.every((s) => s.playerId !== null));
check(
  'the three humans are humans to the engine',
  started.game.players.filter((p) => p.isHuman).length === 3,
  `${started.game.players.filter((p) => p.isHuman).length}`,
);
check(
  'and none of them was handed a peek they never took',
  started.seats
    .filter((s) => s.tokenHash !== null)
    .every((s) => started.game.players.find((p) => p.id === s.playerId).knownCardPositions.length === 0),
);
check(
  'while the bot seat keeps the peek a bot is entitled to',
  started.seats
    .filter((s) => s.tokenHash === null)
    .every((s) => started.game.players.find((p) => p.id === s.playerId).knownCardPositions.length === 2),
);

// --- the lobby closes behind you ---------------------------------------------------------------
check('joining a started game is refused', Boolean(join(playing, 'token-dee', 'Dee', T0 + 30000).error));
check('adding a bot to a started game is refused', Boolean(bot(playing, 'token-ada', T0 + 30000).error));
check(
  'and an action in a lobby is refused before it reaches the engine',
  Boolean(parse(applyAction(state, 'token-ada', '{"type":"DRAW_CARD","payload":{"playerId":"human-1"}}', T0)).error),
);

// --- only seated players may touch the seats ------------------------------------------------------
check(
  'a stranger cannot add a bot',
  Boolean(bot(fresh(), 'token-nobody', T0).error),
);
check(
  'a stranger cannot remove one',
  Boolean(unbot(refilled, 'token-nobody', botSeat.index, T0).error),
);

// --- profiles ------------------------------------------------------------------------------
//
// A nickname lives in a *profile* on the seat rather than loose beside it, so the next thing
// anybody wants to display — an avatar, a flag, a pronoun — is a field there rather than a
// change to every message that carries a seat.
const named = (raw) => parse(joinRoom(fresh(), `tok-${raw}`, raw, T0)).state.seats[0].profile.nickname;

console.log('  — profiles');
check('a nickname is carried in a profile record', typeof named('Ada') === 'string');
check('padding is trimmed', named('   Ada   ') === 'Ada');
check('inner whitespace is collapsed', named('Ada    Lovelace') === 'Ada Lovelace');
check('an empty name gets one', named('') === 'Player 1', named(''));
check('so does a blank one', named('   ') === 'Player 1');
check('a long one is cut to sixteen', named('A'.repeat(40)).length === 16);
check('markup is stripped rather than escaped', named('Ada<script>') === 'Adascript');
check('ordinary punctuation survives', named("O'Brien-1.0_x") === "O'Brien-1.0_x");
check(
  'and so do non-Latin scripts, which a naive [A-Za-z] filter would delete',
  named('\u65e5\u672c\u8a9e') === '\u65e5\u672c\u8a9e',
  named('\u65e5\u672c\u8a9e'),
);

// Not unique, deliberately: two players may both be Bob and the view separates them by seat.
// Rejecting duplicates would be worse than the ambiguity, and would leak who is already here.
let twoBobs = fresh();
twoBobs = JSON.stringify(parse(joinRoom(twoBobs, 'tok-1', 'Bob', T0)).state);
const second = parse(joinRoom(twoBobs, 'tok-2', 'Bob', T0));
check('two players may share a nickname', second.seat === 1 && !second.error, second.error);
check(
  'and are told apart by seat, not by name',
  parse(JSON.stringify(second.state)).seats.slice(0, 2)
    .every((s) => s.profile.nickname === 'Bob'),
);

console.log(`\n${failures === 0 ? 'LOBBY GATE PASS' : `LOBBY GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
