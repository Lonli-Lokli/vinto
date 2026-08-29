/**
 * The room-code namespace, exercised without wrangler.
 *
 *   node worker/cloudflare/gate-registry.mjs
 *
 * The point of the registry is a negative: an unissued code must reach nothing. That is hard
 * to assert from outside a live runtime, so it is asserted from two sides — here, that the
 * Kotlin resolver refuses a code it never minted, and in `gate-two-clients.mjs`, that a
 * refused code leaves the Durable Object count unchanged.
 */
import {
  newRegistry, mintRoomCode, resolveRoomCode, listPublicRooms, forgetRoom, registrySize, touchRoom,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};
const parse = JSON.parse;

/** Stands in for `crypto.getRandomValues`, which is the platform's job in `index.mjs`. */
const bytes = (...values) => values.join(',');

console.log('\nRoom-code registry\n');

let registryJson = newRegistry();
check('a fresh registry is empty', registrySize(registryJson) === 0);

// --- minting -----------------------------------------------------------------------------
let result = parse(mintRoomCode(registryJson, bytes(0, 1, 2, 3, 4, 5), false, 'Ada', 'gate-source'));
check('minting returns a room', Boolean(result.room) && !result.error);
check('the code is six characters', result.room.code.length === 6, result.room.code);

const AMBIGUOUS = ['0', 'O', '1', 'I', 'L'];
check(
  'the alphabet excludes every glyph that is read wrong aloud',
  [...result.room.code].every((c) => !AMBIGUOUS.includes(c)),
  result.room.code,
);
check('the code is deterministic in its bytes', 
  parse(mintRoomCode(newRegistry(), bytes(0, 1, 2, 3, 4, 5), false, '', 'gate-source')).room.code === result.room.code);

registryJson = JSON.stringify(result.state);
const privateCode = result.room.code;
check('the registry now holds one room', registrySize(registryJson) === 1);

// --- resolving ---------------------------------------------------------------------------
check('a minted code resolves', parse(resolveRoomCode(registryJson, privateCode)).known === true);
check(
  'resolving is case-insensitive, because people type codes',
  parse(resolveRoomCode(registryJson, privateCode.toLowerCase())).known === true,
);
check(
  'a code the registry never issued resolves to nothing',
  parse(resolveRoomCode(registryJson, 'ZZZZZZ')).known === false,
);
check('an empty code resolves to nothing', parse(resolveRoomCode(registryJson, '')).known === false);
check(
  'and an unknown code names no room, so there is nothing to route to',
  parse(resolveRoomCode(registryJson, 'ZZZZZZ')).room === null,
);

// --- collisions --------------------------------------------------------------------------
const collision = parse(mintRoomCode(registryJson, bytes(0, 1, 2, 3, 4, 5), false, '', 'gate-source'));
check(
  'the same bytes twice is refused rather than silently reusing a room',
  Boolean(collision.error),
  collision.error ? '' : 'a second room took the same code',
);
check('and the registry is unchanged by the refusal', registrySize(JSON.stringify(collision.state)) === 1);

// --- public and private ------------------------------------------------------------------
result = parse(mintRoomCode(registryJson, bytes(9, 9, 9, 9, 9, 9), true, 'Bo', 'gate-source'));
registryJson = JSON.stringify(result.state);
const publicCode = result.room.code;

const NOW = 1_700_000_000_000;
const listed = parse(listPublicRooms(registryJson, NOW));
check('the public room is listed', listed.rooms.some((r) => r.code === publicCode));
check('the private one is not', !listed.rooms.some((r) => r.code === privateCode));
check(
  'but the private one is still joinable by code',
  parse(resolveRoomCode(registryJson, privateCode)).known === true,
);
check('the listing carries the host nickname', listed.rooms[0].hostNickname === 'Bo');

// The listing is an allow-list, so this is the whole of it — not "the room minus the fields
// we remembered to strip". A field added to the registry must fail this until somebody has
// decided, on purpose, that strangers may read it.
const LISTABLE = ['code', 'hostNickname', 'humans', 'seatsFilled', 'msUntilStart'];
check(
  'the listing carries nothing but what a browser is meant to see',
  Object.keys(listed.rooms[0]).every((k) => LISTABLE.includes(k)),
  Object.keys(listed.rooms[0]).join(','),
);
check(
  'and neither the object name nor the source that opened it',
  listed.rooms.every((r) => r.roomId === undefined && r.sourceId === undefined),
  JSON.stringify(Object.keys(listed.rooms[0])),
);

// A nickname posted straight to the endpoint, which is the only way it arrives.
const shouty = parse(mintRoomCode(
  registryJson,
  bytes(11, 12, 13, 14, 15, 16),
  true,
  `${'A'.repeat(200)}\u0000<script>`,
  'gate-source',
));
const cleaned = parse(listPublicRooms(JSON.stringify(shouty.state), NOW))
  .rooms.find((r) => r.code === shouty.room.code);
check(
  'a host nickname is cleaned and cut to length before anybody else reads it',
  cleaned.hostNickname === 'A'.repeat(16),
  cleaned.hostNickname,
);

// --- what a lobby browser needs -----------------------------------------------------------
const touched = touchRoom(registryJson, publicCode, 3, 4, 1_234_567);
const browsing = parse(listPublicRooms(touched, NOW)).rooms.find((r) => r.code === publicCode);
check('a touched room reports how full it is', browsing.humans === 3 && browsing.seatsFilled === 4);

// A duration rather than a deadline, resolved against the service's clock: a browser reading
// an absolute time would render its own clock's error as somebody else's countdown.
const soon = touchRoom(touched, publicCode, 3, 4, NOW + 5_000);
check(
  'and how long until it deals',
  parse(listPublicRooms(soon, NOW)).rooms.find((r) => r.code === publicCode).msUntilStart === 5_000,
);
check(
  'a countdown that has already run out is zero rather than negative',
  parse(listPublicRooms(soon, NOW + 9_000)).rooms.find((r) => r.code === publicCode).msUntilStart === 0,
);
check(
  'zero means no countdown rather than a start at the epoch',
  parse(listPublicRooms(touchRoom(touched, publicCode, 2, 2, 0), NOW))
    .rooms.find((r) => r.code === publicCode).msUntilStart === null,
);
check(
  'touching a code the registry does not know changes nothing',
  registrySize(touchRoom(registryJson, 'ZZZZZZ', 4, 4, 1)) === registrySize(registryJson),
);

// --- forgetting --------------------------------------------------------------------------
registryJson = forgetRoom(registryJson, publicCode);
check('a forgotten room is gone from the registry', registrySize(registryJson) === 1);
check('and no longer resolves', parse(resolveRoomCode(registryJson, publicCode)).known === false);
check('and is gone from the public list', parse(listPublicRooms(registryJson, NOW)).rooms.length === 0);
check(
  'forgetting twice is not an error, because a room that dies twice is a retry',
  registrySize(forgetRoom(registryJson, publicCode)) === 1,
);
check('forgetting one room leaves the others', parse(resolveRoomCode(registryJson, privateCode)).known === true);

console.log(`\n${failures === 0 ? 'REGISTRY GATE PASS' : `REGISTRY GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
