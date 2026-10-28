package game.vinto.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * A job that fails on the app's scope is reported, and the app goes on drawing.
 *
 * The scope used to be `rememberCoroutineScope { Crashes.handler() }`, and Compose gives that scope
 * a plain `Job` whose parent is the Recomposer's effect job. A plain job is cancelled by a child that
 * fails, and cancels its parent in turn — so one socket loop or one analytics send that threw ended
 * the Recomposer, and the screen stayed exactly as it was, deaf to every tap, for good. The handler
 * reported the failure; nothing reported that the app had stopped.
 */
@OptIn(ExperimentalTestApi::class)
class OneFailedJobLeavesTheAppRunningTest {

    @Test
    fun theScreenStillChangesAfterAJobOnTheAppScopeFails() = runComposeUiTest {
        var appScope: CoroutineScope? = null
        var shown by mutableStateOf("before")
        setContent {
            appScope = rememberAppScope()
            Text(shown)
        }
        waitForIdle()

        assertNotNull(appScope).launch { error("the socket loop fell over") }
        waitForIdle()

        shown = "after"
        waitForIdle()
        onNodeWithText("after").assertExists("one failed job on the app scope stopped the app from drawing")
    }
}
