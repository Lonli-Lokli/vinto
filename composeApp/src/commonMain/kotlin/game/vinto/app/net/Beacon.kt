package game.vinto.app.net

/**
 * A fire-and-forget POST, for the one thing that is not a request.
 *
 * Analytics is the only caller. It is deliberately *not* part of `RoomConnector`: that
 * interface is the game's conversation with its room, every method of it matters, and every
 * method of it is awaited. This is the opposite — nothing waits on it, nothing branches on
 * it, and a failure is a lost count rather than a lost move.
 *
 * Four small actuals rather than one shared HTTP library, for the same reason `RoomConnector`
 * has four: every platform ships a client for `POST` some JSON, and a multiplatform HTTP
 * framework is a large dependency for one verb — in a wasm bundle that has no headroom.
 *
 * Implementations must not throw. The caller runs inside the sink's drain loop, which is
 * expected to survive anything the network does.
 */
expect suspend fun postBeacon(url: String, body: String)
