package game.vinto.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.settings_back
import game.vinto.app.art.settings_bots
import game.vinto.app.art.settings_bots_detail
import game.vinto.app.art.settings_forget
import game.vinto.app.art.settings_haptics
import game.vinto.app.art.settings_haptics_detail
import game.vinto.app.art.settings_off
import game.vinto.app.art.settings_on
import game.vinto.app.art.settings_motion
import game.vinto.app.art.settings_motion_detail
import game.vinto.app.art.settings_pace
import game.vinto.app.art.settings_pace_detail
import game.vinto.app.art.settings_saved_game
import game.vinto.app.art.settings_sound
import game.vinto.app.art.settings_sound_detail
import game.vinto.app.art.settings_saved_game_detail
import game.vinto.app.art.settings_theme
import game.vinto.app.art.settings_theme_detail
import game.vinto.app.art.settings_title
import game.vinto.app.art.settings_version
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.ChoiceRow
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.app.theme.stamped
import game.vinto.client.MotionChoice
import game.vinto.client.Pace
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.shapes.Difficulty
import org.jetbrains.compose.resources.stringResource

private val Pad = 20.dp
private val Gap = 12.dp
private val Tight = 4.dp
private val ColumnMax = 460.dp
private val PanelCorner = 12.dp

/**
 * The handful of things worth choosing.
 *
 * Every one of them is here because playing the game raised it: the bots were too easy or too
 * hard, the table moved faster than anybody could read, the phone's theme is not always the
 * one you want at a table, and a saved game sometimes wants abandoning. Nothing is here to
 * fill the screen — a settings list that mostly does not matter teaches players not to look
 * in it for the one that does.
 *
 * Each setting says what it *does* rather than what it is called. "Calm / Steady / Brisk" is a
 * label; "how quickly the table plays out what happened" is the setting.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    canForget: Boolean,
    onChange: (Settings) -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ColumnMax)
                .verticalScroll(rememberScrollState())
                .padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Plaque(stringResource(Res.string.settings_title))

            Bots(settings, onChange)
            Pacing(settings, onChange)
            Motion(settings, onChange)
            Palette(settings, onChange)
            Noise(settings, onChange)
            Buzz(settings, onChange)

            if (canForget) {
                Setting(
                    title = stringResource(Res.string.settings_saved_game),
                    detail = stringResource(Res.string.settings_saved_game_detail),
                ) {
                    GameButton(
                        label = stringResource(Res.string.settings_forget),
                        tone = ButtonTone.DANGER,
                        onClick = onForget,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text(
                text = stringResource(Res.string.settings_version, VERSION),
                fontSize = FootnoteSize,
                // Below the last panel, so on the felt rather than on paper.
                color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
                modifier = Modifier.padding(top = Tight),
            )

            GameButton(
                label = stringResource(Res.string.settings_back),
                tone = ButtonTone.NEUTRAL,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Bots(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_bots),
        detail = stringResource(Res.string.settings_bots_detail),
    ) {
        ChoiceRow(
            options = Difficulty.entries,
            selected = settings.difficulty,
            label = { stringResource(it.label()) },
            onChoose = { onChange(settings.copy(difficulty = it)) },
        )
    }
}

@Composable
private fun Pacing(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_pace),
        detail = stringResource(Res.string.settings_pace_detail),
    ) {
        ChoiceRow(
            options = Pace.entries,
            selected = settings.pace,
            label = { stringResource(it.label()) },
            onChoose = { onChange(settings.copy(pace = it)) },
        )
    }
}

@Composable
private fun Motion(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_motion),
        detail = stringResource(Res.string.settings_motion_detail),
    ) {
        ChoiceRow(
            options = MotionChoice.entries,
            selected = settings.motion,
            label = { stringResource(it.label()) },
            onChoose = { onChange(settings.copy(motion = it)) },
        )
    }
}

@Composable
private fun Palette(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_theme),
        detail = stringResource(Res.string.settings_theme_detail),
    ) {
        ChoiceRow(
            options = ThemeChoice.entries,
            selected = settings.theme,
            label = { stringResource(it.label()) },
            onChoose = { onChange(settings.copy(theme = it)) },
        )
    }
}

@Composable
private fun Noise(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_sound),
        detail = stringResource(Res.string.settings_sound_detail),
    ) {
        ChoiceRow(
            options = listOf(true, false),
            selected = settings.sound,
            label = { on -> stringResource(if (on) Res.string.settings_on else Res.string.settings_off) },
            onChoose = { on -> onChange(settings.copy(sound = on)) },
        )
    }
}

@Composable
private fun Buzz(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_haptics),
        detail = stringResource(Res.string.settings_haptics_detail),
    ) {
        ChoiceRow(
            options = listOf(true, false),
            selected = settings.haptics,
            label = { on -> stringResource(if (on) Res.string.settings_on else Res.string.settings_off) },
            onChoose = { on -> onChange(settings.copy(haptics = on)) },
        )
    }
}

/**
 * A screen's name, engraved rather than headed.
 *
 * Caps, letterspaced, and set between two hairlines that stop short of the edges — the way a
 * name is cut into a brass plate screwed to a table. A left-aligned bold sentence is how a
 * page announces itself; this is how an object is labelled, and the settings are meant to
 * read as part of the table rather than as a page about it.
 *
 * Not in the wordmark face: this string is translated, and the wordmark carries no Cyrillic.
 */
@Composable
private fun Plaque(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
        Text(
            text = title.uppercase(),
            style = stamped(size = PlaqueSize, weight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
    }
}

/** One thing to choose: what it is, what it does, and the control for it. */
@Composable
private fun Setting(title: String, detail: String, control: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelCorner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
    ) {
        Column(
            modifier = Modifier.padding(Gap),
            verticalArrangement = Arrangement.spacedBy(Tight),
        ) {
            Text(title, fontSize = TitleRowSize, fontWeight = FontWeight.Bold, color = Rail.ink)
            Text(
                text = detail,
                fontSize = DetailSize,
                color = Rail.inkDim,
                modifier = Modifier.padding(bottom = Tight),
            )
            control()
        }
    }
}

private const val PlaqueSize = 17

private val TitleRowSize = 17.sp
private val DetailSize = 13.sp
/** Second-rank text on the felt. */
private const val Quiet = 0.75f

private val FootnoteSize = 12.sp
