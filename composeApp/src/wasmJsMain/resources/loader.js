/**
 * What runs before the game does: the portfolio's usage counting.
 *
 * Vinto is a Compose Multiplatform app rather than one of the `:web` dailies, so it has no
 * `web/` module — but the counting is the same counting, and it is the same block, synced from
 * the same place (`gulnya/web-template`). Before this file existed Vinto was the one game absent
 * from `stats.kupalinka.app`'s visit charts, and the reason was exactly that it had nowhere for
 * the shared block to go.
 *
 * Everything between the markers is MANAGED. Edit it in the template and run:
 *
 *     node ~/sources/gulnya/web-template/sync.mjs
 *
 * `window.px(name, tags, values)` at the foot of the block is how any game sends its own counts;
 * `stats.json` beside this file says how Vinto wants them drawn, and the dashboard charts a game's
 * events whether or not it publishes one.
 */
(function () {
  // >>> web-template:usage-counting — MANAGED, do not edit; run web-template/sync.mjs
// ── Usage counting ──────────────────────────────────────────────────────────────────────────
  //
  // BYTE-IDENTICAL IN EVERY PORTFOLIO GAME. Copy it whole; change nothing. The game names itself
  // from its host, so there is no per-game line to edit and therefore no per-game line to get
  // wrong. The reasoning lives once, in kupalinka's workers/px/README.md and in the `web-daily`
  // skill; a fix belongs there and then here, in every game, unchanged.
  //
  // It cannot live in games-core: `:web` is deliberately games-core-free so it builds from a clone
  // of its own repo alone. So it is shared the way the rest of this page's machinery is shared —
  // written to be game-agnostic and copied verbatim, the same contract as content-hash.js,
  // fingerprintBundle and web-deploy.sh.
  //
  // Three numbers for the whole portfolio: how many came, how long they stayed, how many are here
  // now. It sits in the LOADER rather than the Kotlin bundle for two reasons — this file runs
  // before the engine, so a visitor whose browser cannot run the game still counts, which is
  // exactly the number worth knowing; and it is already in the content-hashed chain, so it costs no
  // new file and `fingerprintBundle` (which fails the build on any unhashed .js) stays satisfied.
  //
  // **IT CREATES NO IDENTIFIER.** Not a cookie, not a session id, not a hashed IP. The obvious
  // design gives each page load a random id and counts distinct ids for "online now"; that design
  // manufactures a per-visit identifier and keeps it for three months, which is the one artefact
  // that turns aggregate counting into something a regulator has to think about. Instead every live
  // page pings once a minute, so the COUNT OF PINGS over a window is the number of live pages.
  // Nothing to join on, nothing that could later be argued into a profile.
  //
  // Nothing is written to or read from the device either, which is what keeps these pages free of a
  // cookie banner: ePrivacy Art. 5(3) is about storage and access, not about whether data is
  // personal. The single exception is the opt-out flag, and a record of someone's objection is as
  // strictly-necessary as storage gets.
    var PX = 'https://px.kupalinka.app/';
  // THE GAME NAMES ITSELF, from the host it is served on, so this whole block is byte-identical in
  // every game and a fix here is a copy rather than a re-edit. It also means a new game wires the
  // counter by deploying to its subdomain and nothing else.
  //
  // Anything that is not `<game>.kupalinka.app` — localhost, a preview deployment, a fork — counts
  // as not production and is not counted at all. That is the cheapest bot-and-noise filter there
  // is: every dry run, every headless check and every preview URL stays out of the numbers without
  // anyone remembering to keep it out.
  var GAME = /\.kupalinka\.app$/.test(location.hostname) ? location.hostname.split('.')[0] : '';
  var OPT_OUT = GAME + '-nostats';

  /**
   * Silence, in three forms, checked before EVERY send rather than once at startup — the switch in
   * the settings sheet has to take effect on the next beat, not on the next page load.
   *
   * GPC is a legally-recognised opt-out signal in its own right and DNT is the older convention;
   * both are honoured without being asked to, because a preference a visitor has already expressed
   * to their browser should not have to be expressed again to us.
   */
  function counted() {
    if (!GAME) return false;
    try {
      if (navigator.globalPrivacyControl === true) return false;
      if (navigator.doNotTrack === '1' || window.doNotTrack === '1') return false;
      if (window.localStorage && localStorage.getItem(OPT_OUT)) return false;
    } catch (e) {
      // Storage throws outright in some privacy modes. A visitor in one of those is the last person
      // to count against their wishes, so treat the failure as an opt-out rather than as absent.
      return false;
    }
    return true;
  }

  /**
   * `text/plain` is deliberate: it makes this a CORS *simple* request, so no preflight is sent and
   * no round trip is spent before the one that carries the data. The Worker parses the body by
   * shape rather than by content type. `keepalive` is what lets the final send survive the page
   * going away underneath it.
   */
  function send(event, seconds) {
    if (!counted()) return;
    var body = JSON.stringify({ game: GAME, event: event, seconds: seconds || 0 });
    try {
      if (event === 'end' && navigator.sendBeacon) {
        navigator.sendBeacon(PX, new Blob([body], { type: 'text/plain;charset=UTF-8' }));
        return;
      }
      fetch(PX, {
        method: 'POST',
        body: body,
        keepalive: true,
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
      }).catch(function () {});
    } catch (e) {
      // Counting is never a reason for the game to misbehave.
    }
  }

  // ENGAGED time, not wall-clock: the clock only runs while the tab is actually being looked at, so
  // a tab left open in a background window for an hour does not report an hour of play.
  var engaged = 0;
  var mark = Date.now();
  var visible = document.visibilityState !== 'hidden';

  function accrue() {
    var now = Date.now();
    if (visible) engaged += (now - mark) / 1000;
    mark = now;
  }

  send('view');
  // Once a minute, and only while visible — the ping is what makes the trailing-60s count mean
  // "people playing right now" rather than "tabs someone forgot about".
  setInterval(function () {
    accrue();
    if (visible) send('ping');
  }, 60000);

  document.addEventListener('visibilitychange', function () {
    accrue();
    visible = document.visibilityState !== 'hidden';
  });

  // `pagehide` rather than `unload`: `unload` is unreliable on mobile and blocks the back/forward
  // cache outright, and a page evicted from that cache is a page that has to be downloaded again.
  window.addEventListener('pagehide', function () {
    accrue();
    if (engaged >= 1) send('end', Math.round(engaged));
  });

  /**
   * THE GAME'S OWN COUNTS — `px('solved', { size: '9x9' }, { seconds: 84 })`.
   *
   * Everything above answers the one question every game answers identically: was a page open, and
   * for how long. This answers the ones only this game can — a puzzle solved, a streak broken, a
   * round finished — and it is here, in the managed block, so that a game gets the sender by being
   * a game rather than by copying anything.
   *
   * The dashboard needs no telling. It asks each game what events it has been sending and charts
   * whatever comes back, so the FIRST call to this function is the whole setup: no config to
   * publish, no list to edit, nothing to deploy but the game. Publishing `stats.json` beside
   * `index.html` later is how a game improves on that default — proper titles, grouping by a tag,
   * averaging a measure — and is never required.
   *
   *   name    an identifier: lower case, letters, digits and underscores. It is what the dashboard
   *           groups by, so `round_end`, not "Round finished!".
   *   tags    up to three, and each is something to GROUP BY — a difficulty, a board size, how
   *           many players. Short labels, never free text and never anything about a person.
   *   values  up to four numbers to average or total — a duration, a score, a move count.
   *
   * Silent on every failure, and subject to the same opt-outs as everything above: GPC, DNT and
   * the game's own switch. A count is never a reason for a game to misbehave, and never a reason
   * to ignore somebody who has said no.
   */
  window.px = function (name, tags, values) {
    if (!counted()) return;
    try {
      fetch(PX + 'e', {
        method: 'POST',
        body: JSON.stringify({ game: GAME, event: name, tags: tags || {}, values: values || {} }),
        keepalive: true,
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
      }).catch(function () {});
    } catch (e) {
      // Counting is never a reason for the game to misbehave.
    }
  };
  // <<< web-template:usage-counting
})();
