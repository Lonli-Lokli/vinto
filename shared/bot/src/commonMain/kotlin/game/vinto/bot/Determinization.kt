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
 * MCTS cannot search a position it only partly knows, so every iteration replaces the unknown
 * cards with one *plausible* world — a consistent deal of everything the bot has not seen,
 * plus an order for the deck — and plays that world forward. Run enough iterations over enough
 * worlds and the good moves are the ones that survive most of them.
 *
 * The sampling is uniform over what is genuinely left: the deck minus the discard pile, the
 * card in play and every card the bot remembers. No prior says an unseen card is likelier to
 * be a Queen than a 3 — the one that used to did so by a hand-written table, and a table is
 * a guess. What *does* narrow a draw is evidence: the [OpponentModeler]'s bounds on a card
 * whose owner was seen to keep it in preference to a known one.
 */

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

    var constrained: List<Rank> = availableRanks
    if (minValue != null || maxValue != null) {
        val filtered = availableRanks.filter { rank ->
            val value = getCardValue(rank)
            (minValue == null || value >= minValue) && (maxValue == null || value <= maxValue)
        }
        if (filtered.isNotEmpty()) constrained = filtered
    }

    val sampledRank = constrained[random.nextInt(constrained.size)]
    availableRanks.remove(sampledRank)

    return sampledCard(sampledRank, "$playerId-$position-sampled")
}

private fun sampledCard(rank: Rank, id: String) = Card(
    id = id,
    rank = rank,
    value = getCardValue(rank),
    actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    played = false,
)

/**
 * Produces one consistent possible world: every hidden card either known or drawn from what
 * is plausibly left, and the rest of the pool shuffled into a deck for the rollout to draw
 * from. The discard pile is carried across so a reshuffle in the world folds back the same
 * cards the real one would.
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

            hiddenCards[key] = if (availableRanks.isNotEmpty()) {
                val belief = state.opponentModeler?.getBelief(player.id, position)
                sampleCardFromPool(
                    availableRanks = availableRanks,
                    playerId = player.id,
                    position = position,
                    random = random,
                    minValue = belief?.minValue,
                    maxValue = belief?.maxValue,
                )
            } else {
                // Pool exhausted: fall back to what memory says is still unaccounted for.
                sampledCard(state.botMemory.sampleCardFromDistribution() ?: FALLBACK_RANK, key)
            }
        }
    }

    // The deck is the rest of the pool, in a random order. A memory that over-counts what
    // is out of the deck leaves the pool short; it is padded from the memory's own
    // distribution rather than left short, so a rollout can always draw.
    val deck = availableRanks.shuffled(random).toMutableList()
    while (deck.size < state.deckSize) deck += state.botMemory.sampleCardFromDistribution() ?: FALLBACK_RANK
    val deckOrder = deck.take(state.deckSize).mapIndexed { index, rank -> sampledCard(rank, "deck-$index") }

    return state.copy(
        hiddenCards = hiddenCards,
        deckOrder = deckOrder,
        discarded = state.discardPile.toList(),
    )
}
