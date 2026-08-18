package game.vinto.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one UI, shared by Android, iOS and the browser. Each platform contributes only an
 * entry point (`MainActivity`, `MainViewController`, `main`) that hosts this composable.
 *
 * Still the platform-gate payload from task 2a.2, not the real game: it pulls in material3,
 * foundation and state, which is the realistic floor for a Compose client. The real screens
 * arrive in phase 7 (design D6). What it proves today is that one Compose source tree runs
 * on all three clients.
 */
@Composable
fun App() {
    var taps by remember { mutableStateOf(0) }

    MaterialTheme {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Vinto — Compose Multiplatform",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("Running on: ${platformName()}", style = MaterialTheme.typography.bodyLarge)
            Text("Taps: $taps", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { taps++ }) { Text("Tap") }
        }
    }
}

/**
 * Names the host platform. Exists so each target is forced to supply an `actual` — the
 * cheapest possible proof that the expect/actual wiring is sound on every target before
 * anything real depends on it (storage and clocks will, per design D1).
 */
expect fun platformName(): String
