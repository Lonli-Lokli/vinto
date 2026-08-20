package game.vinto.client

import game.vinto.engine.calculateFinalScores
import game.vinto.engine.calculateRoundPoints
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Prng
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.random.Random

/**
 * A local game: several rounds, one running score, and a save that survives being closed.
 *
 * A *round* is one deal, which is what [LocalGameSession] plays. A *game* is rounds until the
 * player stops, with points carried between them — which is what the rules describe and what
 * the online room does over its thirty minutes. Locally there is no clock and nobody to keep
 * waiting, so the session ends when the player says so.
 *
 * Everything is written down after every move. A card game that loses your round because the
 * phone rang is one you do not open again, and the round is a few kilobytes.
 */
class LocalGame private constructor(
    private val vault: Vault,
    val difficulty: Difficulty,
    private val seed: Long,
    round: Int,
    standings: Map<String, Int>,
    private val botDispatcher: CoroutineDispatcher?,
    resuming: game.vinto.shapes.GameState?,
) {
    /** Which round is being played, counting from one. */
    var round: Int = round
        private set

    /** Cumulative points across finished rounds. Empty until the first one ends. */
    var standings: Map<String, Int> = standings
        private set

    /** The round in progress. */
    var session: LocalGameSession = deal(resuming)
        private set

    val playerId: String get() = session.playerId

    /**
     * What the round on the table came to, or null while it is still being played.
     *
     * Both numbers, because they are different and easy to confuse: [RoundResult.hands] is
     * what was on the table, and [RoundResult.points] is what the round was worth under the
     * rules. A caller who finishes on 12 against a coalition's 9 loses the round while
     * holding the higher total, and a screen with only one of these makes that look wrong.
     */
    val result: RoundResult?
        get() = session.takeIf { it.isOver }?.state?.let { over ->
            RoundResult(
                callerId = over.vintoCallerId,
                hands = calculateFinalScores(over.players, over.vintoCallerId),
                points = calculateRoundPoints(over.players, over.vintoCallerId),
                seats = over.players.map { it.id to it.nickname },
            )
        }

    /**
     * Records the round just finished and deals the next one.
     *
     * Points come from the rules' own scoring — the caller is compared against the best hand
     * among everybody else, which is why a round is worth ±3 and ±1 rather than the totals on
     * the table.
     */
    fun nextRound() {
        val finished = session
        if (finished.isOver) {
            val earned = calculateRoundPoints(finished.state.players, finished.state.vintoCallerId)
            standings = (standings.keys + earned.keys).associateWith { id ->
                (standings[id] ?: 0) + (earned[id] ?: 0)
            }
        }

        // The counter moves whether or not the round was played out, because it is what picks
        // the deal. Leaving it where it was for an abandoned round would re-deal the identical
        // hand — the deterministic engine's one genuinely surprising behaviour.
        round++
        session = deal(resuming = null)
        save()
    }

    /** Writes the game down. Called after every move, and it is one small string. */
    fun save() {
        vault.saveGame(
            SavedGame(
                difficulty = difficulty,
                seed = seed,
                round = round,
                standings = standings,
                state = session.state,
            ),
        )
    }

    /** Ends the game for good, so the home screen stops offering to continue it. */
    fun abandon() = vault.forgetGame()

    private fun deal(resuming: game.vinto.shapes.GameState?) = LocalGameSession(
        seed = seedForRound(seed, round),
        difficulty = difficulty,
        botDispatcher = botDispatcher,
        random = Random(seedForRound(seed, round)),
        resuming = resuming,
    )

    companion object {
        /**
         * Starts a new game, discarding any saved one.
         *
         * @param seed picked by the caller, because choosing one is ambient randomness and
         *   belongs outside anything that has to replay.
         */
        fun start(
            vault: Vault,
            seed: Long,
            difficulty: Difficulty,
            botDispatcher: CoroutineDispatcher? = null,
        ): LocalGame = LocalGame(
            vault = vault,
            difficulty = difficulty,
            seed = seed,
            round = 1,
            standings = emptyMap(),
            botDispatcher = botDispatcher,
            resuming = null,
        ).also { it.save() }

        /** Picks up the saved game, or null if there is not one to pick up. */
        fun resume(vault: Vault, botDispatcher: CoroutineDispatcher? = null): LocalGame? {
            val saved = vault.loadGame() ?: return null

            return LocalGame(
                vault = vault,
                difficulty = saved.difficulty,
                seed = saved.seed,
                round = saved.round,
                standings = saved.standings,
                botDispatcher = botDispatcher,
                resuming = saved.state,
            )
        }
    }
}

/**
 * How a round ended.
 *
 * @param seats every player, in seating order, paired with the name to show. The scores are
 *   keyed by id and a screen needs names; carrying them together saves every caller from
 *   looking them up against a state it should not be reading.
 */
data class RoundResult(
    val callerId: String?,
    val hands: Map<String, Int>,
    val points: Map<String, Int>,
    val seats: List<Pair<String, String>>,
)

/**
 * The seed a given round is dealt from.
 *
 * Derived by advancing the session's generator once per round, so a whole game replays from
 * one number — the same rule the room uses, for the same reason: a bug report is a seed and a
 * round number rather than a file.
 */
internal fun seedForRound(sessionSeed: Long, round: Int): Long {
    var state = Prng.seed(sessionSeed)
    repeat(round) { state = Prng.next(state).state }
    return state
}
