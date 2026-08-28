package game.vinto.app.net

import game.vinto.client.RoomConnector

/**
 * The platform's way onto the network, behind `shared/client`'s [RoomConnector] seam.
 *
 * Four actuals, each speaking the platform's own WebSocket — `java.net.http` on the JVM,
 * OkHttp on Android, `NSURLSessionWebSocketTask` on iOS, the browser's `WebSocket` on Wasm —
 * because the protocol is JSON text over one socket and every platform already ships a
 * client for that. Deliberately not Ktor: a multiplatform HTTP framework is a large
 * dependency for one verb, and its Wasm engine is the least proven part of it.
 *
 * @param baseUrl the service origin without a scheme, e.g. `vinto-room.example.workers.dev`.
 *   The actuals derive `https://` for REST and `wss://` for the socket from it.
 */
expect fun platformRoomConnector(baseUrl: String): RoomConnector

/** `https://<base>` — REST lives here. */
internal fun httpBase(baseUrl: String) = "https://$baseUrl"

/** `wss://<base>/?room=<code>` — where a room's socket answers. */
internal fun socketUrl(baseUrl: String, code: String) = "wss://$baseUrl/?room=$code"
