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
 *
 * @param contentType what the body is. Analytics posts JSON; a crash report posts a Sentry
 *   envelope, which is a different media type and is rejected under the wrong one.
 * @param auth the value for `x-sentry-auth`, when there is one. Widening this seam rather
 *   than adding a second `expect` is deliberate: a second one would be four more platform
 *   files to get right, and two of the four cannot be compiled on a host without a Mac.
 */
expect suspend fun postBeacon(
    url: String,
    body: String,
    contentType: String = "application/json",
    auth: String? = null,
)
