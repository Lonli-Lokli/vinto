package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.calculateCardTotal
import game.vinto.engine.calculateRoundPoints
import game.vinto.engine.initializeGame
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A whole self-played game, and what it is worth.
 *
 * The gate ([SelfPlayGateTest]) and the tournament ([TournamentTest]) ask the same table two
 * different questions — *is it legal* and *is it any good* — so they play the same game. One
 * loop, one set of diagnostics, and no chance of the two drifting into playing subtly
 * different games and comparing the answers.
 */
internal data class PlayedGame(
    val seed: Long,
    val difficulty: Difficulty,
    val actions: Int,
    val finalPhase: GamePhase,
    val calledVinto: Boolean,
    /** Non-empty when the runner ran out of moves before `scoring`; says where it stopped. */
    val stalledAt: String = "",
    /** Non-empty when an action was refused; says which, by whom, and why. */
    val refused: String = "",
    /** Every seat's final card total, seat order. Empty unless the game reached `scoring`. */
    val handTotals: List<Int> = emptyList(),
    /** What each seat scored for the round, seat order. Empty unless the game reached `scoring`. */
    val roundPoints: List<Int> = emptyList(),
    /** The difficulty each seat played at, seat order. */
    val seatDifficulties: List<Difficulty> = emptyList(),
    /** True when a Vinto call was made *and* the caller was not beaten. */
    val callerWon: Boolean = false,
    /**
     * True when the caller's hand moved after they called. It must not: the caller cannot
     * toss in and the coalition cannot target them, so whatever they hold at the call is
     * what they score.
     */
    val callerHandChanged: Boolean = false,
    /** How long [BotRunner.nextAction] spent thinking, and how many times it was asked. */
    val decisionNanos: Long = 0,
    val decisions: Int = 0,
) {
    val finished: Boolean get() = finalPhase == GamePhase.SCORING

    fun describe(): String = "  seed $seed: ${finalPhase.serialName} after $actions actions — " +
        listOf(stalledAt, refused).filter { it.isNotEmpty() }.joinToString("; ")
}

/** A whole game is a few hundred actions; well past that means it has stopped advancing. */
private const val ACTION_LIMIT = 1_500

/**
 * Plays one game with four bots of one difficulty, and reports what happened.
 *
 * Nothing here fails a test. An illegal action, a refused reduction and a stall are all
 * *recorded* rather than thrown, so the tournament can finish its table when one seed goes
 * wrong and the gate can name every bad seed in one message instead of the first.
 *
 * The bots' `Random` is seeded from the game seed rather than left to the default, so a
 * failure names a seed that reproduces it exactly — and so the tournament's numbers are a
 * property of the bot rather than of the run.
 */
internal fun playSelfPlayGame(seed: Long, difficulty: Difficulty): PlayedGame =
    playGame(seed, difficulty) { difficulty }

/**
 * The seats of a mixed table, rotated by seed so that over twelve seeds each difficulty sits
 * in each chair three times. Two `hard` seats rather than one so that the strongest bot also
 * meets itself, which is the table a person playing on `hard` actually faces.
 */
internal val MIXED_TABLE: List<Difficulty> =
    listOf(Difficulty.EASY, Difficulty.MODERATE, Difficulty.HARD, Difficulty.HARD)

/**
 * A table where the seats play at different difficulties. This is the only way the
 * difficulties can be *ranked*: at a homogeneous table a lower mean hand says the table was
 * easier to sit at, not that the bot was better.
 */
internal fun playMixedGame(seed: Long): PlayedGame {
    val rotated = List(MIXED_TABLE.size) { seat ->
        MIXED_TABLE[((seat + seed) % MIXED_TABLE.size).toInt()]
    }
    return playGame(seed, Difficulty.MODERATE) { seat -> rotated[seat] }
}

@Suppress("ReturnCount")
private fun playGame(
    seed: Long,
    gameDifficulty: Difficulty,
    difficultyOfSeat: (Int) -> Difficulty,
): PlayedGame {
    var state = allBots(initializeGame(seed, gameDifficulty))
    val seatIds = state.players.map { it.id }
    val seatDifficulties = seatIds.indices.map(difficultyOfSeat)
    val runner = BotRunner(gameDifficulty, Random(seed)) { botId ->
        seatDifficulties[seatIds.indexOf(botId)]
    }
    val difficulty = gameDifficulty

    var actions = 0
    var calledVinto = false
    var callerFrozenHand: List<String>? = null
    var decisionNanos = 0L
    var decisions = 0

    while (actions < ACTION_LIMIT && state.phase != GamePhase.SCORING) {
        val startedAt = System.nanoTime()
        val action = runner.nextAction(state)
        decisionNanos += System.nanoTime() - startedAt
        decisions++

        if (action == null) {
            return PlayedGame(
                seed = seed,
                difficulty = difficulty,
                actions = actions,
                finalPhase = state.phase,
                calledVinto = calledVinto,
                stalledAt = whereItStopped(state),
                decisionNanos = decisionNanos,
                decisions = decisions,
            )
        }

        when (val applied = apply(state, action, actions)) {
            is Applied.Refused -> return PlayedGame(
                seed = seed,
                difficulty = difficulty,
                actions = actions,
                finalPhase = state.phase,
                calledVinto = calledVinto,
                refused = applied.why,
                decisionNanos = decisionNanos,
                decisions = decisions,
            )

            is Applied.Accepted -> state = applied.state
        }
        if (action is GameAction.CallVinto) {
            calledVinto = true
            callerFrozenHand = state.players.first { it.id == state.vintoCallerId }.cards.map { it.id }
        }
        actions++
    }

    val totals = state.players.map { calculateCardTotal(it.cards) }
    val caller = state.vintoCallerId?.let { id -> state.players.first { it.id == id } }
    val callerTotal = caller?.let { calculateCardTotal(it.cards) }
    val bestCoalition = caller
        ?.let { c -> state.players.filter { it.id != c.id }.minOfOrNull { calculateCardTotal(it.cards) } }

    val scored = state.phase == GamePhase.SCORING
    val points = calculateRoundPoints(state.players, state.vintoCallerId)

    return PlayedGame(
        seed = seed,
        difficulty = difficulty,
        actions = actions,
        finalPhase = state.phase,
        calledVinto = calledVinto,
        handTotals = if (scored) totals else emptyList(),
        roundPoints = if (scored) state.players.map { points.getValue(it.id) } else emptyList(),
        seatDifficulties = seatDifficulties,
        // The rules give a tie to the caller, so this is `<=` and not `<`.
        callerWon = callerTotal != null && bestCoalition != null && callerTotal <= bestCoalition,
        callerHandChanged = callerFrozenHand != null && callerFrozenHand != caller?.cards?.map { it.id },
        decisionNanos = decisionNanos,
        decisions = decisions,
    )
}

private sealed interface Applied {
    data class Accepted(val state: GameState) : Applied
    data class Refused(val why: String) : Applied
}

/**
 * The validator first, then the engine — the same order and the same boundary a Durable
 * Object runs, so anything refused here is something a live room would refuse too.
 */
private fun apply(state: GameState, action: GameAction, index: Int): Applied {
    val validation = ActionValidator.validate(state, action)
    if (validation is Validation.Invalid) {
        return Applied.Refused(
            "action #$index: the bot proposed an illegal ${action.type} — ${validation.reason}\n" +
                "    phase=${state.phase.serialName} subPhase=${state.subPhase.serialName} " +
                "current=${state.players.getOrNull(state.currentPlayerIndex)?.id} " +
                "pending=${state.pendingAction?.card?.rank?.serialName}" +
                "(${state.pendingAction?.targets?.size} targets)",
        )
    }
    return when (val result = GameEngine.reduce(state, action)) {
        is ReduceResult.Success -> Applied.Accepted(result.state)
        is ReduceResult.Failure -> Applied.Refused(
            "action #$index: the engine rejected a validated ${action.type} — ${result.reason}",
        )
    }
}

private fun whereItStopped(state: GameState): String =
    "subPhase=${state.subPhase.serialName} " +
        "current=${state.players.getOrNull(state.currentPlayerIndex)?.id} " +
        "isBot=${state.players.getOrNull(state.currentPlayerIndex)?.isBot} " +
        "pending=${state.pendingAction?.card?.rank?.serialName}" +
        "(${state.pendingAction?.targets?.size}) " +
        "tossIn=${state.activeTossIn?.ranks?.map { it.serialName }} " +
        "ready=${state.activeTossIn?.playersReadyForNextTurn} " +
        "queued=${state.activeTossIn?.queuedActions?.size} " +
        "draw=${state.drawPile.size}"

/** Every seat is a bot, so the runner drives the whole table. */
private fun allBots(state: GameState): GameState =
    state.copy(players = state.players.map { it.copy(isHuman = false, isBot = true) })

/**
 * One difficulty's row in the committed baseline.
 *
 * Every field is an integer computed from the cards, because a baseline is only worth having
 * if it is exactly reproducible: a float would differ in its last digit between JVMs and the
 * gate would then be a coin toss. Means are carried in hundredths for the same reason —
 * `meanHandTotalCentis = 1834` reads as 18.34 and compares as an `Int`.
 *
 * **Decision latency is deliberately absent.** It is reported by the run and never committed:
 * a millisecond figure measured on one machine is not a fact about the bot, and pinning it
 * would turn a busy CI runner into a red build.
 */
@Serializable
internal data class DifficultyRow(
    val difficulty: String,
    val games: Int,
    val finished: Int,
    val endedOnVinto: Int,
    val callerWins: Int,
    val coalitionWins: Int,
    val meanHandTotalCentis: Int,
    val meanActionsCentis: Int,
    val bestHandTotal: Int,
    val worstHandTotal: Int,
)

/**
 * One difficulty's showing at the mixed table: what its seats scored, averaged, and how often
 * one of them finished with the lowest hand. Round points are the rule's own verdict —
 * +3 for a call that held or a coalition that beat one, −1 for the other side, 0 for the
 * coalition on a tie — so their mean is the number that says which bot is *better*.
 */
@Serializable
internal data class MixedRow(
    val difficulty: String,
    val seats: Int,
    val meanRoundPointsCentis: Int,
    val meanHandTotalCentis: Int,
    val lowestFinishes: Int,
)

@Serializable
internal data class Baseline(
    /** Bumped by hand when the shape of a row changes, so a stale file says so. */
    val version: Int = FORMAT,
    val seeds: Int,
    val rows: List<DifficultyRow>,
    val mixed: List<MixedRow> = emptyList(),
) {
    companion object {
        const val FORMAT = 2
    }
}

private const val CENTIS = 100

internal fun tally(difficulty: Difficulty, games: List<PlayedGame>): DifficultyRow {
    val scored = games.filter { it.finished }
    val totals = scored.flatMap { it.handTotals }
    val called = scored.filter { it.calledVinto }

    return DifficultyRow(
        difficulty = difficulty.serialName,
        games = games.size,
        finished = scored.size,
        endedOnVinto = called.size,
        callerWins = called.count { it.callerWon },
        coalitionWins = called.count { !it.callerWon },
        meanHandTotalCentis = if (totals.isEmpty()) 0 else totals.sum() * CENTIS / totals.size,
        meanActionsCentis = if (scored.isEmpty()) 0 else scored.sumOf { it.actions } * CENTIS / scored.size,
        bestHandTotal = totals.minOrNull() ?: 0,
        worstHandTotal = totals.maxOrNull() ?: 0,
    )
}

internal fun tallyMixed(difficulty: Difficulty, games: List<PlayedGame>): MixedRow {
    val seats = games.filter { it.finished }.flatMap { game ->
        val lowest = game.handTotals.minOrNull() ?: 0
        game.seatDifficulties.indices
            .filter { game.seatDifficulties[it] == difficulty }
            .map { seat ->
                Triple(game.handTotals[seat], game.roundPoints[seat], game.handTotals[seat] == lowest)
            }
    }
    return MixedRow(
        difficulty = difficulty.serialName,
        seats = seats.size,
        meanRoundPointsCentis = if (seats.isEmpty()) 0 else seats.sumOf { it.second } * CENTIS / seats.size,
        meanHandTotalCentis = if (seats.isEmpty()) 0 else seats.sumOf { it.first } * CENTIS / seats.size,
        lowestFinishes = seats.count { it.third },
    )
}
