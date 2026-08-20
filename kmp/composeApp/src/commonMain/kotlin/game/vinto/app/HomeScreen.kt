package game.vinto.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.RailBorder
import game.vinto.app.theme.RailInk
import game.vinto.app.theme.RailInkDim
import game.vinto.app.theme.feltGradient
import androidx.compose.material3.MaterialTheme
import game.vinto.shapes.Difficulty

private val Pad = 24.dp
private val Gap = 12.dp
private val ColumnMax = 420.dp
private val ChipCorner = 5.dp

/**
 * Where a game starts.
 *
 * On the felt, not on a page. The home screen is the first thing anybody sees, and a Material
 * settings screen in front of a card table tells them what kind of thing they have opened
 * before they have seen the table.
 *
 * Only single player, deliberately: it is the mode that needs no server, no account and no
 * second person, and it is finished. Rooms exist and are gated, but nothing here opens one —
 * a button that sometimes reaches a server would undo the property `NoNetworkGuardTest`
 * exists to protect.
 */
@Composable
fun HomeScreen(
    difficulty: Difficulty,
    canContinue: Boolean,
    onDifficulty: (Difficulty) -> Unit,
    onContinue: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(Pad).widthIn(max = ColumnMax),
            verticalArrangement = Arrangement.spacedBy(Gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "VINTO",
                fontSize = TitleSize,
                fontWeight = FontWeight.Black,
                letterSpacing = TitleTracking,
                color = RailInk,
            )
            Text(
                "Lowest hand wins. You are playing three bots.",
                fontSize = BodySize,
                color = RailInkDim,
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Gap),
                modifier = Modifier.padding(vertical = Gap),
            ) {
                Difficulty.entries.forEach { level ->
                    DifficultyChip(
                        label = level.serialName.replaceFirstChar { it.uppercase() },
                        selected = level == difficulty,
                        onClick = { onDifficulty(level) },
                    )
                }
            }

            // Continuing comes first when there is something to continue. A game left
            // half-played is the reason the app was opened; starting a new one over the top of
            // it is the rarer intent and the destructive one.
            if (canContinue) {
                GameButton(
                    label = "Continue",
                    tone = ButtonTone.PLAY,
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
                GameButton(
                    label = "New game",
                    tone = ButtonTone.NEUTRAL,
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GameButton(
                    label = "Play",
                    tone = ButtonTone.PLAY,
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DifficultyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ChipCorner),
        color = if (selected) RailBorder else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) RailInk else RailInkDim,
        border = BorderStroke(1.dp, RailBorder),
    ) {
        Text(
            text = label,
            fontSize = BodySize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private val TitleSize = 46.sp
private val TitleTracking = 6.sp
private val BodySize = 15.sp
