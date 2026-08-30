package game.vinto.client

import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.createDeck
import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.hashGameState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The lesson's round, played.
 *
 * A tutorial is the most fragile thing in a game: it depends on cards turning up, on bots
 * behaving, and on a script that nobody re-reads once it works. These cases are its drift
 * alarm — a bot change, an engine fix or a mis-edited deck breaks one of them long before it
 * breaks somebody's first five minutes with the game.
 */
class TeachingRoundTest {

    @Test
    fun theTeachingDeckIsARealDeck() {
        val deck = TeachingDeal.deck()
        val real = createDeck()

        assertEquals(real.size, deck.size, "a stacked deck is still 54 cards")
        assertEquals(
            real.map { it.id }.sorted(),
            deck.map { it.id }.sorted(),
            "a stacked deck that is not a permutation of the real one is a silent rules change",
        )
        assertEquals(
            real.groupingBy { it.rank }.eachCount(),
            deck.groupingBy { it.rank }.eachCount(),
            "four of every rank, two Jokers",
        )
    }

    /** The lesson leans on these: something to peek at, and a plain card to draw first. */
    @Test
    fun theDealPutsTheTeachingCardsWhereTheLessonLooksForThem() = runTest {
        val session = teachingSession()
        val you = session.state.players.first { it.isHuman }

        assertTrue(Rank.SEVEN in you.cards.map { it.rank }, "a peek-own card to find and use")
        assertTrue(Rank.JOKER in you.cards.map { it.rank }, "the card worth minus one, to meet")
        assertEquals(Rank.FOUR, session.state.drawPile.peekTop()?.rank, "a plain card to draw")
    }

    /**
     * The ending is the half of the game a free-play tutorial never reaches, so it is the half
     * most worth a test: somebody calls Vinto, the coalition forms, and the round scores.
     */
    @Test
    fun aBotCallsVintoAndTheRoundIsScored() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        SimplePlayer(session).play()

        val caller = session.state.vintoCallerId
        assertNotNull(caller, "the lesson's director must have somebody call Vinto")
        assertTrue(caller != me, "and it must be one of the bots, not the player being taught")
        assertEquals(GamePhase.SCORING, session.state.phase, "the round has to finish")
    }

    /**
     * The coalition is the rule that is hardest to explain and easiest to get wrong, so the
     * lesson only claims it if the state actually shows it.
     */
    @Test
    fun theCoalitionFormsAgainstTheCaller() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        SimplePlayer(session).play()

        val caller = assertNotNull(session.state.vintoCallerId)
        val view = projectView(session.state, me)
        assertEquals(caller, view.vintoCallerId, "the player can see who called")

        val everyoneElse = session.state.players.filter { it.id != caller }
        assertTrue(
            everyoneElse.all { it.coalitionWith.isNotEmpty() },
            "everybody who did not call plays as one",
        )
    }

    /**
     * The deal exists to put two particular cards in the player's hands: a Queen, whose look
     * is the best in the game, and a King, which borrows another rank's action. They are the
     * two set-pieces the lesson is built around, and a deck edit that loses them would leave
     * the coach with nothing to point at.
     *
     * Taking from the discard is *not* asserted here, deliberately. Whether an unused action
     * card is still sitting on the pile when the player's turn comes round depends on what the
     * bots did with it — and a bot taking it first is correct play, not a fault. The director
     * makes the seat before the player draw rather than take, which helps; the rule itself is
     * taught in words either way, and the pointed version happens when the round allows it.
     */
    @Test
    fun theDealPutsAQueenAndAKingInThePlayersHands() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        val player = SimplePlayer(session)
        player.play()

        assertTrue(
            player.asked.any { it == Ask.YouDrew(Rank.QUEEN) },
            "the Queen has to reach the player: ${player.asked}",
        )
        assertTrue(
            player.asked.any { it == Ask.YouDrew(Rank.KING) },
            "and so does the King: ${player.asked}",
        )
    }

    /**
     * A bot throws a card in where the player can see it.
     *
     * The toss-in window is the one moment that belongs to the whole table at once, and a
     * player whose window only ever contains themselves learns it as a prompt to dismiss. The
     * director makes it happen once; this is what stops a deck edit or a bot change from
     * quietly removing it.
     */
    @Test
    fun aBotThrowsACardInWhereThePlayerCanSeeIt() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        SimplePlayer(session).play()

        val tossed = session.report(at = "2026-08-20T00:00:00Z", label = "the lesson")
            .actions
            .map { it.action }
            .filterIsInstance<GameAction.ParticipateInTossIn>()

        assertTrue(
            tossed.any { it.payload.playerId != me },
            "the lesson claims anybody may throw in a match, so somebody else has to",
        )
    }

    /**
     * The taught round is an ordinary game in the one sense that matters most: it records and
     * replays like any other, in either engine.
     *
     * This is what makes the stacked deck safe. A deal from a written-down deck could have been
     * a special case that only the tutorial understands; instead the recording carries the
     * whole initial state, so the replay harness — and the TypeScript one — can play it back
     * without knowing it was arranged.
     */
    @Test
    fun theTaughtRoundReplaysLikeAnyOther() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        SimplePlayer(session).play()

        val recording = session.report(at = "2026-08-20T00:00:00Z", label = "the lesson")
        assertTrue(recording.actions.isNotEmpty(), "a round was played")

        var state = recording.initialState
        recording.actions.forEachIndexed { index, recorded ->
            state = when (val result = GameEngine.reduce(state, recorded.action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure ->
                    fail("action $index (${recorded.action}) was refused: ${result.reason}")
            }
            assertEquals(
                recorded.stateHash,
                hashGameState(state),
                "the replay diverged at action $index — the taught round is not reproducible",
            )
        }

        assertEquals(
            recording.finalStateHash,
            hashGameState(state),
            "and it ends where it said it ended",
        )
    }
}

/**
 * The simplest player there is, playing by what the table offers.
 *
 * Driven by [Table] rather than by the raw state, which is both more faithful and less
 * fragile: `activeTossIn` outlives its window in `GameState` — it is cleared when the next
 * card goes down, not when everybody has answered — so a test that reads it directly spends
 * the rest of the round being told it has already confirmed. The table model knows that; a
 * player only ever sees what it decided; so does this.
 *
 * It takes whatever move the table hands it, preferring the ones that need no follow-up tap.
 * That makes it a bad player — it keeps nothing — which is exactly what these cases want: the
 * pile stays stocked and every window stays open to somebody.
 */
private class SimplePlayer(private val session: LocalGameSession) {

    /** Every label the table offered along the way — what the lesson has to teach from. */
    val offered = mutableSetOf<Label>()

    /**
     * And everything it said. The buttons carry the web app's own short words now — "Use
     * Action" rather than the card's whole effect — so what proves a particular card reached
     * the player is the prompt above them, not the label on them.
     */
    /** Every prompt the table put to the player. Typed, so an assertion says what it means. */
    val asked = mutableSetOf<Ask>()

    /** The smaller line under it, still a sentence — see §6h, `detail` is a later slice. */
    val said = mutableSetOf<String>()

    suspend fun play(steps: Int = STEPS) {
        repeat(steps) {
            if (session.isOver) return

            val table = tableFor(projectView(session.state, session.playerId))
            offered += table.choices.map { it.label }
            asked += table.prompt
            said += listOfNotNull(table.detail)

            // Sends only: an Ask is a question the screen asks itself, and answering one needs
            // a tap on a card rather than a move.
            val move = table.choices
                .mapNotNull { it.move as? Move.Send }
                .firstOrNull { it.action !is GameAction.CallVinto }
                ?: return

            session.dispatch(move.action)
        }
    }

    private companion object {
        const val STEPS = 80
    }
}
