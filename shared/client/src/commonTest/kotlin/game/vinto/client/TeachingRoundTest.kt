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
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.hashGameState
import kotlinx.coroutines.flow.first
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

        assertTrue(Rank.SEVEN in you.cards.map { it.rank }, "a peek-own card to throw in and use")
        assertTrue(Rank.EIGHT in you.cards.map { it.rank }, "and one to give up and name")
        assertTrue(Rank.JOKER in you.cards.map { it.rank }, "the card worth minus one, to meet")
        assertEquals(Rank.TWO, session.state.drawPile.peekTop()?.rank, "a plain card to draw")
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
     * The round, played the way the coach says to play it.
     *
     * `TeachingDeal` plans eight turns move by move, and every one of them is a claim the
     * lesson makes to the learner: name the 8 and it finds your Joker, throw in the 7 beside
     * Raph's and it finds a King, watch Mikey's 9 look at you, take Don's Queen and trade your
     * last King for his Joker, call on a hand every card of which you have seen and which
     * adds up to nothing, then watch an Ace, a King and a 9 played against you.
     * This follows the coach's pointer at every step — the buttons it names, the cards it
     * points at, the rank chip it chooses — and asserts each of those claims against the
     * engine. A deck edit, a director change or a script change that breaks the line breaks
     * this, long before it breaks somebody's first five minutes with the game.
     */
    @Test
    fun theRoundRunsAsTheCoachTellsIt() = runTest {
        val session = teachingSession()
        val me = session.playerId
        val learner = Learner(session)

        learner.follow()

        val played = session.report(at = "2026-08-20T00:00:00Z", label = "the lesson").actions.map { it.action }
        val raph = session.state.players[1].id
        val mikey = session.state.players[2].id

        // Turn 1: the 2 went in for the 8, which was named, and its look found the Joker.
        val swap = played.filterIsInstance<GameAction.SwapCard>().first { it.payload.playerId == me }
        assertEquals(1, swap.payload.position, "the 8 was in the second slot, the worse of the two peeked")
        assertEquals(Rank.EIGHT, swap.payload.declaredRank, "and it was named as it went down")
        val peeked = played.filterIsInstance<GameAction.SelectActionTarget>()
            .first { it.payload.playerId == me }
        assertEquals(me, peeked.payload.targetPlayerId, "the 8's look is at one of your own")

        // Turn 2: your 7 and Raph's went into the same window.
        val tossed = played.filterIsInstance<GameAction.ParticipateInTossIn>().map { it.payload.playerId }
        assertTrue(me in tossed, "the learner threw the 7 in: $tossed")
        assertTrue(raph in tossed, "and so did Raph, where they could watch: $tossed")

        // Turn 3: Mikey's 9 looked at one of yours.
        val looked = played.filterIsInstance<GameAction.SelectActionTarget>()
            .first { it.payload.playerId == mikey }
        assertEquals(me, looked.payload.targetPlayerId, "Mikey's 9 is aimed at the learner")

        // Turn 5: the Queen came off the pile, looked, and traded the last King for Raph's Joker.
        assertTrue(played.any { it is GameAction.PlayDiscard && it.payload.playerId == me }, "took the Queen")
        assertTrue(played.any { it is GameAction.ExecuteQueenSwap }, "and traded on what it saw")
        val call = played.indexOfFirst { it is GameAction.CallVinto }
        assertTrue(call >= 0, "the learner called Vinto")
        assertEquals(me, (played[call] as GameAction.CallVinto).payload.playerId, "themselves, not a bot")

        // On a hand nothing can get under: a 2, both Jokers and a King, every one of them seen.
        val hand = session.state.players.first { it.id == me }
        assertEquals(
            listOf(Rank.TWO, Rank.JOKER, Rank.KING, Rank.JOKER),
            hand.cards.map { it.rank },
            "the finished hand",
        )
        assertEquals(hand.cards.indices.toList(), hand.knownCardPositions.sorted(), "all of it seen")

        // The final round: the three cards the learner never held, played where they can watch.
        val after = played.drop(call)
        assertTrue(
            after.any { it is GameAction.SelectActionTarget && it.payload is SelectActionTargetPayload.Ace },
            "an Ace was played against the coalition's own",
        )
        assertTrue(after.any { it is GameAction.DeclareKingAction }, "a King named a card")
        assertTrue(
            after.filterIsInstance<GameAction.SelectActionTarget>().none { it.payload.targetPlayerId == me },
            "and none of it touched the caller's cards",
        )

        // And the call held.
        assertEquals(GamePhase.SCORING, session.state.phase)
        val view = projectView(session.state, me)
        val scores = assertNotNull(view.scores)
        assertEquals(0, scores[me], "a 2, two Jokers and a King")
        assertEquals(scores.values.min(), scores[me], "the lowest hand at the table")
    }

    /**
     * The coalition's whole final round reaches the table.
     *
     * The learner's call submits the call *and* the three bots' last turns as one batch, and
     * in the taught round those turns are long: the director has every bot play its card
     * rather than put it down, and each play opens a toss-in window that has to be closed.
     * The animation queue drops a batch costing more than its budget — the right rule for a
     * client that fell behind, and the end of the lesson if this batch ever crosses it, since
     * everything the ending teaches is in it. It does not today, which is worth knowing
     * rather than assuming: the ending went missing for a different reason
     * (`StageStepsBeforeTheCoachTest`), and this was the case that ruled the queue out.
     * `FinalRoundIsWatchedTest` is the same guard for an ordinary deal, where the budget was
     * raised from 8 to 24 after a Vinto call went unwatched.
     */
    @Test
    fun theCoalitionsWholeFinalRoundIsHandedToTheTable() = runTest {
        val session = teachingSession()
        Learner(session).follow()

        // `frames` replays its last batch, and nothing is dispatched after the call.
        val batch = session.frames.first()
        assertEquals(GamePhase.SCORING, session.state.phase, "the round finished in that batch")

        val played = batch.map { it.action }
        assertTrue(
            played.any { it is GameAction.CallVinto },
            "this is meant to be the batch the learner's call produced: $played",
        )
        assertTrue(
            played.any { it is GameAction.SelectActionTarget || it is GameAction.DeclareKingAction },
            "and it must carry the coalition's play, which is what the ending teaches",
        )

        val queue = AnimationQueue<Frame>(takesTime = { it.hasSomethingToSee })
        queue.submit(batch)

        assertEquals(
            batch.size,
            queue.pending,
            "the taught final round has outgrown the budget: a learner would read the score",
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
 * The learner: does exactly what the coach points at, and nothing it does not.
 *
 * Reads the position the way the screen does — `tableFor(view, question)` with the seat's
 * own memory — asks `lessonFor` what to say about it, and then presses the button, taps the
 * card or picks the rank chip the lesson points at. A talk beat is acknowledged. A lesson
 * that points at nothing while there is something to do fails the walk, because that is a
 * learner left standing.
 */
private class Learner(private val session: LocalGameSession) {

    private var taught = Taught()
    private var question: Question = Question.None

    suspend fun follow(steps: Int = STEPS) {
        repeat(steps) {
            if (session.isOver && session.state.phase == GamePhase.SCORING) return
            val view = projectView(session.state, session.playerId)
            val table = tableFor(view, question)
            val lesson = lessonFor(view, table, taught, session.rememberedHand())
                ?: return

            if (lesson.talkId != null) {
                taught = taught.heard(lesson)
                return@repeat
            }

            val move = moveFor(lesson, table)
                ?: fail("the coach pointed at nothing the table offers: ${lesson.teaches} -> ${lesson.point}")
            taught = taught.heard(lesson)
            when (move) {
                is Move.Ask -> {
                    question = move.question
                }

                is Move.Send -> {
                    val refusal = session.dispatch(move.action)
                    assertEquals(null, refusal, "the coach pointed at a move the engine refused")
                    question = Question.None
                }
            }
        }
        fail("the walk did not reach the scoring in $steps steps")
    }

    private fun moveFor(lesson: Lesson, table: Table): Move? = when (val point = lesson.point) {
        is Target.Button -> table.choices.firstOrNull { it.label == point.label }?.move
        is Target.Place -> (point.anchor as? Anchor.Seat)?.let { table.taps[CardRef(it.playerId, it.position)] }
        is Target.Chip -> table.ranks.firstOrNull { it.rank == point.rank }?.move
        is Target.Seat, is Target.Furniture, null -> null
    }

    private companion object {
        const val STEPS = 120
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

    /** The smaller line under it, also typed now. */
    val said = mutableSetOf<Detail>()

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
