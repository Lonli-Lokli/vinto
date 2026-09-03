package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

    /**
     * The second thing said is what every card's explanation assumes: you cannot see your
     * own hand, so a card you have looked at is worth more than its number says. Added on
     * the product owner's reading of the old opening, which said every card counts and left
     * a newcomer throwing every 9 back in fright.
     */
    @Test
    fun theSecondThingSaidIsThatTheGameIsMemory() = runTest {
        val session = teachingSession()
        val opening = assertNotNull(teach(session))
        val taught = Taught().heard(opening)

        val second = assertNotNull(teach(session, taught))
        assertEquals(Teaches.Memory, second.teaches)
        assertEquals("memory", second.talkId, "and it is read, not done")
    }

    /**
     * The dots over the coach count the intro's beats while the intro is being read. Every
     * talk beat before the table is handed over is one of them, in the order they are said,
     * and nothing after the hand-over is.
     */
    @Test
    fun everyIntroBeatIsAStepOfTheIntroAndNothingAfterItIs() = runTest {
        val session = teachingSession()
        var taught = Taught()
        var expected = 0
        repeat(TALK_LIMIT) {
            val lesson = teach(session, taught) ?: return@repeat
            if (lesson.talkId == null) {
                assertEquals(null, introStep(lesson), "a lesson to *do* is not an intro step")
                assertEquals(INTRO_BEATS.size, expected, "every intro beat was said before the table")
                return@runTest
            }
            assertEquals(expected, introStep(lesson), "the intro is said in the order the dots count")
            expected += 1
            taught = taught.heard(lesson)
        }
        fail("the intro never ended")
    }

    /**
     * While the coach is talking nothing on the table may be touched. The first thing a
     * newcomer does with five breathing cards under a paragraph is tap one, and the peek
     * used to happen under the welcome.
     */
    @Test
    fun aTableHeldStillHasNothingToTouch() = runTest {
        val session = teachingSession()
        val view = stateOf(session)
        val live = tableFor(view)
        assertTrue(live.taps.isNotEmpty(), "the setup table offers the player's cards")

        val held = live.heldStill()
        assertTrue(held.taps.isEmpty(), "and held, it offers none of them")
        assertTrue(held.choices.isEmpty() && held.seats.isEmpty() && held.ranks.isEmpty())
        assertEquals(live.prompt, held.prompt, "the prompt still says what will be asked")
    }

    /**
     * The swap advice, on the shape of hand it was reported with: two cards peeked, a low one
     * drawn. The coach used to point at the first card whatever it was — it read the *view*,
     * which hides what you have seen. The worse of the two peeked is the card to give up: it
     * is worse than the draw, and it is known, so it can be named as it goes down.
     */
    @Test
    fun theCardToGiveUpIsTheWorstOneThePlayerHasSeen() = runTest {
        val session = teachingSession()
        val me = session.playerId
        val taught = talkedThrough(session)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))

        val memory = session.rememberedHand()
        assertEquals(setOf(0, 1), memory.keys, "the two peeks are what the player remembers")
        assertEquals(Rank.EIGHT, memory.getValue(1).rank)

        val view = stateOf(session)
        val lesson = assertNotNull(lessonFor(view, tableFor(view, Question.WhichSlot), taught, memory))
        assertEquals(Teaches.GiveUpWorst, lesson.teaches)
        assertEquals(Target.Place(Anchor.Seat(me, 1)), lesson.point, "the 8, not the 7")
    }

    /**
     * And when nothing the player knows is worse than the card in hand — the Joker and a King
     * peeked, a 2 drawn — the slot to take is one they have not looked at, because giving up a
     * known King for a 2 is a trade that loses two points and learns nothing.
     */
    @Test
    fun withNothingWorseKnownTheSwapGoesIntoAnUnseenSlot() = runTest {
        val session = teachingSession()
        val me = session.playerId
        val taught = talkedThrough(session)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 2)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 3)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))

        val memory = session.rememberedHand()
        assertEquals(Rank.JOKER, memory.getValue(2).rank, "the deal's Joker is where the lesson says")

        val view = stateOf(session)
        val lesson = assertNotNull(lessonFor(view, tableFor(view, Question.WhichSlot), taught, memory))
        assertEquals(Teaches.SwapBlind, lesson.teaches)
        assertEquals(Target.Place(Anchor.Seat(me, 0)), lesson.point, "the first slot never looked at")

        // Before the slot is even asked for, the same reading points at Swap rather than Discard.
        val deciding = assertNotNull(lessonFor(view, tableFor(view), taught, memory))
        assertEquals(Teaches.KeepOrThrow, deciding.teaches)
        assertEquals(Target.Button(Label.SwapCards), deciding.point)
    }

    /**
     * The card going down is named from memory, not from the view — the view shows none of
     * your cards after the setup peeks, so the chip the coach pointed at was always null and
     * the 7 went down unnamed in the first round anybody played (product owner). And a card
     * the learner never looked at is a guess, which the coach says not to make.
     */
    @Test
    fun theRankToNameIsReadFromMemoryAndAnUnseenCardIsNotGuessedAt() = runTest {
        val session = teachingSession()
        val me = session.playerId
        val taught = talkedThrough(session)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        val memory = session.rememberedHand()
        val view = stateOf(session)

        val known = assertNotNull(lessonFor(view, tableFor(view, Question.CallRank(0)), taught, memory))
        assertEquals(Teaches.NameOnlySeen, known.teaches)
        assertEquals(Target.Chip(Rank.SEVEN), known.point, "the 7 in the first slot, which was peeked")

        val unseen = assertNotNull(lessonFor(view, tableFor(view, Question.CallRank(3)), taught, memory))
        assertEquals(Teaches.DoNotGuess, unseen.teaches)
        assertEquals(Target.Button(Label.JustSwap), unseen.point, "put it down without a word")
    }

    /**
     * A hand every card of which has been seen, adding up to nothing or less, is one to call
     * on — and the coach says so over the window that offers the button, ahead of the toss-in
     * beat that window would otherwise get.
     */
    @Test
    fun aFullySeenHandAtZeroOrBelowIsOneToCallOn() = runTest {
        val session = teachingSession()
        val me = session.playerId
        val view = stateOf(session)
        val hand = view.players.first { it.id == me }.cards.indices

        val joker = Card(id = "Joker1", rank = Rank.JOKER, value = -1, played = false)
        val king = Card(id = "K_0", rank = Rank.KING, value = 0, played = false)
        val four = Card(id = "4_0", rank = Rank.FOUR, value = 4, played = false)
        assertTrue(readyToCall(view, hand.associateWith { if (it == 0) joker else king }), "two Jokers' worth")
        assertTrue(!readyToCall(view, hand.associateWith { four }), "not on a hand of 4s")
        assertTrue(!readyToCall(view, mapOf(0 to joker)), "and not with four cards never looked at")
    }

    /** Somebody else's turn points at nothing: the arrow at the log box was noise (product owner). */
    @Test
    fun watchingABotPointsAtNothing() = runTest {
        val session = teachingSession()
        val me = session.playerId
        val taught = talkedThrough(session)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

        // The bots have moved on; whatever the table shows now is theirs or a window of theirs.
        val view = stateOf(session)
        val table = tableFor(view)
        if (table.waiting) {
            val lesson = assertNotNull(lessonFor(view, table, taught, session.rememberedHand()))
            assertTrue(lesson.teaches is Teaches.Watching, "$lesson")
            assertEquals(null, lesson.point, "nothing to point at while somebody else plays")
        }
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

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))

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
