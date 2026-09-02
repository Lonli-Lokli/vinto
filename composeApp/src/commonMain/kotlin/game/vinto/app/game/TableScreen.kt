package game.vinto.app.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.app_name
import game.vinto.app.art.card_discarded
import game.vinto.app.art.card_discarded_live
import game.vinto.app.art.card_in_hand
import game.vinto.app.art.card_position
import game.vinto.app.art.card_thrown_by
import game.vinto.app.art.header_deck_badge
import game.vinto.app.art.header_deck_left
import game.vinto.app.art.header_report
import game.vinto.app.art.header_settings
import game.vinto.app.art.table_discard
import game.vinto.app.art.table_draw
import game.vinto.app.art.table_final_ally
import game.vinto.app.art.table_final_caller
import game.vinto.app.art.table_final_choosing
import game.vinto.app.art.table_final_last_turn
import game.vinto.app.art.table_final_leader
import game.vinto.app.art.table_final_leads
import game.vinto.app.art.table_final_round
import game.vinto.app.art.table_final_side_caller
import game.vinto.app.art.table_final_side_coalition
import game.vinto.app.art.table_final_turns_left
import game.vinto.app.art.table_final_versus
import game.vinto.app.art.table_leads_mark
import game.vinto.app.art.table_round_turn
import game.vinto.app.art.table_toss_in
import game.vinto.app.art.table_toss_in_summary
import game.vinto.app.art.table_toss_in_timed
import game.vinto.app.art.table_tossed
import game.vinto.app.art.table_vinto_mark
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Slate
import game.vinto.app.theme.Wordmark
import game.vinto.app.theme.contactShadow
import game.vinto.app.theme.feltEdge
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.feltLamp
import game.vinto.app.theme.feltShade
import game.vinto.app.theme.onFelt
import game.vinto.app.theme.rememberFeltWeave
import game.vinto.client.Anchor
import game.vinto.client.CardRef
import game.vinto.client.Move
import game.vinto.client.Say
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.client.finalRoundTurnsLeft
import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.engine.cardInPlay
import game.vinto.engine.mySeat
import game.vinto.engine.turnHolderId
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.actionIsLive
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Gap = 6.dp
private val Tight = 4.dp
private val Edge = 6.dp
private val FeltCorner = 14.dp
private val Rim = 2.dp

/** How a contact shadow sits under the thing that casts it: low, and flattened. */
private const val SHADOW_DROP = 0.72f
private const val SHADOW_SQUASH = 0.38f

/** Every control in the header is a thumb wide, whatever is drawn inside it. */
private val HeaderTap = 44.dp
private val WordmarkSize = 19.sp

/**
 * The table, laid out as the web app lays it out on a phone.
 *
 * Each seat is a name plate and a hand, and the plate sits *outboard* of the hand — the top
 * seat's plate to the right of its cards, the side seats' above and below theirs, yours to
 * the left of your own. It looks arbitrary written down and is obvious on the screen: the
 * plates end up around the rim and the cards face inwards, which is how people sit at a table.
 *
 * The alternative, a tidy column of hands, was tried first and is worse for the same reason a
 * list of players is worse than a table — you keep track of an opponent by where they are.
 *
 * The screen's shape decides where the rail stands ([TableLayout]): under the felt in
 * portrait, beside it in landscape. The felt itself is the same four-sided table either way —
 * a player who rotates the phone mid-round finds every seat in the chair it was in, only the
 * controls having moved to their thumb's new resting place.
 */
// Seven of them, and they are seven different questions the screen cannot answer for itself:
// what a move does, and where each of the four header controls leads. Bundling them into one
// `TableActions` bag would satisfy the rule and cost the compiler its ability to say which one
// a caller forgot — which is exactly what it said, usefully, when the gear was added.
@Suppress("LongParameterList")
@Composable
fun TableScreen(
    state: TableState,
    layout: TableLayout,
    onMove: (Move) -> Unit,
    onHelp: (Rank?) -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onDeck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layout.landscape) {
        // Centred, not stretched: the felt column is [TableLayout.feltWidth] wide — the
        // whole remainder on a rotated phone, a capped table on a tablet or desktop — and
        // the rail hugs it, so on a big screen the pair sits together in the middle of the
        // app's dark surround rather than the controls drifting to one horizon and the
        // seats to the other.
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(modifier = Modifier.width(layout.feltWidth)) {
                TableHeader(state.view, state.round, onHelp, onSettings, onReport, onDeck)
                FeltTable(
                    state = state,
                    sizes = layout.sizes,
                    onMove = onMove,
                    onHelp = onHelp,
                    modifier = Modifier.weight(1f).padding(start = Edge, end = Edge, bottom = Edge),
                )
            }

            // The rail, standing at the side. The final-round line sits at its head — in
            // landscape the felt has no height to spare for a banner, and "who plays for
            // whom" is read next to the controls that ask what to do about it anyway.
            Column(modifier = Modifier.width(layout.railWidth).fillMaxHeight()) {
                FinalRoundLine(state.view)
                ControlPanel(
                    state = state,
                    onMove = onMove,
                    side = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            TableHeader(state.view, state.round, onHelp, onSettings, onReport, onDeck)

            FinalRoundLine(state.view)

            FeltTable(
                state = state,
                sizes = layout.sizes,
                onMove = onMove,
                onHelp = onHelp,
                modifier = Modifier.weight(1f).padding(horizontal = Edge),
            )

            ControlPanel(
                state = state,
                onMove = onMove,
                modifier = Modifier.fillMaxWidth().height(layout.railHeight),
            )
        }
    }
}

/** The felt and its four seats — the part of the table that is the same in both shapes. */
@Composable
private fun FeltTable(
    state: TableState,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
    onHelp: (Rank?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = state.view
    val table = state.table

    // Nullable, and the felt is drawn either way.
    //
    // This was `players.first { it.id == view.viewerId }`, which throws — and `tableFor`, one
    // function away, opens with the same lookup as a `firstOrNull` and answers `Ask.Watching`
    // when it comes back empty. So the model handled a view whose viewer has no seat and the
    // felt crashed on it, with nothing between the exception and the launcher. A solo game
    // always seats you, so it could only ever have happened online, which is where nothing
    // catches it.
    //
    // A watcher sees the whole table and no hand of their own: every seat is an opponent, and
    // the bottom of the felt is simply empty. That is a real state — the room decides who is
    // seated — rather than an error to report.
    val mine = view.mySeat
    val opponents = view.players.filter { it.id != mine?.id }

    Felt(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            // Seats are dealt in a fixed order, so the same bot is always in the same
            // chair. The order matches the web table's, which puts the second-dealt
            // opponent across from you and the first down your left.
            TopSeat(opponents.getOrNull(1), view, table, sizes, onMove)

            MiddleRow(
                modifier = Modifier.weight(1f),
                left = opponents.getOrNull(0),
                right = opponents.getOrNull(2),
                view = view,
                table = table,
                sizes = sizes,
                onMove = onMove,
                onHelp = onHelp,
            )

            // Four chairs and four players. Seated, the bottom one is yours; watching, it is
            // whoever would otherwise have nowhere to sit — the felt has exactly four places
            // and a fourth opponent must be in one of them or they vanish from the game.
            val near = mine ?: opponents.getOrNull(NEAR_CHAIR)
            near?.let { NearSeat(it, view, table, sizes, onMove, mine = it.id == mine?.id) }
        }
    }
}

/**
 * Everything the table draws, in one place.
 *
 * Five values that always arrive together and never separately: the game as this seat sees
 * it, what it may do, what it was last told it could not, what has happened lately, and which
 * round of the game this is. Passing them individually is a signature nobody can read and an
 * order nobody can check.
 */
data class TableState(
    val view: PlayerView,
    val table: Table,
    val refusal: String?,
    val recent: List<Say>,
    val round: Int,
    /**
     * Whether a coach is watching over this table, in which case the rule under the prompt
     * is always spelled out. In a real game it fades — see `ControlPanel`.
     */
    val teaching: Boolean = false,
    /**
     * A move on the wire, unanswered — only ever true over a socket.
     *
     * Last in the list, after the other default, so the suites that build this positionally
     * keep compiling: a new parameter in the middle of a five-argument call binds silently to
     * the wrong thing, and this one is a Boolean sitting where a list used to be.
     */
    val sending: Boolean = false,
)

/**
 * One header control: the dressed circle the "?" wears, holding a glyph drawn in the
 * rail's ink rather than fetched from an emoji font nobody chose.
 */
@Composable
private fun HeaderGlyph(
    onClick: () -> Unit,
    description: String,
    glyph: DrawScope.(Color) -> Unit,
) {
    val ink = Rail.inkDim
    Surface(
        onClick = onClick,
        modifier = Modifier.size(HeaderTap).semantics { contentDescription = description },
        shape = CircleShape,
        color = Rail.fill,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rail.edge),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(18.dp)) { glyph(ink) }
        }
    }
}

/** A gear: the ring, eight teeth, and the hub, all strokes. */
private fun DrawScope.drawGear(ink: Color) {
    val c = center
    val r = size.minDimension / 2
    drawCircle(ink, radius = r * GEAR_RING, center = c, style = Stroke(width = r * GEAR_RING_STROKE))
    repeat(GEAR_TEETH) { i ->
        val a = i * (2 * PI.toFloat() / GEAR_TEETH)
        val from = c + Offset(cos(a), sin(a)) * (r * GEAR_TOOTH_ROOT)
        val to = c + Offset(cos(a), sin(a)) * r
        drawLine(ink, from, to, strokeWidth = r * GEAR_TOOTH_STROKE, cap = StrokeCap.Round)
    }
    drawCircle(ink, radius = r * GEAR_HUB, center = c)
}

private const val GEAR_TEETH = 8
private const val GEAR_RING = 0.58f
private const val GEAR_RING_STROKE = 0.28f
private const val GEAR_TOOTH_ROOT = 0.72f
private const val GEAR_TOOTH_STROKE = 0.30f
private const val GEAR_HUB = 0.16f

/** A ladybug: the domed body, the wing split, the head, and four spots. */
private fun DrawScope.drawBug(ink: Color) {
    val c = center
    val r = size.minDimension / 2
    drawCircle(ink, radius = r * BUG_BODY, center = c, style = Stroke(width = r * BUG_SHELL))
    drawLine(
        ink,
        c + Offset(0f, -r * BUG_BODY),
        c + Offset(0f, r * BUG_BODY),
        strokeWidth = r * BUG_SPLIT,
        cap = StrokeCap.Round,
    )
    drawCircle(ink, radius = r * BUG_HEAD, center = c + Offset(0f, -r * BUG_NECK))
    for (dx in listOf(-1f, 1f)) {
        drawCircle(ink, radius = r * BUG_SPOT, center = c + Offset(dx * r * BUG_WING, -r * BUG_HIGH_SPOT))
        drawCircle(ink, radius = r * BUG_SPOT, center = c + Offset(dx * r * BUG_WING, r * BUG_LOW_SPOT))
    }
}

private const val BUG_BODY = 0.78f
private const val BUG_SHELL = 0.18f
private const val BUG_SPLIT = 0.14f
private const val BUG_HEAD = 0.24f
private const val BUG_NECK = 0.98f
private const val BUG_SPOT = 0.14f
private const val BUG_WING = 0.36f
private const val BUG_HIGH_SPOT = 0.22f
private const val BUG_LOW_SPOT = 0.30f

/** Where the round is up to, and how much deck is left. */
@Composable
private fun TableHeader(
    view: PlayerView,
    round: Int,
    onHelp: (Rank?) -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onDeck: () -> Unit,
) {
    val report = stringResource(Res.string.header_report)
    val settings = stringResource(Res.string.header_settings)
    val deck = stringResource(Res.string.header_deck_badge, view.drawPileSize)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Gap),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // On the rail, so the rail's own ink — not the theme's, which is a page colour and
        // reads as dark-on-dark here.
        Text(
            stringResource(Res.string.app_name),
            fontFamily = Wordmark,
            fontSize = WordmarkSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = Rail.brand,
        )
        Text(
            // The *game's* round, not the deal's. The engine counts rounds within one deal
            // — it is a turn counter that wraps — while the player is counting hands played.
            stringResource(Res.string.table_round_turn, round, view.turnNumber),
            style = MaterialTheme.typography.labelLarge,
            color = Rail.inkDim,
        )

        Box(modifier = Modifier.weight(1f))

        // The rules, in the one place on the screen that never moves.
        //
        // It used to sit in the control panel beside the prompt, which meant it slid up and
        // down with whatever the panel was asking — a fourteen-chip King grid one moment, one
        // button the next. A control that is always available and never changes belongs in
        // the header, where nothing else changes either.
        Surface(
            onClick = { onHelp(null) },
            modifier = Modifier.size(HeaderTap).markedAs(LocalStage.current, Target.HELP),
            shape = CircleShape,
            color = Rail.fill,
            border = androidx.compose.foundation.BorderStroke(1.dp, Rail.edge),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Rail.inkDim,
                )
            }
        }

        // The settings, from the table rather than only from the front door.
        //
        // Pace is the setting a player wants to change *while* something is too slow or too
        // fast to sit through, and it was reachable only from the home screen — so changing it
        // meant abandoning the round it was annoying you in, which is a price nobody pays; they
        // put the phone down instead. Theme and haptics are the same shape of want. Going there
        // and coming back returns to this exact table, mid-round, with nothing lost.
        // The same dressed circle as the "?" beside it. These were colour emoji, which
        // made the header three different design languages in a row — an outlined glyph,
        // then whatever the platform's emoji font felt like. Drawn glyphs in the rail's
        // own ink are one decision made once.
        HeaderGlyph(onClick = onSettings, description = settings) { ink ->
            drawGear(ink)
        }

        // Always reachable, because the moment worth reporting is the moment it goes wrong
        // and nobody navigates to a menu to capture it.
        HeaderGlyph(onClick = onReport, description = report) { ink ->
            drawBug(ink)
        }

        // The deck count, which answers when it is asked. It is the one number on the screen
        // that decides how a round ends — when it runs out the pile is shuffled back in and
        // everything anybody remembered about that pile is worthless — and a number nobody
        // explains is a number nobody reads.
        //
        // Named as the control it is rather than as the count it shows: the draw pile on the
        // felt below already reads out "N cards left in the deck", and when this said the same
        // words a screen reader heard one screen say it twice without either saying that one
        // of the two opens an explanation.
        // Dressed exactly as the three controls beside it — one header, one language.
        // It was a gold-on-green plaque, which made the row's fourth control a fourth style.
        Surface(
            onClick = onDeck,
            modifier = Modifier
                .size(HeaderTap)
                .markedAs(LocalStage.current, Target.BADGE)
                .semantics { contentDescription = deck },
            shape = CircleShape,
            color = Rail.fill,
            border = androidx.compose.foundation.BorderStroke(1.dp, Rail.edge),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "${view.drawPileSize}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Rail.inkDim,
                )
            }
        }
    }
}

/**
 * Who is playing for whom, once Vinto has been called.
 *
 * The final round is the one moment the rules change under the player: the other three stop
 * being three opponents and become one hand, and only that hand is compared. The table says
 * so in colour already — blue rings on the coalition, gold on the caller — but a colour is
 * only as good as the player's memory of the legend, and this is the point of the game where
 * they have least attention to spare for remembering it. So it is also said in words, for as
 * long as it is true.
 *
 * Nothing is drawn before the coalition has picked who plays its hand, because until then the
 * sentence has no subject — and the panel is asking that very question.
 */
@Composable
private fun FinalRoundLine(view: PlayerView) {
    if (view.phase == GamePhase.SCORING) return
    val caller = view.players.firstOrNull { it.id == view.vintoCallerId } ?: return
    val leader = view.players.firstOrNull { it.id == view.coalitionLeaderId }

    // Drawn from the call onwards, including the window before a leader is chosen — which is
    // where this used to `return` and show nothing at all. The rules change the moment Vinto
    // is called, not the moment the coalition picks somebody, so a table that says nothing
    // for the first part of the final round is silent exactly when it is most surprising.
    val said = when {
        caller.id == view.viewerId -> stringResource(Res.string.table_final_caller)
        leader == null -> stringResource(Res.string.table_final_choosing, caller.nickname)
        leader.id == view.viewerId ->
            stringResource(Res.string.table_final_leader, caller.nickname)

        else -> stringResource(Res.string.table_final_ally, leader.nickname, caller.nickname)
    }

    Column(modifier = Modifier.fillMaxWidth().background(Rail.fill)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.table_final_round).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Rail.gold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                said,
                style = MaterialTheme.typography.labelMedium,
                color = Rail.ink,
                modifier = Modifier.weight(1f, fill = true),
            )

            // How close the reveal is. Three coalition turns can pass in under a second when
            // the bots hold them all, and a player who looked away for one has no other way to
            // know how much of the final round is left.
            finalRoundTurnsLeft(view)?.let { left ->
                Text(
                    if (left == 1) {
                        stringResource(Res.string.table_final_last_turn)
                    } else {
                        stringResource(Res.string.table_final_turns_left, left)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Rail.gold,
                )
            }
        }

        Sides(view, caller, leader?.id)
    }
}

/**
 * Who is on which side, as faces.
 *
 * Taken from the web client, which draws the final round as two named columns — COALITION
 * against VINTO CALLER — and is plainly better than a sentence at the one moment the game's
 * shape changes. What is *not* taken is its layout: two stacked lists of names is a panel's
 * worth of height, and here this sits above a felt that has four hands to fit on a phone.
 *
 * So it is one line of portraits, which is the same information in a tenth of the room and
 * reads faster besides: three of the four players are bots the person has been watching for
 * ten minutes and knows by face before they know by name. The leader wears a gold ring,
 * because "who plays the hand" is the one thing about the coalition that is not obvious.
 */
@Composable
private fun Sides(view: PlayerView, caller: PlayerSeatView, leaderId: String?) {
    // The caller arrives as a *seat* rather than an id, so there is nothing to look up and
    // nothing to be missing. Looking it up here worked — the only call site found it with a
    // `firstOrNull` first — and "it happens to be safe two frames up" is exactly the reasoning
    // that put a `first {}` on the felt in the first place. `PartialFunctionTest` refuses it.
    val coalition = view.players.filter { it.id != caller.id }
    val leads = leaderId?.let { id -> view.players.firstOrNull { it.id == id }?.nickname }
    val spoken = leads?.let { stringResource(Res.string.table_final_leads, it) }
        ?: stringResource(Res.string.table_final_choosing, caller.nickname)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            // One sentence for the whole row, because four portraits read out one at a time
            // are four names with no relationship between them — and the relationship is the
            // only thing this row is for.
            .semantics {
                contentDescription = spoken
            },
        horizontalArrangement = Arrangement.spacedBy(Tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideLabel(stringResource(Res.string.table_final_side_coalition), Rail.brand)
        coalition.forEach { seat ->
            Face(seat.nickname, ringed = seat.id == leaderId)
        }

        Text(
            stringResource(Res.string.table_final_versus),
            style = MaterialTheme.typography.labelSmall,
            color = Rail.inkDim,
            modifier = Modifier.weight(1f, fill = true).padding(horizontal = Tight),
            textAlign = TextAlign.Center,
        )

        Face(caller.nickname, ringed = true, ring = Slate.gold)
        SideLabel(stringResource(Res.string.table_final_side_caller), Slate.gold)
    }
}

@Composable
private fun SideLabel(text: String, colour: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = colour,
    )
}

/** One player's portrait, at roster size. Named for a screen reader, since it is the label. */
@Composable
private fun Face(name: String, ringed: Boolean, ring: Color = Rail.brand) {
    Image(
        painter = painterResource(portraitFor(name)),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(FaceSize)
            .clip(CircleShape)
            .then(
                if (ringed) {
                    Modifier.border(FaceRing, ring, CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * The chair nearest the player, counted among the opponents.
 *
 * Only reached when the viewer has no seat of their own: three opponents fill the top and the
 * two sides, and the fourth takes the chair the viewer's hand would have used.
 */
private const val NEAR_CHAIR = 3

/** Small enough for a line above the felt, large enough to tell four faces apart. */
private val FaceSize = 22.dp
private val FaceRing = 2.dp

/** The felt: a gradient, a rim, and everything that happens on it. */
@Composable
private fun Felt(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(FeltCorner)
    val weave = rememberFeltWeave()
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(scheme.feltGradient()))
            // Three passes over the same cloth, and together they are the difference between
            // a green rectangle and a lit surface: the lamp above the middle of the table,
            // the shadow the rim throws back onto the felt just inside it, and the rim.
            .drawBehind {
                drawRect(weave)
                drawRect(scheme.feltLamp(size.minDimension))
                drawRect(scheme.feltShade())
            }
            .border(Rim, scheme.feltEdge(), shape),
        content = content,
    )
}

/** Across the table: their hand, then their plate, along the top edge. */
@Composable
private fun TopSeat(
    seat: PlayerSeatView?,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
) {
    if (seat == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hand(seat, view, table, sizes.theirs, onMove, Modifier.weight(1f, fill = false))
        Plate(seat, view, table, sizes, onMove)
    }
}

/** Left hand, piles, right hand — the widest row, and the one that has to fit a phone. */
@Composable
private fun MiddleRow(
    left: PlayerSeatView?,
    right: PlayerSeatView?,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
    onHelp: (Rank?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Centred rather than top-aligned: the middle row takes whatever height the panel leaves,
    // and top-aligning it pools all the spare felt into one gap under the side seats.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideSeat(left, view, table, sizes, plateFirst = true, onMove = onMove)
        Piles(view, sizes, onHelp)
        SideSeat(right, view, table, sizes, plateFirst = false, onMove = onMove)
    }
}

/**
 * A seat down one edge: a column of cards, with the plate at the end nearest the rim — above
 * on the left, below on the right, so neither plate lands in the middle of the felt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideSeat(
    seat: PlayerSeatView?,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    plateFirst: Boolean,
    onMove: (Move) -> Unit,
) {
    if (seat == null) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        if (plateFirst) Plate(seat, view, table, sizes, onMove)

        // A quarter turn, the way cards lie in front of somebody sitting at the side of a
        // table. It is not only decoration: turned, a card is wider than it is tall, so five
        // of them stack down the edge in the height a phone actually has, and the seat reads
        // as facing inwards rather than as a second copy of your own hand.
        HandLine(vertical = true, modifier = Modifier.weight(1f, fill = false)) {
            Cards(seat, view, table, sizes.side, onMove, turned = true)
        }

        if (!plateFirst) Plate(seat, view, table, sizes, onMove)
    }
}

@Composable
private fun NearSeat(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
    /**
     * Whether this is the viewer's own hand.
     *
     * Your own is drawn a third larger, because it is the one that is *read* rather than
     * counted and the one every tap lands on. A watcher has no such hand, and the fourth
     * player sits in the chair it would have used — at everybody else's size, since to a
     * watcher every seat is somebody else's.
     */
    mine: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Plate(seat, view, table, sizes, onMove)
            Hand(
                seat,
                view,
                table,
                if (mine) sizes.mine else sizes.theirs,
                onMove,
                Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * A hand laid along the bottom or top edge: one line of cards while they fit, two when they
 * do not.
 *
 * Five is the deal and not the limit — a wrong guess, a wrong toss-in and an Ace each add one,
 * and only the end of the round takes any away — so eight cards in front of a player is an
 * ordinary way to be losing. Eight would not fit, and what the line did about it was slide
 * them over each other until they did. Past about seven that stops reading as a hand of cards
 * and starts reading as one wide strip of pattern: the backs are a repeat, so the seam between
 * two overlapping cards is invisible and the player cannot count their own hand, let alone aim
 * at a card in it.
 *
 * So a hand that does not fit steps down one size and wraps, which is what the web client did
 * (`legacy-web/.../horizontal-player-cards.tsx` — `flex-wrap`, with the card size chosen from
 * the count). Both halves are needed: wrapping alone doubles the seat's height and squeezes
 * the felt until the side seats have no room, which is why it was taken out before.
 *
 * The three seats opposite keep the old behaviour, because their cards are counted rather than
 * read and the felt's width is the scarcest thing on a phone — see [SideSeat].
 */
@Composable
private fun Hand(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    scale: CardScale,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        // The footprint, not the picture: `CardFace` reserves [TapTarget] whatever it draws,
        // so a hand of nine 44dp boxes needs 44dp nine times over however small the art is.
        val fits = fits(seat.cards.size, scale, maxWidth)
        val drawn = if (fits) scale else scale.crowded()

        HandLine(vertical = false, wrap = true) {
            Cards(seat, view, table, drawn, onMove, turned = false)
        }
    }
}

/** Whether [count] cards at [scale] stand side by side in [room] without touching. */
private fun fits(count: Int, scale: CardScale, room: Dp): Boolean {
    if (count <= 1) return true
    val step = maxOf(scale.width, TapTarget)
    return step * count + Tight * (count - 1) <= room
}

/**
 * One hand's cards, with a gap held open wherever one is in the air.
 *
 * A hand closes up the moment a card leaves it — the table has already stepped to the position
 * the move produced — so without this the rest of the hand slides sideways underneath a card
 * that is still flying, and a card thrown from the last slot leaves from nowhere. The gap is
 * the web app's transparent card by another route: the space stays until the flight lands.
 */
@Composable
private fun Cards(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    scale: CardScale,
    onMove: (Move) -> Unit,
    turned: Boolean,
) {
    val stage = LocalStage.current
    val rendering = Rendering(view, table, scale)

    // A gap is held open only where a card left and **nothing is arriving to fill it**.
    //
    // A swap does both at the same position: the drawn card lands in the slot the old one
    // flew out of, and the hand is the same size afterwards. Holding a gap there as well
    // inserted a slot the hand does not have — so the arriving card was drawn one place along
    // (in the air *and* in the hand at once), the hand was a card wider for the length of the
    // flight, and every anchor after it was off by one. `SeatCard` already draws the gap for a
    // card that is landing; this is only for a hand that has actually lost one.
    //
    // "Arriving" has two tenses, and both matter: a declared swap's outgoing flight is the
    // slower one, so the incoming card *has landed* while the old one is still in the air —
    // the slot is drawn and needs no gap, which is what [Stage.hasLanded] remembers.
    val gaps = stage.leaving[seat.id].orEmpty()
        .filterNot {
            val anchor = Anchor.Seat(seat.id, it)
            anchor in stage.inFlight || stage.hasLanded(anchor)
        }
        .toSet()

    var position = 0
    var card = 0
    while (card < seat.cards.size || position in gaps) {
        if (position in gaps) {
            EmptySlot(
                scale,
                "",
                Modifier.anchoredAt(stage, Anchor.Seat(seat.id, position), scale),
                turned,
            )
        } else {
            SeatCard(seat, position, seat.cards[card], rendering, onMove, turned)
            card++
        }
        position++
    }
}

/**
 * Cards laid along one axis, in a block whose length never exceeds the room it was given.
 *
 * Five cards is the deal and not the limit: a wrong guess, a wrong toss-in and an Ace each
 * add one, and nothing but the end of the round takes any away, so a hand of eight or nine
 * is an ordinary way to lose rather than a stress test. There are two ways to fit one, and
 * this draws both, because they are right in different places.
 *
 * **Overlapping** ([wrap] false) keeps the block exactly one card thick: the cards slide over
 * each other the way a hand of cards behaves in a hand, never past halfway, so each keeps a
 * strip of itself to be tapped by — and the strip belongs to the card on top, because that is
 * the one whose face can be seen. It is what the seats at the sides get, where a second column
 * would take width the felt does not have.
 *
 * **Wrapping** ([wrap] true) runs onto a second line instead, in even lines rather than one
 * full line and a remainder — a row of six above a row of one reads as a mistake. It is what
 * the hand a player *reads* gets, because past about seven the overlapping version stops
 * reading as cards at all: the backs are a repeating pattern, so the seam between two of them
 * is invisible and a player cannot count their own hand. The caller shrinks the cards a step
 * first ([CardScale.crowded]), which is what keeps two lines from doubling the seat's height —
 * wrapping without that is what pushed the middle of the table down until the side seats had
 * a single card's height to lay nine cards in, and is why this was taken out once before.
 */
@Composable
private fun HandLine(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val cards = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        if (cards.isEmpty()) return@Layout layout(0, 0) {}

        val along = { p: Placeable -> if (vertical) p.height else p.width }
        val across = { p: Placeable -> if (vertical) p.width else p.height }
        val card = cards.maxOf(along)
        val thick = cards.maxOf(across)
        val room = if (vertical) constraints.maxHeight else constraints.maxWidth
        val gap = Tight.roundToPx()
        val loose = card * cards.size + gap * (cards.size - 1) <= room

        // How many go on a line, and how many lines that makes. Evened out afterwards so the
        // last line is never left holding one card.
        val lines = if (loose || !wrap) {
            1
        } else {
            val perLine = ((room + gap) / (card + gap)).coerceAtLeast(1)
            (cards.size + perLine - 1) / perLine
        }
        val perLine = (cards.size + lines - 1) / lines

        val pitch = when {
            cards.size == 1 -> 0
            loose || lines > 1 -> card + gap
            // One line and not enough room: slide them over each other, never past halfway.
            else -> ((room - card) / (cards.size - 1)).coerceAtLeast((card * MIN_SHOWING).toInt())
        }
        val length = card + pitch * (minOf(cards.size, perLine) - 1)
        val breadth = thick * lines + gap * (lines - 1)

        layout(
            width = if (vertical) breadth else length,
            height = if (vertical) length else breadth,
        ) {
            cards.forEachIndexed { i, card ->
                // In order, so a card that overlaps its neighbour is the later one — and in
                // Compose the last placed is both the one drawn on top and the one a finger
                // lands on, which is what makes the exposed strip belong to the right card.
                val onLine = (i % perLine) * pitch
                val downLines = (i / perLine) * (thick + gap)
                if (vertical) card.place(downLines, onLine) else card.place(onLine, downLines)
            }
        }
    }
}

/** How much of a card stays out from under the next one when a hand runs out of room. */
private const val MIN_SHOWING = 0.55f

@Composable
private fun Plate(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
) {
    val active = view.turnHolderId == seat.id
    val marks = buildList {
        if (seat.isVintoCaller) add(stringResource(Res.string.table_vinto_mark))
        if (seat.id == view.coalitionLeaderId) add(stringResource(Res.string.table_leads_mark))
        view.scores?.get(seat.id)?.let { add("$it") }
    }
    val tap = table.seatTaps[seat.id]
    val stage = LocalStage.current
    val line = stage.lineFor(seat.id)
    val pointed = stage.attentionOn(seat.id)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Three bots that only ever move cards are furniture. A line at the right moment —
        // announcing a Vinto, wincing at a penalty — is what makes the other seats read as
        // opponents, and it costs one string.
        line?.let {
            Surface(
                shape = RoundedCornerShape(FeltCorner),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = Gap, vertical = 2.dp),
                )
            }
        }

        SeatPlate(
            name = seat.nickname,
            active = active,
            modifier = Modifier.markedAs(stage, "seat:${seat.id}"),
            pointed = pointed,
            marks = marks.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            bot = seat.isBot,
            size = sizes.avatar,
            onClick = tap?.let { { onMove(it) } },
        )
    }
}

@Composable
private fun SeatCard(
    seat: PlayerSeatView,
    position: Int,
    card: CardView,
    of: Rendering,
    onMove: (Move) -> Unit,
    turned: Boolean = false,
) {
    val (view, table, scale) = of
    val ref = CardRef(seat.id, position)
    val move = table.taps[ref]
    val stage = LocalStage.current
    val anchor = Anchor.Seat(seat.id, position)

    if (anchor in stage.inFlight || stage.isPeeking(anchor)) {
        // The same footprint the landed card will claim — `CardFace` pads itself out to
        // [TapTarget], so a gap measured at the bare card size grew on landing, and the first
        // card of a deal to arrive re-pitched the whole row: every card still in the air then
        // settled sideways at the last moment, and the seat plate slid over to make room.
        val w = maxOf(if (turned) scale.height else scale.width, TapTarget)
        val h = maxOf(if (turned) scale.width else scale.height, TapTarget)
        Box(modifier = Modifier.size(w, h).anchoredAt(stage, anchor, scale))
        return
    }

    Box(modifier = Modifier.anchoredAt(stage, anchor, scale)) {
        CardFace(
            // Face-up only where the table says so. The view carries more than that —
            // everything this seat *knows* — and drawing all of it would hand the player a
            // perfect memory of their own hand, which is the one thing this game asks them
            // to keep themselves. Concealed cards wear their backs a moment longer: the
            // scoring reveal turns them seat by seat, and the stage says whose turn it is.
            card = if (ref in table.revealed && !stage.isConcealing(anchor)) {
                card
            } else {
                CardView.Hidden
            },
            scale = scale,
            state = CardState(
                tappable = move != null,
                chosen = ref.isTargeted(view),
                turned = turned,
                flinching = stage.isFlinching(anchor),
            ),
            label = stringResource(Res.string.card_position, seat.nickname, position + 1),
            onClick = move?.let { { onMove(it) } },
        )

        // A declared claim, worn on the card's corner: what its owner *says* it is, readable
        // by every seat and exactly as trustworthy as the memory it came from. A label on
        // the back — never the card itself, which stays hidden.
        table.badges[ref]?.let { claim ->
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                shape = RoundedCornerShape(TableSizes.Corner),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = claim,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** The deck and the discard, labelled as on the web table, with the toss-in rank beneath. */
@Composable
private fun Piles(view: PlayerView, sizes: TableSizes, onHelp: (Rank?) -> Unit) {
    val stage = LocalStage.current

    // The web app's two-by-two, and the reason for it: what a player draws is public, so it
    // belongs in the middle of the table under the deck it came from — not beside the hand of
    // whoever drew it, where it reads as a card they are already holding, and where three
    // quarters of the time it cannot be drawn at all because the seat is somebody else's.
    //
    //      DRAW      DISCARD
    //      drawn     toss-in
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.Top) {
            Pile(stringResource(Res.string.table_draw)) {
                val deck = Modifier.anchoredAt(stage, Anchor.Deck, sizes.theirs)
                if (view.drawPileSize > 0) {
                    CardFace(
                        card = CardView.Hidden,
                        scale = sizes.theirs,
                        modifier = deck,
                        // The count as well as the name: it is the number that decides how
                        // a round ends, and a reader hears it where a glance would see it.
                        label = stringResource(Res.string.header_deck_left, view.drawPileSize),
                    )
                } else {
                    EmptySlot(sizes.theirs, "—", deck)
                }
            }

            Pile(stringResource(Res.string.table_discard)) {
                Discard(view, sizes, stage, onHelp)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = Tight),
        ) {
            DrawnCard(view, sizes, stage, onHelp)
            TossIn(view)
        }
    }
}

/**
 * What somebody has just drawn, under the deck it came from.
 *
 * Whoever drew it: the rules have the drawn card revealed publicly, and watching what an
 * opponent took and what they then did with it is most of the information in this game. It
 * used to be drawn only for the seat holding it, so for three turns out of four the card flew
 * from the deck and vanished — which is exactly how it looked.
 *
 * The slot is always there, empty or not, so nothing moves when a card arrives in it.
 */
@Composable
private fun DrawnCard(view: PlayerView, sizes: TableSizes, stage: Stage, onHelp: (Rank?) -> Unit) {
    // Only while its player is *deciding* about it. The moment the action is engaged the
    // card is on the pile — that is `cardInPlay`'s exact rule, and this is its complement:
    // without the phase check a drawn 8 being aimed sat in this slot and on the discard at
    // once, the same card in two places for the length of its own action.
    val drawn = view.pendingAction?.takeIf {
        it.from == PendingCardOrigin.DRAWING && it.actionPhase == ActionPhase.CHOOSING_ACTION
    }
    val slot = Modifier.anchoredAt(stage, Anchor.Pending, sizes.theirs)

    // Empty whenever the card is somewhere else: on its way in, on its way out, or being
    // shown off before it goes. Each of those draws the card itself, and a slot that draws it
    // as well is the same card in two places — which is what a played card looked like for
    // the length of its flourish, sitting in the slot and lying on the pile at once.
    val elsewhere = Anchor.Pending in stage.inFlight ||
        stage.isLeaving(Anchor.Pending) ||
        stage.isFlourishing(Anchor.Pending)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (drawn == null || elsewhere) {
            EmptySlot(sizes.theirs, "", slot)
        } else {
            // Tapping the drawn card opens the help sheet, whose "right now" block explains
            // exactly this card — the shortest route to "what does this do" for somebody who
            // has not found the "?" yet.
            CardFace(
                drawn.card,
                sizes.theirs,
                modifier = slot,
                label = stringResource(Res.string.card_in_hand),
                onClick = { onHelp((drawn.card as? CardView.Visible)?.card?.rank) },
            )
        }
    }
}

/**
 * The cards this toss-in window has taken, under the ranks it is asking for.
 *
 * The web app's toss-in area, and the half that was missing: it lists what has actually been
 * thrown and by whom. The drawn slot used to carry a line per target instead — "Raph — ?"
 * whenever the seat was not entitled to the face, which is a question mark where a fact
 * should be, in the one place on the table reserved for the card *you* drew.
 *
 * A thrown card is public: it goes face-up on the pile, so its rank is in every seat's view
 * and drawing it here reveals nothing the table has not already seen fly.
 */
@Composable
private fun Thrown(view: PlayerView, toss: ActiveTossIn) {
    // Drawn whether or not anything has gone in yet, and empty when nothing has: the space a
    // thrown card will occupy is held from the moment the window opens. Reserving it with a
    // measured minimum height instead left it two pixels short, and two pixels is enough to
    // move the deck above it while a card is in the air on its way here.
    val thrown = toss.queuedActions

    Text(
        if (thrown.isEmpty()) "" else stringResource(Res.string.table_tossed, thrown.size),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onFelt(),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tight),
        // A minimum, not a fix: the row reserves its space so nothing moves while a card
        // lands here, and still grows when a large system font makes the names taller.
        modifier = Modifier.heightIn(min = ThrownRow),
    ) {
        thrown.forEach { throw_ ->
            val who = view.players.firstOrNull { it.id == throw_.playerId }?.nickname ?: "—"
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(artFor(throw_.rank)),
                    contentDescription = stringResource(
                        Res.string.card_thrown_by,
                        who,
                        throw_.rank.serialName,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(ThrownWidth, ThrownHeight)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Text(
                    who,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onFelt(),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The rank the table is waiting on, under the pile it went down on. */
@Composable
private fun TossIn(view: PlayerView) {
    // The space is reserved whether or not a window is open. It used to appear with the first
    // card that went down, which pushed the whole middle of the table up a line at the exact
    // moment a card was landing there.
    val summary = view.activeTossIn?.let { toss ->
        stringResource(
            Res.string.table_toss_in_summary,
            toss.ranks.joinToString(" ") { it.serialName },
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .heightIn(min = TossHeight)
            .semantics { summary?.let { contentDescription = it } },
    ) {
        val toss = view.activeTossIn ?: return@Column

        // Online, the room finishes the window for whoever stays silent, and the heading
        // carries the countdown it is finishing on. Solo there is no clock and no suffix —
        // the view simply never carries the duration.
        val seconds = rememberCountdownSeconds(view.tossInMsRemaining)
        Text(
            if (seconds != null) {
                stringResource(Res.string.table_toss_in_timed, seconds)
            } else {
                stringResource(Res.string.table_toss_in)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onFelt(),
        )

        // The window breathes while it is open for throws, the same signal a tappable card
        // wears: this is the one moment that belongs to the whole table at once, and a
        // static chip read as furniture — a player who missed the cards' rings had nothing
        // saying "the table is waiting on this".
        val open = toss.waitingForInput
        val pulse = rememberInfiniteTransition(label = "tossWindow")
        val breath by pulse.animateFloat(
            initialValue = TossQuiet,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(TossBreathMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tossBreath",
        )
        Surface(
            shape = RoundedCornerShape(Tight),
            color = MaterialTheme.colorScheme.surface.copy(alpha = TossFill),
            border = androidx.compose.foundation.BorderStroke(
                if (open) 2.dp else 1.dp,
                MaterialTheme.colorScheme.onFelt().copy(alpha = if (open) breath else 1f),
            ),
        ) {
            // On one line, whatever the rank is called. Confined to a card's width, "Joker"
            // broke across two and read as "Jok / er".
            Text(
                toss.ranks.joinToString(" ") { it.serialName },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onFelt(),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = Gap, vertical = 2.dp),
            )
        }

        Thrown(view, toss)
    }
}

private const val TossFill = 0.15f

/** The trough of the open window's breath, and its period — see `CardFace`'s pulse. */
private const val TossQuiet = 0.45f
private const val TossBreathMs = 1100

/**
 * Seconds left on a service clock, counted down locally between broadcasts.
 *
 * The wire carries a *duration* — the room's convention, because a phone whose own clock is
 * a minute out would render an absolute deadline as a minute of nonsense — so the client
 * takes it at receipt and ticks from there. Every broadcast that carries a fresh duration
 * resnaps the count to the room's truth. Rounded up, so a fifteen-second window opens on
 * "15" rather than "14", and floored at zero: the room may be a beat late finishing the
 * window, and a countdown must never say less than nothing.
 */
@Composable
internal fun rememberCountdownSeconds(msLeft: Long?): Int? {
    if (msLeft == null) return null
    var left by remember(msLeft) { mutableStateOf(msLeft) }
    LaunchedEffect(msLeft) {
        while (left > 0) {
            delay(CLOCK_TICK_MS)
            left -= CLOCK_TICK_MS
        }
    }
    return ((left + CLOCK_TICK_MS - 1) / CLOCK_TICK_MS).toInt().coerceAtLeast(0)
}

private const val CLOCK_TICK_MS = 1000L

/** The room a toss-in window takes, kept whether one is open or not. */
private val TossHeight = 96.dp

/**
 * A thrown card, drawn small because it is a record rather than a card in play.
 *
 * The room for one row of them is reserved whether or not any have been thrown — see the
 * note on the toss-in area: this is the corner of the table that grows while a card is
 * landing in it, and growing under a landing card moves the place it is landing.
 */
private val ThrownWidth = 20.dp
private val ThrownHeight = 28.dp

/** The card and the name under it: one row, one height, whatever it holds. */
private val ThrownRow = 44.dp

/**
 * The discard pile: what is on top, what is just under it, and what is in play.
 *
 * Three things the single top card could not say, all of them seen on a phone:
 *
 * - **A card being played is on the table, not in somebody's hand.** A declared swap-out, a
 *   card thrown in, a rank a King borrowed — the engine holds these as the *pending* action
 *   while their action is aimed, and the pile is empty for as long as that takes. Drawn by the
 *   player's own hand it looked like a card they were still holding; it belongs here, which is
 *   where the toss-in window says it is.
 * - **A card can land underneath.** An unplayed action card stays on top so the next player
 *   can take it (`clearTossInAfterActionableCard`), so a card discarded during a toss-in queue
 *   goes *beneath* it — and simply vanished. The one below now peeks out from behind.
 * - Nothing is drawn here while a card is on its way: the overlay has that card, and showing
 *   it at both ends makes the eye follow the copy rather than the movement.
 */
@Composable
private fun Discard(view: PlayerView, sizes: TableSizes, stage: Stage, onHelp: (Rank?) -> Unit) {
    val pile = Modifier.anchoredAt(stage, Anchor.Discard, sizes.theirs)

    // The pile draws every card that is lying on it, and never one that is somewhere else.
    //
    // Two things can be somewhere else: a card in the air on its way here, and a card being
    // shown off before it travels. Both draw themselves, so the pile must not.
    val arriving = stage.landingOn(Anchor.Discard)
    val flourishing = stage.flourish != null

    // What the pile was showing before all this. Kept as the *previous* top rather than the
    // current one, because the table steps to the new position before the cards fly: for one
    // frame the pile's top is already the card about to be thrown at it, and remembering that
    // drew the card on the pile while it was still crossing the table.
    var latest by remember { mutableStateOf<Card?>(null) }
    var previous by remember { mutableStateOf<Card?>(null) }
    LaunchedEffect(view.discardTop?.id) {
        previous = latest
        latest = view.discardTop
    }

    // And the card underneath the one arriving is only the *previous* top when the arriving
    // card is the top already — which is what happens when the engine has recorded the
    // discard before the card has finished travelling. A tossed-in card is queued instead, so
    // the pile still holds the card it held before, and showing the one before *that* left the
    // pile blank, or showing a card two moves old.
    val arrivingIsTheTop = arriving is CardView.Visible && arriving.card.id == view.discardTop?.id
    val covered = if (arrivingIsTheTop) previous else view.cardInPlay ?: view.discardTop

    val face = pileFace(
        top = view.discardTop,
        covered = covered,
        inPlay = view.cardInPlay,
        landing = arriving != null || flourishing,
    )

    if (face == null) {
        EmptySlot(sizes.theirs, "—", pile)
        return
    }

    CardFace(
        card = CardView.Visible(face),
        scale = sizes.theirs,
        modifier = pile,
        state = CardState(
            verdict = stage.verdictAt(Anchor.Discard),
            // Unused, so takeable: the difference between a card somebody played and one
            // they only put down, which is otherwise invisible the moment it lands.
            live = face.actionIsLive(),
        ),
        label = stringResource(
            if (face.actionIsLive()) Res.string.card_discarded_live else Res.string.card_discarded,
            face.rank.serialName,
        ),
        // What does this one do — asked of the card itself, answered about the card itself.
        onClick = { onHelp(face.rank) },
    )
}

/**
 * The one card the discard pile shows.
 *
 * One, not two. A sliver of the card underneath was drawn as well, on the theory that a pile
 * looks like a pile — but the discard is read rather than admired, and a second rank peeking
 * out from behind the first is a second rank to mistake for the top one.
 *
 * The [landing] case is the whole reason this is a function rather than three expressions
 * inline. While a card is on its way to the pile the overlay is drawing it, so the pile must
 * not draw it too — and hiding *everything* is what it used to do, which meant that throwing
 * a King onto a King emptied the pile for the length of the throw and filled it again. What
 * the pile shows while a card is in the air is the card about to be [covered] — remembered by
 * the caller, since the engine sends only the top of the pile — or the real top when a card
 * from a hand is being played over it. Nothing, only when there was nothing there to begin
 * with.
 */
internal fun pileFace(top: Card?, covered: Card?, inPlay: Card?, landing: Boolean): Card? = when {
    landing -> if (inPlay != null) top else covered
    inPlay != null -> inPlay
    else -> top
}

@Composable
private fun Pile(label: String, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // A pile of cards is the one thing on this table with real thickness, so it is the
        // one that most obviously wants a shadow under it. Drawn behind rather than as an
        // elevation, because the felt is not a Material surface and a Material shadow on it
        // reads as a floating card in an app.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawOval(
                            brush = contactShadow(),
                            topLeft = Offset(0f, size.height * SHADOW_DROP),
                            size = Size(size.width, size.height * SHADOW_SQUASH),
                        )
                    },
            )
            content()
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onFelt(),
        )
    }
}

/**
 * What a hand needs to draw itself: the game, what may be touched, and how big to draw it.
 *
 * Three things that always travel together and never separately — bundling them is what keeps
 * the card composables to a readable signature rather than a list of arguments in a fixed
 * order that nobody can check at a glance.
 */
private data class Rendering(val view: PlayerView, val table: Table, val scale: CardScale)

/** Cards this action has already been aimed at, so the player can see what they have chosen. */
private fun CardRef.isTargeted(view: PlayerView): Boolean =
    view.pendingAction?.targets.orEmpty().any { it.playerId == playerId && it.position == position }
