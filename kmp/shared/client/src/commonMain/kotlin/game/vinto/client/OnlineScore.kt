package game.vinto.client

/**
 * What the round paid, derived from public facts alone.
 *
 * A remote client needs this at the moment a round ends: the wire has delivered the scoring
 * view — every hand's total and who called — but the room's own `RoundResult` only arrives
 * with the next `between-rounds`, and a score sheet that waits for it is a score sheet that
 * opens empty. The pay is a pure function of the totals and the caller (`VINTO_RULES.md`
 * §Scoring), so the client computes it: caller at-or-under the best coalition hand pays the
 * caller +3 and each member −1; a tie pays the members 0; a coalition win pays them +3 and
 * the caller −1. A round nobody called (the deck ran out) pays nothing.
 *
 * `OnlineScoreTest` holds this against the engine's own `calculateRoundPoints` over whole
 * played games, so the duplication cannot drift silently.
 */
fun roundPoints(scores: Map<String, Int>, callerId: String?): Map<String, Int> {
    val caller = callerId?.let { scores[it] } ?: return scores.mapValues { 0 }
    val bestCoalition = scores.filterKeys { it != callerId }.values.minOrNull()
        ?: return scores.mapValues { 0 }
    val callerWins = caller <= bestCoalition

    return scores.mapValues { (id, _) ->
        when {
            id == callerId -> if (callerWins) CALLER_WIN else LOSS
            !callerWins -> COALITION_WIN
            caller == bestCoalition -> 0
            else -> LOSS
        }
    }
}

private const val CALLER_WIN = 3
private const val COALITION_WIN = 3
private const val LOSS = -1
