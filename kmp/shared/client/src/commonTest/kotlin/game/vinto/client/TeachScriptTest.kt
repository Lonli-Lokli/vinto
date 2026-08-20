package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the coach says, checked against the positions it says it in.
 *
 * The lesson is derived from the table rather than from a step counter, which is what lets a
 * player wander off the path without breaking it — and also what makes it possible to check:
 * put the game in a position and ask what it would say. These cases cover the ones a
 * play-through is least likely to reach and most likely to need: the opening, the endgame, and
 * pointing at something that is not there.
 */
class TeachScriptTest {

    private fun stateOf(session: LocalGameSession) =
        projectView(session.state, session.playerId)

    private fun teach(session: LocalGameSession, taught: Taught = Taught()): Lesson? {
        val view = stateOf(session)
        return lessonFor(view, tableFor(view), taught)
    }

    @Test
    fun itIntroducesTheTableBeforeAnythingElse() = runTest {
        val session = teachingSession()
        val opening = assertNotNull(teach(session))

        assertEquals(Chapter.TABLE, opening.chapter)
        assertEquals("welcome", opening.talkId, "the first thing is something to read")
        assertTrue(
            opening.body.contains("lowest hand wins", ignoreCase = true),
            "and the first thing it says is the object of the game: ${opening.body}",
        )
    }

    /** The tour is finite: keep acknowledging and it hands the player back their table. */
    @Test
    fun theTourEndsAndTheGameBegins() = runTest {
        val session = teachingSession()
        var taught = Taught()

        repeat(TOUR_LIMIT) {
            val lesson = teach(session, taught) ?: return@repeat
            if (lesson.talkId == null) return@repeat
            taught = taught.heard(lesson)
        }

        val lesson = assertNotNull(teach(session, taught))
        assertEquals(null, lesson.talkId, "after the tour the lesson is something to do")
        assertEquals(Chapter.PEEK, lesson.chapter, "and the thing to do is the setup peek")
        assertNotNull(lesson.point, "pointing at one of the player's own cards")
    }

    /** A card met for the first time is explained in the game's own words. */
    @Test
    fun meetingACardExplainsIt() = runTest {
        val session = teachingSession()
        val me = session.playerId
        var taught = Taught()
        repeat(TOUR_LIMIT) {
            val lesson = teach(session, taught) ?: return@repeat
            if (lesson.talkId == null) return@repeat
            taught = taught.heard(lesson)
        }

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))

        val lesson = assertNotNull(teach(session, taught))
        val note = assertNotNull(lesson.note, "a card just turned over and nobody said what it is")
        assertTrue(
            note.contains("Seven") && note.contains("Peek at one of your own cards"),
            "the note is the card's own copy, not a second set of rules: $note",
        )
    }

    /**
     * The endgame is what a free-play tutorial never reaches, and the hardest part to explain.
     * The coach must name the caller, and point at them.
     */
    @Test
    fun theCallIsExplainedAndTheCallerIsPointedAt() = runTest {
        val session = playToTheCall()
        val caller = assertNotNull(session.state.vintoCallerId)

        val everythingSoFar = Taught(talked = setOf("welcome", "tour", "seats", "help"))
        val lesson = assertNotNull(teach(session, everythingSoFar))

        assertEquals(Chapter.VINTO, lesson.chapter)
        assertEquals("vinto", lesson.talkId, "it stops the table to say it")
        assertEquals(Target.Seat(caller), lesson.point, "and points at whoever called")
        assertTrue(lesson.body.contains("one more turn"), "saying what a call does: ${lesson.body}")
    }

    /** And then the coalition — the rule that is hardest to work out by watching. */
    @Test
    fun theCoalitionIsExplainedAfterTheCall() = runTest {
        val session = playToTheCall()
        val taught = Taught(talked = setOf("welcome", "tour", "seats", "help", "vinto"))

        val lesson = assertNotNull(teach(session, taught))
        assertEquals("coalition", lesson.talkId)
        assertTrue(
            lesson.body.contains("best single hand") && lesson.body.contains("caller's cards"),
            "both halves of the rule: only the best hand counts, and the caller is untouchable",
        )
    }

    /** Scoring is explained over the reveal, with both outcomes named rather than only ours. */
    @Test
    fun scoringIsExplainedWhenTheHandsGoFaceUp() = runTest {
        val session = playToTheEnd()
        assertEquals(GamePhase.SCORING, session.state.phase)

        val taught = Taught(talked = setOf("welcome", "tour", "seats", "help", "vinto", "coalition"))
        val lesson = assertNotNull(teach(session, taught))

        assertEquals(Chapter.SCORE, lesson.chapter)
        assertTrue(lesson.body.contains("+3"), "the numbers, as the rules give them")
        assertTrue(lesson.body.contains("level"), "including the tie, which favours the caller")
    }

    private suspend fun playToTheCall(): LocalGameSession {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        repeat(TURNS) {
            if (session.state.vintoCallerId != null || session.isOver) return@repeat
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
        }
        return session
    }

    private suspend fun playToTheEnd(): LocalGameSession {
        val session = playToTheCall()
        val me = session.playerId

        repeat(TURNS) {
            if (session.isOver) return@repeat
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
        }
        return session
    }

    private companion object {
        const val TOUR_LIMIT = 8
        const val TURNS = 12
    }
}
