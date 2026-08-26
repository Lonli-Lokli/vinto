package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The search's own model of the game, ported from `packages/bot/src/lib/mcts-types.ts`.
 *
 * This is deliberately *not* `GameState`. The bot searches over what it believes, which
 * means opponents are card counts plus memories rather than hands, and the cards it cannot
 * see are filled in by sampling (see determinization). Searching the real state would be
 * both cheating and far more expensive.
 */

enum class MctsMoveType { DRAW, TAKE_DISCARD, USE_ACTION, SWAP, DISCARD, TOSS_IN, CALL_VINTO, PASS }

data class MctsActionTarget(val playerId: String, val position: Int)

data class MctsMove(
    val type: MctsMoveType,
    val playerId: String,
    val actionCard: Card? = null,
    val targets: List<MctsActionTarget> = emptyList(),
    val swapPosition: Int? = null,
    val declaredRank: Rank? = null,
    val tossInPositions: List<Int> = emptyList(),
    val shouldSwap: Boolean? = null,
)

/** An opponent as the bot sees them: a hand size, some memories, and an estimate. */
data class MctsPlayerState(
    val id: String,
    val cardCount: Int,
    val knownCards: Map<Int, CardMemory> = emptyMap(),
    val score: Double = 0.0,
)

data class MctsGameState(
    val players: List<MctsPlayerState>,
    val currentPlayerIndex: Int,
    val botPlayerId: String,
    val discardPileTop: Card? = null,
    val discardPile: Pile = Pile(),
    val deckSize: Int = 0,
    /**
     * How many cards the model thinks are on the discard pile — the real pile at the root,
     * bookkept by [StateTransition] after that. It exists so a rollout can model the
     * reshuffle: the real game folds the pile back into the deck rather than ending, and a
     * search that could not see past an empty deck treated the endgame as a wall.
     */
    val discardCount: Int = 0,
    val botMemory: BotMemory,
    /** Cards filled in by determinization, keyed `playerId-position`. */
    val hiddenCards: Map<String, Card> = emptyMap(),
    val pendingCard: Card? = null,
    val isTossInPhase: Boolean = false,
    val tossInRanks: List<Rank> = emptyList(),
    val turnCount: Int = 0,
    val finalTurnTriggered: Boolean = false,
    val vintoCallerId: String? = null,
    val coalitionLeaderId: String? = null,
    val opponentModeler: OpponentModeler? = null,
    val isTerminal: Boolean = false,
    val winner: String? = null,
) {
    fun hiddenCardKey(playerId: String, position: Int) = "$playerId-$position"
}

/**
 * A node in the search tree.
 *
 * Mutable by nature — MCTS grows and rewrites this tree thousands of times per decision, and
 * rebuilding it immutably would dominate the cost of the search itself.
 */
class MctsNode(
    val state: MctsGameState,
    val move: MctsMove?,
    val parent: MctsNode?,
) {
    val children = mutableListOf<MctsNode>()

    var visits: Int = 0
        private set
    var totalReward: Double = 0.0
        private set

    var untriedMoves: MutableList<MctsMove> = mutableListOf()
    val isTerminal: Boolean = state.isTerminal
    var isFullyExpanded: Boolean = false
        private set

    fun averageReward(): Double = if (visits > 0) totalReward / visits else 0.0

    fun hasUntriedMoves(): Boolean = untriedMoves.isNotEmpty()

    /** Takes an untried move at random. [random] is injected so a search is reproducible. */
    fun takeRandomUntriedMove(random: Random): MctsMove? {
        if (untriedMoves.isEmpty()) return null
        return untriedMoves.removeAt(random.nextInt(untriedMoves.size))
    }

    fun addChild(child: MctsNode) {
        children += child
        if (untriedMoves.isEmpty()) isFullyExpanded = true
    }

    /**
     * UCB1: exploit what looks good, explore what is untested.
     *
     * An unvisited child is returned immediately — its value is unbounded, and trying
     * everything once before comparing is what stops the search committing to the first
     * move that happened to look reasonable.
     */
    fun selectBestChildUcb1(explorationConstant: Double): MctsNode? {
        if (children.isEmpty()) return null

        var best: MctsNode? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (child in children) {
            if (child.visits == 0) return child

            val exploitation = child.totalReward / child.visits
            val exploration = explorationConstant * sqrt(ln(visits.toDouble()) / child.visits)
            val score = exploitation + exploration

            if (score > bestScore) {
                bestScore = score
                best = child
            }
        }
        return best
    }

    /**
     * The move to actually play: the most *visited* child, not the best-scoring one.
     *
     * A high average from few visits is usually luck; visit count is what the search
     * committed its time to, which makes it the more robust choice.
     */
    fun selectMostVisitedChild(): MctsNode? = children.maxByOrNull { it.visits }

    fun backpropagate(reward: Double) {
        var node: MctsNode? = this
        while (node != null) {
            node.visits++
            node.totalReward += reward
            node = node.parent
        }
    }

    fun depth(): Int {
        var depth = 0
        var current = parent
        while (current != null) {
            depth++
            current = current.parent
        }
        return depth
    }
}

/**
 * How hard the bot thinks.
 *
 * `timeLimitMillis` is a safety valve for a live game, and is deliberately **null by
 * default**: a search that stops on the clock produces different moves on different
 * machines, which would make a recorded game unreplayable. Deterministic runs — tests,
 * replays, the parity corpus — use the iteration budget alone (design D4).
 */
data class MctsConfig(
    val iterations: Int,
    val explorationConstant: Double,
    val rolloutDepth: Int,
    val timeLimitMillis: Long? = null,
)

val MCTS_DIFFICULTY_CONFIGS: Map<Difficulty, MctsConfig> = mapOf(
    Difficulty.EASY to MctsConfig(iterations = 500, explorationConstant = 1.8, rolloutDepth = 15),
    Difficulty.MODERATE to MctsConfig(iterations = 2_000, explorationConstant = 1.6, rolloutDepth = 20),
    Difficulty.HARD to MctsConfig(iterations = 5_000, explorationConstant = 1.4, rolloutDepth = 30),
)
