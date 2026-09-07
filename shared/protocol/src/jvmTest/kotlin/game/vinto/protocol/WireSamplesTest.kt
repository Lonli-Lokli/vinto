package game.vinto.protocol

import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.DeclareCardsPayload
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.DifficultyPayload
import game.vinto.shapes.GameAction
import game.vinto.shapes.InitiatorIdPayload
import game.vinto.shapes.Lane
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.Shed
import game.vinto.shapes.Step
import game.vinto.shapes.SwapCardPayload
import game.vinto.shapes.SwapHandWithDeckPayload
import game.vinto.shapes.TableTalk
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * One sample of every message and every action, frozen as the text the wire carries, per
 * protocol version — `fixtures/protocol/v<N>/`.
 *
 * [WireFreezeTest] holds the vocabulary still; this holds the *shapes*. The break that made
 * the number necessary was not a new tag: `DECLARE_CARDS` existed in every store build, and
 * its payload changed under it, so an old build dropped every events batch that carried one
 * and froze in the final round. A tag list cannot see that. A frozen sample can: change a
 * field's type, rename one, make an optional one required, and the encoded text moves and
 * this fails — until the version is bumped and the new shapes are written down under it.
 *
 * Two directions are held. The current encoder must reproduce the current version's samples
 * exactly. And every older version's samples must still *decode* with the current decoder,
 * which is the room reading a build older than itself. (The other direction — an old decoder
 * reading the new room — cannot run here; it is what the floor is for.)
 *
 * Version 1 has no directory: the samples were born with the number, and what version 1 sent
 * is on `master` before it.
 */
class WireSamplesTest {
    private val fixtures = File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "protocol")
    private val current = File(fixtures, "v$PROTOCOL_VERSION")
    private val writing = System.getProperty("vinto.wire.write") == "true"

    private val view = projectView(initializeGame(seed = 7L, difficulty = Difficulty.EASY), "human-1")
    private val lobby = LobbyView(
        phase = RoomPhase.LOBBY,
        seats = listOf(
            LobbySeat(index = 0, occupied = true, isBot = false, removable = false, nickname = "Amber Otter"),
        ),
        humans = 1,
    )
    private val claim = Claim(by = "p1", positions = listOf(0), ranks = listOf(Rank.KING))
    private val plan = CoalitionPlan(
        lanes = listOf(Lane("p2", Step.Swap(CardAt("p2", 0, claim), CardAt("p3", 1)))),
        sheds = listOf(Shed("p3", Rank.SEVEN)),
        agreed = listOf("p2"),
        editedBy = "p2",
    )
    private val talk: TableTalk = TableTalk.Answer(by = "p2", to = "p1", says = TableTalk.Answer.Says.YES)

    private val actions: Map<String, GameAction> = mapOf(
        "DRAW_CARD" to GameAction.DrawCard(PlayerIdPayload("p1")),
        "PLAY_DISCARD" to GameAction.PlayDiscard(PlayerIdPayload("p1")),
        "SWAP_CARD" to GameAction.SwapCard(SwapCardPayload("p1", 0, Rank.KING)),
        "DISCARD_CARD" to GameAction.DiscardCard(PlayerIdPayload("p1")),
        "USE_CARD_ACTION" to GameAction.UseCardAction(PlayerIdPayload("p1")),
        "SELECT_ACTION_TARGET" to GameAction.SelectActionTarget(SelectActionTargetPayload.Positional("p1", "p2", 0)),
        "SELECT_ACTION_TARGET.ace" to GameAction.SelectActionTarget(SelectActionTargetPayload.Ace("p1", "p2")),
        "CONFIRM_PEEK" to GameAction.ConfirmPeek(PlayerIdPayload("p1")),
        "SKIP_PEEK" to GameAction.SkipPeek(PlayerIdPayload("p1")),
        "EXECUTE_JACK_SWAP" to GameAction.ExecuteJackSwap(PlayerIdPayload("p1")),
        "SKIP_JACK_SWAP" to GameAction.SkipJackSwap(PlayerIdPayload("p1")),
        "EXECUTE_QUEEN_SWAP" to GameAction.ExecuteQueenSwap(PlayerIdPayload("p1")),
        "SKIP_QUEEN_SWAP" to GameAction.SkipQueenSwap(PlayerIdPayload("p1")),
        "DECLARE_KING_ACTION" to GameAction.DeclareKingAction(DeclareKingActionPayload("p1", Rank.KING)),
        "PARTICIPATE_IN_TOSS_IN" to GameAction.ParticipateInTossIn(ParticipateInTossInPayload("p1", listOf(0))),
        "PLAYER_TOSS_IN_FINISHED" to GameAction.PlayerTossInFinished(PlayerIdPayload("p1")),
        "FINISH_TOSS_IN_PERIOD" to GameAction.FinishTossInPeriod(InitiatorIdPayload("p1")),
        "CALL_VINTO" to GameAction.CallVinto(PlayerIdPayload("p1")),
        "SET_COALITION_LEADER" to GameAction.SetCoalitionLeader(LeaderIdPayload("p1")),
        "DECLARE_CARDS" to GameAction.DeclareCards(DeclareCardsPayload("p1", "p1", listOf(claim))),
        "END_ROUND" to GameAction.EndRound(PlayerIdPayload("p1")),
        "PROCESS_AI_TURN" to GameAction.ProcessAiTurn(PlayerIdPayload("p1")),
        "PEEK_SETUP_CARD" to GameAction.PeekSetupCard(PositionPayload("p1", 0)),
        "FINISH_SETUP" to GameAction.FinishSetup(PlayerIdPayload("p1")),
        "UPDATE_DIFFICULTY" to GameAction.UpdateDifficulty(DifficultyPayload(Difficulty.EASY)),
        "SET_NEXT_DRAW_CARD" to GameAction.SetNextDrawCard(RankPayload(Rank.ACE)),
        "SWAP_HAND_WITH_DECK" to GameAction.SwapHandWithDeck(SwapHandWithDeckPayload("p1", 0, Rank.ACE)),
        "EMPTY" to GameAction.Empty(JsonObject(emptyMap())),
    )

    private val clientMessages: Map<String, ClientMessage> = mapOf(
        "join" to ClientMessage.Join(token = "tok", nickname = "Amber Otter", protocol = PROTOCOL_VERSION),
        "action" to ClientMessage.Action(token = "tok", action = actions.getValue("DRAW_CARD")),
        "resync" to ClientMessage.Resync(sinceIndex = 3),
        "add-bot" to ClientMessage.AddBot(token = "tok"),
        "remove-bot" to ClientMessage.RemoveBot(token = "tok", seat = 2),
        "next-round" to ClientMessage.NextRound(token = "tok"),
        "leave" to ClientMessage.Leave(token = "tok"),
        "more-time" to ClientMessage.MoreTime(token = "tok"),
        "done-conferring" to ClientMessage.DoneConferring(token = "tok"),
        "say" to ClientMessage.Say(talk),
        "edit-plan" to ClientMessage.EditPlan(token = "tok", edit = PlanEdit.SetLane("p2", Step.TakeTheDiscard)),
        "agree-plan" to ClientMessage.AgreePlan(token = "tok", agree = true),
    )

    private val entry = EventEntry(
        index = 0,
        seat = 0,
        playerId = "human-1",
        action = actions.getValue("DRAW_CARD"),
        view = view,
    )
    private val serverMessages: Map<String, ServerMessage> = mapOf(
        "joined" to ServerMessage.Joined(
            seat = 0,
            token = "tok",
            seats = listOf(
                PublicSeat(index = 0, playerId = "human-1", profile = PlayerProfile("Amber Otter"), occupied = true),
            ),
            nextIndex = 0,
            lobby = lobby,
            view = view,
            plan = plan,
            protocol = PROTOCOL_VERSION,
        ),
        "notice" to ServerMessage.Notice(UPDATE_AVAILABLE_CODE, "a newer build is waiting", NoticeSeverity.WARNING),
        "events" to ServerMessage.Events(
            events = listOf(entry),
            nextIndex = 1,
            view = view,
            said = listOf(talk),
            plan = plan,
        ),
        "sync" to ServerMessage.Sync(events = listOf(entry), nextIndex = 1, view = view, plan = plan),
        "said" to ServerMessage.Said(talk),
        "lobby" to ServerMessage.Lobby(lobby),
        "started" to ServerMessage.Started(view = view, nextIndex = 0),
        "between-rounds" to ServerMessage.BetweenRounds(
            view = view,
            standings = listOf(
                RoundResult(
                    roundNumber = 1,
                    vintoCallerId = "p1",
                    scores = mapOf("p1" to 5),
                    points = mapOf("p1" to 3),
                ),
            ),
            nextIndex = 9,
        ),
        "ended" to ServerMessage.Ended("everybody left"),
        "closed" to ServerMessage.Closed("room closed"),
        "error" to ServerMessage.Error(message = "too old", code = UPDATE_NEEDED_CODE),
    )

    private fun samples(): Map<String, String> = buildMap {
        clientMessages.forEach { (name, m) ->
            put(
                "client.$name",
                ProtocolJson.encodeToString(ClientMessage.serializer(), m),
            )
        }
        serverMessages.forEach { (name, m) ->
            put(
                "server.$name",
                ProtocolJson.encodeToString(ServerMessage.serializer(), m),
            )
        }
        actions.forEach { (name, a) ->
            put("action.$name", ProtocolJson.encodeToString(ClientMessage.serializer(), ClientMessage.Action("tok", a)))
        }
    }

    @Test
    fun everyMessageAndActionHasASampleForThisVersion() {
        val all = samples()
        assertEquals(clientMessages.keys, WireFreezeTest().clientNamesForTest(), "a client message has no sample")
        assertEquals(serverMessages.keys, WireFreezeTest().serverNamesForTest(), "a server message has no sample")
        // Every action class has a sample, and the samples' tags are exactly the frozen list —
        // which is how the tag list is held without a second copy of it in the shapes module.
        assertEquals(
            GameAction::class.sealedSubclasses.toSet(),
            actions.values.map { it::class }.toSet(),
            "an action class has no sample",
        )
        assertEquals(
            WireFreezeTest().actionTagsForTest(),
            actions.values.map { it.type }.toSet(),
            "the actions on the wire are not the ones written down",
        )
        if (writing) {
            current.mkdirs()
            all.forEach { (name, text) -> File(current, "$name.json").writeText(text + "\n") }
            return
        }
        assertTrue(
            current.isDirectory,
            "no samples for protocol $PROTOCOL_VERSION: run -Pwire=write once, after the bump",
        )
        val moved = all.mapNotNull { (name, text) ->
            val file = File(current, "$name.json")
            when {
                !file.exists() -> "$name: no sample (new in this version? run -Pwire=write)"
                file.readText().trimEnd() != text -> "$name: the wire moved without a protocol bump"
                else -> null
            }
        }
        assertTrue(moved.isEmpty(), moved.joinToString("\n"))
        val stale = current.listFiles().orEmpty().map { it.nameWithoutExtension }.filter { it !in all.keys }
        assertTrue(stale.isEmpty(), "samples for nothing on the wire: $stale")
    }

    @Test
    fun everyOlderVersionsSamplesStillDecode() {
        // The room reading a build older than itself. Absent directories are versions from
        // before the samples existed, and say so rather than passing silently.
        val older = fixtures.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("v") }
            .map { it.name.drop(1).toInt() to it }
            .filter { (version, _) -> version < PROTOCOL_VERSION }
        for ((version, dir) in older) {
            for (file in dir.listFiles().orEmpty().filter { it.extension == "json" }) {
                val text = file.readText()
                try {
                    if (file.name.startsWith("server.")) {
                        ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
                    } else {
                        ProtocolJson.decodeFromString(ClientMessage.serializer(), text)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") failed: Exception) {
                    fail("protocol $version's " + file.name + " no longer decodes: " + failed.message)
                }
            }
        }
    }
}
