package game.vinto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import game.vinto.shapes.Difficulty

private val Pad = 24.dp
private val Gap = 12.dp
private val Column = 420.dp

/**
 * Where a game starts.
 *
 * Only single player, deliberately: it is the mode that needs no server, no account and no
 * second person, and it is finished. Rooms exist and are gated, but nothing here opens one —
 * a button that sometimes reaches a server would undo the property `NoNetworkGuardTest`
 * exists to protect.
 */
@Composable
fun HomeScreen(difficulty: Difficulty, onDifficulty: (Difficulty) -> Unit, onPlay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Pad),
        verticalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Vinto", style = MaterialTheme.typography.displaySmall)
        Text(
            "Lowest hand wins. You are playing three bots.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(Gap),
            modifier = Modifier.padding(vertical = Gap),
        ) {
            Difficulty.entries.forEach { level ->
                FilterChip(
                    selected = level == difficulty,
                    onClick = { onDifficulty(level) },
                    label = { Text(level.serialName.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().widthIn(max = Column),
        ) {
            Text("Play")
        }
    }
}
