package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalFaces
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.game.faceTag
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.protocol.AvatarKind
import game.vinto.protocol.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An online table on a phone held sideways: drawn, and with every seat's face whole.
 *
 * Found by a layout review before it shipped. With the side seats' names turned to run along
 * the rim, a turned name is as tall as it is long — and on a sideways iPhone the side columns
 * have about seventy points of height. The left plate lays out its marks, then its name, then
 * its face, so the name took the height and the face was given none; a face drawn at no height
 * took the whole process down with a native trap, on every platform that draws with Skia: iOS,
 * the web and the desktop. Android lays its text out another way and might never have shown it.
 *
 * The face is the one part of a plate that must never give way: it is who is sitting there. The
 * name is the part that shrinks.
 */
@OptIn(ExperimentalTestApi::class)
class SidewaysPhoneTest {

    @Test
    fun aSidewaysPhoneDrawsTheTableWithEveryFaceWhole() = runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
        val dealt = teachingSession().view.value
        val view = dealt.copy(
            players = dealt.players.mapIndexed { i, seat ->
                seat.copy(name = NAMES[i], nickname = NAMES[i], isBot = false, isHuman = true)
            },
        )
        val faces = NAMES.mapIndexed { i, name ->
            name to PlayerProfile(name, AvatarKind.FACE.ordinal, 135L + i, i)
        }.toMap()
        val layout = TableLayout.forScreen(WIDE, HIGH)
        setContent {
            VintoTheme {
                CompositionLocalProvider(LocalFaces provides faces) {
                    Column(modifier = Modifier.size(WIDE, HIGH)) {
                        TableScreen(
                            state = TableState(view, tableFor(view), null, emptyList(), 1),
                            layout = layout,
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                            modifier = Modifier.weight(1f),
                            onLeave = {},
                        )
                        // The row a room keeps under its felt for the toss-in clock.
                        Box(modifier = Modifier.height(CLOCK_ROW))
                    }
                }
            }
        }
        waitForIdle()
        // Drawn, not only laid out: the fault was in the drawing.
        onRoot().captureToImage()

        NAMES.forEach { name ->
            val face = onNodeWithTag(faceTag(name), useUnmergedTree = true).fetchSemanticsNode().size
            val portrait = layout.sizes.avatar
            assertEquals(
                portrait.value.toInt(),
                face.height,
                "$name's face is ${face.width}x${face.height} on a sideways phone, not ${portrait.value}",
            )
        }
    }

    private companion object {
        val NAMES = listOf("Dusty Pebble", "Clever Harbour", "Golden Lantern", "Patient Sparrow")

        /** An iPhone 16 on its side, less the notch's inset at either end and the home bar. */
        val WIDE = 734.dp
        val HIGH = 372.dp
        val CLOCK_ROW = 52.dp

        const val WINDOW_W = 900
        const val WINDOW_H = 600
    }
}
