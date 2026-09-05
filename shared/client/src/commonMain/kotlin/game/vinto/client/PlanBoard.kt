package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TargetType
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.laneOf

/**
 * The coalition's shared plan, as the rail draws it and as a person edits it (design D7a).
 *
 * Three things live here, and they are one subject: the **board** — one line per coalition
 * turn, the sheds, and who has nodded; the **composer** — how a tap on a lane becomes one
 * `PlanEdit`; and the **pre-arm** — the viewer's own lane surfacing on their turn as the first
 * thing to press, when the draw has made it legal.
 *
 * Everything is built from the view and the standing plan alone. Which edits are *legal* is
 * `CoalitionPlan.edited`'s business, the one door both sessions call; what this decides is only
 * which of them to offer, so a lane the door would refuse — locked, or the seat on play — is
 * simply not tappable rather than tappable and refused.
 */

/** The board: what the coalition intends, laid out for reading and for tapping. */
data class Board(
    /** One per coalition turn still to come, in the order they come. */
    val lanes: List<LaneLine>,
    val sheds: List<ShedLine>,
    /** Every coalition member, and whether they have said yes to the board as it stands. */
    val nods: List<Nod>,
    val editedBy: Speaker?,
)

/**
 * The plan in one line, for the tables that are about something else.
 *
 * The board itself is a **mode** — it takes the rail over the way a claim or a King's rank rail
 * does — because drawn beside the prompt it starved the log strip, and the strip is where the
 * bots' answers land. So every final-round table carries this instead: how much of the board
 * is set, how many have nodded, and one tap to open it.
 */
data class PlanSummary(
    val lanesSet: Int,
    val lanes: Int,
    val agreed: Int,
    /** Whether the viewer has nodded; false for the caller, who never does. */
    val mine: Boolean,
    /** Opening the board. Null for nobody: the caller may read a standing plan too. */
    val open: Move,
)

/**
 * One coalition member's turn on the board.
 *
 * [step] null is "your call", which is a real answer. [move] is what tapping the line does —
 * opening the composer for that seat — and null where nothing may change it: the turn has
 * begun, or the viewer is the caller, who reads the board and edits none of it.
 */
data class LaneLine(val who: Speaker, val step: StepLine?, val locked: Boolean, val move: Move?)

/** A step in words a renderer can put into a sentence. Positions are one-based, as people count. */
sealed interface StepLine {
    data class Swap(val fromWho: Speaker, val fromSlot: Int, val toWho: Speaker, val toSlot: Int) : StepLine
    data class Declare(val rank: Rank) : StepLine
    data object TakeTheDiscard : StepLine
}

data class ShedLine(val who: Speaker, val rank: Rank)

/** One member's yes or not-yet. [away] because a seat a bot is covering nods for itself. */
data class Nod(val who: Speaker, val agreed: Boolean, val away: Boolean)

/** What a lane is being composed as, once the person has said which kind of step. */
enum class StepKind { SWAP, DECLARE }

/**
 * The board for [view]'s seat, or null when there is nothing to draw.
 *
 * A coalition member sees it for the whole final round, empty lanes included — the empty
 * lane is what you tap to start planning. The caller sees it only once something stands on
 * it, and can tap nothing: talk is public (design D12) and the plan is built from public
 * claims, so there is nothing to hide, and nothing for them to change.
 */
internal fun boardFor(view: PlayerView, plan: CoalitionPlan?, away: Set<String>): Board? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null

    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    val onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id
    val agreed = plan?.agreed.orEmpty()

    return Board(
        lanes = coalition.map { seat ->
            val lane = plan?.laneOf(seat)
            val locked = lane?.locked == true
            LaneLine(
                who = speakerFor(view, seat),
                step = lane?.step?.let { stepLine(view, it) },
                locked = locked,
                move = Move.Ask(Question.Planning(seat)).takeIf { member && !locked && seat != onPlay },
            )
        },
        sheds = plan?.sheds.orEmpty().map { ShedLine(speakerFor(view, it.seat), it.rank) },
        nods = coalition.map { Nod(speakerFor(view, it), agreed = it in agreed, away = it in away) },
        editedBy = plan?.editedBy?.let { speakerFor(view, it) },
    )
}

/** The one line every final-round table carries, or null where there is no board to open. */
internal fun summaryFor(view: PlayerView, plan: CoalitionPlan?): PlanSummary? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null

    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    return PlanSummary(
        lanesSet = plan?.lanes.orEmpty().count { it.step != null },
        lanes = coalition.size,
        agreed = plan?.agreed.orEmpty().count { it in coalition },
        mine = view.viewerId in plan?.agreed.orEmpty(),
        open = Move.Ask(Question.ThePlan),
    )
}

/**
 * The board, open: every lane, the sheds, the nods — and, for a member with something to say
 * yes to, "Agree". Back closes it. The log is not drawn under this table (the rail treats a
 * board like a rank grid), which is the price of a board a phone can hold.
 */
internal fun boardTable(view: PlayerView, plan: CoalitionPlan?, away: Set<String>): Table {
    val board = boardFor(view, plan, away) ?: return Table(Ask.Watching, waiting = true)
    val member = view.viewerId != view.vintoCallerId
    val agreeable = member && plan != null && !plan.isEmpty && view.viewerId !in plan.agreed
    return Table(
        prompt = Ask.ThePlan,
        detail = Detail.APlanIsASuggestion,
        choices = buildList {
            if (agreeable) add(Choice(Label.Agree, Move.Agree(true), Tone.PLAY))
            add(Choice(Label.Back, Move.Ask(Question.None)))
        },
        board = board,
    )
}

internal fun stepLine(view: PlayerView, step: Step): StepLine = when (step) {
    is Step.Swap -> StepLine.Swap(
        fromWho = speakerFor(view, step.from.seat),
        fromSlot = step.from.position + 1,
        toWho = speakerFor(view, step.to.seat),
        toSlot = step.to.position + 1,
    )

    is Step.Declare -> StepLine.Declare(step.rank)
    Step.TakeTheDiscard -> StepLine.TakeTheDiscard
}

// ---------------------------------------------------------------------------- the composer

/**
 * Composing one lane, by tapping.
 *
 * Three kinds of step and no others (design D7): a swap, named by tapping two cards from two
 * hands; a King's declaration, named on the rank rail; and taking the unused action card off
 * the pile, offered only while there is one there. Every answer is a single `PlanEdit` — one
 * part, not a whole draft — and the rail's ordinary controls are reused rather than a new
 * widget: the same taps a Jack is aimed with, the same rail a King declares on.
 */
internal fun planningTable(view: PlayerView, question: Question.Planning, plan: CoalitionPlan?): Table {
    val seat = view.players.firstOrNull { it.id == question.seat } ?: return Table(Ask.Watching)
    val who = speakerFor(view, seat.id)

    return when (question.kind) {
        null -> Table(
            prompt = Ask.WhatShouldTheyDo(who),
            detail = Detail.APlanIsASuggestion,
            choices = buildList {
                add(Choice(Label.PlanASwap, Move.Ask(question.copy(kind = StepKind.SWAP))))
                add(Choice(Label.PlanADeclare, Move.Ask(question.copy(kind = StepKind.DECLARE))))
                // Only while there is an unplayed action card to take — the same rule the turn
                // itself applies. A step that could not be done is not worth a button.
                val top = view.discardTop
                if (top != null && top.actionText != null && !top.played) {
                    add(
                        Choice(
                            Label.PlanTakeTheDiscard,
                            Move.Plan(PlanEdit.SetLane(seat.id, Step.TakeTheDiscard)),
                            Tone.PLAY,
                        ),
                    )
                }
                if (plan?.laneOf(seat.id) != null) {
                    add(Choice(Label.ClearLane, Move.Plan(PlanEdit.ClearLane(seat.id))))
                }
                add(Choice(Label.Back, Move.Ask(Question.None)))
            },
        )

        StepKind.SWAP -> swapPlanningTable(view, question)

        StepKind.DECLARE -> Table(
            prompt = Ask.WhichRankShouldTheyDeclare(who),
            detail = Detail.APlanIsASuggestion,
            choices = listOf(Choice(Label.Back, Move.Ask(question.copy(kind = null)))),
            ranks = ALL_RANKS.map { rank ->
                RankChoice(rank, Move.Plan(PlanEdit.SetLane(seat.id, Step.Declare(rank))))
            },
        )
    }
}

/**
 * Two cards from two hands, tapped in turn — the Jack's own gesture, borrowed.
 *
 * The caller's cards are simply not tappable: the coalition may not touch them, and the door
 * would refuse the step anyway (`CoalitionPlan.edited`), but a card that could be tapped and
 * then refused reads as a broken control rather than a rule. Once one card is picked, that
 * hand is out too, because a swap is between two different players. The card already picked
 * is drawn in the rail's aim column, as a Jack's first target is.
 */
private fun swapPlanningTable(view: PlayerView, question: Question.Planning): Table {
    val caller = view.vintoCallerId
    val from = question.from

    val taps = view.players
        .filter { it.id != caller && it.id != from?.playerId }
        .flatMap { hand ->
            hand.cards.indices.map { position ->
                val ref = CardRef(hand.id, position)
                ref to if (from == null) {
                    Move.Ask(question.copy(from = ref))
                } else {
                    val swap = Step.Swap(cardAt(view, from), cardAt(view, ref))
                    Move.Plan(PlanEdit.SetLane(question.seat, swap))
                }
            }
        }
        .toMap()

    return Table(
        prompt = Ask.ChooseTwoFromDifferentPlayers,
        detail = Detail.APlanIsASuggestion,
        choices = listOf(Choice(Label.Back, Move.Ask(question.copy(kind = null, from = null)))),
        taps = taps,
        aim = Aim(
            first = from?.let { AimedCard(speakerFor(view, it.playerId), it.position + 1, CardView.Hidden) },
        ),
    )
}

/**
 * A card named for a step, anchored to what the table has said about it.
 *
 * The anchor is the claim the step was built on, so the step can follow its card through a
 * watched swap (design D9). A card nobody has spoken about gets none, correctly: it was pointed
 * at by position and has nothing to follow.
 */
private fun cardAt(view: PlayerView, ref: CardRef): CardAt {
    val hand = view.players.firstOrNull { it.id == ref.playerId }
    val anchor = hand?.let { believedOnView(it, ref.position).sources.firstOrNull() }
    return CardAt(ref.playerId, ref.position, anchor)
}

// ---------------------------------------------------------------------------- the viewer's turn

/**
 * The viewer's own lane, surfacing on their turn.
 *
 * Two things, and only these two. The ask is written under the prompt in words, whatever the
 * draw was — the plan is the reason the turn is being played the way it is, and it should be
 * in front of the person playing it. And when the table's *own* controls already offer a move
 * that does the step — the unused action card is on the pile, a King is in hand, a Jack is
 * aimed — that move is put first as "do as planned". It is found among the moves the table
 * built rather than composed here, so it is legal by construction, and every other move stays
 * exactly where it was: pre-arming aims a turn and never narrows it (design D5).
 */
internal fun Table.planned(view: PlayerView, plan: CoalitionPlan?): Table {
    val step = plan?.laneOf(view.viewerId)?.step ?: return this
    val armed = armedMove(view, step)
    val first = armed?.let { listOf(Choice(Label.DoAsPlanned, it, Tone.PLAY)) }.orEmpty()
    return copy(
        detail = Detail.ThePlanAsksYouTo(stepLine(view, step)),
        choices = first + choices,
    )
}

private fun Table.armedMove(view: PlayerView, step: Step): Move? = when (step) {
    Step.TakeTheDiscard -> {
        choices.firstOrNull { it.label is Label.UseFromPile }?.move
    }

    is Step.Declare -> {
        ranks.firstOrNull { rank ->
            rank.rank == step.rank && (rank.move as? Move.Send)?.action is GameAction.DeclareKingAction
        }?.move
    }

    is Step.Swap -> {
        val pending = view.pendingAction?.takeIf { it.playerId == view.viewerId }
        val twoCards = pending?.targetType == TargetType.SWAP_CARDS ||
            pending?.targetType == TargetType.PEEK_THEN_SWAP
        val targets = pending?.targets.orEmpty()
        val firstAimed = targets.size == 1 &&
            targets[0].playerId == step.from.seat &&
            targets[0].position == step.from.position
        when {
            !twoCards -> null
            targets.isEmpty() -> taps[CardRef(step.from.seat, step.from.position)]
            firstAimed -> taps[CardRef(step.to.seat, step.to.position)]
            else -> choices.firstOrNull { it.label == Label.SwapCards }?.move
        }
    }
}
