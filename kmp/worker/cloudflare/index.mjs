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
  newRoom, joinRoom, applyAction, eventsSince, viewForSeat, seatForToken, replayRecordingJson,
  addBot, removeBot, startGame, lobbyView,
  newRegistry, mintRoomCode, resolveRoomCode, listPublicRooms, forgetRoom, registrySize,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

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
          mintRoomCode(registryJson, bytes, Boolean(body.isPublic), body.hostNickname ?? ''),
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
      return new Response(listPublicRooms(registryJson), {
        headers: { 'content-type': 'application/json' },
      });
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

    const fresh = newRoom(roomId, seed, difficulty);
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
      nickname: seat.nickname,
      ownerId: seat.ownerId,
      occupied: seat.tokenHash !== null,
    }));
  }

  #viewFor(stateJson, seat) {
    const result = JSON.parse(viewForSeat(stateJson, seat));
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

    const startsAt = JSON.parse(stateJson).startsAtEpochMs;
    if (startsAt) await this.ctx.storage.setAlarm(startsAt);
    else await this.ctx.storage.deleteAlarm();
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

    const result = JSON.parse(startGame(stateJson, Date.now()));
    if (result.error) return; // The room stopped being startable while the alarm was pending.

    const nextJson = JSON.stringify(result.state);
    await this.#save(nextJson);
    this.#sendPerSeat((seat) => ({
      type: 'started',
      view: this.#viewFor(nextJson, seat),
      nextIndex: result.state.log.length,
    }));
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

    // A plain GET reports room state — used by the harness to inspect the object without a
    // socket, and to prove state survived an eviction.
    if (request.headers.get('Upgrade') !== 'websocket') {
      const stateJson = await this.#load(roomId);
      return new Response(stateJson, {
        headers: { 'content-type': 'application/json' },
      });
    }

    await this.#load(roomId);

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // acceptWebSocket (rather than server.accept()) is what allows the object to be
    // evicted while the socket stays open — the hibernation API.
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ seat: null, token: null });

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, raw) {
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
        // #sendPerSeat and #broadcast never see it, and no view contains it.
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
        return this.#broadcastLobby(savedJson);
      }

      // --- lobby seat management (design R2a) ---------------------------------------------
      //
      // Any seated player, not only whoever made the room. The countdown is what keeps that
      // safe: adding a bot is a proposal that stands for ten seconds, not a decision.
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
          applyAction(stateJson, token, JSON.stringify(msg.action ?? {})),
        );
        if (result.error) {
          return ws.send(JSON.stringify({ type: 'error', message: result.error }));
        }
        const nextJson = JSON.stringify(result.state);
        await this.#save(nextJson);

        // Every socket sees every accepted action, the caller included — the server is
        // authoritative, so clients never apply an action optimistically. The bots' moves
        // arrive in the same batch, so one send answers "what happened because of that".
        //
        // The view is per-seat and so cannot be broadcast as one message: each socket gets
        // the events, which are public, plus its own redacted view.
        return this.#sendPerSeat((seatIndex) => ({
          type: 'events',
          events: result.events,
          nextIndex: result.state.log.length,
          view: this.#viewFor(nextJson, seatIndex),
        }));
      }

      case 'resync': {
        const result = JSON.parse(eventsSince(stateJson, msg.sinceIndex ?? 0));
        return ws.send(JSON.stringify({ type: 'sync', ...result }));
      }

      default:
        return ws.send(JSON.stringify({ type: 'error', message: `unknown type ${msg.type}` }));
    }
  }

  /**
   * Sends each socket a message built for its own seat.
   *
   * A plain broadcast cannot carry a view: two seats are entitled to different cards, and
   * one shared payload would have to be the union of both.
   */
  #sendPerSeat(build) {
    for (const socket of this.ctx.getWebSockets()) {
      const { seat } = socket.deserializeAttachment() ?? {};
      if (seat === null || seat === undefined) continue;
      try {
        socket.send(JSON.stringify(build(seat)));
      } catch {
        // A socket that has gone away is not an error worth failing the action over.
      }
    }
  }

  async webSocketClose(ws, code, reason, wasClean) {
    // The seat is intentionally kept: design D9 has a disconnected human's seat played by a
    // bot after a grace period, and joinRoom is idempotent by *token* so they get it back —
    // and only they can, which is the whole point of R3.
    ws.close(code === 1006 ? 1000 : code, reason);
  }

  async webSocketError(ws, error) {
    console.error('room socket error', error);
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
      }
    }
  }
}

export default {
  async fetch(request, env) {
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
    const registry = () => env.REGISTRY.get(env.REGISTRY.idFromName('registry'));

    // Creating a room is a POST, and it is the *only* way to bring one into existence.
    if (url.pathname === '/rooms' && request.method === 'POST') {
      const body = await request.json().catch(() => ({}));
      const minted = await registry().fetch(
        new Request('https://registry/mint', { method: 'POST', body: JSON.stringify(body) }),
      );
      return new Response(await minted.text(), {
        status: minted.status,
        headers: { 'content-type': 'application/json' },
      });
    }

    // The public list. Private rooms are simply absent from it; they are reachable by code.
    if (url.pathname === '/rooms' && request.method === 'GET') {
      const listed = await registry().fetch(new Request('https://registry/public'));
      return new Response(await listed.text(), {
        headers: { 'content-type': 'application/json' },
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
  },
};
