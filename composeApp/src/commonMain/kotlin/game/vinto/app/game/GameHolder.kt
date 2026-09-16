package game.vinto.app.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import game.vinto.app.crash.Where
import game.vinto.app.theme.LocalFeedback
import game.vinto.client.Frame
import game.vinto.client.GameSession
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Table
import game.vinto.client.TossInAsk
import game.vinto.client.rehearsal
import game.vinto.client.tableFor
import game.vinto.client.tossInAsk
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.engine.tossInIsOpen
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.Lane
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.TableTalk
import game.vinto.shapes.laneOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * The screen's hold on one game.
 *
 * It owns three things and nothing else: the session, the question the screen is currently
 * putting to the player, and the last refusal. Everything a screen draws comes from
 * [table], which is a pure function of the view — so this class cannot get the game wrong,
 * only how long it holds on to it.
 *
 * Bots think on [Dispatchers.Default]: a moderate search is up to 1.6 s of work, and doing
 * that on the frame thread would freeze the table mid-animation.
 */
class GameHolder(
    // The interface, not the local session: the holder is the seam design R1 promises, and
    // typing it here is what makes an online game the same screens over a different session.
    private val session: GameSession,
    private val view: State<PlayerView>,
    /**
     * Seats a bot is covering, collected by the screen so a change repaints.
     *
     * A `State` for the same reason [view] is one: the session publishes a flow, and a table
     * that read `.value` would label the seat correctly once and then never again.
     */
    private val away: State<Set<String>> = mutableStateOf(emptySet()),
    /** The coalition's shared plan, as the session last had it; the board is drawn from it. */
    private val plan: State<CoalitionPlan?> = mutableStateOf(null),
    /** What the round has turned face up so far, which is what tells a plan its claim was wrong. */
    private val reveals: State<List<PublicReveal>> = mutableStateOf(emptyList()),
    /** What the rail opens on. [Question.None] for a person; the board for a store capture. */
    opening: Question = Question.None,
) {
    /** Recent moves, oldest first, for the strip under the prompt. */
    val log get() = session.log

    /**
     * What there is to see, for the stage to play: the session's frames, and the rehearsals
     * this screen asks for — ghosts of moves that have not happened, played through the same
     * choreography (design D8). Merged here so the stage has one flow, and so a solo game and
     * an online one rehearse the same way.
     */
    private val rehearsals = MutableSharedFlow<List<Frame>>(extraBufferCapacity = 1)
    val frames: Flow<List<Frame>> = merge(session.frames, rehearsals)

    var question: Question by mutableStateOf(opening)
        private set

    /** The last thing the engine refused, until the next move clears it. */
    var refusal: String? by mutableStateOf(null)
        private set

    /**
     * The suggestion standing for this seat, if one is.
     *
     * Newest wins and there is only ever one: a rail offering three people's suggestions at
     * once is a rail nobody reads, and the freshest is the one that knows most about the
     * position. Cleared when it is acted on, declined, or a move lands — a suggestion about a
     * table that has since moved is not a suggestion any more.
     */
    var offered: TableTalk.Proposal? by mutableStateOf(null)
        private set

    /**
     * Whether a move is on the wire and unanswered.
     *
     * Always false for a heartbeat in a local game, and worth drawing in a remote one: a tap
     * that reaches a room over a phone's network takes long enough that a table which does
     * nothing looks like a table that missed it.
     */
    var sending: Boolean by mutableStateOf(false)
        private set

    /**
     * The viewer's own turn of the plan as it was when they last had the plan open, so the
     * switch in the header can say a teammate has changed it since (`PlanSummary.changedBy`).
     * Kept in step for as long as the plan is open, and left alone while it is closed.
     */
    private var seen: Lane? by mutableStateOf(null)

    /**
     * The pile's top when the plan last opened.
     *
     * A toss-in window already open at that moment is the one the call itself opened — the
     * caller's last card, which the member has had the whole confer window to throw in on —
     * and it must not close the plan they asked for: "I'm ready" opened the plan and the
     * window's own view, delivered a beat later, shut it again (product owner). Only a card
     * landing *while* the plan is open steps it aside; see [noticed].
     */
    private var openedOn: String? = null

    /**
     * The toss-in window this seat has already answered, so the same one asked again can be
     * answered for them (see [tossInAsk]).
     */
    private var answered: TossInAsk? = null

    val playerId: String get() = session.playerId
    val current: PlayerView get() = view.value
    val table: Table
        get() = tableFor(view.value, question, away.value, offered, plan.value, reveals.value, seen)
    val isOver: Boolean get() = session.isOver

    /**
     * The table for a given view, which is not always the live one.
     *
     * While the bots' moves are being animated the screen is drawing a table a move or two in
     * the past (see `CardStage`), and it has to draw the *controls* from the same moment —
     * offering the buttons of a position the player cannot see yet is how a game gets played
     * by accident.
     */
    fun tableFor(view: PlayerView): Table =
        tableFor(view, question, away.value, offered, plan.value, reveals.value, seen)

    /**
     * The table as a SCREEN should draw it: [tableFor] the view on the felt, minus the taps that
     * would land on a hand the engine has already changed.
     *
     * One function because the two screens had drifted. Solo applied [withoutStaleOffers] and the
     * room did not — and the room is the worse place to leave it out: it applies whatever index
     * arrives, `ClientMessage.Action` carries no id to dedupe by, and the validator never checks
     * whether this seat has already thrown. `withoutStaleOffers`'s own KDoc names that case ("which,
     * online, is the moment two people throw at once") and it was never wired there.
     */

    /**
     * @param drawn how far the *screen* has got, which is not always where [shown] is: between
     *   the two batches a dispatch emits, the stage has let go of its lag and is drawing the
     *   live table while the bots' turns are still to come. Null before anything is animated.
     */

    /**
     * @param rehearsing whether [shown] is a ghost frame's table — the plan's film playing.
     *   A board rebuilt from one would replay the plan on top of its own result, so the rail
     *   keeps reading the live table while the film plays; the felt follows the frames on its
     *   own (`FeltTable`).
     */
    fun tableAsShown(shown: PlayerView, drawn: PlayerView? = null, rehearsing: Boolean = false): Table {
        val basis = if (rehearsing) current else shown
        return tableFor(basis).withoutStaleOffers(basis, drawn ?: basis, current)
    }

    /** One sentence off the channel, for the holder to keep if it is addressed here. */
    fun heard(talk: TableTalk) {
        if (talk is TableTalk.Proposal && talk.to == session.playerId) offered = talk
    }

    /**
     * The round has moved under an open plan: the plan steps aside when the round needs this
     * seat *now*.
     *
     * Plan mode and the round's controls are never on screen together (design D9), which is
     * the right rule and has one cost: a card lands that this seat could throw in on, and the
     * plan is hiding the button for it — a toss-in window closes on a clock, and a member who
     * said "then I throw in my Queen" is reading the sentence while the Queen goes by. So the
     * plan closes for exactly that moment, and is one switch away again afterwards, parked
     * where it was.
     *
     * **Not for the turn coming round.** It used to close then too, and the turn on play is
     * the one turn the plan most needs to be open for: the card just drawn is the news the
     * plan turns on, and a member reading their own turn had it snatched away as it became
     * theirs. The turn's buttons are one switch away, the same as the plan is.
     */
    fun noticed(view: PlayerView) {
        if (!question.isPlanning) return
        val me = session.playerId
        val myThrow = view.tossInIsOpen && view.vintoCallerId != me && me !in view.barredFromTossIn
        val landedSince = view.discardTop?.id != openedOn
        if (myThrow && landedSince) question = Question.None
    }

    /**
     * Gives this seat's answer again when the window comes back unchanged.
     *
     * Reported from a phone: *"I again see continue button I need to press, why? Rank list did
     * not change and I already responded that no tossin from me."* The window really does
     * reopen — each card thrown into it is played, and each one landing puts the sub-phase back
     * and clears the ready list — and the engine cannot stop doing that: the ready list is
     * inside the canonical hash and leaving a seat marked diverges 48 of the 50 parity
     * recordings. So the answer is given again from here, which is a press saved rather than a
     * rule moved: the same `PLAYER_TOSS_IN_FINISHED` reaches the engine either way.
     *
     * Only when [tossInAsk] says the question is the one that was answered — same ranks, same
     * hand as this seat can see it — and never in the window this seat owns, which [tossInAsk]
     * refuses for the Vinto call's sake.
     */
    suspend fun answerAgain(view: PlayerView) {
        val asking = tossInAsk(view)
        if (asking == null || asking != answered) return
        act(Move.Send(GameAction.PlayerTossInFinished(PlayerIdPayload(view.viewerId))))
    }

    /** Puts the screen at [next], remembering the pile's top if this is the plan opening. */
    private fun ask(next: Question) {
        if (next.isPlanning && !question.isPlanning) openedOn = session.view.value.discardTop?.id
        question = next
    }

    /** The plan has changed: while it is open the viewer is looking at it, so what they have seen moves. */
    fun sawPlan(standing: CoalitionPlan?) {
        if (question.isPlanning) seen = standing?.laneOf(session.playerId)
    }

    /**
     * Acts on whatever the player touched.
     *
     * A question is answered here and goes no further; a move goes to the engine and, if it
     * lands, wipes the question — the screen's half-finished thought is finished.
     */

    /**
     * The plan's film, when the transport has been asked to run.
     *
     * The frames are the plan's own, from where the head is parked; the screen parks it at the
     * end once the felt has finished drawing them (`PlanRunner`), because only the animation
     * knows how long that takes — it depends on the pace dial, on reduced motion, and on how
     * much each turn actually moves. Nothing leaves the phone: these are ghosts off the view
     * this seat already holds.
     */
    private suspend fun film(focus: Question.ThePlan) {
        val target = focus.runningTo ?: return
        val standing = plan.value ?: return
        // Pages, not positions: page k starts from the table after k − 1 turns, so the film
        // from page k to page t plays turns k to t − 1 — and to the last page, every turn to
        // the end. A replay is the one film whose destination is its own page: turn k alone.
        val film = rehearsal(view.value, standing)
        rehearsals.emit(film.between(focus.at - 1, minOf(target, film.turns)))
    }

    suspend fun act(move: Move) {
        when (move) {
            is Move.Ask -> {
                ask(move.question)
                refusal = null

                // Starting the plan's film is the one question that also has something to play.
                (move.question as? Question.ThePlan)?.let { film(it) }
            }

            // One at a time, and the second tap is dropped rather than queued. Locally this
            // never mattered — the reducer answers in the same frame — but a remote session
            // holds a *single* waiter for the answer it is expecting, so a second move sent
            // while the first is in flight replaces that waiter and the first hangs until it
            // times out. The player sees their own first move stall because they hurried it.
            // Talk is not held behind `sending`. Nothing waits on a sentence — the room's
            // answer to one is the broadcast — so making it queue behind a move in flight
            // would mean a player who wanted to say "wait" had to wait first.
            // Not held behind `sending` either: ending your share of a window is not a move,
            // and a player who has finished talking should not wait on one.
            // **And the plan opens.** "I'm ready" ends the talking, and the plan is what the
            // talking was for: it is the coalition's table talk made visible, and a member who
            // has finished saying what they know is looking for where to say what to do with
            // it. It used to land them on the live table with the switch one tap away, and the
            // tap was the one nobody found (product owner). Opened where there is something
            // to do — their own turn while it can still be built, else the first that can.
            // **The press the round waits on, and the one that gets out of the way for it.**
            //
            // It used to open the plan afterwards, and that is what was reported from a phone on
            // 2026-09-16: the turns ran, and the player was then put on the board — where the
            // felt draws the plan's table rather than the live one, so a card the plan has yet
            // to draw came up rose a beat after a real one had been dealt. Whoever pressed this
            // wants to watch what they started.
            //
            // Only when it actually started something. Online the window holds until every
            // human coalition member has pressed, so a press that leaves it open leaves the
            // player on the plan — there is still something to build, and nothing yet to watch.
            is Move.Done -> {
                refusal = session.doneConferring()
                if (refusal == null && session.view.value.conferMsRemaining == null) {
                    question = Question.None
                }
            }

            is Move.Say -> {
                refusal = session.say(move.talk)
                if (refusal == null) {
                    question = Question.None
                    offered = null
                }
            }

            // Planning is talk-shaped: nothing waits on it and the answer is the board coming
            // back, so neither is held behind `sending` either.
            //
            // **And the plan stays open.** An edit wiped the question, which closed the plan:
            // every answer to the rail — which pile, what becomes of the card, which card goes
            // out — dropped the player onto the live table, and the next answer meant finding
            // the switch, the stop and the turn again. Building a turn is three or four edits
            // in a row. Reported from a phone as not being able to work out how to build a plan
            // at all, which is exactly what it was.
            is Move.Plan -> {
                refusal = session.editPlan(move.edit)
                if (refusal == null) question = question.backOnThePlan()
            }

            // Agreeing is the end of talking, not the end of reading.
            is Move.Agree -> {
                refusal = session.agreePlan(move.agree)
                if (refusal == null) question = question.backOnThePlan()
            }

            is Move.Send -> {
                send(move.action)
            }
        }
        sawPlan(plan.value)
    }

    /**
     * One move to the engine, with the in-flight guard around it.
     *
     * @see answerAgain for why a toss-in answer is remembered here rather than read back
     *   afterwards.
     */
    private suspend fun send(action: GameAction) {
        if (sending) return
        sending = true
        try {
            // **Read before the dispatch.** The window this seat is answering is the one
            // standing now, and the dispatch is what marks them ready in it — so asking
            // afterwards answers null every time, and nothing would ever be remembered.
            val answering = (action as? GameAction.PlayerTossInFinished)?.let { tossInAsk(view.value) }
            refusal = session.dispatch(action)
            if (refusal == null) {
                if (action is GameAction.PlayerTossInFinished) answered = answering
                question = Question.None
                offered = null
            }
        } finally {
            sending = false
        }
    }
}

/**
 * Where the screen is once a plan edit has landed: the plan, parked where the edit was made.
 *
 * A chooser's question has been answered, so the chooser closes onto the plan at its own stop;
 * a card picked up is put down by the edit it made; and anything else — an edit made from no
 * plan at all — leaves the screen asking nothing, as before.
 */
private fun Question.backOnThePlan(): Question = when (this) {
    is Question.ThePlan -> copy(picked = null)
    is Question.Doing -> Question.ThePlan(at = at)
    is Question.PuttingDown -> Question.ThePlan(at = at)
    is Question.Naming -> Question.ThePlan(at = at)
    is Question.Aiming -> Question.ThePlan(at = at)
    is Question.Forcing -> Question.ThePlan(at = at)
    is Question.Throwing -> Question.ThePlan(at = at)
    Question.None, Question.WhichSlot, is Question.CallRank, is Question.Claiming -> Question.None
}

/** Whether the screen is somewhere in the plan: reading it, or answering a question about it. */
private val Question.isPlanning: Boolean
    get() = when (this) {
        is Question.ThePlan, is Question.Doing, is Question.PuttingDown, is Question.Naming,
        is Question.Aiming, is Question.Forcing, is Question.Throwing,
        -> true
        Question.None, Question.WhichSlot, is Question.CallRank, is Question.Claiming -> false
    }

/** A holder for one round, rebuilt when the round is. */
@Composable
fun rememberHolder(session: GameSession, opening: Question = Question.None): GameHolder {
    val view = session.view.collectAsState()
    val away = session.away.collectAsState()
    val plan = session.plan.collectAsState()
    val reveals = session.reveals.collectAsState()

    // The one place a local game and an online one both pass through, which is why the crash
    // reporter's address is written here rather than in each table screen. Cleared on the way
    // out, so a crash in the menu is not filed against the game before it.
    DisposableEffect(session) {
        onDispose { Where.atTable(null) }
    }
    Where.atTable(view.value)

    val holder = remember(session) { GameHolder(session, view, away, plan, reveals, opening) }

    // The one place the talk channel becomes something a player can act on. A suggestion
    // addressed to this seat becomes the one-tap move at the top of the rail; everything else
    // is already in the strip, because the session puts it there.
    LaunchedEffect(session) {
        session.talk.collect { holder.heard(it) }
    }
    LaunchedEffect(session) {
        session.view.collect {
            holder.noticed(it)
            holder.answerAgain(it)
        }
    }
    LaunchedEffect(session) {
        session.plan.collect { holder.sawPlan(it) }
    }

    return holder
}

/**
 * Dispatches [move] from the composition, on a scope tied to the screen.
 *
 * @param onEachMove run after every move lands. The game is written down here rather than on
 *   a timer or at the end: a card game that loses your round because the phone rang is one
 *   you do not open again, and a round is a few kilobytes.
 */
@Composable
fun rememberActor(
    holder: GameHolder,
    onEachMove: () -> Unit = {},
    /**
     * A capture that opens on the board plays the plan through once, so a recording of that
     * screen has the app's own cards moving on it rather than a still. Only a store capture
     * passes an opening question, so nothing a player does reaches this.
     */
    rehearseFor: Question = Question.None,
): (Move) -> Unit {
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current

    LaunchedEffect(rehearseFor) {
        if (rehearseFor is Question.ThePlan) {
            delay(REHEARSE_AFTER_MS)
            // The transport's own play-all, so a capture records the plan exactly as a player
            // watches it — one way to play the film, not a second one kept for the camera.
            holder.table.board?.transport?.playAll?.let { holder.act(it) }
        }
    }

    return remember(holder, scope, feedback) {
        {
                move ->
            scope.launch {
                holder.act(move)
                // A refusal is a rule the player has not met yet, and it arrives as a line of
                // small text in a panel they are not looking at. The phone says it too.
                if (holder.refusal != null) feedback.refuse()
                onEachMove()
            }
        }
    }
}

/**
 * The table, minus everything it offers that belongs to a position the engine has left.
 *
 * The screen draws the table as it was after the move being animated, and everything the rail
 * offers is built from that same picture. Two different things go wrong with that while the
 * picture lags, and both were reported from a phone.
 *
 * **The taps.** A tap on "card 3" names card 3 *as shown*, and the engine reads card 3 *as it
 * is now*: a hand that lost a card has slid, and a second tap on the same slot threw a
 * different card, which cost a penalty. Only the viewer's own hand is checked and only their
 * own taps are withheld — another seat's throw changes nothing about where this player's cards
 * are, so their window stays open while the felt catches up, which online is the moment two
 * people throw at once.
 *
 * **The buttons.** A position the screen has not reached can still be a position with something
 * to press:
 * a toss-in window the player has already closed replays with its button, so a control the
 * player was finished with reappears for as long as it takes the next card to move, then goes
 * again. Nobody touched anything and the app changed its mind twice. So a table drawn from a
 * position the live game is no longer in offers nothing that would *act* on the game —
 * [Move.Send] and nothing else, because a question the screen is asking itself is about the
 * screen rather than about the round. `ControlBlinkTest` is the report.
 */
internal fun Table.withoutStaleOffers(shown: PlayerView, drawn: PlayerView, live: PlayerView): Table {
    var table = this
    if (!shown.showsTheSameHandAs(live)) {
        table = table.copy(taps = table.taps.filterKeys { it.playerId != shown.viewerId })
    }
    // Against what has been **drawn**, not against what is being shown. When the stage has let
    // go of its lag the two are the same, and asking `shown` could never answer — it *is* the
    // live view. What has been drawn is the honest measure of how far the player has been
    // shown, and between a dispatch's two batches it is a whole turn short.
    if (!drawn.offersTheSameMovesAs(live)) {
        table = table.copy(
            choices = table.choices.filterNot { it.move is Move.Send },
            ranks = table.ranks.filterNot { it.move is Move.Send },
            seats = table.seats.filterNot { it.move is Move.Send },
            taps = table.taps.filterValues { it !is Move.Send },
        )
    }
    return table
}

/**
 * Whether the position on screen still allows what the live one allows.
 *
 * Not "are these views equal" — they differ constantly and harmlessly, by a card's position in
 * a pile or a count on the deck. Two things decide whether a *control* is still real: **whose
 * turn** the picture is of, and **which window** is open in it. Both are the difference between
 * a control the player may still use and one belonging to a position they have been carried
 * past.
 *
 * The sub-phase is deliberately **not** among them, and that is not an oversight. Your own turn
 * walks through several of them — idle, drawing, choosing — and comparing it withheld the
 * buttons of your own move for as long as its card took to fly, which is a second of a dead
 * rail nobody asked for. `TableUiTest` is what said so: it presses Draw and expects Discard.
 */
internal fun PlayerView.offersTheSameMovesAs(live: PlayerView): Boolean =
    currentPlayerIndex == live.currentPlayerIndex &&
        activeTossIn?.ranks == live.activeTossIn?.ranks

/**
 * Whether the hand drawn on screen is still the hand the engine holds.
 *
 * The one question two views of the same seat can be asked while the table is a move behind,
 * and the answer decides everything addressed *by position*: which card a tap names, and
 * which slot a coach's finger is over. False means the screen is drawing a hand that has
 * since gained or lost a card, so every position after the change points at its neighbour.
 */
internal fun PlayerView.showsTheSameHandAs(live: PlayerView): Boolean {
    val mine = players.firstOrNull { it.id == viewerId }?.cards
    val theirs = live.players.firstOrNull { it.id == viewerId }?.cards
    return mine == theirs
}

/** Long enough for the board to have drawn itself before the ghosts start moving over it. */
private const val REHEARSE_AFTER_MS = 1_500L
