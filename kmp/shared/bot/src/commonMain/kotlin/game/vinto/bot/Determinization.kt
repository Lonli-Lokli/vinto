package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random

/**
 * Filling in the cards the bot cannot see.
 *
 * MCTS cannot search a position it only partly knows, so before each simulation the unknown
 * cards are replaced with a *plausible* set — one consistent possible world. Run enough
 * simulations over enough sampled worlds and the good moves are the ones that survive most
 * of them. This is what makes an imperfect-information game tractable at all.
 *
 * Two things make the sampling better than uniform. The pool is what is genuinely left,
 * after removing everything on the discard pile and everything the bot remembers; and the
 * draw is weighted, because opponents *keep* good cards and shed bad ones, so an unseen card
 * is likelier to be a Queen than a 3.
 *
 * Ported from `packages/bot/src/lib/mcts-determinization.ts`.
 */

/** Below this, a memory is a hunch and the card is sampled rather than assumed. */

private const val COPIES_PER_RANK = 4
private const val JOKER_COUNT = 2

/** Used when the pool is exhausted and memory has nothing to offer either. */
private val FALLBACK_RANK = Rank.SIX

/** 52 cards plus two Jokers, as a flat list of ranks. */
val STANDARD_DECK_RANKS: List<Rank> = ALL_RANKS.flatMap { rank ->
    List(if (rank == Rank.JOKER) JOKER_COUNT else COPIES_PER_RANK) { rank }
}

/** What could still be out there: the deck minus the discard pile and minus what is known. */
fun buildAvailableRanksPool(state: MctsGameState): MutableList<Rank> {
    val available = STANDARD_DECK_RANKS.toMutableList()

    for (discarded in state.discardPile.toList()) {
        available.remove(discarded.rank)
    }

    for (player in state.players) {
        for (position in 0 until player.cardCount) {
            val memory = player.knownCards[position] ?: continue
            if (memory.confidence > TRUSTED_CONFIDENCE) available.remove(memory.card.rank)
        }
    }

    state.pendingCard?.let { available.remove(it.rank) }

    return available
}

/**
 * How likely an unseen card is to be a given rank.
 *
 * Not uniform, because players are not random: they keep the cards that help them and swap
 * away the ones that do not. The ordering is the strategic ranking of the deck —
 * Joker > Q > J > K > 7/8 > A > 9/10 > 6 > 5 > 2-4 — so a hidden card is assumed to be
 * better than average, which is what an opponent's hand actually looks like by mid-game.
 */
@Suppress("MagicNumber")
fun getStrategicProbabilityWeight(rank: Rank): Double = when (rank) {
    Rank.JOKER -> 2.0
    Rank.QUEEN -> 1.8
    Rank.JACK -> 1.7
    Rank.KING -> 1.6
    Rank.SEVEN, Rank.EIGHT -> 1.4
    Rank.ACE -> 1.3
    Rank.NINE, Rank.TEN -> 1.1
    Rank.SIX -> 0.7
    Rank.FIVE -> 0.6
    Rank.TWO, Rank.THREE, Rank.FOUR -> 0.5
}

private fun buildCumulativeProbabilities(ranks: List<Rank>): List<Double> {
    val weights = ranks.map { getStrategicProbabilityWeight(it) }
    val total = weights.sum()

    var running = 0.0
    return weights.map { weight ->
        running += weight / total
        running
    }
}

/**
 * Draws one card from [availableRanks], **removing it** so a later draw cannot produce the
 * same physical card twice — the sampled world has to be internally consistent.
 *
 * [minValue]/[maxValue] come from [OpponentModeler] beliefs, so an inference like "that card
 * is worth more than 7" narrows the draw. If nothing satisfies the constraint the draw falls
 * back to unconstrained: a belief is evidence, not a guarantee, and refusing to sample would
 * abandon the simulation entirely.
 */
fun sampleCardFromPool(
    availableRanks: MutableList<Rank>,
    playerId: String,
    position: Int,
    random: Random,
    minValue: Int? = null,
    maxValue: Int? = null,
): Card {
    require(availableRanks.isNotEmpty()) { "Cannot sample from an empty card pool" }

    var constrained = availableRanks.toList()
    if (minValue != null || maxValue != null) {
        val filtered = constrained.filter { rank ->
            val value = getCardValue(rank)
            (minValue == null || value >= minValue) && (maxValue == null || value <= maxValue)
        }
        if (filtered.isNotEmpty()) constrained = filtered
    }

    val cumulative = buildCumulativeProbabilities(constrained)
    val roll = random.nextDouble()
    val index = cumulative.indexOfFirst { roll <= it }.takeIf { it >= 0 } ?: 0

    val sampledRank = constrained[index]
    availableRanks.remove(sampledRank)

    return sampledCard(sampledRank, playerId, position)
}

private fun sampledCard(rank: Rank, playerId: String, position: Int) = Card(
    id = "$playerId-$position-sampled",
    rank = rank,
    value = getCardValue(rank),
    actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    played = false,
)

/**
 * Produces one consistent possible world: every card either known, or drawn from what is
 * plausibly left.
 */
fun determinize(state: MctsGameState, random: Random): MctsGameState {
    val availableRanks = buildAvailableRanksPool(state)
    val hiddenCards = mutableMapOf<String, Card>()

    for (player in state.players) {
        for (position in 0 until player.cardCount) {
            val memory = player.knownCards[position]
            val key = state.hiddenCardKey(player.id, position)

            if (memory != null && memory.confidence > TRUSTED_CONFIDENCE) {
                hiddenCards[key] = memory.card
                continue
            }

            hiddenCards[key] = when {
                availableRanks.isNotEmpty() -> {
                    val belief = state.opponentModeler?.getBelief(player.id, position)
                    sampleCardFromPool(
                        availableRanks = availableRanks,
                        playerId = player.id,
                        position = position,
                        random = random,
                        minValue = belief?.minValue,
                        maxValue = belief?.maxValue,
                    )
                }

                // Pool exhausted: fall back to what memory says is still unaccounted for.
                else -> sampledCard(
                    state.botMemory.sampleCardFromDistribution() ?: FALLBACK_RANK,
                    player.id,
                    position,
                )
            }
        }
    }

    return state.copy(hiddenCards = hiddenCards)
}
