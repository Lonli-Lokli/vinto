package game.vinto.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

actual fun shareReport(subject: String, body: String): Boolean {
    val context = AndroidStorage.context ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    // A chooser rather than a default: a report is sent once, and picking the app is part of
    // deciding to send it at all.
    val chooser = Intent.createChooser(send, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(chooser) }.isSuccess
}

actual fun copyToClipboard(text: String): Boolean {
    val context = AndroidStorage.context ?: return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Vinto", text))
    return true
}
