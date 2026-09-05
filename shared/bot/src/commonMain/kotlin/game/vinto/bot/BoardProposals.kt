package game.vinto.bot

import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlanEditOutcome
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.believedAt
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.edited
import game.vinto.shapes.laneOf

/**
 * What the bots put on the board (design D7a, task 2.5).
 *
 * A bot cannot read a move for a teammate's turn off its search: the search plans *as* a seat,
 * with that seat's read cards as ground truth, and planning as a teammate would read their
 * private cards — the back channel the claim model closed (D3, D5). What a bot *can* say about a
 * teammate's turn is a **card movement** computed from the shared picture: which two cards
 * should trade places so the coalition's lowest hand gets lower. That is exactly a lane's
 * [Step.Swap], so the board is where a bot's proposals go — where a person reads them, agrees
 * to them, or changes them.
 *
 * Three rules keep this a proposal and not a takeover:
 *
 *  - bots **seed** the board and never fight over it. They fill lanes that are empty, and stop
 *    the moment a person has edited anything — a cleared lane stays clear, a replaced step
 *    stays replaced. Nothing here can loop, because every edit fills a lane and nothing ever
 *    empties one;
 *  - a bot proposes for its **own** lane from its own picture, since it knows its own cards,
 *    and the first bot in turn order proposes for a person's lane from the shared picture;
 *  - only while a **person** is in the coalition. Three bots need no board to agree among
 *    themselves; the board is for talking to somebody.
 *
 * The line is greedy, one lane at a time in turn order, each swap evaluated on the hands the
 * earlier lanes would leave — the same concentration play `CoalitionSearch` plays, said out loud
 * a lane at a time instead of searched in private. It is evaluated on **one** picture: every
 * bot's own hand as that bot knows it, every person's hand as the table has been told it. Two
 * pictures — each proposer's own — made the second lane undo the first, since a card one bot
 * knows is a placeholder to the other and the same trade reads as a gain from both sides.
 */
data class Seeded(val plan: CoalitionPlan, val said: List<TableTalk>)

fun seedTheBoard(state: GameState, plan: CoalitionPlan?): Seeded {
    val untouched = Seeded(plan ?: CoalitionPlan(), emptyList())
    val proposing = proposers(state, plan) ?: return untouched
    val picture = pooledPicture(state, proposing.coalition, proposing.bots) ?: return untouched

    var working = plan ?: CoalitionPlan()
    val said = mutableListOf<TableTalk>()
    for (seat in proposing.coalition) {
        val lane = working.laneOf(seat)
        if (lane?.step != null || lane?.locked == true || seat == proposing.onPlay) continue
        val answered = proposing.proposeFor(seat, working, picture) ?: continue
        working = answered.plan
        answered.said?.let { said += it }
    }
    return Seeded(working, said)
}

/** Who is proposing to whom: the coalition in turn order, the bots in it, and the seat on play. */
private class Proposers(
    val state: GameState,
    val coalition: List<String>,
    val bots: List<String>,
    val onPlay: String?,
) {
    /** One lane's proposal — the best trade left on the board — answered by every bot. */
    fun proposeFor(
        seat: String,
        working: CoalitionPlan,
        picture: Map<String, List<PlanCard>>,
    ): PlanAnswers? {
        val proposer = if (seat in bots) seat else bots.first()
        val swap = bestSwap(picture.after(working, coalition), coalition) ?: return null
        val edit = PlanEdit.SetLane(seat, Step.Swap(cardAt(state, swap.from), cardAt(state, swap.to)))
        val outcome = working.edited(edit, proposer, coalition, onPlay) as? PlanEditOutcome.Edited
            ?: return null
        return botsAnswering(state, outcome.plan, edit, proposer, bots)
    }
}

/**
 * Null when the bots have nothing to propose about: no final round, no bot in the coalition,
 * nobody but bots in it, or a person who has already edited the board.
 */
private fun proposers(state: GameState, plan: CoalitionPlan?): Proposers? {
    val caller = state.vintoCallerId ?: return null
    if (state.phase != GamePhase.FINAL) return null
    val coalition = coalitionInTurnOrder(state.players.map { it.id }, caller)
    val bots = coalition.filter { id -> state.players.first { it.id == id }.isBot }
    if (bots.isEmpty() || bots.size == coalition.size) return null
    // A person has been here: the board is theirs now, and the bots only answer.
    if (plan?.editedBy != null && plan.editedBy !in bots) return null
    return Proposers(state, coalition, bots, state.players.getOrNull(state.currentPlayerIndex)?.id)
}

/**
 * The coalition's hands as the bots know them between them: each bot's own cards as ground
 * truth, everybody else's as the table has been told. What the bots are about to say out loud
 * anyway, pooled before they have.
 */
private fun pooledPicture(
    state: GameState,
    coalition: List<String>,
    bots: List<String>,
): Map<String, List<PlanCard>>? {
    val shared = buildCoalitionPlanInput(state, bots.first()) ?: return null
    val hands = shared.members.associate { it.id to it.cards }.toMutableMap()
    for (bot in bots.drop(1)) {
        val own = buildCoalitionPlanInput(state, bot)?.members?.firstOrNull { it.id == bot } ?: continue
        hands[bot] = own.cards
    }
    return hands.filterKeys { it in coalition }
}

/** A card named by seat and slot, as the planner's hands address it. */
internal data class Slot(val seat: String, val position: Int)

/** Two cards to trade, and the coalition's lowest hand once they have. */
internal data class SwapIdea(val from: Slot, val to: Slot, val minAfter: Int)

/**
 * The trade that lowers the coalition's lowest hand the most, or null when none does.
 *
 * Every pair of cards across two different hands is tried — a Jack and a Queen both swap across
 * players — and the round is scored on the lowest hand, so that is the measure. Two unknown
 * cards trading places change nothing and are never proposed. Ties fall to the first found, in
 * turn order, so two bots looking at the same picture propose the same trade.
 */
internal fun bestSwap(hands: Map<String, List<PlanCard>>, among: List<String>): SwapIdea? {
    val before = minScore(hands.values.toList())
    return trades(hands, among)
        .map { (from, to) -> SwapIdea(from, to, minScore(hands.swapped(from, to).values.toList())) }
        .filter { it.minAfter < before }
        .minByOrNull { it.minAfter }
}

/** Every pair of cards across two different hands, in turn order — the order ties fall to. */
private fun trades(hands: Map<String, List<PlanCard>>, among: List<String>): Sequence<Pair<Slot, Slot>> =
    among.asSequence().withIndex().flatMap { (a, seatA) ->
        among.asSequence().drop(a + 1).flatMap { seatB ->
            val handA = hands[seatA].orEmpty()
            val handB = hands[seatB].orEmpty()
            handA.indices.asSequence().flatMap { i ->
                handB.indices.asSequence().map { j -> Slot(seatA, i) to Slot(seatB, j) }
            }
        }
    }

/** The hands after the steps already on the board, so a later lane builds on the earlier ones. */
private fun Map<String, List<PlanCard>>.after(
    plan: CoalitionPlan,
    coalition: List<String>,
): Map<String, List<PlanCard>> {
    var hands = this
    for (seat in coalition) {
        val step = plan.laneOf(seat)?.step as? Step.Swap ?: continue
        hands = hands.swapped(Slot(step.from.seat, step.from.position), Slot(step.to.seat, step.to.position))
    }
    return hands
}

internal fun Map<String, List<PlanCard>>.swapped(from: Slot, to: Slot): Map<String, List<PlanCard>> {
    val a = this[from.seat]?.getOrNull(from.position) ?: return this
    val b = this[to.seat]?.getOrNull(to.position) ?: return this
    return mapValues { (seat, hand) ->
        hand.mapIndexed { position, card ->
            when {
                seat == from.seat && position == from.position -> b
                seat == to.seat && position == to.position -> a
                else -> card
            }
        }
    }
}

/**
 * A card for a step, anchored to what the table has said about it so the step can follow the
 * card through a watched swap (design D9). A bot's own unspoken card has no anchor, correctly:
 * the table has not been told what it is.
 */
private fun cardAt(state: GameState, slot: Slot): CardAt {
    val owner = state.players.first { it.id == slot.seat }
    return CardAt(slot.seat, slot.position, believedAt(owner, slot.position).sources.firstOrNull())
}
