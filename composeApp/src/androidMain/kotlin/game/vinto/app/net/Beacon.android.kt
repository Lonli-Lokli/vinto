package game.vinto.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * `HttpURLConnection` rather than the OkHttp the socket uses: this is one POST with no
 * response worth reading, and the platform's own client needs no configuration to do it.
 */
/** Long enough for a slow phone network, short enough that nothing waits on a count. */
private const val TIMEOUT_MS = 5_000

actual suspend fun postBeacon(url: String, body: String) {
    withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("content-type", "application/json")
            }
            connection.outputStream.use { it.write(body.encodeToByteArray()) }
            connection.responseCode
            connection.disconnect()
        }
    }
}
