package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The search's own model of the game.
 *
 * This is deliberately *not* `GameState`. The bot searches over what it believes — opponents
 * are card counts plus memories — and every iteration fills the gaps in by sampling one
 * consistent world ([determinize]), then plays that world forward. The tree is shared across
 * worlds and keyed on what everybody at the table could see: a node's move never names a
 * sampled card, only a position, a rank that was shown, or a player.
 */

enum class MctsMoveType { DRAW, TAKE_DISCARD, USE_ACTION, SWAP, DISCARD, TOSS_IN, CALL_VINTO, PASS }

data class MctsActionTarget(val playerId: String, val position: Int)

/**
 * A move, as the tree keys it.
 *
 * [cardInPlay] is the rank of the card being decided about — the card just drawn, taken, or
 * borrowed. A drawn card is public, so two worlds that dealt different ranks lead to different
 * information sets, and the tree separates them by carrying the rank on every reply.
 */
data class MctsMove(
    val type: MctsMoveType,
    val playerId: String,
    val targets: List<MctsActionTarget> = emptyList(),
    val swapPosition: Int? = null,
    val declaredRank: Rank? = null,
    val tossInPositions: List<Int> = emptyList(),
    val shouldSwap: Boolean? = null,
    val cardInPlay: Rank? = null,
)

/**
 * A player as the searching bot sees them: a hand size and what the bot remembers of it.
 *
 * `knownCards` always means *what the searching bot knows about this player's cards* — for
 * the bot's own seat that is its memory of its hand, for anyone else its memory of theirs.
 */
data class MctsPlayerState(
    val id: String,
    val cardCount: Int,
    val knownCards: Map<Int, CardMemory> = emptyMap(),
)

/** How the card in play got there, which decides what may still be done with it. */
enum class PendingOrigin {
    /** Drawn from the deck: may be played, swapped in, or discarded. */
    DRAWN,

    /** Taken off the discard pile, or a tossed-in action: must be aimed or put down. */
    COMMITTED,

    /** A King's declared card, or a declared swap-out: the same, played by its declarer. */
    BORROWED,
}

data class MctsGameState(
    val players: List<MctsPlayerState>,
    val currentPlayerIndex: Int,
    val botPlayerId: String,
    val discardPileTop: Card? = null,
    /** The real pile at the root; determinization reads it to know what is out of the deck. */
    val discardPile: Pile = Pile(),
    val deckSize: Int = 0,
    val discardCount: Int = 0,
    val botMemory: BotMemory,
    /** One sampled world, keyed `playerId-position`. Empty until [determinize] runs. */
    val hiddenCards: Map<String, Card> = emptyMap(),
    /** The sampled deck, top first. A draw takes from here, a reshuffle refills it. */
    val deckOrder: List<Card> = emptyList(),
    /** Every card on the pile in this world, oldest first, so a reshuffle can fold it back. */
    val discarded: List<Card> = emptyList(),
    val pendingCard: Card? = null,
    val pendingOrigin: PendingOrigin? = null,
    /** Ranks waiting for the toss-in window that opens once the current play is finished. */
    val queuedTossRanks: List<Rank> = emptyList(),
    val isTossInPhase: Boolean = false,
    val tossInRanks: List<Rank> = emptyList(),
    /** The turn is over and its owner may call Vinto before play moves on. */
    val awaitingVintoDecision: Boolean = false,
    val turnCount: Int = 0,
    val finalTurnTriggered: Boolean = false,
    val vintoCallerId: String? = null,
    val coalitionLeaderId: String? = null,
    val opponentModeler: OpponentModeler? = null,
    val isTerminal: Boolean = false,
) {
    val currentPlayer: MctsPlayerState? get() = players.getOrNull(currentPlayerIndex)

    fun hiddenCardKey(playerId: String, position: Int) = "$playerId-$position"

    fun playerIndex(playerId: String): Int = players.indexOfFirst { it.id == playerId }
}

/**
 * A node in the search tree: one information set, reached by [move] from [parent].
 *
 * Mutable by nature — MCTS grows and rewrites this tree thousands of times per decision.
 * Rewards are kept per seat, because a node's value depends on whose move it is: each seat
 * plays for itself, and a node is chosen by the mean of the mover's own entry.
 */
class MctsNode(val move: MctsMove?, val parent: MctsNode?, seats: Int) {

    /** Insertion order is kept so a tie in visits resolves the same way on every run. */
    val children = LinkedHashMap<MctsMove, MctsNode>()

    var visits: Int = 0
        private set

    /**
     * How often this node was *available* — legal in the world being searched — when its
     * parent chose among its children. In an information-set tree a child may be legal in one
     * sampled world and not another, so the exploration term is scaled by the times it could
     * have been picked rather than by the parent's visits (Cowling, Powley & Whitehouse, 2012).
     */
    var availability: Int = 0
        private set

    val totalReward = DoubleArray(seats)

    fun mean(seat: Int): Double = if (visits > 0) totalReward[seat] / visits else 0.0

    /** Legal moves this node has no child for yet. */
    fun untried(legal: List<MctsMove>): List<MctsMove> = legal.filter { it !in children }

    fun child(move: MctsMove): MctsNode = children.getOrPut(move) { MctsNode(move, this, totalReward.size) }

    /**
     * UCB1 over the children that are legal in this world, from the mover's point of view.
     * An unvisited child is taken first: its value is unbounded, and trying everything once
     * before comparing is what stops the search committing to the first move that looked
     * reasonable.
     */
    fun selectChild(legal: List<MctsMove>, mover: Int, explorationConstant: Double): MctsNode? {
        val candidates = legal.mapNotNull { children[it] }
        if (candidates.isEmpty()) return null
        candidates.forEach { it.availability++ }

        var best: MctsNode? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (child in candidates) {
            if (child.visits == 0) return child
            val score = child.mean(mover) +
                explorationConstant * sqrt(ln(child.availability.toDouble()) / child.visits)
            if (score > bestScore) {
                bestScore = score
                best = child
            }
        }
        return best
    }

    /**
     * The move to actually play: the most *visited* child, not the best-scoring one. A high
     * mean from few visits is usually luck; visits are what the search committed its time to.
     */
    fun mostVisitedChild(): MctsNode? = children.values.maxByOrNull { it.visits }

    fun backpropagate(reward: DoubleArray) {
        var node: MctsNode? = this
        while (node != null) {
            node.visits++
            for (seat in reward.indices) node.totalReward[seat] += reward[seat]
            node = node.parent
        }
    }
}

/**
 * How hard the bot thinks. These are budgets, not judgement: how many worlds are sampled,
 * how far a rollout runs, and how much the search explores before it exploits.
 *
 * `timeLimitMillis` is a safety valve for a live game, and is deliberately **null by
 * default**: a search that stops on the clock produces different moves on different
 * machines, which would make a recorded game unreplayable (design D4).
 */
data class MctsConfig(
    val iterations: Int,
    val explorationConstant: Double,
    /** Plies, not turns; a turn is about three of them (draw, reply, the Vinto question). */
    val rolloutDepth: Int,
    val timeLimitMillis: Long? = null,
)

val MCTS_DIFFICULTY_CONFIGS: Map<Difficulty, MctsConfig> = mapOf(
    Difficulty.EASY to MctsConfig(iterations = 500, explorationConstant = 0.7, rolloutDepth = 15),
    Difficulty.MODERATE to MctsConfig(iterations = 2_000, explorationConstant = 0.7, rolloutDepth = 20),
    Difficulty.HARD to MctsConfig(iterations = 5_000, explorationConstant = 0.7, rolloutDepth = 30),
)
