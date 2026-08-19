package game.vinto.shapes

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Every interaction with the engine, as data.
 *
 * A sealed hierarchy rather than a bag of strings, so a `when` over an action in the engine
 * is exhaustive and adding a case is a compile error at every handler — which is the point
 * of porting the union rather than reinventing it.
 *
 * The wire form is TypeScript's discriminated union, `{ "type": ..., "payload": {...} }`.
 * kotlinx's built-in polymorphism writes its discriminator as a sibling of the payload's
 * own fields, so [GameActionSerializer] builds the two-level shape by hand instead.
 *
 * Payload types are shared where TypeScript shares them: fourteen actions carry nothing but
 * a `playerId`, and giving each its own identical payload class would be noise.
 */
@Serializable(with = GameActionSerializer::class)
sealed interface GameAction {
    /** The wire discriminator. Must match `packages/shapes/src/lib/action-types.ts`. */
    val type: String

    // --- Turn actions ---

    @Serializable
    data class DrawCard(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "DRAW_CARD"
    }

    @Serializable
    data class PlayDiscard(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "PLAY_DISCARD"
    }

    @Serializable
    data class SwapCard(val payload: SwapCardPayload) : GameAction {
        override val type get() = "SWAP_CARD"
    }

    @Serializable
    data class DiscardCard(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "DISCARD_CARD"
    }

    // --- Card actions ---

    @Serializable
    data class UseCardAction(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "USE_CARD_ACTION"
    }

    @Serializable
    data class SelectActionTarget(val payload: SelectActionTargetPayload) : GameAction {
        override val type get() = "SELECT_ACTION_TARGET"
    }

    @Serializable
    data class ConfirmPeek(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "CONFIRM_PEEK"
    }

    @Serializable
    data class SkipPeek(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "SKIP_PEEK"
    }

    @Serializable
    data class ExecuteJackSwap(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "EXECUTE_JACK_SWAP"
    }

    @Serializable
    data class SkipJackSwap(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "SKIP_JACK_SWAP"
    }

    @Serializable
    data class ExecuteQueenSwap(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "EXECUTE_QUEEN_SWAP"
    }

    @Serializable
    data class SkipQueenSwap(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "SKIP_QUEEN_SWAP"
    }

    @Serializable
    data class DeclareKingAction(val payload: DeclareKingActionPayload) : GameAction {
        override val type get() = "DECLARE_KING_ACTION"
    }

    // --- Toss-in actions ---

    @Serializable
    data class ParticipateInTossIn(val payload: ParticipateInTossInPayload) : GameAction {
        override val type get() = "PARTICIPATE_IN_TOSS_IN"
    }

    @Serializable
    data class PlayerTossInFinished(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "PLAYER_TOSS_IN_FINISHED"
    }

    @Serializable
    data class FinishTossInPeriod(val payload: InitiatorIdPayload) : GameAction {
        override val type get() = "FINISH_TOSS_IN_PERIOD"
    }

    // --- Game flow ---

    @Serializable
    data class CallVinto(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "CALL_VINTO"
    }

    @Serializable
    data class SetCoalitionLeader(val payload: LeaderIdPayload) : GameAction {
        override val type get() = "SET_COALITION_LEADER"
    }

    @Serializable
    data class ProcessAiTurn(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "PROCESS_AI_TURN"
    }

    // --- Setup ---

    @Serializable
    data class PeekSetupCard(val payload: PositionPayload) : GameAction {
        override val type get() = "PEEK_SETUP_CARD"
    }

    @Serializable
    data class FinishSetup(val payload: PlayerIdPayload) : GameAction {
        override val type get() = "FINISH_SETUP"
    }

    // --- Configuration ---

    @Serializable
    data class UpdateDifficulty(val payload: DifficultyPayload) : GameAction {
        override val type get() = "UPDATE_DIFFICULTY"
    }

    // --- Debug / testing ---

    @Serializable
    data class SetNextDrawCard(val payload: RankPayload) : GameAction {
        override val type get() = "SET_NEXT_DRAW_CARD"
    }

    @Serializable
    data class SwapHandWithDeck(val payload: SwapHandWithDeckPayload) : GameAction {
        override val type get() = "SWAP_HAND_WITH_DECK"
    }

    /** `payload: any` in TypeScript, so it is carried through untouched. */
    @Serializable
    data class Empty(val payload: JsonElement) : GameAction {
        override val type get() = "EMPTY"
    }
}

// --- Payloads ---

@Serializable
data class PlayerIdPayload(val playerId: String)

@Serializable
data class InitiatorIdPayload(val initiatorId: String)

@Serializable
data class LeaderIdPayload(val leaderId: String)

@Serializable
data class PositionPayload(val playerId: String, val position: Int)

@Serializable
data class DifficultyPayload(val difficulty: Difficulty)

@Serializable
data class RankPayload(val rank: Rank)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SwapCardPayload(
    val playerId: String,
    val position: Int,
    /** Optional in TypeScript: absent when the player declines to declare. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val declaredRank: Rank? = null,
)

@Serializable
data class DeclareKingActionPayload(val playerId: String, val declaredRank: Rank)

@Serializable
data class ParticipateInTossInPayload(
    val playerId: String,
    /** Always at least one; TypeScript encodes that as `[number, ...number[]]`. */
    val positions: List<Int>,
)

@Serializable
data class SwapHandWithDeckPayload(
    val playerId: String,
    val handPosition: Int,
    val deckCardRank: Rank,
)

/**
 * Ace targets a player; every other action targets a specific card. TypeScript models this
 * as a union discriminated on a literal `rank` field of `'A'` or `'Any'` — not the rank of
 * a card, despite the name.
 */
@Serializable(with = SelectActionTargetPayloadSerializer::class)
sealed interface SelectActionTargetPayload {
    val playerId: String
    val targetPlayerId: String

    /** `rank: 'A'` — force-draw, which has no position. */
    @Serializable
    data class Ace(
        override val playerId: String,
        override val targetPlayerId: String,
    ) : SelectActionTargetPayload

    /** `rank: 'Any'` — every other action, which names a card position. */
    @Serializable
    data class Positional(
        override val playerId: String,
        override val targetPlayerId: String,
        val position: Int,
    ) : SelectActionTargetPayload
}

object SelectActionTargetPayloadSerializer : KSerializer<SelectActionTargetPayload> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SelectActionTargetPayload")

    override fun serialize(encoder: Encoder, value: SelectActionTargetPayload) {
        val output = encoder as? JsonEncoder
            ?: error("SelectActionTargetPayload is JSON-only")

        // `rank` is not a field on either class — it exists purely as the discriminator, so
        // it is written here rather than duplicated into both payloads as a constant.
        val body = when (value) {
            is SelectActionTargetPayload.Ace ->
                output.json.encodeToJsonElement(SelectActionTargetPayload.Ace.serializer(), value)

            is SelectActionTargetPayload.Positional ->
                output.json.encodeToJsonElement(
                    SelectActionTargetPayload.Positional.serializer(),
                    value,
                )
        }
        val discriminator = if (value is SelectActionTargetPayload.Ace) "A" else "Any"

        output.encodeJsonElement(
            JsonObject(mapOf("rank" to JsonPrimitive(discriminator)) + body.jsonObject),
        )
    }

    override fun deserialize(decoder: Decoder): SelectActionTargetPayload {
        val input = decoder as? JsonDecoder
            ?: error("SelectActionTargetPayload is JSON-only")
        val element = input.decodeJsonElement().jsonObject
        val body = JsonObject(element - "rank")

        return when (val rank = element["rank"]?.jsonPrimitive?.content) {
            "A" -> input.json.decodeFromJsonElement(SelectActionTargetPayload.Ace.serializer(), body)
            "Any" -> input.json.decodeFromJsonElement(
                SelectActionTargetPayload.Positional.serializer(),
                body,
            )

            else -> throw IllegalArgumentException(
                "SELECT_ACTION_TARGET payload has rank '$rank'; expected 'A' or 'Any'",
            )
        }
    }
}

/**
 * Builds TypeScript's two-level `{ type, payload }` shape.
 *
 * The `when` is exhaustive in both directions, so a new action cannot be added without
 * teaching both halves about it — which is what stops the two implementations drifting.
 */
object GameActionSerializer : KSerializer<GameAction> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GameAction") {
        element("type", PrimitiveSerialDescriptor("type", PrimitiveKind.STRING))
        element("payload", buildClassSerialDescriptor("payload"))
    }

    /**
     * Detekt reads this as highly complex. What it measures is the size of the action union,
     * not the difficulty of the code: an exhaustive `when` over the sealed hierarchy is what
     * makes adding an action a compile error here, which is worth more than the metric.
     */
    @Suppress("CyclomaticComplexMethod")
    override fun serialize(encoder: Encoder, value: GameAction) {
        val output = encoder as? JsonEncoder
            ?: error("GameAction is JSON-only")
        val json = output.json

        fun <T> enc(serializer: KSerializer<T>, payload: T): JsonElement =
            json.encodeToJsonElement(serializer, payload)

        val payload: JsonElement = when (value) {
            is GameAction.DrawCard -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.PlayDiscard -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.SwapCard -> enc(SwapCardPayload.serializer(), value.payload)
            is GameAction.DiscardCard -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.UseCardAction -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.SelectActionTarget -> enc(SelectActionTargetPayloadSerializer, value.payload)
            is GameAction.ConfirmPeek -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.SkipPeek -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.ExecuteJackSwap -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.SkipJackSwap -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.ExecuteQueenSwap -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.SkipQueenSwap -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.DeclareKingAction -> enc(DeclareKingActionPayload.serializer(), value.payload)
            is GameAction.ParticipateInTossIn -> enc(ParticipateInTossInPayload.serializer(), value.payload)
            is GameAction.PlayerTossInFinished -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.FinishTossInPeriod -> enc(InitiatorIdPayload.serializer(), value.payload)
            is GameAction.CallVinto -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.SetCoalitionLeader -> enc(LeaderIdPayload.serializer(), value.payload)
            is GameAction.ProcessAiTurn -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.PeekSetupCard -> enc(PositionPayload.serializer(), value.payload)
            is GameAction.FinishSetup -> enc(PlayerIdPayload.serializer(), value.payload)
            is GameAction.UpdateDifficulty -> enc(DifficultyPayload.serializer(), value.payload)
            is GameAction.SetNextDrawCard -> enc(RankPayload.serializer(), value.payload)
            is GameAction.SwapHandWithDeck -> enc(SwapHandWithDeckPayload.serializer(), value.payload)
            is GameAction.Empty -> value.payload
        }

        output.encodeJsonElement(
            buildJsonObject {
                put("type", value.type)
                put("payload", payload)
            },
        )
    }

    /** Complex for the same reason [serialize] is: one branch per action type. */
    @Suppress("CyclomaticComplexMethod")
    override fun deserialize(decoder: Decoder): GameAction {
        val input = decoder as? JsonDecoder
            ?: error("GameAction is JSON-only")
        val json = input.json
        val element = input.decodeJsonElement().jsonObject

        val type = element["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("action has no 'type'")
        val payload = element["payload"]
            ?: throw IllegalArgumentException("action '$type' has no 'payload'")

        fun <T> dec(serializer: KSerializer<T>): T =
            json.decodeFromJsonElement(serializer, payload)
        fun playerId() = dec(PlayerIdPayload.serializer())

        return when (type) {
            "DRAW_CARD" -> GameAction.DrawCard(playerId())
            "PLAY_DISCARD" -> GameAction.PlayDiscard(playerId())
            "SWAP_CARD" -> GameAction.SwapCard(dec(SwapCardPayload.serializer()))
            "DISCARD_CARD" -> GameAction.DiscardCard(playerId())
            "USE_CARD_ACTION" -> GameAction.UseCardAction(playerId())
            "SELECT_ACTION_TARGET" -> GameAction.SelectActionTarget(dec(SelectActionTargetPayloadSerializer))
            "CONFIRM_PEEK" -> GameAction.ConfirmPeek(playerId())
            "SKIP_PEEK" -> GameAction.SkipPeek(playerId())
            "EXECUTE_JACK_SWAP" -> GameAction.ExecuteJackSwap(playerId())
            "SKIP_JACK_SWAP" -> GameAction.SkipJackSwap(playerId())
            "EXECUTE_QUEEN_SWAP" -> GameAction.ExecuteQueenSwap(playerId())
            "SKIP_QUEEN_SWAP" -> GameAction.SkipQueenSwap(playerId())
            "DECLARE_KING_ACTION" -> GameAction.DeclareKingAction(dec(DeclareKingActionPayload.serializer()))
            "PARTICIPATE_IN_TOSS_IN" ->
                GameAction.ParticipateInTossIn(dec(ParticipateInTossInPayload.serializer()))
            "PLAYER_TOSS_IN_FINISHED" -> GameAction.PlayerTossInFinished(playerId())
            "FINISH_TOSS_IN_PERIOD" -> GameAction.FinishTossInPeriod(dec(InitiatorIdPayload.serializer()))
            "CALL_VINTO" -> GameAction.CallVinto(playerId())
            "SET_COALITION_LEADER" -> GameAction.SetCoalitionLeader(dec(LeaderIdPayload.serializer()))
            "PROCESS_AI_TURN" -> GameAction.ProcessAiTurn(playerId())
            "PEEK_SETUP_CARD" -> GameAction.PeekSetupCard(dec(PositionPayload.serializer()))
            "FINISH_SETUP" -> GameAction.FinishSetup(playerId())
            "UPDATE_DIFFICULTY" -> GameAction.UpdateDifficulty(dec(DifficultyPayload.serializer()))
            "SET_NEXT_DRAW_CARD" -> GameAction.SetNextDrawCard(dec(RankPayload.serializer()))
            "SWAP_HAND_WITH_DECK" -> GameAction.SwapHandWithDeck(dec(SwapHandWithDeckPayload.serializer()))
            "EMPTY" -> GameAction.Empty(payload)
            else -> throw IllegalArgumentException("unknown action type '$type'")
        }
    }
}
