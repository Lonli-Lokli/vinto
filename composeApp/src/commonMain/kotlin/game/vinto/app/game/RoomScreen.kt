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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.CountConnectionTrouble
import game.vinto.app.CountRefusals
import game.vinto.app.art.Res
import game.vinto.app.art.invite_body
import game.vinto.app.art.invite_copied
import game.vinto.app.art.invite_copy
import game.vinto.app.art.invite_read_it_out
import game.vinto.app.art.invite_share
import game.vinto.app.art.invite_subject
import game.vinto.app.art.invite_title
import game.vinto.app.art.lobby_add_bot
import game.vinto.app.art.lobby_connecting
import game.vinto.app.art.lobby_counting_down
import game.vinto.app.art.lobby_fill_seats
import game.vinto.app.art.lobby_leave
import game.vinto.app.art.lobby_needs_human
import game.vinto.app.art.lobby_offered_bots
import game.vinto.app.art.lobby_over
import game.vinto.app.art.lobby_remove_bot
import game.vinto.app.art.lobby_retry
import game.vinto.app.art.lobby_seat_open
import game.vinto.app.art.lobby_seat_working
import game.vinto.app.art.lobby_seat_you
import game.vinto.app.art.lobby_title
import game.vinto.app.art.lobby_unreachable
import game.vinto.app.art.net_closed
import game.vinto.app.art.net_connected
import game.vinto.app.art.net_connecting
import game.vinto.app.art.net_reconnecting
import game.vinto.app.art.online_session_over
import game.vinto.app.art.table_next_round_waiting
import game.vinto.app.art.table_see_score
import game.vinto.app.link.inviteLink
import game.vinto.app.shareText
import game.vinto.app.theme.BusyLine
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.SeatSize
import game.vinto.app.theme.VintoSpinner
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.client.ConnectionState
import game.vinto.client.LobbySeatUi
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
fun RoomScreen(room: RemoteRoom, pace: Pace, onSettings: () -> Unit, onLeft: () -> Unit) {
    val session by room.session.collectAsState()
    val ended by room.ended.collectAsState()

    when (val playing = session) {
        null -> LobbyScreen(room, onLeft)
        else -> RemoteGameScreen(
            room,
            playing,
            pace,
            endedReason = ended,
            onSettings = onSettings,
            onLeft = onLeft,
        )
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
    CountConnectionTrouble(connection)
    val mySeat by room.seat.collectAsState()
    val pending by room.pendingSeats.collectAsState()
    val notice = rememberLatestNotice(room)
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

            if (ui.seats.isEmpty() && !ui.canRetry) {
                WaitingSeats()
            }

            ui.seats.forEach { seat ->
                SeatRow(seat, changing = seat.index in pending) { room.removeBot(seat.index) }
            }

            LobbyLine(ui.word, ui.msUntilStart)
            Notice(notice)

            // A room that was never reached is the one failure worth offering an answer to.
            // Giving up with no way back would be a worse screen than the spinner that used to
            // sit here for ever: at least that one was still trying.
            if (ui.canRetry) {
                GameButton(
                    label = stringResource(Res.string.lobby_retry),
                    tone = ButtonTone.PLAY,
                    onClick = room::retry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (ui.canAddBot) {
                GameButton(
                    label = stringResource(Res.string.lobby_add_bot),
                    tone = ButtonTone.PLAY,
                    onClick = room::addBot,
                    // Busy while any seat is mid-change: the room fills the first free seat,
                    // so two quick taps are two bots, and the second was asked for by
                    // somebody who had no way of knowing the first had landed.
                    busy = pending.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            InviteRow(room.code)
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

/**
 * One seat: who is in it, and whichever of the two things it currently has to say.
 *
 * The spinner sits on the seat that is changing rather than across the screen, because the
 * wait belongs to this row — somebody who tapped "remove" wants to see *that* seat thinking
 * about it, not the whole lobby greying out.
 */
@Composable
internal fun SeatRow(seat: LobbySeatUi, changing: Boolean, onRemove: () -> Unit) {
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
            when {
                changing -> VintoSpinner(
                    size = SeatSize,
                    colour = Rail.inkDim,
                    description = stringResource(Res.string.lobby_seat_working),
                )

                seat.removable -> GameButton(
                    label = stringResource(Res.string.lobby_remove_bot, seat.index + 1),
                    tone = ButtonTone.NEUTRAL,
                    onClick = onRemove,
                    compact = true,
                )
            }
        }
    }
}

/**
 * The four seats, before the room has said who is in them.
 *
 * Two faults in one, both reported from a phone. There was a spinner here, and the badge
 * beside the title was already spinning — so a single wait was announced twice, and a player
 * looking at two turning rings has to work out whether they are two things or one. The badge
 * keeps it: the connection is what is being waited for, and the badge is where a connection
 * lives all game.
 *
 * And this column is CENTRED, so its height is the screen's layout. A 40 dp box standing in
 * for 190 dp of seats meant that at the moment the room answered, the title, the invitation
 * and the leave button all jumped a couple of hundred pixels — the one instant the player is
 * reading the screen hardest. Four placeholders, sized by the same type in the same padding
 * as the rows that replace them, so nothing moves when the answer arrives.
 *
 * Four is not a guess: a Vinto room deals exactly four seats (`RoomCore.SEAT_COUNT`), and at
 * this moment none of them can carry a remove button yet, so the rows they become are the
 * plain text-only kind these match.
 */
@Composable
private fun WaitingSeats() {
    // One description for the group. Four silent rows would otherwise be four blanks to
    // read out, and what a listener needs is the sentence the badge is showing.
    val spoken = stringResource(Res.string.lobby_connecting)
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        repeat(SEATS) { WaitingSeat() }
    }
}

@Composable
internal fun WaitingSeat() {
    Surface(shape = MaterialTheme.shapes.medium, color = Rail.fill) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The bar wraps a blank line of the row's own type rather than taking a height
            // in dp. That is what makes this exactly as tall as the `SeatRow` it stands in
            // for — a hard-coded height is a guess that drifts the next time the type does.
            Box(
                modifier = Modifier
                    .fillMaxWidth(WaitingBar)
                    .background(Rail.inkDim.copy(alpha = Ghost), MaterialTheme.shapes.small),
            ) {
                Text(" ")
            }
        }
    }
}

/**
 * The code, and the two ways to get it to somebody else.
 *
 * A room code is useless in the room — it is only ever wanted by the person *not* in it — so
 * this is a share sheet first and a clipboard second, in that order, because the difference
 * between them is whether the player has to go and find somewhere to paste. On a platform
 * with no share sheet the first button quietly becomes the second, rather than doing nothing
 * a player can see.
 *
 * And under both, the code itself, in a typeface built for reading a character at a time —
 * because the oldest way of passing six characters to somebody in the same room is to say
 * them out loud, and it needs no platform at all.
 */
@Composable
private fun InviteRow(code: String) {
    var copied by remember { mutableStateOf(false) }
    val subject = stringResource(Res.string.invite_subject)
    val body = stringResource(Res.string.invite_body, inviteLink(code), code)

    // `LocalClipboardManager` is deprecated in favour of `LocalClipboard`, and the
    // replacement is still not usable from common code in Compose Multiplatform 1.12:
    // `Clipboard.setClipEntry` takes a `ClipEntry`, and the only way to build one from a
    // string is `ClipEntry.withPlainText`, which is declared **per platform** rather than in
    // commonMain. Checked rather than assumed — a one-line probe calling it from commonMain
    // compiles for wasmJs and fails for the JVM with "Unresolved reference". So migrating
    // still means an `expect`/`actual` per platform, which is the four hand-written
    // implementations `Share.kt` deliberately does not have, "for a job the framework has
    // already done".
    //
    // Suppressed rather than left warning, because a warning nobody can act on is noise that
    // hides the ones somebody can. Delete the suppression the day `withPlainText` reaches
    // commonMain; the probe above is how to check.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    fun copy() {
        clipboard.setText(AnnotatedString(body))
        copied = true
    }

    Surface(shape = MaterialTheme.shapes.medium, color = Rail.fill) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                text = stringResource(Res.string.invite_title),
                style = MaterialTheme.typography.labelMedium,
                color = Rail.inkDim,
            )
            Text(
                // Monospaced and spaced out: this is the one string in the app somebody
                // reads aloud down a telephone.
                text = code,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = CodeTracking,
                color = Rail.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                GameButton(
                    label = stringResource(Res.string.invite_share),
                    tone = ButtonTone.KEEP,
                    onClick = { if (!shareText(subject, body)) copy() },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                GameButton(
                    label = if (copied) {
                        stringResource(Res.string.invite_copied)
                    } else {
                        stringResource(Res.string.invite_copy)
                    },
                    tone = ButtonTone.NEUTRAL,
                    onClick = { copy() },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(Res.string.invite_read_it_out),
                style = MaterialTheme.typography.bodySmall,
                color = Rail.inkDim,
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
        LobbyWord.OFFERED_BOTS -> stringResource(Res.string.lobby_offered_bots, seconds ?: 0)
        LobbyWord.OVER -> stringResource(Res.string.lobby_over)
        LobbyWord.UNREACHABLE -> stringResource(Res.string.lobby_unreachable)
    }
    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onFelt())
}

/** The room's closing line, when the session has finished but the screen is still here. */
@Composable
private fun SessionOver(reason: String?) {
    if (reason == null) return
    Surface(modifier = Modifier.fillMaxWidth(), color = Rail.fill) {
        Text(
            text = stringResource(Res.string.online_session_over, reason),
            modifier = Modifier.padding(Gap),
            color = Rail.inkDim,
        )
    }
}

/**
 * The last thing the room said no to, for a few seconds.
 *
 * `RemoteRoom.notices` carries every refusal that belongs to no dispatch — a bot the room
 * would not add, a next round it would not start — and **nothing read it**. So a refused
 * lobby op showed a spinner on the seat, which timed out after five seconds, and then nothing
 * at all: the tap looked like it had worked and then quietly had not. That is the worst answer
 * of the three available, because the player's next move is to try it again.
 *
 * It fades rather than accumulating, because a refusal is about the moment it happened and a
 * list of them is a log nobody asked for.
 */
@Composable
private fun rememberLatestNotice(room: RemoteRoom): String? {
    var notice by remember(room) { mutableStateOf<String?>(null) }
    LaunchedEffect(room) {
        room.notices.collect { said ->
            notice = said
            delay(NOTICE_MS)
            // Only if nothing newer arrived meanwhile: the next collection overwrites this,
            // and clearing unconditionally would blank a message that is a moment old.
            if (notice == said) notice = null
        }
    }
    return notice
}

/** One refusal, said where the thing that was refused is. */
@Composable
private fun Notice(said: String?) {
    if (said == null) return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Rail.fill,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = said,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(Gap),
        )
    }
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
    val waiting = connection is ConnectionState.Connecting || connection is ConnectionState.Reconnecting

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DotGap),
    ) {
        // A dot for a settled connection, a spinner for one that is still trying. The amber
        // dot alone said "something is wrong here"; a turning one says "wait", which is the
        // true thing and the one worth waiting through.
        if (waiting) {
            VintoSpinner(size = BadgeSpinner, colour = colour, description = label)
        } else {
            Surface(shape = CircleShape, color = colour, modifier = Modifier.size(Dot)) {}
        }
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
    onSettings: () -> Unit,
    onLeft: () -> Unit,
) {
    val holder = rememberHolder(session)
    val act = rememberActor(holder)
    val log by session.log.collectAsState()
    val standings by room.standings.collectAsState()
    val connection by room.connection.collectAsState()
    val notice = rememberLatestNotice(room)
    CountConnectionTrouble(connection)
    CountRefusals(holder.refusal)

    var helpOpen by remember { mutableStateOf(false) }
    var scoreOpen by remember(session) { mutableStateOf(false) }

    // Agreeing to the next round is an ask, not an act: the room deals when *every* seat has
    // agreed. Keyed on the session, so the new round's arrival is what clears it — which is
    // exactly the event being waited for, and needs no second message to say so.
    var agreed by remember(session) { mutableStateOf(false) }

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
                        sending = holder.sending,
                        recent = log,
                        round = standings.size + 1,
                    ),
                    layout = layout,
                    onMove = act,
                    onHelp = { helpOpen = true },
                    onSettings = onSettings,
                    onReport = {},
                    onDeck = {},
                    modifier = Modifier.weight(1f),
                )

                // A refused lobby op — a next round the room would not start — belongs where
                // the tap was, not in a log. `agreed` above would otherwise sit true for ever
                // waiting for a deal that was declined.
                Notice(notice)

                BelowTheFelt(
                    connection = connection,
                    over = shown.phase == GamePhase.SCORING,
                    agreed = agreed,
                    onSee = { scoreOpen = true },
                )
            }
        }
    }

    HelpSheet(open = helpOpen, now = holder.table.help, onDismiss = { helpOpen = false })

    SessionOver(endedReason)

    // The just-finished round, from public facts: the wire delivered the scoring view; what
    // it paid is derived by the tested rule in `roundPoints`. The room's own standings feed
    // the cumulative column, minus this round if it has already been filed there.
    if (scoreOpen) {
        val view = holder.current
        val scores = view.scores.orEmpty()
        val filed = standings.lastOrNull()?.scores == scores
        val earlier = if (filed) standings.dropLast(1) else standings
        StandingsSheet(
            // Composed only once there is a result to show, so it is showing whenever it
            // exists. `VintoSheet` animates from first composition, so this still rises.
            open = true,
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
                agreed = true
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
private fun BelowTheFelt(
    connection: ConnectionState,
    over: Boolean,
    agreed: Boolean,
    onSee: () -> Unit,
) {
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
                // Once this seat has agreed there is nothing left for it to do, and the
                // button would only invite it to agree again. What it is waiting for is the
                // other three, so that is what it says.
                if (agreed) {
                    BusyLine(
                        label = stringResource(Res.string.table_next_round_waiting),
                        colour = Rail.inkDim,
                    )
                } else {
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
}

private val Pad = 24.dp
private val Gap = 10.dp
private val LobbyMax = 420.dp
private val StripMax = 420.dp

/** A Vinto room is always four seats — see `RoomCore.SEAT_COUNT`, which is a design
 * constant rather than a setting. */
private const val SEATS = 4

/** How much of a waiting row the placeholder bar fills, and how faint it is. */
private const val WaitingBar = 0.45f
private const val Ghost = 0.25f

private val Dot = 10.dp
private val DotGap = 6.dp

/** Matched to the dot it replaces, so the badge does not jump when the connection settles. */
private val BadgeSpinner = 12.dp

/** A room code is read a character at a time; the tracking is what makes that possible. */
private val CodeTracking = 4.sp

/**
 * Where a shared invitation points.
 *
 * The Compose web client, which is the one place a link can seat somebody without an install.
 * Android and iOS deep links want an app-links manifest and an associated-domains file, and
 * until those exist a phone that follows this opens the web client — which is a working
 * outcome rather than a broken one. The code in the message is what makes the invitation
 * work regardless: it can be read out, and typed into any of the four clients.
 */
private val LiveGreen = Color(0xFF43A047)
private val WaitAmber = Color(0xFFF9A825)
private val DeadRed = Color(0xFFE53935)

/** Long enough to read one line, short enough that it is plainly about what just happened. */
private const val NOTICE_MS = 4_000L

private const val MS_PER_SECOND = 1_000.0
private const val TICK_MS = 1_000L
