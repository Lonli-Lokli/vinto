package game.vinto.bot

import game.vinto.shapes.Rank
import kotlin.random.Random

/**
 * How a simulation plays out a position once it leaves the tree.
 *
 * A pure-random rollout would tell the search almost nothing here: Vinto has enough legal
 * but pointless moves that random play looks the same from a good position as from a bad
 * one. So rollouts are *prioritised* — end the game when winning, gather information,
 * shed points, defend — and only fall back to random when none of those apply. The point is
 * not to play well, it is to play plausibly enough that the resulting score means something.
 *
 * Ported from `packages/bot/src/lib/mcts-rollout-policy.ts`.
 */


/** How far ahead the bot must be before ending the game is clearly right. */
private const val WINNING_MARGIN = 5

/** Rollouts take a peek most of the time, but not always — variety is the point of a rollout. */
private const val PEEK_PROBABILITY = 0.75

/** A hand this short is close enough to winning to be worth attacking. */
private const val NEARLY_WINNING_HAND = 2

/** Worth swapping away. */
private const val EXPENSIVE_CARD = 9

/** Worth swapping in. */
private const val CHEAP_CARD = 3

fun selectRolloutMove(state: MctsGameState, moves: List<MctsMove>, random: Random): MctsMove? {
    if (moves.isEmpty()) return null
    val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return moves.first()

    return selectGameEndingMove(state, moves, currentPlayer)
        ?: selectInfoGatheringMove(state, moves, currentPlayer, random)
        ?: selectScoreReductionMove(state, moves, currentPlayer)
        ?: selectDefensiveMove(state, moves, currentPlayer)
        ?: moves[random.nextInt(moves.size)]
}

/** Calling Vinto from in front, or shedding a last card, ends the game on your terms. */
private fun selectGameEndingMove(
    state: MctsGameState,
    moves: List<MctsMove>,
    currentPlayer: MctsPlayerState,
): MctsMove? {
    val vintoMoves = moves.filter { it.type == MctsMoveType.CALL_VINTO }
    if (vintoMoves.isNotEmpty()) {
        val opponentScores = state.players.filter { it.id != currentPlayer.id }.map { it.score }
        val averageOpponentScore =
            if (opponentScores.isEmpty()) 0.0 else opponentScores.sum() / opponentScores.size

        if (currentPlayer.score < averageOpponentScore - WINNING_MARGIN) return vintoMoves.first()
    }

    val tossInMoves = moves.filter { it.type == MctsMoveType.TOSS_IN }
    if (tossInMoves.isNotEmpty() && currentPlayer.cardCount == 1) return tossInMoves.first()

    return null
}

/** Shedding a card you are sure of, or looking at one you are not. */
private fun selectInfoGatheringMove(
    state: MctsGameState,
    moves: List<MctsMove>,
    currentPlayer: MctsPlayerState,
    random: Random,
): MctsMove? {
    val discardRank = state.discardPileTop?.rank
    if (discardRank != null) {
        val certainTossIn = moves
            .filter { it.type == MctsMoveType.TOSS_IN }
            .firstOrNull { move ->
                move.tossInPositions.any { position ->
                    val memory = currentPlayer.knownCards[position]
                    val card = state.hiddenCards[state.hiddenCardKey(currentPlayer.id, position)]
                    memory != null && memory.confidence > TRUSTED_CONFIDENCE &&
                        card != null && card.rank == discardRank
                }
            }
        if (certainTossIn != null) return certainTossIn
    }

    val pendingRank = state.pendingCard?.rank
    val peekMoves = moves.filter {
        it.type == MctsMoveType.USE_ACTION &&
            (pendingRank == Rank.SEVEN || pendingRank == Rank.EIGHT || pendingRank == Rank.QUEEN)
    }

    if (peekMoves.isNotEmpty() && random.nextDouble() < PEEK_PROBABILITY) {
        return peekMoves[random.nextInt(peekMoves.size)]
    }

    return null
}

/** Trading something expensive for something cheap, when the bot is sure of both. */
private fun selectScoreReductionMove(
    state: MctsGameState,
    moves: List<MctsMove>,
    currentPlayer: MctsPlayerState,
): MctsMove? = moves
    .filter { it.type == MctsMoveType.SWAP }
    .firstOrNull { move ->
        val position = move.swapPosition ?: return@firstOrNull false
        val oldCard = state.hiddenCards[state.hiddenCardKey(currentPlayer.id, position)]
        val newCard = state.pendingCard
        val memory = currentPlayer.knownCards[position]

        oldCard != null && newCard != null && memory != null &&
            memory.confidence > TRUSTED_CONFIDENCE &&
            oldCard.value > EXPENSIVE_CARD && newCard.value < CHEAP_CARD
    }

/** An Ace aimed at whoever is closest to going out. */
private fun selectDefensiveMove(
    state: MctsGameState,
    moves: List<MctsMove>,
    currentPlayer: MctsPlayerState,
): MctsMove? {
    val closeToWinning = state.players
        .filter { it.id != currentPlayer.id && it.cardCount <= NEARLY_WINNING_HAND }
    if (closeToWinning.isEmpty()) return null

    if (state.pendingCard?.rank != Rank.ACE) return null
    val aceMoves = moves.filter { it.type == MctsMoveType.USE_ACTION }
    if (aceMoves.isEmpty()) return null

    val target = closeToWinning.minBy { it.cardCount }
    return aceMoves.firstOrNull { it.targets.firstOrNull()?.playerId == target.id }
}
