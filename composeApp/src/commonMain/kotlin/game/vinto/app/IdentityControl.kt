package game.vinto.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.online_avatar_another
import game.vinto.app.art.online_avatar_ground
import game.vinto.app.art.online_avatar_name
import game.vinto.app.art.online_avatar_pick
import game.vinto.app.art.online_nickname
import game.vinto.app.art.online_nickname_detail
import game.vinto.app.theme.AvatarGrounds
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.GeneratedAvatar
import game.vinto.app.theme.Hairline
import game.vinto.app.theme.LocalFeedback
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Slate
import game.vinto.app.theme.onFelt
import game.vinto.app.theme.pressable
import game.vinto.app.theme.stamped
import game.vinto.protocol.AVATAR_ROW
import game.vinto.protocol.AvatarKind
import game.vinto.protocol.avatarRow
import game.vinto.protocol.mintAvatar
import org.jetbrains.compose.resources.stringResource

/**
 * Who you are at the table: a face, a name, and the two controls that change them.
 *
 * **Not an [game.vinto.app.theme.ActionTile], and that is the whole point of this file.** The
 * name used to wear one, stacked identically above "open a room", "join with a code" and
 * "browse public rooms" — so a chevron promised it would take you somewhere and then re-rolled
 * a word in place, and a proper noun sat in the slot where its three neighbours carry
 * imperatives. Four identical slabs, one of which is not a destination.
 *
 * What it is instead: an identity strip. No card, no chevron, no lift — the face and the name
 * on the felt, with the list of three below a hairline. A player reads it as an attribute of
 * this screen rather than as a fourth thing to tap, which is what it is.
 *
 * The picker underneath opens on request, because choosing a face is a thing somebody does once
 * and the three ways into a game are what they came for. Seven rows, one per family, six faces
 * each — a phone fits six across at plate size, and a family per row is what stops forty-two
 * generated marks reading as one undifferentiated wall.
 */
@Composable
fun IdentityStrip(
    nickname: String,
    avatarKind: AvatarKind,
    avatarSeed: Long,
    ground: Int,
    onNewName: () -> Unit,
    onPick: (AvatarKind, Long) -> Unit,
    onGround: (Int) -> Unit,
    /** A fresh number for the sheet, so "another set" has somewhere new to walk from. */
    freshSeed: () -> Long,
) {
    var open by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(freshSeed()) }
    val feedback = LocalFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The face is the tap target for the picker: it is the thing being changed, so it is
            // the thing to press. A separate "choose a face" row would be a fourth item in a list
            // this control exists to stay out of.
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(Res.string.online_avatar_pick)) {
                        feedback.commit()
                        open = !open
                    },
            ) {
                GeneratedAvatar(
                    traits = mintAvatar(avatarKind, avatarSeed),
                    ground = AvatarGrounds[ground % AvatarGrounds.size],
                    size = FaceSize,
                )
            }

            // Announced as one thing — "Your name at the table: Lucky Rowan" — rather than as two
            // texts a screen reader reads as unrelated lines. The label alone says nothing and
            // the name alone does not say what it is.
            val label = stringResource(Res.string.online_nickname)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "$label: $nickname" },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
                )
                Text(
                    text = nickname,
                    style = stamped(size = NameSize),
                    color = MaterialTheme.colorScheme.onFelt(),
                )
            }

            // A circular arrow, drawn rather than fetched from a glyph font — the same rule the
            // rest of the app's chrome follows, and the reason it looks the same on all three
            // platforms. It spins a third of a turn on each press, so a name that happens to
            // re-roll to something similar still shows that the press was heard.
            RerollButton(onClick = onNewName)
        }

        Text(
            text = stringResource(Res.string.online_nickname_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
            modifier = Modifier.padding(top = Tight, bottom = Gap),
        )

        AnimatedVisibility(visible = open) {
            AvatarPicker(
                from = from,
                avatarKind = avatarKind,
                avatarSeed = avatarSeed,
                ground = ground,
                onPick = onPick,
                onGround = onGround,
                onAnother = { from = freshSeed() },
            )
        }

        // The line that says the list below is a different kind of thing from the strip above.
        Hairline(modifier = Modifier.padding(vertical = Gap))
    }
}

/**
 * Seven rows of six, one family each, and the two things that change what they sit on.
 *
 * Its own composable rather than a branch inside the strip, because it is a different job: the
 * strip says who you are in one line, and this is the wall you only see if you want to change
 * it. Splitting them also keeps the strip readable, which is the part every visit to this screen
 * renders whether or not anybody opens the picker.
 */
@Composable
private fun AvatarPicker(
    from: Long,
    avatarKind: AvatarKind,
    avatarSeed: Long,
    ground: Int,
    onPick: (AvatarKind, Long) -> Unit,
    onGround: (Int) -> Unit,
    onAnother: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tight)) {
        AvatarKind.entries.forEach { kind ->
            FamilyRow(
                kind = kind,
                from = from,
                ground = ground,
                chosenIn = avatarSeed.takeIf { kind == avatarKind },
                onPick = onPick,
            )
        }

        Text(
            text = stringResource(Res.string.online_avatar_ground),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
            modifier = Modifier.padding(top = Gap),
        )
        GroundRow(ground = ground, onGround = onGround)

        GameButton(
            label = stringResource(Res.string.online_avatar_another),
            tone = ButtonTone.NEUTRAL,
            onClick = onAnother,
            modifier = Modifier.fillMaxWidth().padding(top = Gap),
        )
    }
}

/** One family's six faces, with a ring on the one in use. */
@Composable
private fun FamilyRow(
    kind: AvatarKind,
    from: Long,
    ground: Int,
    /** The seed chosen in THIS family, or null when the choice is in another row. */
    chosenIn: Long?,
    onPick: (AvatarKind, Long) -> Unit,
) {
    val feedback = LocalFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        avatarRow(kind, from, AVATAR_ROW).forEach { seed ->
            val ring = if (seed == chosenIn) {
                Modifier.border(Chosen, Rail.gold, CircleShape)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier.clip(CircleShape).clickable {
                    feedback.commit()
                    onPick(kind, seed)
                },
            ) {
                GeneratedAvatar(
                    traits = mintAvatar(kind, seed),
                    ground = AvatarGrounds[ground % AvatarGrounds.size],
                    size = TileSize,
                    modifier = ring,
                )
            }
        }
    }
}

/** The measured grounds, every one of which `ContrastTest` holds against the ink on it. */
@Composable
private fun GroundRow(ground: Int, onGround: (Int) -> Unit) {
    val feedback = LocalFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        AvatarGrounds.forEachIndexed { index, colour ->
            val here = index == ground
            Box(
                modifier = Modifier
                    .size(SwatchSize)
                    .clip(CircleShape)
                    .background(colour)
                    .border(
                        if (here) Chosen else Edge,
                        if (here) Rail.gold else Slate.inkDim,
                        CircleShape,
                    )
                    .clickable {
                        feedback.commit()
                        onGround(index)
                    },
            )
        }
    }
}

/**
 * The re-roll: a circular arrow the app draws itself.
 *
 * Its own button rather than a tap on the name, because the name is not a control — it is the
 * answer. A player who wants to read what they have been called should be able to look at it
 * without changing it, which a tappable word does not allow.
 */
@Composable
private fun RerollButton(onClick: () -> Unit) {
    val another = stringResource(Res.string.online_avatar_name)
    var turns by remember { mutableStateOf(0f) }
    val spin by animateFloatAsState(turns, label = "reroll")
    val feedback = LocalFeedback.current

    Surface(
        onClick = {
            feedback.commit()
            turns += TURN
            onClick()
        },
        shape = ControlShape,
        color = Color.Transparent,
        border = BorderStroke(Edge, Rail.line),
        contentColor = Rail.ink,
        modifier = Modifier.pressable()
            .size(ButtonSize)
            .semantics { contentDescription = another },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = ARROW,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onFelt(),
                modifier = Modifier.rotate(spin),
            )
        }
    }
}

private const val ARROW = "⟳"
private const val TURN = 120f

/**
 * A control, so a rounded square — the circles on this screen are faces.
 *
 * The same 10 dp the header's four controls and `GameButton` are cut to, which is what makes a
 * round thing on a Vinto screen mean "this is somebody" rather than "this is a button".
 */
private val ControlShape = RoundedCornerShape(10.dp)

private val FaceSize = 56.dp
private val TileSize = 44.dp
private val SwatchSize = 28.dp
private val ButtonSize = 44.dp
private const val NameSize = 20
private val Gap = 12.dp
private val Tight = 6.dp
private val Edge = 1.dp

/** The ring on the face and the ground in use — gold, which this app spends only on meaning. */
private val Chosen = 2.dp

/** The app's own dimming for a second line on the felt. */

/**
 * The app's own dimming for a second line on the felt.
 *
 * 0.85, not a darker one: `ScreenContrastTest` measures the rendered pixels and 0.72 put "Your
 * name at the table" at 4.37:1 on the light felt, under WCAG's 4.5. The screen this sits on
 * already uses 0.85 for exactly this, so it is the same number rather than a third one.
 */
private const val Quiet = 0.85f
