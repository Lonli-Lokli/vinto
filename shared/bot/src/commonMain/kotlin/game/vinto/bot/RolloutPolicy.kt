package game.vinto.bot

import game.vinto.shapes.Rank
import kotlin.random.Random

/**
 * How a simulation plays out a position once it leaves the tree.
 *
 * A rollout is a policy, not an evaluation: it only has to play plausibly enough that where
 * the round ends up says something about the position it started from. So every choice here
 * is a comparison of card values in the world being simulated, and none is a weight —
 * shed the dearest card for a cheaper one, use an action that is worth using, call Vinto
 * when you hold the lowest hand and know it. Where nothing decides, the move is random.
 */
fun selectRolloutMove(state: MctsGameState, moves: List<MctsMove>, random: Random): MctsMove? {
    if (moves.isEmpty()) return null
    if (moves.size == 1) return moves.first()
    val player = state.currentPlayer ?: return moves.first()

    return when {
        state.awaitingVintoDecision -> vintoDecision(state, moves, player)
        state.isTossInPhase -> moves.firstOrNull { it.type == MctsMoveType.TOSS_IN } ?: moves.first()
        state.pendingCard != null -> pendingCardPolicy(state, moves, player)
        else -> moves[random.nextInt(moves.size)]
    }
}

/**
 * Call when this hand is the lowest at the table — a tie goes to the caller — and its owner
 * knows it. The searching bot knows only what it has read; everybody else is assumed to know
 * their own hand, which is the same assumption the transition makes.
 */
private fun vintoDecision(state: MctsGameState, moves: List<MctsMove>, player: MctsPlayerState): MctsMove {
    val call = moves.firstOrNull { it.type == MctsMoveType.CALL_VINTO } ?: return moves.first()
    val pass = moves.firstOrNull { it.type == MctsMoveType.PASS } ?: call

    if (player.id == state.botPlayerId && MoveGenerator.unknownPositions(player).isNotEmpty()) return pass

    val mine = StateTransition.handTotal(state, player.id)
    val lowestOther = state.players
        .filter { it.id != player.id }
        .minOfOrNull { StateTransition.handTotal(state, it.id) }
        ?: return pass
    return if (mine <= lowestOther) call else pass
}

/**
 * With a card in play: trade it for the dearest card the mover can name if that sheds
 * points; otherwise play its action if the action is worth playing; otherwise put a cheap
 * card into a blind slot, or discard.
 */
private fun pendingCardPolicy(
    state: MctsGameState,
    moves: List<MctsMove>,
    player: MctsPlayerState,
): MctsMove {
    val pending = state.pendingCard ?: return moves.first()
    val isBot = player.id == state.botPlayerId

    val values = (0 until player.cardCount).mapNotNull { position ->
        val known = isBot && MoveGenerator.knownCards(player).containsKey(position)
        val dealt = state.hiddenCards[state.hiddenCardKey(player.id, position)]
        if (dealt != null && (!isBot || known)) position to dealt.value else null
    }
    val dearest = values.maxByOrNull { it.second }
    if (dearest != null && dearest.second > pending.value) {
        moves.firstOrNull { it.type == MctsMoveType.SWAP && it.swapPosition == dearest.first }
            ?.let { return it }
    }

    moves.firstOrNull { it.type == MctsMoveType.USE_ACTION && worthUsing(state, it) }?.let { return it }

    if (isBot && pending.value < averageRemainingCardValue(state.botMemory)) {
        val blind = MoveGenerator.unknownPositions(player).firstOrNull()
        moves.firstOrNull { it.type == MctsMoveType.SWAP && it.swapPosition == blind }?.let { return it }
    }

    return moves.firstOrNull { it.type == MctsMoveType.DISCARD } ?: moves.first()
}

/** A Jack is worth playing when the trade it names sheds the mover's points; anything else, always. */
private fun worthUsing(state: MctsGameState, move: MctsMove): Boolean {
    if (state.pendingCard?.rank != Rank.JACK || move.shouldSwap == false) return move.shouldSwap != false
    val own = move.targets.firstOrNull { it.playerId == move.playerId } ?: return false
    val theirs = move.targets.firstOrNull { it.playerId != move.playerId } ?: return false
    val ownValue = state.hiddenCards[state.hiddenCardKey(own.playerId, own.position)]?.value
    val theirValue = state.hiddenCards[state.hiddenCardKey(theirs.playerId, theirs.position)]?.value
    return ownValue != null && theirValue != null && ownValue > theirValue
}
