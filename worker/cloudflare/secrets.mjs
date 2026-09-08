/**
 * Comparing a secret against what somebody offered, without leaking how close they got.
 *
 * Its own module because two unrelated doors need it — the room object's debug read and the
 * stats page — and one of those two is on its way to another repository. A helper that lives in
 * the thing being moved leaves the thing staying behind with a dangling import.
 *
 * Constant time in the length it compares, and length-checked first. Timing is not a plausible
 * attack on either of these doors; writing the comparison this way costs nothing and means
 * nobody has to decide whether it is plausible again later.
 */
export function keyMatches(given, expected) {
  if (typeof given !== 'string' || typeof expected !== 'string') return false;
  if (given.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < given.length; i += 1) diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}
