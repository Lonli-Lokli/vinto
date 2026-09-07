#!/usr/bin/env node
/**
 * Refuses to publish a build that was not made from `master`.
 *
 * THE BUILD NUMBER IS A COMMIT COUNT, and a commit count only means anything along one line of
 * history. Build from a branch, then squash or cherry-pick that branch onto master, and the
 * commits the number counted stop existing: master's count drops *below* what has already been
 * uploaded, and it can never catch up on its own.
 *
 * That is not a hypothetical. Vinto shipped 384, 402 and 423 from the long pre-squash branch;
 * the work landed on master as a cherry-pick (`master@{1}`), master counted 386, and the next
 * release had nowhere to go — Google Play refuses a versionCode that does not strictly exceed
 * the last one AND permanently reserves every code it has ever been given, so those three can
 * never be freed. It cost a permanent `OFFSET` in `Scripts/build-number.sh` to climb back over
 * them. A second occurrence costs a second offset, and by then no number means anything.
 *
 * So: publish from master, or do not publish.
 *
 * The check is `HEAD` being an ancestor of (or equal to) `master`, NOT `HEAD == master`. Those
 * differ in a case the release runbook actually hits: `vydanne prerelease --store google --apply`
 * renames the changelogs it just uploaded, so the notes are committed *after* the binary is
 * built, and master is legitimately a commit or two past the tree the artifact came from. What
 * matters is that the artifact's commit is on master's history and will stay there.
 *
 * Escape hatch: `ALLOW_OFF_MASTER=1`, which is a decision that appears in the command line
 * rather than a default that hides. There is no config file for it on purpose.
 */
import { execFileSync } from 'node:child_process';

const RELEASE_BRANCH = 'master';

/** Trimmed stdout, or null when git refuses — a missing ref is an answer, not a crash. */
function git(...args) {
  try {
    return execFileSync('git', args, { encoding: 'utf8' }).trim();
  } catch {
    return null;
  }
}

function fail(lines) {
  console.error(`\x1b[31mrefusing to publish: ${lines[0]}\x1b[0m`);
  for (const line of lines.slice(1)) console.error(`  ${line}`);
  process.exit(1);
}

if (process.env.ALLOW_OFF_MASTER === '1') {
  console.warn('\x1b[33mALLOW_OFF_MASTER=1 — publishing without the branch check.\x1b[0m');
  console.warn('  The build number will not map to a commit on master. Say why in the release notes.');
  process.exit(0);
}

if (git('rev-parse', '--git-dir') === null) {
  fail([
    'not a git checkout, so there is no way to tell which history this build came from.',
    'Set ALLOW_OFF_MASTER=1 if this is deliberate (exported tree, CI artifact).',
  ]);
}

const head = git('rev-parse', 'HEAD');
const master = git('rev-parse', RELEASE_BRANCH);

if (master === null) {
  fail([
    `there is no local \`${RELEASE_BRANCH}\` to check against.`,
    `Fetch it: git fetch origin ${RELEASE_BRANCH}:${RELEASE_BRANCH}`,
  ]);
}

// --is-ancestor exits 0 for an ancestor and 1 for anything else, so the throw IS the answer.
let onMaster = true;
try {
  execFileSync('git', ['merge-base', '--is-ancestor', head, master], { stdio: 'ignore' });
} catch {
  onMaster = false;
}

if (!onMaster) {
  const branch = git('rev-parse', '--abbrev-ref', 'HEAD');
  const ahead = git('rev-list', '--count', `${master}..${head}`);
  fail([
    `HEAD is not on \`${RELEASE_BRANCH}\`.`,
    `on: ${branch === 'HEAD' ? `detached at ${head.slice(0, 7)}` : branch} (${ahead} commit(s) ${RELEASE_BRANCH} does not have)`,
    '',
    'The build number is a commit count, and it only survives on the history that survives.',
    `Publish this and then squash the branch into ${RELEASE_BRANCH}, and the number counts`,
    'commits that no longer exist — while Play keeps the versionCode forever.',
    '',
    `Merge into ${RELEASE_BRANCH} first, rebuild, then publish.`,
  ]);
}

// A dirty tree is a warning rather than a refusal: the release runbook renames changelog files
// mid-upload, so "clean" is not a state the whole flow can be held to. But a binary built over
// uncommitted edits matches no commit, which is worth saying out loud.
const dirty = git('status', '--porcelain');
if (dirty) {
  const count = dirty.split('\n').filter(Boolean).length;
  console.warn(`\x1b[33mworking tree has ${count} uncommitted change(s).\x1b[0m`);
  console.warn('  If the artifacts were built over these, the build number names a commit that');
  console.warn('  does not describe them. Check before you upload.');
}

const remote = git('rev-parse', `origin/${RELEASE_BRANCH}`);
if (remote && remote !== master) {
  const unpushed = git('rev-list', '--count', `origin/${RELEASE_BRANCH}..${master}`);
  if (unpushed && unpushed !== '0') {
    console.warn(`\x1b[33m${RELEASE_BRANCH} is ${unpushed} commit(s) ahead of origin/${RELEASE_BRANCH}.\x1b[0m`);
    console.warn('  Testers will have a build whose commit nobody else can fetch. Push when you can.');
  }
}

const number = git('rev-list', '--count', head);
console.log(`\x1b[32mon ${RELEASE_BRANCH}\x1b[0m — HEAD ${head.slice(0, 7)}, ${number} commits`);
