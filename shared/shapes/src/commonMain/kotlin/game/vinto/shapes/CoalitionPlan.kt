package game.vinto.shapes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the coalition has agreed to try, for the rest of the final round.
 *
 * **Not game state** (design D6). The plan mutates nothing, reaches no recording and never
 * touches a hash; it is a shared draft the room keeps for the length of a round and throws
 * away when the round is scored. One per round and editable by any member — three competing
 * plans is not a coalition deciding together, and last-edit-wins over one draft is what
 * deciding together actually looks like.
 *
 * Two bounds make it a small feature wearing a big one's clothes:
 *
 *  - **the spine is fixed.** The final round is one turn per coalition member, in table order
 *    after the caller, so there are at most three [lanes] and nobody chooses their order —
 *    only what happens in each;
 *  - **the palette is bounded by what has been said.** A step can only name cards the table
 *    has been told about, which is what makes declaring worth doing.
 *
 * The caller's cards are absent from every step by construction rather than by a check: the
 * coalition may not touch them, and a shape with no way to name one cannot be given one by a
 * missed `if`.
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
