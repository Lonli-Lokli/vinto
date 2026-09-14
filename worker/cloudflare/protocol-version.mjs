// The wire's number, read from the one file that declares it.
//
// **This exists because a copy of it went stale and took CI down for five days.** Two gates sent a
// hand-typed `protocol` in their `join`, and `MIN_PROTOCOL` rose to 4 underneath them: the room
// refused `gate-two-clients` at the door with `update-needed` and the harness sat waiting for a
// message that was never coming, which reads exactly like a hung Durable Object. The two copies
// did not even agree with each other — one said 2 and the other 5 — which is the tell that neither
// was being maintained.
//
// A number that has to be right about another language's constant will go on being wrong, so it is
// not typed here at all. `Protocol.kt` is the declaration; this reads it.
//
// Parsing Kotlin with a regular expression is a small ugliness bought deliberately. The honest
// alternatives are worse for a harness: export it from the Worker (which makes a gate's input
// depend on the thing it is testing), or generate a JS file at build time (which is a Gradle task
// and a committed artefact for one integer). This reads the source of truth directly and fails
// loudly if it cannot, which is the property that matters — a gate that guesses the wire version
// is a gate that tests nothing.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const SOURCE = resolve(HERE, '../../shared/protocol/src/commonMain/kotlin/game/vinto/protocol/Protocol.kt');

/**
 * One `public const val <name>: Int = <n>` out of the protocol's declaration.
 *
 * Throws rather than defaults. A missing constant means the file moved or was renamed, and the
 * one thing that must not happen then is a gate quietly falling back to a number that used to be
 * right — that is the failure this module was written to end, reintroduced one level down.
 */
function readConstant(name) {
    let source;
    try {
        source = readFileSync(SOURCE, 'utf8');
    } catch (cause) {
        throw new Error(`cannot read the protocol declaration at ${SOURCE}: ${cause.message}`);
    }
    const found = source.match(new RegExp(`const val ${name}\\s*:\\s*Int\\s*=\\s*(\\d+)`));
    if (!found) throw new Error(`${name} is not declared in ${SOURCE} — has it been renamed?`);
    return Number(found[1]);
}

/** The wire a current build speaks, and what a gate must send to be seated as one. */
export const PROTOCOL_VERSION = readConstant('PROTOCOL_VERSION');

/** The oldest wire the room will seat. Below it a join is refused at the door. */
export const MIN_PROTOCOL = readConstant('MIN_PROTOCOL');
