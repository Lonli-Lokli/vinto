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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * Platform-gate payload for task 2a.2 — measuring the Compose/Wasm bundle.
 *
 * Deliberately minimal but not trivial: it pulls in material3, foundation and state, which
 * is the realistic floor for a Compose web client. The number that matters is the produced
 * bundle (including the skiko WebAssembly runtime), not this UI.
 */
@Composable
private fun GatePreview() {
    var taps by remember { mutableStateOf(0) }

    MaterialTheme {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Vinto — Compose/Wasm platform gate",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("Taps: $taps", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { taps++ }) { Text("Tap") }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        GatePreview()
    }
}
