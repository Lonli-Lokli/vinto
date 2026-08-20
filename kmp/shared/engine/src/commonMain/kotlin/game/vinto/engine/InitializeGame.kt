package game.vinto.engine

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Prng
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardConfig

/**
 * Creating the initial state of a game.
 *
 * Ported from `packages/local-client/src/lib/initializeGame.ts`. It lives in `shared/engine`
 * rather than the `shared/client` module design D1 sketches, because the **server** deals the
 * cards: a Durable Object creating a room needs this, and it must not have to depend on a
 * client module to get it (D9).
 *
 * The seed is a required parameter here, unlike TypeScript where it is optional and falls
 * back to `crypto.getRandomValues`. Picking a seed is ambient randomness and belongs outside
 * the engine — that separation is what makes a game reproducible from `(seed, actions[])`,
 * and the purity guard exists to keep it true.
 */

/** Prefix of the deterministic `gameId`; the remainder is the seed. */
private const val GAME_ID_PREFIX = "vinto-"

private const val CARDS_PER_PLAYER = 5

/** Four of each rank, one per suit. */
private const val COPIES_PER_RANK = 4

/**
 * The 54-card deck in a fixed order, which the seeded shuffle then permutes.
 *
 * The order is part of the cross-language contract: the same seed must deal the same game in
 * both implementations, and the shuffle is a permutation of *this* sequence. Number cards
 * carry no `actionText`, which is how `participate-in-toss` tells an action card from a plain
 * one — so an empty string here would silently queue Jokers for actions they do not have.
 */
fun createDeck(): List<Card> {
    val deck = mutableListOf<Card>()

    val numberRanks = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX)
    for (rank in numberRanks) {
        val config = getCardConfig(rank)
        repeat(COPIES_PER_RANK) { copy ->
            deck += Card(
                id = "${rank.serialName}_$copy",
                rank = rank,
                value = config.value,
                played = false,
            )
        }
    }

    val actionRanks = listOf(
        Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
        Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
    )
    for (rank in actionRanks) {
        val config = getCardConfig(rank)
        repeat(COPIES_PER_RANK) { copy ->
            deck += Card(
                id = "${rank.serialName}_$copy",
                rank = rank,
                value = config.value,
                actionText = config.shortDescription,
                played = false,
            )
        }
    }

    val jokerConfig = getCardConfig(Rank.JOKER)
    deck += Card(id = "Joker1", rank = Rank.JOKER, value = jokerConfig.value, played = false)
    deck += Card(id = "Joker2", rank = Rank.JOKER, value = jokerConfig.value, played = false)

    return deck
}

/**
 * The four seats.
 *
 * Names and ids are fixed, and the bots start knowing their own first two cards — the peek
 * every player gets at setup, already spent. **This is the offline shape** (one human, three
 * bots) and online play needs seats assigned per joining client instead (D9, phase 9); it is
 * ported as-is so the deal matches TypeScript card for card.
 */
private fun createPlayers(): List<PlayerState> = listOf(
    PlayerState(
        id = "human-1",
        name = "You",
        nickname = "You",
        isHuman = true,
        isBot = false,
        cards = emptyList(),
        knownCardPositions = emptyList(),
        isVintoCaller = false,
        coalitionWith = emptyList(),
    ),
    bot("bot-1", "Raphael", "Raph"),
    bot("bot-2", "Michelangelo", "Mikey"),
    bot("bot-3", "Donatello", "Don"),
)

private fun bot(id: String, name: String, nickname: String) = PlayerState(
    id = id,
    name = name,
    nickname = nickname,
    isHuman = false,
    isBot = true,
    cards = emptyList(),
    knownCardPositions = listOf(0, 1),
    isVintoCaller = false,
    coalitionWith = emptyList(),
)

/**
 * Deals a fresh game.
 *
 * Five cards each, the rest to the draw pile, and the advanced generator state carried into
 * `rngState` so the very first action continues the same sequence the deal used.
 *
 * @param seed unsigned 32-bit; the same seed always produces the same game.
 */
fun initializeGame(seed: Long, difficulty: Difficulty = Difficulty.MODERATE): GameState {
    val seeded = Prng.seed(seed)
    val shuffled = Prng.shuffle(createDeck(), seeded)

    val players = createPlayers().mapIndexed { index, player ->
        val from = index * CARDS_PER_PLAYER
        player.copy(cards = shuffled.items.subList(from, from + CARDS_PER_PLAYER))
    }
    val drawPile = shuffled.items.drop(players.size * CARDS_PER_PLAYER)

    return GameState(
        gameId = "$GAME_ID_PREFIX$seeded",
        roundNumber = 1,
        turnNumber = 1,
        // Setup: everyone peeks at two of their own cards before play begins.
        phase = GamePhase.SETUP,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = false,
        players = players,
        currentPlayerIndex = 0,
        vintoCallerId = null,
        coalitionLeaderId = null,
        drawPile = Pile(drawPile),
        discardPile = Pile(),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = difficulty,
        rngState = shuffled.state,
    )
}

/**
 * Deals a game from a **deck somebody arranged**, for teaching.
 *
 * The lesson has to show a Queen being played, an Ace being found in somebody's hand and a
 * Joker arriving on the last turn of the round. A shuffle that happens to do all of that does
 * not exist to be searched for: the constraints are joint over a dozen named positions, and
 * any change to the engine or the bots would silently invalidate a seed found today. So the
 * order is written down instead, and this deals from it.
 *
 * It stays inside the engine's rules in the way that matters. The deck must be a permutation
 * of [createDeck] — every rank, four of each, two Jokers — so this cannot deal a game that
 * could not have been shuffled. `rngState` is still seeded, so a mid-round reshuffle is as
 * deterministic as any other game's.
 *
 * The recording contract is untouched: a `GameRecording` carries `initialState` in full and
 * `replayRecording` starts from it, so a taught round records and replays like any other, in
 * either language.
 *
 * @throws IllegalArgumentException if [deck] is not a permutation of the real deck.
 */
fun initializeTeachingGame(
    deck: List<Card>,
    difficulty: Difficulty = Difficulty.EASY,
    seed: Long = TEACHING_RNG_SEED,
): GameState {
    val real = createDeck()
    require(deck.size == real.size && deck.map { it.id }.toSet() == real.map { it.id }.toSet()) {
        "a teaching deck must be a permutation of the real deck: " +
            "got ${deck.size} cards, expected ${real.size}"
    }

    val seeded = Prng.seed(seed)
    val players = createPlayers().mapIndexed { index, player ->
        val from = index * CARDS_PER_PLAYER
        player.copy(cards = deck.subList(from, from + CARDS_PER_PLAYER))
    }

    return GameState(
        gameId = "$GAME_ID_PREFIX$seeded",
        roundNumber = 1,
        turnNumber = 1,
        phase = GamePhase.SETUP,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = false,
        players = players,
        currentPlayerIndex = 0,
        vintoCallerId = null,
        coalitionLeaderId = null,
        drawPile = Pile(deck.drop(players.size * CARDS_PER_PLAYER)),
        discardPile = Pile(),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = difficulty,
        rngState = seeded,
    )
}

/** Any constant will do; it only has to be the same one every time the lesson is opened. */
private const val TEACHING_RNG_SEED = 1L

/** Every rank appears in the deck; a missing one would be a silent rules change. */
internal fun deckCoversEveryRank(): Boolean =
    createDeck().map { it.rank }.toSet() == ALL_RANKS.toSet()
