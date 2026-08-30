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

/**
 * How a round came out, in the terms the rules use.
 *
 * The score sheet used to open with "Round 3" and a table of numbers, and left the player to
 * work out from a column of +3 and −1 which side had actually won. That is a arithmetic
 * exercise at the exact moment somebody wants an answer — and it is the one thing the web
 * client did better, where the round ends on a sentence naming the winner and the two totals
 * that decided it.
 *
 * A type rather than a string, for the reason every other player-facing message here is one:
 * the words are the UI's business and the *result* is the model's, so this can be checked
 * without asserting on English.
 */
sealed interface RoundOutcome {
    /** The caller finished at or under the best of the others, and takes +3. */
    data class CallerWon(val caller: Int, val best: Int) : RoundOutcome

    /** Level: the caller still takes +3, and the others take nothing rather than losing one. */
    data class Level(val caller: Int, val best: Int) : RoundOutcome

    /** Somebody beat the caller. The coalition takes +3 each and the caller −1. */
    data class CoalitionWon(val caller: Int, val best: Int) : RoundOutcome

    /** Nobody called; the deck ran out. Every hand is counted and nothing is paid. */
    data object DeckRanOut : RoundOutcome
}

/** Reads [RoundOutcome] off the same public facts [roundPoints] uses. */
fun outcomeOf(scores: Map<String, Int>, callerId: String?): RoundOutcome {
    val caller = callerId?.let { scores[it] } ?: return RoundOutcome.DeckRanOut
    val best = scores.filterKeys { it != callerId }.values.minOrNull()
        ?: return RoundOutcome.DeckRanOut

    return when {
        caller < best -> RoundOutcome.CallerWon(caller, best)
        caller == best -> RoundOutcome.Level(caller, best)
        else -> RoundOutcome.CoalitionWon(caller, best)
    }
}

/**
 * Whose hand the round was decided against: the lowest of the coalition's.
 *
 * A set rather than one id, because two players can tie on the same total and marking one of
 * them would be picking a winner the rules do not pick. Empty when nobody called, since with
 * no caller there is no hand for the others to be measured against.
 */
fun bestCoalitionHands(scores: Map<String, Int>, callerId: String?): Set<String> {
    if (callerId == null) return emptySet()
    val others = scores.filterKeys { it != callerId }
    val best = others.values.minOrNull() ?: return emptySet()
    return others.filterValues { it == best }.keys
}
