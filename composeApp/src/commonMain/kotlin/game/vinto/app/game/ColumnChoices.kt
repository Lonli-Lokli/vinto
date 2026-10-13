package game.vinto.app.game

import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.choice_back
import game.vinto.app.art.choice_call_vinto
import game.vinto.app.art.choice_continue
import game.vinto.app.art.choice_discard
import game.vinto.app.art.choice_done
import game.vinto.app.art.choice_draw
import game.vinto.app.art.choice_just_swap
import game.vinto.app.art.choice_keep
import game.vinto.app.art.choice_leave_them
import game.vinto.app.art.choice_put_down
import game.vinto.app.art.choice_start
import game.vinto.app.art.choice_swap
import game.vinto.app.art.choice_use_action
import game.vinto.app.art.choice_use_from_pile
import game.vinto.app.art.pill_back
import game.vinto.app.art.pill_continue
import game.vinto.app.art.pill_discard
import game.vinto.app.art.pill_done
import game.vinto.app.art.pill_down
import game.vinto.app.art.pill_draw
import game.vinto.app.art.pill_keep
import game.vinto.app.art.pill_leave
import game.vinto.app.art.pill_start
import game.vinto.app.art.pill_swap
import game.vinto.app.art.pill_use
import game.vinto.app.art.pill_vinto
import game.vinto.client.Choice
import game.vinto.client.Label
import game.vinto.client.Table
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * A choice as the status column draws it: a mark the app draws over one short word, and where it
 * stands among the others.
 *
 * [rank] counts up from the foot of the column, where a thumb rests: the choice most often taken
 * stands lowest, so it is the one a thumb finds without looking — Discard under Swap under Use the
 * action, Draw under Use from the pile, Continue under Call Vinto (owner, 2026-10-13). Call Vinto is
 * the highest of any set it is in, because it ends the round for everybody and must never be where a
 * resting thumb would press it.
 */
internal data class Pill(val icon: DrawableResource, val word: StringResource, val rank: Int)

/**
 * Where the turn's choices stand: in the rail, as they always have, or in the status column.
 *
 * Never some in each: a set the column cannot hold whole stays wholly in the rail ([choicesAt]),
 * because a turn whose Swap stood at the foot of the column and whose Discard stood under the felt
 * would be a turn with its answers in two places.
 */
enum class ChoicesAt { RAIL, COLUMN }

/**
 * The pill a choice is drawn as in a status column, or null for one that has none.
 *
 * Every choice of an ordinary turn has one — the ones that come one, two or three at a time. The
 * coalition's words have none: they are sentences ("That's all I know", "No, thanks", "Not sure
 * which"), they come beside a picker or a plan that has to stay in the rail anyway, and one word
 * under a mark cannot say them. A choice with no pill keeps its whole set in the rail.
 */
internal fun pillOf(label: Label): Pill? = when (label) {
    Label.DrawCard -> Pill(Res.drawable.choice_draw, Res.string.pill_draw, FOOT)
    is Label.UseFromPile -> Pill(Res.drawable.choice_use_from_pile, Res.string.pill_use, SECOND)
    Label.UseAction -> Pill(Res.drawable.choice_use_action, Res.string.pill_use, THIRD)
    Label.SwapCards -> Pill(Res.drawable.choice_swap, Res.string.pill_swap, SECOND)
    Label.KeepIt -> Pill(Res.drawable.choice_keep, Res.string.pill_keep, SECOND)
    Label.Discard -> Pill(Res.drawable.choice_discard, Res.string.pill_discard, FOOT)
    Label.JustSwap -> Pill(Res.drawable.choice_just_swap, Res.string.pill_swap, SECOND)
    Label.PutItDown -> Pill(Res.drawable.choice_put_down, Res.string.pill_down, FOOT)
    Label.LeaveThem -> Pill(Res.drawable.choice_leave_them, Res.string.pill_leave, THIRD)
    Label.Continue -> Pill(Res.drawable.choice_continue, Res.string.pill_continue, FOOT)
    Label.Done -> Pill(Res.drawable.choice_done, Res.string.pill_done, FOOT)
    Label.StartRound -> Pill(Res.drawable.choice_start, Res.string.pill_start, FOOT)
    Label.Back -> Pill(Res.drawable.choice_back, Res.string.pill_back, FOURTH)
    Label.CallVinto -> Pill(Res.drawable.choice_call_vinto, Res.string.pill_vinto, CALL)
    Label.Withdraw, Label.DoAsSuggested, Label.DeclineSuggestion, Label.Ready, Label.StartTheTurns,
    Label.ThatsAllIKnow, Label.SayIt, is Label.ThisWayRound, Label.NotSureWhichWayRound, Label.Agree,
    is Label.RankSaidBy, Label.AnotherRank, Label.RemoveThrow,
    -> null
}

/**
 * The choices in the order the column stands them, top first: the highest [Pill.rank] at the top and
 * the lowest at the foot. Choices of one rank keep the model's order, which already reads the way the
 * turn does.
 */
internal fun columnOrder(choices: List<Choice>): List<Choice> =
    choices.sortedByDescending { pillOf(it.label)?.rank ?: FOOT }

/**
 * How many pills a status column holds, under its fixed controls: the way out, the rules, the
 * settings, the round's counter — and the plan's switch, in a final round with a plan ([plan]).
 *
 * Worked out from the column's [run] rather than from what the turn is offering, so that whether a
 * set goes to the column is a fact about the screen and the set's size, not about how its words
 * happen to wrap. [fontScale] is the system's text size: the counter and the switch's word grow with
 * it, and the pills, which are one size and shrink their word to fit, do not.
 */
internal fun pillsThatFit(run: ColumnRun, plan: Boolean, fontScale: Float): Int {
    val controls = TapTarget * FIXED_CONTROLS + CounterLine * fontScale + ColumnGap * FIXED_GAPS
    val switch = if (plan) PlanSwitchHigh + PlanSwitchWord * fontScale + ColumnGap else 0.dp
    val room = run.length - controls - switch
    return (room / (PillHigh + ColumnGap)).toInt().coerceAtLeast(0)
}

/**
 * Where the turn's choices stand, given how many pills the column holds ([fit]).
 *
 * In the column only when the whole set can be: no picker (a King's ranks, naming a rank after a
 * swap, a seat to name, the plan) — those stay in the rail with whatever comes beside them, so that
 * "Just swap" is never parted from the ranks it is the alternative to; every choice has a pill; and
 * there are no more of them than fit. Anything else stays wholly in the rail. A turn with nothing to
 * press is the column's too, so the rail does not keep an empty row for a set that is never there.
 */
internal fun choicesAt(table: Table, fit: Int): ChoicesAt = when {
    table.board != null || table.ranks.isNotEmpty() || table.seats.isNotEmpty() -> ChoicesAt.RAIL
    table.choices.any { pillOf(it.label) == null } -> ChoicesAt.RAIL
    table.choices.size > fit -> ChoicesAt.RAIL
    else -> ChoicesAt.COLUMN
}

/**
 * What a pill is called aloud: the choice's whole name, which holds the word the pill shows.
 *
 * WCAG 2.5.3 asks that the name spoken contain the words drawn, so that somebody who sees DISCARD can
 * say "Discard" to Voice Control and be understood. Every English word is a word of its choice's name.
 * A translation may not keep it so — or may not be there yet, when the pill's word falls back to
 * English beside a translated name — and then the word is said first and the name after it, rather
 * than a name that leaves out what is on the screen.
 */
internal fun pillSaid(word: String, full: String): String =
    if (full.contains(word, ignoreCase = true)) full else "$word, $full"

/** The rank of a pill at the foot, and of each place above it; Call Vinto above them all. */
private const val FOOT = 0
private const val SECOND = 1
private const val THIRD = 2
private const val FOURTH = 3
private const val CALL = 9

/** A pill: a little narrower than the closed Duo's 84-point column, and a mark and a word deep. */
internal val PillWide = 72.dp
internal val PillHigh = 70.dp

/** The mark on a pill: large enough to read at a glance, as the column's other marks are. */
internal val PillMark = 26.dp

/** Between the column's controls. */
internal val ColumnGap = 8.dp

/** The controls above the pills: the way out, the rules and the settings, each a thumb ([TapTarget]). */
private const val FIXED_CONTROLS = 3

/** The gaps the fixed controls, the counter and the room above the pills take between them. */
private const val FIXED_GAPS = 4

/** The counter's line at the system's own text size, and the plan's switch: its track, then its word. */
private val CounterLine = 16.dp
private val PlanSwitchHigh = 48.dp
private val PlanSwitchWord = 16.dp
