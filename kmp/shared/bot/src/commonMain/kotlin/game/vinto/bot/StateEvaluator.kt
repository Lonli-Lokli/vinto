package game.vinto.bot

/**
 * Scoring a position, 0-1, from the bot's point of view.
 *
 * Ported from `packages/bot/src/lib/mcts-state-evaluator.ts` and
 * `mcts-coalition-evaluator.ts`.
 */
fun evaluateState(state: MctsGameState, botPlayerId: String): Double {
    val botPlayer = state.players.firstOrNull { it.id == botPlayerId } ?: return 0.0

    if (state.isTerminal) return evaluateTerminalState(state, botPlayerId)

    // In the final round the coalition wins or loses together, so every member evaluates the
    // coalition's chances rather than their own — a member with a hopeless hand should still
    // play to help whoever can actually beat the caller.
    val inCoalitionMode = state.vintoCallerId != null && state.coalitionLeaderId != null
    val isCoalitionMember = botPlayerId != state.vintoCallerId
    if (inCoalitionMode && isCoalitionMember) return evaluateCoalitionState(state)

    return evaluateNormalState(state, botPlayer)
}

private fun evaluateTerminalState(state: MctsGameState, botPlayerId: String): Double {
    if (state.vintoCallerId != null && state.coalitionLeaderId != null) {
        // Anyone but the caller winning is a coalition victory.
        return if (state.winner != state.vintoCallerId) 1.0 else 0.0
    }
    return if (state.winner == botPlayerId) 1.0 else 0.0
}

/** Five components, weighted. Toss-in potential dominates because it compounds. */
private fun evaluateNormalState(state: MctsGameState, botPlayer: MctsPlayerState): Double {
    val score = evaluateTossInPotential(botPlayer) * 0.3 +
        evaluateRelativePosition(state, botPlayer) * 0.25 +
        evaluateActionCardValue(botPlayer) * 0.2 +
        evaluateInformationAdvantage(state, botPlayer) * 0.15 +
        evaluateThreatLevel(state, botPlayer) * 0.1

    return score.coerceIn(0.0, 1.0)
}

/**
 * The coalition needs exactly one member to beat the caller, so it is scored on its
 * *champion* — the lowest hand among them — rather than on any average.
 */
fun evaluateCoalitionState(state: MctsGameState): Double {
    val vintoCallerId = state.vintoCallerId ?: return 0.0
    val vintoPlayer = state.players.firstOrNull { it.id == vintoCallerId } ?: return 0.0
    val champion = findCoalitionChampion(state, vintoCallerId) ?: return 0.0

    val scoreAdvantage = ((vintoPlayer.score - champion.score + 10) / 30).coerceIn(0.0, 1.0)
    val cardAdvantage =
        ((vintoPlayer.cardCount - champion.cardCount + 2).toDouble() / 5).coerceIn(0.0, 1.0)
    val championTossIn = evaluateTossInPotential(champion)
    val vintoThreat = 1.0 - evaluateActionCardValue(vintoPlayer)

    val coalitionScore = scoreAdvantage * 0.4 +
        cardAdvantage * 0.3 +
        championTossIn * 0.2 +
        vintoThreat * 0.1

    return coalitionScore.coerceIn(0.0, 1.0)
}

/** The member with the best chance of winning, which is the one with the lowest hand. */
fun findCoalitionChampion(state: MctsGameState, vintoCallerId: String): MctsPlayerState? =
    state.players.filter { it.id != vintoCallerId }.minByOrNull { it.score }
