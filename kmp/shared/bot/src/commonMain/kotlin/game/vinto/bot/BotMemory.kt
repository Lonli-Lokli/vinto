package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

/**
 * What a bot remembers, and how imperfectly.
 *
 * Difficulty is not implemented by making the bot think worse — it thinks the same at every
 * level — but by how reliably it *remembers*. An easy bot misremembers cards, forgets them
 * quickly and can only hold four at a time; a hard bot remembers everything perfectly. That
 * is a far better model of a weak player than deliberately choosing bad moves.
 *
 * Ported from `packages/bot/src/lib/bot-memory.ts`, with two things injected that were
 * ambient there:
 *
 *  - **[Random]**, replacing `Math.random`. Design D4 requires it, and it is what makes a
 *    bot's play reproducible from a seed. It is also the direct cause of the two known-flaky
 *    bot tests in the TypeScript suite.
 *  - **the clock**, replacing `Date.now()`. Memory decays with elapsed time, so a bot reading
 *    a wall clock plays differently depending on how long a turn took — which would make a
 *    recorded game unreplayable. [ticks] is advanced by the caller at turn boundaries.
 */
data class DifficultyMemoryConfig(
    /** Chance of correctly recording a card that was seen. */
    val memoryAccuracy: Double,
    /** How fast confidence decays per tick. */
    val memoryDecayRate: Double,
    val maxMemorySize: Int,
    /** Chance of dropping any given memory at a turn boundary. */
    val forgetChance: Double,
    val observationRequired: Int,
)

val DIFFICULTY_CONFIGS: Map<Difficulty, DifficultyMemoryConfig> = mapOf(
    Difficulty.EASY to DifficultyMemoryConfig(
        memoryAccuracy = 0.4,
        memoryDecayRate = 0.00015,
        maxMemorySize = 4,
        forgetChance = 0.3,
        observationRequired = 3,
    ),
    Difficulty.MODERATE to DifficultyMemoryConfig(
        memoryAccuracy = 0.75,
        memoryDecayRate = 0.00008,
        maxMemorySize = 8,
        forgetChance = 0.1,
        observationRequired = 2,
    ),
    // Perfect and non-decaying, which is also what makes hard-difficulty tests deterministic.
    Difficulty.HARD to DifficultyMemoryConfig(
        memoryAccuracy = 1.0,
        memoryDecayRate = 0.0,
        maxMemorySize = 100,
        forgetChance = 0.0,
        observationRequired = 1,
    ),
)

data class CardMemory(
    val card: Card,
    /** 0-1, decaying with time and rising with repeated sightings. */
    val confidence: Double,
    val lastSeen: Long,
    val observations: Int,
)

private const val INITIAL_CONFIDENCE = 0.7
private const val REOBSERVATION_BOOST = 0.3
private const val FORGET_BELOW_CONFIDENCE = 0.1
private const val COPIES_PER_RANK = 4
private const val JOKER_COUNT = 2

/** Fallback when nothing is known about what is left; roughly a mid-deck card. */
private const val NEUTRAL_AVERAGE_CARD_VALUE = 6.0

/** A memory below this confidence is a hunch, not a fact, and is estimated rather than trusted. */


class BotMemory(
    private val botId: String,
    difficulty: Difficulty,
    private val random: Random = Random.Default,
) {
    private val config = DIFFICULTY_CONFIGS.getValue(difficulty)

    private val ownCards = mutableMapOf<Int, CardMemory>()
    private val opponentCards = mutableMapOf<String, MutableMap<Int, CardMemory>>()

    /**
     * Ranks lying in plain sight — the discard pile and the card in play — as last synced
     * from the table by [syncVisibleCards]. Public facts, not memories: they are never
     * misremembered, never decay, and shrink on their own when a reshuffle folds the pile
     * back into the deck.
     */
    private var visibleRanks: List<Rank> = emptyList()

    /**
     * The bot's own sense of elapsed time, advanced by [processTurnBoundary] rather than read
     * from a clock. One tick per turn keeps decay meaningful and the whole thing replayable.
     */
    private var ticks: Long = 0

    private fun initialDistribution(): MutableMap<Rank, Int> =
        ALL_RANKS.associateWith { rank ->
            if (rank == Rank.JOKER) JOKER_COUNT else COPIES_PER_RANK
        }.toMutableMap()

    /**
     * Records a card the bot has seen — and, depending on difficulty, sometimes fails to.
     * A failed observation is silent, exactly as it would be for a person.
     */
    fun observeCard(card: Card, playerId: String, position: Int) {
        if (random.nextDouble() >= config.memoryAccuracy) return

        val target = memoryFor(playerId)
        val existing = target[position]
        target[position] = CardMemory(
            card = card,
            confidence = existing?.let { min(1.0, it.confidence + REOBSERVATION_BOOST) }
                ?: INITIAL_CONFIDENCE,
            lastSeen = ticks,
            observations = (existing?.observations ?: 0) + 1,
        )

        enforceMemoryLimit()
    }

    /** A card that moved or left; the memory is wrong now. The pool recovers on its own. */
    fun forgetCard(playerId: String, position: Int) {
        memoryFor(playerId).remove(position)
    }

    /** The table's public cards, re-synced before each decision. See [visibleRanks]. */
    fun syncVisibleCards(ranks: List<Rank>) {
        visibleRanks = ranks.toList()
    }

    fun getCardMemory(playerId: String, position: Int): CardMemory? = memoryFor(playerId)[position]

    fun getPlayerMemory(playerId: String): Map<Int, CardMemory> = memoryFor(playerId).toMap()

    /**
     * What this bot would declare about its own hand: the positions it trusts, as ranks.
     * On lower difficulties observation can record the wrong card or nothing at all, so a
     * declaration built from this can be honestly wrong — which is the point.
     */
    fun believedOwnCards(): Map<Int, Rank> = ownCards
        .filterValues { it.confidence > TRUSTED_CONFIDENCE }
        .mapValues { (_, memory) -> memory.card.rank }

    fun getConfidence(playerId: String, position: Int): Double =
        getCardMemory(playerId, position)?.confidence ?: 0.0

    /**
     * What is still unaccounted for: a full deck minus everything this bot can point to — the
     * public cards on the table and every memory it trusts, its own hand's and its
     * opponents'.
     *
     * *Derived*, never mutated. The old bookkeeping decremented on observation and credited
     * back on forgetting, and leaked on every path those two did not cover: an overwritten
     * memory, the same card re-seen at a new position, a discard nobody observed, a
     * reshuffle, a new deal. Recomputing from what is currently known makes each of those a
     * non-event.
     */
    fun getCardDistribution(): Map<Rank, Int> {
        val remaining = initialDistribution()
        fun consume(rank: Rank) {
            remaining[rank]?.let { count -> if (count > 0) remaining[rank] = count - 1 }
        }

        visibleRanks.forEach(::consume)
        ownCards.values.forEach { if (it.confidence > TRUSTED_CONFIDENCE) consume(it.card.rank) }
        opponentCards.values.forEach { map ->
            map.values.forEach { if (it.confidence > TRUSTED_CONFIDENCE) consume(it.card.rank) }
        }
        return remaining
    }

    fun getMemorySize(): Int = ownCards.size + opponentCards.values.sumOf { it.size }

    /** Advances the bot's clock, then forgets and decays. Call once per turn. */
    fun processTurnBoundary() {
        ticks++
        randomForget(ownCards)
        opponentCards.values.forEach { randomForget(it) }
        decayMemory()
    }

    fun decayMemory() {
        decayMemoryMap(ownCards)
        opponentCards.values.forEach { decayMemoryMap(it) }
    }

    /** Draws a plausible rank for an unseen card, weighted by what is still unaccounted for. */
    fun sampleCardFromDistribution(): Rank? {
        val available = getCardDistribution().entries.flatMap { (rank, count) -> List(count) { rank } }
        if (available.isEmpty()) return null
        return available[random.nextInt(available.size)]
    }

    fun clear() {
        ownCards.clear()
        opponentCards.clear()
        visibleRanks = emptyList()
        ticks = 0
    }

    private fun memoryFor(playerId: String): MutableMap<Int, CardMemory> =
        if (playerId == botId) ownCards else opponentCards.getOrPut(playerId) { mutableMapOf() }

    private fun decayMemoryMap(memoryMap: MutableMap<Int, CardMemory>) {
        if (config.memoryDecayRate == 0.0) return

        val forgotten = mutableListOf<Int>()
        for ((position, memory) in memoryMap) {
            val elapsed = (ticks - memory.lastSeen).toDouble()
            val decayed = memory.confidence * exp(-config.memoryDecayRate * elapsed)
            if (decayed < FORGET_BELOW_CONFIDENCE) {
                forgotten += position
            } else {
                memoryMap[position] = memory.copy(confidence = decayed)
            }
        }
        forgotten.forEach { memoryMap.remove(it) }
    }

    private fun randomForget(memoryMap: MutableMap<Int, CardMemory>) {
        if (config.forgetChance == 0.0) return

        memoryMap.keys.filter { random.nextDouble() < config.forgetChance }
            .forEach { memoryMap.remove(it) }
    }

    /**
     * Over the limit, the least-certain memories go first — which is how a person forgets.
     *
     * The limit is a cap on *opponent* tracking only. The bot's own row is the thing it
     * rehearses every turn and is never crowded out by watching the table — when it was,
     * every sighting of an opponent's card evicted one of the bot's own, `believedOwnCards`
     * went empty for any bot that had seen a few opponents, and no bot on a limited
     * difficulty could ever believe its full hand — which meant it could never call Vinto,
     * and games stopped ending. Own-hand fallibility comes from [DifficultyMemoryConfig]'s
     * accuracy and forget chance instead, which is the honest kind: gaps, not amnesia.
     */
    private fun enforceMemoryLimit() {
        val over = opponentCards.values.sumOf { it.size } - config.maxMemorySize
        if (over <= 0) return

        val all = opponentCards.values.flatMap { map ->
            map.map { (position, memory) -> Triple(map, position, memory) }
        }

        all.sortedBy { it.third.confidence }
            .take(over)
            .forEach { (map, position, _) -> map.remove(position) }
    }
}

/**
 * The bot's estimate of a player's total, mixing what it remembers with what it can infer
 * about the rest. Only memories it is more than half confident in are trusted as fact.
 *
 * Ported from `packages/bot/src/lib/mcts-score-estimator.ts`.
 */
fun estimatePlayerScore(handSize: Int, botMemory: BotMemory, playerId: String): Double {
    val known = botMemory.getPlayerMemory(playerId)

    var knownScore = 0.0
    var unknownCount = 0
    for (position in 0 until handSize) {
        val memory = known[position]
        if (memory != null && memory.confidence > TRUSTED_CONFIDENCE) knownScore += memory.card.value
        else unknownCount++
    }

    return knownScore + unknownCount * averageRemainingCardValue(botMemory)
}

/** Average value of everything still unaccounted for. */
fun averageRemainingCardValue(botMemory: BotMemory): Double {
    var totalValue = 0.0
    var totalCount = 0

    for ((rank, count) in botMemory.getCardDistribution()) {
        if (count > 0) {
            totalValue += game.vinto.shapes.getCardValue(rank) * count
            totalCount += count
        }
    }

    return if (totalCount == 0) NEUTRAL_AVERAGE_CARD_VALUE else totalValue / totalCount
}
