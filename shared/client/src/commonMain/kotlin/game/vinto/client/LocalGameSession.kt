package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.bot.botsAnswering
import game.vinto.bot.seedTheBoard
import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.calculateFinalScores
import game.vinto.engine.calculateRoundPoints
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.Card
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlanEditOutcome
import game.vinto.shapes.TableTalk
import game.vinto.shapes.actorId
import game.vinto.shapes.agreeing
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.edited
import game.vinto.shapes.lockingLaneOf
import game.vinto.shapes.retired
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
    /**
     * A game dealt from a written-down deck rather than a seed — the lesson, and nothing else.
     *
     * Passed in rather than switched on by a flag, so the ordinary path cannot accidentally
     * reach it and a room has no way to ask for one.
     */
    dealt: GameState? = null,
    /**
     * Somebody deciding the bots' moves in place of the search. Null in a real game, which is
     * every game but the lesson. See [BotDirector].
     */
    private val director: BotDirector? = null,
) : GameSession {

    // Internal rather than private so the tests can drive the *person's* seat with the same
    // bot brain: choosing a move needs the full state, and the view is redacted by design.
    internal var state: GameState = resuming ?: dealt ?: initializeGame(seed, difficulty)
        private set

    /** The seat the person is playing. `initializeGame` deals seat zero as the human. */
    override val playerId: String = state.players.first { it.isHuman }.id

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

    /** Nobody can leave a game that is one person and three bots in one process. */
    override val away: StateFlow<Set<String>> = MutableStateFlow<Set<String>>(emptySet()).asStateFlow()

    /**
     * Whether this seat is still being offered its say before the final round runs.
     *
     * A solo game has a window too, and it needs one: without it the bots declare and play the
     * instant Vinto is called, so a person in the third coalition seat watches two turns go by
     * before they can tell anybody anything.
     *
     * **No clock, unlike the room's.** The deadline online exists because a *person* is being
     * held — the caller, who is entitled to see the round played out. Here the caller is a bot
     * and nobody is waiting, so the window ends when the player says it does.
     */
    private var conferring: Boolean = false

    /** Set once per round, so the window opens at the call and not again after it. */
    private var conferred: Boolean = false

    override val isOver: Boolean get() = state.phase == GamePhase.SCORING

    /** What has happened lately, newest last. Fed to the screen's recent-actions strip. */
    private val _log = MutableStateFlow<List<Say>>(emptyList())
    override val log: StateFlow<List<Say>> = _log.asStateFlow()

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
    override val frames: SharedFlow<List<Frame>> = _frames.asSharedFlow()

    /**
     * What the table has said. Replayed generously, because a strip that subscribes a moment
     * late should still show the conversation it arrived in the middle of.
     */
    private val _talk = MutableSharedFlow<TableTalk>(
        replay = TALK_REPLAY,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val talk: SharedFlow<TableTalk> = _talk.asSharedFlow()

    /**
     * The coalition's shared plan, kept here because a solo game has no room to keep it.
     *
     * Held under the same rules as the room's — `CoalitionPlan.edited` decides what a legal
     * edit is for both — so the composer a person learns against three bots is the composer
     * they meet online. The bots answer for their own lanes in-process, as the room's do.
     */
    private val _plan = MutableStateFlow<CoalitionPlan?>(null)
    override val plan: StateFlow<CoalitionPlan?> = _plan.asStateFlow()

    private val _reveals = MutableStateFlow<List<PublicReveal>>(emptyList())
    override val reveals: StateFlow<List<PublicReveal>> = _reveals.asStateFlow()

    override suspend fun editPlan(edit: PlanEdit): String? {
        val caller = state.vintoCallerId
        if (state.phase != GamePhase.FINAL || caller == null) return refuse("there is no round to plan")
        if (caller == playerId) return refuse("the caller has no coalition to plan with")

        val coalition = coalitionInTurnOrder(state.players.map { it.id }, caller)
        val onPlay = state.players.getOrNull(state.currentPlayerIndex)?.id
        val merged = when (val outcome = _plan.value.edited(edit, playerId, coalition, onPlay)) {
            is PlanEditOutcome.Refused -> return refuse(outcome.reason)
            is PlanEditOutcome.Edited -> outcome.plan
        }

        val bots = state.players.filter { it.isBot && it.id != caller }.map { it.id }
        val answered = botsAnswering(state, merged, edit, playerId, bots)
        _plan.value = answered.plan
        answered.said?.let(::overhear)
        return null
    }

    override suspend fun agreePlan(agree: Boolean): String? {
        val caller = state.vintoCallerId
        if (state.phase != GamePhase.FINAL || caller == null) return refuse("there is no round to plan")
        if (caller == playerId) return refuse("the caller has no coalition to plan with")
        val standing = _plan.value?.takeUnless { it.isEmpty }
            ?: return refuse("there is nothing on the board to agree to")

        _plan.value = standing.agreeing(playerId, agree)
        // Agreeing is how you finish talking. A no is only a no.
        return if (agree && conferring) doneConferring() else null
    }

    /**
     * The board, kept in step with the table: the lane of whoever is on play locks, and a
     * scored round has no plan — the room throws its away at scoring, and so does this.
     */
    private fun settlePlan() {
        _plan.value = if (state.phase == GamePhase.SCORING) {
            null
        } else {
            _plan.value?.lockingLaneOf(state.players.getOrNull(state.currentPlayerIndex)?.id)
        }
    }

    /**
     * A solo game has no room to check anything, so the seat rule is checked here — the same
     * rule, in the one place, so a screen that tried to speak for a bot is refused exactly as
     * it would be online rather than working locally and failing on a real opponent.
     */
    override suspend fun say(talk: TableTalk): String? {
        if (talk.by != playerId) return "you may only speak as $playerId"
        overhear(talk)

        // A suggestion addressed to a bot is answered by that bot, with its own planner, and
        // the move it makes when it agrees is *its own*. Without this a person's proposal was
        // broadcast into a flow nobody read — the one configuration where a human talks to
        // bots, and the talking went nowhere.
        if (talk is TableTalk.Proposal) answerFromABot(talk)
        return null
    }

    /**
     * A bot's reply to a suggestion, and the move if it agreed.
     *
     * The move goes through `dispatch`-equivalent validation like any other, so an agreement
     * cannot smuggle in something the rules refuse.
     */
    private suspend fun answerFromABot(proposal: TableTalk.Proposal) {
        val (move, answer) = runner.answerTo(state, proposal)
        overhear(answer)
        val accepted = move ?: return
        if (ActionValidator.validate(state, accepted) !is Validation.Valid) return
        (GameEngine.reduce(state, accepted) as? ReduceResult.Success)?.let { result ->
            runner.observe(accepted, state, result.state)
            state = result.state
            _view.value = myView()
            settlePlan()
        }
    }

    /**
     * One sentence, put where the table can read it.
     *
     * Into the **log** as well as the flow, because the log is the strip a screen already
     * draws and `Say` is already its vocabulary — talk that only reached a flow nobody
     * collected was a channel wired to nothing.
     */

    /**
     * The view this seat should see, with the window's state on it.
     *
     * A solo window has no clock, so it carries a nominal duration: the flag a screen reads is
     * *non-null*, and inventing a countdown nobody is racing would be a lie in the shape of a
     * number.
     */
    private fun myView(): PlayerView =
        projectView(state, playerId, conferMsRemaining = if (conferring) SOLO_CONFER_MS else null)

    /**
     * A final round somebody else called, which this seat is therefore in the coalition for.
     *
     * Never during a **directed** round. The lesson is scripted: the coach decides what
     * happens next and the learner is being taught rather than conferring, so a window would
     * be an interruption asking them to do something nobody has explained yet.
     */
    private fun inACoalitionFinalRound(): Boolean =
        director == null &&
            state.phase == GamePhase.FINAL &&
            state.vintoCallerId != null &&
            state.vintoCallerId != playerId

    private fun overhear(talk: TableTalk) {
        _talk.tryEmit(talk)
        val nicknames = state.players.associate { it.id to it.nickname }
        _log.value = (_log.value + spoken(talk, playerId, nicknames)).takeLast(LOG_LENGTH)
    }

    /**
     * What this seat has seen of its own hand, as the engine remembers it: every position in
     * its `knownCardPositions`, with the card lying there.
     *
     * For the coach, and only the coach. The view deliberately does not carry this —
     * remembering your hand is the game, and a screen that drew it would have won it for
     * you — but a coach that says "give up your worst card" has to know which card that is,
     * and pointing at the highest card *you have looked at* is advice, where pointing at one
     * you have not would be cheating on your behalf. It never names a card the player has
     * not seen. The engine already keeps the list, because a Jack that swaps a card out from
     * under you takes the position off it, which is the bookkeeping this would otherwise
     * have to repeat.
     */
    fun rememberedHand(): Map<Int, Card> {
        val me = state.players.first { it.id == playerId }
        return me.knownCardPositions
            .mapNotNull { position -> me.cards.getOrNull(position)?.let { position to it } }
            .toMap()
    }

    override suspend fun doneConferring(): String? {
        conferring = false
        conferred = true
        _view.value = myView()
        playBots().takeIf { it.isNotEmpty() }?.let { _frames.tryEmit(it) }
        return null
    }

    override suspend fun dispatch(action: GameAction): String? {
        // Acting ends the conversation. A player who has started playing has finished
        // talking, so the window does not need a second gesture to dismiss it — the button
        // exists for the other case, ending it *without* acting so the bots may go first.
        // Talk does not close it: saying things is what the window is for.
        if (conferring) {
            conferring = false
            conferred = true
        }

        // Retired moves, refused at the door rather than in the validator — `reduce` validates
        // before it dispatches, so a rule there would refuse the frozen corpus too. The room's
        // door reads the same `retired`.
        if (action.retired) return refuse("that move is no longer part of the game")

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
        val revealed: List<PublicReveal>
        state = when (val result = GameEngine.reduce(state, action)) {
            is ReduceResult.Success -> {
                revealed = result.revealed
                result.state
            }

            is ReduceResult.Failure -> return refuse(result.reason)
        }

        // The bots watch the player play, exactly as they watch each other: every accepted
        // action feeds the runner's public-information model of the table.
        runner.observe(action, before, state)
        if (revealed.isNotEmpty()) _reveals.value = _reveals.value + revealed

        publish()
        val line = narrate(action, before, state, playerId)
        val seen = mutableListOf(
            Frame(
                action = action,
                // What the table was shown rides beside the move, because it is not in the
                // state: a card turned face up by a wrong declaration is public for that
                // moment and private again afterwards.
                scenes = scenesFor(action, seenBefore, _view.value, revealed),
                view = _view.value,
                said = listOfNotNull(line),
            ),
        )
        record(action, state, line)

        seen += playBots()
        // After the bots, because opening the confer window is something `playBots` decides.
        _view.value = myView()
        _frames.tryEmit(seen)
        return null
    }

    /**
     * Notes one action: in words for the player, and in full for a bug report.
     *
     * The log is trimmed to the recent past because it is read at a glance; the recording is
     * not, because it is read by a replay.
     */
    private fun record(action: GameAction, after: GameState, line: Say?) {
        recorder.record(action, after)
        line?.let { _log.value = (_log.value + it).takeLast(LOG_LENGTH) }
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
        // The coalition's window opens here rather than on a clock, because a local game has
        // none — see `conferring`.
        if (!conferred && inACoalitionFinalRound()) conferring = true

        val start = state
        var moves = 0
        val told = mutableListOf<BotMove>()
        val overheard = mutableListOf<TableTalk>()

        val next = onBotDispatcher {
            var working = start
            while (moves < MAX_BOT_STEPS && working.phase != GamePhase.SCORING) {
                // The window holds the bots' **turns**, never their declarations. A coalition
                // confers in order to pool what it knows, so a window that silenced the bots
                // would be a conversation with nothing in it — the person would be asked to
                // plan against three hands nobody had described.
                if (conferring && nextBotAction(working) !is GameAction.DeclareCards) break

                // Anything the bots have to say about the position they are in, before they
                // move in it. Talk is not a move — it changes no state and is not counted
                // against `MAX_BOT_STEPS` — so it is collected here and emitted below rather
                // than folded into the frames.
                runner.nextTalk(working)?.let { overheard += it }

                val action = nextBotAction(working) ?: break
                val result = GameEngine.reduce(working, action) as? ReduceResult.Success
                    ?: break

                runner.observe(action, working, result.state)
                told += BotMove(action, working, result.state, result.revealed)
                working = result.state
                moves++
            }
            working
        }

        overheard.forEach(::overhear)

        // The bots' proposals on the board, for the person to read, agree to or change — built
        // after the bots have declared, so the picture they are built on is the one the person
        // sees. Fills empty lanes and stops once the person has edited anything, so it is cheap
        // to repeat on every pass.
        if (inACoalitionFinalRound()) {
            val standing = _plan.value ?: CoalitionPlan()
            val seeded = seedTheBoard(next, _plan.value)
            if (seeded.plan != standing) {
                _plan.value = seeded.plan
                seeded.said.forEach(::overhear)
            }
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
        // Reveals ride with each move, exactly as they do for the human's own dispatch above.
        // They used to be dropped here, which meant a bot's wrong King or failed throw turned
        // a card face up for the table and the one person at it never saw the card.
        val lines = told.map { move -> narrate(move.action, move.before, move.after, playerId) }
        val seen = told.zip(lines) { move, line ->
            val before = projectView(move.before, playerId)
            val after = projectView(move.after, playerId)
            Frame(
                move.action,
                scenesFor(move.action, before, after, move.revealed),
                after,
                said = listOfNotNull(line),
            )
        }

        told.zip(lines) { move, line -> record(move.action, move.after, line) }
        told.flatMap { it.revealed }.takeIf { it.isNotEmpty() }?.let { _reveals.value = _reveals.value + it }
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
        // The director speaks first, and only the lesson has one. A move it names still has to
        // pass the validator below, and a refused one falls through to the search — a script
        // that has drifted should cost the lesson its shape, not the game.
        director?.nextAction(from)
            ?.takeIf { it.actorId != playerId && ActionValidator.validate(from, it) is Validation.Valid }
            ?.let { return it }

        val action = runner.nextAction(from) ?: return null
        if (action.actorId == playerId) return null
        if (ActionValidator.validate(from, action) is Validation.Invalid) return null

        return action
    }

    private suspend fun <T> onBotDispatcher(block: () -> T): T =
        botDispatcher?.let { withContext(it) { block() } } ?: block()

    private fun publish() {
        val wasOver = _view.value.phase == GamePhase.SCORING
        _view.value = myView()
        settlePlan()

        // On the transition alone: `publish` runs twice for a dispatch that the bots answer,
        // and a round does not end twice.
        if (!wasOver && state.phase == GamePhase.SCORING) {
            _events.tryEmit(
                SessionEvent.RoundEnded(
                    scores = calculateFinalScores(state.players, state.vintoCallerId),
                    points = calculateRoundPoints(state.players, state.vintoCallerId),
                ),
            )
        }
    }

    /**
     * One bot move with everything the engine said about it. The reveals are here because
     * they exist only on the reduce result — they are what happened, not what is — and a
     * frame built without them silently swallows the moment.
     */
    private data class BotMove(
        val action: GameAction,
        val before: GameState,
        val after: GameState,
        val revealed: List<PublicReveal>,
    )

    private companion object {
        /** A guard, not a rule: a bot loop this long has stopped being a game. */
        const val MAX_BOT_STEPS = 200

        /** Room for a whole turn's worth of events before the oldest is dropped. */
        const val EVENT_BUFFER = 64

        /** Enough of the conversation for a strip that subscribes mid-round to make sense. */
        const val TALK_REPLAY = 16

        /**
         * The duration a solo window reports.
         *
         * Nominal: nothing counts it down, because nobody is being held — the caller is a bot.
         * A screen reads *non-null* as "the window is open"; the number is there because the
         * field is a duration and a screen may choose to draw one.
         */
        const val SOLO_CONFER_MS = 20_000L

        /** Enough to see a turn go by, not enough to become a transcript. */
        // A whole turn with its toss-in window, and the one before it — the rail scrolls now,
        // so it keeps enough to read back through what just happened.
        const val LOG_LENGTH = 24
    }
}
