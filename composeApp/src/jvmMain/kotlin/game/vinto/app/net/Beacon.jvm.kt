package game.vinto.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Long enough for a slow phone network, short enough that nothing waits on a count. */
private val TIMEOUT = Duration.ofSeconds(5)

private val client: HttpClient by lazy {
    HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
}

actual suspend fun postBeacon(url: String, body: String) {
    withContext(Dispatchers.IO) {
        runCatching {
            client.send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        }
    }
}
