package game.vinto.room

import kotlin.test.Test

/**
 * `RoomRecordingTest`'s slowest test, 9.1 to 10.6 s on the JVM on CI, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `RoomRecordingTest`, unchanged, under the
 * same name with `Run` added.
 */
class RoomRecordingDeepTest {

    @Test
    fun aRoomsRoundReplaysFromItsOwnRecording() = RoomRecordingTest().aRoomsRoundReplaysFromItsOwnRecordingRun()
}
