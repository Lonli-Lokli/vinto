package game.vinto.app

import androidx.compose.runtime.mutableStateOf
import game.vinto.app.game.GameHolder
import game.vinto.client.Frame
import game.vinto.client.GameSession
import game.vinto.client.Move
import game.vinto.client.Say
import game.vinto.client.SessionEvent
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.TableTalk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window asked twice, answered once.
 *
 * Reported from a phone: *"if I press continue to mark I have nothing to toss in, and somebody
 * tossin card, I again see continue button I need to press, why?"* The rule is
 * `tossInAsk` and is tested against the model in `AskedTwiceTest`; this is the wiring — that
 * the holder remembers the answer as it sends it, and gives it again unasked.
 *
 * A stand-in session rather than a real one, because what is being asserted is what reaches
 * `dispatch`: a real game would have to be driven to a window with a queued action in it, and
 * would then prove the engine's behaviour over again rather than the screen's.
 */
class AnsweredTwiceTest {

    private class Sent : GameSession {
        val actions = mutableListOf<GameAction>()
        override val playerId: String = teachingSession().view.value.viewerId
        private val _view = MutableStateFlow(teachingSession().view.value)
        override val view: StateFlow<PlayerView> = _view.asStateFlow()
        override val events: SharedFlow<SessionEvent> = MutableSharedFlow<SessionEvent>().asSharedFlow()
        override val isOver: Boolean = false
        override val frames: SharedFlow<List<Frame>> = MutableSharedFlow<List<Frame>>().asSharedFlow()
        override val log: StateFlow<List<Say>> = MutableStateFlow<List<Say>>(emptyList()).asStateFlow()
        override val away: StateFlow<Set<String>> = MutableStateFlow<Set<String>>(emptySet()).asStateFlow()
        override val talk: SharedFlow<TableTalk> = MutableSharedFlow<TableTalk>().asSharedFlow()
        override val reveals: StateFlow<List<PublicReveal>> =
            MutableStateFlow<List<PublicReveal>>(emptyList()).asStateFlow()
        override val plan: StateFlow<CoalitionPlan?> = MutableStateFlow<CoalitionPlan?>(null).asStateFlow()

        override suspend fun dispatch(action: GameAction): String? {
            actions += action
            return null
        }

        override suspend fun doneConferring(): String? = null
        override suspend fun say(talk: TableTalk): String? = null
        override suspend fun editPlan(edit: PlanEdit): String? = null
        override suspend fun agreePlan(agree: Boolean): String? = null
    }

    /** A window on nines, owned by somebody else, that this seat has not answered. */
    private fun window(): PlayerView {
        val whole = teachingSession().view.value
        val owner = whole.players.indexOfFirst { it.id != whole.viewerId }
        return whole.copy(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            activeTossIn = ActiveTossIn(
                ranks = listOf(Rank.NINE),
                initiatorId = whole.players[owner].id,
                originalPlayerIndex = owner,
                participants = emptyList(),
                queuedActions = emptyList(),
                waitingForInput = true,
                playersReadyForNextTurn = emptyList(),
            ),
        )
    }

    @Test
    fun theSameWindowIsAnsweredWithoutAsking() = runTest {
        val session = Sent()
        val open = window()
        val holder = GameHolder(session, view = mutableStateOf(open))
        val done = GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId))

        // The press the player actually makes.
        holder.act(Move.Send(done))
        assertEquals(listOf<GameAction>(done), session.actions)

        // The window comes back: a nine was thrown in, played, and the ready list was wiped.
        // Nothing about this seat moved, so the answer it already gave is given again.
        holder.answerAgain(open)
        assertEquals(listOf<GameAction>(done, done), session.actions, "the same window was not answered for me")
    }

    @Test
    fun aWindowOnANewRankIsStillAsked() = runTest {
        val session = Sent()
        val holder = GameHolder(session, view = mutableStateOf(window()))
        val done = GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId))

        holder.act(Move.Send(done))

        // A thrown King widens the window to its declared rank as well, which is a rank this
        // seat has never been asked about.
        val widened = window().let { view ->
            view.copy(activeTossIn = view.activeTossIn?.copy(ranks = listOf(Rank.KING, Rank.NINE)))
        }
        holder.answerAgain(widened)

        assertEquals(listOf<GameAction>(done), session.actions, "a wider window was answered for me")
    }

    /** Nothing is answered before the player has answered it once. */
    @Test
    fun aWindowNobodyHasAnsweredIsNotAnswered() = runTest {
        val session = Sent()
        val holder = GameHolder(session, view = mutableStateOf(window()))

        holder.answerAgain(window())

        assertEquals(emptyList<GameAction>(), session.actions, "a window was answered before I had")
    }
}
