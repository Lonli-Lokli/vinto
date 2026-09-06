package game.vinto.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import game.vinto.app.art.Res
import game.vinto.app.art.label_not_now
import game.vinto.app.art.label_update
import game.vinto.app.art.notice_update_available
import game.vinto.app.art.notice_update_title
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.client.RoomNotice
import game.vinto.client.RoomTrouble
import game.vinto.protocol.UPDATE_AVAILABLE_CODE
import org.jetbrains.compose.resources.stringResource

/**
 * The two ways the room asks for a newer build.
 *
 * A refusal at the door ([RoomTrouble.UPDATE_NEEDED]) is final: the only useful control is the
 * way to the store, so the trouble gets a button and nothing else. A notice after a seat is
 * taken ([UPDATE_AVAILABLE_CODE]) is advice: a dialog with the same button and a way to carry
 * on, because a person who is mid-game gets to finish it.
 */
@Composable
fun UpdateTheApp(trouble: RoomTrouble, onUpdate: () -> Unit) {
    if (trouble != RoomTrouble.UPDATE_NEEDED) return
    GameButton(
        label = stringResource(Res.string.label_update),
        tone = ButtonTone.PLAY,
        onClick = onUpdate,
    )
}

@Composable
fun UpdateNoticeDialog(notice: RoomNotice, onUpdate: () -> Unit, onNotNow: () -> Unit) {
    // The room's own sentence is English and for builds older than this dialog; a build that
    // has the dialog says it in the phone's language.
    val words = if (notice.code == UPDATE_AVAILABLE_CODE) {
        stringResource(Res.string.notice_update_available)
    } else {
        notice.message
    }
    AlertDialog(
        onDismissRequest = onNotNow,
        title = {
            Text(stringResource(Res.string.notice_update_title), style = MaterialTheme.typography.titleMedium)
        },
        text = { Text(words) },
        confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(Res.string.label_update)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(Res.string.label_not_now)) } },
    )
}
