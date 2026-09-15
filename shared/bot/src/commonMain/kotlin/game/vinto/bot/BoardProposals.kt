package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Opening
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlanEditOutcome
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.TossIn
import game.vinto.shapes.believedAt
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.edited
import game.vinto.shapes.laneOf

/**
 * What the bots put on the board (design D7a, task 2.5).
 *
 * A bot cannot read a move for a teammate's turn off its search: the search plans *as* a seat,
 * with that seat's read cards as ground truth, and planning as a teammate would read their
 * private cards — the back channel the claim model closed (D3, D5). What a bot *can* say about
 * a teammate's turn is a **card movement** computed from the shared picture: which two cards
 * should trade places so the coalition's lowest hand gets lower.
 *
 * **And it can only say it the way the rules let it happen.** A plan can speak only about cards
 * the table can see or has been told about, so a trade needs a Jack or a Queen the table knows
 * of: one the seat is known to hold, put down and called; or the unplayed one lying on the pile,
 * taken on the round's first turn, which is the one turn whose pile is known. *"Draws, plays it,
 * trades…"* was what the bots used to seed, and no table can say it — nobody knows the drawn
 * card. A seat with nothing of the kind gets no proposal: its turn reads "draws, and we'll see",
 * which is the truth.
 *
 * **A King is the other thing a table can say, and usually the bigger one.** A trade moves points
 * between two hands; a correct declaration takes a card *out of the game*, and hands its action
 * to whoever played the King — so a King that names a Jack or a Queen is a card off the table and
 * a trade, which is the strongest single turn of the round. It reaches any card the table can
 * name, in any hand including its own, and the bots cost it against the same lowest hand a trade
 * is costed against, because it is not free: the King's own slot takes the draw.
 *
 * **No proposal ever names an ace.** Its action makes somebody draw, and from the call onwards
 * the caller's hand is out of reach — so the only seats an ace can reach are the coalition's own,
 * and all it can do is lengthen a hand the coalition is trying to keep short. That holds whether
 * the ace is the card a King would point at or the card a turn would play, and it is the same
 * answer [BotRunner] gives an ace it draws in the final round: swap it in, or put it down, but
 * never play it.
 *
 * Three rules keep this a proposal and not a takeover:
 *
 *  - bots **seed** the board and never fight over it. They fill lanes that are empty, and stop
 *    the moment a person has edited anything;
 *  - a bot proposes for its **own** lane from its own picture, and the first bot in turn order
 *    proposes for a person's lane from the shared picture;
 *  - only while a **person** is at the table to read it.
 *
 * A put-down lands a card of a known rank, and a teammate known to hold that rank is proposed
 * to throw it in — a vouched throw, never a blind one: a bot does not gamble a teammate's
 * penalty card.
 */
data class Seeded(val plan: CoalitionPlan, val said: List<TableTalk>)

fun seedTheBoard(state: GameState, plan: CoalitionPlan?): Seeded {
    val untouched = Seeded(plan ?: CoalitionPlan(), emptyList())
    val proposing = proposers(state, plan) ?: return untouched
    val picture = pooledPicture(state, proposing.coalition, proposing.bots) ?: return untouched

    var working = plan ?: CoalitionPlan()
    val said = mutableListOf<TableTalk>()
    for ((index, seat) in proposing.coalition.withIndex()) {
        val lane = working.laneOf(seat)
        if (lane?.step != null || lane?.locked == true || seat == proposing.onPlay) continue
        val answered = proposing.proposeFor(seat, index, working, picture) ?: continue
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
    /** Which coalition turn is being played; -1 while play is still parked on the caller. */
    private val playing: Int = coalition.indexOf(onPlay)

    /** One lane's proposal, answered by every bot: a called Jack, Queen or King, or the pile's. */
    fun proposeFor(
        seat: String,
        index: Int,
        working: CoalitionPlan,
        picture: Map<String, List<PlanCard>>,
    ): PlanAnswers? {
        val proposer = if (seat in bots) seat else bots.first()
        val hands = picture.after(working, coalition)
        val edits = bestTurn(seat, index, hands)?.edits ?: return null

        var plan = working
        var answers: PlanAnswers? = null
        for (edit in edits) {
            val outcome = plan.edited(edit, proposer, coalition, onPlay) as? PlanEditOutcome.Edited
                ?: return null
            answers = botsAnswering(state, outcome.plan, edit, proposer, bots)
            plan = answers.plan
        }
        return answers
    }

    /**
     * The best of the turns this table could actually say, on the one number the round is
     * scored on.
     *
     * A trade moves points between two hands; a King takes a card **off the table**, which no
     * trade can do and which is usually worth more. Both are costed the same way and the lower
     * wins, so a King is not preferred for being dramatic — ties fall to the trade, which is
     * the older proposal and the cheaper turn.
     */
    private fun bestTurn(seat: String, index: Int, hands: Map<String, List<PlanCard>>): Idea? =
        listOfNotNull(
            calledTrade(seat, hands),
            calledKing(seat, hands),
            takenTrade(seat, index, hands),
            takenKing(seat, index, hands),
        ).minByOrNull { it.minAfter }

    /**
     * A Jack or a Queen the seat is known to hold, put down and called, and the trade it then
     * makes — with the teammates known to hold its rank throwing theirs in after it.
     */
    private fun calledTrade(seat: String, hands: Map<String, List<PlanCard>>): Idea? {
        val hand = hands[seat].orEmpty()
        val held = hand.indices.filter { hand[it].rankKnown && hand[it].rank.trades() }
        val best = held.mapNotNull { position ->
            val slot = Slot(seat, position)
            bestSwap(hands.puttingDown(slot), coalition, excluding = setOf(slot))?.let { slot to it }
        }.minByOrNull { (_, swap) -> swap.minAfter } ?: return null
        val (slot, swap) = best
        if (swap.minAfter >= minScore(hands.values.toList())) return null
        val rank = hand[slot.position].rank
        val step = Step.PutDown(
            cardAt(state, slot),
            guess = rank,
            then = Step.Swap(cardAt(state, swap.from), cardAt(state, swap.to)),
        )
        return Idea(
            swap.minAfter,
            listOfNotNull(PlanEdit.SetLane(seat, step), throwsOn(seat, setOf(rank), gone = setOf(slot))),
        )
    }

    /**
     * A King the seat is known to hold, put down and called, pointing at the card the table can
     * name whose leaving helps most — and then everybody holding a King or that rank throws
     * theirs in on it.
     *
     * The turn a coalition wants and a trade cannot buy: a correct declaration takes the named
     * card **out of the game** rather than moving it to a hand that is not being compared. It is
     * costed against the same lowest hand as a trade, because it is not free — the King's own
     * slot takes the draw, and a King is worth nothing to hold but five on average to replace.
     *
     * And it says what the named card then *does*, where that is a trade: a Jack or a Queen
     * leaving a hand hands its action to whoever played the King, and a lane that stopped at the
     * removal would understate its own turn by the better part of the round. See [bestDeclare]
     * for what may be named, and why an ace never is.
     */
    private fun calledKing(seat: String, hands: Map<String, List<PlanCard>>): Idea? {
        val hand = hands[seat].orEmpty()
        val held = hand.indices.filter { hand[it].rankKnown && hand[it].rank == Rank.KING }
        val best = held.mapNotNull { position ->
            val slot = Slot(seat, position)
            bestDeclare(hands.puttingDown(slot), coalition, excluding = slot)?.let { slot to it }
        }.minByOrNull { (_, declare) -> declare.minAfter } ?: return null
        val (slot, declare) = best
        if (declare.minAfter >= minScore(hands.values.toList())) return null
        val step = Step.PutDown(cardAt(state, slot), guess = Rank.KING, then = declaring(declare))
        return Idea(
            declare.minAfter,
            listOfNotNull(
                PlanEdit.SetLane(seat, step),
                // The engine opens the window on both ranks, and neither card is in a hand to
                // throw by then: the King is on the pile and the named card has left the game.
                throwsOn(seat, setOf(Rank.KING, declare.rank), gone = setOf(slot, declare.at)),
            ),
        )
    }

    /**
     * The pile's unplayed Jack or Queen, taken and played — only on the round's first turn,
     * which is the one turn whose pile is known.
     */
    private fun takenTrade(seat: String, index: Int, hands: Map<String, List<PlanCard>>): Idea? {
        val top = takeableTop(index) ?: return null
        if (!top.rank.trades()) return null
        val swap = bestSwap(hands, coalition) ?: return null
        return Idea(
            swap.minAfter,
            listOf(
                PlanEdit.OpenLane(seat, Opening.TAKE_THE_DISCARD),
                PlanEdit.SetLane(seat, Step.Swap(cardAt(state, swap.from), cardAt(state, swap.to))),
            ),
        )
    }

    /**
     * The pile's unplayed King, taken and played — the same one turn as [takenTrade], and the
     * cheapest declaration there is, since nothing has to leave the taker's hand to buy it.
     */
    private fun takenKing(seat: String, index: Int, hands: Map<String, List<PlanCard>>): Idea? {
        val top = takeableTop(index) ?: return null
        if (top.rank != Rank.KING) return null
        val declare = bestDeclare(hands, coalition) ?: return null
        return Idea(
            declare.minAfter,
            listOf(
                PlanEdit.OpenLane(seat, Opening.TAKE_THE_DISCARD),
                PlanEdit.SetLane(seat, declaring(declare)),
            ),
        )
    }

    /**
     * The card on the pile a turn could take, on the round's first turn only — which is the one
     * turn whose pile is known, since every later one opens on whatever the turn before left.
     */
    private fun takeableTop(index: Int): Card? {
        if (index != 0 || playing >= 0) return null
        return state.discardPile.cards.lastOrNull()?.takeIf { !it.played }
    }

    /** A King's declaration: the card it points at, and what that card's action then does. */
    private fun declaring(idea: DeclareIdea) = Step.Declare(
        idea.rank,
        cardAt(state, idea.at),
        then = idea.then?.let { Step.Swap(cardAt(state, it.from), cardAt(state, it.to)) },
    )

    /**
     * Every teammate the table knows to hold one of [ranks], throwing it in after [seat]'s card
     * lands — a vouched throw, never a blind one. [gone] are the cards that will not be in a
     * hand to throw by then: the one put down, and the one a King took out of the game.
     */
    private fun throwsOn(seat: String, ranks: Set<Rank>, gone: Set<Slot>): PlanEdit? {
        val throws = coalition.flatMap { thrower ->
            val owner = state.players.first { it.id == thrower }
            owner.cards.indices
                .filter { position -> Slot(thrower, position) !in gone }
                .mapNotNull { position ->
                    val believed = believedAt(owner, position)
                    val rank = believed.candidates.singleOrNull()
                        ?.takeIf { believed.sources.isNotEmpty() && it in ranks }
                    rank?.let { TossIn(thrower, it, card = cardAt(state, Slot(thrower, position))) }
                }
        }
        return PlanEdit.SetTossIns(seat, throws).takeIf { throws.isNotEmpty() }
    }
}

/** One turn a bot could propose, and the coalition's lowest hand once it has been played. */
private class Idea(val minAfter: Int, val edits: List<PlanEdit>)

/** The two ranks whose action is a trade. */
private fun Rank.trades(): Boolean = this == Rank.JACK || this == Rank.QUEEN

/**
 * Null when the bots have nothing to propose about: no final round, no bot in the coalition, no
 * person at the table to read the board, or a person who has already edited it.
 *
 * **A coalition of nothing but bots still writes its plan down**: the plan is public and the
 * caller may read it (design D12), and it is the whole tension of the round they just started.
 */
private fun proposers(state: GameState, plan: CoalitionPlan?): Proposers? {
    val caller = state.vintoCallerId ?: return null
    if (state.phase != GamePhase.FINAL) return null
    val coalition = coalitionInTurnOrder(state.players.map { it.id }, caller)
    val bots = coalition.filter { id -> state.players.first { it.id == id }.isBot }
    if (bots.isEmpty()) return null
    // Somebody has to be reading it. Four bots and nobody watching is a solver talking to itself.
    if (state.players.none { it.isHuman }) return null
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
 *
 * @param excluding cards that are not there to trade: the one put down, on the pile by then,
 *   and the one a King has named, which by then has left the game.
 */
internal fun bestSwap(
    hands: Map<String, List<PlanCard>>,
    among: List<String>,
    excluding: Set<Slot> = emptySet(),
): SwapIdea? {
    val before = minScore(hands.values.toList())
    return trades(hands, among)
        .filter { (from, to) -> from !in excluding && to !in excluding }
        .map { (from, to) -> SwapIdea(from, to, minScore(hands.swapped(from, to).values.toList())) }
        .filter { it.minAfter < before }
        .minByOrNull { it.minAfter }
}

/**
 * A card a King could name, what that card's own action then does, and the coalition's lowest
 * hand once both have happened.
 */
internal data class DeclareIdea(
    val at: Slot,
    val rank: Rank,
    val then: SwapIdea?,
    val minAfter: Int,
)

/**
 * The card a King should point at, or null when naming one helps nobody.
 *
 * Only a card the table can **name** is a candidate: a King that guesses costs its player a
 * penalty card and shows the card it guessed at, and a plan that gambles a teammate's turn is
 * not a plan. Everything else falls out of the measure — a Joker leaving a hand raises it, a
 * King leaving one moves it by nothing, and neither survives `minAfter < before`.
 *
 * **An ace is never named**, and that is a rule rather than an arithmetic. A correct
 * declaration hands the named card's action to the King's player, and an ace's action is to
 * make somebody draw — in a final round the caller is out of reach, so the only seats it can
 * reach are the coalition's own. There is no good victim, so there is nothing to plan: the ace
 * is worth one point and taking it off a teammate was never the win it looks like.
 *
 * @param excluding a card that is not there to name: the King's own slot, which holds the card
 *   drawn into it by then — and which the door refuses a call to point back at.
 */
internal fun bestDeclare(
    hands: Map<String, List<PlanCard>>,
    among: List<String>,
    excluding: Slot? = null,
): DeclareIdea? {
    val before = minScore(hands.values.toList())
    return among.asSequence()
        .flatMap { seat -> hands[seat].orEmpty().indices.asSequence().map { Slot(seat, it) } }
        .filter { it != excluding }
        .mapNotNull { slot -> hands.naming(slot, among, excluding) }
        .filter { it.minAfter < before }
        .minByOrNull { it.minAfter }
}

/**
 * What naming the card at [slot] is worth, or null for a card a King may not name.
 *
 * A named Jack or Queen does not merely leave the hand: its action is the King's player's to
 * play, and it is the strongest turn in the round — a card off the table *and* a trade. The
 * trade is addressed in the positions the table can see **now**, before the named card is
 * removed, which is the order a lane is read in (`Priced.declaring`) and the only order in
 * which a plan's addresses mean anything to somebody looking at the felt.
 */
private fun Map<String, List<PlanCard>>.naming(
    slot: Slot,
    among: List<String>,
    excluding: Slot?,
): DeclareIdea? {
    val card = getValue(slot.seat)[slot.position]
    if (!card.rankKnown || card.rank == Rank.ACE) return null
    val gone = setOfNotNull(slot, excluding)
    val trade = if (card.rank.trades()) bestSwap(this, among, excluding = gone) else null
    val traded = trade?.let { swapped(it.from, it.to) } ?: this
    return DeclareIdea(slot, card.rank, trade, minScore(traded.without(slot).values.toList()))
}

/** The hands once the card at [slot] has left the game, which is what a correct King does. */
private fun Map<String, List<PlanCard>>.without(slot: Slot): Map<String, List<PlanCard>> =
    mapValues { (seat, hand) ->
        if (seat == slot.seat) hand.filterIndexed { position, _ -> position != slot.position } else hand
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
        val step = plan.laneOf(seat)?.step ?: continue
        val trade = when (step) {
            is Step.Swap -> {
                step
            }

            is Step.PutDown -> {
                hands = hands.puttingDown(Slot(step.card.seat, step.card.position))
                step.then as? Step.Swap
            }

            else -> {
                null
            }
        } ?: continue
        hands = hands.swapped(
            Slot(trade.from.seat, trade.from.position),
            Slot(trade.to.seat, trade.to.position),
        )
    }
    return hands
}

/** The hands once the card at [slot] is put down: it goes, and a card nobody has seen takes its place. */
internal fun Map<String, List<PlanCard>>.puttingDown(slot: Slot): Map<String, List<PlanCard>> {
    val put = this[slot.seat]?.getOrNull(slot.position) ?: return this
    return mapValues { (seat, hand) ->
        if (seat != slot.seat) {
            hand
        } else {
            hand.mapIndexed { position, card ->
                if (position == slot.position) {
                    put.copy(
                        id = "drawn",
                        value = UNSEEN_CARD_VALUE,
                        rankKnown = false,
                    )
                } else {
                    card
                }
            }
        }
    }
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
internal fun cardAt(state: GameState, slot: Slot): CardAt {
    val owner = state.players.first { it.id == slot.seat }
    return CardAt(slot.seat, slot.position, believedAt(owner, slot.position).sources.firstOrNull())
}
