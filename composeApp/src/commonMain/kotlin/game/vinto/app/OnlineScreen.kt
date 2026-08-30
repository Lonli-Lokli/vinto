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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.online_browse
import game.vinto.app.art.online_code
import game.vinto.app.art.online_code_detail
import game.vinto.app.art.online_create
import game.vinto.app.art.online_creating
import game.vinto.app.art.online_failed
import game.vinto.app.art.online_join
import game.vinto.app.art.online_nickname
import game.vinto.app.art.online_nickname_detail
import game.vinto.app.art.online_screen_title
import game.vinto.app.art.online_visibility
import game.vinto.app.art.online_visibility_detail
import game.vinto.app.art.online_visibility_private
import game.vinto.app.art.online_visibility_public
import game.vinto.app.art.settings_back
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.ChoiceRow
import game.vinto.app.theme.GameButton
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
 * The way into a room: a name, and either a code somebody shared or a new room of your own.
 *
 * Everything here is one decision deep on purpose — online play's whole entrance is "who am
 * I and which table" — and the nickname is remembered so the second visit is one tap. Room
 * creation talks to the service (`POST /rooms`) before anything is drawn for it, so a
 * failure lands here as a sentence rather than in a broken lobby.
 */
@Composable
fun OnlineScreen(
    connector: RoomConnector,
    vault: Vault,
    onEnterRoom: (code: String, nickname: String) -> Unit,
    onBrowse: (nickname: String) -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf(vault.identity { freshSeed() }.nickname) }
    var code by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var listed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun enter(room: String) {
        vault.rememberNickname(nickname)
        onEnterRoom(room.uppercase(), nickname)
    }

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
            Text(
                text = stringResource(Res.string.online_screen_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onFelt(),
            )

            NameAndCode(
                nickname = nickname,
                onNickname = { nickname = it },
                code = code,
                onCode = { code = it },
            )

            VisibilityChoice(listed) { listed = it }

            failure?.let {
                Text(
                    text = stringResource(Res.string.online_failed, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            GameButton(
                label = stringResource(Res.string.online_join),
                tone = ButtonTone.PLAY,
                onClick = { if (!busy && code.length == CODE_LENGTH) enter(code) },
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                // Busy while the room is being opened. `POST /rooms` is a round trip on a
                // phone's network, and the second tap that comes out of the silence is a
                // second room — one of which nobody ever joins.
                label = stringResource(
                    if (busy) Res.string.online_creating else Res.string.online_create,
                ),
                busy = busy,
                tone = ButtonTone.NEUTRAL,
                onClick = {
                    if (busy) return@GameButton
                    busy = true
                    failure = null
                    scope.launch {
                        // Two branches, and the compiler counts them. This was a `try` around
                        // a call that could fail four different ways on four platforms, with
                        // nothing checking that the `catch` was there at all — which is how a
                        // forgotten one elsewhere on this path became a crash rather than a
                        // sentence.
                        when (val answer = connector.createRoom(listed, nickname)) {
                            is RoomAnswer.Ok -> enter(answer.value.code)
                            is RoomAnswer.Failed -> failure = answer.reason
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                label = stringResource(Res.string.online_browse),
                tone = ButtonTone.NEUTRAL,
                onClick = {
                    vault.rememberNickname(nickname)
                    onBrowse(nickname)
                },
                modifier = Modifier.fillMaxWidth(),
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

/**
 * The two fields, and the limits they hold to.
 *
 * Both are capped as they are typed rather than checked afterwards: the service caps them
 * too — it must, since nothing stops a client posting whatever it likes — but a field that
 * silently accepts a hundred characters and then has them cut off elsewhere is a field that
 * lied to the person filling it in.
 */
@Composable
private fun NameAndCode(
    nickname: String,
    onNickname: (String) -> Unit,
    code: String,
    onCode: (String) -> Unit,
) {
    OutlinedTextField(
        value = nickname,
        onValueChange = { onNickname(it.take(NICKNAME_MAX)) },
        label = { Text(stringResource(Res.string.online_nickname)) },
        supportingText = { Text(stringResource(Res.string.online_nickname_detail)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = code,
        onValueChange = { onCode(it.uppercase().take(CODE_LENGTH)) },
        label = { Text(stringResource(Res.string.online_code)) },
        supportingText = { Text(stringResource(Res.string.online_code_detail)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Listed publicly, or reachable only by its code.
 *
 * Asked before the room exists rather than after, and defaulting to private. A room that is
 * listed by default publishes a game somebody meant to play with two friends — and a listing
 * cannot be taken back once a stranger has read it, so the safe answer is the one that is
 * already chosen.
 */
@Composable
private fun VisibilityChoice(listed: Boolean, onChoose: (Boolean) -> Unit) {
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
        onChoose = onChoose,
    )
    Text(
        text = stringResource(Res.string.online_visibility_detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onFelt(),
    )
}

private val Pad = 24.dp
private val Gap = 12.dp
private val ColumnMax = 420.dp
private const val CODE_LENGTH = 6
private const val NICKNAME_MAX = 16
