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
    const roomId = url.searchParams.get('room') ?? 'default';
    const seed = Number(url.searchParams.get('seed') ?? '42');

    // A plain GET reports room state — used by the harness to inspect the object without
    // a socket, and to prove state survived an eviction.
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

    if (url.pathname === '/health') {
      return new Response('ok');
    }

    // Replays a recording through the real Kotlin engine, in the runtime that actually
    // serves it. This is how the engine is verified on a deployment with no UI: POST a
    // GameRecording and get back either ok, or the exact action where it diverged.
    if (url.pathname === '/replay' && request.method === 'POST') {
      return new Response(replayRecordingJson(await request.text()), {
        headers: { 'content-type': 'application/json' },
      });
    }

    // One Durable Object per room id — the unit of isolation (design D9). The Worker does
    // nothing but route, because its 10 ms CPU budget allows nothing more.
    const id = env.ROOM.idFromName(roomId);
    return env.ROOM.get(id).fetch(request);
  },
};
