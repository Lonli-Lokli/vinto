package game.vinto.bot

/**
 * What a position is worth to each seat, 0 to 1, taken from the round's own scoring.
 *
 * Once somebody has called, the round is scored the way the rules score it: the caller
 * against the coalition's best hand, +3 / −1 / 0, mapped linearly onto 0 to 1. Before a
 * call there is nothing to score yet, so a seat is worth where it stands — the lowest hand
 * at the table is 1, the highest 0, the rest in proportion. Neither number is a weight: one
 * is the rule, the other is the rule's ordering of the hands.
 */
fun rewards(state: MctsGameState): DoubleArray {
    val totals = state.players.map { StateTransition.handTotal(state, it.id) }
    val callerIndex = state.vintoCallerId?.let { state.playerIndex(it) } ?: -1
    return if (callerIndex >= 0) roundPointsReward(totals, callerIndex) else standingReward(totals)
}

private const val CALLER_WIN_POINTS = 3
private const val LOSS_POINTS = -1
private const val COALITION_WIN_POINTS = 3
private const val COALITION_TIE_POINTS = 0

/** The span of round points, so +3 maps to 1 and −1 to 0. */
private const val POINT_SPAN = (CALLER_WIN_POINTS - LOSS_POINTS).toDouble()

private fun roundPointsReward(totals: List<Int>, callerIndex: Int): DoubleArray {
    val callerTotal = totals[callerIndex]
    val bestCoalition = totals.filterIndexed { index, _ -> index != callerIndex }.minOrNull()
        ?: return DoubleArray(totals.size) { 1.0 }
    val callerWins = callerTotal <= bestCoalition
    val tie = callerTotal == bestCoalition

    return DoubleArray(totals.size) { index ->
        val points = when {
            index == callerIndex -> if (callerWins) CALLER_WIN_POINTS else LOSS_POINTS
            tie -> COALITION_TIE_POINTS
            callerWins -> LOSS_POINTS
            else -> COALITION_WIN_POINTS
        }
        (points - LOSS_POINTS) / POINT_SPAN
    }
}

private const val LEVEL = 0.5

private fun standingReward(totals: List<Int>): DoubleArray {
    val lowest = totals.minOrNull() ?: return DoubleArray(0)
    val highest = totals.maxOrNull() ?: return DoubleArray(0)
    if (highest == lowest) return DoubleArray(totals.size) { LEVEL }
    val span = (highest - lowest).toDouble()
    return DoubleArray(totals.size) { index -> (highest - totals[index]) / span }
}
