package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardValue

/**
 * The cheap decisions — the ones where a search would be spending time to reach an answer
 * that is obvious.
 *
 * Ported from `legacy-web/packages/bot/src/lib/mcts-bot-heuristics.ts`.
 */

/** A card worth this much is worth swapping an Ace out for. */
private const val WORTH_SWAPPING_FOR = 8

/** Below this hand size, an opponent is close enough to calling that slowing them matters. */
private const val VULNERABLE_HAND_SIZE = 3

/** How far ahead an opponent must look before a defensive Ace is worth the turn. */
private const val DEFENSIVE_ACE_SCORE_GAP = 3

/**
 * Taking from the discard pile forces you to play the action, so it is only ever worth it
 * when the action is worth more than a free choice of card.
 */
fun shouldAlwaysTakeDiscardPeekCard(discardTop: Card?, botPlayer: PlayerState): Boolean {
    if (discardTop == null || discardTop.played) return false

    // A Queen peeks two cards from two players and may swap them. Nothing else comes close.
    if (discardTop.rank == Rank.QUEEN) return true

    // 7 and 8 are cheap cards whose peek is worth more than holding them — but only while
    // there is something left to look at.
    return (discardTop.rank == Rank.SEVEN || discardTop.rank == Rank.EIGHT) &&
        countUnknownCards(botPlayer) > 0
}

/** The same judgement for a card drawn from the deck, where swapping is also an option. */
fun shouldAlwaysUsePeekAction(drawnCard: Card, botPlayer: PlayerState): Boolean {
    if (drawnCard.actionText.isNullOrEmpty() || drawnCard.played) return false

    if (drawnCard.rank == Rank.QUEEN) return true

    return (drawnCard.rank == Rank.SEVEN || drawnCard.rank == Rank.EIGHT) &&
        countUnknownCards(botPlayer) > 0
}

/**
 * Whether to play an Ace's force-draw rather than swap the Ace into hand.
 *
 * **This deviates from TypeScript deliberately.** There, the decision summed every
 * opponent's actual cards — reading hidden hands, in the one bot engine that was kept
 * *because* the other one did that (`docs/bot/BOT-ENGINE-DECISION.md`). It was the only such
 * read left in the bot, and it does not survive the port: a bot that sees through the table
 * is not a difficulty setting, and once bots run server-side (D9) nothing but this code
 * stops them.
 *
 * The estimate now comes from [BotMemory] — what the bot has actually seen — which is the
 * same substitution the rest of the bot already makes.
 */
fun shouldUseAceAction(
    botPlayer: PlayerState,
    allPlayers: List<PlayerState>,
    botId: String,
    botMemory: BotMemory,
): Boolean {
    // A high card the bot knows it holds is worth more gone than an Ace's action.
    val maxKnownValue = botPlayer.knownCardPositions
        .mapNotNull { botPlayer.cards.getOrNull(it)?.value }
        .maxOrNull() ?: 0
    if (maxKnownValue >= WORTH_SWAPPING_FOR) return false

    val botScore = estimatePlayerScore(botPlayer.cards.size, botMemory, botId)

    // Forcing a draw is a defensive move: it is worth a turn only against someone who looks
    // close to calling — a short hand and a better score than ours.
    return allPlayers.any { player ->
        if (player.id == botId) return@any false
        val opponentScore = estimatePlayerScore(player.cards.size, botMemory, player.id)
        opponentScore < botScore - DEFENSIVE_ACE_SCORE_GAP &&
            player.cards.size <= VULNERABLE_HAND_SIZE
    }
}

/**
 * Tossing in is free value whenever the bot *believes* it holds a matching rank: the card
 * leaves the hand and the score drops. Only believed cards count — guessing costs a penalty
 * card and ends the bot's participation for the round — and a belief that is wrong pays
 * that same price, which is what makes a weak memory a real handicap.
 */
fun shouldParticipateInTossIn(
    discardedRanks: List<Rank>,
    botPlayer: PlayerState,
    believed: Map<Int, Rank> = botPlayer.knownCardPositions
        .filter { it in botPlayer.cards.indices }
        .associateWith { botPlayer.cards[it].rank },
): Boolean {
    val ranksToCheck = discardedRanks.filter { getCardValue(it) >= 0 }

    return believed.any { (position, rank) ->
        position in botPlayer.cards.indices && rank in ranksToCheck
    }
}

fun countUnknownCards(player: PlayerState): Int =
    player.cards.size - player.knownCardPositions.size

fun calculateHandScore(cards: List<Card>): Int = cards.sumOf { it.value }
