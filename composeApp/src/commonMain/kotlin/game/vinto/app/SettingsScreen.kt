package game.vinto.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.home_version
import game.vinto.app.art.settings_analytics
import game.vinto.app.art.settings_analytics_detail
import game.vinto.app.art.settings_back
import game.vinto.app.art.settings_bots
import game.vinto.app.art.settings_bots_detail
import game.vinto.app.art.settings_contact
import game.vinto.app.art.settings_contact_detail
import game.vinto.app.art.settings_explain
import game.vinto.app.art.settings_forget
import game.vinto.app.art.settings_forget_record
import game.vinto.app.art.settings_group_about
import game.vinto.app.art.settings_group_about_summary
import game.vinto.app.art.settings_group_game
import game.vinto.app.art.settings_group_game_summary
import game.vinto.app.art.settings_group_privacy
import game.vinto.app.art.settings_group_privacy_summary
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
import game.vinto.app.art.settings_original
import game.vinto.app.art.settings_original_detail
import game.vinto.app.art.settings_pace
import game.vinto.app.art.settings_pace_detail
import game.vinto.app.art.settings_privacy
import game.vinto.app.art.settings_privacy_detail
import game.vinto.app.art.settings_rate
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
import game.vinto.app.art.settings_studio
import game.vinto.app.art.settings_studio_detail
import game.vinto.app.art.settings_support
import game.vinto.app.art.settings_support_buy
import game.vinto.app.art.settings_support_detail
import game.vinto.app.art.settings_support_link
import game.vinto.app.art.settings_support_link_detail
import game.vinto.app.art.settings_support_thanks
import game.vinto.app.art.settings_support_unavailable
import game.vinto.app.art.settings_terms
import game.vinto.app.art.settings_terms_detail
import game.vinto.app.art.settings_theme
import game.vinto.app.art.settings_theme_detail
import game.vinto.app.art.settings_title
import game.vinto.app.art.stats_best
import game.vinto.app.art.stats_played
import game.vinto.app.art.stats_separator
import game.vinto.app.art.stats_streak
import game.vinto.app.art.stats_won
import game.vinto.app.openUrl
import game.vinto.app.theme.ActionTile
import game.vinto.app.theme.BackChevron
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.ChoiceRow
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Hairline
import game.vinto.app.theme.LocalFeedback
import game.vinto.app.theme.PickerField
import game.vinto.app.theme.PickerRow
import game.vinto.app.theme.PickerSheet
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltGold
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.app.theme.pressable
import game.vinto.app.theme.stamped
import game.vinto.client.MotionChoice
import game.vinto.client.Pace
import game.vinto.client.Settings
import game.vinto.client.ThemeChoice
import game.vinto.client.forgetStats
import game.vinto.client.loadStats
import game.vinto.shapes.Difficulty
import kotlinx.coroutines.launch
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

/**
 * Which page of the settings is showing.
 *
 * Carried on `Screen.Settings` rather than held here, so the phone's back gesture and the
 * chevron drawn on the screen mean the same thing. `Discover` taught that lesson the hard way —
 * one gesture meaning two things depending on which you used — and a submenu with local state
 * would have repeated it exactly.
 */
enum class SettingsPage { ROOT, GAME, PRIVACY, ABOUT }

/** What the heading says on each page. A submenu titled "Settings" says nothing about itself. */
private fun titleOf(page: SettingsPage) = when (page) {
    SettingsPage.ROOT -> Res.string.settings_title
    SettingsPage.GAME -> Res.string.settings_group_game
    SettingsPage.PRIVACY -> Res.string.settings_group_privacy
    SettingsPage.ABOUT -> Res.string.settings_group_about
}

@Composable
fun SettingsScreen(
    settings: Settings,
    canForget: Boolean,
    page: SettingsPage,
    onOpen: (SettingsPage) -> Unit,
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
                text = stringResource(titleOf(page)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onFelt(),
                modifier = Modifier.semantics { heading() },
            )

            // Three of the eighteen at the top, and three doors to the rest.
            //
            // Sound, haptics and the review button are what people actually come here to press;
            // the other fifteen are decisions somebody makes once. Eighteen tall panels made the
            // three common ones as far away as the fifteen rare ones, which is the wrong way
            // round on the longest screen in the app.
            Page(page, settings, canForget, onOpen, onChange, onForget) {
                pickingLanguage = true
            }

            // The same words as the home screen's corner, from the same two values.
            //
            // It said "Vinto v1.0" — the app's own name, to somebody already inside the app,
            // and no build number. The number a player can read back is the *build*: it is what
            // a crash report is matched against and what a store listing is checked against,
            // and the marketing version alone identifies twenty builds at once. One string for
            // both places, so the two can never disagree about what this build is called.
            Text(
                text = stringResource(Res.string.home_version, VERSION, BUILD_NUMBER),
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

/**
 * Whichever page of the settings is open.
 *
 * Split from [SettingsScreen] so the scaffold around it — the felt, the chevron, the heading,
 * the version — stays one readable block rather than a frame around a four-branch `when`.
 */
@Composable
@Suppress("LongParameterList")
private fun Page(
    page: SettingsPage,
    settings: Settings,
    canForget: Boolean,
    onOpen: (SettingsPage) -> Unit,
    onChange: (Settings) -> Unit,
    onForget: () -> Unit,
    onPickLanguage: () -> Unit,
) {
    when (page) {
        SettingsPage.ROOT -> {
            SupportRow()
            Noise(settings, onChange)
            Buzz(settings, onChange)
            RateRow()

            Door(
                title = stringResource(Res.string.settings_group_game),
                summary = stringResource(Res.string.settings_group_game_summary),
                onOpen = { onOpen(SettingsPage.GAME) },
            )
            Door(
                title = stringResource(Res.string.settings_group_privacy),
                summary = stringResource(Res.string.settings_group_privacy_summary),
                onOpen = { onOpen(SettingsPage.PRIVACY) },
            )
            Door(
                title = stringResource(Res.string.settings_group_about),
                summary = stringResource(Res.string.settings_group_about_summary),
                onOpen = { onOpen(SettingsPage.ABOUT) },
            )
        }

        SettingsPage.GAME -> {
            Bots(settings, onChange)
            Pacing(settings, onChange)
            Tongue(settings, onOpen = onPickLanguage)
            Motion(settings, onChange)
            Palette(settings, onChange)

            // Personal, so forgettable. The anonymous counts have an opt-out because
            // they leave the device; this has one because it does not — a record about
            // somebody that they cannot clear is a record they did not agree to keep.
            ClearRecord()

            if (canForget) {
                Setting(
                    title = stringResource(Res.string.settings_saved_game),
                    detail = stringResource(Res.string.settings_saved_game_detail),
                    // Inline, never behind the (i): a consequence that cannot be undone
                    // belongs beside the button that causes it.
                    always = true,
                ) {
                    GameButton(
                        label = stringResource(Res.string.settings_forget),
                        tone = ButtonTone.DANGER,
                        onClick = onForget,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SettingsPage.PRIVACY -> {
            Counting(settings, onChange)
            PrivacyLinks()
        }

        SettingsPage.ABOUT -> {
            About()
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
    SwitchRow(
        title = stringResource(Res.string.settings_sound),
        detail = stringResource(Res.string.settings_sound_detail),
        on = settings.sound,
        mark = { drawSpeaker(it) },
        onToggle = { on -> onChange(settings.copy(sound = on)) },
    )
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
    SwitchRow(
        title = stringResource(Res.string.settings_analytics),
        detail = stringResource(Res.string.settings_analytics_detail),
        on = settings.analytics,
        // Never behind the (i): this sentence is a claim about what leaves the device, and a
        // player deciding whether to allow it should not have to open anything to read it.
        always = true,
        mark = { drawTally(it) },
        onToggle = { on -> onChange(settings.copy(analytics = on)) },
    )
}

@Composable
private fun Buzz(settings: Settings, onChange: (Settings) -> Unit) {
    SwitchRow(
        title = stringResource(Res.string.settings_haptics),
        detail = stringResource(Res.string.settings_haptics_detail),
        on = settings.haptics,
        mark = { drawBuzz(it) },
        onToggle = { on -> onChange(settings.copy(haptics = on)) },
    )
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
 * **"Rate this game" is here, and its links point at listings that are not live yet.** That
 * reverses an earlier decision recorded in this file, which said a review button opening
 * nothing reads as the app being broken. The reasoning was sound and the arithmetic was wrong:
 * adding two constants later costs a whole review cycle on each store, while the window in which
 * the links are dead is one in which the only people who can press them are TestFlight and Play
 * internal testers — who know what they are testing. Each link heals itself the moment its store
 * approves, with no build. `storeReviewUrl()` picks the right one per platform.
 *
 * **No language selector either**, for the same shape of reason: the only translation that
 * exists is `values/`. WORDS.md §6h made adding one a file and no code, and no file has been added, so
 * a selector today is a control with a single option. The unblocking step is a translated
 * `strings.xml`, not screen work.
 */
@Composable
private fun PrivacyLinks() {
    val failed = remember { mutableStateOf<String?>(null) }

    // Beside the switch rather than under About: what is counted and the document describing it
    // are one errand, and they were two screens apart because one is a toggle and one is a link.
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
    OpenFailed(failed)
}

/** Whose game this is, whose app, and how to reach whoever made it. */
@Composable
private fun About() {
    val failed = remember { mutableStateOf<String?>(null) }

    LinkRow(
        title = stringResource(Res.string.settings_contact),
        detail = stringResource(Res.string.settings_contact_detail),
        url = Pages.CONTACT,
        onFailed = { failed.value = it },
    )

    // Whose game this is, and whose app. The home screen already says the first of these
    // under the wordmark; this is where somebody who went looking for it finds the address
    // itself, beside the studio's own page for the app. The order is the honest one: the game
    // that existed first, then the client somebody wrote for it.
    LinkRow(
        title = stringResource(Res.string.settings_original),
        detail = stringResource(Res.string.settings_original_detail),
        url = Pages.OFFICIAL,
        onFailed = { failed.value = it },
        // Never behind the (i). This sentence carries the address of the game this app is an
        // unofficial client for, and "we did mention it, one tap in" is not that promise —
        // `AttributionTest` is what keeps it said.
        always = true,
    )
    LinkRow(
        title = stringResource(Res.string.settings_studio),
        detail = stringResource(Res.string.settings_studio_detail),
        url = Pages.THIS_APP,
        onFailed = { failed.value = it },
        always = true,
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

    OpenFailed(failed)
}

/**
 * A way to say thanks, at the top of the settings.
 *
 * One price and no box to type in, because neither store lets a buyer choose a figure — see
 * `Support.kt` for the whole reasoning. The price shown is the store's own formatted string, so
 * a player in Warsaw reads zloty and one in Tokyo reads yen without this app knowing either.
 *
 * **It says what it does not do.** "It unlocks nothing — there is nothing locked" is the line,
 * because a support button in a game usually means a paywall somewhere, and the first thing
 * somebody wants to know is which. Answering it before they ask is the difference between a
 * thank-you and a sales pitch.
 *
 * On a platform with no store it says so rather than showing a button that cannot work: the
 * RELIABILITY.md §6p rule, that a trouble picks the sentence.
 */
@Composable
private fun SupportRow() {
    val offer = remember { supportOffer() }

    // Not here on the web or the desktop, because the header already carries it.
    //
    // `Support.Elsewhere` is exactly the platforms whose table header draws the cup — the same
    // condition, read from the same seam — so on those a row here is the second copy of one
    // offer, on the screen a player opened to change a setting. The phones keep it: their offer
    // is an in-app purchase, it has no header of its own, and this is the only place it lives.
    if (offer is Support.Elsewhere) return
    var thanked by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val failed = remember { mutableStateOf<String?>(null) }

    Setting(
        title = stringResource(Res.string.settings_support),
        detail = stringResource(Res.string.settings_support_detail),
    ) {
        when {
            thanked -> Text(
                text = stringResource(Res.string.settings_support_thanks),
                fontSize = TitleRowSize,
                color = Rail.gold,
            )

            offer is Support.Offered -> GameButton(
                label = stringResource(Res.string.settings_support_buy, offer.price),
                tone = ButtonTone.PLAY,
                onClick = { scope.launch { thanked = buySupport() } },
                modifier = Modifier.fillMaxWidth(),
            )

            // Web and desktop, where no store's rules reach and the amount is the giver's own.
            offer is Support.Elsewhere -> Column(verticalArrangement = Arrangement.spacedBy(Tight)) {
                Text(
                    text = stringResource(Res.string.settings_support_link_detail),
                    fontSize = DetailSize,
                    color = Rail.inkDim,
                )
                GameButton(
                    label = stringResource(Res.string.settings_support_link),
                    tone = ButtonTone.PLAY,
                    onClick = { if (!openUrl(offer.url)) failed.value = offer.url },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> Text(
                text = stringResource(Res.string.settings_support_unavailable),
                fontSize = DetailSize,
                color = Rail.inkDim,
            )
        }
    }
    OpenFailed(failed)
}

/**
 * Leaving a review, on the first screen of the settings.
 *
 * One of the three things somebody actually comes here to press, so it is not behind a door.
 * Asks the store the player actually installed from — and on the web and the desktop, where
 * there is no such store, the app's own page. Never hidden: a control that exists on two
 * platforms and not the other two is read as a fault.
 */
@Composable
private fun RateRow() {
    val failed = remember { mutableStateOf<String?>(null) }
    val url = storeReviewUrl()

    // A button, not a panel. There is nothing to choose and nothing to explain — a title, a
    // sentence and an (i) around one action is furniture, and this is the one row on the page
    // whose whole content is "press this".
    GameButton(
        label = stringResource(Res.string.settings_rate),
        tone = ButtonTone.NEUTRAL,
        onClick = { if (!openUrl(url)) failed.value = url },
        modifier = Modifier.fillMaxWidth(),
    )
    OpenFailed(failed)
}

/**
 * A two-state setting on one line: a mark, what it is, and where it stands.
 *
 * The panel these replace was a title, a sentence and a full-width two-position track — three
 * stacked blocks for a yes/no. Sound and haptics are the two things people come to this screen
 * to flip, and they were as tall as a difficulty picker.
 *
 * [detail] stays available behind the (i) unless [always], which is for a sentence that changes
 * what somebody decides: the counting switch makes a claim about what leaves the device, and a
 * privacy claim is not a footnote.
 */
@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    on: Boolean,
    always: Boolean = false,
    mark: DrawScope.(Color) -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val feedback = LocalFeedback.current
    // Read here, not inside the Canvas: `Rail.inkDim` is a composable property and a draw
    // lambda is not a composition.
    val markInk = Rail.inkDim

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelCorner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
    ) {
        Column(modifier = Modifier.padding(Gap), verticalArrangement = Arrangement.spacedBy(Tight)) {
            SwitchLine(
                title = title,
                on = on,
                markInk = markInk,
                mark = mark,
                explain = if (always) null else fun() { open = !open },
                open = open,
                onToggle = {
                    feedback.commit()
                    onToggle(!on)
                },
            )
            if (always || open) {
                Text(text = detail, fontSize = DetailSize, color = Rail.inkDim)
            }
        }
    }
}

/**
 * A link that would not open, said out loud rather than swallowed.
 *
 * A locked-down desktop has no browse action at all, and a button that silently does nothing is
 * indistinguishable from a broken app — so the address goes on the screen where it can at least
 * be read or copied. Shared by the three pages that carry links, so each of them fails the same
 * way rather than two of them failing silently.
 */
@Composable
private fun OpenFailed(failed: androidx.compose.runtime.MutableState<String?>) {
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
private fun LinkRow(
    title: String,
    detail: String,
    url: String,
    onFailed: (String) -> Unit,
    always: Boolean = false,
) {
    Setting(title = title, detail = detail, always = always) {
        GameButton(
            label = stringResource(Res.string.settings_open),
            tone = ButtonTone.NEUTRAL,
            onClick = { if (!openUrl(url)) onFailed(url) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One thing to choose: what it is, what it does, and the control for it.
 *
 * **The sentence is behind an (i) by default**, which is what took this screen from eighteen
 * tall panels to something a thumb can cross. Every one of those sentences is worth keeping —
 * several answer a real question, like what the counts actually count — but a player who opens
 * Settings to turn the sound off should not have to read past twelve of them to do it.
 *
 * [always] is the exception, and it is deliberately narrow: a consequence that cannot be undone,
 * and a claim about privacy, are not footnotes. Putting "forgetting it cannot be undone" one tap
 * away from the button that does it is exactly the place an explanation earns its height.
 */
@Composable
private fun Setting(
    title: String,
    detail: String,
    always: Boolean = false,
    control: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shown = always || open

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = TitleRowSize,
                    fontWeight = FontWeight.Bold,
                    color = Rail.ink,
                    modifier = Modifier.weight(1f),
                )
                if (!always) Explain(open = open, title = title, onToggle = { open = !open })
            }
            if (shown) {
                Text(
                    text = detail,
                    fontSize = DetailSize,
                    color = Rail.inkDim,
                    modifier = Modifier.padding(bottom = Tight),
                )
            }
            control()
        }
    }
}

/**
 * The (i) that shows a row's sentence.
 *
 * Drawn rather than taken from an icon font, like every other mark in this app — see
 * `Panels.kt` on the sheet's own ✕. Named for a screen reader with the row it belongs to, so
 * "more about Sound" is what gets read rather than twelve identical "info" buttons.
 */
@Composable
private fun Explain(open: Boolean, title: String, onToggle: () -> Unit) {
    val label = stringResource(Res.string.settings_explain, title)
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (open) Rail.edge else Rail.line),
        contentColor = Rail.inkDim,
        modifier = Modifier.size(ExplainTap).semantics { contentDescription = label }.pressable(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "i",
                fontSize = DetailSize,
                fontWeight = FontWeight.Bold,
                color = if (open) Rail.ink else Rail.inkDim,
            )
        }
    }
}

/**
 * A way into a group of settings, rather than the group itself.
 *
 * Eighteen rows on one screen is what this replaces. The summary is the point: a door with only
 * a name behind it makes somebody open all three to find the one they want, so each says what is
 * inside in the words they would have gone looking for.
 */
@Composable
private fun Door(title: String, summary: String, onOpen: () -> Unit) {
    ActionTile(title = title, detail = summary, onClick = onOpen)
}

private const val PlaqueSize = 17

/**
 * The one line a two-state setting is: its mark, what it is, and where it stands.
 *
 * Split from [SwitchRow] because the row also carries the sentence underneath, and one function
 * holding both was doing two jobs — the shape detekt reads as complexity and a reader reads as a
 * long `if`.
 */
@Composable
@Suppress("LongParameterList")
private fun SwitchLine(
    title: String,
    on: Boolean,
    markInk: Color,
    mark: DrawScope.(Color) -> Unit,
    explain: (() -> Unit)?,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tight),
    ) {
        Canvas(modifier = Modifier.size(MarkSize)) { mark(markInk) }
        Text(
            text = title,
            fontSize = TitleRowSize,
            fontWeight = FontWeight.Bold,
            color = Rail.ink,
            modifier = Modifier.weight(1f),
        )
        explain?.let { Explain(open = open, title = title, onToggle = it) }
        // The state and the control are one thing: the word says where it stands and pressing
        // it is what moves it, which is what a switch has always been.
        // A fixed width, because this is a switch and a switch does not resize as it is thrown.
        //
        // It sized itself to its label, so "ON" was narrower than "OFF" and the control moved
        // under the finger that had just pressed it — and at four points of padding either side
        // it read as a word set on the panel rather than as something to press. One width, wide
        // enough for the longer of the two in every language the app is written in.
        GameButton(
            label = stringResource(if (on) Res.string.settings_on else Res.string.settings_off),
            tone = if (on) ButtonTone.PLAY else ButtonTone.NEUTRAL,
            onClick = onToggle,
            modifier = Modifier.widthIn(min = SwitchWidth),
            compact = true,
        )
    }
}

/** Wide enough that ON and OFF are the same object, in every locale. */
private val SwitchWidth = 84.dp

private val ExplainTap = 32.dp
private val MarkSize = 18.dp

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

    // The numbers themselves, which used to sit under the wordmark on the home screen. They
    // were the reason to open the app a second time, and they were also the first thing anybody
    // saw — a scoreboard on the front door of a game somebody opened to play. Here they are
    // where a person goes when they want to know, beside the button that clears them.
    val line = listOfNotNull(
        stringResource(Res.string.stats_played, stats.roundsPlayed),
        stats.winRate?.let { stringResource(Res.string.stats_won, it) },
        stats.bestHand?.let { stringResource(Res.string.stats_best, it) },
        stats.streak.takeIf { it > 1 }?.let { stringResource(Res.string.stats_streak, it) },
    ).joinToString(stringResource(Res.string.stats_separator))

    Setting(
        title = stringResource(Res.string.settings_record),
        detail = stringResource(Res.string.settings_record_detail),
    ) {
        Text(
            text = line,
            fontSize = TitleRowSize,
            color = Rail.ink,
            modifier = Modifier.padding(bottom = Tight),
        )
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
