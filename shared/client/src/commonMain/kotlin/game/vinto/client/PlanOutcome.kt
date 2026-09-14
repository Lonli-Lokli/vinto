package game.vinto.client

import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.getCardValue

/**
 * Where a plan would leave the coalition, said as an **outcome** rather than as a number.
 *
 * The round is decided by the caller's total against the *lowest* coalition hand, and a tie
 * pays the caller — so a strip reading "our best: 4" beside a caller on 4 looks level and is a
 * loss. A readout that made the player do that comparison themselves would be a readout that
 * gets ignored at the one moment it matters.
 *
 * [unseen] is carried for the same reason. Most of the caller's hand is usually unknown, and a
 * believed total stated without saying how much of it is a guess is a number pretending to be
 * information.
 */
data class PlanOutcome(
    /** The lowest coalition hand the plan would produce, from what the table has been told. */
    val ourBest: Int,
    /** What the coalition believes the caller holds, from the cards it has actually seen. */
    val theirBelieved: Int,
    /** How many of the caller's cards nobody has spoken about. */
    val unseen: Int,
    /**
     * Every coalition hand as the plan leaves it, in turn order: what is named, and how many
     * cards are not.
     */
    val hands: List<HandReading> = emptyList(),
) {
    /**
     * Strictly below, because level is a loss.
     *
     * `RoundScoringTest.aTieGoesToTheCaller` is the rule this reads: the caller takes the
     * round at par, so the coalition has to beat them and not merely match them.
     */
    val wins: Boolean get() = ourBest < theirBelieved

    /** Level: shown as losing, since it pays the caller. */
    val level: Boolean get() = ourBest == theirBelieved

    /** With most of a hand unseen, the comparison is a guess and should read as one. */
    val confident: Boolean get() = unseen == 0
}

/** One hand as the plan leaves it: the total of the cards somebody has named, and the count nobody has. */
data class HandReading(val seat: String, val who: Speaker, val named: Int, val unnamed: Int)

/**
 * Reads a plan against the table, from public information alone.
 *
 * **Priced off the table the plan arrives at**, which the rehearsal already builds: every hand
 * as the turns leave it — cards traded, thrown, pointed at by a King, put down for an unseen
 * draw, drawn on an Ace's say-so — with what was said about each card still on it. One picture
 * for the film, the felt and the numbers, so the three cannot disagree about where a plan lands.
 *
 * The numbers come from [believedOnView] — standing claims — rather than from anybody's real
 * hand, so this is the same arithmetic the coalition could do out loud, and a plan built on a
 * wrong claim reads as good right up until the reveal says otherwise. That is the model, not a
 * defect: a claim is only as good as the claimant's memory.
 */
fun planOutcome(view: PlayerView, plan: CoalitionPlan): PlanOutcome? {
    val caller = view.players.firstOrNull { it.id == view.vintoCallerId } ?: return null
    if (view.players.all { it.id == caller.id }) return null

    val arrival = rehearsal(view, plan).arrival
    val order = game.vinto.shapes.coalitionInTurnOrder(view.players.map { it.id }, caller.id)
    val coalition = order.mapNotNull { id -> arrival.players.firstOrNull { it.id == id } }

    val believed = caller.cards.indices.map { position ->
        val claims = believedOnView(caller, position)
        if (claims.sources.isEmpty()) null else claims.value
    }

    return PlanOutcome(
        ourBest = coalition.minOfOrNull { seat -> seat.cards.indices.sumOf { valueAt(seat, it) } } ?: 0,
        theirBelieved = believed.filterNotNull().sum(),
        unseen = believed.count { it == null },
        hands = coalition.map { seat ->
            val named = seat.cards.indices.map { position ->
                believedOnView(seat, position).takeIf { it.sources.isNotEmpty() }?.value
            }
            HandReading(
                seat.id,
                speakerFor(view, seat.id),
                named.filterNotNull().sum(),
                named.count { it == null },
            )
        },
    )
}

/** What a position is worth as far as the table has been told; the deck's average otherwise. */
internal fun believedValueAt(seat: PlayerSeatView, position: Int): Int = valueAt(seat, position)

private fun valueAt(seat: PlayerSeatView, position: Int): Int {
    val believed = believedOnView(seat, position)
    return if (believed.sources.isEmpty()) UNSEEN_CARD else believed.value
}

/**
 * A card nobody has spoken about, priced.
 *
 * The deck's mean, rounded: 2–6 at face, the action cards at ten, a King at nothing, an Ace at
 * one and a Joker below zero. A plan has to price an unknown as *something* or every unspoken
 * card would read as free.
 */
private val UNSEEN_CARD = game.vinto.shapes.ALL_RANKS
    .sumOf { getCardValue(it) }
    .let { it / game.vinto.shapes.ALL_RANKS.size }
