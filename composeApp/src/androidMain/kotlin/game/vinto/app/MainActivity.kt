package game.vinto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything composes, so the home screen can already know whether there is a
        // game to come back to.
        AndroidStorage.attach(this)
        setContent { App() }
    }
}
