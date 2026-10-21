// Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only in the capture build,
// See CLAUDE.md, "The capture build".
package game.vinto.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import game.vinto.app.capture.CAPTURE_ID
import game.vinto.app.capture.Staged

/**
 * The capture build's screen, declared only in `src/capture/AndroidManifest.xml`: the app on the
 * store scene the intent extra [CAPTURE_ID] names, or with none the app as a player opens it.
 *
 *     adb shell am start -n app.kupalinka.vinto.capture/game.vinto.app.CaptureActivity \
 *       --es kupalinka-capture-a47d267ea8c1795fbb7df224 table
 *
 * zdymak drives exactly this (`Scripts/capture-android.sh`). It is `MainActivity` without what a
 * capture has no use for: no launch screen, no invitation link, and no store (`BuildConfig.STORE`),
 * as `VintoApplication` starts no crash reports in this build (`BuildConfig.CRASH_REPORTS`).
 */
class CaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        holdPhonesUpright()
        val handle = intent?.getStringExtra(CAPTURE_ID)
        setContent { Staged(handle) { App(firstScreen = it) } }
    }

    /**
     * The next scene. The process survives between states, one `am start` per screen bringing this
     * window forward rather than making a new one, so without this every shot after the first is
     * whatever was already on screen: how a run once produced six pictures of the same board.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /** Upright on a phone, free on a tablet, as `MainActivity` holds it. */
    private fun holdPhonesUpright() {
        requestedOrientation = if (phoneHeldUpright(resources.configuration.smallestScreenWidthDp)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        holdPhonesUpright()
    }
}
