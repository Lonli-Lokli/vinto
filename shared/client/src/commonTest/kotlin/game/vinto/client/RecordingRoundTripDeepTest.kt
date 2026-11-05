package game.vinto.client

import kotlin.test.Test

/**
 * `RecordingRoundTripTest`'s slowest test, 11 s on Kotlin/JS and 10 s on Wasm, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `RecordingRoundTripTest`, unchanged,
 * under the same name with `Run` added.
 */
class RecordingRoundTripDeepTest {

    @Test
    fun oneSeedProducesOneDocument() = RecordingRoundTripTest().oneSeedProducesOneDocumentRun()
}
