package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Analytics
import game.vinto.client.AnalyticsConsent
import game.vinto.client.AnalyticsTransport
import game.vinto.client.MemoryVault
import game.vinto.protocol.Difficulty
import game.vinto.protocol.FailureKind
import game.vinto.protocol.FunnelStep
import game.vinto.protocol.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 4.4: nothing identifying leaves the device.
 *
 * `AnalyticsPrivacyTest` in `shared/protocol` proves the *type* cannot carry a room code or a
 * nickname, and `gate-analytics.mjs` proves it of the bytes the Worker writes. This is the
 * third place it has to be true and the only one where a person is actually driving: the app
 * itself, walking real screens with the sink recording every payload.
 *
 * The three checks are independent on purpose. A type that cannot hold a secret, a server
 * that drops one, and a client that never sends one are different failures, and any of them
 * alone would be enough to leak if the other two were the only guards.
 */
@OptIn(ExperimentalTestApi::class)
class AnalyticsPrivacyUiTest {

    /** Records everything sent, so the assertions read the real bytes rather than intent. */
    private class Recording : AnalyticsTransport {
        val payloads = mutableListOf<String>()
        override suspend fun send(payloadJson: String) {
            payloads += payloadJson
        }
    }

    private fun walkTheApp(
        consent: AnalyticsConsent,
        transport: Recording,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
    ) = runComposeUiTest {
        // Unconfined, so a recorded event reaches the transport in the same breath rather
        // than on some later dispatch a UI test has no scheduler to advance.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val sink = Analytics(transport = transport, consent = consent, scope = scope, batchSize = 1)
        setContent {
            VintoTheme { App(seeds = { SEED }, vault = MemoryVault(), counting = counting(sink)) }
        }
        waitForIdle()
        block()
        waitForIdle()
        scope.cancel()
    }

    @Test
    fun nothingSentCarriesAnythingIdentifying() {
        val transport = Recording()
        walkTheApp(AnalyticsConsent(optedIn = true, platformObjects = false), transport) {
            // A solo round, the lesson and the online screen — the three surfaces a client
            // reports on, which between them are everywhere an identifier could come from.
            onNodeWithContentDescription("Play online").performClick()
            waitForIdle()
            onNodeWithContentDescription("Back").performClick()
            waitForIdle()
            onNodeWithContentDescription("How to play").performClick()
            waitForIdle()
        }

        val everything = transport.payloads.joinToString("\n")
        for (forbidden in listOf("nickname", "token", "roomCode", "playerId", "seat", "\"code\"")) {
            assertFalse(
                everything.contains(forbidden, ignoreCase = true),
                "'$forbidden' appears in what was sent: $everything",
            )
        }
        // Nothing shaped like a room code — six characters from the unambiguous alphabet.
        //
        // The enum labels have to come out first: `LESSON` and `ONLINE` are themselves six
        // uppercase characters, and the first version of this check failed on its own
        // vocabulary. Stripping what is *allowed* and then looking at the remainder is the
        // version that can only fail on something unexpected, which is the point.
        val vocabulary = buildList {
            addAll(FunnelStep.entries.map { it.name })
            addAll(Surface.entries.map { it.name })
            addAll(Difficulty.entries.map { it.name })
            addAll(FailureKind.entries.map { it.name })
        }
        val remainder = vocabulary.fold(everything) { text, word -> text.replace(word, "") }
        assertFalse(
            Regex("""\b[A-Z0-9]{6}\b""").containsMatchIn(remainder),
            "something shaped like a room code was sent: $everything",
        )
    }

    /**
     * And the whole thing is silent when the platform objects — which is the case the app
     * cannot ask the player about, because the player already answered somewhere else.
     */
    @Test
    fun aPlatformSignalSilencesEverything() {
        val transport = Recording()
        walkTheApp(AnalyticsConsent(optedIn = true, platformObjects = true), transport) {
            onNodeWithContentDescription("Play online").performClick()
            waitForIdle()
        }

        assertTrue(
            transport.payloads.isEmpty(),
            "Do-Not-Track was set and ${transport.payloads.size} payload(s) were still sent",
        )
    }

    @Test
    fun optingOutSendsNothing() {
        val transport = Recording()
        walkTheApp(AnalyticsConsent(optedIn = false, platformObjects = false), transport) {
            onNodeWithContentDescription("How to play").performClick()
            waitForIdle()
        }

        assertTrue(transport.payloads.isEmpty(), "an opted-out session sent: ${transport.payloads}")
    }

    private companion object {
        const val SEED = 20260819L
    }
}
