package game.vinto.shapes

/**
 * Per-rank configuration — values, actions and the player-facing copy.
 *
 * Ported verbatim from `legacy-web/packages/shapes/src/lib/constants.ts` so every client shows the
 * same words. The values are rules, not presentation: King is 0, Ace is 1, Joker is -1,
 * and every action card (7-Q) is worth 10 except where the rank says otherwise.
 */
data class CardConfig(
    val rank: Rank,
    val name: String,
    val value: Int,
    val shortDescription: String,
    val longDescription: String,
    val helpText: String,
    val action: CardAction? = null,
)

private const val PEEK_OWN_HELP =
    "Click on one of your cards to temporarily reveal it and memorize its value. " +
        "You can skip this action if you prefer."

private const val PEEK_OPPONENT_HELP =
    "Click on an opponent's card to temporarily reveal it and see its value. " +
        "You can skip this action if you prefer."

val CARD_CONFIGS: Map<Rank, CardConfig> = mapOf(
    Rank.TWO to CardConfig(Rank.TWO, "Two", 2, "", "", "Number card with value 2"),
    Rank.THREE to CardConfig(Rank.THREE, "Three", 3, "", "", "Number card with value 3"),
    Rank.FOUR to CardConfig(Rank.FOUR, "Four", 4, "", "", "Number card with value 4"),
    Rank.FIVE to CardConfig(Rank.FIVE, "Five", 5, "", "", "Number card with value 5"),
    Rank.SIX to CardConfig(Rank.SIX, "Six", 6, "", "", "Number card with value 6"),
    Rank.SEVEN to CardConfig(
        rank = Rank.SEVEN,
        name = "Seven",
        value = 7,
        shortDescription = "Peek 1 of your cards",
        longDescription = "Peek at one of your own cards",
        helpText = PEEK_OWN_HELP,
        action = CardAction.PEEK_OWN,
    ),
    Rank.EIGHT to CardConfig(
        rank = Rank.EIGHT,
        name = "Eight",
        value = 8,
        shortDescription = "Peek 1 of your cards",
        longDescription = "Peek at one of your own cards",
        helpText = PEEK_OWN_HELP,
        action = CardAction.PEEK_OWN,
    ),
    Rank.NINE to CardConfig(
        rank = Rank.NINE,
        name = "Nine",
        value = 9,
        shortDescription = "Peek 1 opponent card",
        longDescription = "Peek at one card of another player",
        helpText = PEEK_OPPONENT_HELP,
        action = CardAction.PEEK_OPPONENT,
    ),
    Rank.TEN to CardConfig(
        rank = Rank.TEN,
        name = "Ten",
        value = 10,
        shortDescription = "Peek 1 opponent card",
        longDescription = "Peek at one card of another player",
        helpText = PEEK_OPPONENT_HELP,
        action = CardAction.PEEK_OPPONENT,
    ),
    Rank.JACK to CardConfig(
        rank = Rank.JACK,
        name = "Jack",
        value = 10,
        shortDescription = "Swap 2 face-down cards from 2 players",
        longDescription = "Swap any two facedown cards from two different players",
        helpText = "Select two cards from different players to swap their positions - " +
            "you can include your own cards or only opponents. You cannot select two cards " +
            "from the same player. You can reset your selection or skip this action.",
        action = CardAction.SWAP_CARDS,
    ),
    Rank.QUEEN to CardConfig(
        rank = Rank.QUEEN,
        name = "Queen",
        value = 10,
        shortDescription = "Peek 2 cards from 2 players, swap optional",
        longDescription =
        "Peek at any two cards from two different players, then optionally swap them",
        helpText = "1. Peek at two cards from different players (can be your own or " +
            "opponents, but must be from different players)\n2. After peeking both cards, " +
            "decide whether to swap them",
        action = CardAction.PEEK_AND_SWAP,
    ),
    Rank.KING to CardConfig(
        rank = Rank.KING,
        name = "King",
        value = 0,
        shortDescription = "Declare any card's action",
        longDescription = "Declare another card action from any player (7-A)",
        helpText = "Choose which card action to execute: 7 (peek own), 8 (peek own), " +
            "9/10 (peek opponent), J (swap), Q (peek & swap), A (force draw). You can also " +
            "declare non-action cards (2-6, K, Joker).",
        action = CardAction.DECLARE_ACTION,
    ),
    Rank.ACE to CardConfig(
        rank = Rank.ACE,
        name = "Ace",
        value = 1,
        shortDescription = "Force opponent to draw",
        longDescription = "Force an opponent to draw a penalty card",
        helpText = "Select an opponent to force them to draw a penalty card from the deck. " +
            "This increases their hand size and card total. You can skip this action if you prefer.",
        action = CardAction.FORCE_DRAW,
    ),
    Rank.JOKER to CardConfig(Rank.JOKER, "Joker", -1, "", "", "Joker with value -1"),
)

fun getCardConfig(rank: Rank): CardConfig =
    CARD_CONFIGS[rank] ?: error("no config for rank $rank")

fun getCardShortDescription(rank: Rank): String = getCardConfig(rank).shortDescription

fun getCardLongDescription(rank: Rank): String = getCardConfig(rank).longDescription

fun getCardHelpText(rank: Rank): String = getCardConfig(rank).helpText

fun getCardValue(rank: Rank): Int = getCardConfig(rank).value

fun getCardAction(rank: Rank): CardAction? = getCardConfig(rank).action

fun getCardName(rank: Rank): String = getCardConfig(rank).name

/** Mirrors TypeScript's `hasAction`, which keys off the short description being non-empty. */
fun hasAction(rank: Rank): Boolean = getCardConfig(rank).shortDescription != ""
