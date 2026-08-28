package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.FailedTossInAttempt
import game.vinto.shapes.GameActionHistory
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Prng
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import game.vinto.shapes.TargetType
import game.vinto.shapes.TossInAction
import kotlinx.serialization.json.JsonElement

/**
 * A mutable working copy of [GameState], used only inside the reducer.
 *
 * The TypeScript handlers deep-copy the state and then mutate it freely — `player.cards[2]
 * .played = true`, `state.activeTossIn.queuedActions.shift()`, and so on. Porting that into
 * immutable Kotlin would mean restructuring every handler into nested `copy()` chains, and
 * the parity gate cannot tell a faithful restructuring from a subtly wrong one.
 *
 * So the port keeps design D3's file-for-file rule literally: [GameState.toMutable] takes a
 * deep working copy, each handler mutates it exactly as its TypeScript counterpart does, and
 * [MutableGameState.freeze] produces the immutable result. `GameEngine.reduce` stays a pure
 * function of state and action; the mutation is confined inside one call and never escapes.
 *
 * The alternative — rewriting each handler idiomatically — is the better Kotlin and the
 * worse migration. It can be done later, one handler at a time, with the parity gate
 * holding the behaviour still.
 */
class MutableCard(
    var id: String,
    var rank: Rank,
    var value: Int,
    var actionText: String?,
    var played: Boolean,
) {
    constructor(card: Card) : this(card.id, card.rank, card.value, card.actionText, card.played)

    fun freeze() = Card(id = id, rank = rank, value = value, actionText = actionText, played = played)

    fun copy() = MutableCard(id, rank, value, actionText, played)
}

/** Mirrors the mutating `Pile` in `legacy-web/packages/shapes/src/lib/domain-types.ts`. */
class MutablePile(cards: List<MutableCard> = emptyList()) {

    val cards: MutableList<MutableCard> = cards.toMutableList()

    constructor(pile: Pile) : this(pile.toList().map { MutableCard(it) })

    val length: Int get() = cards.size

    fun isEmpty(): Boolean = cards.isEmpty()

    fun at(index: Int): MutableCard? {
        val normalized = if (index >= 0) index else cards.size + index
        return cards.getOrNull(normalized)
    }

    fun peekTop(): MutableCard? = at(0)

    fun drawTop(): MutableCard? = if (cards.isEmpty()) null else cards.removeAt(0)

    fun addToTop(card: MutableCard) = cards.add(0, card)

    fun addBeforeTop(card: MutableCard) = cards.add(if (cards.isEmpty()) 0 else 1, card)

    fun takeAt(index: Int): MutableCard? =
        if (index !in cards.indices) null else cards.removeAt(index)

    fun replace(newCards: List<MutableCard>) {
        cards.clear()
        cards.addAll(newCards)
    }

    /**
     * Reshuffles [other] (minus its top card) into this pile, returning the advanced
     * generator state which the caller MUST store back into `rngState` — otherwise the
     * next reshuffle repeats this one.
     */
    fun reshuffleFrom(other: MutablePile, rngState: Long): Long {
        val otherCards = other.cards.toList()
        val otherTopCard = otherCards.firstOrNull()
        val cardsToShuffle = otherCards.drop(1)

        val thisTopCard = drawTop()

        val toShuffle = (cardsToShuffle + cards).map { it.copy().also { c -> c.played = false } }
        val shuffled = Prng.shuffle(toShuffle, rngState)

        replace(listOfNotNull(thisTopCard?.copy()?.also { it.played = false }) + shuffled.items)
        other.replace(listOfNotNull(otherTopCard))

        return shuffled.state
    }

    fun freeze() = Pile(cards.map { it.freeze() })
}

class MutablePlayerState(source: PlayerState) {
    var id: String = source.id
    var name: String = source.name
    var nickname: String = source.nickname
    var isHuman: Boolean = source.isHuman
    var isBot: Boolean = source.isBot
    val cards: MutableList<MutableCard> = source.cards.map { MutableCard(it) }.toMutableList()
    val knownCardPositions: MutableList<Int> = source.knownCardPositions.toMutableList()
    var isVintoCaller: Boolean = source.isVintoCaller
    val coalitionWith: MutableList<String> = source.coalitionWith.toMutableList()
    var botMemory: JsonElement? = source.botMemory
    var opponentKnowledge: MutableMap<String, SerializedOpponentKnowledge>? =
        source.opponentKnowledge?.toMutableMap()
    var declaredCards: MutableMap<Int, Rank>? = source.declaredCards?.toMutableMap()

    fun freeze() = PlayerState(
        id = id,
        name = name,
        nickname = nickname,
        isHuman = isHuman,
        isBot = isBot,
        cards = cards.map { it.freeze() },
        knownCardPositions = knownCardPositions.toList(),
        isVintoCaller = isVintoCaller,
        coalitionWith = coalitionWith.toList(),
        botMemory = botMemory,
        opponentKnowledge = opponentKnowledge?.toMap(),
        // An emptied map goes back to null so the field re-omits from serialisation — that
        // is what keeps a state that never declared anything hashing exactly as before.
        declaredCards = declaredCards?.takeIf { it.isNotEmpty() }?.toMap(),
    )
}

class MutablePendingAction(
    var card: MutableCard,
    var playerId: String,
    var actionPhase: ActionPhase,
    var from: PendingCardOrigin,
    var targetType: TargetType? = null,
    val targets: MutableList<ActionTarget> = mutableListOf(),
    var declaredRank: Rank? = null,
    var swapPosition: Int? = null,
) {
    constructor(source: PendingAction) : this(
        card = MutableCard(source.card),
        playerId = source.playerId,
        actionPhase = source.actionPhase,
        from = source.from,
        targetType = source.targetType,
        targets = source.targets.toMutableList(),
        declaredRank = source.declaredRank,
        swapPosition = source.swapPosition,
    )

    fun freeze() = PendingAction(
        card = card.freeze(),
        playerId = playerId,
        actionPhase = actionPhase,
        from = from,
        targetType = targetType,
        targets = targets.toList(),
        declaredRank = declaredRank,
        swapPosition = swapPosition,
    )
}

class MutableActiveTossIn(
    var ranks: MutableList<Rank>,
    var initiatorId: String,
    var originalPlayerIndex: Int,
    var participants: MutableList<String> = mutableListOf(),
    var queuedActions: MutableList<TossInAction> = mutableListOf(),
    var waitingForInput: Boolean = true,
    var timeRemaining: Int? = null,
    var playersReadyForNextTurn: MutableList<String> = mutableListOf(),
    var failedAttempts: MutableList<FailedTossInAttempt>? = null,
    var tossInCompleted: Boolean? = null,
) {
    constructor(source: ActiveTossIn) : this(
        ranks = source.ranks.toMutableList(),
        initiatorId = source.initiatorId,
        originalPlayerIndex = source.originalPlayerIndex,
        participants = source.participants.toMutableList(),
        queuedActions = source.queuedActions.toMutableList(),
        waitingForInput = source.waitingForInput,
        timeRemaining = source.timeRemaining,
        playersReadyForNextTurn = source.playersReadyForNextTurn.toMutableList(),
        failedAttempts = source.failedAttempts?.toMutableList(),
        tossInCompleted = source.tossInCompleted,
    )

    fun freeze() = ActiveTossIn(
        ranks = ranks.toList(),
        initiatorId = initiatorId,
        originalPlayerIndex = originalPlayerIndex,
        participants = participants.toList(),
        queuedActions = queuedActions.toList(),
        waitingForInput = waitingForInput,
        timeRemaining = timeRemaining,
        playersReadyForNextTurn = playersReadyForNextTurn.toList(),
        failedAttempts = failedAttempts?.toList(),
        tossInCompleted = tossInCompleted,
    )
}

class MutableGameState(source: GameState) {
    /**
     * Cards this action turned face up for the whole table.
     *
     * Not part of the game's state, and deliberately so: it is what *happened*, not what *is*.
     * A card revealed to the table is revealed for a moment and then goes on being a
     * face-down card everybody is expected to remember — the rules have it that way, and a
     * state that carried the reveal would have to carry an expiry for it too, in both engines,
     * and in every recorded hash.
     *
     * It rides out on [ReduceResult.Success] instead, which is where a room would put it when
     * telling four clients what they just watched.
     */
    val revealed: MutableList<PublicReveal> = mutableListOf()

    var gameId: String = source.gameId
    var roundNumber: Int = source.roundNumber
    var turnNumber: Int = source.turnNumber
    var phase: GamePhase = source.phase
    var subPhase: GameSubPhase = source.subPhase
    var finalTurnTriggered: Boolean = source.finalTurnTriggered
    val players: MutableList<MutablePlayerState> =
        source.players.map { MutablePlayerState(it) }.toMutableList()
    var currentPlayerIndex: Int = source.currentPlayerIndex
    var vintoCallerId: String? = source.vintoCallerId
    var coalitionLeaderId: String? = source.coalitionLeaderId
    var drawPile: MutablePile = MutablePile(source.drawPile)
    var discardPile: MutablePile = MutablePile(source.discardPile)
    var pendingAction: MutablePendingAction? = source.pendingAction?.let { MutablePendingAction(it) }
    var activeTossIn: MutableActiveTossIn? = source.activeTossIn?.let { MutableActiveTossIn(it) }
    var turnActions: MutableList<GameActionHistory> = source.turnActions.toMutableList()
    var roundActions: MutableList<GameActionHistory> = source.roundActions.toMutableList()
    var roundFailedAttempts: MutableList<FailedTossInAttempt> =
        source.roundFailedAttempts.toMutableList()
    var difficulty: Difficulty = source.difficulty
    var rngState: Long = source.rngState

    fun playerById(id: String): MutablePlayerState? = players.firstOrNull { it.id == id }

    fun freeze() = GameState(
        gameId = gameId,
        roundNumber = roundNumber,
        turnNumber = turnNumber,
        phase = phase,
        subPhase = subPhase,
        finalTurnTriggered = finalTurnTriggered,
        players = players.map { it.freeze() },
        currentPlayerIndex = currentPlayerIndex,
        vintoCallerId = vintoCallerId,
        coalitionLeaderId = coalitionLeaderId,
        drawPile = drawPile.freeze(),
        discardPile = discardPile.freeze(),
        pendingAction = pendingAction?.freeze(),
        activeTossIn = activeTossIn?.freeze(),
        turnActions = turnActions.toList(),
        roundActions = roundActions.toList(),
        roundFailedAttempts = roundFailedAttempts.toList(),
        difficulty = difficulty,
        rngState = rngState,
    )
}

fun GameState.toMutable() = MutableGameState(this)
