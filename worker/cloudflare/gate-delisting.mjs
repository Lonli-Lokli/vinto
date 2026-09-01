// A dead room leaves the public list — through the real glue, not a fixture's idea of it.
//
//   (cd .. && ./gradlew :worker:jsProductionExecutableCompileSync)
//   node gate-delisting.mjs        # plain Node; no wrangler needed
//
// Every other lifecycle gate drives the Kotlin exports directly, so the one seam none of them
// crossed was the two Durable Objects actually talking: the Request a dying Room sends and
// the handler the Registry answers it with. That seam is exactly where the ghost-room bug
// lived — the Room put the code in the body, the Registry read the query string, and every
// deletion ever sent removed the room whose code is the empty string, answered { ok: true }.
// Three abandoned lobbies sat on the live public list for hours.
//
// So this gate instantiates the real classes from index.mjs with in-memory storage, wires
// the Room's REGISTRY binding to the real Registry, and walks the whole story: minted,
// listed, joined, expired, deleted — and *gone from the list*, because the real forget met
// the real handler.

import { Registry, Room } from './index.mjs';
import {
  newRoom, joinRoom,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};

/** Durable Object storage, minus the durability: enough for one gate's lifetime. */
const storageOf = () => {
  const map = new Map();
  return {
    map,
    async get(key) { return map.get(key); },
    async put(key, value) { map.set(key, value); },
    async delete(key) { map.delete(key); },
    async deleteAll() { map.clear(); },
    async setAlarm() {},
    async deleteAlarm() {},
  };
};
const ctxOf = () => ({ storage: storageOf(), getWebSockets: () => [], waitUntil: () => {} });

const registry = new Registry(ctxOf(), {});
const env = {
  REGISTRY: { idFromName: () => 'registry', get: () => registry },
};

const ask = (path, init) => registry.fetch(new Request(`https://registry${path}`, init));
const publicCodes = async () => (await (await ask('/public')).json()).rooms.map((r) => r.code);

console.log('\nDelisting, through the real Room and the real Registry\n');

// --- a room is born and advertised ----------------------------------------------------------
const minted = await (await ask('/mint', {
  method: 'POST',
  body: JSON.stringify({ isPublic: true, hostNickname: 'Ada', sourceId: 'gate' }),
})).json();
check('a public room is minted', Boolean(minted.code), JSON.stringify(minted));
check('and listed', (await publicCodes()).includes(minted.code));

// --- it lives, is abandoned, and its own alarm buries it ------------------------------------
//
// The room's state is built with the same Kotlin exports the room itself uses, backdated
// eleven minutes so the lobby TTL is already due — the alarm handler reads the real clock,
// and this harness does not get to lie to it.
const past = Date.now() - 11 * 60 * 1000;
let stateJson = newRoom(`room-${minted.code}`, 42, 'moderate', past);
stateJson = JSON.stringify(JSON.parse(joinRoom(stateJson, 'token-ada', 'Ada', past)).state);

const roomCtx = ctxOf();
await roomCtx.storage.put('room', stateJson);
const room = new Room(roomCtx, env);

await room.alarm();

check('an expired lobby deletes its own storage', roomCtx.storage.map.size === 0);
check(
  'and leaves the public list — the forget really landed',
  !(await publicCodes()).includes(minted.code),
  `still listed: ${minted.code}`,
);
check(
  'and its code no longer resolves',
  (await (await ask(`/resolve?code=${minted.code}&source=stranger`)).json()).known === false,
);

// --- the tripwire that would have caught the original bug on day one ------------------------
//
// A forget whose body carries no plausible code is a caller bug, and it is refused out loud.
// The original mismatch produced exactly this shape — code resolved to '' — and was answered
// { ok: true }; success on a no-op delete is the silence the ghosts hid behind.
const vacuous = await ask('/forget', { method: 'POST', body: JSON.stringify({}) });
check('a forget with no code in the body is refused, not silently honoured', vacuous.status === 400);

const queryOnly = await ask(`/forget?code=${minted.code}`, { method: 'POST', body: '{}' });
check('and the query string alone does not name a room', queryOnly.status === 400);

console.log(failures === 0 ? '\nDELISTING GATE PASS' : `\nDELISTING GATE FAIL (${failures})`);
process.exit(failures === 0 ? 0 : 1);
