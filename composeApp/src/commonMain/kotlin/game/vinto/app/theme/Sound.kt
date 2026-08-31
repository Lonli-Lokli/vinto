package game.vinto.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import game.vinto.app.art.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The table's four sounds, and no more than four.
 *
 * The same discipline as [Feedback]'s three kicks: a card leaving the dealer's hand, a card
 * landing, a penalty, and the round ending. A game that plays a sound at every event teaches
 * the player to mute it, and then the one sound that mattered is muted with the rest. The
 * files are synthesized placeholders (`tools/make-sfx.py`) — swap the WAVs under
 * `composeResources/files/sfx/` for recorded ones and nothing here changes.
 */
enum class Sfx(internal val file: String) {
    /** A card flicked off the deck — the deal, and every flight's launch. */
    DEAL("deal.wav"),

    /** A card arriving where it was going. */
    LAND("land.wav"),

    /** A rule biting: the penalty card, the flinch. */
    THUD("thud.wav"),

    /** The hands going face-up: the round is over. */
    CHIME("chime.wav"),
}

/**
 * One platform's way of actually making noise.
 *
 * Loaded once with each sound's bytes (and the resource URI, for platforms that would
 * rather stream a URL than decode bytes), then asked to [play] with no further ceremony.
 * Playing a sound that never loaded is silence, not an error — audio is garnish, and a
 * platform whose decoder balked must not take the game down with it.
 */
expect class SoundPlayer() {
    fun load(sfx: Sfx, bytes: ByteArray, uri: String)
    fun play(sfx: Sfx)
    fun dispose()
}

/**
 * The sounds in force: the player, gated by the setting.
 *
 * Silent by default — like [LocalFeedback], so a test, a preview or a screenshot harness is
 * silent without having to say so.
 */
class Sounds internal constructor(
    private val player: SoundPlayer?,
    private val enabled: Boolean,
) {
    fun play(sfx: Sfx) {
        if (enabled) player?.play(sfx)
    }
}

val LocalSounds = staticCompositionLocalOf { Sounds(player = null, enabled = false) }

/**
 * The platform's audio, obeying the player's setting.
 *
 * The files load once per composition of the app root, off the UI thread (`readBytes` is a
 * suspend call), and the player is disposed with the composition. A load that fails leaves
 * that sound silent, which is the failure mode audio deserves.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberSounds(enabled: Boolean): Sounds {
    val player = remember { SoundPlayer() }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Sfx.entries.forEach { sfx ->
            val path = "files/sfx/${sfx.file}"
            runCatching {
                player.load(sfx, Res.readBytes(path), Res.getUri(path))
            }
        }
        loaded = true
    }
    DisposableEffect(Unit) { onDispose { player.dispose() } }

    return remember(enabled, loaded) { Sounds(player.takeIf { loaded }, enabled) }
}
