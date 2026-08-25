package game.vinto.shapes

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `RecordingParityTest` round-trips all 13,900 actions in the corpus and is the real gate,
 * but a corpus only covers what those games happened to do. These pin the wire shape itself,
 * and the cases the corpus never produced — most importantly the `rank: 'A'` variant of
 * `SELECT_ACTION_TARGET`, which appears nowhere in the 50 recordings.
 */
class GameActionTest {

    private fun encode(action: GameAction) =
        CanonicalJson.of(VintoJson.encodeToJsonElement(GameActionSerializer, action))

    private fun decode(raw: String) =
        VintoJson.decodeFromJsonElement(GameActionSerializer, Json.parseToJsonElement(raw))

    @Test
    fun usesTheTwoLevelWireShape() {
        assertEquals(
            """{"payload":{"playerId":"p1"},"type":"DRAW_CARD"}""",
            encode(GameAction.DrawCard(PlayerIdPayload("p1"))),
        )
    }

    @Test
    fun omitsAnUnsetOptionalRatherThanWritingNull() {
        assertEquals(
            """{"payload":{"playerId":"p1","position":2},"type":"SWAP_CARD"}""",
            encode(GameAction.SwapCard(SwapCardPayload("p1", 2))),
        )
        assertEquals(
            """{"payload":{"declaredRank":"Q","playerId":"p1","position":2},"type":"SWAP_CARD"}""",
            encode(GameAction.SwapCard(SwapCardPayload("p1", 2, Rank.QUEEN))),
        )
    }

    @Test
    fun writesTheAceTargetVariantWithoutAPosition() {
        // Not present anywhere in the recording corpus, so it is pinned here.
        assertEquals(
            """{"payload":{"playerId":"p1","rank":"A","targetPlayerId":"p2"},"type":"SELECT_ACTION_TARGET"}""",
            encode(GameAction.SelectActionTarget(SelectActionTargetPayload.Ace("p1", "p2"))),
        )
    }

    @Test
    fun writesThePositionalTargetVariant() {
        assertEquals(
            """{"payload":{"playerId":"p1","position":3,"rank":"Any","targetPlayerId":"p2"},"type":"SELECT_ACTION_TARGET"}""",
            encode(GameAction.SelectActionTarget(SelectActionTargetPayload.Positional("p1", "p2", 3))),
        )
    }

    @Test
    fun readsBothTargetVariantsBack() {
        val ace = decode(
            """{"type":"SELECT_ACTION_TARGET","payload":{"rank":"A","playerId":"p1","targetPlayerId":"p2"}}""",
        )
        assertEquals(
            GameAction.SelectActionTarget(SelectActionTargetPayload.Ace("p1", "p2")),
            ace,
        )

        val positional = decode(
            """{"type":"SELECT_ACTION_TARGET","payload":{"rank":"Any","playerId":"p1","targetPlayerId":"p2","position":3}}""",
        )
        assertEquals(
            GameAction.SelectActionTarget(SelectActionTargetPayload.Positional("p1", "p2", 3)),
            positional,
        )
    }

    @Test
    fun rejectsAnUnknownTargetDiscriminator() {
        val failure = assertFailsWith<IllegalArgumentException> {
            decode("""{"type":"SELECT_ACTION_TARGET","payload":{"rank":"K","playerId":"p1","targetPlayerId":"p2"}}""")
        }
        assertTrue(failure.message!!.contains("expected 'A' or 'Any'"), failure.message!!)
    }

    @Test
    fun rejectsAnUnknownActionType() {
        val failure = assertFailsWith<IllegalArgumentException> {
            decode("""{"type":"NOT_AN_ACTION","payload":{}}""")
        }
        assertTrue(failure.message!!.contains("NOT_AN_ACTION"), failure.message!!)
    }

    @Test
    fun carriesTheEmptyActionPayloadThrough() {
        // `payload: any` in TypeScript, so whatever is there survives the round trip.
        assertEquals(
            """{"payload":{"anything":[1,2]},"type":"EMPTY"}""",
            encode(decode("""{"type":"EMPTY","payload":{"anything":[1,2]}}""")),
        )
    }

    @Test
    fun roundTripsEveryActionTypeItModels() {
        val actions = listOf(
            GameAction.DrawCard(PlayerIdPayload("p")),
            GameAction.PlayDiscard(PlayerIdPayload("p")),
            GameAction.SwapCard(SwapCardPayload("p", 1, Rank.SEVEN)),
            GameAction.DiscardCard(PlayerIdPayload("p")),
            GameAction.UseCardAction(PlayerIdPayload("p")),
            GameAction.SelectActionTarget(SelectActionTargetPayload.Positional("p", "q", 0)),
            GameAction.ConfirmPeek(PlayerIdPayload("p")),
            GameAction.SkipPeek(PlayerIdPayload("p")),
            GameAction.ExecuteJackSwap(PlayerIdPayload("p")),
            GameAction.SkipJackSwap(PlayerIdPayload("p")),
            GameAction.ExecuteQueenSwap(PlayerIdPayload("p")),
            GameAction.SkipQueenSwap(PlayerIdPayload("p")),
            GameAction.DeclareKingAction(DeclareKingActionPayload("p", Rank.ACE)),
            GameAction.ParticipateInTossIn(ParticipateInTossInPayload("p", listOf(0, 2))),
            GameAction.PlayerTossInFinished(PlayerIdPayload("p")),
            GameAction.FinishTossInPeriod(InitiatorIdPayload("p")),
            GameAction.CallVinto(PlayerIdPayload("p")),
            GameAction.SetCoalitionLeader(LeaderIdPayload("p")),
            GameAction.DeclareCards(DeclareCardsPayload("p", mapOf(0 to Rank.QUEEN, 2 to Rank.JOKER))),
            GameAction.ProcessAiTurn(PlayerIdPayload("p")),
            GameAction.PeekSetupCard(PositionPayload("p", 4)),
            GameAction.FinishSetup(PlayerIdPayload("p")),
            GameAction.UpdateDifficulty(DifficultyPayload(Difficulty.HARD)),
            GameAction.SetNextDrawCard(RankPayload(Rank.JOKER)),
            GameAction.SwapHandWithDeck(SwapHandWithDeckPayload("p", 1, Rank.KING)),
        )

        for (action in actions) {
            val json = VintoJson.encodeToJsonElement(GameActionSerializer, action)
            assertEquals(action, VintoJson.decodeFromJsonElement(GameActionSerializer, json), action.type)
        }

        // Every branch of the sealed hierarchy except EMPTY, which is covered above.
        assertEquals(25, actions.map { it.type }.toSet().size)
    }

    @Test
    fun declareCardsCarriesPositionKeysAsStrings() {
        // JSON object keys are strings, so the Int positions ride as "0"/"2" on the wire.
        // Kotlin-only action — nothing on the TypeScript side ever reads this — but the
        // shape is part of the recording format all the same.
        val action = GameAction.DeclareCards(
            DeclareCardsPayload("p", mapOf(0 to Rank.QUEEN, 2 to Rank.JOKER)),
        )
        val json = VintoJson.encodeToJsonElement(GameActionSerializer, action).toString()
        assertEquals(
            """{"type":"DECLARE_CARDS","payload":{"playerId":"p","claims":{"0":"Q","2":"Joker"}}}""",
            json,
        )
    }
}
