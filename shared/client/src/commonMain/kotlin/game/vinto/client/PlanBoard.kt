package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Shed
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
    /**
     * Where the plan would leave the round, from standing claims alone (design D8): the
     * coalition's best hand, the caller's believed total, how much of it nobody has seen, and
     * whether that wins — with level shown as losing, since a tie pays the caller.
     */
    val outcome: PlanOutcome? = null,
    /** Watching the plan run, when there is a step to run. */
    val rehearse: Move? = null,
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
    /** Whether the plan as it stands wins, from what the table has been told. See [Board.outcome]. */
    val outcome: PlanOutcome? = null,
)

/**
 * One coalition member's turn on the board.
 *
 * [step] null is "your call", which is a real answer. [move] is what tapping the line does —
 * opening the composer for that seat — and null where nothing may change it: the turn has
 * begun, or the viewer is the caller, who reads the board and edits none of it.
 */
data class LaneLine(
    val who: Speaker,
    val step: StepLine?,
    val locked: Boolean,
    val move: Move?,
    /**
     * How the step is bearing up (design D9): still pointing at its card, following a card that
     * moved, or built on a claim a reveal has since proved wrong. The last is the game working,
     * not a player failing, and the copy says so.
     */
    val health: StepHealth = StepHealth.LIVE,
)

/** A step in words a renderer can put into a sentence. Positions are one-based, as people count. */
sealed interface StepLine {
    data class Swap(val fromWho: Speaker, val fromSlot: Int, val toWho: Speaker, val toSlot: Int) : StepLine
    data class Declare(val rank: Rank) : StepLine
    data object TakeTheDiscard : StepLine
}

/** Somebody will throw in a rank if it lands. [move] takes it back, for the one who said it. */
data class ShedLine(val who: Speaker, val rank: Rank, val move: Move? = null)

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
internal fun boardFor(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal> = emptyList(),
): Board? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null

    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    val onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id
    val agreed = plan?.agreed.orEmpty()
    // Read against the table as it is now: a step follows its card in silence, and one whose
    // claim a reveal contradicted is marked rather than repaired (design D9).
    val reading = plan?.let { readPlan(view, it, reveals) }

    return Board(
        lanes = coalition.map { seat ->
            val lane = plan?.laneOf(seat)
            val index = reading?.plan?.lanes?.indexOfFirst { it.seat == seat } ?: -1
            val followed = reading?.plan?.lanes?.getOrNull(index) ?: lane
            val locked = lane?.locked == true
            LaneLine(
                who = speakerFor(view, seat),
                step = followed?.step?.let { stepLine(view, it) },
                locked = locked,
                move = Move.Ask(Question.Planning(seat)).takeIf { member && !locked && seat != onPlay },
                health = reading?.health?.getOrNull(index) ?: StepHealth.LIVE,
            )
        },
        sheds = plan?.sheds.orEmpty().map { shed ->
            ShedLine(
                who = speakerFor(view, shed.seat),
                rank = shed.rank,
                move = Move.Plan(PlanEdit.RemoveShed(shed)).takeIf { member && shed.seat == view.viewerId },
            )
        },
        nods = coalition.map { Nod(speakerFor(view, it), agreed = it in agreed, away = it in away) },
        editedBy = plan?.editedBy?.let { speakerFor(view, it) },
        outcome = plan?.let { planOutcome(view, it) },
        rehearse = Move.Rehearse.takeIf { plan?.lanes.orEmpty().any { it.step != null } },
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
        outcome = plan?.takeUnless { it.isEmpty }?.let { planOutcome(view, it) },
    )
}

/**
 * The board, open: every lane, the sheds, the nods — and, for a member with something to say
 * yes to, "Agree". Back closes it. The log is not drawn under this table (the rail treats a
 * board like a rank grid), which is the price of a board a phone can hold.
 */
internal fun boardTable(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val board = boardFor(view, plan, away, reveals) ?: return Table(Ask.Watching, waiting = true)
    val member = view.viewerId != view.vintoCallerId
    val agreeable = member && plan != null && !plan.isEmpty && view.viewerId !in plan.agreed
    return Table(
        prompt = Ask.ThePlan,
        // A broken step is news, and it is the game working: somebody's memory was wrong.
        detail = if (board.lanes.any { it.health == StepHealth.BROKEN }) {
            Detail.AClaimWasWrong
        } else {
            Detail.APlanIsASuggestion
        },
        choices = buildList {
            if (agreeable) add(Choice(Label.Agree, Move.Agree(true), Tone.PLAY))
            if (member) add(Choice(Label.PlanAShed, Move.Ask(Question.Shedding)))
            add(Choice(Label.Back, Move.Ask(Question.None)))
        },
        board = board,
    )
}

/**
 * "I hold one of these and I will throw it in if one lands" — a shed, said on the board.
 *
 * Shedding is the cheapest way to lower a hand in the game and costs no turn, so it is most of
 * how a coalition plays its window (design D13a); but a wrong throw costs a card and, in the
 * final round, bars the seat for the rest of it — and when the seat is the hand the coalition is
 * pushing, that is the round. The rail says which of the two the viewer is looking at.
 */
internal fun sheddingTable(view: PlayerView): Table {
    val me = view.viewerId
    return Table(
        prompt = Ask.WhichRankWillYouThrowIn,
        detail = Detail.ShedRisk(pushed = isTheHandBeingPushed(view)),
        choices = listOf(Choice(Label.Back, Move.Ask(Question.ThePlan))),
        ranks = ALL_RANKS.map { rank -> RankChoice(rank, Move.Plan(PlanEdit.AddShed(Shed(me, rank)))) },
    )
}

/**
 * Whether the viewer's hand is the coalition's lowest as far as the table has been told — the
 * one hand whose cards a wrong throw costs the round, not just a member.
 */
private fun isTheHandBeingPushed(view: PlayerView): Boolean {
    val caller = view.vintoCallerId ?: return false
    val coalition = view.players.filter { it.id != caller }
    val believed = coalition.associate { seat ->
        seat.id to seat.cards.indices.sumOf { believedValueAt(seat, it) }
    }
    val lowest = believed.values.minOrNull() ?: return false
    return believed[view.viewerId] == lowest
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

        // A King names a rank so that every coalition hand holding one throws it in. The ranks
        // worth naming are the ones the table knows a coalition hand to hold — the rest are on
        // the rail too, muted, as a King's own rail draws them.
        StepKind.DECLARE -> Table(
            prompt = Ask.WhichRankShouldTheyDeclare(who),
            detail = Detail.APlanIsASuggestion,
            choices = listOf(Choice(Label.Back, Move.Ask(question.copy(kind = null)))),
            ranks = ALL_RANKS.map { rank ->
                RankChoice(
                    rank,
                    Move.Plan(PlanEdit.SetLane(seat.id, Step.Declare(rank))),
                    muted = rank !in ranksTheCoalitionIsKnownToHold(view),
                )
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
 *
 * **The palette is what has been said** (design D7): a card somebody has claimed, or one of
 * the viewer's own they have read. A card nobody knows anything about is not on offer — a
 * plan that moved it would be moving a guess — and that is what makes declaring worth doing:
 * say what a card is, and it becomes something the coalition can plan with.
 */
private fun swapPlanningTable(view: PlayerView, question: Question.Planning): Table {
    val caller = view.vintoCallerId
    val from = question.from

    val taps = view.players
        .filter { it.id != caller && it.id != from?.playerId }
        .flatMap { hand ->
            hand.cards.indices.filter { spokenFor(view, hand, it) }.map { position ->
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

/** A card the plan may name: claimed by somebody, or one of the viewer's own they have read. */
private fun spokenFor(view: PlayerView, hand: PlayerSeatView, position: Int): Boolean {
    val claimed = believedOnView(hand, position).sources.isNotEmpty()
    val ownAndRead = hand.id == view.viewerId && position in hand.knownCardPositions
    return claimed || ownAndRead
}

/** Every rank some coalition card is believed to be — the ranks a King on the board could empty. */
private fun ranksTheCoalitionIsKnownToHold(view: PlayerView): Set<Rank> =
    view.players
        .filter { it.id != view.vintoCallerId }
        .flatMap { hand ->
            hand.cards.indices.flatMap { position ->
                val believed = believedOnView(hand, position)
                if (believed.rankKnown) believed.candidates else emptySet()
            }
        }
        .toSet()

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
    val planned = armed?.let { listOf(Choice(Label.DoAsPlanned, it, Tone.PLAY)) }.orEmpty()

    // A draw that beats the plan offers the re-plan rather than insisting (3.11): keeping the
    // card comes first, the plan's step stays where it was, and the line under the prompt says
    // which card the draw is worth more than.
    val better = betterDraw(view, plan)
    val keep = better?.let {
        listOf(Choice(Label.KeepItInstead, Move.Ask(Question.WhichSlot), Tone.PLAY))
    }.orEmpty()
    return copy(
        detail = better?.let { Detail.YourDrawBeatsThePlan(it.rank, it.position) }
            ?: Detail.ThePlanAsksYouTo(stepLine(view, step)),
        choices = keep + planned + choices,
    )
}

/** The card the viewer has just drawn and is choosing about, when keeping it beats the plan. */
private fun betterDraw(view: PlayerView, plan: CoalitionPlan): KeepInstead? {
    if (view.subPhase != GameSubPhase.CHOOSING) return null
    val pending = view.pendingAction?.takeIf { it.playerId == view.viewerId && it.canGoToHand } ?: return null
    val rank = (pending.card as? CardView.Visible)?.card?.rank ?: return null
    return keepingBeatsThePlan(view, plan, rank)
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
