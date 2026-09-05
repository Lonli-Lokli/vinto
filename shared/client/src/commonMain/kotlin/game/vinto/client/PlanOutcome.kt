package game.vinto.client

import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Step
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

/**
 * Reads a plan against the table, from public information alone.
 *
 * The numbers come from [believedOnView] — standing claims — rather than from anybody's real
 * hand, so this is the same arithmetic the coalition could do out loud, and a plan built on a
 * wrong claim reads as good right up until the reveal says otherwise. That is the model, not a
 * defect: a claim is only as good as the claimant's memory.
 */
fun planOutcome(view: PlayerView, plan: CoalitionPlan): PlanOutcome? {
    val caller = view.players.firstOrNull { it.id == view.vintoCallerId } ?: return null
    val coalition = view.players.filter { it.id != caller.id }
    if (coalition.isEmpty()) return null

    val hands = coalition.associate { seat -> seat.id to seat.cards.indices.map { valueAt(seat, it) } }
    val after = applied(hands, plan, view)

    val believed = caller.cards.indices.map { position ->
        val claims = believedOnView(caller, position)
        if (claims.sources.isEmpty()) null else claims.value
    }

    return PlanOutcome(
        ourBest = after.values.minOfOrNull { hand -> hand.sum() } ?: 0,
        theirBelieved = believed.filterNotNull().sum(),
        unseen = believed.count { it == null },
    )
}

/**
 * The hands a plan would leave behind.
 *
 * Positions rather than cards, because that is all a plan can name: a swap trades what is at
 * two positions, whatever those turn out to be. A step naming a position nobody has spoken
 * about simply moves an unknown, which is honest — the plan is worth what the claims behind it
 * are worth.
 */
private fun applied(
    hands: Map<String, List<Int>>,
    plan: CoalitionPlan,
    view: PlayerView,
): Map<String, List<Int>> {
    var working = hands

    for (lane in plan.lanes) {
        when (val step = lane.step) {
            is Step.Swap -> working = swap(working, step.from, step.to)

            // A King empties a rank out of every coalition hand — and only theirs, since the
            // caller may not toss in once they have called.
            is Step.Declare -> working = working.mapValues { (seat, hand) ->
                val owner = view.players.first { it.id == seat }
                hand.filterIndexed { position, _ ->
                    believedOnView(owner, position).candidates.singleOrNull() != step.rank
                }
            }

            Step.TakeTheDiscard, null -> Unit
        }
    }

    // A shed leaves the hand it came from, which is the cheapest way to lower one and costs no
    // turn at all.
    for (shed in plan.sheds) {
        val owner = view.players.firstOrNull { it.id == shed.seat } ?: continue
        working = working.mapValues { (seat, hand) ->
            if (seat != shed.seat) {
                hand
            } else {
                hand.filterIndexed { position, _ ->
                    believedOnView(owner, position).candidates.singleOrNull() != shed.rank
                }
            }
        }
    }

    return working
}

private fun swap(hands: Map<String, List<Int>>, from: CardAt, to: CardAt): Map<String, List<Int>> {
    val here = hands[from.seat]?.getOrNull(from.position) ?: return hands
    val there = hands[to.seat]?.getOrNull(to.position) ?: return hands
    return hands.mapValues { (seat, hand) ->
        hand.mapIndexed { position, value ->
            when {
                seat == from.seat && position == from.position -> there
                seat == to.seat && position == to.position -> here
                else -> value
            }
        }
    }
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
