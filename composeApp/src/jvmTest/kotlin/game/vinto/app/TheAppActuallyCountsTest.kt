package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.client.MemoryVault
import game.vinto.protocol.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The app's screens are given the app's counter.
 *
 * Reported as a doubt about the dashboard — *"have some doubts if stats for vinto are
 * correct"* — with every panel reading **Nothing in this period**, including the solo ones, on
 * a phone that had played a great many solo rounds.
 *
 * They were not correct, and the reason is one missing line. `LocalCounting` defaults to
 * `NoCounting`, *"a sink that drops everything, used by tests, previews and goldens"* — which
 * is the right default for a thing whose failure mode is sending data nobody asked to send,
 * and which means the app has to say otherwise. It never did: `App` built the sink, wrapped it
 * as `count`, and then used `count` nowhere at all. Every screen read the default. Not one
 * client event has ever left a device.
 *
 * Everything around it worked and was tested — consent, the batch, the transport, the
 * allow-list at `/e`, `writeDataPoint`, the dashboard's config — which is why the hole sat in
 * the one link none of them covered. `App`'s own docstring for the `counting` parameter says
 * what it is for: *"a test needs to see what the app would have sent, and the only honest way
 * to check that is to let it send to something that records."* Nothing did.
 */
@OptIn(ExperimentalTestApi::class)
class TheAppActuallyCountsTest {

    @Test
    fun aLessonOpenedAndLeftIsCounted() {
        val heard = mutableListOf<AnalyticsEvent>()

        runComposeUiTest {
            setContent {
                App(
                    seeds = { SEED },
                    vault = MemoryVault(),
                    counting = { event -> heard += event },
                    marketing = "teach",
                )
            }
            waitForIdle()
        }

        // The lesson counts itself on the way out, however it goes — the composition ending is
        // the app being killed, which is the commonest way a lesson is left.
        assertTrue(
            heard.any { it is AnalyticsEvent.Lesson },
            "the app dropped its own counts on the floor: heard $heard",
        )
    }

    private companion object {
        const val SEED = 20_260_921L
    }
}
