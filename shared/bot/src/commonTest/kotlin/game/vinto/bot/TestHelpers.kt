package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue

/**
 * Shared fixtures for the bot tests, ported from
 * `legacy-web/packages/bot/src/lib/__tests__/test-helpers.ts` so the ported tests read the same as their
 * originals and a difference between them is a real difference.
 */

fun testCard(rank: Rank, id: String) = Card(
    id = id,
    rank = rank,
    value = getCardValue(rank),
    played = false,
    actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
)

/**
 * Bots in tests know all their own cards, so a decision under test is about strategy rather
 * than about what the bot happens to have peeked at.
 */
fun testPlayer(
    id: String,
    name: String,
    isHuman: Boolean,
    cards: List<Card> = emptyList(),
    knownCardPositions: List<Int> = if (!isHuman) cards.indices.toList() else emptyList(),
) = PlayerState(
    id = id,
    name = name,
    nickname = name,
    isHuman = isHuman,
    isBot = !isHuman,
    cards = cards,
    knownCardPositions = knownCardPositions,
    isVintoCaller = false,
    coalitionWith = emptyList(),
)

/** Four seats, which is every Vinto game — there is no player-count setting. */
private const val SEAT_COUNT = 4

@Suppress("LongParameterList")
fun testState(
    players: List<PlayerState> =
        (1..SEAT_COUNT).map { testPlayer("p$it", "Player $it", isHuman = false) },
    turnNumber: Int = 0,
    phase: GamePhase = GamePhase.PLAYING,
    subPhase: GameSubPhase = GameSubPhase.IDLE,
    vintoCallerId: String? = null,
    coalitionLeaderId: String? = null,
    drawPile: Pile = Pile(),
    discardPile: Pile = Pile(),
    difficulty: Difficulty = Difficulty.HARD,
) = GameState(
    gameId = "test-game",
    roundNumber = 1,
    turnNumber = turnNumber,
    phase = phase,
    subPhase = subPhase,
    finalTurnTriggered = false,
    players = players,
    currentPlayerIndex = 0,
    vintoCallerId = vintoCallerId,
    coalitionLeaderId = coalitionLeaderId,
    drawPile = drawPile,
    discardPile = discardPile,
    pendingAction = null,
    activeTossIn = null,
    turnActions = emptyList(),
    roundActions = emptyList(),
    roundFailedAttempts = emptyList(),
    difficulty = difficulty,
    rngState = 0,
)

/**
 * The bot's own known cards go into `opponentKnowledge` under its own id, which is how the
 * TypeScript helper sidesteps the probabilistic memory layer and makes a test deterministic.
 */
fun botContext(
    botId: String,
    gameState: GameState,
    opponentKnowledge: Map<String, Map<Int, Card>>? = null,
): BotDecisionContext {
    val botPlayer = gameState.players.firstOrNull { it.id == botId }
        ?: error("Bot player $botId not found in game state")

    val ownCards = botPlayer.cards
        .mapIndexedNotNull { position, card ->
            if (position in botPlayer.knownCardPositions) position to card else null
        }
        .toMap()

    return BotDecisionContext(
        botId = botId,
        botPlayer = botPlayer,
        allPlayers = gameState.players,
        gameState = gameState,
        discardTop = gameState.discardPile.peekTop(),
        discardPile = gameState.discardPile,
        opponentKnowledge = opponentKnowledge ?: mapOf(botId to ownCards),
    )
}
