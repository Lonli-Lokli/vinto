package game.vinto.engine

import kotlin.test.Test

/**
 * `CorpusReplayTest`'s slowest test, 11 s on the JVM, so it runs in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts). Its body stays in `CorpusReplayTest`, unchanged, under the same name with `Run`
 * added.
 */
class CorpusReplayDeepTest {

    @Test
    fun everyRecordingReplaysWithMatchingHashes() = CorpusReplayTest().everyRecordingReplaysWithMatchingHashesRun()
}
