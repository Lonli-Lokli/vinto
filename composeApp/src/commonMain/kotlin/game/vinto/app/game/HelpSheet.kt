package game.vinto.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.Pages
import game.vinto.app.art.Res
import game.vinto.app.art.badge_disputed
import game.vinto.app.art.badge_paired
import game.vinto.app.art.badge_plain
import game.vinto.app.art.badge_right
import game.vinto.app.art.badge_wrong
import game.vinto.app.art.deck_body
import game.vinto.app.art.deck_title
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
import game.vinto.app.art.help_rules_action
import game.vinto.app.art.help_rules_body
import game.vinto.app.art.help_rules_title
import game.vinto.app.art.help_tab_badges
import game.vinto.app.art.help_tab_cards
import game.vinto.app.art.help_tab_more
import game.vinto.app.art.help_tab_rings
import game.vinto.app.art.signal_live
import game.vinto.app.art.signal_live_meaning
import game.vinto.app.art.signal_peek
import game.vinto.app.art.signal_peek_meaning
import game.vinto.app.art.signal_penalty
import game.vinto.app.art.signal_penalty_meaning
import game.vinto.app.art.signal_pick
import game.vinto.app.art.signal_pick_meaning
import game.vinto.app.art.signal_reshuffle
import game.vinto.app.art.signal_reshuffle_meaning
import game.vinto.app.art.signal_tappable
import game.vinto.app.art.signal_tappable_meaning
import game.vinto.app.art.signal_turn
import game.vinto.app.art.signal_turn_meaning
import game.vinto.app.cardLong
import game.vinto.app.cardName
import game.vinto.app.explained
import game.vinto.app.openUrl
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.CardWhite
import game.vinto.app.theme.ChoiceRow
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Signal
import game.vinto.app.theme.Slate
import game.vinto.app.theme.VintoSheet
import game.vinto.client.Badge
import game.vinto.client.Explains
import game.vinto.client.Verdict
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
fun HelpSheet(open: Boolean, now: Explains?, left: Int, onDismiss: () -> Unit, focus: HelpTopic? = null) {
    // A tap on one thing asks about that thing and nothing else: its line, its row, and the
    // sheet is done. The whole reference is behind the "?" for whoever wants it.
    //
    // Exhaustive and with no `else`, so a third thing worth touching is a compile error here
    // rather than a tap that silently opens the whole reference.
    when (focus) {
        is HelpTopic.Card -> {
            FocusedHelp(open, focus.rank, onDismiss)
            return
        }

        HelpTopic.Deck -> {
            DeckHelp(open, left, onDismiss)
            return
        }

        null -> {
            // The whole reference, which is what the "?" opens.
        }
    }

    VintoSheet(open = open, onDismiss = onDismiss) {
        var tab by rememberSaveable { mutableStateOf(HelpTab.CARDS) }

        Column(modifier = Modifier.padding(horizontal = Pad).fillMaxWidth()) {
            // Above the tabs, not inside one. "What am I being asked right now" is the reason a
            // player opens this sheet mid-turn, and an answer filed under a category is an
            // answer they have to go looking for.
            now?.let { RightNow(it) }

            ChoiceRow(
                options = HelpTab.entries,
                selected = tab,
                label = { stringResource(it.title) },
                onChoose = { tab = it },
                modifier = Modifier.padding(vertical = Gap),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                when (tab) {
                    HelpTab.CARDS -> theCards()
                    HelpTab.RINGS -> theRings()
                    HelpTab.BADGES -> theBadges()
                    HelpTab.MORE -> theRest(left)
                }
            }
        }
    }
}

/**
 * The four things a player can be looking for in here, as four tabs.
 *
 * It was one column and the reason it stopped working is arithmetic: thirteen ranks, seven
 * rings, and four paragraphs at the foot is a screen and a half of scrolling to reach a legend.
 * A player opens this sheet with a card waiting and one hand free, so what they came for has to
 * be one press away rather than one hunt away.
 */
private enum class HelpTab(val title: StringResource) {
    CARDS(Res.string.help_tab_cards),
    RINGS(Res.string.help_tab_rings),
    BADGES(Res.string.help_tab_badges),
    MORE(Res.string.help_tab_more),
}

/** What the table is asking of this player at this moment, in its own words. */
@Composable
private fun RightNow(now: Explains) {
    Surface(
        shape = RoundedCornerShape(Corner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
        modifier = Modifier.fillMaxWidth().padding(top = Gap),
    ) {
        Column(modifier = Modifier.padding(Gap)) {
            Text(
                text = stringResource(Res.string.help_right_now),
                fontWeight = FontWeight.Bold,
                fontSize = TitleSize,
            )
            Text(explained(now), fontSize = BodySize, color = Rail.inkDim)
        }
    }
}

/**
 * Every rank, grouped by what a card *does*.
 *
 * That is how a player has to think about them at the table: is this worth points, does it
 * look, does it move cards, or is it one of the odd ones.
 */
private fun LazyListScope.theCards() {
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
}

/** The colours the table draws round a card or a seat, and only the ones it still draws. */
private fun LazyListScope.theRings() {
    items(SIGNALS) { signal -> SignalRow(signal) }
}

/**
 * The marks a seat and a card wear, which are facts rather than alarms.
 *
 * Colour is down to the three things a player must react to *now*; everything else about a seat
 * or a card is said in a mark, and a mark nobody can read is a mark that is not saying anything.
 * Each row draws the **real** thing — the same composable the table uses — beside the same
 * sentence a screen reader is given for it, so the legend cannot drift from the table.
 */
private fun LazyListScope.theBadges() {
    items(SEAT_MARKS) { mark -> SeatMarkRow(mark) }
    items(CLAIM_MARKS) { mark -> ClaimMarkRow(mark) }
}

/** Everything that is neither a card nor a mark: the round, the count, the deck, the rules. */
private fun LazyListScope.theRest(left: Int) {
    item {
        Text(
            stringResource(Res.string.help_closing),
            fontSize = BodySize,
            color = Rail.inkDim,
            modifier = Modifier.padding(vertical = Pad),
        )
    }

    // Task 4.5. Present because the one place a player will look for the answer is the sheet
    // they already open to ask what a card does. Said in the app's own words rather than linked
    // to a policy: the whole claim is small enough to fit in a paragraph.
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

    item { TheDeck(left) }

    // The rulebook, and deliberately not in here. This sheet answers the question a player has
    // mid-turn — what does this card do — with a card waiting and one hand free.
    // `VINTO_RULES.md` runs to four pages, and the answer to "what are the rules" belongs with
    // the people whose game it is: one authoritative copy, theirs.
    item { RulesLink() }
}

/**
 * The deck, which used to be a chip of its own in the header.
 *
 * Six controls is too many for a phone's header, and this was the one of them that was a
 * *number* rather than a thing to press — so it came out, and its explanation came here, where
 * the rest of "what does this mean" already lives. The count is live, so the sentence is still
 * about this round rather than about decks in general.
 */
@Composable
private fun TheDeck(left: Int) {
    Column(modifier = Modifier.padding(top = Gap, bottom = Pad)) {
        Text(stringResource(Res.string.deck_title), fontWeight = FontWeight.Bold, fontSize = TitleSize)
        Text(stringResource(Res.string.deck_body, left), fontSize = BodySize, color = Rail.inkDim)
    }
}

/** Where the rest of the rules are, on the original game's own site. */
@Composable
private fun RulesLink() {
    Column(modifier = Modifier.padding(top = Gap, bottom = Pad)) {
        Text(
            stringResource(Res.string.help_rules_title),
            fontWeight = FontWeight.Bold,
            fontSize = TitleSize,
        )
        Text(
            stringResource(Res.string.help_rules_body),
            fontSize = BodySize,
            color = Rail.inkDim,
            modifier = Modifier.padding(vertical = Pad),
        )
        GameButton(
            label = stringResource(Res.string.help_rules_action),
            tone = ButtonTone.NEUTRAL,
            // A link that cannot open is silent on purpose: there is nothing a player can do
            // about a missing browser, and the sheet they are reading is not the place to say
            // so. Settings, where a link is the point of the row, reports it.
            onClick = { openUrl(Pages.RULES) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What one touchable thing on the felt is, when somebody touches it.
 *
 * A `Rank?` said this badly. Null meant "the whole reference" and a rank meant "this card", which
 * left no way to say "this pile" — and the deck is the other thing on that table a player presses
 * expecting an answer. Naming the topic instead of nullably naming a card makes the third case a
 * case rather than a special value.
 */
sealed interface HelpTopic {
    /** One rank, from tapping the card that carries it. */
    data class Card(val rank: Rank) : HelpTopic

    /** The draw pile, whose answer is a live count and what happens when it runs out. */
    data object Deck : HelpTopic
}

/**
 * The deck, and how much of it is left.
 *
 * The count is the one figure on the felt that decides how a round *ends*, and until now it was
 * only ever spoken: it is the deck's accessible name, so a screen reader had it and a player
 * looking at the screen did not. It used to be a chip in the header; six controls were too many
 * for a phone, so the chip went (41f9aa1) and left a dialog behind in `GameScreen` that nothing
 * could open.
 *
 * It answers here, beside the discard's answer and in the same shape, because they are the same
 * question asked of two piles. The reshuffle is the half worth saying — everything anybody had
 * learned from watching the discard becomes worthless the moment the deck runs dry, which is a
 * reason to call Vinto rather than a curiosity — and `deck_body` has said so all along.
 */
@Composable
private fun DeckHelp(open: Boolean, left: Int, onDismiss: () -> Unit) {
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
                        text = stringResource(Res.string.deck_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = TitleSize,
                    )
                    Text(
                        text = stringResource(Res.string.deck_body, left),
                        fontSize = BodySize,
                        color = Rail.inkDim,
                    )
                }
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

/**
 * The colours the table still uses, and only those.
 *
 * Vinto and the coalition were rings once and are marks now, so they left this list — a legend
 * that names a colour the table has stopped drawing is worse than no legend, because the one
 * person reading it is the one person trying to learn the table.
 */
private val SIGNALS = listOf(
    Cue(Signal.turn, Slate.fill, Res.string.signal_turn, Res.string.signal_turn_meaning),
    Cue(Signal.pick, Slate.fill, Res.string.signal_pick, Res.string.signal_pick_meaning),
    Cue(Signal.penalty, Slate.fill, Res.string.signal_penalty, Res.string.signal_penalty_meaning),
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

/**
 * The marks a seat plate wears, in the order a player meets them.
 *
 * The list is the enum minus the one mark that is not durable: whether the table is *waiting*
 * on a seat changes every turn and is drawn on the portrait rather than in the row, so a legend
 * entry for it would name something a reader cannot find beside the others.
 */
private val SEAT_MARKS = SeatBadge.entries.toList()

/** The claims a card can wear, as the table draws them. */
private val CLAIM_MARKS = listOf(
    Badge(text = "Q", speakers = emptyList()),
    Badge(text = "Q", speakers = emptyList(), disputed = true),
    Badge(text = "Q", speakers = emptyList(), paired = true),
    Badge(text = "Q", speakers = emptyList(), verdict = Verdict.RIGHT),
    Badge(text = "Q", speakers = emptyList(), verdict = Verdict.WRONG),
)

/** One seat mark: the real glyph, and the sentence a screen reader is given for it. */
@Composable
private fun SeatMarkRow(mark: SeatBadge) {
    LegendRow(said = stringResource(mark.spoken())) { SeatMark(mark, MarkSize) }
}

/** One claim mark, drawn by the same composable the felt draws it with. */
@Composable
private fun ClaimMarkRow(mark: Badge) {
    LegendRow(said = claimMeaning(mark)) { ClaimBadge(mark) }
}

/** What a claim badge is saying, in the words the card itself uses. */
@Composable
private fun claimMeaning(mark: Badge): String = when {
    mark.verdict == Verdict.RIGHT -> stringResource(Res.string.badge_right)
    mark.verdict == Verdict.WRONG -> stringResource(Res.string.badge_wrong)
    mark.disputed -> stringResource(Res.string.badge_disputed)
    mark.paired -> stringResource(Res.string.badge_paired)
    else -> stringResource(Res.string.badge_plain)
}

/** A mark beside what it means, laid out like every other row in this sheet. */
@Composable
private fun LegendRow(said: String, mark: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(Chip), contentAlignment = Alignment.Center) { mark() }
        Text(said, fontSize = BodySize, color = Rail.ink, modifier = Modifier.weight(1f))
    }
}

private val MarkSize = 18.dp

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
                stringResource(Res.string.help_card_worth, cardName(config.rank), config.value),
                fontWeight = FontWeight.SemiBold,
                fontSize = BodySize,
            )
            Text(
                // `cardLong`, not `config.longDescription`: the sentence around this was
                // translated long before the words inside it were, which is what made the "?"
                // read as half-English. CardCopyIsTranslatedTest fails if it comes back.
                text = cardLong(config.rank).ifEmpty { stringResource(Res.string.help_no_action) },
                fontSize = BodySize,
                color = Rail.inkDim,
            )
        }
    }
}

private val SwatchRing = 3.dp

private val TitleSize = 16.sp
private val BodySize = 14.sp
