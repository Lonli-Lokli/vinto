package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue

/**
 * Shared fixtures for the engine tests, ported from
 * `packages/engine/src/lib/__tests__/test-helpers.ts`.
 *
 * Kept close to the original so a ported test reads like its source and a difference between
 * them is a real difference rather than a translation artefact.
 */

/**
 * A card with the right value and action text for its rank.
 *
 * `actionText` is null rather than empty for a card with no action, which is how the real
 * deck is built — `participate-in-toss` tells an action card from a plain one by looking at
 * it, and TypeScript's empty string is falsy in exactly the same way.
 */
fun testCard(rank: Rank, id: String) = Card(
    id = id,
    rank = rank,
    value = getCardValue(rank),
    played = false,
    actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
)

/** Cards a player holds but has not peeked at: `knownCardPositions` starts empty. */
fun testPlayer(
    id: String,
    name: String,
    isHuman: Boolean,
    cards: List<Card> = emptyList(),
    knownCardPositions: List<Int> = emptyList(),
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

fun pileOf(vararg cards: Card) = Pile(cards.toList())

fun pileOf(cards: List<Card>) = Pile(cards)

/** The four seats every game has, none of them holding anything yet. */
private fun defaultPlayers() = listOf(
    testPlayer("p1", "Player 1", isHuman = true),
    testPlayer("p2", "Player 2", isHuman = false),
    testPlayer("p3", "Player 3", isHuman = false),
    testPlayer("p4", "Player 4", isHuman = false),
)

// Mirrors GameState's own breadth; a builder would be more machinery than the tests it serves.
@Suppress("LongParameterList")
fun testState(
    players: List<PlayerState> = defaultPlayers(),
    phase: GamePhase = GamePhase.PLAYING,
    subPhase: GameSubPhase = GameSubPhase.IDLE,
    currentPlayerIndex: Int = 0,
    turnNumber: Int = 0,
    roundNumber: Int = 1,
    finalTurnTriggered: Boolean = false,
    vintoCallerId: String? = null,
    coalitionLeaderId: String? = null,
    drawPile: Pile = Pile(),
    discardPile: Pile = Pile(),
    pendingAction: PendingAction? = null,
    activeTossIn: ActiveTossIn? = null,
    difficulty: Difficulty = Difficulty.MODERATE,
    rngState: Long = 0,
) = GameState(
    gameId = "test-game",
    roundNumber = roundNumber,
    turnNumber = turnNumber,
    phase = phase,
    subPhase = subPhase,
    finalTurnTriggered = finalTurnTriggered,
    players = players,
    currentPlayerIndex = currentPlayerIndex,
    vintoCallerId = vintoCallerId,
    coalitionLeaderId = coalitionLeaderId,
    drawPile = drawPile,
    discardPile = discardPile,
    pendingAction = pendingAction,
    activeTossIn = activeTossIn,
    turnActions = emptyList(),
    roundActions = emptyList(),
    roundFailedAttempts = emptyList(),
    difficulty = difficulty,
    rngState = rngState,
)

/** A card mid-play: drawn and awaiting a decision, or aimed and awaiting targets. */
fun pending(
    card: Card,
    playerId: String,
    actionPhase: ActionPhase = ActionPhase.SELECTING_TARGET,
    from: PendingCardOrigin = PendingCardOrigin.DRAWING,
    targets: List<ActionTarget> = emptyList(),
    declaredRank: Rank? = null,
    swapPosition: Int? = null,
) = PendingAction(
    card = card,
    playerId = playerId,
    actionPhase = actionPhase,
    from = from,
    targets = targets,
    declaredRank = declaredRank,
    swapPosition = swapPosition,
)

/**
 * Reduces, and throws with the reason if the engine refuses.
 *
 * Tests that expect a refusal check `ReduceResult.Failure` directly; everywhere else a
 * rejected action means the test's own setup is wrong, and failing loudly at that point is
 * far easier to read than an assertion about an unchanged state ten lines later.
 */
fun unsafeReduce(state: GameState, action: GameAction): GameState =
    when (val result = GameEngine.reduce(state, action)) {
        is ReduceResult.Success -> result.state
        is ReduceResult.Failure -> error("Action reduction failed: ${result.reason} (${action.type})")
    }

/** Walks the toss-in window forward by having each named player say they are done. */
fun markPlayersReady(state: GameState, playerIds: List<String>): GameState =
    playerIds.fold(state) { current, playerId ->
        unsafeReduce(current, GameAction.PlayerTossInFinished(PlayerIdPayload(playerId)))
    }

/** An open toss-in window on the given ranks. */
@Suppress("LongParameterList")
fun tossIn(
    ranks: List<Rank>,
    initiatorId: String,
    originalPlayerIndex: Int = 0,
    participants: List<String> = emptyList(),
    queuedActions: List<game.vinto.shapes.TossInAction> = emptyList(),
    waitingForInput: Boolean = false,
    playersReadyForNextTurn: List<String> = emptyList(),
) = ActiveTossIn(
    ranks = ranks,
    initiatorId = initiatorId,
    originalPlayerIndex = originalPlayerIndex,
    participants = participants,
    queuedActions = queuedActions,
    waitingForInput = waitingForInput,
    playersReadyForNextTurn = playersReadyForNextTurn,
)

/** Convenience for the actions a test fires most often. */
fun selectTarget(playerId: String, targetPlayerId: String, position: Int) =
    GameAction.SelectActionTarget(
        game.vinto.shapes.SelectActionTargetPayload.Positional(playerId, targetPlayerId, position),
    )

fun selectPlayerTarget(playerId: String, targetPlayerId: String) =
    GameAction.SelectActionTarget(
        game.vinto.shapes.SelectActionTargetPayload.Ace(playerId, targetPlayerId),
    )

fun confirmPeek(playerId: String) = GameAction.ConfirmPeek(PlayerIdPayload(playerId))

fun useCardAction(playerId: String) = GameAction.UseCardAction(PlayerIdPayload(playerId))

fun swapCard(playerId: String, position: Int, declaredRank: Rank? = null) =
    GameAction.SwapCard(game.vinto.shapes.SwapCardPayload(playerId, position, declaredRank))

fun discardCard(playerId: String) = GameAction.DiscardCard(PlayerIdPayload(playerId))

fun drawCard(playerId: String) = GameAction.DrawCard(PlayerIdPayload(playerId))

fun playDiscard(playerId: String) = GameAction.PlayDiscard(PlayerIdPayload(playerId))

fun participateInTossIn(playerId: String, positions: List<Int>) =
    GameAction.ParticipateInTossIn(
        game.vinto.shapes.ParticipateInTossInPayload(playerId, positions),
    )

fun callVinto(playerId: String) = GameAction.CallVinto(PlayerIdPayload(playerId))

fun declareKing(playerId: String, declaredRank: Rank) =
    GameAction.DeclareKingAction(
        game.vinto.shapes.DeclareKingActionPayload(playerId, declaredRank),
    )

/** True when the engine refuses the action — what the TypeScript spells `expect(...).toThrow()`. */
fun rejects(state: GameState, action: GameAction): Boolean =
    GameEngine.reduce(state, action) is ReduceResult.Failure
