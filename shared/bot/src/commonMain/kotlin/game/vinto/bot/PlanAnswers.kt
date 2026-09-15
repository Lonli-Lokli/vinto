package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameState
import game.vinto.shapes.Lane
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.TossIn
import game.vinto.shapes.agreeing
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.getCardValue
import game.vinto.shapes.laneOf
import game.vinto.shapes.suggesting

/**
 * What the bots make of the coalition's shared plan (design D7a).
 *
 * A bot answers **for its own lane and nothing else**. The plan is agreed as a whole, but the
 * only part a bot can honestly judge is the turn it will play itself: judging a teammate's lane
 * would mean planning with that teammate's cards, which is the back channel the claim model
 * closed (D3). So each bot reads its own turn against the shared picture — every claim on the
 * table plus its own read cards — and says yes where the turn leaves the coalition's lowest
 * hand no worse, which is the same "within reach" test [BotRunner.answerTo] applies to a single
 * proposal. A bot's no is propose-never-command (D5) working, not a defect.
 *
 * A turn is its step **and** the throw-ins said on it, each with what its card does, so a bot
 * prices the whole of what its turn sets off — a teammate's promised five leaving their hand
 * counts for the bot's turn exactly as it counts for the round.
 */

/** The coalition's hands as a bot prices them, by seat. */
private typealias Priced = Map<String, List<PlanCard>>

/** The plan with the bots' agreement recomputed, and the one sentence a bot had about it. */
data class PlanAnswers(val plan: CoalitionPlan, val said: TableTalk?)

/**
 * Every bot in the coalition answers for its own lane after [edit], and the plan carries the
 * result: a bot that agrees is in [CoalitionPlan.agreed], one that does not is not.
 *
 * Only the bot whose turn the edit **changed** speaks. The others re-answer in silence —
 * agreement resets on every edit, so their yes has to be recorded again, but three bots saying
 * "yes" every time somebody touches a lane is a strip nobody reads. Clearing a lane is a silent
 * yes too: nothing is being asked.
 */
fun botsAnswering(
    state: GameState,
    plan: CoalitionPlan,
    edit: PlanEdit,
    editor: String,
    bots: Collection<String>,
): PlanAnswers {
    var answered = plan
    var said: TableTalk? = null
    for (bot in bots) {
        val answer = answerForTurn(state, bot, plan.laneOf(bot), askedBy = editor)
        val agrees = answer.says == TableTalk.Answer.Says.YES
        answered = answered.agreeing(bot, agree = agrees)
        val changedMine = when (edit) {
            is PlanEdit.SetLane -> edit.seat == bot
            is PlanEdit.SetTossIns -> edit.seat == bot
            is PlanEdit.OpenLane, is PlanEdit.ClearLane, is PlanEdit.AddShed, is PlanEdit.RemoveShed -> false
        }
        if (changedMine) {
            said = answer
            // As a person would (3.13): a no comes with what the bot would rather do, set
            // beside the step for somebody to put on the board — never written over it.
            if (!agrees && editor !in bots && edit is PlanEdit.SetLane) {
                answered = answered.suggesting(bot, ownLaneAlternative(state, bot, edit.step))
            }
        }
    }
    return PlanAnswers(answered, said)
}

/**
 * What a bot would rather do on its own turn, from its own picture — a Jack or a Queen it
 * holds, put down, called, and the trade it then makes: the same rules-true play it seeds the
 * board with — or nothing when no such play helps, or when it is the step it was just offered.
 */
private fun ownLaneAlternative(state: GameState, bot: String, offered: Step): Step? {
    val caller = state.vintoCallerId ?: return null
    val input = buildCoalitionPlanInput(state, bot) ?: return null
    val hands = input.members.associate { it.id to it.cards }
    val coalition = coalitionInTurnOrder(state.players.map { it.id }, caller)
    val hand = hands[bot].orEmpty()
    val before = minScore(hands.values.toList())
    val best = hand.indices
        .filter { hand[it].rankKnown && (hand[it].rank == Rank.JACK || hand[it].rank == Rank.QUEEN) }
        .mapNotNull { position ->
            val slot = Slot(bot, position)
            bestSwap(hands.puttingDown(slot), coalition, excluding = setOf(slot))?.let { slot to it }
        }
        .minByOrNull { (_, swap) -> swap.minAfter }
        ?.takeIf { (_, swap) -> swap.minAfter < before }
        ?: return null
    val (slot, swap) = best
    val rather = Step.PutDown(
        cardAt(state, slot),
        guess = hand[slot.position].rank,
        then = Step.Swap(cardAt(state, swap.from), cardAt(state, swap.to)),
    )
    return rather.takeIf { it != offered }
}

/**
 * One bot's answer for [step] as its own turn, with nothing thrown in on it. See [answerForTurn].
 */
fun answerForLane(state: GameState, seat: String, step: Step?, askedBy: String): TableTalk.Answer =
    answerForTurn(state, seat, step?.let { Lane(seat, it) }, askedBy)

/**
 * One bot's answer for [lane] as its own turn: yes, no, or "that leaves us worse".
 *
 * Measured on the thing the round is scored on — the lowest coalition hand — over the shared
 * picture. The bot's own cards are ground truth to it; everybody else's are what the table has
 * been told. An empty lane is a yes: nothing is being asked. A step that cannot be read at
 * all — a position off the end of a hand, a discard that is not an action card, a throw by a
 * seat the table has never heard holds the rank — is a no rather than a shrug, because a plan
 * built on it would be built on nothing.
 */
fun answerForTurn(state: GameState, seat: String, lane: Lane?, askedBy: String): TableTalk.Answer {
    fun says(what: TableTalk.Answer.Says) = TableTalk.Answer(seat, askedBy, what)
    if (lane == null) return says(TableTalk.Answer.Says.YES)
    if (lane.step == null && lane.tossIns.isEmpty()) return says(TableTalk.Answer.Says.YES)

    val input = buildCoalitionPlanInput(state, seat) ?: return says(TableTalk.Answer.Says.NO)
    val hands = input.members.associate { it.id to it.cards }
    val before = minScore(hands.values.toList())
    val takeable = isTakeableAction(input.discardTop)

    val step = lane.step
    var after: Priced? = if (step == null) hands else hands.afterStep(step, takeable)
    for (tossIn in lane.tossIns) after = after?.afterThrow(tossIn)
    val lowest = after?.let { minScore(it.values.toList()) }

    return when {
        lowest == null -> says(TableTalk.Answer.Says.NO)
        lowest <= before -> says(TableTalk.Answer.Says.YES)
        else -> says(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE)
    }
}

/**
 * The hands once [step] has happened, or null for a step that cannot be read.
 *
 * @param takeable whether the discard holds an action card a turn could take.
 */
private fun Priced.afterStep(step: Step, takeable: Boolean): Priced? =
    when (step) {
        is Step.Swap -> {
            val from = Slot(step.from.seat, step.from.position)
            val to = Slot(step.to.seat, step.to.position)
            if (holds(from) && holds(to)) swapped(from, to) else null
        }

        is Step.Declare -> {
            declaring(step)
        }

        Step.TakeTheDiscard -> {
            this.takeIf { takeable }
        }

        is Step.PutDown -> {
            val slot = Slot(step.card.seat, step.card.position)
            // The call's action first, by the positions the plan names: the put-down takes a
            // card out of a hand and shifts the ones after it, and the trade never names the
            // card put down (the door holds that), so nothing but the addresses would change.
            val acted = step.then?.let { acting(it) }
            when {
                !holds(slot) -> null
                step.then != null && acted == null -> null
                else -> (acted ?: this).puttingDown(slot)
            }
        }

        // Letting the card go touches nothing, so the hands are what they were. A yes — which
        // is right: "don't touch your hand" is a real instruction to the seat holding the best
        // one, and a bot asked to leave its hand alone has no grounds to object. A look moves
        // nothing either; a card nobody has seen cannot be priced and is priced as no change.
        Step.Bin, Step.UseIt, is Step.Peek -> {
            this
        }

        // An Ace lengthens the hand it names by a card nobody has seen.
        is Step.ForceDraw -> {
            drawing(step.seat)
        }
    }

/**
 * A King: the card it points at leaves its hand — its own action first, by the positions the
 * plan names — or, for the loose King that names no card, every card known to be the rank.
 * Null where the pointed-at card is not there, which is a step nobody can read.
 */
private fun Priced.declaring(step: Step.Declare): Priced? {
    val pointed = step.card?.let { Slot(it.seat, it.position) }
    if (pointed == null) {
        // A King that has named nothing and pointed at nothing has done nothing yet.
        val rank = step.rank ?: return this
        return mapValues { (_, hand) -> hand.filterNot { it.rankKnown && it.rank == rank } }
    }
    if (!holds(pointed)) return null
    val acted = step.then?.let { acting(it) ?: return null } ?: this
    return acted.mapValues { (seat, hand) ->
        if (seat == pointed.seat) hand.filterIndexed { position, _ -> position != pointed.position } else hand
    }
}

/**
 * A throw-in: a card the thrower is known to hold leaves their hand, and what it does is
 * played. Null where the table has no grounds for the throw — nobody said the seat holds one.
 *
 * A **blind** throw is priced as its likely outcome: the card stays, and a penalty card nobody
 * has seen lands beside it. A bot never proposes one, and prices one honestly when a person
 * plans it.
 */
private fun Priced.afterThrow(tossIn: TossIn): Priced? {
    val hand = this[tossIn.seat] ?: return null
    val rank = tossIn.rank
    if (rank == null) {
        val card = tossIn.card ?: return null
        return if (holds(Slot(card.seat, card.position))) drawing(tossIn.seat) else null
    }
    val position = tossIn.card?.position?.takeIf {
        it in hand.indices && hand[it].rankKnown && hand[it].rank == rank
    }
        ?: hand.indexOfFirst { it.rankKnown && it.rank == rank }
    if (position < 0) return null
    val acted = tossIn.then?.let { acting(it) ?: return null } ?: this
    return acted.mapValues { (seat, cards) ->
        if (seat == tossIn.seat) cards.filterIndexed { at, _ -> at != position } else cards
    }
}

/**
 * The hands once a played card has done what the plan says it does: a Jack's or a Queen's
 * trade, a King's declare, a look, an Ace's forced draw. Null where the trade names a card that
 * is not there, which is a step nobody can read.
 */
private fun Priced.acting(then: Step): Priced? = when (then) {
    is Step.Swap -> {
        val from = Slot(then.from.seat, then.from.position)
        val to = Slot(then.to.seat, then.to.position)
        if (holds(from) && holds(to)) swapped(from, to) else null
    }

    is Step.Declare -> {
        declaring(then)
    }

    is Step.Peek -> {
        this
    }

    is Step.ForceDraw -> {
        drawing(then.seat)
    }

    // The door lets a played card do nothing else; a step that says otherwise is unreadable.
    is Step.PutDown, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> {
        null
    }
}

/** The hand of [seat] with a card nobody has seen on the end of it. */
private fun Priced.drawing(seat: String): Priced = mapValues { (id, hand) ->
    if (id == seat) {
        hand + PlanCard(
            id = "drawn-$seat-${hand.size}",
            rank = Rank.SEVEN,
            value = UNSEEN_CARD_VALUE,
            played = false,
            rankKnown = false,
        )
    } else {
        hand
    }
}

/** The deck's mean value, which is what a card nobody has seen is worth to a plan. */
internal val UNSEEN_CARD_VALUE = ALL_RANKS.sumOf(::getCardValue) / ALL_RANKS.size

/** Whether the hands have a card at [slot] at all: a step naming a card that is not there is unreadable. */
internal fun Priced.holds(slot: Slot): Boolean =
    this[slot.seat]?.getOrNull(slot.position) != null
