package game.vinto.client

import game.vinto.shapes.VintoJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * What this player has done, kept on the device.
 *
 * Deliberately small, and deliberately nowhere else. There is no account, no sync and no
 * server involved: this is four numbers in the same vault the settings live in, and the whole
 * privacy cost of it is that a phone remembers something about its owner. That is also why it
 * is separate from the anonymous counts in `AnalyticsEvent` — those go somewhere and are
 * stripped of everything identifying to do so; these stay here and can therefore be personal.
 *
 * It exists because a game made of sessions is worth opening twice. A win rate and a streak
 * are the difference between "I played that once" and "I am four rounds into something".
 */
@Serializable
data class Stats(
    @SerialName("version") val version: Int = FORMAT,

    /** Rounds played to the score sheet. A round walked out of does not count. */
    val roundsPlayed: Int = 0,

    /** Rounds where nobody at the table finished lower. */
    val roundsWon: Int = 0,

    /**
     * The lowest hand ever finished on, or null before the first round.
     *
     * A hand rather than a round's points, because "I got down to 2" is the thing a player
     * remembers and tells somebody, and ±3 for winning is not.
     */
    val bestHand: Int? = null,

    /** Rounds won in a row, right now. */
    val streak: Int = 0,

    /** The longest that streak has ever been. */
    val bestStreak: Int = 0,
) {
    /** Rounds won as a percentage, or null before there is anything to divide. */
    val winRate: Int? get() = if (roundsPlayed == 0) null else roundsWon * PERCENT / roundsPlayed

    /**
     * This record, plus one finished round.
     *
     * Pure, so the rule is testable without a vault, a screen or a game — and so "did the
     * streak break" is a question with an answer rather than a sequence of writes.
     */
    fun plus(hand: Int, won: Boolean): Stats {
        val nowStreak = if (won) streak + 1 else 0
        return copy(
            roundsPlayed = roundsPlayed + 1,
            roundsWon = roundsWon + if (won) 1 else 0,
            bestHand = listOfNotNull(bestHand, hand).min(),
            streak = nowStreak,
            bestStreak = maxOf(bestStreak, nowStreak),
        )
    }

    companion object {
        /** Bumped when the shape changes; an older file is replaced by an empty record. */
        const val FORMAT = 1

        private const val PERCENT = 100
    }
}

private const val KEY = "vinto.stats"

/**
 * What the player has done, or nothing yet.
 *
 * Never throws, for the same reason [loadSettings] does not: a file this app wrote badly must
 * not be a reason it cannot start. Losing a streak to a corrupt file is a small sadness;
 * refusing to open is not.
 */
fun Vault.loadStats(): Stats {
    val stored = read(KEY) ?: return Stats()

    return try {
        VintoJson.decodeFromString(Stats.serializer(), stored)
            .takeIf { it.version == Stats.FORMAT } ?: Stats()
    } catch (_: SerializationException) {
        Stats()
    } catch (_: IllegalArgumentException) {
        Stats()
    }
}

/** Writes the record. One small string, written when a round ends. */
fun Vault.saveStats(stats: Stats) {
    write(KEY, VintoJson.encodeToString(Stats.serializer(), stats))
}

/** Throws it all away, for a player who asks. */
fun Vault.forgetStats() {
    erase(KEY)
}

/**
 * This record, plus the round on the table.
 *
 * "Won" is *finished lowest*, not "took the round's points". They are different and the
 * difference matters here: a Vinto caller who ties takes +3 under the rules while somebody
 * else was level with them, and a player told they won a round they did not finish lowest in
 * would rightly not believe the rest of these numbers either. Points are the game; this is
 * the hand.
 *
 * Null when the viewer is not seated in the result, which is a table being watched rather
 * than played.
 */
fun Stats.plus(result: RoundResult, viewerId: String): Stats? {
    val mine = result.hands[viewerId] ?: return null
    val best = result.hands.values.min()
    return plus(hand = mine, won = mine == best)
}
