package game.vinto.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.feltEdge
import game.vinto.app.theme.onFelt
import game.vinto.app.theme.feltGradient
import game.vinto.client.Anchor
import game.vinto.client.CardRef
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Table
import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView

private val Gap = 6.dp
private val Tight = 4.dp
private val Edge = 6.dp
private val FeltCorner = 14.dp
private val Rim = 2.dp

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
    view: PlayerView,
    table: Table,
    refusal: String?,
    recent: List<String>,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = view.viewerId
    val opponents = view.players.filter { it.id != me }
    val mine = view.players.first { it.id == me }

    Column(modifier = modifier.fillMaxSize()) {
        TableHeader(view)

        Felt(modifier = Modifier.weight(1f).padding(horizontal = Edge)) {
            val sizes = TableSizes.forHeight(maxHeight)

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

        ControlPanel(table = table, refusal = refusal, recent = recent, onMove = onMove)
    }
}

/** Where the round is up to, and how much deck is left. */
@Composable
private fun TableHeader(view: PlayerView) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Gap),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "VINTO",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "R${view.roundNumber} / T${view.turnNumber}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(modifier = Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(Tight),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                "${view.drawPileSize}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
        Hand(seat, view, table, sizes.theirs, onMove)
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        SideSeat(left, view, table, sizes, plateFirst = true, onMove = onMove)
        Piles(view, table, sizes)
        SideSeat(right, view, table, sizes, plateFirst = false, onMove = onMove)
    }
}

/**
 * A seat down one edge: a column of cards, with the plate at the end nearest the rim — above
 * on the left, below on the right, so neither plate lands in the middle of the felt.
 */
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
        seat.cards.forEachIndexed { position, card ->
            SeatCard(seat, position, card, view, table, sizes.side, onMove)
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
            Hand(seat, view, table, sizes.mine, onMove)
        }
    }
}

@Composable
private fun Hand(
    seat: PlayerSeatView,
    view: PlayerView,
    table: Table,
    scale: CardScale,
    onMove: (Move) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tight)) {
        seat.cards.forEachIndexed { position, card ->
            SeatCard(seat, position, card, view, table, scale, onMove)
        }
    }
}

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
        if (seat.isVintoCaller) add("Vinto")
        if (seat.id == view.coalitionLeaderId) add("leads")
        view.scores?.get(seat.id)?.let { add("$it") }
    }
    val tap = table.seatTaps[seat.id]

    SeatPlate(
        name = seat.nickname,
        active = active,
        marks = marks.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        size = sizes.avatar,
        onClick = tap?.let { { onMove(it) } },
    )
}

@Composable
private fun SeatCard(
    seat: PlayerSeatView,
    position: Int,
    card: CardView,
    view: PlayerView,
    table: Table,
    scale: CardScale,
    onMove: (Move) -> Unit,
) {
    val ref = CardRef(seat.id, position)
    val move = table.taps[ref]
    val stage = LocalStage.current
    val anchor = Anchor.Seat(seat.id, position)

    if (anchor in stage.inFlight) {
        Box(modifier = Modifier.size(scale.width, scale.height).anchoredAt(stage, anchor))
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
            chosen = ref.isTargeted(view) || ref.isBeingTossed(table),
        ),
        label = "${seat.nickname}, card ${position + 1}",
        onClick = move?.let { { onMove(it) } },
    )
}

/** The deck and the discard, labelled as on the web table, with the toss-in rank beneath. */
@Composable
private fun Piles(view: PlayerView, table: Table, sizes: TableSizes) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.Top) {
            val stage = LocalStage.current

            Pile("DRAW") {
                val deck = Modifier.anchoredAt(stage, Anchor.Deck)
                if (view.drawPileSize > 0) {
                    CardFace(CardView.Hidden, sizes.theirs, modifier = deck, label = "the deck")
                } else {
                    EmptySlot(sizes.theirs, "—", deck)
                }
            }

            Pile("DISCARD") {
                val pile = Modifier.anchoredAt(stage, Anchor.Discard)
                // Nothing on the pile while a card is on its way to it — the overlay is
                // drawing that card, and showing it at both ends makes the eye notice the
                // copy rather than the movement.
                val top = view.discardPile.lastOrNull()
                    .takeIf { Anchor.Discard !in stage.inFlight }
                if (top != null) {
                    CardFace(
                        CardView.Visible(top),
                        sizes.theirs,
                        modifier = pile,
                        label = "discarded ${top.rank.serialName}",
                    )
                } else {
                    EmptySlot(sizes.theirs, "—", pile)
                }
            }
        }

        view.activeTossIn?.let { toss ->
            Text(
                "Toss-in",
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

        if (table.waiting) {
            Text(
                text = table.prompt,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onFelt(),
                modifier = Modifier.padding(top = Gap),
            )
        }
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
    val pending = view.pendingAction?.takeIf { it.playerId == view.viewerId }
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
                label = "the card in your hand",
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

/** Cards this action has already been aimed at, so the player can see what they have chosen. */
private fun CardRef.isTargeted(view: PlayerView): Boolean =
    view.pendingAction?.targets.orEmpty().any { it.playerId == playerId && it.position == position }

private fun CardRef.isBeingTossed(table: Table): Boolean {
    val question = table.taps[this] as? Move.Ask ?: return false
    val tossing = question.question as? Question.Tossing ?: return false
    // The tap toggles: a card already chosen is one this tap would REMOVE.
    return position !in tossing.positions
}
