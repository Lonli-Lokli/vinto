package game.vinto.shapes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The string values are the wire format and must match `legacy-web/packages/shapes/src/lib` exactly —
 * a recording written by either implementation is read by the other. Never rename a
 * `@SerialName` without changing the TypeScript union and regenerating the corpus.
 */

@Serializable
enum class Rank(val serialName: String) {
    @SerialName("2") TWO("2"),
    @SerialName("3") THREE("3"),
    @SerialName("4") FOUR("4"),
    @SerialName("5") FIVE("5"),
    @SerialName("6") SIX("6"),
    @SerialName("7") SEVEN("7"),
    @SerialName("8") EIGHT("8"),
    @SerialName("9") NINE("9"),
    @SerialName("10") TEN("10"),
    @SerialName("J") JACK("J"),
    @SerialName("Q") QUEEN("Q"),
    @SerialName("K") KING("K"),
    @SerialName("A") ACE("A"),
    @SerialName("Joker") JOKER("Joker");

    companion object {
        private val byName = entries.associateBy { it.serialName }

        fun of(serialName: String): Rank =
            byName[serialName] ?: error("unknown rank '$serialName'")
    }
}

/** `ALL_RANKS` in TypeScript. Declaration order is the wire order; keep them aligned. */
val ALL_RANKS: List<Rank> = Rank.entries.toList()

@Serializable
enum class Difficulty(val serialName: String) {
    @SerialName("easy") EASY("easy"),
    @SerialName("moderate") MODERATE("moderate"),
    @SerialName("hard") HARD("hard"),
}

@Serializable
enum class CardAction(val serialName: String) {
    @SerialName("peek-own") PEEK_OWN("peek-own"),
    @SerialName("peek-opponent") PEEK_OPPONENT("peek-opponent"),
    @SerialName("peek-and-swap") PEEK_AND_SWAP("peek-and-swap"),
    @SerialName("swap-cards") SWAP_CARDS("swap-cards"),
    @SerialName("force-draw") FORCE_DRAW("force-draw"),
    @SerialName("declare-action") DECLARE_ACTION("declare-action"),
}

@Serializable
enum class GamePhase(val serialName: String) {
    @SerialName("setup") SETUP("setup"),
    @SerialName("playing") PLAYING("playing"),
    @SerialName("final") FINAL("final"),
    @SerialName("scoring") SCORING("scoring"),
}

/** Turn progression: idle → drawing/choosing → selecting → awaiting_action → toss_* → idle. */
@Serializable
enum class GameSubPhase(val serialName: String) {
    @SerialName("idle") IDLE("idle"),
    @SerialName("drawing") DRAWING("drawing"),
    @SerialName("choosing") CHOOSING("choosing"),
    @SerialName("selecting") SELECTING("selecting"),
    @SerialName("awaiting_action") AWAITING_ACTION("awaiting_action"),
    @SerialName("ai_thinking") AI_THINKING("ai_thinking"),
    @SerialName("toss_queue_active") TOSS_QUEUE_ACTIVE("toss_queue_active"),
    @SerialName("toss_queue_processing") TOSS_QUEUE_PROCESSING("toss_queue_processing"),
}

@Serializable
enum class ActionPhase(val serialName: String) {
    @SerialName("choosing-action") CHOOSING_ACTION("choosing-action"),
    @SerialName("selecting-target") SELECTING_TARGET("selecting-target"),
}

@Serializable
enum class TargetType(val serialName: String) {
    @SerialName("own-card") OWN_CARD("own-card"),
    @SerialName("opponent-card") OPPONENT_CARD("opponent-card"),
    @SerialName("peek-then-swap") PEEK_THEN_SWAP("peek-then-swap"),
    @SerialName("swap-cards") SWAP_CARDS("swap-cards"),
    @SerialName("force-draw") FORCE_DRAW("force-draw"),
    @SerialName("declare-action") DECLARE_ACTION("declare-action"),
}

/** Where a pending card came from — `'drawing' | 'hand'` in TypeScript. */
@Serializable
enum class PendingCardOrigin(val serialName: String) {
    @SerialName("drawing") DRAWING("drawing"),
    @SerialName("hand") HAND("hand"),
}

@Serializable
enum class PlayerPosition(val serialName: String) {
    @SerialName("bottom") BOTTOM("bottom"),
    @SerialName("left") LEFT("left"),
    @SerialName("top") TOP("top"),
    @SerialName("right") RIGHT("right"),
}

/**
 * Whether a rank carries an action. Mirrors `isRankActionable`; exhaustive `when` over the
 * enum so adding a rank is a compile error here rather than a silent `false`.
 */
fun Rank.isActionable(): Boolean = when (this) {
    Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.JOKER -> false
    Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
    Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
    -> true
}
