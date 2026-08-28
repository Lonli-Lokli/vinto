package game.vinto.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.lobby_add_bot
import game.vinto.app.art.lobby_connecting
import game.vinto.app.art.lobby_counting_down
import game.vinto.app.art.lobby_fill_seats
import game.vinto.app.art.lobby_leave
import game.vinto.app.art.lobby_needs_human
import game.vinto.app.art.lobby_over
import game.vinto.app.art.lobby_remove_bot
import game.vinto.app.art.lobby_seat_open
import game.vinto.app.art.lobby_seat_you
import game.vinto.app.art.lobby_title
import game.vinto.app.art.net_closed
import game.vinto.app.art.net_connected
import game.vinto.app.art.net_connecting
import game.vinto.app.art.net_reconnecting
import game.vinto.app.art.online_session_over
import game.vinto.app.art.table_see_score
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.client.ConnectionState
import game.vinto.client.LobbyWord
import game.vinto.client.Pace
import game.vinto.client.RemoteGameSession
import game.vinto.client.RemoteRoom
import game.vinto.client.RoundResult
import game.vinto.client.lobbyUi
import game.vinto.client.roundPoints
import game.vinto.shapes.GamePhase
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * One room, hosted: the lobby until the deal, then the same table every local game uses.
 *
 * The switch is the session appearing on [RemoteRoom.session] — the "new round, new session"
 * shape — and everything below it is reuse: `GameHolder`, `CardStage`, `TableScreen` cannot
 * tell this game from a local one, which is design R1 landing where it was aimed.
 */
@Composable
fun RoomScreen(room: RemoteRoom, pace: Pace, onLeft: () -> Unit) {
    val session by room.session.collectAsState()
    val ended by room.ended.collectAsState()

    when (val playing = session) {
        null -> LobbyScreen(room, onLeft)
        else -> RemoteGameScreen(room, playing, pace, endedReason = ended, onLeft = onLeft)
    }
}

/**
 * Who is in, what everybody is waiting for, and the two things a seat may do about it. All
 * decisions live in `lobbyUi` (pure, tested); this draws what it is told.
 */
@Composable
private fun LobbyScreen(room: RemoteRoom, onLeft: () -> Unit) {
    val lobby by room.lobby.collectAsState()
    val connection by room.connection.collectAsState()
    val mySeat by room.seat.collectAsState()
    val ui = lobbyUi(lobby, connection, mySeat)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = LobbyMax)
                .verticalScroll(rememberScrollState())
                .padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.lobby_title, room.code),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onFelt(),
                )
                ConnectionBadge(connection)
            }

            ui.seats.forEach { seat ->
                Surface(shape = MaterialTheme.shapes.medium, color = Rail.fill) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Gap),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = when {
                                !seat.occupied -> stringResource(Res.string.lobby_seat_open)
                                seat.isMine ->
                                    stringResource(Res.string.lobby_seat_you, seat.nickname.orEmpty())

                                else -> seat.nickname.orEmpty()
                            },
                            color = if (seat.occupied) Rail.ink else Rail.inkDim,
                        )
                        if (seat.removable) {
                            GameButton(
                                label = stringResource(Res.string.lobby_remove_bot, seat.index + 1),
                                tone = ButtonTone.NEUTRAL,
                                onClick = { room.removeBot(seat.index) },
                                compact = true,
                            )
                        }
                    }
                }
            }

            LobbyLine(ui.word, ui.msUntilStart)

            if (ui.canAddBot) {
                GameButton(
                    label = stringResource(Res.string.lobby_add_bot),
                    tone = ButtonTone.PLAY,
                    onClick = room::addBot,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GameButton(
                label = stringResource(Res.string.lobby_leave),
                tone = ButtonTone.NEUTRAL,
                onClick = {
                    room.leave()
                    onLeft()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The one sentence under the seats, ticking locally while a countdown runs. */
@Composable
private fun LobbyLine(word: LobbyWord, msUntilStart: Double?) {
    // The server says how long once, on the transition; the seconds tick here. A rebuild
    // mid-countdown restarts from whatever the latest lobby broadcast reported.
    var seconds by remember(msUntilStart) {
        mutableStateOf(msUntilStart?.let { (it / MS_PER_SECOND).toInt() })
    }
    LaunchedEffect(msUntilStart) {
        while ((seconds ?: 0) > 0) {
            delay(TICK_MS)
            seconds = (seconds ?: 1) - 1
        }
    }

    val line = when (word) {
        LobbyWord.CONNECTING -> stringResource(Res.string.lobby_connecting)
        LobbyWord.NEEDS_ANOTHER_HUMAN -> stringResource(Res.string.lobby_needs_human)
        LobbyWord.FILL_THE_SEATS -> stringResource(Res.string.lobby_fill_seats)
        LobbyWord.COUNTING_DOWN -> stringResource(Res.string.lobby_counting_down, seconds ?: 0)
        LobbyWord.OVER -> stringResource(Res.string.lobby_over)
    }
    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onFelt())
}

/** The connection, as a dot and a word — beside the lobby title, and over a remote table. */
@Composable
fun ConnectionBadge(connection: ConnectionState) {
    val (label, colour) = when (connection) {
        is ConnectionState.Connected -> stringResource(Res.string.net_connected) to LiveGreen
        is ConnectionState.Connecting -> stringResource(Res.string.net_connecting) to WaitAmber
        is ConnectionState.Reconnecting -> stringResource(Res.string.net_reconnecting) to WaitAmber
        is ConnectionState.Closed -> stringResource(Res.string.net_closed) to DeadRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DotGap),
    ) {
        Surface(shape = CircleShape, color = colour, modifier = Modifier.size(Dot)) {}
        Text(label, style = MaterialTheme.typography.labelMedium, color = Rail.inkDim)
    }
}

/**
 * The remote table: the local game's exact screens over the remote session. What is
 * different is only what genuinely differs — the score sheet reads the room's standings,
 * the header shows the connection, and "deal the next round" is an agreement rather than an
 * act.
 */
@Composable
private fun RemoteGameScreen(
    room: RemoteRoom,
    session: RemoteGameSession,
    pace: Pace,
    endedReason: String?,
    onLeft: () -> Unit,
) {
    val holder = rememberHolder(session)
    val act = rememberActor(holder)
    val log by session.log.collectAsState()
    val standings by room.standings.collectAsState()
    val connection by room.connection.collectAsState()

    var helpOpen by remember { mutableStateOf(false) }
    var scoreOpen by remember(session) { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = TableLayout.forScreen(maxWidth, maxHeight)

        CardStage(
            frames = session.frames,
            live = holder.current,
            sizes = layout.sizes,
            pace = pace.scale,
        ) { shown ->
            Column(modifier = Modifier.fillMaxSize()) {
                TableScreen(
                    state = TableState(
                        view = shown,
                        table = holder.tableFor(shown),
                        refusal = holder.refusal,
                        recent = log,
                        round = standings.size + 1,
                    ),
                    layout = layout,
                    onMove = act,
                    onHelp = { helpOpen = true },
                    onReport = {},
                    onDeck = {},
                    modifier = Modifier.weight(1f),
                )

                BelowTheFelt(
                    connection = connection,
                    over = shown.phase == GamePhase.SCORING,
                    onSee = { scoreOpen = true },
                )
            }
        }
    }

    if (helpOpen) {
        HelpSheet(now = holder.table.help, onDismiss = { helpOpen = false })
    }

    endedReason?.let {
        Surface(modifier = Modifier.fillMaxWidth(), color = Rail.fill) {
            Text(
                text = stringResource(Res.string.online_session_over, it),
                modifier = Modifier.padding(Gap),
                color = Rail.inkDim,
            )
        }
    }

    // The just-finished round, from public facts: the wire delivered the scoring view; what
    // it paid is derived by the tested rule in `roundPoints`. The room's own standings feed
    // the cumulative column, minus this round if it has already been filed there.
    if (scoreOpen) {
        val view = holder.current
        val scores = view.scores.orEmpty()
        val filed = standings.lastOrNull()?.scores == scores
        val earlier = if (filed) standings.dropLast(1) else standings
        StandingsSheet(
            round = earlier.size + 1,
            you = session.playerId,
            result = RoundResult(
                callerId = view.vintoCallerId,
                hands = scores,
                points = roundPoints(scores, view.vintoCallerId),
                seats = view.players.map { it.id to it.nickname },
            ),
            standings = earlier
                .flatMap { it.points.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, values) -> values.sum() },
            onNextRound = {
                scoreOpen = false
                room.nextRound()
            },
            onQuit = {
                scoreOpen = false
                room.leave()
                onLeft()
            },
        )
    }
}

/** The strips under a remote table: the connection when it wavers, the score when it ends. */
@Composable
private fun BelowTheFelt(connection: ConnectionState, over: Boolean, onSee: () -> Unit) {
    if (connection !is ConnectionState.Connected) {
        Surface(modifier = Modifier.fillMaxWidth(), color = Rail.fill) {
            Box(modifier = Modifier.padding(Gap), contentAlignment = Alignment.Center) {
                ConnectionBadge(connection)
            }
        }
    }

    if (over) {
        Surface(modifier = Modifier.fillMaxWidth(), color = Rail.fill) {
            Column(
                modifier = Modifier.padding(Gap).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GameButton(
                    label = stringResource(Res.string.table_see_score),
                    tone = ButtonTone.PLAY,
                    onClick = onSee,
                    modifier = Modifier.widthIn(max = StripMax).fillMaxWidth(),
                )
            }
        }
    }
}

private val Pad = 24.dp
private val Gap = 10.dp
private val LobbyMax = 420.dp
private val StripMax = 420.dp
private val Dot = 10.dp
private val DotGap = 6.dp
private val LiveGreen = Color(0xFF43A047)
private val WaitAmber = Color(0xFFF9A825)
private val DeadRed = Color(0xFFE53935)
private const val MS_PER_SECOND = 1_000.0
private const val TICK_MS = 1_000L
