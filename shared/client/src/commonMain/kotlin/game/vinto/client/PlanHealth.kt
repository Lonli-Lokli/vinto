package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Step

/**
 * How a step is bearing up.
 *
 * The distinction that matters is between a card **moving** and a belief being **wrong**, and
 * they are treated completely differently: the first is bookkeeping and is repaired without
 * saying anything, the second is news and is never repaired at all.
 */
enum class StepHealth {
    /** Still points at what it pointed at. */
    LIVE,

    /**
     * The card moved and the step followed it.
     *
     * Repaired in silence on purpose. The table watched the card go — a Jack or a Queen swap
     * is public — so nothing has been learned and nothing needs saying; a plan that announced
     * its own bookkeeping would be noise at exactly the moment a coalition is busy.
     */
    REANCHORED,

    /**
     * A claim the step stands on has been contradicted by a public reveal.
     *
     * Never repaired, never silently substituted. Somebody's memory was wrong, which is the
     * game working rather than a player failing — and it is information the coalition needs,
     * because everything built on that claim is now built on nothing.
     */
    BROKEN,
}

/** A plan re-read against the table it was made for. */
data class PlanReading(
    /** The plan with every step that could follow its card following it. */
    val plan: CoalitionPlan,
    /** One verdict per lane, by index. */
    val health: List<StepHealth>,
) {
    val anyBroken: Boolean get() = health.any { it == StepHealth.BROKEN }
}

/**
 * Reads a plan against the current table and the reveals so far.
 *
 * **All of this is a client concern**, deliberately. The engine never compares a claim against
 * a card — a claim is speech, and checking it when it is made would end the game the coalition
 * is playing — so the comparison happens here, off the `PublicReveal` stream the client already
 * receives, where it can change nothing and cost no hash.
 *
 * Bots need none of it: `CoalitionPlanner` recomputes at every decision point rather than
 * storing a line, so the shared human plan is the only stored one and therefore the only one
 * that can go stale. Staleness is a UI problem, not a bot problem.
 */
fun readPlan(view: PlayerView, plan: CoalitionPlan, reveals: List<PublicReveal>): PlanReading {
    val contradicted = reveals.filter { reveal ->
        val seat = view.players.firstOrNull { it.id == reveal.playerId } ?: return@filter false
        val believed = believedOnView(seat, reveal.position)
        believed.sources.isNotEmpty() && reveal.card.rank !in believed.candidates
    }

    val lanes = plan.lanes.map { lane -> lane to lane.step }
    val repaired = mutableListOf<game.vinto.shapes.Lane>()
    val health = mutableListOf<StepHealth>()

    for ((lane, step) in lanes) {
        when (step) {
            null -> {
                repaired += lane
                health += StepHealth.LIVE
            }

            is Step.Swap -> {
                val from = follow(view, step.from, contradicted)
                val to = follow(view, step.to, contradicted)
                val worst = worseOf(from.second, to.second)
                repaired += lane.copy(step = Step.Swap(from.first, to.first))
                health += worst
            }

            is Step.Declare -> {
                // A rank nobody is believed to hold any more is a King aimed at nothing.
                val stillThere = view.players.any { seat ->
                    seat.id != view.vintoCallerId &&
                        seat.cards.indices.any { believedOnView(seat, it).candidates == setOf(step.rank) }
                }
                repaired += lane
                health += if (stillThere) StepHealth.LIVE else StepHealth.BROKEN
            }

            Step.TakeTheDiscard -> {
                repaired += lane
                health += StepHealth.LIVE
            }
        }
    }

    return PlanReading(plan.copy(lanes = repaired), spreadBreakage(repaired, health))
}

/**
 * A step that follows its card.
 *
 * The claim moved with the card, keeping its speaker, so the way to find where a step now
 * points is to find where its claim went. That is why a step carries the claim it was built on
 * — a position alone cannot be followed, because once the card has gone the position holds
 * something else and nothing left at the old address says what used to be there.
 *
 * One standing claim saying the same thing is that card, moved. None means it left the table,
 * which is a step standing on nothing.
 */
private fun follow(
    view: PlayerView,
    at: CardAt,
    contradicted: List<PublicReveal>,
): Pair<CardAt, StepHealth> {
    if (contradicted.any { it.playerId == at.seat && it.position == at.position }) {
        return at to StepHealth.BROKEN
    }

    val anchor = at.anchor
    val here = view.players.firstOrNull { it.id == at.seat }
    if (anchor == null) {
        // Pointed at a position rather than at a card — the composer lets a member name their
        // own unspoken card, and a bot its own. There is nothing to follow and nothing a reveal
        // could contradict, so it stands as long as the position does. It used to read as
        // broken from the moment it was made, which made every plan built on one's own hand
        // arrive already in the warning colours.
        val standing = here != null && at.position in here.cards.indices
        return at to if (standing) StepHealth.LIVE else StepHealth.BROKEN
    }
    val stillHere = here?.claims.orEmpty().any { claim ->
        at.position in claim.positions && sameThing(claim, anchor)
    }
    if (stillHere) return at to StepHealth.LIVE

    val moved = view.players.flatMap { seat ->
        seat.claims.mapNotNull { claim ->
            claim.positions.singleOrNull()
                ?.takeIf { sameThing(claim, anchor) }
                ?.let { position -> CardAt(seat.id, position, anchor) }
        }
    }

    return if (moved.size == 1) {
        moved.single() to StepHealth.REANCHORED
    } else {
        at to StepHealth.BROKEN
    }
}

private fun sameThing(claim: Claim, other: Claim): Boolean =
    claim.by == other.by && claim.ranks == other.ranks

private fun worseOf(a: StepHealth, b: StepHealth): StepHealth =
    listOf(a, b).maxBy { it.ordinal }

/**
 * A broken step takes its dependents with it.
 *
 * Only its dependents: a later lane that names a card the broken step would have moved is
 * standing on the same nothing. One that touches different cards is untouched, because
 * marking the whole rest of a plan broken would tell a coalition to start again when most of
 * what they agreed still holds.
 */
private fun spreadBreakage(
    lanes: List<game.vinto.shapes.Lane>,
    health: List<StepHealth>,
): List<StepHealth> {
    val poisoned = mutableSetOf<CardAt>()
    return lanes.mapIndexed { index, lane ->
        val touched = (lane.step as? Step.Swap)?.let { setOf(it.from, it.to) }.orEmpty()
        when {
            health[index] == StepHealth.BROKEN -> {
                poisoned += touched
                StepHealth.BROKEN
            }

            touched.any { it in poisoned } -> {
                StepHealth.BROKEN
            }

            else -> {
                health[index]
            }
        }
    }
}
