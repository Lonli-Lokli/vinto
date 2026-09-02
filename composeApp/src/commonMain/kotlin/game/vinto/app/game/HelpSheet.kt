package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.help_card_worth
import game.vinto.app.art.help_closing
import game.vinto.app.art.help_counts_body
import game.vinto.app.art.help_counts_title
import game.vinto.app.art.help_group_lookers
import game.vinto.app.art.help_group_movers
import game.vinto.app.art.help_group_numbers
import game.vinto.app.art.help_group_odd
import game.vinto.app.art.help_no_action
import game.vinto.app.art.help_right_now
import game.vinto.app.art.help_signals
import game.vinto.app.art.help_the_cards
import game.vinto.app.art.signal_coalition
import game.vinto.app.art.signal_coalition_meaning
import game.vinto.app.art.signal_live
import game.vinto.app.art.signal_live_meaning
import game.vinto.app.art.signal_peek
import game.vinto.app.art.signal_peek_meaning
import game.vinto.app.art.signal_penalty
import game.vinto.app.art.signal_penalty_meaning
import game.vinto.app.art.signal_reshuffle
import game.vinto.app.art.signal_reshuffle_meaning
import game.vinto.app.art.signal_tappable
import game.vinto.app.art.signal_tappable_meaning
import game.vinto.app.art.signal_turn
import game.vinto.app.art.signal_turn_meaning
import game.vinto.app.art.signal_vinto
import game.vinto.app.art.signal_vinto_meaning
import game.vinto.app.explained
import game.vinto.app.theme.CardWhite
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Signal
import game.vinto.app.theme.Slate
import game.vinto.app.theme.VintoSheet
import game.vinto.client.Explains
import game.vinto.shapes.CardConfig
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardConfig
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val Pad = 16.dp
private val Gap = 10.dp
private val RowGap = 12.dp
private val Corner = 6.dp
private val Chip = 46.dp

/**
 * What the cards do.
 *
 * Two parts, in the order a player needs them: what is happening *now* — the rule that
 * applies to the move being asked for, or what the card in hand does — and then every rank,
 * because the answer to "what does a Queen do again" is the reason people stop playing card
 * games they have not played before.
 *
 * The words are `CARD_CONFIGS`, which was ported with the engine and is the same copy the web
 * app shows. One set of rules, written once.
 */
@Composable
fun HelpSheet(open: Boolean, now: Explains?, onDismiss: () -> Unit, focus: Rank? = null) {
    // A tap on one card asks about that card and nothing else: its line, its row, and the
    // sheet is done. The whole reference is behind the "?" for whoever wants it.
    if (focus != null) {
        FocusedHelp(open, focus, onDismiss)
        return
    }

    VintoSheet(open = open, onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = Pad).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            now?.let {
                item {
                    Surface(
                        shape = RoundedCornerShape(Corner),
                        color = Rail.fill,
                        border = BorderStroke(1.dp, Rail.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(Gap)) {
                            Text(
                                text = stringResource(Res.string.help_right_now),
                                fontWeight = FontWeight.Bold,
                                fontSize = TitleSize,
                            )
                            Text(explained(it), fontSize = BodySize, color = Rail.inkDim)
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(Res.string.help_the_cards),
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    modifier = Modifier.padding(top = Gap),
                )
            }

            // Grouped by what a card *does*, because that is how a player has to think about
            // them at the table: is this worth points, does it look, does it move cards, or is
            // it one of the odd ones. Fourteen ranks in one column is a list to scroll past.
            GROUPS.forEach { group ->
                item {
                    Text(
                        stringResource(group.title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = BodySize,
                        color = Rail.inkDim,
                        modifier = Modifier.padding(top = Gap),
                    )
                }
                items(group.ranks) { rank -> RankRow(getCardConfig(rank)) }
            }

            item {
                Text(
                    stringResource(Res.string.help_signals),
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    modifier = Modifier.padding(top = Gap),
                )
            }

            items(SIGNALS) { signal -> SignalRow(signal) }

            item {
                Text(
                    stringResource(Res.string.help_closing),
                    fontSize = BodySize,
                    color = Rail.inkDim,
                    modifier = Modifier.padding(vertical = Pad),
                )
            }

            // Task 4.5. Last, because nobody opens this sheet to read it — and present,
            // because the one place a player will look for the answer is the sheet they
            // already open to ask what a card does. Said in the app's own words rather than
            // linked to a policy: the whole claim is small enough to fit in a paragraph.
            item {
                Text(
                    stringResource(Res.string.help_counts_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    modifier = Modifier.padding(top = Gap),
                )
            }

            item {
                Text(
                    stringResource(Res.string.help_counts_body),
                    fontSize = BodySize,
                    color = Rail.inkDim,
                    modifier = Modifier.padding(vertical = Pad),
                )
            }
        }
    }
}

/** The four kinds of card there are, in the order a player meets them. */
@Composable
private fun FocusedHelp(open: Boolean, rank: Rank, onDismiss: () -> Unit) {
    VintoSheet(open = open, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Pad).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Surface(
                shape = RoundedCornerShape(Corner),
                color = Rail.fill,
                border = BorderStroke(1.dp, Rail.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(Gap)) {
                    Text(
                        text = getCardConfig(rank).name,
                        fontWeight = FontWeight.Bold,
                        fontSize = TitleSize,
                    )
                    Text(explained(Explains.TheCardInPlay(rank)), fontSize = BodySize, color = Rail.inkDim)
                }
            }
            RankRow(getCardConfig(rank))
        }
    }
}

private data class Group(val title: StringResource, val ranks: List<Rank>)

private val GROUPS = listOf(
    Group(
        Res.string.help_group_numbers,
        listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX),
    ),
    Group(
        Res.string.help_group_lookers,
        listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN),
    ),
    Group(Res.string.help_group_movers, listOf(Rank.JACK, Rank.QUEEN)),
    Group(Res.string.help_group_odd, listOf(Rank.KING, Rank.ACE, Rank.JOKER)),
)

/**
 * The table's own vocabulary, written down.
 *
 * A card game teaches its rules and then leaves its *signals* to be worked out — which glow
 * means "your turn", which means "this can be touched", which means "somebody just looked at
 * that card". They are not decoration: each one is information a player at a real table would
 * get from watching hands and faces, and a player who has not worked them out is playing a
 * different, worse game.
 *
 * The swatches are [Signal] itself — the same values the table draws — because this list once
 * carried its own copies and they drifted: the sheet showed a white chip for the "can be
 * touched" ring while the felt drew it deep green, and a player went looking for a white ring
 * that does not exist.
 */
private data class Cue(
    val swatch: Color,
    /** What the real ring is drawn against: a white card, or the dark seat plate. */
    val ground: Color,
    val name: StringResource,
    val meaning: StringResource,
)

private val SIGNALS = listOf(
    Cue(Signal.turn, Slate.fill, Res.string.signal_turn, Res.string.signal_turn_meaning),
    Cue(Signal.vinto, Slate.fill, Res.string.signal_vinto, Res.string.signal_vinto_meaning),
    Cue(Signal.penalty, Slate.fill, Res.string.signal_penalty, Res.string.signal_penalty_meaning),
    Cue(
        Signal.coalition,
        Slate.fill,
        Res.string.signal_coalition,
        Res.string.signal_coalition_meaning,
    ),
    Cue(Signal.live, CardWhite, Res.string.signal_live, Res.string.signal_live_meaning),
    Cue(Signal.tappable, CardWhite, Res.string.signal_tappable, Res.string.signal_tappable_meaning),
    Cue(Signal.peeked, CardWhite, Res.string.signal_peek, Res.string.signal_peek_meaning),
    Cue(
        Color(0xFF9AA5B1),
        CardWhite,
        Res.string.signal_reshuffle,
        Res.string.signal_reshuffle_meaning,
    ),
)

@Composable
private fun SignalRow(signal: Cue) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(Chip),
            shape = RoundedCornerShape(Corner),
            color = signal.ground,
            border = BorderStroke(SwatchRing, signal.swatch),
            content = {},
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(signal.name),
                fontWeight = FontWeight.SemiBold,
                fontSize = BodySize,
            )
            Text(stringResource(signal.meaning), fontSize = BodySize, color = Rail.inkDim)
        }
    }
}

@Composable
private fun RankRow(config: CardConfig) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.Top,
    ) {
        // The card itself, not its letter. A player who learned "Q" from a list still has to
        // match it against a picture on the felt; showing the picture skips that step, and it
        // is the same art the table deals.
        CardPicture(rank = config.rank, width = Chip)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.help_card_worth, config.name, config.value),
                fontWeight = FontWeight.SemiBold,
                fontSize = BodySize,
            )
            Text(
                text = config.longDescription.ifEmpty { stringResource(Res.string.help_no_action) },
                fontSize = BodySize,
                color = Rail.inkDim,
            )
        }
    }
}

private val SwatchRing = 3.dp

private val TitleSize = 16.sp
private val BodySize = 14.sp
