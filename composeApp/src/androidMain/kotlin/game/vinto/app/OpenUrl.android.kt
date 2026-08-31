package game.vinto.app

import android.content.Intent
import androidx.core.net.toUri

actual fun openUrl(url: String): Boolean {
    val context = AndroidStorage.context ?: return false
    // `NEW_TASK` because the context here is the application's rather than an activity's —
    // the same reason the share chooser needs it.
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(view) }.isSuccess
}
