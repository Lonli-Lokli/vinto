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
 *
 * **The plan can say everything the rules let the coalition do**, and that is a requirement
 * rather than a nicety: it stands in for the talk people have at a real table, and a table that
 * can say *"you play this Queen, then I throw in my Queen and play mine"* is not served by a
 * plan that can only say the first half. So a turn is one member's move **and** the throw-ins
 * it sets off, in the order people throw, each saying what its card then does ([Lane.tossIns]);
 * and every action a card has — a look, a trade, a King's pointed card, an Ace's forced draw —
 * has a [Step].
 */
@Serializable
data class CoalitionPlan(
    /** One per turn still to come, in the order they come. Never more than three. */
    val lanes: List<Lane> = emptyList(),
    /**
     * Who intends to shed what, if the rank lands — a standing promise about the whole round,
     * not tied to any turn.
     *
     * Kept on the wire for the plans that carry one; the app no longer writes them. A throw-in
     * belongs to the turn it lands on ([Lane.tossIns]), because that is where it is read, where
     * its order against the other throws matters, and where what the thrown card *does* has a
     * table to be aimed at. A promise beside the turns said none of that.
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
    val isEmpty: Boolean
        get() = lanes.all { it.step == null && it.opening == null && it.tossIns.isEmpty() } && sheds.isEmpty()
}

/**
 * How a turn opens. Every turn in this game starts by taking a card from one of the two piles.
 *
 * Recorded beside the step because a turn is a *sequence* — take a card, then do something with
 * it — and three of the four steps never said which pile their card came from. A plan that
 * cannot say "take the King off the pile **and** declare fives" is describing half a turn.
 *
 * **A draw is still worth planning around**, which is the thing that looked like a blocker and
 * is not: you cannot say which card arrives, but you can say which of your own goes out in its
 * place, whether to guess that card's rank, and who throws in after it lands. And the moment the
 * draw is face up it is public, so the board is rethought — which is how the bots already work,
 * filling empty lanes on every pass.
 */
@Serializable
enum class Opening {
    /** Off the deck, sight unseen. */
    @SerialName("draw")
    DRAW,

    /** The unused action card lying face up, which is the one card a plan can name in advance. */
    @SerialName("take-the-discard")
    TAKE_THE_DISCARD,
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
     * Which pile the turn's card comes from, or null where nobody has said.
     *
     * Additive on the wire: an older build ignores it and reads the step exactly as it did
     * before, which is why this is a new field rather than a new [Step] shape. **After** [step]
     * rather than before it, so that `Lane(seat, step)` goes on meaning what it always did.
     */
    val opening: Opening? = null,
    /**
     * Set once this seat's turn has been **played** — it is history, and history is not edited.
     *
     * Not when the turn *begins*, which is what it used to mean. A turn never locks under the
     * hand of the person playing it: the moment their drawn card is face up it is public, and
     * the whole point of the plan is that the coalition can say what to do with it while the
     * card is still in their hand. So the turn on play stays open, and the ones before it close
     * ([lockingLaneOf]). The door refuses the played turns whether or not pacing has stamped
     * them, from the order of the coalition alone.
     */
    val locked: Boolean = false,
    /**
     * What the lane's own seat would rather do (task 3.13): a bot's alternative to a step a
     * person set for it, offered beside the step rather than written over it. A person puts it
     * on the board with one tap, which is an ordinary edit; nothing a bot says undoes an edit,
     * and every edit to this lane clears it. Bots only ever suggest for their own lane, since
     * that is the only turn they can honestly judge (design D3).
     */
    val suggestion: Step? = null,
    /**
     * Who throws in on what this turn puts down, **in the order they throw**, each with what
     * the thrown card then does.
     *
     * Inside the turn rather than beside the plan, because a throw-in is part of the turn that
     * lands its rank: the rules let any player — the one on turn included — throw a matching
     * card in the moment one lands and play its action at once, and the order the coalition
     * throws in is the order those actions resolve. *"Then I throw in my Queen and play mine"*
     * is this list. Empty where nobody has said they will throw.
     *
     * Additive on the wire, and last, so every older `Lane(...)` still means what it did.
     */
    val tossIns: List<TossIn> = emptyList(),
)

/**
 * One throw-in inside a turn: who throws, what they say the card is, which card, and what the
 * thrown card then does.
 *
 * [rank] is what the thrower says the card is — "I throw in my Queen" — and null where they
 * cannot say: a **blind** throw of a card nobody has named, made because a wrong throw costs a
 * penalty card but shows the card, and at a real table knowing sometimes beats not knowing. A
 * blind throw names its [card], since it has no rank to be found by, and says nothing of what
 * the card does, since a card nobody has seen has no action anybody can aim. A vouched throw
 * may name its card too, so a film need not go looking for it.
 *
 * [then] is the card's own action — a look, a trade, a declare, a forced draw — held to the
 * rank at the door exactly as a called card's is, and null where the card has no action or the
 * thrower has not said.
 */
@Serializable
data class TossIn(
    val seat: String,
    val rank: Rank? = null,
    val then: Step? = null,
    val card: CardAt? = null,
) {
    /** A throw of a card the thrower cannot name: the table cannot vouch for it. */
    val blind: Boolean get() = rank == null
}

/** A standing promise: somebody holds that rank and will throw it in if one lands. */
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
 * What a member is being asked to do with their turn — or what a played card does.
 *
 * One vocabulary for both, because a turn's own move and what a thrown or called card does
 * are the same actions: the rules give each rank one action and it does not matter how the
 * card reached the table. Everything here is expressible from what the table has been told,
 * and nothing here depends on what its owner draws — their turn opens with a card nobody can
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
     * A King: point at a card, name its rank, and — if the name was right — that card leaves
     * its hand and its own action is played by the King's player; then everybody holding a
     * King or the named rank may throw in.
     *
     * Safe in a way that is worth knowing: the caller may not toss in at all once they have
     * called, so a King can only empty coalition hands. It cannot help the caller shed.
     *
     * @param rank the rank the King names, or null while it has only pointed: the card comes
     *   first at a table — "point at Ember's third" — and the rank is what the table says that
     *   card is, offered afterwards. What the card *does* waits for the rank, since its action
     *   is the rank's.
     * @param card the card the King points at, or null for the older, looser shape — a rank
     *   named at whoever is believed to hold one — which older plans carry and which still
     *   reads.
     * @param then what the pointed-at card's action does when the name was right, or null
     *   where it has none or nobody has said. Held to [rank] at the door, like a called card's.
     */
    @Serializable
    @SerialName("declare")
    data class Declare(val rank: Rank? = null, val card: CardAt? = null, val then: Step? = null) : Step

    /** Take the unused action card off the discard pile and play it. */
    @Serializable
    @SerialName("take-discard")
    data object TakeTheDiscard : Step

    /**
     * Put one of your own cards face up on the pile — draw, and swap the draw into that card's
     * place — so that a teammate holding its rank can throw theirs in on it (task 3.14).
     *
     * The proposal that sets a throw-in up: this lands a 7, and the turn's [Lane.tossIns] say
     * who throws one in on it. Only the lane's own seat can put a card down, since it is their
     * hand the draw goes into; the door holds that.
     */
    @Serializable
    @SerialName("put-down")
    data class PutDown(
        val card: CardAt,
        /**
         * The rank to guess the put-down card as, or null to put it down in silence.
         *
         * The rules let a player name the rank of the card they just swapped out: right, and
         * they play that card's action for free; wrong, and they take a penalty card. It is a
         * real decision with a real price, so it is a thing a coalition plans rather than a
         * thing one member springs.
         *
         * **Not the King's declare**, which is [Declare] and a different move: that one names a
         * rank so that everybody throws it in. This one is a guess about one card.
         */
        val guess: Rank? = null,
        /**
         * What the called card's action then does: a [Swap] for a Jack or a Queen, a [Declare]
         * for a King, a [Peek] for a 7 to 10, a [ForceDraw] for an Ace. Null where the call is
         * silent or nobody has said.
         *
         * On the put-down rather than beside it, because it is one turn: *"swap with my Jack and
         * move some cards"* is keep the draw in the Jack's place, call it, and the Jack trades
         * two cards — one sequence, and the trade is what the call is *for*. A lane holding two
         * steps would let a plan say the trade without the call that makes it possible.
         *
         * What it may hold is the door's business (`edited`): the action of the rank called, and
         * nothing that names the card put down, which is on the pile by then.
         */
        val then: Step? = null,
    ) : Step

    /**
     * Let the card go: onto the pile, action unused, hand untouched.
     *
     * It looks like a wasted turn and it is the opposite. This is how a coalition tells the seat
     * holding its best hand **don't touch your hand** — draw, and whatever it is, let it go. Only
     * the lowest hand is compared, so protecting it is worth a turn; and the alternative, leaving
     * the lane at "your call", says nothing and reads as nobody having got to it yet.
     */
    @Serializable
    @SerialName("bin")
    data object Bin : Step

    /**
     * Play whatever the turn's card turns out to be, for its action.
     *
     * The instruction you can give about a card nobody has seen yet: *use it*. No targets,
     * because there is nothing to aim until the card is face up — and the moment it is, the
     * board is rethought and this becomes a [Swap], a [Declare] or a better idea.
     *
     * [TakeTheDiscard] is this same instruction with the pile named, and stays because it is on
     * the wire; a lane whose opening says `TAKE_THE_DISCARD` and whose step is this one means
     * exactly what that one always meant.
     */
    @Serializable
    @SerialName("use-it")
    data object UseIt : Step

    /**
     * Look at a card: one of your own for a 7 or an 8, one of somebody else's for a 9 or a 10,
     * and two cards of two hands for a Queen — [also] is the Queen's second card.
     *
     * A look moves nothing and the plan cannot price it, but a coalition plans them all the
     * time: a look at a teammate's unspoken card is how the next claim gets made, and a Queen
     * that looks and decides not to swap is a real turn.
     */
    @Serializable
    @SerialName("peek")
    data class Peek(val card: CardAt, val also: CardAt? = null) : Step

    /**
     * An Ace: make [seat] draw a card face down. Never the caller, whose hand is frozen from
     * the call — so in a final round an Ace can only ever lengthen a coalition hand, which is
     * why a plan will mostly say to let one go. It is here because the plan says what the rules
     * allow, not what is wise.
     */
    @Serializable
    @SerialName("force-draw")
    data class ForceDraw(val seat: String) : Step
}

// ---------------------------------------------------------------- editing the board

/**
 * One part of the plan, changed.
 *
 * The plan is a **board of parts**, and an edit names one of them (design D7a): a lane set,
 * replaced or cleared, its throw-ins set, or a shed added or removed. The room merges the part
 * into the standing plan, which is what lets two people work on different lanes without either
 * overwriting the other — the first cut of this sent the whole draft on every gesture, and two
 * messages crossing lost a lane every time. Two edits to the *same* part resolve as the most
 * recent standing, which is the one case "last edit stands" was ever for.
 *
 * Any coalition member may edit any part, whoever's turn it names. The caller may edit none.
 */
@Serializable
sealed interface PlanEdit {

    /** Set or replace what [seat] is asked to do with their turn. */
    @Serializable
    @SerialName("set-lane")
    data class SetLane(val seat: String, val step: Step) : PlanEdit

    /**
     * Set which pile [seat]'s turn opens from, leaving whatever is planned after it alone.
     *
     * Its own edit because it is its own decision, and the first one: a turn is built a part at
     * a time and the opening is the part that is always there. Setting it must not wipe a step
     * already agreed, and choosing a step must not silently pick a pile.
     */
    @Serializable
    @SerialName("open-lane")
    data class OpenLane(val seat: String, val opening: Opening) : PlanEdit

    /** Take the lane back to "your call" — the whole turn, throw-ins included. */
    @Serializable
    @SerialName("clear-lane")
    data class ClearLane(val seat: String) : PlanEdit

    @Serializable
    @SerialName("add-shed")
    data class AddShed(val shed: Shed) : PlanEdit

    @Serializable
    @SerialName("remove-shed")
    data class RemoveShed(val shed: Shed) : PlanEdit

    /**
     * Set who throws in on [seat]'s turn, in the order they throw — the whole list, because
     * the order is part of what is being said and one edit that names it cannot be crossed by
     * another that names it differently. Adding, removing and reordering are all this.
     */
    @Serializable
    @SerialName("set-toss-ins")
    data class SetTossIns(val seat: String, val tossIns: List<TossIn>) : PlanEdit
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
 *  - a lane, a shed or a throw-in for a seat outside it — the caller has no turn in this round,
 *    may not throw in, and their cards are untouchable, so a plan that could name their seat is
 *    a plan that could name their cards. A step's own card references are held to the same rule;
 *  - a lane whose owner's turn has been **played** — one already locked, or one for a seat
 *    before [onPlay] in turn order, which is the same turn before pacing has stamped it. The
 *    turn on play itself stays open: a plan is rewritten under the hand of the person playing
 *    it, because their drawn card is the news the whole plan turns on. An edit *names* its
 *    lane, so "cannot be the target" is the whole check — where a whole-draft door had to
 *    notice a locked lane being omitted;
 *  - what a card does that the card cannot do: the rank a card is called, thrown or declared as
 *    decides what its action is, and a plan may only say that.
 *
 * Every edit resets agreement to the editor alone: a yes to a plan that no longer exists is not
 * a yes, and making the edit is agreeing to it.
 */
@Suppress("ReturnCount")
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
            stepRefusal(edit.seat, edit.step, coalition)?.let { return it }
            // The opening and the throw-ins are kept: they are separate parts of the same turn,
            // and a member who said "take the discard" and then chose what to do with it has
            // said two things.
            lanes[edit.seat] = Lane(
                seat = edit.seat,
                opening = lanes[edit.seat]?.opening,
                step = edit.step,
                locked = false,
                tossIns = lanes[edit.seat]?.tossIns.orEmpty(),
            )
        }

        is PlanEdit.OpenLane -> {
            laneRefusal(edit.seat, lanes, coalition, onPlay)?.let { return it }
            lanes[edit.seat] = Lane(
                seat = edit.seat,
                opening = edit.opening,
                step = lanes[edit.seat]?.step,
                locked = false,
                tossIns = lanes[edit.seat]?.tossIns.orEmpty(),
            )
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

        is PlanEdit.SetTossIns -> {
            laneRefusal(edit.seat, lanes, coalition, onPlay)?.let { return it }
            edit.tossIns.forEach { tossIn -> tossInRefusal(tossIn, coalition)?.let { return it } }
            lanes[edit.seat] = Lane(
                seat = edit.seat,
                opening = lanes[edit.seat]?.opening,
                step = lanes[edit.seat]?.step,
                locked = false,
                tossIns = edit.tossIns,
            )
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

/**
 * What is wrong with the step itself, as opposed to with the lane it is being put on.
 *
 * Its own function because [edited] had reached the number of ways out a reader can hold at
 * once, and these are one subject: a step may not touch the caller's cards, a swap is between
 * two hands, a Queen's two looks are at two hands, only a lane's own seat can put its own card
 * down, and what a called or pointed-at card goes on to do is what its rank does.
 *
 * @param seat whose turn the step is on — the one who plays it, which is what a look at "one of
 *   your own cards" is measured against.
 */
private fun stepRefusal(seat: String, step: Step, coalition: List<String>): PlanEditOutcome.Refused? = when {
    step.cardsNamed().any { it.seat !in coalition } ->
        PlanEditOutcome.Refused("a step may not touch the caller's cards")

    step.drawers().any { it !in coalition } ->
        PlanEditOutcome.Refused("the caller may not be made to draw")

    step.trades().any { it.from.seat == it.to.seat } ->
        PlanEditOutcome.Refused("a swap is between two hands")

    step.looks().any { it.also != null && it.also.seat == it.card.seat } ->
        PlanEditOutcome.Refused("a Queen looks at two cards of two hands")

    // The draw goes into the hand that puts the card down, and only the lane's own seat draws
    // on that turn.
    step is Step.PutDown && step.card.seat != seat ->
        PlanEditOutcome.Refused("you can only put down your own card")

    step is Step.PutDown -> callRefusal(seat, step)

    step is Step.Declare -> pointedRefusal(seat, step)

    else -> null
}

/**
 * A throw-in, held to the same rules as a step: who may throw, whose card, and what the thrown
 * card may do — which for a blind throw is nothing, since nobody can aim a card nobody has seen.
 */
@Suppress("ReturnCount")
private fun tossInRefusal(tossIn: TossIn, coalition: List<String>): PlanEditOutcome.Refused? {
    if (tossIn.seat !in coalition) return PlanEditOutcome.Refused("the caller may not throw in")
    val card = tossIn.card
    if (card != null && card.seat != tossIn.seat) {
        return PlanEditOutcome.Refused("you can only throw your own card")
    }
    val rank = tossIn.rank
    if (rank == null) {
        if (card == null) return PlanEditOutcome.Refused("a blind throw names the card it throws")
        if (tossIn.then != null) return PlanEditOutcome.Refused("a blind throw cannot say what its card does")
        return null
    }
    val then = tossIn.then ?: return null
    return stepRefusal(tossIn.seat, then, coalition) ?: actionRefusal(rank, then, tossIn.seat)
}

/**
 * What a called card may go on to do: the action of the rank that was called, aimed at cards
 * that are still there. A trade naming the card put down is a trade with a card on the pile.
 */
private fun callRefusal(seat: String, step: Step.PutDown): PlanEditOutcome.Refused? {
    val then = step.then ?: return null
    if (step.guess == null) return PlanEditOutcome.Refused("call the card before saying what it does")
    // Compared without anchors: the same card can carry a different claim at each end.
    val put = step.card.copy(anchor = null)
    if (then.cardsNamed().any { it.copy(anchor = null) == put }) {
        return PlanEditOutcome.Refused("the card put down is not there to swap")
    }
    return actionRefusal(step.guess, then, seat)
}

/** What a pointed-at card may go on to do: its rank's action, aimed at cards other than itself. */
private fun pointedRefusal(seat: String, step: Step.Declare): PlanEditOutcome.Refused? {
    val then = step.then ?: return null
    val rank = step.rank ?: return PlanEditOutcome.Refused("name the rank before saying what the card does")
    val pointed = step.card?.copy(anchor = null)
    if (pointed != null && then.cardsNamed().any { it.copy(anchor = null) == pointed }) {
        return PlanEditOutcome.Refused("the declared card is not there to move")
    }
    return actionRefusal(rank, then, seat)
}

/**
 * Whether [then] is what a card of [rank] does, played by [actor] — the rules' table of
 * actions, applied to whatever a plan says a card will do once it is face up.
 *
 * A 7 or an 8 looks at one of the player's own cards; a 9 or a 10 at one card of another hand;
 * a Jack trades two cards; a Queen looks at two cards of two hands and may trade them; a King
 * declares; an Ace makes somebody draw; every other rank does nothing, so a plan may say
 * nothing about it.
 */
@Suppress("CyclomaticComplexMethod")
private fun actionRefusal(rank: Rank, then: Step, actor: String): PlanEditOutcome.Refused? {
    val wanted = when (then) {
        is Step.Swap -> "only a Jack or a Queen swaps"
        is Step.Declare -> "only a King declares"
        is Step.ForceDraw -> "only an Ace makes somebody draw"
        is Step.Peek -> "only a 7, 8, 9, 10 or Queen looks at a card"
        is Step.PutDown, Step.TakeTheDiscard, Step.Bin, Step.UseIt ->
            return PlanEditOutcome.Refused("a played card can only look, swap, declare or make somebody draw")
    }
    val fits = when (rank) {
        Rank.SEVEN, Rank.EIGHT -> then is Step.Peek && then.also == null && then.card.seat == actor
        Rank.NINE, Rank.TEN -> then is Step.Peek && then.also == null && then.card.seat != actor
        Rank.JACK -> then is Step.Swap
        Rank.QUEEN -> when (then) {
            is Step.Swap -> true
            is Step.Peek -> then.also != null
            else -> false
        }
        Rank.KING -> then is Step.Declare
        Rank.ACE -> then is Step.ForceDraw
        Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.JOKER -> false
    }
    if (fits) return null
    return PlanEditOutcome.Refused(
        when {
            !hasAction(rank) -> "a ${rank.serialName} has no action to plan"
            then is Step.Peek && (rank == Rank.SEVEN || rank == Rank.EIGHT) ->
                "a 7 or an 8 looks at one of your own cards"
            then is Step.Peek && (rank == Rank.NINE || rank == Rank.TEN) ->
                "a 9 or a 10 looks at one card of another hand"
            then is Step.Peek && rank == Rank.QUEEN -> "a Queen looks at two cards of two hands"
            else -> wanted
        },
    )
}

/** Every trade a step makes — its own, or the one its call, its King or its throw-in plays. */
private fun Step.trades(): List<Step.Swap> = when (this) {
    is Step.Swap -> listOf(this)
    is Step.PutDown -> then?.trades().orEmpty()
    is Step.Declare -> then?.trades().orEmpty()
    is Step.Peek, is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> emptyList()
}

/** Every look a step takes, its own or its called card's. */
private fun Step.looks(): List<Step.Peek> = when (this) {
    is Step.Peek -> listOf(this)
    is Step.PutDown -> then?.looks().orEmpty()
    is Step.Declare -> then?.looks().orEmpty()
    is Step.Swap, is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> emptyList()
}

/** Every seat a step makes draw. */
private fun Step.drawers(): List<String> = when (this) {
    is Step.ForceDraw -> listOf(seat)
    is Step.PutDown -> then?.drawers().orEmpty()
    is Step.Declare -> then?.drawers().orEmpty()
    is Step.Swap, is Step.Peek, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> emptyList()
}

private fun laneRefusal(
    seat: String,
    lanes: Map<String, Lane>,
    coalition: List<String>,
    onPlay: String?,
): PlanEditOutcome.Refused? =
    when {
        seat !in coalition -> PlanEditOutcome.Refused("that seat is not playing a turn in this round")
        lanes[seat]?.locked == true || played(seat, coalition, onPlay) ->
            PlanEditOutcome.Refused("that turn has been played")
        else -> null
    }

/**
 * Whether [seat]'s turn is over: somebody after it in turn order is on play. Nothing has been
 * played while play is still parked on the caller, who is not in the coalition at all.
 */
private fun played(seat: String, coalition: List<String>, onPlay: String?): Boolean {
    val playing = coalition.indexOf(onPlay)
    return playing >= 0 && coalition.indexOf(seat) < playing
}

/**
 * The cards a step names, so a door can check whose they are — the called card's trade, the
 * pointed-at card and a look's cards included. A King without a card names a rank, not a card.
 */
fun Step.cardsNamed(): List<CardAt> = when (this) {
    is Step.Swap -> listOf(from, to)
    is Step.PutDown -> listOf(card) + then?.cardsNamed().orEmpty()
    is Step.Declare -> listOfNotNull(card) + then?.cardsNamed().orEmpty()
    is Step.Peek -> listOfNotNull(card, also)
    is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> emptyList()
}

/** One seat's yes or no to the plan as it stands. */
fun CoalitionPlan.agreeing(seat: String, agree: Boolean): CoalitionPlan =
    copy(agreed = if (agree) (agreed + seat).distinct() else agreed - seat)

/** [seat]'s lane carrying what its owner would rather do, or nothing; no lane, no change. */
fun CoalitionPlan.suggesting(seat: String, step: Step?): CoalitionPlan =
    copy(lanes = lanes.map { lane -> if (lane.seat == seat) lane.copy(suggestion = step) else lane })

/** Whether everybody who has to say yes has. */
fun CoalitionPlan.agreedBy(everyone: Collection<String>): Boolean = everyone.all { it in agreed }

/** The lane owned by [seat], if one has been set. */
fun CoalitionPlan.laneOf(seat: String): Lane? = lanes.firstOrNull { it.seat == seat }

/**
 * Locks the lanes of the turns that have been **played** — every coalition seat before
 * [onPlay] in turn order — and leaves the turn on play and the ones after it open.
 *
 * The turn on play is deliberately not among them: it is the one turn the coalition most needs
 * to rewrite, because its drawn card is face up and public and the plan turns on what it is.
 * Once locked, a lane stays locked: a turn does not un-play itself.
 */
fun CoalitionPlan.lockingLaneOf(onPlay: String?, coalition: List<String>): CoalitionPlan {
    val playing = coalition.indexOf(onPlay)
    if (playing < 0) return this
    val over = coalition.take(playing).toSet()
    if (lanes.all { it.seat !in over || it.locked }) return this
    return copy(lanes = lanes.map { lane -> if (lane.seat in over) lane.copy(locked = true) else lane })
}
