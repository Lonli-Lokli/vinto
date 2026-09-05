package game.vinto.shapes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the coalition has agreed to try, for the rest of the final round.
 *
 * **Not game state** (design D6). The plan mutates nothing, reaches no recording and never
 * touches a hash; it is a shared draft the room keeps for the length of a round and throws
 * away when the round is scored. One per round and editable by any member — three competing
 * plans is not a coalition deciding together. It is a **board of parts** agreed **as a whole**
 * (design D7a): an edit names one lane or one shed, and the coalition says yes to the board.
 *
 * Two bounds make it a small feature wearing a big one's clothes:
 *
 *  - **the spine is fixed.** The final round is one turn per coalition member, in table order
 *    after the caller, so there are at most three [lanes] and nobody chooses their order —
 *    only what happens in each;
 *  - **the palette is bounded by what has been said.** A step can only name cards the table
 *    has been told about, which is what makes declaring worth doing.
 *
 * The caller has no lane by construction — a lane belongs to a coalition seat — and a step that
 * names one of the caller's cards is refused at the one door every edit comes through
 * ([edited]), since the coalition may not touch them.
 */
@Serializable
data class CoalitionPlan(
    /** One per turn still to come, in the order they come. Never more than three. */
    val lanes: List<Lane> = emptyList(),
    /**
     * Who intends to shed what, if the rank lands.
     *
     * Beside the lanes rather than in them, because a toss-in is **not a turn**: it costs none
     * and can happen in anybody's. The final round is three turns *plus every window they
     * open*, and shedding is the cheapest way to lower a hand in the game — a plan that could
     * only describe turns would miss the coalition's best tool.
     *
     * Not a conditional, either (design D7): "I hold a 7 and will shed it" is a statement
     * about what its speaker holds, not a branch.
     */
    val sheds: List<Shed> = emptyList(),
    /**
     * Who has said yes to the plan **as a whole** — player ids.
     *
     * To the whole and not to a part, because the lanes only pay off together: a swap in one
     * lane is worth something because of what the next lane does with it. Every edit resets
     * this to the editor alone, since a yes to a plan that no longer exists is not a yes, and
     * making the edit is agreeing to it. Bots are in here too, having answered for their own
     * lane (design D7a).
     */
    val agreed: List<String> = emptyList(),
    /** Who made the last edit, so a strip can say that Nina changed Don's lane. */
    val editedBy: String? = null,
) {
    /** Whether anything has actually been planned, as opposed to a draft nobody has filled. */
    val isEmpty: Boolean get() = lanes.all { it.step == null } && sheds.isEmpty()
}

/**
 * One member's turn, and what the coalition hopes they will do with it.
 *
 * [step] may be null — "your call" is a real answer, and a plan that demanded an intent for
 * every lane would be a form rather than a conversation.
 */
@Serializable
data class Lane(
    val seat: String,
    val step: Step? = null,
    /**
     * Set when this seat's turn begins.
     *
     * A plan must not change under the hand of the person executing it. Later lanes stay
     * editable, because the round is still going and better information keeps arriving.
     */
    val locked: Boolean = false,
)

/** Somebody holds that rank and will throw it in if one lands. */
@Serializable
data class Shed(val seat: String, val rank: Rank)

/**
 * One card, named by whose hand it is in and where — and by what was *said* about it.
 *
 * [anchor] is the claim the step was built on, carried so the step can follow its card. A
 * position alone cannot: once a Jack has moved the card, the position holds something else,
 * and nothing left at the old address says what used to be there. The claim travels with the
 * card (`ActionUtils` carries it through a watched swap, keeping its speaker), so the claim is
 * what a step chases.
 *
 * Null where the step was built on a card nobody had spoken about. Such a step cannot follow
 * anything, which is correct: it was pointing at a position rather than at a card.
 */
@Serializable
data class CardAt(
    val seat: String,
    val position: Int,
    val anchor: Claim? = null,
)

/**
 * What a member is being asked to do with their turn.
 *
 * Deliberately small. Everything here is expressible from what the table has been told, and
 * nothing here depends on what its owner draws — their turn opens with a card nobody can
 * predict, so a step that assumed one would be stale before it was read.
 */
@Serializable
sealed interface Step {

    /**
     * Trade two cards between two hands — what a Jack does, and what a Queen may do after
     * looking.
     *
     * The concentration play, which is the whole of coalition strategy: only the lowest hand
     * is compared, so the good cards belong in one hand and the rest are dumping grounds.
     */
    @Serializable
    @SerialName("swap")
    data class Swap(val from: CardAt, val to: CardAt) : Step

    /**
     * Name a rank with a King, so that everybody holding one throws it in.
     *
     * Safe in a way that is worth knowing: the caller may not toss in at all once they have
     * called, so a King can only empty coalition hands. It cannot help the caller shed.
     */
    @Serializable
    @SerialName("declare")
    data class Declare(val rank: Rank) : Step

    /** Take the unused action card off the discard pile and play it. */
    @Serializable
    @SerialName("take-discard")
    data object TakeTheDiscard : Step
}

// ---------------------------------------------------------------- editing the board

/**
 * One part of the plan, changed.
 *
 * The plan is a **board of parts**, and an edit names one of them (design D7a): a lane set,
 * replaced or cleared, or a shed added or removed. The room merges the part into the standing
 * plan, which is what lets two people work on different lanes without either overwriting the
 * other — the first cut of this sent the whole draft on every gesture, and two messages crossing
 * lost a lane every time. Two edits to the *same* part resolve as the most recent standing,
 * which is the one case "last edit stands" was ever for.
 *
 * Any coalition member may edit any part, whoever's turn it names. The caller may edit none.
 */
@Serializable
sealed interface PlanEdit {

    /** Set or replace what [seat] is asked to do with their turn. */
    @Serializable
    @SerialName("set-lane")
    data class SetLane(val seat: String, val step: Step) : PlanEdit

    /** Take the lane back to "your call". */
    @Serializable
    @SerialName("clear-lane")
    data class ClearLane(val seat: String) : PlanEdit

    @Serializable
    @SerialName("add-shed")
    data class AddShed(val shed: Shed) : PlanEdit

    @Serializable
    @SerialName("remove-shed")
    data class RemoveShed(val shed: Shed) : PlanEdit
}

/** What the door made of an edit. */
sealed interface PlanEditOutcome {
    data class Edited(val plan: CoalitionPlan) : PlanEditOutcome
    data class Refused(val reason: String) : PlanEditOutcome
}

/**
 * The coalition in the order it plays the final round: table order, starting after the caller.
 *
 * The spine of the plan (design D7). Lanes are kept in this order whatever order they were set
 * in, so a rehearsal plays them as the round will.
 */
fun coalitionInTurnOrder(playerIds: List<String>, callerId: String): List<String> {
    val at = playerIds.indexOf(callerId)
    if (at < 0) return playerIds
    return playerIds.drop(at + 1) + playerIds.take(at)
}

/**
 * One edit applied to the standing plan — **the** door, called by the room and by the solo
 * session alike so the two cannot disagree about what a legal edit is. The same shape as
 * `GameAction.retired` (design D2): a rule both doors must answer identically lives in neither.
 *
 * [coalition] is every seat but the caller, in turn order. What is refused:
 *
 *  - an editor outside it — the caller has no coalition to plan with, and a stranger has no seat;
 *  - a lane or a shed for a seat outside it — the caller has no turn in this round and their
 *    cards are untouchable, so a plan that could name their seat is a plan that could name
 *    their cards. A step's own card references are held to the same rule;
 *  - a lane whose owner's turn has begun — one already locked, or one for the seat [onPlay]
 *    right now, which is the same turn before pacing has stamped it. A plan must not change
 *    under the hand of the person executing it, and an edit *names* its lane, so "cannot be
 *    the target" is the whole check — where a whole-draft door had to notice a locked lane
 *    being omitted.
 *
 * Every edit resets agreement to the editor alone: a yes to a plan that no longer exists is not
 * a yes, and making the edit is agreeing to it.
 */
fun CoalitionPlan?.edited(
    edit: PlanEdit,
    by: String,
    coalition: List<String>,
    onPlay: String?,
): PlanEditOutcome {
    val standing = this ?: CoalitionPlan()
    if (by !in coalition) return PlanEditOutcome.Refused("only the coalition may plan")

    val lanes = standing.lanes.associateBy { it.seat }.toMutableMap()
    val sheds = standing.sheds.toMutableList()
    when (edit) {
        is PlanEdit.SetLane -> {
            laneRefusal(edit.seat, lanes, coalition, onPlay)?.let { return it }
            edit.step.cardsNamed().firstOrNull { it.seat !in coalition }?.let {
                return PlanEditOutcome.Refused("a step may not touch the caller's cards")
            }
            lanes[edit.seat] = Lane(seat = edit.seat, step = edit.step, locked = false)
        }

        is PlanEdit.ClearLane -> {
            laneRefusal(edit.seat, lanes, coalition, onPlay)?.let { return it }
            lanes.remove(edit.seat)
        }

        is PlanEdit.AddShed -> {
            if (edit.shed.seat !in coalition) {
                return PlanEditOutcome.Refused("that seat is not playing a turn in this round")
            }
            if (edit.shed !in sheds) sheds += edit.shed
        }

        is PlanEdit.RemoveShed -> {
            sheds -= edit.shed
        }
    }

    return PlanEditOutcome.Edited(
        standing.copy(
            lanes = coalition.mapNotNull { lanes[it] },
            sheds = sheds,
            agreed = listOf(by),
            editedBy = by,
        ),
    )
}

private fun laneRefusal(
    seat: String,
    lanes: Map<String, Lane>,
    coalition: List<String>,
    onPlay: String?,
): PlanEditOutcome.Refused? =
    when {
        seat !in coalition -> PlanEditOutcome.Refused("that seat is not playing a turn in this round")
        lanes[seat]?.locked == true || seat == onPlay ->
            PlanEditOutcome.Refused("that turn has already started")
        else -> null
    }

/** The cards a step names, so a door can check whose they are. */
fun Step.cardsNamed(): List<CardAt> = when (this) {
    is Step.Swap -> listOf(from, to)
    is Step.Declare, Step.TakeTheDiscard -> emptyList()
}

/** One seat's yes or no to the plan as it stands. */
fun CoalitionPlan.agreeing(seat: String, agree: Boolean): CoalitionPlan =
    copy(agreed = if (agree) (agreed + seat).distinct() else agreed - seat)

/** Whether everybody who has to say yes has. */
fun CoalitionPlan.agreedBy(everyone: Collection<String>): Boolean = everyone.all { it in agreed }

/** The lane owned by [seat], if one has been set. */
fun CoalitionPlan.laneOf(seat: String): Lane? = lanes.firstOrNull { it.seat == seat }

/**
 * Locks the lane of whoever is on play, and leaves the rest open.
 *
 * A plan must not change under the hand of the person executing it — the step they agreed to
 * is the step they are acting on. Later lanes stay editable, because the round is still going
 * and better information keeps arriving. Once locked, a lane stays locked: a turn does not
 * un-begin.
 */
fun CoalitionPlan.lockingLaneOf(onPlay: String?): CoalitionPlan =
    if (onPlay == null || lanes.all { it.seat != onPlay || it.locked }) {
        this
    } else {
        copy(lanes = lanes.map { lane -> if (lane.seat == onPlay) lane.copy(locked = true) else lane })
    }
