package game.vinto.bot

import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardValue

/**
 * Tossing in is free value whenever the bot *believes* it holds a matching rank: the card
 * leaves the hand and the score drops. Only believed cards count — guessing costs a penalty
 * card and bars the bot from the window — and a belief that is wrong pays that same price,
 * which is what makes a weak memory a real handicap. The one card never worth throwing is
 * the Joker, which is worth less than nothing to hold.
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
