package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameState
import game.vinto.shapes.VintoJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/**
 * A game in progress, written down.
 *
 * The whole state rather than the seed and a list of moves. Replaying from a seed is tempting
 * — the engine is deterministic and the log is already kept — but it restores the *cards* and
 * not the *bots*: their memories of what they have seen are rebuilt as they play, and a replay
 * would hand you three opponents who had forgotten the round they were in the middle of. The
 * state is a few kilobytes; the cheap answer is the right one.
 *
 * @param round which round of the session this is, counting from one.
 * @param standings cumulative points per player across finished rounds — the session's score,
 *   not this round's.
 * @param state the round in progress, or null if the last one finished and the next has not
 *   been dealt.
 */
@Serializable
data class SavedGame(
    @SerialName("version") val version: Int = FORMAT,
    val difficulty: Difficulty,
    val seed: Long,
    val round: Int,
    val standings: Map<String, Int>,
    val state: GameState? = null,
) {
    companion object {
        /**
         * Bumped when the shape changes.
         *
         * A saved game from an older version is discarded rather than migrated: this is one
         * unfinished round of a solo card game, and the cost of losing it is a deal. Migration
         * code for that is code written to protect nothing.
         */
        const val FORMAT = 1
    }
}

/** Where a local game lives, and the only key this app writes. */
private const val KEY = "vinto.local.game"

/**
 * Reads the saved game, or null if there is none, it is unreadable, or it is from a format
 * this build no longer understands.
 *
 * Never throws. A corrupt save is a save that gets thrown away — the alternative is an app
 * that cannot start because of something it wrote itself.
 */
fun Vault.loadGame(): SavedGame? {
    val stored = read(KEY) ?: return null

    return try {
        VintoJson.decodeFromString(SavedGame.serializer(), stored)
            .takeIf { it.version == SavedGame.FORMAT }
    } catch (failure: SerializationException) {
        // Swallowed on purpose, and the save goes with it. There is nothing upstream that
        // could act on the reason: a save this build cannot read is one unfinished round of a
        // solo card game, and the cost of discarding it is a deal. Keeping it would mean
        // failing the same way on every launch.
        discard(failure)
        null
    } catch (failure: IllegalArgumentException) {
        discard(failure)
        null
    }
}

private fun Vault.discard(@Suppress("UNUSED_PARAMETER") reason: Throwable) = erase(KEY)

fun Vault.saveGame(game: SavedGame) {
    write(KEY, VintoJson.encodeToString(SavedGame.serializer(), game))
}

fun Vault.forgetGame() = erase(KEY)
