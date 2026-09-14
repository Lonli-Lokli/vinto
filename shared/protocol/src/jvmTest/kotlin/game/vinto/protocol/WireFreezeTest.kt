package game.vinto.protocol

import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Step
import kotlinx.serialization.SerialName
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wire, frozen per protocol version.
 *
 * Adding a message type, or a game action that rides inside an events entry, is the one change
 * an older build cannot survive: it does not skip what it does not know, it drops the whole
 * message. So each version's vocabulary is written down here, and a build that grows the
 * vocabulary without bumping [PROTOCOL_VERSION] — and without writing the new vocabulary down
 * under the new number — fails here rather than freezing somebody's table in the final round.
 *
 * Fields are not listed: a new optional field is additive and `ignoreUnknownKeys` covers it.
 * Version 1 is the wire as it shipped in the stores, read off master when the number was born.
 */
class WireFreezeTest {
    private val clientMessages = mapOf(
        1 to setOf("join", "action", "resync", "add-bot", "remove-bot", "next-round", "more-time"),
        2 to
            setOf("done-conferring", "say", "edit-plan", "agree-plan", "join", "action", "resync", "add-bot", "remove-bot", "next-round", "more-time"),
        // 3 adds `leave`: the exit a dropped connection is not. Additive, so the floor stays 2.
        3 to
            setOf("done-conferring", "say", "edit-plan", "agree-plan", "join", "action", "resync", "add-bot", "remove-bot", "next-round", "more-time", "leave"),
        // 4 changed no message: what grew is the plan's vocabulary inside `edit-plan`, `joined`,
        // `events` and `sync` — see [planShapes]. The floor rose with it.
        4 to
            setOf("done-conferring", "say", "edit-plan", "agree-plan", "join", "action", "resync", "add-bot", "remove-bot", "next-round", "more-time", "leave"),
    )
    private val serverMessages = mapOf(
        1 to setOf("joined", "events", "sync", "lobby", "started", "between-rounds", "ended", "closed", "error"),
        2 to
            setOf("joined", "notice", "events", "sync", "said", "lobby", "started", "between-rounds", "ended", "closed", "error"),
        // Nothing new comes back for a leave: the lobby the room already broadcasts says the
        // seat is free, which every client is listening for anyway.
        3 to
            setOf("joined", "notice", "events", "sync", "said", "lobby", "started", "between-rounds", "ended", "closed", "error"),
        4 to
            setOf("joined", "notice", "events", "sync", "said", "lobby", "started", "between-rounds", "ended", "closed", "error"),
    )

    /**
     * The plan's vocabulary: every `Step` and every `PlanEdit` shape, per version.
     *
     * A shape is a polymorphic tag inside a message, and an older build that meets one it does
     * not know drops the whole message — so growing this list is a bump *and* a floor, which
     * is what version 4 was. Version 3 shipped the four steps and four edits of the first plan;
     * everything the plan learned to say about the rest of the rules went out under 4.
     */
    private val planShapes = mapOf(
        2 to setOf("swap", "declare", "take-discard", "put-down", "set-lane", "clear-lane", "add-shed", "remove-shed"),
        3 to setOf("swap", "declare", "take-discard", "put-down", "set-lane", "clear-lane", "add-shed", "remove-shed"),
        4 to
            setOf("swap", "declare", "take-discard", "put-down", "bin", "use-it", "peek", "force-draw", "set-lane", "open-lane", "clear-lane", "add-shed", "remove-shed", "set-toss-ins"),
    )
    private val actions = mutableMapOf(
        1 to
            setOf("DRAW_CARD", "PLAY_DISCARD", "SWAP_CARD", "DISCARD_CARD", "USE_CARD_ACTION", "SELECT_ACTION_TARGET", "CONFIRM_PEEK", "SKIP_PEEK", "EXECUTE_JACK_SWAP", "SKIP_JACK_SWAP", "EXECUTE_QUEEN_SWAP", "SKIP_QUEEN_SWAP", "DECLARE_KING_ACTION", "PARTICIPATE_IN_TOSS_IN", "PLAYER_TOSS_IN_FINISHED", "FINISH_TOSS_IN_PERIOD", "CALL_VINTO", "SET_COALITION_LEADER", "DECLARE_CARDS", "END_ROUND", "PROCESS_AI_TURN", "PEEK_SETUP_CARD", "FINISH_SETUP", "UPDATE_DIFFICULTY", "SET_NEXT_DRAW_CARD", "SWAP_HAND_WITH_DECK", "EMPTY"),
        2 to
            setOf("DRAW_CARD", "PLAY_DISCARD", "SWAP_CARD", "DISCARD_CARD", "USE_CARD_ACTION", "SELECT_ACTION_TARGET", "CONFIRM_PEEK", "SKIP_PEEK", "EXECUTE_JACK_SWAP", "SKIP_JACK_SWAP", "EXECUTE_QUEEN_SWAP", "SKIP_QUEEN_SWAP", "DECLARE_KING_ACTION", "PARTICIPATE_IN_TOSS_IN", "PLAYER_TOSS_IN_FINISHED", "FINISH_TOSS_IN_PERIOD", "CALL_VINTO", "SET_COALITION_LEADER", "DECLARE_CARDS", "END_ROUND", "PROCESS_AI_TURN", "PEEK_SETUP_CARD", "FINISH_SETUP", "UPDATE_DIFFICULTY", "SET_NEXT_DRAW_CARD", "SWAP_HAND_WITH_DECK", "EMPTY"),
    )

    init {
        // Protocol 3 changed only the client's vocabulary. Inheriting the actions rather than
        // restating them keeps the two lists from drifting apart on a version that did not
        // touch the engine.
        actions += 3 to actions.getValue(2)
        // And 4 touched no action either: the plan is talk, not a move.
        actions += 4 to actions.getValue(3)
    }

    internal fun clientNamesForTest(): Set<String> = clientMessages.getValue(PROTOCOL_VERSION)
    internal fun serverNamesForTest(): Set<String> = serverMessages.getValue(PROTOCOL_VERSION)
    internal fun actionTagsForTest(): Set<String> = actions.getValue(PROTOCOL_VERSION)

    private fun serialNames(subclasses: Collection<KClass<*>>): Set<String> =
        subclasses.map { klass -> klass.annotations.filterIsInstance<SerialName>().single().value }.toSet()

    @Test
    fun theCurrentVersionIsTheLastOneWrittenDown() {
        assertEquals(clientMessages.keys.max(), PROTOCOL_VERSION, "bump the number and write the new vocabulary down")
        assertEquals(serverMessages.keys.max(), PROTOCOL_VERSION)
        assertEquals(actions.keys.max(), PROTOCOL_VERSION)
        assertTrue(MIN_PROTOCOL <= PROTOCOL_VERSION)
    }

    @Test
    fun theMessagesAreExactlyThoseWrittenDownForTheCurrentVersion() {
        assertEquals(clientMessages.getValue(PROTOCOL_VERSION), serialNames(ClientMessage::class.sealedSubclasses))
        assertEquals(serverMessages.getValue(PROTOCOL_VERSION), serialNames(ServerMessage::class.sealedSubclasses))
    }

    @Test
    fun thePlansShapesAreExactlyThoseWrittenDownForTheCurrentVersion() {
        assertEquals(
            planShapes.getValue(PROTOCOL_VERSION),
            serialNames(Step::class.sealedSubclasses) + serialNames(PlanEdit::class.sealedSubclasses),
            "a plan shape grew without being written down under a bumped version",
        )
        assertEquals(planShapes.keys.max(), PROTOCOL_VERSION)
    }

    @Test
    fun aVersionThatGrowsThePlansShapesRaisesTheFloor() {
        // An older build cannot skip a shape it does not know, so every version whose plan says
        // more than the one before it has to be the floor — or an old build freezes mid-game.
        for (older in 2 until PROTOCOL_VERSION) {
            if (planShapes.getValue(older + 1) != planShapes.getValue(older)) {
                assertTrue(
                    MIN_PROTOCOL >= older + 1,
                    "version ${older + 1} grew the plan and left the floor at $MIN_PROTOCOL",
                )
            }
        }
    }

    @Test
    fun eachVersionOnlyEverGrows() {
        for (older in 1 until PROTOCOL_VERSION) {
            assertTrue(clientMessages.getValue(older + 1).containsAll(clientMessages.getValue(older)))
            assertTrue(serverMessages.getValue(older + 1).containsAll(serverMessages.getValue(older)))
            assertTrue(actions.getValue(older + 1).containsAll(actions.getValue(older)))
            if (older >= 2) assertTrue(planShapes.getValue(older + 1).containsAll(planShapes.getValue(older)))
        }
    }
}
