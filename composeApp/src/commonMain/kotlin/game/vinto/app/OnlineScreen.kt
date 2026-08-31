package game.vinto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.online_back
import game.vinto.app.art.online_browse
import game.vinto.app.art.online_browse_detail
import game.vinto.app.art.online_code
import game.vinto.app.art.online_code_detail
import game.vinto.app.art.online_create
import game.vinto.app.art.online_creating
import game.vinto.app.art.online_join
import game.vinto.app.art.online_join_detail
import game.vinto.app.art.online_join_screen
import game.vinto.app.art.online_join_title
import game.vinto.app.art.online_name_placeholder
import game.vinto.app.art.online_nickname
import game.vinto.app.art.online_nickname_detail
import game.vinto.app.art.online_open_detail
import game.vinto.app.art.online_open_screen
import game.vinto.app.art.online_open_title
import game.vinto.app.art.online_screen_title
import game.vinto.app.art.online_visibility
import game.vinto.app.art.online_visibility_detail
import game.vinto.app.art.online_visibility_private
import game.vinto.app.art.online_visibility_public
import game.vinto.app.link.roomCodeFrom
import game.vinto.app.theme.ActionTile
import game.vinto.app.theme.BackChevron
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.ChoiceRow
import game.vinto.app.theme.CodeField
import game.vinto.app.theme.CodeLength
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.VintoField
import game.vinto.app.theme.errorOnFelt
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.Vault
import game.vinto.client.identity
import game.vinto.client.rememberNickname
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Which of the three things you came to do.
 *
 * This screen was one form asking for everything, followed by three verbs that each need a
 * different part of it. Join wants a name and a code; opening a room wants a name and a
 * visibility; browsing wants only a name. So somebody who came to browse was asked for a room
 * code they do not have, and somebody joining a friend's table was asked to decide the
 * visibility of a room they are not creating — which is also the one control here whose wrong
 * answer cannot be taken back, sitting as noise on two paths out of three.
 *
 * Intent first. The name is asked once because all three want it and it is remembered between
 * visits; everything mode-specific has moved to the path it belongs to. The consequence worth
 * having is that **Open a room always works**: the primary action on the front door of online
 * play is no longer one that requires somebody to have sent you something first.
 */
@Composable
fun OnlineScreen(
    vault: Vault,
    onOpenRoom: (nickname: String) -> Unit,
    onJoinByCode: (nickname: String) -> Unit,
    onBrowse: (nickname: String) -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf(vault.identity { freshSeed() }.nickname) }

    // Saved on the way out of this screen rather than on every keystroke: the vault is a
    // write to storage, and a name is typed a character at a time.
    fun leaveWith(go: (String) -> Unit) {
        vault.rememberNickname(nickname)
        go(nickname)
    }

    Scaffold(title = stringResource(Res.string.online_screen_title), onBack = onBack) {
        VintoField(
            value = nickname,
            onValueChange = { nickname = it.take(NicknameMax) },
            label = stringResource(Res.string.online_nickname),
            detail = stringResource(Res.string.online_nickname_detail),
            placeholder = stringResource(Res.string.online_name_placeholder),
        )

        // The one that needs nothing from anybody else, so it is the one wearing the colour.
        ActionTile(
            title = stringResource(Res.string.online_open_title),
            detail = stringResource(Res.string.online_open_detail),
            accent = ButtonTone.PLAY.rim,
            onClick = { leaveWith(onOpenRoom) },
        )
        ActionTile(
            title = stringResource(Res.string.online_join_title),
            detail = stringResource(Res.string.online_join_detail),
            onClick = { leaveWith(onJoinByCode) },
        )
        ActionTile(
            title = stringResource(Res.string.online_browse),
            detail = stringResource(Res.string.online_browse_detail),
            onClick = { leaveWith(onBrowse) },
        )
    }
}

/**
 * Opening a room: who may find it, and one button.
 *
 * The visibility choice lives here and only here. It used to sit above a Join button it had
 * nothing to do with, which is how a control whose wrong answer publishes somebody's private
 * game gets tapped absent-mindedly on the way past.
 */
@Composable
fun OpenRoomScreen(
    connector: RoomConnector,
    nickname: String,
    onEnterRoom: (code: String, nickname: String) -> Unit,
    onBack: () -> Unit,
) {
    // **Listed, unless the host says otherwise** — reversed on the product owner's decision.
    //
    // The previous default was private, and the reasoning for it was sound as far as it went:
    // a listing cannot be taken back once a stranger has read it, so the safe answer is the
    // one already chosen. What that reasoning left out is that the room has to be *found*.
    // With nobody listed, the public browser is an empty screen, and a game whose whole
    // online mode depends on two strangers meeting has no way for them to meet.
    //
    // The cost is real and worth naming: somebody opening a room for two friends now
    // publishes it unless they notice the control. What makes that acceptable rather than
    // careless is that the choice is on this screen, one tap away, before the room exists —
    // and that a listing exposes a nickname and a seat count and nothing else.
    var listed by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<RoomAnswer.Failed?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(title = stringResource(Res.string.online_open_screen), onBack = onBack) {
        Text(
            text = stringResource(Res.string.online_visibility),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onFelt(),
        )
        ChoiceRow(
            options = listOf(false, true),
            selected = listed,
            label = {
                if (it) {
                    stringResource(Res.string.online_visibility_public)
                } else {
                    stringResource(Res.string.online_visibility_private)
                }
            },
            onChoose = { listed = it },
        )
        Text(
            text = stringResource(Res.string.online_visibility_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onFelt(),
        )

        Trouble(failure)

        GameButton(
            // Busy while the room is being opened. `POST /rooms` is a round trip on a phone's
            // network, and the second tap that comes out of the silence is a second room.
            label = stringResource(
                if (busy) Res.string.online_creating else Res.string.online_create,
            ),
            busy = busy,
            tone = ButtonTone.PLAY,
            onClick = {
                if (busy) return@GameButton
                busy = true
                failure = null
                scope.launch {
                    // Two branches, and the compiler counts them. This was a `try` around a
                    // call that can fail four different ways on four platforms, with nothing
                    // checking the `catch` was there at all.
                    when (val answer = connector.createRoom(listed, nickname)) {
                        is RoomAnswer.Ok -> onEnterRoom(answer.value.code, nickname)
                        is RoomAnswer.Failed -> failure = answer
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Joining: six characters, and a button that lights up when they are all there.
 *
 * The button is [GameButton]'s `enabled` rather than a live-looking control whose `onClick`
 * returns — which is what it was, and what made the brightest thing on the old screen do
 * nothing at all for anybody who had not been sent a code.
 */
@Composable
fun JoinCodeScreen(
    nickname: String,
    onEnterRoom: (code: String, nickname: String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Scaffold(title = stringResource(Res.string.online_join_screen), onBack = onBack) {
        CodeField(
            value = code,
            // Through `roomCodeFrom` on the way in, so pasting a whole invitation works:
            // what lands on somebody's clipboard is `https://…/r/ABC123`, and asking them to
            // find the six characters inside it and retype them is asking them to do the
            // app's job. It returns null for anything that is not one, which leaves whatever
            // they typed alone.
            onValueChange = { typed -> code = roomCodeFrom(typed) ?: typed },
            label = stringResource(Res.string.online_code),
            detail = stringResource(Res.string.online_code_detail),
        )

        GameButton(
            label = stringResource(Res.string.online_join),
            tone = ButtonTone.PLAY,
            enabled = code.length == CodeLength,
            onClick = { onEnterRoom(code.uppercase(), nickname) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What went wrong, in the player's words with the service's underneath.
 *
 * Showing only the service's line is what put "server-side action validation is not
 * implemented yet" in front of a player. Showing only ours loses the detail that makes a bug
 * report useful, so both, at two sizes.
 */
@Composable
private fun Trouble(failure: RoomAnswer.Failed?) {
    failure ?: return
    Text(
        text = troubled(failure.trouble),
        style = MaterialTheme.typography.bodyMedium,
        // On the felt, not on a scheme surface — the light scheme's own error red is 1:1
        // against green cloth. See `errorOnFelt`.
        color = MaterialTheme.colorScheme.errorOnFelt(),
    )
    Text(
        text = failure.reason,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
    )
}

/**
 * The shape every screen in this flow has: felt, a title with a way back, and a column.
 *
 * One place, because three screens that agree about their margins by coincidence stop agreeing
 * the first time one of them is edited.
 */
@Composable
private fun Scaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = ColumnMax)
                .verticalScroll(rememberScrollState())
                .padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            BackChevron(
                description = stringResource(Res.string.online_back),
                onClick = onBack,
                modifier = Modifier.padding(bottom = TitleGap),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onFelt(),
                modifier = Modifier.padding(bottom = TitleGap),
            )
            content()
        }
    }
}

private val Pad = 24.dp
private val Gap = 14.dp
private val TitleGap = 2.dp
private val ColumnMax = 420.dp
private const val NicknameMax = 16

/**
 * The service's own words, under the sentence that says what to do about them. Quieter than
 * that sentence and no quieter: 0.6 measured 3.35:1 on the lighter felt.
 */
private const val Quiet = 0.85f
