package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.engine.CardView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * One human, three bots, one device.
 *
 * The session is driven the way a screen would drive it — construct, dispatch, read the view —
 * and the person's own moves come from a second [BotRunner] acting as a stand-in for whoever
 * is holding the phone. That stand-in needs the full state to choose a move, which the view
 * deliberately will not give it; that is why it reads `session.state` while every assertion
 * reads the view.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `runCurrent`, to drain the event collector deterministically.
class LocalGameSessionTest {

    /**
     * Everything the session announces, in order.
     *
     * Collected rather than sampled: one dispatch can produce several events, and reading a
     * latest value is how the pair a finished round produces — the bots moved, then it
     * ended — turns into whichever happened to land last.
     */
    private fun TestScope.eventsOf(session: LocalGameSession): List<SessionEvent> {
        val seen = mutableListOf<SessionEvent>()
        backgroundScope.launch { session.events.collect { seen.add(it) } }
        // Run the collector up to its `collect` before returning. A shared flow buffers for
        // the subscribers it has, so anything emitted before this line would be gone — which
        // reads in a test as "the session announced nothing" rather than as a race.
        runCurrent()
        return seen
    }

    /**
     * Plays a whole round, the person's own moves included, and returns how many were made.
     *
     * The person is stood in for by a [BotRunner], which will not choose a move for a seat
     * marked human — so the state it is *asked* about has that seat marked as a bot, while
     * every move it produces is dispatched into the real session through the ordinary public
     * `dispatch`. A robot holding the phone: it makes the session's own refusals meaningful,
     * because the session is not in on the arrangement.
     */
    private suspend fun playOut(session: LocalGameSession, seed: Long = 1L): Int {
        val person = BotRunner(THINKING_TIME, Random(seed))
        var moves = 0

        while (!session.isOver && moves < MOVE_LIMIT) {
            val action = person.nextAction(session.state.asIfEveryoneWereABot()) ?: break
            if (session.dispatch(action) != null) break
            moves++
        }
        return moves
    }

    /** The same position with nobody marked human, so the runner will speak for every seat. */
    private fun GameState.asIfEveryoneWereABot(): GameState =
        copy(players = players.map { it.copy(isHuman = false, isBot = true) })

    @Test
    fun aSoloGameRunsFromTheDealToScoring() = runTest(timeout = A_WHOLE_ROUND) {
        val session = LocalGameSession(seed = 20260819L, difficulty = THINKING_TIME)
        val events = eventsOf(session)

        assertEquals(GamePhase.SETUP, session.view.value.phase, "a fresh session is dealt, not started")
        assertEquals(FOUR_SEATS, session.view.value.players.size)
        assertEquals(1, session.view.value.players.count { it.isHuman })

        playOut(session)
        runCurrent()

        assertTrue(session.isOver, "the round finished")
        assertEquals(GamePhase.SCORING, session.view.value.phase)

        val ended = events.filterIsInstance<SessionEvent.RoundEnded>()
        assertEquals(1, ended.size, "the round announced itself, once: $events")
        assertEquals(FOUR_SEATS, ended.single().scores.size, "every seat scored")
        assertTrue(
            events.any { it is SessionEvent.BotsPlayed },
            "and the bots' moves were announced too, rather than being erased by the ending",
        )
    }

    /**
     * The session waits for the person and never plays their seat for them.
     *
     * Asserted at the deal, where the engine leaves the human with no peeked cards and every
     * bot already holding two: the only move available belongs to the person, so a session
     * that moved at all would be moving on their behalf.
     */
    @Test
    fun theSessionNeverPlaysThePersonsSeat() = runTest {
        val session = LocalGameSession(seed = 7L)
        val events = eventsOf(session)
        val dealt = session.view.value

        // Nothing dispatched — nothing may happen, however long we wait.
        assertEquals(dealt, session.view.value)
        assertTrue(events.isEmpty(), "and nothing was announced: $events")

        val human = dealt.players.first { it.isHuman }
        assertTrue(human.knownCardPositions.isEmpty(), "the person has not peeked yet")

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        assertEquals(listOf(0), session.view.value.players.first { it.isHuman }.knownCardPositions)

        // Still the person's move — their second setup peek — so the bots have stayed put.
        assertTrue(
            session.view.value.phase == GamePhase.SETUP,
            "the bots did not finish setup on the person's behalf",
        )
    }

    /** The person's hand is their own; nobody else's is legible. */
    @Test
    fun theViewShowsThePersonOnlyWhatTheyMaySee() = runTest {
        val session = LocalGameSession(seed = 31L)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))

        val view = session.view.value
        val me = view.players.first { it.id == session.playerId }
        assertTrue(me.cards[0] is CardView.Visible, "the card just peeked is visible")

        val opponents = view.players.filter { it.id != session.playerId }
        assertTrue(
            opponents.all { seat -> seat.cards.all { it is CardView.Hidden } },
            "no opponent card is legible",
        )
    }

    /** Same seed, same moves, same game — the property the whole engine is built on. */
    @Test
    fun theSameSeedPlaysTheSameGame() = runTest(timeout = A_WHOLE_ROUND) {
        val first = LocalGameSession(seed = 4242L, difficulty = THINKING_TIME)
        val second = LocalGameSession(seed = 4242L, difficulty = THINKING_TIME)
        val firstEvents = eventsOf(first)
        val secondEvents = eventsOf(second)

        playOut(first)
        playOut(second)
        runCurrent()

        assertEquals(first.view.value, second.view.value)
        assertEquals(firstEvents, secondEvents, "the same game announced the same things")
    }

    /** A different seed is a different game, or the seed is not doing anything. */
    @Test
    fun aDifferentSeedDealsADifferentGame() = runTest {
        val a = LocalGameSession(seed = 1L).view.value
        val b = LocalGameSession(seed = 2L).view.value
        assertFalse(a.players.first().cards == b.players.first().cards && a.gameId == b.gameId)
    }

    /**
     * A refused move is answered, not thrown, and changes nothing.
     *
     * A UI can offer a move the rules disallow — that is what a tappable card is — so refusal
     * has to be an ordinary outcome with a reason attached.
     */
    @Test
    fun anIllegalMoveIsRefusedWithAReasonAndLeavesTheGameAlone() = runTest {
        val session = LocalGameSession(seed = 99L)
        val events = eventsOf(session)
        val before = session.view.value

        // Drawing during setup: the phase is wrong, and it is not this seat's to decide yet.
        val reason = session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        runCurrent()
        assertNotNull(reason, "the refusal came with a reason")
        assertEquals(before, session.view.value, "and the game did not move")
        assertEquals(listOf(SessionEvent.Refused(reason)), events)
    }

    /** Nobody may move for another seat, locally no less than online. */
    @Test
    fun thePersonCannotMoveForABot() = runTest {
        val session = LocalGameSession(seed = 5L)
        val someBot = session.view.value.players.first { it.isBot }.id

        val reason = session.dispatch(GameAction.PeekSetupCard(PositionPayload(someBot, 2)))
        assertNotNull(reason, "acting for a bot is refused: $reason")
    }

    private companion object {
        const val FOUR_SEATS = 4

        /**
         * The cheapest brain, for the tests that play a round out.
         *
         * What is under test is the session — that a round runs from the deal to a score, and
         * that the same seed replays — none of which cares how hard the bots thought. Easy
         * searches 500 nodes to moderate's 2,000, the difference between a round finishing in
         * seconds and one timing out on an iOS simulator. Bot strength is measured in
         * `shared/bot`, where it belongs.
         */
        val THINKING_TIME = Difficulty.EASY

        /** Far past any real round; a loop this long has stopped being a game. */
        const val MOVE_LIMIT = 400

        /**
         * `runTest` gives a test one minute. A round is a hundred-odd MCTS searches and the
         * slower targets take up to 1.6 s each, so a minute is a wall-clock failure on
         * correct code — see `SLOW_TEST_TIMEOUT` in this module's build script.
         */
        val A_WHOLE_ROUND = 10.minutes
    }
}
