package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.hasAction
import game.vinto.shapes.laneOf

/**
 * The coalition's shared plan, as the rail draws it and as a person edits it (design D7a).
 *
 * Three things live here, and they are one subject: the **board** — one turn per coalition
 * seat, who has nodded, and where the plan lands; the **page** — the turn being read and built,
 * as a sentence whose decisions can be touched (`PlanSentence`); and the **answers** — what the
 * rail offers under the sentence while a word is being asked for.
 *
 * Everything is built from the view and the standing plan alone. Which edits are *legal* is
 * `CoalitionPlan.edited`'s business, the one door both sessions call; what this decides is only
 * which of them to offer, so a turn the door would refuse — one already played, or the caller's
 * view — is simply not touchable rather than touchable and refused.
 *
 * **The plan is talk, not a control.** Nothing here reaches the engine: every move is a
 * [Move.Quiet], and the live table keeps its ordinary buttons whatever the plan says.
 */

/** The board: what the coalition intends, laid out for reading and for tapping. */
data class Board(
    /** One per coalition turn, in the order they come. */
    val lanes: List<LaneLine>,
    /** The page on screen: a turn, or — past the last turn — where the plan lands. */
    val at: Int = 1,
    /** True while the transport is running, where nothing may be edited (design D14). */
    val running: Boolean = false,
    /** Every coalition member, and whether they have said yes to the board as it stands. */
    val nods: List<Nod>,
    val editedBy: Speaker?,
    /** Where the plan would leave the round, from standing claims alone (design D8). */
    val outcome: PlanOutcome? = null,
    /** The turn being read and built, as a sentence. Null on the last page, where the arrival is read. */
    val sentence: TurnSentence? = null,
    /**
     * Every turn as a sentence, one per page, so the pager can show the neighbours of the page
     * on screen mid-swipe. The one at [at] is [sentence]; the rest are read against the table
     * their own turn starts from, with no question open on them.
     */
    val pages: List<TurnSentence> = emptyList(),
    /** Moving through the plan (design D14). See [Transport]. */
    val transport: Transport = Transport(1, 0, null, emptyList(), null),
    /**
     * The table the felt draws while the plan is open: the one the page's turn starts from —
     * or, once the turn's film has been watched, the one it leaves; on the last page, where
     * the plan lands. Built by transforming the view and never by reducing (design R1).
     */
    val felt: PlayerView? = null,
    /** The card a select-then-select edit has picked up, if one has (design D5). */
    val picked: CardRef? = null,
    /** The cards the turn being read names, at the seats that hold them. */
    val marks: Set<CardRef> = emptySet(),
    /**
     * The cards on the felt that are **not on the table yet** — drawn or dealt by the plan —
     * each tagged with the turn it arrives on. Rose on the felt and in the sentence: nobody
     * reading turn ③ has to remember what turn ① drew.
     */
    val fresh: Map<CardRef, Int> = emptyMap(),
    /** The turn since which the pile's top is a card nobody knows, or null while it is known. */
    val pileUnknown: Int? = null,
    /**
     * The turn whose own draw is in front of its seat on the felt, or null.
     *
     * Every turn begins with a card nobody has seen, and a page that left it out drew a seat
     * about to do something with nothing. Rose and tagged like every other card the plan deals
     * rather than finds, so "and we'll see" is a card on the table instead of an absence.
     */
    val drawing: Int? = null,
    /** The table the turn being built starts from, which is what its open questions are asked of. */
    val startsFrom: PlayerView? = null,
    /** The answers row: the alternatives for the word being asked, or nothing. */
    val answers: List<Choice> = emptyList(),
    /** Of the cards the felt offers, the ones the table can vouch for — a throw of one of these matches. */
    val wanted: Set<CardRef> = emptySet(),
    /**
     * One per turn: whether the film has anything to draw for it.
     *
     * A turn nobody has decided still has one — the seat draws a card nobody knows, which is a
     * picture. What has none is a turn that cannot be drawn at all: one naming a card that has
     * gone. The runner that parks the head has to know which turn's cards are the last to land.
     */
    val watchable: List<Boolean> = emptyList(),
) {
    /** The lane being built: the one the page names, and the only one with a composer. */
    val building: LaneLine? get() = lanes.firstOrNull { it.composer != null }
}

/**
 * The plan in one line, for the tables that are about something else: how much of the board
 * is set, how many have nodded, and one tap to open it.
 */
data class PlanSummary(
    val lanesSet: Int,
    val lanes: Int,
    val agreed: Int,
    /** Whether the viewer has nodded; false for the caller, who never does. */
    val mine: Boolean,
    /** Where the board opens — see [openingStop]. [open] is this, as the switch presses it. */
    val opens: Question.ThePlan,
    /** Opening the board. Null for nobody: the caller may read a standing plan too. */
    val open: Move = Move.Ask(opens),
    val outcome: PlanOutcome? = null,
    /** Which seats have said yes to the plan as it stands, and which have a throw-in planned. */
    val nodded: Set<String> = emptySet(),
    val shedding: Set<String> = emptySet(),
    /** Who last changed the plan. */
    val editedBy: Speaker? = null,
    /**
     * Who changed the viewer's own turn since they last looked at it, or null.
     *
     * What the switch in the header wears while the plan is closed: a teammate rewrote your
     * turn — most often round the card you have just drawn — and the news must not wait for
     * you to open the plan on the off chance.
     */
    val changedBy: Speaker? = null,
)

/**
 * One coalition member's turn on the board.
 *
 * Whether this turn may be *changed* is [composer] being present, and it is absent wherever
 * the door would refuse an edit: the turn has been played, or the viewer is the caller.
 */
data class LaneLine(
    val who: Speaker,
    val step: StepLine?,
    val locked: Boolean,
    /** How the step is bearing up (design D9). */
    val health: StepHealth = StepHealth.LIVE,
    /** What the lane's own seat would rather do (3.13), offered beside the step, not over it. */
    val suggestion: StepLine? = null,
    val useSuggestion: Move? = null,
    /** Which turn this is, as people count: ① ② ③. */
    val number: Int = 0,
    /**
     * Whether the step can be drawn at all — false where it names a card that is not there, or
     * aims a card nobody has seen.
     */
    val playable: Boolean = true,
    /** How this turn is edited on the felt, when it is the one being composed (design D5). */
    val composer: PlanComposer? = null,
)

/**
 * A step in words a renderer can put into a sentence. Positions are one-based, as people count.
 *
 * Kept for the places a whole step is said at once — a lane owner's alternative, the log.
 */
sealed interface StepLine {
    data class Swap(val fromWho: Speaker, val fromSlot: Int, val toWho: Speaker, val toSlot: Int) : StepLine

    /** Declare [rank] with a King — pointing at [who]'s card [slot] where the plan names one. */
    data class Declare(
        val rank: Rank?,
        val who: Speaker? = null,
        val slot: Int? = null,
        val then: StepLine? = null,
    ) : StepLine
    data object TakeTheDiscard : StepLine

    /**
     * Put [who]'s card [slot] on the pile; [rank] when the table knows what it is; [call] the
     * rank it is called as.
     */
    data class PutDown(
        val who: Speaker,
        val slot: Int,
        val rank: Rank?,
        val call: Rank? = null,
        val then: StepLine? = null,
    ) : StepLine

    /** Let the drawn card go and leave the hand alone. */
    data object Bin : StepLine

    /** Use whatever the card turns out to be, for its action. */
    data object UseIt : StepLine

    /** Look at [who]'s card [slot] — and, for a Queen, at [alsoWho]'s card [alsoSlot]. */
    data class Peek(
        val who: Speaker,
        val slot: Int,
        val alsoWho: Speaker? = null,
        val alsoSlot: Int? = null,
    ) : StepLine

    /** Make [who] draw a card. */
    data class ForceDraw(val who: Speaker) : StepLine
}

/** One member's yes or not-yet. [away] because a seat a bot is covering nods for itself. */
data class Nod(val who: Speaker, val agreed: Boolean, val away: Boolean)

// ---------------------------------------------------------------------------- the board

/**
 * The board for [view]'s seat, or null when there is nothing to draw.
 *
 * A coalition member sees it for the whole final round, empty lanes included — the empty
 * lane is what you touch to start planning. The caller sees it only once something stands on
 * it, and can touch nothing: talk is public (design D12) and the plan is built from public
 * claims, so there is nothing to hide, and nothing for them to change.
 */
internal fun boardFor(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal> = emptyList(),
    focus: Question.ThePlan = Question.ThePlan(),
    asking: Asking? = null,
    question: Question? = null,
): Board? = compose(view, plan, away, reveals, focus, asking, question)?.board()

/** Everything the board is built from, or null when there is no board to draw. See [Composing]. */
@Suppress("LongParameterList")
internal fun compose(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal>,
    focus: Question.ThePlan,
    asking: Asking? = null,
    question: Question? = null,
): Composing? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null
    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    return Composing(view, plan, away, reveals, focus, asking, question, coalition, member)
}

/**
 * Everything the board is built from, worked out once.
 *
 * A class rather than one long function, because the board has a dozen parts and each is a
 * few lines: which page is on screen, which table that page's turn starts from, whether it may
 * be changed, what it still needs answered.
 */
@Suppress("LongParameterList")
internal class Composing(
    val view: PlayerView,
    val plan: CoalitionPlan?,
    val away: Set<String>,
    reveals: List<PublicReveal>,
    val focus: Question.ThePlan,
    val asking: Asking?,
    val question: Question?,
    val coalition: List<String>,
    val member: Boolean,
) {
    val onPlay: String? = view.players.getOrNull(view.currentPlayerIndex)?.id

    /** Which coalition turn is being played; -1 while play is still parked on the caller. */
    val playing: Int = coalition.indexOf(onPlay)

    // Read against the table as it is now: a step follows its card in silence, and one whose
    // claim a reveal contradicted is marked rather than repaired (design D9).
    val reading: PlanReading? = plan?.let { readPlan(view, it, reveals) }

    val turns: Int = coalition.size
    val pages: Int = turns + 1

    /**
     * One per turn: whether it is settled. Throw-ins are not a decision.
     *
     * A turn already played is settled by having happened — the table it leaves is the table as
     * it is — so it never closes the pages after it, whatever the plan did or did not say about
     * it before it was taken.
     *
     * **Which includes the turn on play, once its card is down.** `index < playing` is every turn
     * the table has moved *past*, and a seat keeps `playing` for the whole of its turn — so a seat
     * that had drawn, swapped and opened a toss-in window still counted as undecided, and an
     * undecided turn is where the reading stops. Every stop clamped back to it and drew the same
     * table, and the reader could reach neither the next turn nor their own. Reported from a phone
     * twice over, as "tide started his turn without me" and as the same nine on the next turn.
     *
     * [Lane.locked] is the answer to "has this turn happened", set by `CoalitionPlan.lockingLaneOf`
     * from the engine's own `turnIsSpent`, so this reads it rather than deciding it a second time.
     */
    val decided: List<Boolean> = coalition.mapIndexed { index, seat ->
        val lane = plan?.laneOf(seat)
        index < playing || lane?.locked == true || lane?.step != null
    }

    /** How far the plan reads — see `Transport.reach`. */
    val reach: Int = (0 until turns).firstOrNull { !decided[it] }?.plus(1) ?: pages

    // The page, clamped to the pages the plan reaches: one per decided turn, the first
    // undecided one, and — once none is left open — where the plan lands.
    val at: Int = focus.at.coerceIn(1, reach)
    val lands: Boolean = at == pages

    /** The turn being read, zero-based; -1 on the last page. */
    val turn: Int = if (lands) -1 else at - 1

    val film: Rehearsal? = plan?.let { rehearsal(view, it) }

    val seat: String? = coalition.getOrNull(turn)
    val lane: Lane? = seat?.let { plan?.laneOf(it) }

    val start: Start = startFor(turn.coerceAtLeast(0))
    val there: PlayerView = start.view

    /**
     * Whether the turn on screen may be changed: by a member, not while the film runs, and not
     * once it has been played.
     */
    val editable: Boolean =
        member && !focus.running && seat != null && lane?.locked != true && turn >= playing

    // A card picked up, a question open, or the felt answering: the page is being built.
    val building: Boolean = asking != null || focus.picked != null || question != null

    /** What the turn still needs answered, asked of the table it starts from. */
    val next: NextPart? = seat?.let { nextPart(start, lane) }
    val drawable: List<Boolean> = film?.frames?.map { it != null } ?: List(turns) { false }

    /** The table [turn] starts from, with what the plan has dealt and drawn by then. */
    fun startFor(index: Int): Start {
        val there = film?.tables?.getOrNull(index) ?: view
        return startOf(
            there,
            coalition.getOrElse(index) { "" },
            film?.pileUnknown?.getOrNull(index),
            film?.fresh?.getOrNull(index).orEmpty(),
        )
    }

    fun board(): Board {
        val lanes = lanes()
        val position = feltPosition()
        val table = film?.tables?.getOrNull(position) ?: view
        // The card the page's turn draws, where it has not been drawn for real already.
        val blind = seat?.takeIf { position == turn && table.pendingAction == null }
        val composer = lanes.firstOrNull { it.composer != null }?.composer
        val transport = transport()
        val pages = coalition.indices.map { index -> sentenceFor(index, lanes, transport) }
        return Board(
            at = at,
            running = focus.running,
            transport = transport,
            lanes = lanes,
            nods = coalition.map { id ->
                Nod(speakerFor(view, id), agreed = id in plan?.agreed.orEmpty(), away = id in away)
            },
            editedBy = plan?.editedBy?.let { speakerFor(view, it) },
            outcome = plan?.let { planOutcome(view, it) },
            felt = blind?.let { table.drawing(it) } ?: table,
            drawing = (turn + 1).takeIf { blind != null },
            picked = focus.picked,
            marks = if (lands) emptySet() else lane?.let { cardsNamedBy(there, it) }.orEmpty(),
            fresh = film?.fresh?.getOrNull(position).orEmpty(),
            pileUnknown = film?.pileUnknown?.getOrNull(position),
            startsFrom = there.takeIf { seat != null },
            sentence = pages.getOrNull(turn),
            pages = pages,
            wanted = composer?.wanted.orEmpty(),
            watchable = drawable,
        )
    }

    /**
     * The page shows the table its turn starts from — the one its cards are named against and
     * touched on — and, once its film has been watched, the table it leaves; the last page
     * shows where the plan lands.
     */
    private fun feltPosition(): Int = when {
        lands -> turns
        focus.landed && !building -> turn + 1
        else -> turn
    }

    private fun lanes(): List<LaneLine> {
        // What the felt answers: the question open on the page, or else the next open part of
        // the turn — which two cards, which card to look at, which card the King points at.
        val wanted = asking ?: next?.let { askingFor(it) }
        return coalition.mapIndexed { index, id ->
            val editableHere = member && !focus.running && index >= playing
            val composing = index == turn && editableHere && lane?.locked != true
            laneLine(view, plan, reading, id, index, startFor(index), editableHere, composing, wanted)
        }
    }

    // Read off the same rehearsal the film is made from, so what the transport promises is
    // watchable is exactly what plays.
    private fun transport(): Transport = transportFor(
        focus,
        seats = coalition.map { speakerFor(view, it) },
        drawable = drawable,
        names = coalition.map { id -> view.players.firstOrNull { it.id == id }?.nickname },
        decided = decided,
    )

    /**
     * The [index]th turn as a sentence. The page on screen carries the open question and what
     * the felt is answering; every other page is read whole, against its own start table, so
     * that a swipe reveals the neighbouring turn as it is rather than a blank.
     */
    private fun sentenceFor(index: Int, lanes: List<LaneLine>, transport: Transport): TurnSentence {
        val id = coalition[index]
        val line = lanes[index]
        val here = index == turn
        val laneThere = if (here) lane else plan?.laneOf(id)
        val startThere = if (here) start else startFor(index)
        val editableThere = if (here) {
            editable
        } else {
            member && !focus.running && laneThere?.locked != true && index >= playing
        }
        val words = Words(
            view,
            startThere,
            id,
            laneThere,
            index + 1,
            question.takeIf { here },
            asking.takeIf { here },
            if (here) next else nextPart(startThere, laneThere),
            editableThere,
        )
        return TurnSentence(
            who = line.who,
            nickname = view.players.firstOrNull { it.id == id }?.nickname,
            number = line.number,
            clauses = words.clauses(),
            replay = transport.stops.getOrNull(index)?.replay,
            health = line.health,
            playable = line.playable,
            suggestion = line.suggestion,
            useSuggestion = line.useSuggestion,
        )
    }
}

/** What the felt answers for the next open part of the turn, where the felt is where it is answered. */
private fun askingFor(next: NextPart): Asking? = when (next) {
    is NextPart.Targets -> Asking.Trade(next.part)
    is NextPart.Look -> Asking.Look(next.part, next.own)
    is NextPart.Point -> Asking.Point(next.part)
    is NextPart.KingsRank, is NextPart.Victim, NextPart.Done -> null
}

/** One coalition turn, as the plan draws it and as a person edits it. */
@Suppress("LongParameterList")
private fun laneLine(
    view: PlayerView,
    plan: CoalitionPlan?,
    reading: PlanReading?,
    seat: String,
    index: Int,
    start: Start,
    editable: Boolean,
    composing: Boolean,
    asking: Asking?,
): LaneLine {
    val lane = plan?.laneOf(seat)
    val at = reading?.plan?.lanes?.indexOfFirst { it.seat == seat } ?: -1
    val followed = reading?.plan?.lanes?.getOrNull(at) ?: lane
    val locked = lane?.locked == true

    return LaneLine(
        who = speakerFor(view, seat),
        step = followed?.step?.let { stepLine(view, it) },
        locked = locked,
        health = reading?.health?.getOrNull(at) ?: StepHealth.LIVE,
        // Only on the turn being read (design D8).
        suggestion = lane?.suggestion?.takeIf { composing }?.let { stepLine(view, it) },
        useSuggestion = lane?.suggestion
            ?.takeIf { editable && composing }
            ?.let { Move.Plan(PlanEdit.SetLane(seat, it)) },
        number = index + 1,
        playable = followed?.let { drawable(start, it) } ?: true,
        composer = composerFor(start, seat, lane, asking).takeIf { composing },
    )
}

/**
 * The one line every final-round table carries, or null where there is no board to open.
 *
 * @param seen the viewer's own lane as it was when they last had the plan open, so the summary
 *   can say whether a teammate has changed it since.
 */
internal fun summaryFor(view: PlayerView, plan: CoalitionPlan?, seen: Lane? = null): PlanSummary? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null

    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    val mine = plan?.laneOf(view.viewerId)
    val editor = plan?.editedBy
    return PlanSummary(
        lanesSet = plan?.lanes.orEmpty().count { it.step != null },
        lanes = coalition.size,
        agreed = plan?.agreed.orEmpty().count { it in coalition },
        mine = view.viewerId in plan?.agreed.orEmpty(),
        opens = Question.ThePlan(at = openingStop(view, plan)),
        outcome = plan?.takeUnless { it.isEmpty }?.let { planOutcome(view, it) },
        editedBy = editor?.let { speakerFor(view, it) },
        changedBy = editor
            ?.takeIf { it != view.viewerId && mine != null && mine != seen }
            ?.let { speakerFor(view, it) },
        nodded = plan?.agreed.orEmpty().filter { it in coalition }.toSet(),
        shedding = (
            plan?.sheds.orEmpty().map { it.seat } +
                plan?.lanes.orEmpty().flatMap { lane -> lane.tossIns.map { it.seat } }
            ).filter { it in coalition }.toSet(),
    )
}

/**
 * Where the plan opens: the viewer's own turn while it can still be built, else the first turn
 * that can, else where the plan lands — and for the caller, who has no turn and came to watch,
 * the first turn.
 *
 * A turn can be built while it has not been played: the one on play included, because its
 * drawn card is the news the plan turns on.
 */
internal fun openingStop(view: PlayerView, plan: CoalitionPlan?): Int {
    val caller = view.vintoCallerId ?: return 1
    if (view.viewerId == caller) return 1
    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    val onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id
    val playing = coalition.indexOf(onPlay)
    fun buildable(turn: Int) = turn >= playing && plan?.laneOf(coalition[turn])?.locked != true
    val mine = coalition.indexOf(view.viewerId).takeIf { it >= 0 && buildable(it) }
    val turn = mine ?: coalition.indices.firstOrNull { buildable(it) } ?: coalition.size
    return turn + 1
}

// ---------------------------------------------------------------------------- the tables

/**
 * The board, open: the turn as a sentence, the answers row, the transport, the way to agree.
 * The switch in the header closes it.
 */
internal fun boardTable(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal> = emptyList(),
    focus: Question.ThePlan = Question.ThePlan(),
): Table {
    val composing = compose(view, plan, away, reveals, focus) ?: return Table(Ask.Watching, waiting = true)
    val board = composing.board()
    val member = composing.member
    val agreeable = member && plan != null && !plan.isEmpty && view.viewerId !in plan.agreed
    val seat = board.building?.composer?.seat
    val next = composing.next.takeIf { seat != null }

    return Table(
        prompt = promptFor(composing),
        detail = hintFor(composing, board),
        // Agree is the one standing button (design D17); everything else is a word.
        choices = if (agreeable) listOf(Choice(Label.Agree, Move.Agree(true), Tone.PLAY)) else emptyList(),
        taps = feltTaps(board, focus),
        // Every coalition seat is a way to its own turn — or, when an Ace is asking who draws,
        // the answer. Nothing is a control while the film runs (design D14), or for the caller.
        seats = when {
            !member || board.running -> emptyList()
            next is NextPart.Victim && seat != null -> victims(view, seat, composing.lane, next.part)
            else -> waysToTurns(view, focus)
        },
        board = board.copy(answers = if (composing.editable) kingsRankAnswers(composing) else emptyList()),
    )
}

/** What the rail asks over the sentence, for whoever hears the screen rather than sees it. */
private fun promptFor(composing: Composing): Ask {
    val seat = composing.seat ?: return Ask.ThePlan
    val who = speakerFor(composing.view, seat)
    return when (val next = composing.next) {
        is NextPart.Targets -> Ask.WhichTwoWillItSwap(next.rank)
        is NextPart.Look -> Ask.WhichCardWillItLookAt(next.rank)
        is NextPart.Point -> Ask.WhichCardDoesTheKingPointAt
        is NextPart.KingsRank -> Ask.WhichRankShouldTheyDeclare(who)
        is NextPart.Victim -> Ask.WhoShouldDraw
        NextPart.Done, null -> Ask.WhatShouldTheyDo(who)
    }
}

/** The one line under the sentence: where to touch, or who changed the turn, or how to change it. */
private fun hintFor(composing: Composing, board: Board): Detail {
    val editor = composing.plan?.editedBy
    return when {
        board.lanes.any { it.health == StepHealth.BROKEN } -> Detail.AClaimWasWrong
        !composing.member -> Detail.TheCallerReads
        composing.next is NextPart.Targets -> Detail.CarryACardOrTouchIt
        composing.next is NextPart.Look || composing.next is NextPart.Point -> Detail.TouchTheCard
        editor != null && editor != composing.view.viewerId ->
            Detail.ChangedBy(speakerFor(composing.view, editor))
        else -> Detail.TouchAWord
    }
}

/**
 * The King's rank, offered from what the table said the pointed card is — one answer per rank
 * somebody named — and the whole set one touch further.
 */
private fun kingsRankAnswers(composing: Composing): List<Choice> {
    val asked = composing.next as? NextPart.KingsRank ?: return emptyList()
    val seat = composing.seat ?: return emptyList()
    val standing = composing.lane.stepAt(asked.part) as? Step.Declare ?: return emptyList()
    val believed = believedOnView(composing.there, asked.card)
    val said = believed.sources.flatMap { claim -> claim.ranks.map { it to claim.by } }
        .filter { (rank, _) -> rank in believed.candidates }
        .distinctBy { it.first }
    return buildList {
        said.forEach { (rank, by) ->
            composing.lane.edit(seat, asked.part, standing.copy(rank = rank))?.let { edit ->
                val label = Label.RankSaidBy(rank, speakerFor(composing.view, by))
                add(Choice(label, Move.Plan(edit), Tone.DECLARE))
            }
        }
        add(Choice(Label.AnotherRank, Move.Ask(Question.Naming(seat, composing.at, asked.part))))
    }
}

/** Every coalition seat's plate, as the way to its own turn. */
private fun waysToTurns(view: PlayerView, focus: Question.ThePlan): List<SeatChoice> {
    val caller = view.vintoCallerId ?: return emptyList()
    return coalitionInTurnOrder(view.players.map { it.id }, caller).mapIndexed { turn, seat ->
        SeatChoice(
            id = seat,
            nickname = view.players.first { it.id == seat }.nickname,
            who = speakerFor(view, seat),
            move = Move.Ask(focus.copy(at = turn + 1, picked = null, runningTo = null, landed = false)),
            planTurn = true,
        )
    }
}

/** The seats an Ace may make draw: every coalition seat — the caller's hand is frozen from the call. */
private fun victims(view: PlayerView, seat: String, lane: Lane?, part: Part): List<SeatChoice> {
    val caller = view.vintoCallerId ?: return emptyList()
    return coalitionInTurnOrder(view.players.map { it.id }, caller).mapNotNull { id ->
        val edit = lane.edit(seat, part, Step.ForceDraw(id)) ?: return@mapNotNull null
        SeatChoice(
            id = id,
            nickname = view.players.first { it.id == id }.nickname,
            who = speakerFor(view, id),
            move = Move.Plan(edit),
        )
    }
}

/**
 * Composing on the felt: a carry lands where the composer allows; the first touch picks a
 * card up and the second puts it somewhere; a single touch answers a single-card question.
 */
private fun feltTaps(board: Board, focus: Question.ThePlan): Map<CardRef, Move> {
    val composer = board.building?.composer ?: return emptyMap()
    val picked = focus.picked

    if (picked == null) {
        return composer.touches + composer.sources.associateWith { Move.Ask(focus.copy(picked = it)) }
    }

    val destinations = composer.drops[picked].orEmpty()
    return buildMap {
        // Down again, on the card that is up: the way out is the thing already under the finger.
        put(picked, Move.Ask(focus.copy(picked = null)))
        destinations.forEach { (target, edit) ->
            if (target is PlanTarget.Card) put(target.ref, edit)
        }
    }
}

/**
 * What should this seat do with the card their turn takes? — the outcome word, asked.
 *
 * The answers: **put a card down** or **let it go** — and **play it**, only once the card is
 * face up and has an action, because before that there is nothing to play.
 *
 * **"We'll see" is not among them.** It was, and it cleared the lane — which is how a turn
 * gets back to undecided. Since the plan is read front to back (`Transport.reach`), undeciding
 * a turn closes every page after it, so the one deliberate "I don't know yet" answer was also
 * the only one that could throw away the rest of the board, including the reader's own turn.
 * A decision can still be changed: touch the word and answer it again.
 */
internal fun doingTable(
    view: PlayerView,
    question: Question.Doing,
    plan: CoalitionPlan? = null,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val focus = Question.ThePlan(at = question.at)
    val composing = compose(view, plan, away, reveals, focus, question = question)
        ?: return Table(Ask.Watching)
    val seat = composing.seat ?: return Table(Ask.Watching)
    val drawn = composing.start.drawn
    val answers = buildList {
        // **The rail's own words, not a second set.** These three answers are the three a player
        // is offered on their own turn — use the action, swap it into the hand, put it down — and
        // the plan had its own names for them: "Play it", "Put a card down", "Let it go". One
        // move with two names is one move a player has to recognise twice, and the plan is where
        // somebody is learning what the turn will look like when it happens. Asked for from a
        // phone: "I prefer to have same names as we have during game".
        if (drawn != null && hasAction(drawn)) {
            add(Choice(Label.UseAction, Move.Plan(PlanEdit.SetLane(seat, Step.UseIt)), Tone.PLAY))
        }
        add(Choice(Label.SwapCards, Move.Ask(Question.PuttingDown(seat, question.at)), Tone.KEEP))
        add(Choice(Label.Discard, Move.Plan(PlanEdit.SetLane(seat, Step.Bin))))
    }
    return Table(
        prompt = Ask.WhatShouldTheyDo(speakerFor(view, seat)),
        detail = Detail.APlanIsASuggestion,
        board = composing.board().copy(answers = if (composing.editable) answers else emptyList()),
    )
}

/**
 * Which of [Question.PuttingDown]'s seat's cards goes out — touched on the felt, or carried to
 * the pile, which is the same edit. Any of them: one nobody knows lands a card nobody knows.
 */
internal fun puttingDownTable(
    view: PlayerView,
    question: Question.PuttingDown,
    plan: CoalitionPlan? = null,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val focus = Question.ThePlan(at = question.at)
    val composing = compose(view, plan, away, reveals, focus, Asking.WhichCard, question)
        ?: return Table(Ask.Watching)
    val seat = composing.seat ?: return Table(Ask.Watching)
    val board = composing.board()
    val composer = board.building?.composer

    return Table(
        prompt = Ask.WhichCardShouldTheyPutDown(speakerFor(view, seat)),
        detail = Detail.TouchTheCard,
        taps = composer?.drops.orEmpty()
            .mapNotNull { (ref, drops) -> drops[PlanTarget.Discard]?.let { ref to it } }
            .toMap(),
        board = board,
    )
}

/**
 * Naming the rank a King declares for the card it points at, off the whole rail of ranks —
 * for a card nobody has named, or a rank nobody said. The plan stays open behind it.
 */
internal fun namingTable(
    view: PlayerView,
    question: Question.Naming,
    plan: CoalitionPlan?,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val focus = Question.ThePlan(at = question.at)
    val composing = compose(view, plan, away, reveals, focus, question = question)
        ?: return Table(Ask.Watching)
    val seat = composing.seat ?: return Table(Ask.Watching)
    val actor = composing.lane.actorAt(question.part, seat)

    return Table(
        prompt = Ask.WhichRankShouldTheyDeclare(speakerFor(view, actor)),
        detail = Detail.APlanIsASuggestion,
        ranks = if (composing.editable) {
            declareRanks(
                composing.there,
                seat,
                composing.lane,
                question.part,
            )
        } else {
            emptyList()
        },
        board = composing.board(),
    )
}

/**
 * The cards an action names, touched on the felt: the two a Jack or a Queen trades, the one a
 * 7 to 10 looks at, the one a King points at. Reopened from the sentence, where the felt already
 * asks the same of the next open part.
 */
internal fun aimingTable(
    view: PlayerView,
    question: Question.Aiming,
    plan: CoalitionPlan?,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val focus = Question.ThePlan(at = question.at, picked = question.picked)
    val behind = compose(view, plan, away, reveals, focus, question = question) ?: return Table(Ask.Watching)
    val seat = behind.seat ?: return Table(Ask.Watching)
    val lane = behind.lane
    val rank = lane.rankAt(question.part, behind.start)
    val said = lane.stepAt(question.part)
    val asking: Asking = when {
        said is Step.Declare || rank == Rank.KING -> Asking.Point(question.part)
        rank == Rank.SEVEN || rank == Rank.EIGHT -> Asking.Look(question.part, own = true)
        rank == Rank.NINE || rank == Rank.TEN -> Asking.Look(question.part, own = false)
        else -> Asking.Trade(question.part)
    }
    val board = compose(view, plan, away, reveals, focus, asking, question)?.board()
        ?: return Table(Ask.Watching)

    return Table(
        prompt = when (asking) {
            is Asking.Trade -> Ask.WhichTwoWillItSwap(rank ?: Rank.JACK)
            is Asking.Look -> Ask.WhichCardWillItLookAt(rank ?: Rank.NINE)
            is Asking.Point -> Ask.WhichCardDoesTheKingPointAt
            Asking.WhichCard -> Ask.WhichCardShouldTheyPutDown(speakerFor(view, seat))
            is Asking.Throw -> Ask.WhichCardWillTheyThrowIn(speakerFor(view, seat))
        },
        detail = if (asking is Asking.Trade) Detail.CarryACardOrTouchIt else Detail.TouchTheCard,
        taps = aimingTaps(board, question),
        board = board,
    )
}

/** The felt's taps while a question is aiming: the composer's, with the pick-up kept inside the question. */
private fun aimingTaps(board: Board, question: Question.Aiming): Map<CardRef, Move> {
    val composer = board.building?.composer ?: return emptyMap()
    val picked = question.picked
    if (picked == null) {
        return composer.touches + composer.sources.associateWith { Move.Ask(question.copy(picked = it)) }
    }
    return buildMap {
        put(picked, Move.Ask(question.copy(picked = null)))
        composer.drops[picked].orEmpty().forEach { (target, edit) ->
            if (target is PlanTarget.Card) put(target.ref, edit)
        }
    }
}

/** Who an Ace makes draw: every coalition seat, on its plate and on the rail. */
internal fun forcingTable(
    view: PlayerView,
    question: Question.Forcing,
    plan: CoalitionPlan?,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val focus = Question.ThePlan(at = question.at)
    val composing = compose(view, plan, away, reveals, focus, question = question)
        ?: return Table(Ask.Watching)
    val seat = composing.seat ?: return Table(Ask.Watching)
    return Table(
        prompt = Ask.WhoShouldDraw,
        detail = Detail.APlanIsASuggestion,
        seats = if (composing.editable) victims(view, seat, composing.lane, question.part) else emptyList(),
        board = composing.board(),
    )
}

/**
 * "Then I throw in my Queen" — one throw-in on the turn, picked on the felt.
 *
 * Touching "+ throw in…" lights the coalition's cards: those known to match the landing rank
 * in gold, those nobody has named dimly — a dim one throws **blind**, which real tables do
 * because a wrong throw costs a penalty card but shows the card. A card known to be some other
 * rank can never match and is not offered. The thrower is the card's owner.
 */
internal fun throwingTable(
    view: PlayerView,
    question: Question.Throwing,
    plan: CoalitionPlan?,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val focus = Question.ThePlan(at = question.at)
    val asking = Asking.Throw(question.index)
    val composing = compose(view, plan, away, reveals, focus, asking, question) ?: return Table(Ask.Watching)
    val seat = composing.seat ?: return Table(Ask.Watching)
    val board = composing.board()
    val standing = composing.lane?.tossIns.orEmpty()
    val remove = if (question.index < standing.size && composing.editable) {
        val without = standing.filterIndexed { index, _ -> index != question.index }
        listOf(Choice(Label.RemoveThrow, Move.Plan(PlanEdit.SetTossIns(seat, without))))
    } else {
        emptyList()
    }

    return Table(
        prompt = Ask.WhichCardWillTheyThrowIn(speakerFor(view, seat)),
        detail = Detail.TouchACardToThrow,
        taps = board.building?.composer?.touches.orEmpty(),
        board = board.copy(answers = remove),
    )
}

internal fun stepLine(view: PlayerView, step: Step): StepLine = when (step) {
    is Step.Swap -> StepLine.Swap(
        fromWho = speakerFor(view, step.from.seat),
        fromSlot = step.from.position + 1,
        toWho = speakerFor(view, step.to.seat),
        toSlot = step.to.position + 1,
    )

    is Step.Declare -> StepLine.Declare(
        step.rank,
        who = step.card?.let { speakerFor(view, it.seat) },
        slot = step.card?.let { it.position + 1 },
        then = step.then?.let { stepLine(view, it) },
    )

    Step.TakeTheDiscard -> StepLine.TakeTheDiscard
    Step.Bin -> StepLine.Bin
    Step.UseIt -> StepLine.UseIt
    is Step.PutDown -> StepLine.PutDown(
        who = speakerFor(view, step.card.seat),
        slot = step.card.position + 1,
        rank = knownRankOf(view, step.card),
        call = step.guess,
        then = step.then?.let { stepLine(view, it) },
    )

    is Step.Peek -> StepLine.Peek(
        who = speakerFor(view, step.card.seat),
        slot = step.card.position + 1,
        alsoWho = step.also?.let { speakerFor(view, it.seat) },
        alsoSlot = step.also?.let { it.position + 1 },
    )

    is Step.ForceDraw -> StepLine.ForceDraw(speakerFor(view, step.seat))
}

/** A card named by seat and slot, anchored to what the table has said about it. */
internal fun cardAt(view: PlayerView, seat: String, position: Int): CardAt =
    cardAt(view, CardRef(seat, position))
