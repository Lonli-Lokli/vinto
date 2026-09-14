package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Lane
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TossIn
import game.vinto.shapes.cardsNamed

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
 *
 * A turn is as well as the worst thing in it: its own step, and every throw-in said on it —
 * a throw by a seat no longer known to hold the rank stands on nothing, and a thrown card's
 * trade follows its cards and breaks with them like any other.
 */
fun readPlan(view: PlayerView, plan: CoalitionPlan, reveals: List<PublicReveal>): PlanReading {
    val contradicted = reveals.filter { reveal ->
        val seat = view.players.firstOrNull { it.id == reveal.playerId } ?: return@filter false
        val believed = believedOnView(seat, reveal.position)
        believed.sources.isNotEmpty() && reveal.card.rank !in believed.candidates
    }

    val repaired = mutableListOf<Lane>()
    val health = mutableListOf<StepHealth>()

    for (lane in plan.lanes) {
        val step = lane.step?.let { read(view, it, contradicted) }
        val throws = lane.tossIns.map { readThrow(view, it, contradicted) }
        repaired += lane.copy(step = step?.first, tossIns = throws.map { it.first })
        health += (listOfNotNull(step?.second) + throws.map { it.second }).fold(StepHealth.LIVE, ::worseOf)
    }

    return PlanReading(plan.copy(lanes = repaired), spreadBreakage(repaired, health))
}

/**
 * One step, following its cards: where they are now, and how well it stands on them.
 *
 * Recursive for a put-down and a King, because the called card's action — and the pointed-at
 * card's — names cards of its own, and the step is as well as the worst card it names.
 */
private fun read(view: PlayerView, step: Step, contradicted: List<PublicReveal>): Pair<Step, StepHealth> =
    when (step) {
        is Step.Swap -> {
            val from = follow(view, step.from, contradicted)
            val to = follow(view, step.to, contradicted)
            Step.Swap(from.first, to.first) to worseOf(from.second, to.second)
        }

        is Step.Peek -> {
            val card = follow(view, step.card, contradicted)
            val also = step.also?.let { follow(view, it, contradicted) }
            val standing = also?.let { worseOf(card.second, it.second) } ?: card.second
            Step.Peek(card.first, also?.first) to standing
        }

        is Step.Declare -> {
            readKing(view, step, contradicted)
        }

        // None of them names a card, so none has one to follow or to lose. A forced draw
        // names a seat, which is always there.
        Step.TakeTheDiscard, Step.Bin, Step.UseIt, is Step.ForceDraw -> {
            step to StepHealth.LIVE
        }

        is Step.PutDown -> {
            readPutDown(view, step, contradicted)
        }
    }

/**
 * A King, following the card it points at and what that card goes on to do. A rank nobody is
 * believed to hold any more is a King aimed at nothing; a King that has pointed and not yet
 * named stands on the card it pointed at.
 */
private fun readKing(
    view: PlayerView,
    step: Step.Declare,
    contradicted: List<PublicReveal>,
): Pair<Step, StepHealth> {
    val pointed = step.card?.let { follow(view, it, contradicted) }
    val then = step.then?.let { read(view, it, contradicted) }
    val rank = step.rank
    val standing = when {
        pointed != null -> pointed.second
        rank == null || stillHeld(view, rank, contradicted) -> StepHealth.LIVE
        else -> StepHealth.BROKEN
    }
    return step.copy(card = pointed?.first, then = then?.first) to
        (then?.let { worseOf(standing, it.second) } ?: standing)
}

/**
 * A put-down, following its card and what its call goes on to do.
 *
 * **The guess travels with the card.** Rebuilding the step rather than copying it would drop
 * the rank its owner meant to call, silently, every time a step followed a card that moved —
 * and the call's trade with it.
 */
private fun readPutDown(
    view: PlayerView,
    step: Step.PutDown,
    contradicted: List<PublicReveal>,
): Pair<Step, StepHealth> {
    val at = follow(view, step.card, contradicted)
    val then = step.then?.let { read(view, it, contradicted) }
    return step.copy(card = at.first, then = then?.first) to
        (then?.let { worseOf(at.second, it.second) } ?: at.second)
}

/**
 * One throw-in: broken where its seat is no longer known to hold the rank it promised — the
 * claim it rested on has been contradicted, or has gone — and otherwise as well as what the
 * thrown card goes on to do. A blind throw rests on nothing but its card being there, so it
 * stands for as long as the card does.
 */
private fun readThrow(
    view: PlayerView,
    tossIn: TossIn,
    contradicted: List<PublicReveal>,
): Pair<TossIn, StepHealth> {
    val hand = view.players.firstOrNull { it.id == tossIn.seat }
    val card = tossIn.card
    val rank = tossIn.rank
    val held = when {
        hand == null -> false
        card != null ->
            card.position in hand.cards.indices &&
                contradicted.none { it.playerId == card.seat && it.position == card.position } &&
                (rank == null || knownRankOf(view, hand, card.position) == rank)
        rank == null -> false
        else -> hand.cards.indices.any { position ->
            knownRankOf(view, hand, position) == rank &&
                contradicted.none { it.playerId == tossIn.seat && it.position == position }
        }
    }
    val then = tossIn.then?.let { read(view, it, contradicted) }
    val standing = if (held) StepHealth.LIVE else StepHealth.BROKEN
    return tossIn.copy(then = then?.first) to (then?.let { worseOf(standing, it.second) } ?: standing)
}

/** Whether some coalition card is still believed to be [rank], by a claim no reveal has contradicted. */
private fun stillHeld(view: PlayerView, rank: Rank, contradicted: List<PublicReveal>): Boolean =
    view.players.any { seat ->
        seat.id != view.vintoCallerId &&
            seat.cards.indices.any { position ->
                believedOnView(seat, position).candidates == setOf(rank) &&
                    contradicted.none { it.playerId == seat.id && it.position == position }
            }
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

/** Every card a turn touches — its step's, every card thrown, and every thrown card's action's. */
private fun Lane.touches(): Set<CardAt> =
    step?.cardsNamed().orEmpty().toSet() +
        tossIns.flatMap { listOfNotNull(it.card) + it.then?.cardsNamed().orEmpty() }

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
    lanes: List<Lane>,
    health: List<StepHealth>,
): List<StepHealth> {
    val poisoned = mutableSetOf<CardAt>()
    return lanes.mapIndexed { index, lane ->
        val touched = lane.touches()
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
