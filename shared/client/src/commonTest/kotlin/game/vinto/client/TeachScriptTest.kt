package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        assertEquals(
            Teaches.Welcome,
            opening.teaches,
            "and the first thing it teaches is the object of the game",
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
        assertEquals(
            Rank.SEVEN,
            lesson.noteRank,
            "a card just turned over and the lesson did not offer to say what it is",
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

        // The caller travels as a Speaker, not as an interpolated name — the renderer decides
        // how a person is addressed, and the beat only says which person.
        val named = session.state.players.first { it.id == caller }.nickname
        assertEquals(Teaches.VintoCalled(Speaker.Named(named)), lesson.teaches)
    }

    /** And then the coalition — the rule that is hardest to work out by watching. */
    @Test
    fun theCoalitionIsExplainedAfterTheCall() = runTest {
        val session = playToTheCall()
        val lesson = assertNotNull(teach(session, talkedThrough(session, stopAt = "coalition")))
        assertEquals("coalition", lesson.talkId)
        assertEquals(Teaches.Coalition, lesson.teaches)
    }

    /** Scoring is explained over the reveal, with both outcomes named rather than only ours. */
    @Test
    fun scoringIsExplainedWhenTheHandsGoFaceUp() = runTest {
        val session = playToTheEnd()
        assertEquals(GamePhase.SCORING, session.state.phase)

        val lesson = assertNotNull(teach(session, talkedThrough(session, stopAt = "scoring")))

        assertEquals(Chapter.SCORE, lesson.chapter)
        assertEquals(Teaches.Scoring, lesson.teaches)
    }

    /**
     * Scoring is explained *before* the session beat that depends on it.
     *
     * What the numbers say moved to `LessonCopyTest` in composeApp when the words did (WORDS.md §6h) —
     * this is what is left here, and it is the half that belongs here: the running order is a
     * fact about the script, and explaining what a round is worth after explaining that a game
     * is many of them is explaining it backwards.
     */
    @Test
    fun theRoundIsScoredBeforeTheSessionIsExplained() = runTest {
        val session = playToTheEnd()

        val scoring = assertNotNull(teach(session, talkedThrough(session, stopAt = "scoring")))
        assertEquals(Teaches.Scoring, scoring.teaches)

        val next = assertNotNull(teach(session, talkedThrough(session, stopAt = "session")))
        assertEquals(Teaches.Session, next.teaches)
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
        assertEquals(Teaches.YourTurnToCall, lesson.teaches)
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
