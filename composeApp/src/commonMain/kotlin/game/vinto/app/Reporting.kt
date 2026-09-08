package game.vinto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import game.vinto.app.art.Res
import game.vinto.app.art.report_body
import game.vinto.app.art.report_copy
import game.vinto.app.art.report_dismiss
import game.vinto.app.art.report_send
import game.vinto.app.art.report_subject
import game.vinto.app.art.report_title
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.DialogGap
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.VintoDialog
import game.vinto.client.Recording
import game.vinto.client.toJson
import org.jetbrains.compose.resources.stringResource

/**
 * Reporting a problem, offered rather than performed.
 *
 * It used to copy the game to the clipboard and then tell the player it had — which leaves them
 * holding a wall of JSON and no idea where to put it, and is where most bug reports stop. Now it
 * says what would be sent and what is *not* in it, and hands the sending to the platform's own
 * share sheet, where the player already knows how to mail it, message it or keep it. The
 * clipboard is still there as the second answer, and as the answer on platforms that have
 * nothing to share with.
 *
 * **It lives in the settings now rather than on the table.** A bug icon in the header was a
 * control every player saw on every turn for the sake of the few who would ever press it, on the
 * one screen with no room to spare — six controls on a phone header is five too many. What it
 * needs is not to be *near*, it is to still have the game in it when it is reached, which is why
 * this takes a recording rather than making one: the settings screen has no game, and the caller
 * that sent somebody there does.
 */
@Composable
fun Reporting(recording: (() -> Recording)?, open: Boolean, onDone: () -> Unit) {
    if (recording == null) return

    val subject = stringResource(Res.string.report_subject)

    // `LocalClipboardManager` is deprecated in favour of `LocalClipboard`, and the replacement is
    // still not usable from common code in Compose Multiplatform 1.12 — see the note this moved
    // from in `GameScreen`.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    ReportProblem(
        open = open,
        onSend = {
            val made = recording()
            if (!shareText(subject, made.toJson())) clipboard.setText(AnnotatedString(made.toJson()))
            onDone()
        },
        onCopy = {
            clipboard.setText(AnnotatedString(recording().toJson()))
            onDone()
        },
        onDismiss = onDone,
    )
}

/** The three answers to one question, which is why they are stacked rather than split. */
@Composable
private fun ReportProblem(open: Boolean, onSend: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    VintoDialog(
        open = open,
        onDismiss = onDismiss,
        title = stringResource(Res.string.report_title),
        body = stringResource(Res.string.report_body),
    ) {
        // All three stacked rather than split across a confirm and a dismiss slot: a row of two
        // with a wrapped third is what that produces, and these are three answers to one question
        // rather than two and an afterthought.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogGap),
        ) {
            GameButton(
                label = stringResource(Res.string.report_send),
                tone = ButtonTone.PLAY,
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                label = stringResource(Res.string.report_copy),
                tone = ButtonTone.NEUTRAL,
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                label = stringResource(Res.string.report_dismiss),
                tone = ButtonTone.NEUTRAL,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
