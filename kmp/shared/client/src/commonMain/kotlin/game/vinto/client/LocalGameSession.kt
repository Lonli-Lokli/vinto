package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.PlayerView
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.calculateFinalScores
import game.vinto.engine.calculateRoundPoints
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.actorId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * One human against three bots, entirely on this device.
 *
 * This is design R1 in code: a solo game creates no room, opens no socket and contacts no
 * server. The engine and the bot are Kotlin Multiplatform and already run on Android, iOS and
 * Wasm, so hosting a solo game would cost CPU — up to 1.6 s per action once three bots take a
 * turn — and buy nothing. It also means a solo game works on a plane, which is most of when
 * people play a card game alone.
 *
 * The bots are driven by the same [BotRunner] the Durable Object uses, through the same
 * validator, so a local game and an online one are the same game rather than two that resemble
 * each other. The one difference worth knowing is that here the *player* is seat zero and the
 * runner declines to act for them, exactly as it declines to act for a seated human online.
 *
 * @param seed picked by the caller. Required rather than defaulted, because choosing one is
 *   ambient randomness and belongs outside anything that has to replay.
 * @param botDispatcher where the search runs. Defaults to the caller's context so tests stay
 *   deterministic; an app passes `Dispatchers.Default` so a thinking bot never blocks drawing.
 */
class LocalGameSession(
    seed: Long,
    difficulty: Difficulty = Difficulty.MODERATE,
    private val botDispatcher: CoroutineDispatcher? = null,
    random: Random = Random(seed),
    /**
     * A round already in progress, for a game being resumed.
     *
     * The bots' memories are not restored with it — they are rebuilt as they play, so a
     * resumed opponent has forgotten what it saw before the app closed. That is a real
     * difference and an acceptable one: it makes them slightly worse, never wrong, and the
     * alternative is persisting a search's internal state to save a solo game.
     */
    resuming: GameState? = null,
) : GameSession {

    // Internal rather than private so the tests can drive the *person's* seat with the same
    // bot brain: choosing a move needs the full state, and the view is redacted by design.
    internal var state: GameState = resuming ?: initializeGame(seed, difficulty)
        private set

    /** The seat the person is playing. `initializeGame` deals seat zero as the human. */
    val playerId: String = state.players.first { it.isHuman }.id

    private val runner = BotRunner(difficulty, random)

    /**
     * Everything that has happened, written down as it happens.
     *
     * Always on rather than switched on when something looks wrong: by then the interesting
     * actions are already in the past. What it costs is an action and a hash per move; what
     * it buys is that a bug report is a *recording* the replay harness can play back, in
     * either language, and point at the exact action where the two engines disagree.
     */
    private val recorder = Recorder(seed, difficulty, state)

    private val _view = MutableStateFlow(projectView(state, playerId))
    override val view: StateFlow<PlayerView> = _view.asStateFlow()

    // A shared flow rather than a state flow, because one dispatch can produce more than one
    // event and the last must not erase the ones before it. The round that ends on a bot's
    // final move produces exactly that pair — `BotsPlayed` then `RoundEnded` — and a
    // latest-value flow drops whichever arrives first, which is how a score screen ends up
    // never appearing.
    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 1,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    override val isOver: Boolean get() = state.phase == GamePhase.SCORING

    /** What has happened lately, newest last. Fed to the screen's recent-actions strip. */
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /**
     * What there is to see, in the order it happened, each with the table it left behind.
     *
     * Frames rather than states: this is the same stream a room's log will feed, so the
     * screen above cannot tell a solo game from an online one — which is the point of
     * deriving it from the view (design C1). Each frame is one action, so a screen can step
     * through the bots' turns at the pace it draws them rather than jumping to the end and
     * narrating backwards.
     */
    private val _frames = MutableSharedFlow<List<Frame>>(
        replay = 1,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<List<Frame>> = _frames.asSharedFlow()

    override suspend fun dispatch(action: GameAction): String? {
        // The seat boundary, the same one the Durable Object checks before the engine sees
        // anything. There is nobody to keep honest in a solo game — the point is that the
        // rule lives in one place and holds in both, so a screen that tries to act for a bot
        // is refused here exactly as it would be refused online, rather than working locally
        // and failing the first time somebody plays a real opponent.
        action.actorId?.let { claimed ->
            if (claimed != playerId) return refuse("you may only act as $playerId")
        }

        // Validated before reducing, exactly as the room does. A local player has nobody to
        // cheat but themselves, and that is not the point: the same path means a UI that works
        // here works online, and a rule that is enforced in one place is enforced.
        when (val validation = ActionValidator.validate(state, action)) {
            is Validation.Invalid -> return refuse(validation.reason)

            Validation.Valid -> Unit
        }

        val before = state
        val seenBefore = _view.value
        state = when (val result = GameEngine.reduce(state, action)) {
            is ReduceResult.Success -> result.state
            is ReduceResult.Failure -> return refuse(result.reason)
        }

        publish()
        val seen = mutableListOf(
            Frame(
                action = action,
                scenes = choreograph(action, seenBefore, _view.value),
                view = _view.value,
            ),
        )
        record(action, before, state)

        seen += playBots()
        _frames.tryEmit(seen)
        return null
    }

    /**
     * Notes one action: in words for the player, and in full for a bug report.
     *
     * The log is trimmed to the recent past because it is read at a glance; the recording is
     * not, because it is read by a replay.
     */
    private fun record(action: GameAction, before: GameState, after: GameState) {
        recorder.record(action, after)
        narrate(action, before, after, playerId)?.let { line ->
            _log.value = (_log.value + line).takeLast(LOG_LENGTH)
        }
    }

    /**
     * This game, as a replayable recording.
     *
     * @param at a timestamp from the caller. The session has no clock — nothing inside the
     *   engine or beside it may read one, or a recording stops being reproducible.
     */
    fun report(at: String, label: String): Recording = recorder.export(state, at, label)

    /** Announces a refusal and hands the reason back to the caller. */
    private fun refuse(reason: String): String {
        _events.tryEmit(SessionEvent.Refused(reason))
        return reason
    }

    /**
     * Runs every bot move that follows, off whatever thread called in.
     *
     * The authoritative view is published once, *after* the bots have finished: it is the
     * state of the game, and the game really has moved on. What the screen shows is a
     * different question, answered by the frames returned here — one per move, each carrying
     * the table that move left behind, so the animation layer can walk them at a readable
     * pace instead of jumping to the end and explaining afterwards.
     */
    private suspend fun playBots(): List<Frame> {
        val start = state
        var moves = 0
        val told = mutableListOf<Triple<GameAction, GameState, GameState>>()

        val next = onBotDispatcher {
            var working = start
            while (moves < MAX_BOT_STEPS && working.phase != GamePhase.SCORING) {
                val action = nextBotAction(working) ?: break
                val stepped = (GameEngine.reduce(working, action) as? ReduceResult.Success)?.state
                    ?: break

                told += Triple(action, working, stepped)
                working = stepped
                moves++
            }
            working
        }

        if (moves == 0) return emptyList()

        // Choreographed from the *views*, not the states, so this is the same computation a
        // client will do from a socket — the bots' moves arrive there as a log of actions and
        // a new view, which is exactly what these three are.
        //
        // One frame per move rather than one batch for the lot. The engine finishes all three
        // bots' turns before anything is drawn — it must, since each move depends on the last
        // — but the screen is given them a move at a time, with the table each one left
        // behind, so it can show them one after another instead of all at once.
        val seen = told.map { (action, was, now) ->
            val before = projectView(was, playerId)
            val after = projectView(now, playerId)
            Frame(action, choreograph(action, before, after), after)
        }

        told.forEach { (action, was, now) -> record(action, was, now) }
        state = next
        // Announced before the view is published, so a round the bots finished reads in the
        // order it happened: they moved, and then it ended.
        _events.tryEmit(SessionEvent.BotsPlayed(moves))
        publish()
        return seen
    }

    /**
     * The bots' next move, or null for every reason the room stops making them: the runner has
     * nothing to say, the move belongs to the person holding the phone, or the engine will not
     * have it. The last is not defensive — a bot the validator refuses is a bug worth seeing
     * as a stuck game rather than one papered over by trying the next move.
     */
    private fun nextBotAction(from: GameState): GameAction? {
        val action = runner.nextAction(from) ?: return null
        if (action.actorId == playerId) return null
        if (ActionValidator.validate(from, action) is Validation.Invalid) return null

        return action
    }

    private suspend fun <T> onBotDispatcher(block: () -> T): T =
        botDispatcher?.let { withContext(it) { block() } } ?: block()

    private fun publish() {
        val wasOver = _view.value.phase == GamePhase.SCORING
        _view.value = projectView(state, playerId)

        // On the transition alone: `publish` runs twice for a dispatch that the bots answer,
        // and a round does not end twice.
        if (!wasOver && state.phase == GamePhase.SCORING) {
            _events.tryEmit(SessionEvent.RoundEnded(
                scores = calculateFinalScores(state.players, state.vintoCallerId),
                points = calculateRoundPoints(state.players, state.vintoCallerId),
            ))
        }
    }

    private companion object {
        /** A guard, not a rule: a bot loop this long has stopped being a game. */
        const val MAX_BOT_STEPS = 200

        /** Room for a whole turn's worth of events before the oldest is dropped. */
        const val EVENT_BUFFER = 64

        /** Enough to see a turn go by, not enough to become a transcript. */
        const val LOG_LENGTH = 6
    }
}
