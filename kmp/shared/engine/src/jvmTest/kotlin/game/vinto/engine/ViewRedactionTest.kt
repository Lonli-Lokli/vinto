package game.vinto.engine

import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
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

    private val recordings: List<Pair<String, GameRecording>> =
        File("../../../fixtures/recordings")
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

    @Test
    fun aSeatAlwaysSeesTheCardsItHasPeekedAt() {
        // The redaction must not be so eager that it hides what a player legitimately knows —
        // a view that shows nothing would pass the leak test perfectly.
        var confirmed = 0
        for ((_, recording) in recordings.take(10)) {
            for (state in statesOf(recording)) {
                for (seat in state.players) {
                    val view = projectView(state, seat.id)
                    val self = view.players.first { it.id == seat.id }
                    for (position in seat.knownCardPositions) {
                        assertTrue(
                            self.cards[position] is CardView.Visible,
                            "a seat could not see its own peeked card at $position",
                        )
                        confirmed++
                    }
                }
            }
        }
        assertTrue(confirmed > 1_000, "expected many peeked cards to check, saw $confirmed")
    }

    @Test
    fun theCoalitionLeaderSeesTheCoalitionButNotTheCaller() {
        val positions = recordings.asSequence()
            .flatMap { (_, recording) -> statesOf(recording) }
            .filter {
                it.phase == GamePhase.FINAL &&
                    it.vintoCallerId != null &&
                    it.coalitionLeaderId != null
            }
            .take(20)
            .toList()

        assertTrue(positions.isNotEmpty(), "corpus contains no coalition final round")

        for (state in positions) {
            val leader = state.coalitionLeaderId!!
            val view = projectView(state, leader)

            for (seat in view.players) {
                val hidden = seat.cards.count { it is CardView.Hidden }
                if (seat.id == state.vintoCallerId) {
                    assertTrue(hidden == seat.cards.size, "the leader could see the Vinto caller's hand")
                } else {
                    // The leader's own hand counts as a coalition member's — matching the web
                    // app, where the condition is "has a coalition list and is not the caller".
                    assertEquals(0, hidden, "the leader could not see coalition member ${seat.id}")
                }
            }
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
