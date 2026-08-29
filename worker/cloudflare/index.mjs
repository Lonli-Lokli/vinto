// Cloudflare entry point for the Vinto room — platform gate task 2a.3.
//
// This file is deliberately thin and deliberately JavaScript. Everything it knows is how
// to move bytes between sockets and storage; every decision about room state lives in
// Kotlin (`worker/src/jsMain/.../Room.kt`), compiled to the ES module imported below.
// That split is the point of the gate: it shows the Kotlin bundle running inside a Durable
// Object, which is where `GameEngine.reduce` will run once the engine is ported (design D9).
//
// Two invariants worth stating, because breaking either breaks hibernation:
//   1. No authoritative state is held in instance fields. The room lives in DO storage and
//      is read at the start of each handler. A hibernated object loses memory, not storage.
//   2. Per-socket state (which seat) rides on the socket via serializeAttachment, which
//      survives hibernation; a Map keyed by socket would not.

import {
  clientEventPoint,
  roomCreatedPoint,
  newRoom, joinRoom, viewForSeat, seatForToken, replayRecordingJson,
  addBot, removeBot, lobbyView, updatePresence, nextAlarmAt,
  applyActionEnvelopes, readyEnvelopes, alarmEnvelopes, syncEnvelope, roundRecording,
  newRegistry, mintRoomCode, resolveRoomCode, looksLikeRoomCode, listPublicRooms, forgetRoom,
  registrySize, touchRoom,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

/**
 * The known event names, and nothing else gets written.
 *
 * `POST /e` is public, so this is the allow-list that stops the store being filled with
 * whatever a stranger posts. It mirrors the sealed type in `shared/protocol`; a name not on
 * this list is dropped silently rather than answered with an error, because telling a scanner
 * which names are real is telling it something.
 */
import { reportError } from './sentry.mjs';

const CLIENT_EVENTS = new Set(['funnel', 'solo_round', 'lesson', 'failure']);

/** A client batch is small by construction; anything larger is not one of ours. */
const MAX_EVENT_BYTES = 8 * 1024;
const MAX_EVENTS_PER_BATCH = 40;

/**
 * Writes one data point, if there is anywhere to write it.
 *
 * **Absent-safe by design, not by accident.** With no `ANALYTICS` binding this is a no-op, so
 * `wrangler dev` and every gate script run identically without a Cloudflare account — which
 * is what keeps analytics from becoming a thing you need credentials to develop against.
 *
 * `writeDataPoint` does not count against the invocation's CPU time and does not return a
 * promise worth awaiting. That matters more here than anywhere: the thing being measured is a
 * Durable Object whose 30-second budget is already going on MCTS, and analytics that slowed
 * the room down would be measuring a room nobody wants.
 */
function emit(env, point) {
  if (!env.ANALYTICS || !point) return;
  try {
    env.ANALYTICS.writeDataPoint(point);
  } catch {
    // A sink that refuses a point must never fail the request that produced it. There is
    // nothing to retry and nothing to report: the count is simply lost.
  }
}

/**
 * What this invocation cost, carried on every server event.
 *
 * A Durable Object gets 30 seconds of CPU per request and spends most of it on MCTS.
 * `PLATFORM-GATE.md` measured exactly one worst case at 1.6 s; the distribution is unknown,
 * and "what does a room cost" is the number that decides whether online play stays free.
 * Collecting it here is free, and it is the one thing no client could report.
 */
function costSince(startedAt, requests = 1) {
  return { wallMs: Date.now() - startedAt, requests };
}

const ROOM_KEY = 'room';

/**
 * Largest recording `/replay` will accept. The endpoint is public and CPU-bound — roughly
 * 1 ms per action — so an unbounded body is an invitation to spend someone else's compute.
 * The largest recording in the corpus is 141 KB; 1 MB leaves room without leaving a hole.
 */
const MAX_REPLAY_BYTES = 1_000_000;

/**
 * How many Durable Objects share the replay load. A Durable Object is single-threaded, so
 * one object would queue a batch of recordings behind each other; replay holds no state, so
 * spreading it costs nothing.
 */
const REPLAY_SHARDS = 8;

/**
 * A 32-byte secret, base64url, generated where the platform's random source is.
 *
 * The room issues one per seat and stores only its SHA-256 (design R3). The raw value exists
 * in exactly two places: the client that owns it, and the single message that delivered it.
 * Kotlin never reaches for randomness itself — the same rule the engine follows.
 */
function mintToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

const REGISTRY_KEY = 'registry';

/**
 * How many times to retry a code collision before giving up.
 *
 * At 900 million codes and a handful of live rooms a collision is vanishingly unlikely, so
 * three attempts is not a tuning parameter — it is the difference between "impossible" and
 * "impossible, and it says so if it happens".
 */
const MINT_ATTEMPTS = 3;

/**
 * The largest message a client may send.
 *
 * A game action is a few hundred bytes. This is not a tuning parameter so much as a refusal to
 * parse something that cannot be a move — JSON parsing is CPU, and CPU is what gets billed.
 */
const MAX_MESSAGE_BYTES = 8 * 1024;

/**
 * Sockets one room will hold.
 *
 * Four seats and a little slack for a reconnect racing its own close. Beyond that the extra
 * sockets are not players, and every one of them is memory the object has to carry.
 */
const MAX_SOCKETS_PER_ROOM = 8;

/**
 * The room-code namespace, as a single Durable Object (design R4).
 *
 * Everything that creates a room goes through here, which is exactly why it exists: a code
 * has to be minted before `idFromName` is ever called, so a stranger cannot conjure objects
 * out of query strings. It is also where the caps in phase 5 belong, since it is the only
 * place that knows how many rooms are live.
 */
export class Registry {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async #load() {
    const stored = await this.ctx.storage.get(REGISTRY_KEY);
    if (stored) return stored;
    const fresh = newRegistry();
    await this.ctx.storage.put(REGISTRY_KEY, fresh);
    return fresh;
  }

  async fetch(request) {
    const url = new URL(request.url);
    let registryJson = await this.#load();

    if (request.method === 'POST' && url.pathname === '/mint') {
      const body = await request.json();

      // Retry on collision with fresh entropy rather than reusing an entry: "one code, one
      // room" is an invariant the Kotlin side is allowed to assume absolutely.
      for (let attempt = 0; attempt < MINT_ATTEMPTS; attempt++) {
        const bytes = [...crypto.getRandomValues(new Uint8Array(6))].join(',');
        const result = JSON.parse(
          mintRoomCode(registryJson, bytes, Boolean(body.isPublic), body.hostNickname ?? '',
            body.sourceId ?? ''),
        );
        if (!result.error) {
          await this.ctx.storage.put(REGISTRY_KEY, JSON.stringify(result.state));
          return Response.json({ code: result.room.code, roomId: result.room.roomId });
        }
        registryJson = JSON.stringify(result.state);
      }
      return Response.json({ error: 'could not mint a code' }, { status: 503 });
    }

    if (url.pathname === '/resolve') {
      const code = url.searchParams.get('code') ?? '';
      return new Response(resolveRoomCode(registryJson, code), {
        headers: { 'content-type': 'application/json' },
      });
    }

    if (url.pathname === '/public') {
      return new Response(listPublicRooms(registryJson, Date.now()), {
        headers: { 'content-type': 'application/json' },
      });
    }

    if (request.method === 'POST' && url.pathname === '/touch') {
      const body = await request.json();
      await this.ctx.storage.put(
        REGISTRY_KEY,
        touchRoom(registryJson, body.code ?? '', body.humans ?? 0, body.seatsFilled ?? 0,
          body.startsAtEpochMs ?? 0),
      );
      return Response.json({ ok: true });
    }

    if (request.method === 'POST' && url.pathname === '/forget') {
      const code = url.searchParams.get('code') ?? '';
      await this.ctx.storage.put(REGISTRY_KEY, forgetRoom(registryJson, code));
      return Response.json({ ok: true });
    }

    if (url.pathname === '/size') {
      return Response.json({ size: registrySize(registryJson) });
    }

    return new Response('not found', { status: 404 });
  }
}

export class Room {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async #load(roomId, difficulty = 'moderate') {
    const stored = await this.ctx.storage.get(ROOM_KEY);
    if (stored) return stored;

    // The seed is the server's, always (design R9). It used to come off the query string,
    // which let a client reload until it liked its hand. TEST_SEED exists so the gate
    // harnesses stay deterministic; it is an environment variable, never a request.
    const seed = this.env.TEST_SEED
      ? Number(this.env.TEST_SEED)
      : crypto.getRandomValues(new Uint32Array(1))[0];

    const fresh = newRoom(roomId, seed, difficulty, Date.now());
    await this.ctx.storage.put(ROOM_KEY, fresh);
    return fresh;
  }

  /**
   * What one seat is allowed to see.
   *
   * Never the room state: that holds every hand. Everything sent to a client goes through
   * the Kotlin projection, which replaces cards the seat may not see with a token carrying
   * nothing at all — not even an id, since a card id spells out its rank.
   */
  /**
   * Seats as everybody else may see them.
   *
   * `tokenHash` is not a secret in the way the token is, but it is a credential's shadow and
   * it has no business on the wire. Stripping it in one place beats remembering to per
   * message type.
   */
  #publicSeats(state) {
    return state.seats.map((seat) => ({
      index: seat.index,
      playerId: seat.playerId,
      // The whole profile, not a nickname picked out of it: whatever is added there next is
      // display-only by construction, so it can travel without a decision each time.
      profile: seat.profile,
      ownerId: seat.ownerId,
      occupied: seat.tokenHash !== null,
    }));
  }

  #viewFor(stateJson, seat) {
    const result = JSON.parse(viewForSeat(stateJson, seat, Date.now()));
    return result.view ?? null;
  }

  /** Seats as everybody else may see them, with the lobby's own occupancy. */

  /**
   * Stores the room and keeps the alarm in step with it.
   *
   * One function rather than two calls, because the failure it prevents is invisible: a
   * transition that writes `startsAtEpochMs` and forgets to schedule leaves a countdown that
   * never fires, and a lobby that sits there forever looks exactly like a slow one.
   */
  async #save(stateJson) {
    await this.ctx.storage.put(ROOM_KEY, stateJson);

    // One alarm, five possible deadlines. The room works out which is soonest; scheduling it
    // here rather than at each call site is what stops a transition writing a deadline and
    // forgetting to wake for it — a failure that looks like a slow lobby, not a broken one.
    // The room works out which of its five deadlines is soonest. Computing that here instead
    // would mean the same rule in two languages, and the symptom of drift would be an alarm
    // at the wrong time — which looks like nothing at all until a room fails to clean up.
    const at = nextAlarmAt(stateJson);
    if (at > 0) await this.ctx.storage.setAlarm(at);
    else await this.ctx.storage.deleteAlarm();
  }

  /** Seats with a socket open, which is what every deadline is computed from. */
  #connectedSeats() {
    return this.ctx.getWebSockets()
      .map((socket) => (socket.deserializeAttachment() ?? {}).seat)
      .filter((seat) => seat !== null && seat !== undefined);
  }

  /**
   * Files the just-finished round's recording, if this request finished one.
   *
   * Detected by the session growing a round between the state a request read and the state
   * it produced, which makes the write once-per-round however the round ended — a player's
   * last action or the buzzer's alarm. Stored under its round number, so a room's rounds
   * are fetchable individually for as long as the room lives; `recordedAt` is stamped here
   * because this is where the platform's clock lives, and it is informational only.
   */
  async #fileRecording(beforeJson, afterState) {
    const before = JSON.parse(beforeJson).session.rounds.length;
    const after = afterState.session.rounds.length;
    if (after <= before) return;

    const result = JSON.parse(
      roundRecording(JSON.stringify(afterState), new Date().toISOString()),
    );
    if (result.recording) {
      await this.ctx.storage.put(`recording:${after}`, JSON.stringify(result.recording));
    }
  }

  /** Recomputes the deadlines from who is actually here, and reschedules. */
  async #refreshPresence(stateJson) {
    const result = JSON.parse(
      updatePresence(stateJson, this.#connectedSeats().join(','), Date.now()),
    );
    const nextJson = JSON.stringify(result.state);
    await this.#save(nextJson);
    return nextJson;
  }

  /** Tells the registry what a lobby browser would want to know, and forgets a dead room. */
  async #tellRegistry(path, body) {
    try {
      const registry = this.env.REGISTRY.get(this.env.REGISTRY.idFromName('registry'));
      await registry.fetch(new Request(`https://registry${path}`, {
        method: 'POST',
        body: JSON.stringify(body),
      }));
    } catch {
      // A registry that is briefly unreachable must not take a room down with it. The public
      // list going stale is recoverable; a game dying because a listing failed is not.
    }
  }

  /**
   * The countdown expiring.
   *
   * This is why the deadline is an alarm and not a `setTimeout`: a lobby with nobody typing is
   * precisely when the object is evicted, and an in-memory timer would go with it.
   */
  async alarm() {
    const stateJson = await this.ctx.storage.get(ROOM_KEY);
    if (!stateJson) return;

    // The room decides what was due — and builds the messages its outcome calls for, per
    // seat, with per-event views (see shared/room/Envelopes.kt). It is deliberately not told
    // which alarm fired, because after an eviction, or a late wake, more than one deadline
    // may have passed.
    const result = JSON.parse(alarmEnvelopes(stateJson, Date.now()));

    if (result.deleted) {
      const code = JSON.parse(stateJson).roomId.replace(/^room-/, '');
      await this.#tellRegistry('/forget', { code });
      this.#broadcast({ type: 'closed', reason: 'the room ended' });
      for (const socket of this.ctx.getWebSockets()) {
        try { socket.close(1000, 'room closed'); } catch { /* already gone */ }
      }
      await this.ctx.storage.deleteAll();
      return;
    }

    await this.#save(JSON.stringify(result.state));
    await this.#fileRecording(stateJson, result.state);

    // Anything the alarm produced messages for — a deal, a takeover's moves, a pacing
    // expiry's — goes out; the room decided what, this layer only delivers.
    if (Object.keys(result.messages).length > 0) {
      return this.#sendPrebuilt(result.messages);
    }

    if (result.state.phase === 'FINISHED') {
      return this.#broadcast({ type: 'ended', reason: 'not enough players' });
    }

    return undefined;
  }

  /** The lobby as everyone in it sees it: seats and a countdown, never hands or hashes. */
  #broadcastLobby(stateJson) {
    const view = JSON.parse(lobbyView(stateJson, Date.now()));
    this.#broadcast({ type: 'lobby', lobby: view });
  }

  async fetch(request) {
    const url = new URL(request.url);

    // Replay runs HERE rather than in the Worker's own fetch handler, and the reason is a
    // hard platform limit rather than tidiness: a plain Worker gets ~10 ms of CPU per
    // invocation on the free plan, while a Durable Object gets 30 s per request. Replaying
    // one game costs ~250 ms, so in the Worker it exceeded the limit and Cloudflare returned
    // `error code: 1102`.
    //
    // It passed locally and it passed as single requests. `wrangler dev` enforces no CPU
    // limit at all, and in production the limit is applied on a rolling average, so spaced
    // requests slip through while a tight loop does not — which is exactly the shape of bug
    // that only a real deployment under real load will show you.
    if (url.pathname === '/replay') {
      return new Response(replayRecordingJson(await request.text()), {
        headers: { 'content-type': 'application/json' },
      });
    }

    // The gate sits ABOVE everything else for a reason found on the first real deployment:
    // the state endpoint below calls #load, which CREATES the Durable Object and writes it to
    // storage. Reachable publicly, that let any stranger conjure an unbounded number of rooms
    // out of query strings — each one storage and row writes against the free-tier budget in
    // D9 — and read them back.
    //
    // Locally that was an inspection aid for the 2a.3 harness and entirely reasonable. The
    // difference is only that one of them is on the internet, which is exactly the class of
    // thing a deployment tells you and a local run cannot.
    //
    // The room now runs the real engine: ActionValidator on every action, the seat boundary
    // above it, per-seat redacted views out, and bots server-side. Opening it is still a
    // deliberate act — set ROOM_OPEN="true" — because a room that anyone can create is a
    // resource question rather than a correctness one, and that decision is the operator's.
    if (this.env.ROOM_OPEN !== 'true') {
      return new Response(
        'The room is closed. Set ROOM_OPEN="true" to open it. ' +
          'POST /replay to exercise the engine.',
        { status: 503, headers: { 'content-type': 'text/plain' } },
      );
    }

    const roomId = url.searchParams.get('room') ?? 'default';

    // A finished round's recording, by round number: the GameRecording document /replay
    // verifies, written when the round was filed. Only ever a round that has ended — every
    // hand in it was already turned face-up by scoring.
    const wantedRecording = url.searchParams.get('recording');
    if (wantedRecording && request.headers.get('Upgrade') !== 'websocket') {
      const stored = await this.ctx.storage.get(`recording:${Number(wantedRecording)}`);
      if (!stored) return new Response('no such recording', { status: 404 });
      return new Response(stored, { headers: { 'content-type': 'application/json' } });
    }

    // A plain GET reports room state — used by the harness to inspect the object without a
    // socket, and to prove state survived an eviction.
    if (request.headers.get('Upgrade') !== 'websocket') {
      const stateJson = await this.#load(roomId);
      return new Response(stateJson, {
        headers: { 'content-type': 'application/json' },
      });
    }

    await this.#load(roomId);

    // A room is four people. Sockets beyond that are not players, and each one is memory the
    // object carries for as long as it lives.
    if (this.ctx.getWebSockets().length >= MAX_SOCKETS_PER_ROOM) {
      return new Response('too many connections to this room', { status: 429 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // acceptWebSocket (rather than server.accept()) is what allows the object to be
    // evicted while the socket stays open — the hibernation API.
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ seat: null, token: null });

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, raw) {
    // Checked before parsing, not after. Parsing is the cost being avoided, so measuring it
    // first and refusing second would pay for exactly what the cap exists to refuse.
    if (typeof raw === 'string' && raw.length > MAX_MESSAGE_BYTES) {
      return ws.send(JSON.stringify({ type: 'error', message: 'message too large' }));
    }

    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return ws.send(JSON.stringify({ type: 'error', message: 'malformed json' }));
    }

    const stateJson = await this.ctx.storage.get(ROOM_KEY);
    if (!stateJson) {
      return ws.send(JSON.stringify({ type: 'error', message: 'room not initialised' }));
    }

    switch (msg.type) {
      case 'join': {
        // A token in the message means "I already have a seat here"; no token means "give
        // me one". Either way the seat comes back from the room, never from the client.
        const token = msg.token ?? mintToken();
        const result = JSON.parse(joinRoom(stateJson, token, msg.nickname ?? '', Date.now()));
        if (result.error) {
          return ws.send(JSON.stringify({ type: 'error', message: result.error }));
        }
        await this.#save(JSON.stringify(result.state));
        const savedJson = JSON.stringify(result.state);
        ws.serializeAttachment({ seat: result.seat, token });

        // The one and only message that carries the raw token. It goes to this socket alone;
        // #sendPrebuilt and #broadcast never see it, and no view contains it.
        // In a lobby there is no game and therefore no view — `#viewFor` returns null and
        // the client gets the lobby instead. Sending a made-up empty view would be worse
        // than sending none: the client could not tell "not dealt" from "dealt, nothing to
        // see", and those need different screens.
        ws.send(JSON.stringify({
          type: 'joined',
          seat: result.seat,
          token,
          seats: this.#publicSeats(result.state),
          nextIndex: result.state.log.length,
          lobby: JSON.parse(lobbyView(savedJson, Date.now())),
          view: this.#viewFor(savedJson, result.seat),
        }));
        // A socket arriving cancels a grace and may cancel the lonely clock, so presence is
        // recomputed here rather than only on disconnect.
        const withPresence = await this.#refreshPresence(savedJson);
        await this.#reflectInRegistry(withPresence);
        return this.#broadcastLobby(withPresence);
      }

      // --- lobby seat management (design R2a) ---------------------------------------------
      //
      // Any seated player, not only whoever made the room. The countdown is what keeps that
      // safe: adding a bot is a proposal that stands for ten seconds, not a decision.
      // Agreeing to another round. Every connected human has to; the last to say so deals it.
      case 'next-round': {
        const token = msg.token ?? (ws.deserializeAttachment() ?? {}).token;
        if (!token) {
          return ws.send(JSON.stringify({ type: 'error', message: 'join before agreeing' }));
        }
        const result = JSON.parse(readyEnvelopes(stateJson, token, Date.now()));
        if (result.error) {
          return ws.send(JSON.stringify({ type: 'error', message: result.error }));
        }
        await this.#save(JSON.stringify(result.state));
        return this.#sendPrebuilt(result.messages);
      }

      case 'add-bot':
      case 'remove-bot': {
        const token = msg.token ?? (ws.deserializeAttachment() ?? {}).token;
        if (!token) {
          return ws.send(JSON.stringify({ type: 'error', message: 'join before changing seats' }));
        }

        const result = JSON.parse(
          msg.type === 'add-bot'
            ? addBot(stateJson, token, Date.now())
            : removeBot(stateJson, token, msg.seat ?? -1, Date.now()),
        );
        if (result.error) {
          return ws.send(JSON.stringify({ type: 'error', message: result.error }));
        }

        const nextJson = JSON.stringify(result.state);
        await this.#save(nextJson);
        await this.#reflectInRegistry(nextJson);
        return this.#broadcastLobby(nextJson);
      }

      case 'action': {
        // The token, not the socket's memory of a seat, is what authorises this. An
        // attachment is state the room set; a token is a claim the client has to keep
        // making, and the room re-checks it every single time.
        const token = msg.token ?? (ws.deserializeAttachment() ?? {}).token;
        if (!token) {
          return ws.send(JSON.stringify({ type: 'error', message: 'join before acting' }));
        }
        const result = JSON.parse(
          applyActionEnvelopes(stateJson, token, JSON.stringify(msg.action ?? {}), Date.now()),
        );
        if (result.error) {
          // A throttled action still costs a storage write, because the budget it spent has
          // to be remembered — an in-memory bucket would refill on every eviction, which is
          // exactly when a flood is cheapest to run.
          if (result.retryAfterMs) await this.#save(JSON.stringify(result.state));
          return ws.send(JSON.stringify({
            type: 'error',
            message: result.error,
            retryAfterMs: result.retryAfterMs ?? undefined,
          }));
        }
        await this.#save(JSON.stringify(result.state));
        await this.#fileRecording(stateJson, result.state);

        // Every socket sees every accepted action, the caller included — the server is
        // authoritative, so clients never apply an action optimistically. The bots' moves
        // arrive in the same batch, so one send answers "what happened because of that".
        //
        // The view is per-seat — per *event*, now — and so cannot be broadcast as one
        // message. The messages arrive prebuilt from Kotlin, one per seat, already redacted;
        // this layer looks up the socket's seat and sends the string as-is.
        return this.#sendPrebuilt(result.messages);
      }

      case 'resync': {
        // The seat rides on the socket, set at join; a socket that never joined syncs the
        // public log with no view (seat -1).
        const { seat } = ws.deserializeAttachment() ?? {};
        return ws.send(syncEnvelope(stateJson, seat ?? -1, msg.sinceIndex ?? 0, Date.now()));
      }

      default:
        return ws.send(JSON.stringify({ type: 'error', message: `unknown type ${msg.type}` }));
    }
  }

  /**
   * Sends each socket the message Kotlin built for its seat, verbatim.
   *
   * A plain broadcast cannot carry a view: two seats are entitled to different cards, and
   * one shared payload would have to be the union of both. The per-seat messages arrive
   * prebuilt from `shared/room/Envelopes.kt` — one serializer on both ends of the wire —
   * and this layer never parses what it relays.
   */
  #sendPrebuilt(messages) {
    for (const socket of this.ctx.getWebSockets()) {
      const { seat } = socket.deserializeAttachment() ?? {};
      if (seat === null || seat === undefined) continue;
      const payload = messages[seat];
      if (!payload) continue;
      try {
        socket.send(payload);
      } catch {
        // A socket that has gone away is not an error worth failing the action over.
      }
    }
  }

  /**
   * Tells the registry what a lobby browser wants: how full, and how soon it starts.
   *
   * On transitions rather than on a timer, so the write count is bounded by play. A private
   * room is touched too — the registry knows about it either way, and branching here would be
   * one more thing to get wrong for no saving.
   */
  async #reflectInRegistry(stateJson) {
    const state = JSON.parse(stateJson);
    await this.#tellRegistry('/touch', {
      code: state.roomId.replace(/^room-/, ''),
      humans: state.seats.filter((s) => s.tokenHash !== null).length,
      seatsFilled: state.seats.filter((s) => s.tokenHash !== null || s.isBot).length,
      startsAtEpochMs: state.startsAtEpochMs ?? 0,
    });
  }

  async webSocketClose(ws, code, reason, wasClean) {
    // The seat is intentionally kept: design D9 has a disconnected human's seat played by a
    // bot after a grace period, and joinRoom is idempotent by *token* so they get it back —
    // and only they can, which is the whole point of R3.
    ws.close(code === 1006 ? 1000 : code, reason);

    // Closing starts the clocks: a grace on this seat, and the lonely clock if the table has
    // dropped below two people. Both are computed from who is left, not from who just went.
    const stateJson = await this.ctx.storage.get(ROOM_KEY);
    if (stateJson) {
      const refreshed = await this.#refreshPresence(stateJson);
      this.#broadcastLobby(refreshed);
    }
  }

  async webSocketError(ws, error) {
    console.error('room socket error', error);
    // Reported as well as logged: a `console.error` in a Durable Object is visible only to
    // somebody already tailing it, which is nobody at three in the morning.
    reportError(this.env, error, { surface: 'room-socket' });
  }

  #broadcast(message, except) {
    const payload = JSON.stringify(message);
    // getWebSockets() is served from the object itself, so it is correct after a resume
    // even though the sockets outlived the previous instance.
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === except) continue;
      try {
        socket.send(payload);
      } catch (err) {
        console.error('broadcast failed', err);
        reportError(this.env, err, { surface: 'room-broadcast' });
      }
    }
  }
}

export default {
  async fetch(request, env, ctx) {
    // One place where an unhandled failure becomes a reported one. Without this a bug in any
    // branch below is a 500 with an opaque Cloudflare page, and the only person who knows is
    // the player who hit it.
    try {
      return await handle(request, env);
    } catch (error) {
      const sent = reportError(env, error, { surface: 'worker' });
      // Handed to the runtime rather than awaited: the response should not wait on telemetry.
      if (sent && ctx?.waitUntil) ctx.waitUntil(sent);
      console.error('worker failed', error);
      return new Response('something went wrong', { status: 500 });
    }
  },
};

async function handle(request, env) {
    const url = new URL(request.url);

    // Reports what is deployed and what is switched on, so a deployment can be identified
    // without reading its source. `roomOpen` is the answer to "is this thing accepting play
    // yet", which is the question worth being able to ask from outside.
    if (url.pathname === '/health') {
      return Response.json({
        ok: true,
        service: 'vinto-room',
        engine: 'kotlin',
        roomOpen: env.ROOM_OPEN === 'true',
      });
    }

    // Replays a recording through the real Kotlin engine, in the runtime that actually
    // serves it. This is how the engine is verified on a deployment with no UI: POST a
    // GameRecording and get back either ok, or the exact action where it diverged.
    if (url.pathname === '/replay' && request.method === 'POST') {
      const body = await request.text();
      if (body.length > MAX_REPLAY_BYTES) {
        return Response.json(
          { ok: false, error: `recording exceeds ${MAX_REPLAY_BYTES} bytes` },
          { status: 413 },
        );
      }

      // Forwarded to a Durable Object for its CPU budget — see Room.fetch. Sharded across a
      // few objects so a batch of recordings is not serialised through one single-threaded
      // object; they hold no state, so which one answers does not matter.
      const shard = `replay-${Math.floor(Math.random() * REPLAY_SHARDS)}`;
      const stub = env.ROOM.get(env.ROOM.idFromName(shard));
      return stub.fetch(new Request(request.url, { method: 'POST', body }));
    }

    // --- the registry: everything that creates or finds a room ---------------------------
    //
    // Shut when the room is shut. ROOM_OPEN used to guard only the socket, one layer down in
    // `Room.fetch`, which left a closed deployment still minting codes and still naming its
    // public rooms to anybody who asked: a stranger could write rows into the Registry object
    // and read back who was playing, on a service that answers "roomOpen: false". The caps in
    // `RegistryCore` bound how much of that is possible, and bounded is not the same as
    // refused. The flag means the room service is closed, so it closes the door as well as
    // the table.
    //
    // /health and /replay stay open above this line on purpose: one says whether the thing is
    // alive, the other is a pure function of its own argument that holds and discloses no
    // state.
    // Client events: the small, bounded stream of what the room cannot see — solo play, the
    // lesson, the menu funnel before a room exists, and client-side failures.
    //
    // Above the ROOM_OPEN gate deliberately. A closed deployment still has clients that
    // opened the app and clients that broke, and those are exactly the counts worth having on
    // a day the room is shut. It writes nothing anybody could be identified by, and with no
    // binding it writes nothing at all.
    if (url.pathname === '/e' && request.method === 'POST') {
      const raw = await request.text();
      // Answer 204 whatever happens. A body that is too large, malformed or full of unknown
      // names is dropped in silence: this endpoint is a drain, and an error code would tell a
      // prober which shapes are interesting.
      if (raw.length <= MAX_EVENT_BYTES) {
        const batch = (() => {
          try {
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed.slice(0, MAX_EVENTS_PER_BATCH) : [];
          } catch {
            return [];
          }
        })();

        for (const event of batch) {
          if (!event || !CLIENT_EVENTS.has(event.type)) continue;
          // Built on this side from a name we recognise, never forwarded as the client sent
          // it — so a field nobody declared cannot reach the store, and a client-supplied
          // timestamp cannot be believed.
          emit(env, clientEventPoint(JSON.stringify(event)));
        }
      }
      return new Response(null, { status: 204 });
    }

    if (env.ROOM_OPEN !== 'true') {
      return new Response('the room service is closed', {
        status: 503,
        headers: { 'content-type': 'text/plain' },
      });
    }

    const registry = () => env.REGISTRY.get(env.REGISTRY.idFromName('registry'));

    // Creating a room is a POST, and it is the *only* way to bring one into existence.
    if (url.pathname === '/rooms' && request.method === 'POST') {
      const startedAt = Date.now();
      const body = await request.json().catch(() => ({}));

      // Who asked, as an opaque id. The registry enforces a per-source cap and never sees an
      // address: hashing here keeps the cap enforceable without storing anything anybody would
      // mind being stored.
      const address = request.headers.get('cf-connecting-ip') ?? 'local';
      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(address));
      const sourceId = [...new Uint8Array(digest)].slice(0, 8)
        .map((b) => b.toString(16).padStart(2, '0')).join('');

      const minted = await registry().fetch(
        new Request('https://registry/mint', {
          method: 'POST',
          body: JSON.stringify({ ...body, sourceId }),
        }),
      );
      const answer = await minted.text();

      // Only a room that was actually minted counts. A refusal here is a rate limit or a cap
      // biting, which is a different event and not this one — counting attempts as creations
      // would make the funnel's first step wrong in the flattering direction.
      if (minted.status === 200) {
        const cost = costSince(startedAt);
        emit(
          env,
          roomCreatedPoint(
            body.isPublic === true,
            typeof body.difficulty === 'string' ? body.difficulty : 'moderate',
            cost.wallMs,
            cost.requests,
          ),
        );
      }

      return new Response(answer, {
        status: minted.status,
        headers: { 'content-type': 'application/json' },
      });
    }

    // The public list. Private rooms are simply absent from it; they are reachable by code.
    //
    // `no-store` because this is a room's occupancy a second ago: a cached copy sends people
    // to a table that filled while the answer sat in a proxy, and there is nothing here worth
    // saving anybody a round trip for.
    if (url.pathname === '/rooms' && request.method === 'GET') {
      const listed = await registry().fetch(new Request('https://registry/public'));
      return new Response(await listed.text(), {
        headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
      });
    }

    // --- reaching a room ------------------------------------------------------------------
    //
    // The code is resolved through the registry FIRST, and `idFromName` is reached only for a
    // code it has issued. This is the whole of design R4: not a check that a room is
    // legitimate, but the fact that an unknown code never touches a Durable Object at all,
    // and therefore never creates or bills one.
    const code = url.searchParams.get('room');
    if (!code) {
      return new Response('missing room code', { status: 400 });
    }

    // The shape first, here, where there is no state to wake. Every live code is in one
    // Durable Object, so `resolve` is a round trip to the only single-threaded thing in the
    // path — and a scan does not have to send plausible codes to cost the registry one each.
    // A string that could never have been issued is answered by the stateless half.
    //
    // The answer is the same 404 an unknown code gets, so this does not become an oracle
    // that sorts guesses into "wrong shape" and "right shape, wrong room".
    if (!looksLikeRoomCode(code)) {
      return new Response('no such room', { status: 404 });
    }

    const resolved = await (
      await registry().fetch(new Request(`https://registry/resolve?code=${encodeURIComponent(code)}`))
    ).json();

    if (!resolved.known) {
      return new Response('no such room', { status: 404 });
    }

    // One Durable Object per room — the unit of isolation (design D9). The Worker does
    // nothing but route, because its ~10 ms CPU budget allows nothing more; the engine work
    // happens inside the object, which is where the real CPU budget is.
    const id = env.ROOM.idFromName(resolved.room.roomId);
    return env.ROOM.get(id).fetch(request);
}
