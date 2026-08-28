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

    /**
     * Acknowledges the talk beats, as a player tapping "Go on" would, and stops either when
     * the lesson becomes something to *do* or when it reaches [stopAt].
     *
     * Written as a walk rather than a set of ids on purpose: the point of these cases is what
     * the coach says at a given position, and a test that had to be told the running order
     * would break every time a beat was added — which is exactly what happened when the card
     * tour arrived and eight of them were.
     */
    private fun talkedThrough(session: LocalGameSession, stopAt: String? = null): Taught {
        var taught = Taught()
        repeat(TALK_LIMIT) {
            val lesson = teach(session, taught) ?: return taught
            if (lesson.talkId == null || lesson.talkId == stopAt) return taught
            taught = taught.heard(lesson)
        }
        return taught
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
        val taught = talkedThrough(session)

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
        val taught = talkedThrough(session)

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

        val lesson = assertNotNull(teach(session, talkedThrough(session, stopAt = "vinto")))

        assertEquals(Chapter.VINTO, lesson.chapter)
        assertEquals("vinto", lesson.talkId, "it stops the table to say it")
        assertEquals(Target.Seat(caller), lesson.point, "and points at whoever called")
        assertTrue(lesson.body.contains("one more turn"), "saying what a call does: ${lesson.body}")
    }

    /** And then the coalition — the rule that is hardest to work out by watching. */
    @Test
    fun theCoalitionIsExplainedAfterTheCall() = runTest {
        val session = playToTheCall()
        val lesson = assertNotNull(teach(session, talkedThrough(session, stopAt = "coalition")))
        assertEquals("coalition", lesson.talkId)
        assertTrue(
            lesson.body.contains("single best hand") && lesson.body.contains("caller's cards"),
            "both halves of the rule: only the best hand counts, and the caller is untouchable",
        )
    }

    /** Scoring is explained over the reveal, with both outcomes named rather than only ours. */
    @Test
    fun scoringIsExplainedWhenTheHandsGoFaceUp() = runTest {
        val session = playToTheEnd()
        assertEquals(GamePhase.SCORING, session.state.phase)

        val lesson = assertNotNull(teach(session, talkedThrough(session, stopAt = "scoring")))

        assertEquals(Chapter.SCORE, lesson.chapter)
        assertTrue(lesson.body.contains("+3"), "the numbers, as the rules give them")
        assertTrue(
            lesson.body.contains("Level", ignoreCase = true),
            "including the tie, which favours the caller: ${lesson.body}",
        )
    }

    /**
     * The three outcomes of a round, all of them, with the right numbers.
     *
     * This is here because the copy got one wrong: it said a caller who finishes lower takes
     * +3 "while the rest take nothing", when the rules and `calculateRoundPoints` both charge
     * the others a point each — nothing is what a *tie* costs them. A tutorial that teaches a
     * scoring rule incorrectly is worse than one that skips it, because the player believes it.
     */
    @Test
    fun theScoringLessonGivesAllThreeOutcomes() = runTest {
        val session = playToTheEnd()
        val lesson = assertNotNull(teach(session, talkedThrough(session, stopAt = "scoring")))

        assertTrue(lesson.body.contains("+3"), "the winning number")
        assertTrue(lesson.body.contains("loses 1"), "and the losing one")
        assertTrue(
            lesson.body.contains("everybody else loses 1"),
            "a caller who finishes lower costs the others a point each: ${lesson.body}",
        )
        assertTrue(
            lesson.body.contains("Level"),
            "and only a tie leaves them on nothing: ${lesson.body}",
        )
    }

    /**
     * The button is hidden while the player is learning, so somebody has to tell them it is
     * theirs afterwards — otherwise the lesson teaches a game they can never end.
     */
    @Test
    fun thePlayerIsToldTheyMayCallItThemselves() = runTest {
        val session = playToTheCall()
        val lesson = assertNotNull(
            teach(session, talkedThrough(session, stopAt = "your_turn_to_call")),
        )

        assertEquals("your_turn_to_call", lesson.talkId)
        assertTrue(
            lesson.body.contains("end of any turn of yours"),
            "saying when it may be pressed: ${lesson.body}",
        )
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
        const val TALK_LIMIT = 40
        const val TURNS = 12
    }
}
