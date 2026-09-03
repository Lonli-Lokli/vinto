package game.vinto.app

import game.vinto.app.game.lessonIsOver
import game.vinto.client.INTRO_BEATS
import game.vinto.client.Lesson
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Taught
import game.vinto.client.Teaches
import game.vinto.client.lessonFor
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lesson ends when the coach has finished, not when the engine has.
 *
 * The hands going face up is the beginning of the last thing the lesson has to teach — who
 * won the round, and how a session is scored — and the screen used to treat it as the end:
 * the moment the phase reached scoring the coach dropped whatever it was saying for "that is
 * the lesson" and a Done button that closes it. So the endgame beats, written and translated,
 * could never be reached, and a player watching for the payoff got one tap and the menu
 * (product owner).
 */
class LessonEndingTest {

    @Test
    fun theCoachStillHasTheEndgameToTeachWhenTheHandsGoFaceUp() {
        val scoring = playedOut()
        assertEquals(GamePhase.SCORING, scoring.phase, "this case needs a finished round")

        var taught = introRead()
        val said = mutableListOf<Lesson>()
        repeat(BEATS) {
            val lesson = lessonFor(scoring, tableFor(scoring, Question.None), taught) ?: return@repeat
            said += lesson
            taught = taught.heard(lesson)
        }

        assertTrue(
            said.any { it.teaches == Teaches.Scoring },
            "the round's own scoring is never explained: ${said.map { it.teaches }}",
        )
        assertTrue(
            said.any { it.teaches == Teaches.Session },
            "nor is the session: ${said.map { it.teaches }}",
        )
        assertTrue(said.all { it.talkId != null }, "everything said over a finished round is read")
    }

    @Test
    fun theEndCardWaitsForTheLastBeat() {
        val scoring = playedOut()
        val beat = lessonFor(scoring, tableFor(scoring, Question.None), introRead())

        assertFalse(
            lessonIsOver(scoring, beat),
            "the Done button replaced a beat the coach had not said yet",
        )
        assertTrue(lessonIsOver(scoring, null), "and it appears once there is nothing left")
    }

    @Test
    fun aRoundStillBeingPlayedIsNeverOver() {
        val playing = playedOut().copy(phase = GamePhase.PLAYING)
        assertFalse(lessonIsOver(playing, null), "a quiet coach is not a finished lesson")
    }

    /** A coach that has read out the whole introduction, so the next beat is the endgame. */
    private fun introRead(): Taught = Taught(talked = INTRO_BEATS.toSet())

    /**
     * The taught round, played to the end by taking whatever the table offers.
     *
     * The same bad player `TeachingRoundTest` drives its cases with: it keeps nothing, so
     * every window stays open to somebody and the director's late call still lands.
     */
    private fun playedOut(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

            repeat(MOVES) {
                if (session.isOver) return@repeat
                val move = tableFor(session.view.value, Question.None).choices
                    .mapNotNull { it.move as? Move.Send }
                    .firstOrNull { it.action !is GameAction.CallVinto }
                    ?: return@repeat
                session.dispatch(move.action)
            }
            view = session.view.value
        }
        return view
    }

    private companion object {
        const val MOVES = 80
        const val BEATS = 12
    }
}
