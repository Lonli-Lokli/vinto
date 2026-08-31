package game.vinto.app

import android.content.Intent

actual fun shareText(subject: String, body: String): Boolean {
    val context = AndroidStorage.context ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    // A chooser rather than a default: a report is sent once, an invitation goes to one
    // person, and picking the app is part of deciding to send it at all.
    val chooser = Intent.createChooser(send, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(chooser) }.isSuccess
}
