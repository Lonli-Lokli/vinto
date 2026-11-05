package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `RecordingRoundTripTest`'s one seed, one document over two whole games.
 *
 * Two whole games are 10 to 11 s on Kotlin/JS and Wasm and 43 s on the iOS simulator, so they
 * run in the deep suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite
 * writes the documents of the games' first moves.
 */
class RecordingRoundTripDeepTest {

    @Test
    fun oneSeedProducesOneDocument() = runTest(timeout = WHOLE_GAME) {
        RecordingRoundTripTest().oneSeedProducesOneDocumentOver(moveLimit = null)
    }
}
