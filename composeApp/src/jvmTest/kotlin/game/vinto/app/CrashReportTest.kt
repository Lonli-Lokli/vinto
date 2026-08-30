package game.vinto.app

import game.vinto.app.crash.CrashReport
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.crashEnvelope
import game.vinto.app.crash.parseDsn
import game.vinto.app.crash.scrubReport
import game.vinto.app.crash.sentryAuth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 8.2: what a crash report may contain, held by a test rather than by a review.
 *
 * The room's half of this is `gate-sentry.mjs`, and these are deliberately the same
 * assertions against the same rules. A crash reporter is the pipe nobody thinks of as
 * telemetry, which is exactly why it is the one that leaks: §6c binds this zone to no
 * identifiers, and a stack trace is where a room code goes to hide.
 */
class CrashReportTest {

    @Test
    fun aDsnSplitsIntoKeyAndEndpoint() {
        val dsn = parseDsn("https://abc123@o1.ingest.us.sentry.io/456")
        assertEquals("abc123", dsn?.key)
        assertEquals("https://o1.ingest.us.sentry.io/api/456/envelope/", dsn?.url)
    }

    @Test
    fun aMalformedDsnSwitchesReportingOffRatherThanThrowing() {
        for (bad in listOf(null, "", "   ", "not-a-url", "https://host/1", "https://key@host/", "https://@host/1")) {
            assertNull(parseDsn(bad), "'$bad' was accepted as a DSN")
        }
    }

    @Test
    fun theAuthHeaderNamesTheKeyAndNothingElse() {
        val auth = sentryAuth("abc123")
        assertTrue(auth.contains("sentry_key=abc123"), auth)
        assertTrue(auth.contains("sentry_version=7"), auth)
    }

    @Test
    fun nothingIdentifyingSurvivesScrubbing() {
        val leaky = listOf(
            "GET wss://vinto-room.example/?room=7KQ2MP failed",
            "at connect(RemoteGame.kt) room: \"7KQ2MP\"",
            "{\"token\":\"aGVsbG8td29ybGQtc2VjcmV0\"}",
            "connection from 203.0.113.9 reset",
        ).joinToString(" | ")

        val clean = scrubReport(leaky)
        assertFalse(clean.contains("7KQ2MP"), "a room code survived: $clean")
        assertFalse(clean.contains("aGVsbG8td29ybGQtc2VjcmV0"), "a token survived: $clean")
        assertFalse(clean.contains("203.0.113.9"), "an address survived: $clean")
        // And the message is still worth reading afterwards.
        assertTrue(clean.contains("failed") && clean.contains("RemoteGame.kt"), clean)
    }

    @Test
    fun theEnvelopeIsThreeLinesAndSaysWhatItIs() {
        val envelope = envelope()
        val lines = envelope.split("\n")
        assertEquals(3, lines.size, "an envelope is a header, an item header and the item")
        assertTrue(lines[0].contains("\"event_id\":\"deadbeef\""), lines[0])
        assertEquals("""{"type":"event"}""", lines[1])
        assertTrue(lines[2].contains("\"level\":\"error\""), lines[2])
    }

    @Test
    fun aCrashCarriesNoUserAndNoDeviceIdentifier() {
        val envelope = envelope()
        for (forbidden in listOf("\"user\"", "device_id", "deviceId", "\"ip_address\"", "playerId", "nickname")) {
            assertFalse(envelope.contains(forbidden), "$forbidden appears in a crash report:\n$envelope")
        }
    }

    @Test
    fun theRoomCodeInAStackTraceIsScrubbedBeforeItIsSent() {
        val envelope = crashEnvelope(
            CrashReport(
                eventId = "deadbeef", sentAtIso = "2026-08-30T00:00:00Z", timestampSeconds = 1.0,
                platform = "java", release = "1.0", environment = "production",
                surface = CrashSurface.ONLINE, type = "IllegalStateException",
                message = "join failed for ?room=7KQ2MP",
                frames = listOf("RemoteGame.kt:120 room: \"7KQ2MP\""),
            ),
        )
        assertFalse(envelope.contains("7KQ2MP"), "the code reached the wire:\n$envelope")
    }

    @Test
    fun quotesAndNewlinesInAMessageCannotBreakTheJson() {
        val envelope = crashEnvelope(
            CrashReport(
                eventId = "deadbeef", sentAtIso = "2026-08-30T00:00:00Z", timestampSeconds = 1.0,
                platform = "java", release = "1.0", environment = "production",
                surface = CrashSurface.SOLO, type = "Error",
                message = "he said \"no\"\nand left\t",
                frames = emptyList(),
            ),
        )
        val item = envelope.split("\n").last()
        assertTrue(item.contains("""\"no\""""), item)
        assertTrue(item.contains("""\n"""), item)
        // Three lines still, so a newline in a message did not become a fourth envelope line.
        assertEquals(3, envelope.split("\n").size, envelope)
    }

    @Test
    fun theNewestFrameGoesLastBecauseThatIsWhereSentryLooks() {
        val envelope = crashEnvelope(
            CrashReport(
                eventId = "deadbeef", sentAtIso = "2026-08-30T00:00:00Z", timestampSeconds = 1.0,
                platform = "java", release = "1.0", environment = "production",
                surface = CrashSurface.MENU, type = "Error", message = "x",
                frames = listOf("newest.kt", "middle.kt", "oldest.kt"),
            ),
        )
        val item = envelope.split("\n").last()
        assertTrue(
            item.indexOf("oldest.kt") < item.indexOf("newest.kt"),
            "frames are in the wrong order — Sentry shows the last one as the crash site:\n$item",
        )
    }

    private fun envelope() = crashEnvelope(
        CrashReport(
            eventId = "deadbeef", sentAtIso = "2026-08-30T00:00:00Z", timestampSeconds = 1_756_512_000.0,
            platform = "java", release = "vinto@1.0.0", environment = "production",
            surface = CrashSurface.SOLO, type = "IllegalStateException",
            message = "the stage never drained",
            frames = listOf("CardStage.kt:610", "GameScreen.kt:130"),
        ),
    )
}
