package game.vinto.engine

import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
import game.vinto.shapes.Rank
import game.vinto.shapes.VintoJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Hidden cards stay hidden", asserted the way the online-multiplayer spec asks for: over
 * recorded states rather than invented ones.
 *
 * The test is deliberately blunt. Serialise each seat's view, then search the resulting JSON
 * for the **id** of every card that seat is not entitled to see. Card ids are `"7_0"`,
 * `"K_2"`, `"Joker1"` — they contain the rank — so an id appearing anywhere in a view is a
 * leak whether or not it appears in a field called `rank`. Checking the serialised bytes
 * rather than the object graph is what makes this catch a leak through a field nobody thought
 * about, which is the only kind that ever ships.
 *
 * This runs across every state of every recording, for all four seats: ~14,000 states × 4.
 */
class ViewRedactionTest {

    /** How many recordings the slower cases walk. Ten is a few thousand states. */
    private val sample = 10

    private val recordings: List<Pair<String, GameRecording>> =
        File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    private fun statesOf(recording: GameRecording): Sequence<GameState> = sequence {
        var state = recording.initialState
        yield(state)
        for (entry in recording.actions) {
            state = (GameEngine.reduce(state, entry.action) as ReduceResult.Success).state
            yield(state)
        }
    }

    @Test
    fun noViewEverContainsACardTheSeatMayNotSee() {
        val leaks = mutableListOf<String>()
        var checked = 0

        for ((name, recording) in recordings) {
            for ((index, state) in statesOf(recording).withIndex()) {
                for (seat in state.players) {
                    checked++
                    leaksIn(state, seat.id, "$name#$index", leaks)
                }
            }
        }

        assertTrue(checked > 50_000, "expected the whole corpus projected, checked $checked")
        assertEquals(emptyList(), leaks, "a player view exposed a card the seat may not see")
    }

    /**
     * Serialises one seat's view and looks for the id of anything it may not see. Substring
     * search on purpose: it catches an id smuggled through any field, including one added
     * later by someone not thinking about redaction.
     */
    private fun leaksIn(state: GameState, seatId: String, where: String, leaks: MutableList<String>) {
        val serialised =
            VintoJson.encodeToString(PlayerView.serializer(), projectView(state, seatId))

        for (card in hiddenFrom(state, seatId)) {
            if (serialised.contains("\"${card.id}\"") && leaks.size < 10) {
                leaks += "$where seat $seatId: leaked ${card.id} (${card.rank})"
            }
        }
    }

    @Test
    fun theDrawPileIsNeverSent() {
        for ((name, recording) in recordings.take(5)) {
            for (state in statesOf(recording)) {
                val seat = state.players.first().id
                val serialised =
                    VintoJson.encodeToString(PlayerView.serializer(), projectView(state, seat))

                // Knowing the order of the draw pile would decide the game outright.
                for (card in state.drawPile.toList()) {
                    assertTrue(
                        !serialised.contains("\"${card.id}\""),
                        "$name: draw pile card ${card.id} reached a view",
                    )
                }
                assertEquals(state.drawPile.size, projectView(state, seat).drawPileSize)
            }
        }
    }

    /**
     * The counterweight to the leak test: a view that showed nothing would pass that one
     * perfectly, so this asserts the things a seat is *supposed* to see.
     *
     * During setup, the two cards the rules tell you to look at.
     */
    @Test
    fun aSeatSeesItsOwnTwoCardsWhileTheRulesSayToLookAtThem() {
        var confirmed = 0
        for ((_, recording) in recordings.take(sample)) {
            for (state in statesOf(recording).filter { it.phase == GamePhase.SETUP }) {
                for (seat in state.players) {
                    val self = projectView(state, seat.id).players.first { it.id == seat.id }
                    for (position in seat.knownCardPositions) {
                        assertTrue(
                            self.cards[position] is CardView.Visible,
                            "a seat could not see its own peeked card at $position during setup",
                        )
                        confirmed++
                    }
                }
            }
        }
        assertTrue(confirmed > 0, "expected setup peeks to check, saw $confirmed")
    }

    /**
     * And once the round is running, it sees them no longer.
     *
     * The engine remembers what every seat has learned, because the bots and the scoring need
     * it. Sending a seat its own remembered cards face-up put that memory in the client and
     * left the *screen* responsible for not drawing it — which is no protection at all from a
     * client we did not write, and remembering your own hand is most of what this game asks
     * of you. What the view carries is `knownCardPositions`: which cards you have seen, which
     * is public — everybody watches you peek — and not what they were.
     */
    @Test
    fun aSeatIsNotSentItsOwnRememberedCardsOnceTheRoundStarts() {
        var checked = 0
        for ((name, recording) in recordings.take(sample)) {
            for (state in statesOf(recording).filter { it.phase == GamePhase.PLAYING }) {
                for (seat in state.players) {
                    val view = projectView(state, seat.id)
                    val self = view.players.first { it.id == seat.id }
                    val shownNow = view.pendingAction
                        ?.takeIf { it.playerId == seat.id }
                        ?.targets
                        ?.filter { it.playerId == seat.id }
                        ?.map { it.position }
                        .orEmpty()

                    for (position in seat.knownCardPositions - shownNow.toSet()) {
                        assertTrue(
                            self.cards[position] !is CardView.Visible,
                            "$name sent ${seat.id} its own card at $position, which it is only " +
                                "supposed to remember",
                        )
                        checked++
                    }
                }
            }
        }
        assertTrue(checked > 1_000, "expected many remembered cards to check, saw $checked")
    }

    @Test
    fun theCoalitionLeaderIsSentNoHandsAtAll() {
        // Being nominated leader changes nothing about what a seat may see: coalition
        // knowledge travels as declared claims (`DECLARE_CARDS`), never as real cards. This
        // used to assert the opposite — the leader seeing every member's hand — a rule
        // replaced by declarations.
        val positions = recordings.asSequence()
            .flatMap { (_, recording) -> statesOf(recording) }
            .filter {
                it.phase == GamePhase.FINAL &&
                    it.vintoCallerId != null &&
                    it.coalitionLeaderId != null &&
                    // A running action may legitimately show the actor a card; what is being
                    // asserted here is the *standing* view of a leader between actions.
                    it.pendingAction == null
            }
            .take(20)
            .toList()

        assertTrue(positions.isNotEmpty(), "corpus contains no coalition final round")

        for (state in positions) {
            val leader = state.coalitionLeaderId!!
            val view = projectView(state, leader)

            for (seat in view.players) {
                val hidden = seat.cards.count { it is CardView.Hidden }
                assertEquals(
                    seat.cards.size,
                    hidden,
                    "the leader was sent ${seat.id}'s real cards — claims travel, cards do not",
                )
            }
        }
    }

    @Test
    fun declaredRanksAreSentToEverySeatIncludingTheCaller() {
        // A claim is table talk: every seat's view carries it, the caller's included, and
        // the card underneath it stays hidden.
        val base = recordings.asSequence()
            .flatMap { (_, recording) -> statesOf(recording) }
            .first {
                it.phase == GamePhase.FINAL &&
                    it.vintoCallerId != null &&
                    it.players.any { seat -> !seat.isVintoCaller && seat.cards.isNotEmpty() }
            }
        val member = base.players.first { !it.isVintoCaller && it.cards.isNotEmpty() }
        val declared = base.copy(
            players = base.players.map { seat ->
                if (seat.id == member.id) seat.copy(declaredCards = mapOf(0 to Rank.QUEEN))
                else seat
            },
        )

        for (viewer in declared.players) {
            val view = projectView(declared, viewer.id)
            val seat = view.players.first { it.id == member.id }
            assertEquals(mapOf(0 to Rank.QUEEN), seat.declaredCards, "claim missing for ${viewer.id}")
            assertTrue(seat.cards[0] is CardView.Hidden, "a claim must not turn the card over")
        }
    }

    @Test
    fun scoresAppearOnlyWhenTheGameIsOver() {
        for ((_, recording) in recordings.take(10)) {
            for (state in statesOf(recording)) {
                val view = projectView(state, state.players.first().id)
                if (state.phase == GamePhase.SCORING) {
                    assertEquals(state.players.size, view.scores?.size)
                } else {
                    assertEquals(null, view.scores, "scores were exposed mid-game")
                }
            }
        }
    }
}
