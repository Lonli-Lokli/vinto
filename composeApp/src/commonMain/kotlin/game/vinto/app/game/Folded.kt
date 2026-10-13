package game.vinto.app.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import game.vinto.client.Move

/**
 * The table half open like a book ([Posture.BOOK]): the felt on one page, the rail and the status
 * column on the other, and the crease between them left to the fold.
 *
 * The iPhone Duo held like this reports its fold as a line about eight points wide down the middle
 * of the inner screen, and a card across it is a card bent in two — a plate's name broken at the
 * spine, a pile one half of which faces the player and the other half the ceiling. So nothing of the
 * table's lies on it: the felt keeps a margin from it on its own page, as it keeps one from every
 * other edge, and the rail starts on the far side of it.
 *
 * **By the side of the glass, whichever way the language reads.** The rail stands beside the status
 * column, and the column is where iOS keeps it — down the Duo's right edge — so the felt has the page
 * away from it ([TableLayout.feltRect]): a right-to-left table that mirrored the pages would put its
 * felt beside the clock and its rail across the fold from the choices it answers.
 *
 * The felt's page is about the closed screen's shape, and its cards are dealt for it
 * ([TableLayout.forScreen]); the rail is the narrow stack, read top to bottom, its choices in the
 * column ([StatusColumn]) — or, with no column to stand in, the header at the top of its page.
 */
@Composable
internal fun Book(
    laid: Laid,
    onMove: (Move) -> Unit,
    onHelp: (HelpTopic?) -> Unit,
    onSettings: () -> Unit,
    onLeave: (() -> Unit)?,
) {
    val layout = laid.layout
    val felt: @Composable () -> Unit = {
        FeltTable(
            state = laid.state,
            sizes = layout.sizes,
            onMove = onMove,
            onHelp = onHelp,
            modifier = Modifier
                .width(layout.feltWidth)
                .fillMaxHeight()
                .padding(start = PageEdge, end = PageEdge, bottom = PageEdge),
        )
    }
    val rail: @Composable () -> Unit = {
        Column(modifier = Modifier.width(layout.railWidth).fillMaxHeight()) {
            if (layout.column == 0.dp) TopRow(laid, onMove, onHelp, onSettings, onLeave)
            ControlPanel(
                state = laid.state,
                onMove = onMove,
                shape = RailShape.STACK,
                choices = laid.choices,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .glides(laid.reshape, RAIL_KEY, Glide.RAIL),
            )
        }
    }
    // The crease is what is left between the two pages' fixed widths ([TableLayout.forScreen]).
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Absolute.Left) {
        if (layout.chrome.columnOnRight) {
            felt()
            Spacer(modifier = Modifier.weight(1f))
            rail()
        } else {
            rail()
            Spacer(modifier = Modifier.weight(1f))
            felt()
        }
    }
}

/**
 * The table half open like a laptop ([Posture.LAPTOP]), in the owner's arrangement (2026-10-13): the
 * other three seats stand up on the upper half, and your own hand, the piles and the turn's choices
 * lie flat on the lower half.
 *
 * Held like this the upper half is a screen standing up across the table from you and the lower one
 * lies on the table in front of you, where your hands are — so the seats you watch stand where you
 * look across, and the cards you touch lie where you touch them. The fold between them is about eight
 * points across the middle, and nothing lies on it ([TableLayout.feltRect]).
 *
 * **The side seats keep their columns.** The upper half has the height for a dealt hand along each
 * rim — five cards a thumb apart are 236 points, and the Duo's half leaves the middle about 290 — so
 * each seat sits in the chair it sits in flat, its cards lying the way a side seat's always lie, and
 * a hand that grows takes a column beside the first as it does on any table ([sideHand]). Laid as rows
 * instead they would have been a third kind of seat, drawn by nothing else, and a glide from the flat
 * table would have had to turn every one of their cards on the way.
 *
 * The header stays in the strip at the top ([TopRow]); the rail stands beside your felt on the lower
 * half, as it does beside the felt of a table on its side, with the drawn card and the log as its room
 * allows. Pieces keep their names across the fold, so folding the phone glides each of them to its
 * half ([glides]).
 */
@Composable
internal fun Laptop(
    laid: Laid,
    onMove: (Move) -> Unit,
    onHelp: (HelpTopic?) -> Unit,
    onSettings: () -> Unit,
    onLeave: (() -> Unit)?,
) {
    val layout = laid.layout
    val direction = LocalLayoutDirection.current
    val far = layout.feltRect(direction)
    val near = layout.nearFelt(direction)
    Column(modifier = Modifier.fillMaxSize()) {
        // The upper half ends at the fold, whatever the header above the felt turns out to need.
        Column(modifier = Modifier.fillMaxWidth().height(far.bottom)) {
            if (layout.column == 0.dp) TopRow(laid, onMove, onHelp, onSettings, onLeave)
            FeltTable(
                state = laid.state,
                sizes = layout.sizes,
                onMove = onMove,
                onHelp = onHelp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = PageEdge, end = PageEdge, bottom = PageEdge),
                part = FeltPart.OTHERS,
            )
        }
        Spacer(modifier = Modifier.height(near.top - far.bottom))
        // What is under the fold takes whatever the screen has below it, which is less than the
        // layout was told where a room keeps its clock under the table.
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            FeltTable(
                state = laid.state,
                sizes = layout.sizes,
                onMove = onMove,
                onHelp = onHelp,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(PageEdge),
                part = FeltPart.OURS,
            )
            ControlPanel(
                state = laid.state,
                onMove = onMove,
                shape = if (layout.narrowRail) RailShape.STACK else RailShape.BESIDE,
                choices = laid.choices,
                modifier = Modifier
                    .width(layout.railWidth)
                    .fillMaxHeight()
                    .glides(laid.reshape, RAIL_KEY, Glide.RAIL),
            )
        }
    }
}

/**
 * The margin a felt keeps inside its page, from the crease as from the screen's edges: the same the
 * felt keeps on every table, so a half reads as the same cloth.
 */
private val PageEdge = 6.dp
