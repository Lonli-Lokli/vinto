package game.vinto.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import game.vinto.app.link.offerOpenedLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
