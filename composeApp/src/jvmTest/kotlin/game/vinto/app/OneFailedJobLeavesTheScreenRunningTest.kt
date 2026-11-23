package game.vinto.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CreatedRoom
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.protocol.PublicRoom
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A job that fails in a screen's own scope is reported, and the app goes on drawing.
 *
 * `OneFailedJobLeavesTheAppRunningTest` holds the app's long-lived scope; this holds the screens'.
 * Public rooms, Open a room, the share card, the support button and the table's moves each
 * launched on a bare `rememberCoroutineScope()`, whose plain `Job` sits under the Recomposer's
 * effect job: one launch that threw cancelled it, and the screen stayed as it was, deaf to every
 * tap, with nothing reported. Public rooms in a web build did exactly that the first time `fetch`
 * threw where it was called.
 */
@OptIn(ExperimentalTestApi::class)
class OneFailedJobLeavesTheScreenRunningTest {

    @Test
    fun publicRoomsStillDrawsAfterTheListThrows() = runComposeUiTest {
        var shown by mutableStateOf("before")
        setContent {
            VintoTheme {
                Column {
                    Text(shown)
                    DiscoverScreen(connector = throwsOnList(), onJoin = {}, onBack = {})
                }
            }
        }
        waitForIdle()

        shown = "after"
        waitForIdle()
        onNodeWithText("after").assertExists("a list that threw stopped the app from drawing")
    }

    /**
     * And no screen can go back to the bare scope: `rememberScreenScope` and `rememberAppScope`
     * are the only ways to one, and `AppScope.kt`, where they are built, the one caller.
     */
    @Test
    fun noScreenLaunchesOnABareComposeScope() {
        val bare = File("src")
            .walkTopDown()
            .onEnter { !it.name.endsWith("Test") }
            .filter { it.isFile && it.extension == "kt" && it.name != "AppScope.kt" }
            .filter { file ->
                file.readLines().any { line ->
                    val code = line.trimStart()
                    !code.startsWith("import ") && !code.startsWith("*") && !code.startsWith("//") &&
                        code.contains("rememberCoroutineScope(")
                }
            }
            .map { it.path }
            .toList()
        assertTrue(
            bare.isEmpty(),
            "these launch on a bare rememberCoroutineScope(), where one failed job stops the app " +
                "drawing — use rememberScreenScope(): $bare",
        )
    }

    private fun throwsOnList(): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = error("not asked")
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            error("not asked")

        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = error("fetch threw where it was called")
    }
}
