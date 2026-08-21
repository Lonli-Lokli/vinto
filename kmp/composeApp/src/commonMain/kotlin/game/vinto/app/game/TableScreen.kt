package game.vinto.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.app_name
import game.vinto.app.art.card_discarded
import game.vinto.app.art.card_in_hand
import game.vinto.app.art.card_position
import game.vinto.app.art.card_the_deck
import game.vinto.app.art.table_discard
import game.vinto.app.art.table_draw
import game.vinto.app.art.table_leads_mark
import game.vinto.app.art.table_round_turn
import game.vinto.app.art.table_toss_in
import game.vinto.app.art.table_vinto_mark
import game.vinto.app.theme.Brand
import game.vinto.app.theme.Rail
import game.vinto.app.theme.feltEdge
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.onFelt
import game.vinto.client.Anchor
import game.vinto.client.CardRef
import game.vinto.client.Move
import game.vinto.client.Table
import game.vinto.client.Target
import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.engine.discardTop
import game.vinto.engine.discardUnder
import game.vinto.shapes.PendingCardOrigin
import org.jetbrains.compose.resources.stringResource

private val Gap = 6.dp
private val Tight = 4.dp
private val Edge = 6.dp
private val FeltCorner = 14.dp
private val Rim = 2.dp

/** How far the card under the top one shows from behind it. */
private val Peek = 5.dp
private val HelpSize = 30.dp

/** The deck badge the wordmark sits beside: a dark pill in both schemes, like the plates. */
private val DeckBadge = Color(0xFF14351F)

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
 */
@Composable
fun TableScreen(
    state: TableState,
    layout: TableLayout,
    onMove: (Move) -> Unit,
    onHelp: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = state.view
    val table = state.table
    val me = view.viewerId
    val opponents = view.players.filter { it.id != me }
    val mine = view.players.first { it.id == me }
    val sizes = layout.sizes

    Column(modifier = modifier.fillMaxSize()) {
        TableHeader(view, state.round, onHelp, onReport)

        Felt(modifier = Modifier.weight(1f).padding(horizontal = Edge)) {
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
                )

                MySeat(mine, view, table, sizes, onMove)
            }
        }

        ControlPanel(
            state = state,
            height = layout.railHeight,
            onMove = onMove,
        )
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
    val recent: List<String>,
    val round: Int,
    /**
     * Whether a coach is watching over this table, in which case the rule under the prompt
     * is always spelled out. In a real game it fades — see `ControlPanel`.
     */
    val teaching: Boolean = false,
)

/** Where the round is up to, and how much deck is left. */
@Composable
private fun TableHeader(
    view: PlayerView,
    round: Int,
    onHelp: () -> Unit,
    onReport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Gap),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // On the rail, so the rail's own ink — not the theme's, which is a page colour and
        // reads as dark-on-dark here.
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
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
            onClick = onHelp,
            modifier = Modifier.size(HelpSize).markedAs(LocalStage.current, Target.HELP),
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

        // Always reachable, because the moment worth reporting is the moment it goes wrong
        // and nobody navigates to a menu to capture it.
        Text(
            text = "🐞",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clickable(onClick = onReport)
                .padding(horizontal = Gap, vertical = Tight),
        )

        Surface(
            modifier = Modifier.markedAs(LocalStage.current, Target.BADGE),
            shape = RoundedCornerShape(Tight),
            color = DeckBadge,
            border = androidx.compose.foundation.BorderStroke(1.dp, Brand),
        ) {
            Text(
                "${view.drawPileSize}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Brand,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = Tight),
            )
        }
    }
}

/** The felt: a gradient, a rim, and everything that happens on it. */
@Composable
private fun Felt(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(FeltCorner))
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient()))
            .border(Rim, MaterialTheme.colorScheme.feltEdge(), RoundedCornerShape(FeltCorner)),
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
        Piles(view, sizes)
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
        FlowColumn(
            verticalArrangement = Arrangement.spacedBy(Tight),
            horizontalArrangement = Arrangement.spacedBy(Tight),
            maxItemsInEachColumn = HAND_ROW,
        ) {
            seat.cards.forEachIndexed { position, card ->
                SeatCard(seat, position, card, Rendering(view, table, sizes.side), onMove, turned = true)
            }
        }

        if (!plateFirst) Plate(seat, view, table, sizes, onMove)
    }
}

@Composable
private fun MySeat(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        PendingCard(view, sizes)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Plate(seat, view, table, sizes, onMove)
            Hand(seat, view, table, sizes.mine, onMove, Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * A hand, wrapping onto a second row rather than running off the edge.
 *
 * Five cards is the deal and not the limit — every wrong declaration and every wrong toss-in
 * adds one, and a hand of eight is an ordinary way to lose. A fixed row would push the extras
 * off the screen, which is the one place a player must be able to count.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hand(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    scale: CardScale,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tight),
        verticalArrangement = Arrangement.spacedBy(Tight),
        maxItemsInEachRow = HAND_ROW,
    ) {
        seat.cards.forEachIndexed { position, card ->
            SeatCard(seat, position, card, Rendering(view, table, scale), onMove)
        }
    }
}

/** Five to a row: the size of a dealt hand, so the deal never wraps and a penalty does. */
private const val HAND_ROW = 5

@Composable
private fun Plate(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    sizes: TableSizes,
    onMove: (Move) -> Unit,
) {
    val active = view.players.getOrNull(view.currentPlayerIndex)?.id == seat.id
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
        val w = if (turned) scale.height else scale.width
        val h = if (turned) scale.width else scale.height
        Box(modifier = Modifier.size(w, h).anchoredAt(stage, anchor))
        return
    }

    CardFace(
        modifier = Modifier.anchoredAt(stage, anchor),
        // Face-up only where the table says so. The view carries more than that — everything
        // this seat *knows* — and drawing all of it would hand the player a perfect memory of
        // their own hand, which is the one thing this game asks them to keep themselves.
        card = if (ref in table.revealed) card else CardView.Hidden,
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
}

/** The deck and the discard, labelled as on the web table, with the toss-in rank beneath. */
@Composable
private fun Piles(view: PlayerView, sizes: TableSizes) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.Top) {
            val stage = LocalStage.current

            Pile(stringResource(Res.string.table_draw)) {
                val deck = Modifier.anchoredAt(stage, Anchor.Deck)
                if (view.drawPileSize > 0) {
                    CardFace(
                        card = CardView.Hidden,
                        scale = sizes.theirs,
                        modifier = deck,
                        label = stringResource(Res.string.card_the_deck),
                    )
                } else {
                    EmptySlot(sizes.theirs, "—", deck)
                }
            }

            Pile(stringResource(Res.string.table_discard)) {
                Discard(view, sizes, stage)
            }
        }

        view.activeTossIn?.let { toss ->
            Text(
                stringResource(Res.string.table_toss_in),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onFelt(),
                modifier = Modifier.padding(top = Tight),
            )
            Surface(
                shape = RoundedCornerShape(Tight),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onFelt(),
                ),
            ) {
                Text(
                    toss.ranks.joinToString(" ") { it.serialName },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onFelt(),
                    modifier = Modifier.padding(horizontal = Gap, vertical = 2.dp),
                )
            }
        }

    }
}

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
private fun Discard(view: PlayerView, sizes: TableSizes, stage: Stage) {
    val pile = Modifier.anchoredAt(stage, Anchor.Discard)
    val arriving = Anchor.Discard in stage.inFlight

    // A card in play came off the table, so it is drawn on the table.
    val inPlay = (view.pendingAction?.card as? CardView.Visible)?.card
        ?.takeIf { view.pendingAction?.from == PendingCardOrigin.HAND }

    val top = (inPlay ?: view.discardTop).takeIf { !arriving }
    // Whatever the top card is covering: the real pile top when a card from a hand is being
    // played over it, and otherwise the second card down.
    val under = (if (inPlay != null) view.discardTop else view.discardUnder)
        ?.takeIf { it.id != top?.id && !arriving }

    if (top == null) {
        EmptySlot(sizes.theirs, "—", pile)
        return
    }

    Box(contentAlignment = Alignment.Center) {
        under?.let {
            CardFace(
                card = CardView.Visible(it),
                scale = sizes.theirs,
                modifier = Modifier.offset(x = Peek, y = Peek),
            )
        }

        CardFace(
            card = CardView.Visible(top),
            scale = sizes.theirs,
            modifier = pile,
            state = CardState(verdict = stage.verdictAt(Anchor.Discard)),
            label = stringResource(Res.string.card_discarded, top.rank.serialName),
        )
    }
}

@Composable
private fun Pile(label: String, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onFelt(),
        )
    }
}

/**
 * The card in hand awaiting a decision — apart from the hand, because it is not in it yet.
 *
 * The slot is always there, empty or not. Two reasons, and both are about not moving things
 * under the player: the table would otherwise jump every time a card is drawn, and a place
 * that does not exist has no position, so a card cannot be flown to it — which is exactly the
 * flight that matters most.
 */
@Composable
private fun PendingCard(view: PlayerView, sizes: TableSizes) {
    // Only a card that came off the **deck** waits here. One that came off the table — a
    // declared swap-out, a card thrown in, a rank a King borrowed — is drawn on the discard
    // pile, because that is where it is: face up, in play, and open to anybody holding a
    // match. Drawn beside the hand it read as a card the player was still holding.
    val pending = view.pendingAction
        ?.takeIf { it.playerId == view.viewerId && it.from == PendingCardOrigin.DRAWING }
    val stage = LocalStage.current
    val landing = Anchor.Pending in stage.inFlight

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        if (pending == null || landing) {
            Box(
                modifier = Modifier
                    .size(sizes.mine.width, sizes.mine.height)
                    .anchoredAt(stage, Anchor.Pending),
            )
        } else {
            CardFace(
                pending.card,
                sizes.mine,
                modifier = Modifier.anchoredAt(stage, Anchor.Pending),
                label = stringResource(Res.string.card_in_hand),
            )
        }

        Column {
            pending?.targets.orEmpty().forEach { target ->
                val who = view.players.firstOrNull { it.id == target.playerId }?.nickname ?: "someone"
                val what = (target.card as? CardView.Visible)?.card?.rank?.serialName ?: "a card"
                Text(
                    "$who — $what",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
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
