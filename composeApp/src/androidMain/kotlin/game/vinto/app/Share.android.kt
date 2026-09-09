package game.vinto.app

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

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

/**
 * The chooser again, with the code as a `content://` attachment.
 *
 * The file is written to the cache and served through `FileProvider` rather than handed over as a
 * `file://` path: Android has refused those between apps since N, and the failure is an exception
 * in the *sender*, so the button would take out the screen it sits on rather than doing nothing.
 * `androidApp`'s manifest declares the provider and `res/xml/file_paths.xml` names this directory;
 * both are part of this function and neither is optional.
 *
 * `EXTRA_TEXT` rides along with `EXTRA_STREAM`, so a target that takes both (a chat) sends the
 * message and the picture, and one that takes only an image still gets something scannable.
 */
actual fun sharePicture(subject: String, body: String, picture: ByteArray): Boolean {
    val context = AndroidStorage.context ?: return false
    val uri = runCatching {
        val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, SHARE_FILE).apply { writeBytes(picture) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return false

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/** Named in `androidApp/src/main/res/xml/file_paths.xml`; changing one means changing both. */
private const val SHARE_DIRECTORY = "share"
private const val SHARE_FILE = "vinto-code.png"
