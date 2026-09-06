package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameState
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.agreeing
import game.vinto.shapes.getCardValue
import game.vinto.shapes.laneOf

/**
 * What the bots make of the coalition's shared plan (design D7a).
 *
 * A bot answers **for its own lane and nothing else**. The plan is agreed as a whole, but the
 * only part a bot can honestly judge is the turn it will play itself: judging a teammate's lane
 * would mean planning with that teammate's cards, which is the back channel the claim model
 * closed (D3). So each bot reads its own step against the shared picture — every claim on the
 * table plus its own read cards — and says yes where the step leaves the coalition's lowest
 * hand no worse, which is the same "within reach" test [BotRunner.answerTo] applies to a single
 * proposal. A bot's no is propose-never-command (D5) working, not a defect.
 */

/** The plan with the bots' agreement recomputed, and the one sentence a bot had about it. */
data class PlanAnswers(val plan: CoalitionPlan, val said: TableTalk?)

/**
 * Every bot in the coalition answers for its own lane after [edit], and the plan carries the
 * result: a bot that agrees is in [CoalitionPlan.agreed], one that does not is not.
 *
 * Only the bot whose lane the edit **set** speaks. The others re-answer in silence — agreement
 * resets on every edit, so their yes has to be recorded again, but three bots saying "yes"
 * every time somebody touches a lane is a strip nobody reads. Clearing a lane is a silent yes
 * too: nothing is being asked.
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
        val answer = answerForLane(state, bot, plan.laneOf(bot)?.step, askedBy = editor)
        answered = answered.agreeing(bot, agree = answer.says == TableTalk.Answer.Says.YES)
        if (edit is PlanEdit.SetLane && edit.seat == bot) said = answer
    }
    return PlanAnswers(answered, said)
}

/**
 * One bot's answer for [step] as its own turn: yes, no, or "that leaves us worse".
 *
 * Measured on the thing the round is scored on — the lowest coalition hand — over the shared
 * picture. The bot's own cards are ground truth to it; everybody else's are what the table has
 * been told. An empty lane is a yes: nothing is being asked. A step that cannot be read at
 * all — a position off the end of a hand, a discard that is not an action card — is a no
 * rather than a shrug, because a plan built on it would be built on nothing.
 */
fun answerForLane(state: GameState, seat: String, step: Step?, askedBy: String): TableTalk.Answer {
    fun says(what: TableTalk.Answer.Says) = TableTalk.Answer(seat, askedBy, what)
    if (step == null) return says(TableTalk.Answer.Says.YES)

    val input = buildCoalitionPlanInput(state, seat) ?: return says(TableTalk.Answer.Says.NO)
    val hands = input.members.associate { it.id to it.cards }
    val before = minScore(hands.values.toList())

    val after = when (step) {
        is Step.Swap -> {
            val from = Slot(step.from.seat, step.from.position)
            val to = Slot(step.to.seat, step.to.position)
            if (hands.holds(from) && hands.holds(to)) {
                minScore(hands.swapped(from, to).values.toList())
            } else {
                null
            }
        }

        is Step.Declare -> {
            minScore(hands.values.map { hand -> hand.filterNot { it.rankKnown && it.rank == step.rank } })
        }

        Step.TakeTheDiscard -> {
            before.takeIf { isTakeableAction(input.discardTop) }
        }

        is Step.PutDown -> {
            val slot = Slot(step.card.seat, step.card.position)
            if (hands.holds(slot)) minScore(hands.puttingDown(slot).values.toList()) else null
        }
    }

    return when {
        after == null -> says(TableTalk.Answer.Says.NO)
        after <= before -> says(TableTalk.Answer.Says.YES)
        else -> says(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE)
    }
}

/**
 * The hands once the card at [slot] is put down: it goes, so does every card whose rank is
 * known to match it — the toss-in the step exists to set up — and the draw that takes its place
 * is a card nobody has seen, priced at the deck's mean.
 */
private fun Map<String, List<PlanCard>>.puttingDown(slot: Slot): Map<String, List<PlanCard>> {
    val put = this[slot.seat]?.getOrNull(slot.position) ?: return this
    val rank = put.rank.takeIf { put.rankKnown }
    return mapValues { (seat, hand) ->
        val kept = hand.filterIndexed { position, card ->
            val isTheCard = seat == slot.seat && position == slot.position
            val matches = rank != null && card.rankKnown && card.rank == rank
            !isTheCard && !matches
        }
        val drawn = put.copy(id = "drawn", value = UNSEEN_CARD_VALUE, rankKnown = false)
        if (seat == slot.seat) kept + drawn else kept
    }
}

/** The deck's mean value, which is what a card nobody has seen is worth to a plan. */
private val UNSEEN_CARD_VALUE = ALL_RANKS.sumOf(::getCardValue) / ALL_RANKS.size

/** Whether the hands have a card at [slot] at all: a step naming a card that is not there is unreadable. */
internal fun Map<String, List<PlanCard>>.holds(slot: Slot): Boolean =
    this[slot.seat]?.getOrNull(slot.position) != null
