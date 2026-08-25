package game.vinto.shapes

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The authoritative, fully serialisable game state — the single source of truth, and the
 * thing the cross-language hash is computed over.
 *
 * Field-for-field with `packages/shapes/src/lib/game-state-types.ts`. See [Card] for how
 * TypeScript's optional-versus-nullable distinction is expressed here; it is load-bearing.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GameState(
    val gameId: String,
    val roundNumber: Int,
    val turnNumber: Int,

    val phase: GamePhase,
    val subPhase: GameSubPhase,
    val finalTurnTriggered: Boolean,

    val players: List<PlayerState>,
    val currentPlayerIndex: Int,

    val vintoCallerId: String?,
    val coalitionLeaderId: String?,

    val drawPile: Pile,
    val discardPile: Pile,

    val pendingAction: PendingAction?,
    val activeTossIn: ActiveTossIn?,

    val turnActions: List<GameActionHistory>,
    val roundActions: List<GameActionHistory>,
    val roundFailedAttempts: List<FailedTossInAttempt>,

    val difficulty: Difficulty,

    /**
     * Seeded mulberry32 state, unsigned 32-bit, carried as [Long] — a signed [Int] would
     * corrupt any value at or above 2^31. The engine's only source of randomness; every
     * handler that consumes it must store the advanced state back. See [Prng].
     */
    val rngState: Long,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val nickname: String,
    val isHuman: Boolean,
    val isBot: Boolean,
    val cards: List<Card>,

    /** A serialisable set — positions this player has seen. */
    val knownCardPositions: List<Int>,

    val isVintoCaller: Boolean,
    val coalitionWith: List<String>,

    /**
     * Bot-internal and float-bearing, so it is excluded from the canonical hash and kept
     * as raw JSON rather than modelled — the engine never writes it.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val botMemory: kotlinx.serialization.json.JsonElement? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val opponentKnowledge: Map<String, SerializedOpponentKnowledge>? = null,

    /**
     * Position → rank this player has *claimed* their card to be, out loud, during the
     * final round — table talk, never checked against the real card, so a claim can be
     * wrong. Kotlin-only: `null` (the TypeScript states' shape) is omitted from
     * serialisation, which is what keeps the parity corpus hashes untouched; the engine
     * normalises an emptied map back to `null` for the same reason.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val declaredCards: Map<Int, Rank>? = null,
)

@Serializable
data class SerializedOpponentKnowledge(
    val knownCards: Map<Int, Card>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PendingAction(
    val card: Card,
    val playerId: String,
    val actionPhase: ActionPhase,
    val from: PendingCardOrigin,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val targetType: TargetType? = null,
    val targets: List<ActionTarget>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val declaredRank: Rank? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val swapPosition: Int? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ActionTarget(
    val playerId: String,
    val position: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val card: Card? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ActiveTossIn(
    /** Always at least one rank; TypeScript encodes that as `[Rank, ...Rank[]]`. */
    val ranks: List<Rank>,
    val initiatorId: String,
    /** Whose turn it was when the toss-in started, so it can be restored afterwards. */
    val originalPlayerIndex: Int,
    val participants: List<String>,
    val queuedActions: List<TossInAction>,
    val waitingForInput: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timeRemaining: Int? = null,
    val playersReadyForNextTurn: List<String>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val failedAttempts: List<FailedTossInAttempt>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tossInCompleted: Boolean? = null,
)

@Serializable
data class TossInAction(
    val playerId: String,
    val rank: Rank,
    val position: Int,
)

@Serializable
data class FailedTossInAttempt(
    val playerId: String,
    val cardRank: Rank,
    val position: Int,
    val expectedRanks: List<Rank>,
)

/**
 * UI-facing history. Excluded from the canonical hash: `description` is user-facing prose,
 * and hashing it would make UI copy part of the cross-language contract.
 */
@Serializable
data class GameActionHistory(
    val playerId: String,
    val playerName: String,
    val description: String,
    val timestamp: Long,
    val turnNumber: Int,
    val roundNumber: Int,
)
