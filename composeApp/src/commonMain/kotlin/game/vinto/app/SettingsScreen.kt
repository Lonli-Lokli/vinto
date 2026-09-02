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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.settings_analytics
import game.vinto.app.art.settings_analytics_detail
import game.vinto.app.art.settings_back
import game.vinto.app.art.settings_bots
import game.vinto.app.art.settings_bots_detail
import game.vinto.app.art.settings_contact
import game.vinto.app.art.settings_contact_detail
import game.vinto.app.art.settings_forget
import game.vinto.app.art.settings_forget_record
import game.vinto.app.art.settings_group_about
import game.vinto.app.art.settings_group_feel
import game.vinto.app.art.settings_group_game
import game.vinto.app.art.settings_group_privacy
import game.vinto.app.art.settings_haptics
import game.vinto.app.art.settings_haptics_detail
import game.vinto.app.art.settings_language
import game.vinto.app.art.settings_language_chosen
import game.vinto.app.art.settings_language_current
import game.vinto.app.art.settings_language_detail
import game.vinto.app.art.settings_language_device
import game.vinto.app.art.settings_link_failed
import game.vinto.app.art.settings_motion
import game.vinto.app.art.settings_motion_detail
import game.vinto.app.art.settings_off
import game.vinto.app.art.settings_on
import game.vinto.app.art.settings_open
import game.vinto.app.art.settings_pace
import game.vinto.app.art.settings_pace_detail
import game.vinto.app.art.settings_privacy
import game.vinto.app.art.settings_privacy_detail
import game.vinto.app.art.settings_record
import game.vinto.app.art.settings_record_detail
import game.vinto.app.art.settings_saved_game
import game.vinto.app.art.settings_saved_game_detail
import game.vinto.app.art.settings_share
import game.vinto.app.art.settings_share_body
import game.vinto.app.art.settings_share_detail
import game.vinto.app.art.settings_share_subject
import game.vinto.app.art.settings_sound
import game.vinto.app.art.settings_sound_detail
import game.vinto.app.art.settings_terms
import game.vinto.app.art.settings_terms_detail
import game.vinto.app.art.settings_theme
import game.vinto.app.art.settings_theme_detail
import game.vinto.app.art.settings_title
import game.vinto.app.art.settings_version
import game.vinto.app.theme.BackChevron
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.ChoiceRow
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Hairline
import game.vinto.app.theme.PickerField
import game.vinto.app.theme.PickerRow
import game.vinto.app.theme.PickerSheet
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltGold
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.app.theme.stamped
import game.vinto.client.MotionChoice
import game.vinto.client.Pace
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.forgetStats
import game.vinto.client.loadStats
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
    var pickingLanguage by remember { mutableStateOf(false) }

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
            // The way back, out of the thumb's way and consistent with every other screen.
            // It was a full-width slab at the foot of the scroll, which spent the most
            // reachable region on a phone on a control that duplicates the system gesture —
            // and on the *longest* screen in the app, so reaching it meant scrolling past
            // everything first.
            BackChevron(
                description = stringResource(Res.string.settings_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onFelt(),
                modifier = Modifier.semantics { heading() },
            )

            // Four groups rather than one column of eight.
            //
            // The controls have always fallen into these four and the screen never said so:
            // uniform spacing between every panel makes a list of switches out of what is
            // actually four decisions, and it left the two irreversible actions sitting at
            // the same weight and rhythm as a haptics toggle.
            Plaque(stringResource(Res.string.settings_group_game))
            Bots(settings, onChange)
            Pacing(settings, onChange)

            Plaque(stringResource(Res.string.settings_group_feel))
            Tongue(settings, onOpen = { pickingLanguage = true })
            Motion(settings, onChange)
            Palette(settings, onChange)
            Noise(settings, onChange)
            Buzz(settings, onChange)

            Plaque(stringResource(Res.string.settings_group_privacy))
            Counting(settings, onChange)

            // Personal, so forgettable. The anonymous counts have an opt-out because they
            // leave the device; this has one because it does not — a record about somebody
            // that they cannot clear is a record they did not agree to keep.
            ClearRecord()

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

            Plaque(stringResource(Res.string.settings_group_about))
            About()

            Text(
                text = stringResource(Res.string.settings_version, VERSION),
                fontSize = FootnoteSize,
                // Below the last panel, so on the felt rather than on paper.
                color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
                modifier = Modifier.padding(top = Tight),
            )
        }

        // Outside the scrolling column and inside the root box, which is the only place a
        // full-screen overlay can be composed from: inside the column it would be clipped to
        // the column's width and scroll away with it.
        TongueSheet(
            open = pickingLanguage,
            settings = settings,
            onChange = onChange,
            onDismiss = { pickingLanguage = false },
        )
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

/**
 * The counts, worded as what they are rather than as a category.
 *
 * "Analytics" is a word that makes people assume the worst, usually correctly. What this
 * actually controls is a handful of numbers with no identity attached — so the setting says
 * that, and says plainly that off means nothing is sent rather than less.
 */
@Composable
private fun Counting(settings: Settings, onChange: (Settings) -> Unit) {
    Setting(
        title = stringResource(Res.string.settings_analytics),
        detail = stringResource(Res.string.settings_analytics_detail),
    ) {
        ChoiceRow(
            options = listOf(true, false),
            selected = settings.analytics,
            label = { on -> stringResource(if (on) Res.string.settings_on else Res.string.settings_off) },
            onChoose = { on -> onChange(settings.copy(analytics = on)) },
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
        // Gold leaf, not the rail's brass: these plaques are stamped on the felt, where the
        // brass is 2.9:1 against the lighter cloth. Same metal, under the table's own lamp.
        Hairline(modifier = Modifier.weight(1f), colour = MaterialTheme.colorScheme.feltGold())
        Text(
            text = title.uppercase(),
            style = stamped(size = PlaqueSize, weight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.feltGold(),
            modifier = Modifier.semantics { heading() },
        )
        Hairline(modifier = Modifier.weight(1f), colour = MaterialTheme.colorScheme.feltGold())
    }
}

/**
 * Which language to read the game in — a drop-down, because there are twenty-one answers.
 *
 * It was a grid: "Follow the device" full width, then ten rows of two. That is a control taller
 * than the phone for a setting most people touch once, and it pushed Motion, Theme, Sound and
 * Haptics — the four things somebody actually came to this screen for — below the fold behind
 * nineteen languages they were not looking for.
 *
 * The endonym is still the point, and it is what the closed field shows. Somebody hunting for
 * Ukrainian is hunting for "Українська"; "Ukrainian" only helps a person who can already read
 * the language they are trying to leave.
 *
 * "Follow the device" is first in the list and is the default. It is a real answer rather than a
 * null one: most people want the language their phone is already in, and storing `en` for
 * somebody who never chose it would pin an English app on a Ukrainian phone the first time they
 * opened this screen.
 *
 * The open flag is the caller's, not this composable's, because the list is composed at the
 * screen's root where it can cover — see [PickerField]'s note. Splitting the state from the
 * control is the price of not putting a scroll inside a scroll.
 */
@Composable
private fun Tongue(settings: Settings, onOpen: () -> Unit) {
    val device = stringResource(Res.string.settings_language_device)
    val current = Language.withTag(settings.language)?.endonym ?: device

    Setting(
        title = stringResource(Res.string.settings_language),
        detail = stringResource(Res.string.settings_language_detail),
    ) {
        PickerField(
            // The panel above already says "Language"; see `PickerField`.
            label = null,
            value = current,
            description = stringResource(Res.string.settings_language_current, current),
            onOpen = onOpen,
        )
    }
}

/**
 * The list itself, composed over the screen rather than inside its scroll.
 *
 * Every row says what it is *and* whether it is the one in use, because the mark that carries
 * that is a coloured dot — which a screen reader cannot see, and colour alone is not an answer
 * to anybody who cannot either.
 */
@Composable
private fun TongueSheet(
    open: Boolean,
    settings: Settings,
    onChange: (Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    val device = stringResource(Res.string.settings_language_device)

    PickerSheet(
        open = open,
        title = stringResource(Res.string.settings_language),
        onDismiss = onDismiss,
    ) {
        LanguageRow(name = device, chosen = settings.language == null) {
            onChange(settings.copy(language = null))
            onDismiss()
        }
        Language.entries.forEach { language ->
            LanguageRow(name = language.endonym, chosen = settings.language == language.tag) {
                onChange(settings.copy(language = language.tag))
                onDismiss()
            }
        }
    }
}

/** One row, with the "chosen" state spoken rather than only coloured. */
@Composable
private fun LanguageRow(name: String, chosen: Boolean, onChoose: () -> Unit) {
    PickerRow(
        label = name,
        chosen = chosen,
        description = if (chosen) stringResource(Res.string.settings_language_chosen, name) else name,
        onChoose = onChoose,
    )
}

/** Blue for the language in use, charcoal for the twenty that are not. */
private fun toneFor(chosen: Boolean): ButtonTone =
    if (chosen) ButtonTone.KEEP else ButtonTone.NEUTRAL

/**
 * The pages that belong to the game but are not in it, and a way to pass it on.
 *
 * **No "rate this app".** There is no store listing to send anybody to — 9.10 has not shipped
 * — and a review button that opens nothing is worse than an absent one: it reads as the app
 * being broken by the person most inclined to say so publicly. It belongs here the day there
 * is a listing and not before.
 *
 * **No language selector either**, for the same shape of reason: the only translation that
 * exists is `values/`. WORDS.md §6h made adding one a file and no code, and no file has been added, so
 * a selector today is a control with a single option. The unblocking step is a translated
 * `strings.xml`, not screen work.
 */
@Composable
private fun About() {
    val failed = remember { mutableStateOf<String?>(null) }

    LinkRow(
        title = stringResource(Res.string.settings_privacy),
        detail = stringResource(Res.string.settings_privacy_detail),
        url = Pages.PRIVACY,
        onFailed = { failed.value = it },
    )
    LinkRow(
        title = stringResource(Res.string.settings_terms),
        detail = stringResource(Res.string.settings_terms_detail),
        url = Pages.TERMS,
        onFailed = { failed.value = it },
    )
    LinkRow(
        title = stringResource(Res.string.settings_contact),
        detail = stringResource(Res.string.settings_contact_detail),
        url = Pages.CONTACT,
        onFailed = { failed.value = it },
    )

    val subject = stringResource(Res.string.settings_share_subject)
    val body = stringResource(Res.string.settings_share_body, Pages.GAME)
    val clipboard = LocalClipboardManager.current
    Setting(
        title = stringResource(Res.string.settings_share),
        detail = stringResource(Res.string.settings_share_detail),
    ) {
        GameButton(
            label = stringResource(Res.string.settings_share),
            tone = ButtonTone.NEUTRAL,
            // Falls through to the clipboard where a platform has no share sheet, which is
            // the JVM and iOS today. Doing nothing visible is the one answer a share button
            // must not give.
            onClick = { if (!shareText(subject, body)) clipboard.setText(AnnotatedString(body)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Said out loud rather than swallowed. A locked-down desktop has no browse action at all,
    // and a button that silently does nothing is indistinguishable from a broken app — so the
    // address goes on the screen where it can at least be read or copied.
    failed.value?.let {
        Text(
            text = stringResource(Res.string.settings_link_failed, it),
            fontSize = FootnoteSize,
            color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
        )
    }
}

/** A page, opened in whatever this device uses to read one. */
@Composable
private fun LinkRow(title: String, detail: String, url: String, onFailed: (String) -> Unit) {
    Setting(title = title, detail = detail) {
        GameButton(
            label = stringResource(Res.string.settings_open),
            tone = ButtonTone.NEUTRAL,
            onClick = { if (!openUrl(url)) onFailed(url) },
            modifier = Modifier.fillMaxWidth(),
        )
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

/**
 * Throwing the local record away.
 *
 * Shown only when there is something to throw away, for the same reason the home screen's line
 * is: an empty statistics section on a fresh install advertises homework nobody has been set.
 *
 * It clears immediately and without a confirmation dialog. That is deliberate — the thing
 * being deleted is four small numbers about a card game, and a modal asking somebody whether
 * they are sure they want to forget their streak takes the decision more seriously than the
 * person does.
 */
@Composable
private fun ClearRecord() {
    val vault = LocalVault.current ?: return
    var cleared by remember { mutableStateOf(false) }
    val stats = remember(vault, cleared) { vault.loadStats() }
    if (stats.roundsPlayed == 0) return

    Setting(
        title = stringResource(Res.string.settings_record),
        detail = stringResource(Res.string.settings_record_detail),
    ) {
        GameButton(
            label = stringResource(Res.string.settings_forget_record),
            tone = ButtonTone.DANGER,
            onClick = {
                vault.forgetStats()
                cleared = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
