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

import { newRoom, joinRoom, applyAction, eventsSince, replayRecordingJson } from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

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

export class Room {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async #load(roomId, seed) {
    const stored = await this.ctx.storage.get(ROOM_KEY);
    if (stored) return stored;
    const fresh = newRoom(roomId, seed);
    await this.ctx.storage.put(ROOM_KEY, fresh);
    return fresh;
  }

  async #save(stateJson) {
    await this.ctx.storage.put(ROOM_KEY, stateJson);
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

    // The room is closed until ActionValidator is ported, and the gate sits ABOVE everything
    // else for a reason found on the first real deployment: the state endpoint below calls
    // #load, which CREATES the Durable Object and writes it to storage. Reachable publicly,
    // that let any stranger conjure an unbounded number of rooms out of query strings — each
    // one storage and row writes against the free-tier budget in D9 — and read them back.
    //
    // Locally that was an inspection aid for the 2a.3 harness and entirely reasonable. The
    // difference is only that one of them is on the internet, which is exactly the class of
    // thing a deployment tells you and a local run cannot.
    //
    // Opening the room is a deliberate act — set ROOM_OPEN="true" — not a default, because
    // the validator currently permits everything and design D9 puts server-side validation at
    // the centre of the anti-cheat model.
    if (this.env.ROOM_OPEN !== 'true') {
      return new Response(
        'The room is closed: server-side action validation is not implemented yet ' +
          '(see ActionValidator, task 4.4). POST /replay to exercise the engine.',
        { status: 503, headers: { 'content-type': 'text/plain' } },
      );
    }

    const roomId = url.searchParams.get('room') ?? 'default';
    const seed = Number(url.searchParams.get('seed') ?? '42');

    // A plain GET reports room state — used by the harness to inspect the object without a
    // socket, and to prove state survived an eviction.
    if (request.headers.get('Upgrade') !== 'websocket') {
      const stateJson = await this.#load(roomId, seed);
      return new Response(stateJson, {
        headers: { 'content-type': 'application/json' },
      });
    }

    await this.#load(roomId, seed);

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // acceptWebSocket (rather than server.accept()) is what allows the object to be
    // evicted while the socket stays open — the hibernation API.
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ seat: null, clientId: null });

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
        const result = JSON.parse(joinRoom(stateJson, msg.clientId, msg.nickname ?? ''));
        if (result.error) {
          return ws.send(JSON.stringify({ type: 'error', message: result.error }));
        }
        await this.#save(JSON.stringify(result.state));
        ws.serializeAttachment({ seat: result.seat, clientId: msg.clientId });
        ws.send(JSON.stringify({ type: 'joined', seat: result.seat, state: result.state }));
        return this.#broadcast(
          { type: 'presence', seats: result.state.seats },
          null,
        );
      }

      case 'action': {
        const { seat } = ws.deserializeAttachment() ?? {};
        if (seat === null || seat === undefined) {
          return ws.send(JSON.stringify({ type: 'error', message: 'join before acting' }));
        }
        const result = JSON.parse(applyAction(stateJson, seat, msg.action ?? 'noop'));
        if (result.error) {
          return ws.send(JSON.stringify({ type: 'error', message: result.error }));
        }
        await this.#save(JSON.stringify(result.state));
        // Every socket sees every accepted action, the caller included — the server is
        // authoritative, so clients never apply an action optimistically.
        return this.#broadcast({ type: 'event', event: result.event });
      }

      case 'resync': {
        const result = JSON.parse(eventsSince(stateJson, msg.sinceIndex ?? 0));
        return ws.send(JSON.stringify({ type: 'sync', ...result }));
      }

      default:
        return ws.send(JSON.stringify({ type: 'error', message: `unknown type ${msg.type}` }));
    }
  }

  async webSocketClose(ws, code, reason, wasClean) {
    // The seat is intentionally kept: design D9 has a disconnected human's seat played by a
    // bot after a grace period, and joinRoom is idempotent by clientId so they get it back.
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
    const roomId = url.searchParams.get('room') ?? 'default';

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

    // One Durable Object per room id — the unit of isolation (design D9). The Worker does
    // nothing but route, because its ~10 ms CPU budget allows nothing more; the engine work
    // happens inside the object, which is where the real CPU budget is.
    const id = env.ROOM.idFromName(roomId);
    return env.ROOM.get(id).fetch(request);
  },
};
