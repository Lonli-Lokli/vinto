package game.vinto.shapes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the coalition can say to each other — a **phrasebook**, not a chat.
 *
 * Every sentence is a typed value with no natural-language text in it, rendered into words by
 * the reader's own client in the reader's own locale. That is not a way around the invariant
 * that nothing a player types reaches another player's screen; it is better than a text box
 * would be, and for a reason a text box cannot match: twenty locales ship, so a Belarusian and
 * a Japanese player can agree a Jack swap with no language in common. A text box would leave
 * them looking at each other.
 *
 * **None of this is game state.** Claims are — they are what the table believes about a card,
 * `CoalitionPlanner` reads them and every view carries them — and they travel as
 * `DECLARE_CARDS` through the engine like any other action. What is here is the transient
 * half: intentions, requests, answers, opinions. It must never mutate a game, never reach a
 * recording, and never touch a state hash.
 *
 * It lives here rather than in `shared/protocol` for the same reason [GameAction] does: it is
 * a **vocabulary**, not a transport. The bot produces it, the UI renders it and the wire
 * carries it, so it belongs where all three can reach it; what stays in the protocol module is
 * the envelope that carries it — `ClientMessage.Say` and `ServerMessage.Said`.
 *
 * Design D4 and D6.
 */
@Serializable
sealed interface TableTalk {

    /** The seat that said it. Checked at the room's door against the socket's own seat. */
    val by: String

    // ---------------------------------------------------------------- proposals

    /**
     * "You do this." A move addressed to the seat that could legally make it.
     *
     * The rule the whole design turns on: **a proposal is not an action.** It is never
     * validated as the proposer's, never reduced, and carries no authority whatever. The
     * recipient decides — a person with one tap, a bot by running it through its own planner —
     * and what reaches the engine is the *recipient's* own move, seat-bound as always.
     *
     * That is what lets a human and a bot be addressed identically, and it is why no control
     * anywhere acts for another seat. Letting one would be exactly the impersonation
     * `ValidatorImpersonationTest` refuses 18,066 times, and a local-only exception would
     * teach the UI a habit that breaks the first time somebody plays a real opponent.
     */
    @Serializable
    @SerialName("proposal")
    data class Proposal(
        override val by: String,
        /** Whose move it would be. */
        val to: String,
        val move: GameAction,
    ) : TableTalk

    /** "Give me that one." A request for a card, without naming the move that would do it. */
    @Serializable
    @SerialName("give_me")
    data class GiveMe(
        override val by: String,
        val from: String,
        val position: Int,
    ) : TableTalk

    /** "Take this one off me." The other half of the same trade. */
    @Serializable
    @SerialName("take_this")
    data class TakeThis(override val by: String, val position: Int) : TableTalk

    // ---------------------------------------------------------------- announcements

    /** "I am going to do this." Legibility, so three seats do not surprise each other. */
    @Serializable
    @SerialName("i_will")
    data class IWill(override val by: String, val move: GameAction) : TableTalk

    /**
     * "I hold one of those and I will throw it in if one lands."
     *
     * Not a conditional plan — design D7 refuses those — but a statement about what its
     * speaker holds. Shedding into a toss-in is the cheapest way to lower a hand in the game
     * and costs no turn, so saying it out loud is most of how a coalition uses the window.
     */
    @Serializable
    @SerialName("will_shed")
    data class WillShed(override val by: String, val rank: Rank) : TableTalk

    // ---------------------------------------------------------------- assessments

    /**
     * "Push me" / "use me as the bin" — where the speaker thinks they stand.
     *
     * Only the lowest coalition hand is compared, so the right play is to load one hand and
     * dump into the others. Which hand that is has to be a shared answer or three independent
     * turns undo each other.
     */
    @Serializable
    @SerialName("standing")
    data class Standing(override val by: String, val where: Where) : TableTalk {
        @Serializable
        enum class Where {
            /** "Mine is low — play for it". */
            @SerialName("low")
            LOW,

            /** "Mine is high — give me your rubbish". */
            @SerialName("high")
            HIGH,

            /** "I am out of it; use me". */
            @SerialName("bin")
            BIN,
        }
    }

    /**
     * "Play for that one."
     *
     * The retired nomination, returned as something you can *say* rather than something the
     * state records. Non-binding and costing nothing — the coalition's real target stays the
     * lowest hand, whoever holds it — so it gives back the beat of agency the vote's removal
     * took away without reintroducing a decision that settled nothing.
     */
    @Serializable
    @SerialName("play_for")
    data class PlayFor(override val by: String, val seat: String) : TableTalk

    // ---------------------------------------------------------------- answers

    /** An answer to something addressed to the speaker. */
    @Serializable
    @SerialName("answer")
    data class Answer(override val by: String, val to: String, val says: Says) : TableTalk {
        @Serializable
        enum class Says {
            @SerialName("yes")
            YES,

            @SerialName("no")
            NO,

            @SerialName("wait")
            WAIT,

            /** A bot declining with its reason, which is more use than a bare refusal. */
            @SerialName("worse")
            THAT_LEAVES_US_WORSE,
        }
    }
}
