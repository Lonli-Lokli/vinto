package game.vinto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.discover_dealing
import game.vinto.app.art.discover_failed
import game.vinto.app.art.discover_full
import game.vinto.app.art.discover_join
import game.vinto.app.art.discover_looking
import game.vinto.app.art.discover_people
import game.vinto.app.art.discover_quiet
import game.vinto.app.art.discover_refresh
import game.vinto.app.art.discover_retry
import game.vinto.app.art.discover_room_host
import game.vinto.app.art.discover_room_summary
import game.vinto.app.art.discover_room_unnamed
import game.vinto.app.art.discover_seats
import game.vinto.app.art.discover_title
import game.vinto.app.art.settings_back
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.InlineSize
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoSpinner
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.client.DiscoveryRow
import game.vinto.client.DiscoveryState
import game.vinto.client.RoomConnector
import game.vinto.client.discoveryRows
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The public rooms, for somebody who has no code.
 *
 * Four states and a spinner for two of them, which is the whole design. A first load has
 * nothing to show and gets the middle of the screen; a refresh keeps the list and puts a small
 * one beside the title, because taking a list away from somebody who is reading it to tell them
 * it is being re-read is the rudest thing a lobby can do.
 *
 * A private room never appears here — the service does not list it — so this screen is a way to
 * find a table, never a way to enumerate who is playing.
 */
@Composable
fun DiscoverScreen(
    connector: RoomConnector,
    onJoin: (code: String) -> Unit,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(DiscoveryState(loading = true)) }
    val scope = rememberCoroutineScope()

    fun load(first: Boolean) {
        state = state.copy(loading = first, refreshing = !first, failure = null)
        scope.launch {
            state = try {
                val rooms = connector.listPublicRooms()
                DiscoveryState(rows = discoveryRows(rooms))
            } catch (@Suppress("TooGenericExceptionCaught") refused: Exception) {
                // Four platforms, four exception types, one thing to say about all of them.
                DiscoveryState(rows = state.rows, failure = refused.message ?: "no answer")
            }
        }
    }

    LaunchedEffect(Unit) { load(first = true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ColumnMax)
                .padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.discover_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onFelt(),
                )
                if (state.refreshing) {
                    VintoSpinner(
                        size = InlineSize,
                        description = stringResource(Res.string.discover_looking),
                    )
                }
            }

            DiscoverBody(state, onJoin) { load(first = state.rows.isEmpty()) }

            GameButton(
                label = stringResource(Res.string.discover_refresh),
                tone = ButtonTone.NEUTRAL,
                onClick = { load(first = false) },
                busy = state.refreshing || state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(
                label = stringResource(Res.string.settings_back),
                tone = ButtonTone.NEUTRAL,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The four things this screen can be: still asking, unable to ask, asked and empty, or a list.
 *
 * Split out because a screen that draws four states in one function is a function nobody reads
 * — and because these four are the whole design, so they are worth being able to see at once.
 */
@Composable
private fun ColumnScope.DiscoverBody(
    state: DiscoveryState,
    onJoin: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.loading -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = Empty),
            contentAlignment = Alignment.Center,
        ) {
            // The first load owns the middle of the screen: there is nothing else on
            // it yet, and a small spinner in a corner of an empty page reads as a
            // page that failed rather than one that is working.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                VintoSpinner(description = stringResource(Res.string.discover_looking))
                Text(
                    text = stringResource(Res.string.discover_looking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onFelt(),
                )
            }
        }

        state.failure != null -> Column(
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                text = stringResource(Res.string.discover_failed, state.failure.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            GameButton(
                label = stringResource(Res.string.discover_retry),
                tone = ButtonTone.PLAY,
                onClick = onRetry,
                busy = state.refreshing,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.quiet -> Text(
            text = stringResource(Res.string.discover_quiet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onFelt(),
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            items(state.rows, key = { it.code }) { row -> RoomRow(row, onJoin) }
        }
    }
}

/** One table: who opened it, how full it is, and whether it can be sat at. */
@Composable
private fun RoomRow(row: DiscoveryRow, onJoin: (String) -> Unit) {
    // One description for the whole row: a screen reader that reads a code, then a count,
    // then a button, makes the listener assemble the table themselves.
    val spoken = stringResource(Res.string.discover_room_summary, row.code, row.seatsFilled)

    Surface(shape = MaterialTheme.shapes.medium, color = Rail.fill) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Gap)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Tight)) {
                Text(
                    text = row.host?.let { stringResource(Res.string.discover_room_host, it) }
                        ?: stringResource(Res.string.discover_room_unnamed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Rail.ink,
                )
                Text(
                    // Monospaced, because a code is read a character at a time and O/0 and
                    // I/1 are the two mistakes the alphabet already went out of its way to
                    // make impossible.
                    text = row.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Rail.ink,
                )
                Text(
                    text = when {
                        row.startsInSeconds != null ->
                            stringResource(Res.string.discover_dealing, row.startsInSeconds)

                        row.humans > 0 -> stringResource(Res.string.discover_people, row.humans)
                        else -> stringResource(Res.string.discover_seats, row.seatsFilled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Rail.inkDim,
                )
            }

            if (row.joinable) {
                GameButton(
                    label = stringResource(Res.string.discover_join),
                    tone = ButtonTone.PLAY,
                    onClick = { onJoin(row.code) },
                    compact = true,
                )
            } else {
                Text(
                    text = stringResource(Res.string.discover_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = Rail.inkDim,
                )
            }
        }
    }
}

private val Pad = 24.dp
private val Gap = 12.dp
private val Tight = 2.dp
private val Empty = 48.dp
private val ColumnMax = 480.dp
