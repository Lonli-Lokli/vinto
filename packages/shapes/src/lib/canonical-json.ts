import { Pile } from './domain-types';
import { GameState } from './game-state-types';

/**
 * Canonical serialisation of a `GameState` for cross-implementation comparison.
 *
 * Two implementations agree iff their canonical strings are byte-identical, so every
 * rule here is part of the contract with the Kotlin port and is documented in
 * `docs/game-engine/RECORDING.md`:
 *
 * - object keys sorted lexicographically at every level
 * - arrays in order; a `Pile` becomes a plain array, top card first
 * - `undefined` properties omitted, `null` kept
 * - no whitespace
 * - integers only — a fractional number throws, because TypeScript prints `1` where
 *   Kotlin prints `1.0` and the two would silently diverge
 */

/** Fields excluded from the canonical form. Everything else in `GameState` is hashed. */
export const CANONICAL_EXCLUDED_STATE_FIELDS = [
  // Client-authored history whose `description` strings are user-facing prose. Hashing
  // them would make UI copy part of the cross-language contract.
  'turnActions',
  'roundActions',
] as const;

/** Per-player fields excluded from the canonical form. */
export const CANONICAL_EXCLUDED_PLAYER_FIELDS = [
  // Bot-internal, contains floats, and never written into GameState by the engine.
  'botMemory',
] as const;

function assertCanonicalNumber(value: number, path: string): void {
  if (!Number.isFinite(value)) {
    throw new TypeError(`Non-finite number at ${path}: ${value}`);
  }
  if (!Number.isInteger(value)) {
    throw new TypeError(
      `Non-integer number at ${path}: ${value}. GameState must contain integers only — ` +
        `TypeScript prints 1 where Kotlin prints 1.0, which would break parity.`,
    );
  }
}

function canonicalize(value: unknown, path: string): string | undefined {
  if (value === undefined) return undefined;
  if (value === null) return 'null';

  if (typeof value === 'number') {
    assertCanonicalNumber(value, path);
    return String(value);
  }

  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'boolean') return value ? 'true' : 'false';

  if (value instanceof Pile) {
    return canonicalize(value.toArray(), path);
  }

  if (Array.isArray(value)) {
    const items = value.map(
      (item, index) => canonicalize(item, `${path}[${index}]`) ?? 'null',
    );
    return `[${items.join(',')}]`;
  }

  if (typeof value === 'object') {
    const source = value as Record<string, unknown>;
    const entries: string[] = [];

    for (const key of Object.keys(source).sort()) {
      const serialised = canonicalize(source[key], `${path}.${key}`);
      if (serialised !== undefined) {
        entries.push(`${JSON.stringify(key)}:${serialised}`);
      }
    }

    return `{${entries.join(',')}}`;
  }

  throw new TypeError(`Unsupported value at ${path}: ${typeof value}`);
}

/** Strips excluded fields, leaving a plain structure the generic canonicaliser can walk. */
function toCanonicalShape(state: GameState): Record<string, unknown> {
  const shaped: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(state)) {
    if ((CANONICAL_EXCLUDED_STATE_FIELDS as readonly string[]).includes(key)) {
      continue;
    }

    if (key === 'players' && Array.isArray(value)) {
      shaped[key] = value.map((player) => {
        const kept: Record<string, unknown> = {};
        for (const [playerKey, playerValue] of Object.entries(
          player as Record<string, unknown>,
        )) {
          if (
            !(CANONICAL_EXCLUDED_PLAYER_FIELDS as readonly string[]).includes(
              playerKey,
            )
          ) {
            kept[playerKey] = playerValue;
          }
        }
        return kept;
      });
      continue;
    }

    shaped[key] = value;
  }

  return shaped;
}

export function canonicalizeGameState(state: GameState): string {
  return canonicalize(toCanonicalShape(state), '$') ?? 'null';
}

/**
 * Lowercase hex SHA-256 of the canonical string.
 *
 * Async and built on WebCrypto rather than `node:crypto` so there is a single code path
 * in Node, the browser and tests — the same reason the Kotlin side uses one pure-Kotlin
 * SHA-256 across all targets instead of per-platform implementations.
 */
export async function hashCanonicalString(canonical: string): Promise<string> {
  const bytes = new TextEncoder().encode(canonical);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);

  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

export function hashGameState(state: GameState): Promise<string> {
  return hashCanonicalString(canonicalizeGameState(state));
}
