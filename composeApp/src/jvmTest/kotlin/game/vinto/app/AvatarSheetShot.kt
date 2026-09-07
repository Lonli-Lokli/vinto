package game.vinto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.AvatarGrounds
import game.vinto.app.theme.GeneratedAvatar
import game.vinto.app.theme.VintoTheme
import game.vinto.protocol.AvatarKind
import game.vinto.protocol.avatarSheet
import game.vinto.protocol.mintAvatar
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders a sheet of generated faces to a PNG so a person can look at them.
 *
 * Not a golden and not a gate — the geometry is judged by eye, which is the same reason
 * `ScreenshotTest`'s images are a maintainer's artefact rather than something CI asserts. It
 * writes to the build directory and asserts nothing beyond "the drawing did not throw".
 */
class AvatarSheetShot {

    @Test
    fun drawASheet() {
        val sheet = avatarSheet(from = 1_739_812_345_678L)
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(2f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(PAD.dp),
                verticalArrangement = Arrangement.spacedBy(GAP.dp),
            ) {
                sheet.entries.forEachIndexed { row, (kind, seeds) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                        seeds.forEachIndexed { col, seed ->
                            GeneratedAvatar(
                                traits = mintAvatar(kind, seed),
                                ground = AvatarGrounds[(row * 3 + col) % AvatarGrounds.size],
                                size = TILE.dp,
                            )
                        }
                    }
                }
            }
        }
        write(scene, "avatar-sheet.png")
    }

    /** The identity strip and the three destinations, as a phone lays them out. */
    @Test
    fun drawTheOnlineScreen() {
        val scene = ImageComposeScene(width = PHONE_W * 2, height = PHONE_H * 2, density = Density(2f)) {
            VintoTheme(dark = true) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E43))) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        IdentityStrip(
                            nickname = "Lucky Rowan",
                            avatarKind = AvatarKind.FACE,
                            avatarSeed = 1_739_812_345_678L,
                            ground = 2,
                            onNewName = {},
                            onPick = { _, _ -> },
                            onGround = {},
                            freshSeed = { 4L },
                        )
                    }
                }
            }
        }
        write(scene, "online-strip.png")
    }

    private fun write(scene: ImageComposeScene, name: String) {
        try {
            val image = scene.render(0L)
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("$name did not encode")
            val out = File("build/$name")
            out.parentFile?.mkdirs()
            out.writeBytes(png.bytes)
            println("wrote " + out.absolutePath)
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val PER_ROW = 6
        const val ROWS = 7
        const val TILE = 76
        const val GAP = 9
        const val PAD = 14
        const val PHONE_W = 411
        const val PHONE_H = 560
        const val WIDTH = (TILE * PER_ROW + GAP * (PER_ROW - 1) + PAD * 2) * 2
        const val HEIGHT = (TILE * ROWS + GAP * (ROWS - 1) + PAD * 2) * 2
    }
}
