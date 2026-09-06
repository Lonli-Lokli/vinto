package game.vinto.shapes

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The authoritative, fully serialisable game state — the single source of truth, and the
 * thing the cross-language hash is computed over.
 *
 * Field-for-field with `legacy-web/packages/shapes/src/lib/game-state-types.ts`. See [Card] for how
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
    /**
     * Replay-only shape. **Always null in a game dealt today**, and nothing reads it.
     *
     * The coalition used to nominate a member to play its hand, and the nomination settled
     * nothing: the round is scored against the *lowest* coalition hand whoever holds it, and
     * the bots all declare before any coalition turn is played, so the planners already reach
     * the same target from the same public claims. `SET_COALITION_LEADER` is refused by
     * `ActionValidator` now.
     *
     * The field cannot go with it. Unlike `declaredCards` it carries no `@EncodeDefault(NEVER)`
     * and no default, so it is written into **every** canonical state — including all 50
     * recordings in `fixtures/recordings/`, whose hashes a second implementation computed and
     * which cannot be regenerated. Removing it would move every one of them.
     */
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
     * What the table has *said* about this player's cards, during the final round.
     *
     * Table talk, never checked against the real cards: a claim is speech, and a player
     * declares from memory, which can be wrong. Every claim names the seat that **spoke** it,
     * which is why these hang off the card's owner rather than the speaker — a coalition
     * member may claim a teammate's card or the caller's, and storing that on the speaker
     * would lose which card it was about.
     *
     * Kotlin-only, and held to the discipline that keeps it so: `@EncodeDefault(NEVER)` with
     * `null` — the shape every parity recording has — restored the moment the list empties.
     * No recorded state materialises the field, so the frozen hashes cannot move.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val claims: List<Claim>? = null,

    /**
     * Retired, and kept only so a room already in flight still decodes.
     *
     * [claims] replaced it. `VintoJson` sets `ignoreUnknownKeys = false`, so a Durable Object
     * holding a final round **with table talk on it** would meet an unknown `declaredCards`
     * after a deploy and fail to read its own state — a live room bricked mid-game, for a
     * field nobody needs any more. Accepting and ignoring it costs that room its standing
     * claims and lets it play on, which is the right way round.
     *
     * Nothing reads it and nothing writes it: `@EncodeDefault(NEVER)` with a null default, so
     * it is absent from every state this build produces and the corpus hashes are untouched.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Deprecated("Replaced by claims; kept so an in-flight room still decodes.")
    val declaredCards: Map<Int, Rank>? = null,
)

/**
 * One thing somebody said about one player's cards.
 *
 * The shape carries **partial** knowledge on purpose, because an exact claim is the least
 * common thing a person actually has ten turns after setup. What they have is the pair
 * without the order — "those two are a King and an Ace, and I have lost which is which" — and
 * a vocabulary that cannot say that forces a guess, which the coalition then plans on as
 * though it were a fact.
 *
 * It is not a new idea in this codebase, either: `CardMemory` already carries a per-card
 * `confidence` that `believedOwnCards` flattens away at the moment a bot speaks.
 *
 * | Said | [positions] | [ranks] | [covering] |
 * | --- | --- | --- | --- |
 * | "my third is a King" | `[3]` | `[K]` | `true` |
 * | "these two are a King and an Ace, I forget which" | `[2, 4]` | `[K, A]` | `true` |
 * | "it is a 2 or a 3" | `[3]` | `[2, 3]` | `false` |
 * | "there is a Joker in here somewhere" | `[]` | `[Joker]` | `false` |
 */
@Serializable
data class Claim(
    /** The seat that said it — never inferred from whose cards these are. */
    val by: String,
    /**
     * Which of the owner's positions this is about. Empty means "somewhere in this hand",
     * which constrains no single position and is a statement about the hand as a whole.
     */
    val positions: List<Int>,
    /** The ranks in play. One for an exact claim; two for a pair whose order is lost. */
    val ranks: List<Rank>,
    /**
     * True when [ranks] is exactly what [positions] holds, in some order — "these two are a
     * King and an Ace". False when each position is merely *one of* [ranks] — "it is low".
     *
     * The two readings only differ once [positions] names more than one card.
     */
    val covering: Boolean = true,
) {
    /**
     * Whether this claim says anything at all: one that names every rank narrows nothing.
     *
     * It is how a speaker takes back what they said about **one** card without touching the
     * rest: a declaration replaces the speaker's earlier claims that overlap it, so a claim of
     * "any rank" on that card replaces the old word and leaves nothing to believe. Belief reads
     * past it (`standingClaims`), and it stays in the state only as the trace that the seat
     * has spoken for the hand.
     */
    val vacuous: Boolean get() = ranks.toSet().containsAll(ALL_RANKS)
}

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
