package game.vinto.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import game.vinto.app.crash.Crashes
import game.vinto.app.crash.appReportingScope
import game.vinto.app.link.offerOpenedLink

class MainActivity : ComponentActivity() {

    /**
     * A scope that outlives the activity, because a crash report is fired while the process is
     * ending. Held by the activity only because there is nowhere smaller to hold it; `install`
     * is idempotent, so a configuration change does not chain a second handler.
     */
    private val reporting = appReportingScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First of all, and before `setContent` rather than inside it. A crash during
        // `AndroidStorage.attach`, while the deep link is read, or in the first composition
        // used to happen with nothing listening — the handler was installed by a
        // `LaunchedEffect` inside `App()`, which is after all of that. A crash on the launcher
        // is the one report worth having most, and it was the one that could never arrive.
        Crashes.install(reporting)
        // Before anything composes, so the home screen can already know whether there is a
        // game to come back to.
        AndroidStorage.attach(this)
        // And before that too, so an invite is already waiting when `App()` first reads it
        // rather than arriving a frame later and pushing a screen the player did not ask for
        // on top of the one they were looking at.
        offerOpenedLink(intent?.dataString)
        setContent { App() }
    }

    /**
     * A second invite, while the app is already open.
     *
     * The activity is `singleTop` by default for a launcher activity being re-launched, so a
     * tapped link with the app running arrives here rather than through `onCreate`. Without
     * this, the second invitation of an evening does nothing at all — which looks exactly
     * like a broken link.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerOpenedLink(intent.dataString)
    }
}
