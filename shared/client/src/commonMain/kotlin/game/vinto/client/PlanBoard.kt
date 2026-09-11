package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Opening
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Shed
import game.vinto.shapes.Step
import game.vinto.shapes.TargetType
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.laneOf

/**
 * The coalition's shared plan, as the rail draws it and as a person edits it (design D7a).
 *
 * Three things live here, and they are one subject: the **board** — one line per coalition
 * turn, the sheds, and who has nodded; the **composer** — how a tap on a lane becomes one
 * `PlanEdit`; and the **pre-arm** — the viewer's own lane surfacing on their turn as the first
 * thing to press, when the draw has made it legal.
 *
 * Everything is built from the view and the standing plan alone. Which edits are *legal* is
 * `CoalitionPlan.edited`'s business, the one door both sessions call; what this decides is only
 * which of them to offer, so a lane the door would refuse — locked, or the seat on play — is
 * simply not tappable rather than tappable and refused.
 */

/** Where a card may be put, when the plan is being composed by moving it. */
sealed interface PlanTarget {
    /** Onto another card: the two swap. */
    data class Card(val ref: CardRef) : PlanTarget

    /** Onto the pile: the card is put down for a teammate to throw in on. */
    data object Discard : PlanTarget
}

/**
 * One turn's edits, offered as *moving a card* rather than as a menu (design D5).
 *
 * Two paths and one door. A drag reads [drops] — where this card may go and what the plan
 * becomes if it lands there — and a select-then-select reads the same map through the table's
 * own [Table.taps], so the two cannot diverge: they are the same `Move.Plan` values, and the
 * tests build each edit both ways and compare the plans.
 *
 * Only what `CoalitionPlan.edited` would accept is here. A lane the door refuses — locked, or
 * the seat already on play — gets no composer at all rather than one whose drops are refused,
 * because a drop that is taken and then rejected reads as a broken control rather than a rule.
 */
data class PlanComposer(
    /** Whose turn is being composed. */
    val seat: String,
    /**
     * Every card a drag or a selection may start from, and where each may be put.
     *
     * Empty for a card that may be picked up and has nowhere legal to go, which is why this is
     * a map rather than a set with a lookup beside it: the highlight is the keys' keys, and a
     * source with no destinations is simply not offered.
     */
    val drops: Map<CardRef, Map<PlanTarget, Move.Plan>>,
) {
    /** The cards a drag may begin on: the ones that can reach somewhere. */
    val sources: Set<CardRef> get() = drops.filterValues { it.isNotEmpty() }.keys
}

/**
 * The plan's transport: one named stop per turn, and the table each of them leaves behind
 * (design D14).
 *
 * **A movie, not a scrubber.** The film is a few seconds long and has at most four positions
 * worth stopping on — the table now, and the table after each of at most three turns — so a
 * continuous track would be a control with more resolution than its data: on a phone every
 * drag overshoots, and nearly every position it could reach shows cards in mid-air, which is
 * the one moment a table can be neither read nor edited. That is the detent, and it is
 * structural rather than a rule somebody applies: [at] is a count of turns.
 *
 * **And it names its positions, rather than offering ways to move between them.** It was
 * Back / Play / Next, which says how to travel and never says where you are: the same felt
 * meant "now" and "after the whole plan", the only difference was how many times you had
 * pressed, and the one position everybody wants — how the hands end up — took three presses
 * and announced itself nowhere. Reported from a phone as not being able to see how the cards
 * would look at the end. A stop per turn goes straight there, says whose turn it is, and shows
 * which one is being read.
 *
 * Every control is a [Move.Quiet] over the screen's own [Question.ThePlan] — nothing here
 * travels, and nothing here can reach the engine.
 */
data class Transport(
    /** Where the head is: 0 is the table now, [turns] is where the plan arrives. */
    val at: Int,
    /** How many turns the plan has, which is how many positions there are beyond the first. */
    val turns: Int,
    /**
     * The stop the film is travelling to, or null when the head is parked.
     *
     * The screen needs the destination as well as the fact of moving: it plays the frames
     * between [at] and here, and parks the head on arrival. See `PlanRunner`.
     */
    val travellingTo: Int?,
    /** Every position, in order: the table now, then the table after each turn. */
    val stops: List<Stop>,
    /** Stop, and come to rest on the boundary reached. Null when it is not running. */
    val halt: Move.Quiet?,
) {
    /** Whether it is running. Editing sleeps while it is. */
    val running: Boolean get() = travellingTo != null

    /** True at the last position, where the plan's arrival is read (design D10). */
    val arrived: Boolean get() = turns > 0 && at == turns
}

/**
 * One position the transport can be sent to.
 *
 * @param at 0 is the table now; *k* is the table after the plan's *k*th turn.
 * @param seat whose turn this position is the end of, or null for the table now — which is
 *   nobody's turn and is labelled as the present rather than as a player.
 * @param here whether the head is parked on it, which is the only thing that says which table
 *   is on the felt.
 * @param go where to press to come here. Null on the stop the head is already on, and null on
 *   every stop while the transport is running — a film that could be redirected mid-flight
 *   would leave cards travelling to a position nobody is watching for.
 */
data class Stop(
    val at: Int,
    val seat: Speaker?,
    val here: Boolean,
    val go: Move.Quiet?,
)

/**
 * The transport for [focus] over the coalition's [seats], in turn order. See [Transport].
 *
 * @param drawable one per turn: whether that turn has anything to *watch*. A turn nobody has
 *   decided still has a stop — it is somebody's turn either way, and ② must mean the same turn
 *   to every member reading it (design D6) — but there is nothing to play on the way to it, so
 *   going there is a jump rather than a film. Getting this wrong is what left a member watching
 *   a transport travel to the end with no card moving.
 */
internal fun transportFor(
    focus: Question.ThePlan,
    seats: List<Speaker?>,
    drawable: List<Boolean> = List(seats.size) { true },
): Transport {
    val turns = seats.size
    // Padded rather than trusted: a caller whose flags are shorter than the coalition is a
    // caller whose plan has fewer decisions than turns, and that is the ordinary case.
    val watchable = List(turns) { drawable.getOrElse(it) { false } }
    val at = focus.at.coerceIn(0, turns)
    val running = focus.running

    fun goTo(target: Int): Move.Quiet? = when {
        running -> null
        target == at -> null
        // Forward, over something worth watching: play the film between here and there. The
        // head stays where it is and travels — `PlanRunner` parks it on arrival.
        target > at && watchable.subList(at, target).any { it } ->
            Move.Ask(focus.copy(picked = null, runningTo = target))

        // Backward, or forward over turns with nothing in them: go straight there. Cards do
        // not fly in reverse, and a film of nothing is a wait.
        else -> Move.Ask(focus.copy(at = target, picked = null, runningTo = null))
    }

    return Transport(
        at = at,
        turns = turns,
        travellingTo = focus.runningTo,
        stops = (0..turns).map { position ->
            Stop(
                at = position,
                seat = seats.getOrNull(position - 1),
                here = position == at,
                go = goTo(position),
            )
        },
        // Halting parks the head where it already is, which is a boundary because there is no
        // other kind of position. The screen finishes the movement it was drawing and stops.
        halt = Move.Ask(focus.copy(at = at, picked = null, runningTo = null)).takeIf { running },
    )
}

/** The board: what the coalition intends, laid out for reading and for tapping. */
data class Board(
    /** One per coalition turn still to come, in the order they come. */
    val lanes: List<LaneLine>,
    /**
     * The transport's position: how many of the plan's turns the felt is showing as done.
     *
     * 0 is the table now and [lanes]`.size` is where the plan arrives, which is the position
     * the outcome is read at (design D10). Supplied by the screen — see [Question.ThePlan()].
     */
    val at: Int = 0,
    /** True while the transport is running, where nothing may be edited (design D14). */
    val running: Boolean = false,
    val sheds: List<ShedLine>,
    /** Every coalition member, and whether they have said yes to the board as it stands. */
    val nods: List<Nod>,
    val editedBy: Speaker?,
    /**
     * Where the plan would leave the round, from standing claims alone (design D8): the
     * coalition's best hand, the caller's believed total, how much of it nobody has seen, and
     * whether that wins — with level shown as losing, since a tie pays the caller.
     */
    val outcome: PlanOutcome? = null,
    /**
     * The turn being composed, as the belt draws it. Null at the last stop, where the plan has
     * run out of turns and there is nothing left to build.
     */
    val turn: TurnParts? = null,
    /** Moving through the plan (design D14). See [Transport]. */
    val transport: Transport = Transport(0, 0, null, emptyList(), null),
    /**
     * The table the felt draws while the plan is open: the one the transport is parked on.
     *
     * The whole plan is a picture of *where cards go*, so the felt has to show the hands as the
     * turns so far leave them rather than as they are. Built by transforming the view and never
     * by reducing — a client holds no `GameState` and must not acquire one (design R1) — so
     * nothing here can turn over a card this seat was not already shown.
     */
    val felt: PlayerView? = null,
    /**
     * The card a select-then-select edit has picked up, if one has (design D5).
     *
     * On the board so the felt can light the same destinations for a **touch** as it does for a
     * drag. Without it the two paths would look different even though they produce the identical
     * edit — the dragger would see where a card may go and the tapper would be guessing, which
     * is exactly how an accessible path quietly becomes a lesser one.
     */
    val picked: CardRef? = null,
    /**
     * The cards the turn being read would move, at the seats that hold them.
     *
     * The plan's content is *where cards go*, and at rest there is no animation saying so — the
     * transport is parked, which is the state a plan is read and edited in. So the two ends of
     * a planned swap wear a mark on the felt, between the seats they travel between, and the
     * choreography draws the journey itself when the film runs.
     */
    val marks: Set<CardRef> = emptySet(),
    /**
     * The cards the plan has already turned into a **draw nobody has seen**.
     *
     * A step that puts a card down does not leave a gap: the seat draws a replacement off the
     * deck, face down, and nobody at the table knows what it is — not even the seat holding it.
     * On the felt that card wears the same back as every other, so a member reading the plan at
     * a later stop was looking at a hand where one card is a known quantity and another is a
     * lottery ticket, with nothing to tell them apart. Asked for from a phone: *"drawn cards
     * during the plan must be a different colour, to show the difference between a draw nobody
     * has seen yet and one we can guess from our own hands"*.
     *
     * Only up to the stop being read, because that is the only thing the felt is showing: a
     * draw two turns further on has not happened in the picture on screen.
     */
    val fresh: Set<CardRef> = emptySet(),
) {
    /**
     * The lane being built: the one the lit stop names, and the only one with a composer.
     *
     * **Asked of the lanes rather than worked out a second time.** Which index that is has moved
     * once already — a stop names the turn it *ends*, not the one it starts — and the two places
     * that re-derived it drifted apart the moment it did, so the felt lit one turn's cards while
     * the taps edited another's.
     */
    val building: LaneLine? get() = lanes.firstOrNull { it.composer != null }
}

/**
 * The plan in one line, for the tables that are about something else.
 *
 * The board itself is a **mode** — it takes the rail over the way a claim or a King's rank rail
 * does — because drawn beside the prompt it starved the log strip, and the strip is where the
 * bots' answers land. So every final-round table carries this instead: how much of the board
 * is set, how many have nodded, and one tap to open it.
 */
data class PlanSummary(
    val lanesSet: Int,
    val lanes: Int,
    val agreed: Int,
    /** Whether the viewer has nodded; false for the caller, who never does. */
    val mine: Boolean,
    /** Opening the board. Null for nobody: the caller may read a standing plan too. */
    val open: Move,
    /** Whether the plan as it stands wins, from what the table has been told. See [Board.outcome]. */
    val outcome: PlanOutcome? = null,
    /**
     * Which seats have said yes to the plan as it stands, and which have a rank ready to throw
     * in — both worn on the seat plates rather than in the plan (design D7).
     *
     * Neither is a *turn*. A nod is about a member and a shed costs nobody a turn, so putting
     * either in the sequence of turns would say they are the same kind of thing as a step. They
     * are on the summary rather than on the board because the plates draw them for the whole of
     * the final round, and the board exists only while the plan is open.
     */
    val nodded: Set<String> = emptySet(),
    val shedding: Set<String> = emptySet(),
    /**
     * Who last changed the plan, for the band the plan puts over the felt.
     *
     * On the summary rather than on the board because it is about the plan as a whole — not
     * about any one turn — and the band is drawn from the summary.
     */
    val editedBy: Speaker? = null,
)

/**
 * One coalition member's turn on the board.
 *
 * [step] null is "your call", which is a real answer. Whether this turn may be *changed* is
 * [composer] being present, and it is absent wherever the door would refuse an edit: the turn
 * has begun, or the viewer is the caller, who reads the plan and edits none of it.
 */
data class LaneLine(
    val who: Speaker,
    val step: StepLine?,
    val locked: Boolean,
    /**
     * How the step is bearing up (design D9): still pointing at its card, following a card that
     * moved, or built on a claim a reveal has since proved wrong. The last is the game working,
     * not a player failing, and the copy says so.
     */
    val health: StepHealth = StepHealth.LIVE,
    /** What the lane's own seat would rather do (3.13), offered beside the step, not over it. */
    val suggestion: StepLine? = null,
    /** One tap puts the suggestion on the board — an ordinary edit, by whoever taps. */
    val useSuggestion: Move? = null,
    /**
     * Which turn this is, as people count: ① ② ③. Carried rather than derived from the index,
     * because a lane with no step still holds its place and must keep its number (design D6) —
     * ② has to mean the same turn to every member reading it.
     */
    val number: Int = 0,
    /**
     * Whether the step can be drawn at all.
     *
     * False where it names a card that is not there: `rehearse()` already returns no frame for
     * one, because "a rehearsal of a broken plan would be a picture of something that cannot
     * happen", and the felt says so rather than inventing one (design D6).
     */
    val playable: Boolean = true,
    /**
     * How this turn is edited by moving a card, when it is the one being composed (design D5).
     *
     * Non-null on exactly one lane — the next turn to happen from the table the transport is
     * parked on — and null everywhere the door would refuse an edit: a locked lane, the seat
     * already on play, the caller's seat, and every lane at all while the transport runs.
     */
    val composer: PlanComposer? = null,
)

/** A step in words a renderer can put into a sentence. Positions are one-based, as people count. */
sealed interface StepLine {
    data class Swap(val fromWho: Speaker, val fromSlot: Int, val toWho: Speaker, val toSlot: Int) : StepLine
    data class Declare(val rank: Rank) : StepLine
    data object TakeTheDiscard : StepLine

    /** Put [who]'s card [slot] on the pile; [rank] when the table knows what it is. */
    data class PutDown(val who: Speaker, val slot: Int, val rank: Rank?) : StepLine

    /** Let the drawn card go and leave the hand alone. */
    data object Bin : StepLine

    /** Use whatever the card turns out to be, for its action. */
    data object UseIt : StepLine
}

/**
 * The turn being built, as a row of parts in the order they happen.
 *
 * **A turn is a sequence, so the belt draws one.** It was a sentence — "Turn 1, Tide: swap
 * Tide's card 1 with your card 1" — under a row of verbs that acted on it, and neither said
 * which of the two piles the card came from, because three of the four steps never recorded it.
 * A row of parts says the shape at a glance and opens the right question when a part is
 * touched: take from *here*, then do *this* with it, on *that*, and *these people* throw in.
 *
 * Every part is a [Move.Quiet] over the screen's own question. Nothing here reaches the engine.
 */
data class TurnParts(
    val who: Speaker,
    /** Which pile the card comes from. Null until somebody says. */
    val opening: Opening?,
    /** Swap it for the other pile. Null when the other pile has nothing worth taking. */
    val changeOpening: Move.Quiet?,
    /** What is done with the card. Null until somebody says. */
    val play: PlayKind?,
    val changePlay: Move.Quiet?,
    /**
     * What the play names — a rank to declare, the cards a swap moves, the card put down.
     *
     * Null where the play names nothing yet, or nothing at all: letting a card go names no more
     * than itself.
     */
    val detail: StepLine?,
    val changeDetail: Move.Quiet?,
    /** Who has said they will throw in on what this turn puts down. */
    val tossers: List<ShedLine>,
    val addToss: Move.Quiet?,
    /**
     * How the step is bearing up (design D9), and whether it can be drawn at all.
     *
     * News rather than decoration, and the reason the row is not only a row: a claim a reveal
     * has proved wrong, or a card that has gone, has to be *said*. It rode on the sentence the
     * row replaced, and a row of marks has nowhere to put a sentence — so the note sits under
     * the row, and only when there is one.
     */
    val health: StepHealth = StepHealth.LIVE,
    val playable: Boolean = true,
    /** What this turn's own seat would rather do, offered beside the step rather than over it. */
    val suggestion: StepLine? = null,
    val useSuggestion: Move? = null,
)

/** The three things a turn can do with the card it takes. See [TurnParts]. */
enum class PlayKind {
    /** Use its action — which for a Jack or a Queen is itself a swap of two cards. */
    PLAY_IT,

    /** Into the hand, and one of your own goes out. */
    KEEP_IT,

    /** Onto the pile, action unused, hand untouched. */
    BIN_IT,
}

/** Somebody will throw in a rank if it lands. [move] takes it back, for the one who said it. */
data class ShedLine(val who: Speaker, val rank: Rank, val move: Move? = null)

/** One member's yes or not-yet. [away] because a seat a bot is covering nods for itself. */
data class Nod(val who: Speaker, val agreed: Boolean, val away: Boolean)

/**
 * The board for [view]'s seat, or null when there is nothing to draw.
 *
 * A coalition member sees it for the whole final round, empty lanes included — the empty
 * lane is what you tap to start planning. The caller sees it only once something stands on
 * it, and can tap nothing: talk is public (design D12) and the plan is built from public
 * claims, so there is nothing to hide, and nothing for them to change.
 */
internal fun boardFor(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal> = emptyList(),
    focus: Question.ThePlan = Question.ThePlan(),
    /**
     * Whether a part of the turn is open for changing — a chooser, or a card held.
     *
     * Watching and editing want different tables, and this is which of the two the felt gets.
     * See the note on [Board.felt].
     */
    editing: Boolean = false,
): Board? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null

    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    val onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id
    val agreed = plan?.agreed.orEmpty()
    // Read against the table as it is now: a step follows its card in silence, and one whose
    // claim a reveal contradicted is marked rather than repaired (design D9).
    val reading = plan?.let { readPlan(view, it, reveals) }

    // The transport, clamped to positions that exist: 0 is the table now, one per turn after.
    // Clamped rather than trusted, because it arrives from the screen and a plan can shrink
    // under it — a member clears the last lane while another is parked past it.
    val at = focus.at.coerceIn(0, coalition.size)

    // **The stop names the turn, so the turn it names is the one being built.** Stop 1 is
    // "Tide", and Tide's turn is the first — so parking there shows what Tide did and offers
    // Tide's turn to change. It used to build the turn that *starts* at the stop rather than
    // the one that ends there, so the header said "Tide" while the belt below built Dune's
    // turn. Reported from a phone as the two lists being a position out of step.
    //
    // -1 at "Now", which is nobody's turn: it is the table before the plan begins, and there
    // is nothing there to build.
    val composed = at - 1

    // A card picked up is a builder that is open, exactly as a chooser is: the select-then-
    // select path is the drag's equal and must show the same table (design D5).
    val building = editing || focus.picked != null

    // The table a turn is composed *against* is the one it starts from — a swap on Tide's turn
    // trades the cards as the turns before it leave them, which is the only reading that makes
    // the picture true.
    val film = plan?.let { rehearsal(view, it) }
    val there = film?.tables?.getOrNull(composed.coerceAtLeast(0)) ?: view

    val lanes = coalition.mapIndexed { turn, seat ->
        laneLine(view, plan, reading, Lanes(seat, turn, composed, there, onPlay, member, focus.running))
    }

    return Board(
        at = at,
        running = focus.running,
        transport = transportFor(
            focus,
            seats = coalition.map { speakerFor(view, it) },
            // Read off the same rehearsal the film is made from, so what the transport promises
            // is watchable is exactly what plays.
            drawable = film?.frames?.map { it != null } ?: List(coalition.size) { false },
        ),
        lanes = lanes,
        sheds = plan?.sheds.orEmpty().map { shed ->
            ShedLine(
                who = speakerFor(view, shed.seat),
                rank = shed.rank,
                move = Move.Plan(PlanEdit.RemoveShed(shed)).takeIf { member && shed.seat == view.viewerId },
            )
        },
        nods = coalition.map { Nod(speakerFor(view, it), agreed = it in agreed, away = it in away) },
        editedBy = plan?.editedBy?.let { speakerFor(view, it) },
        outcome = plan?.let { planOutcome(view, it) },
        // **Watching and editing want different tables.** Parked on Tide's stop you have just
        // watched Tide's turn, so what belongs on the felt is its *result* — that is the whole
        // point of going there. But to *change* Tide's turn you need the table Tide starts
        // from, which is the one before it. So the felt steps back the moment a part of the
        // turn is open for changing, and returns to the result when it is not.
        felt = feltAt(film, at, there, building),
        picked = focus.picked,
        marks = coalition.getOrNull(composed)
            ?.let { seat -> plan?.laneOf(seat)?.step }
            ?.let { step -> cardsMoved(step) }
            .orEmpty(),
        fresh = freshDraws(coalition, plan, at),
        turn = lanes.getOrNull(composed)?.let { line ->
            partsFor(view, plan, coalition[composed], line, at, editable = member && focus.runningTo == null)
        },
    )
}

/**
 * Which table the felt draws: the result of the turn being read, or the table it starts from.
 *
 * Its own function because [boardFor] is at the number of branches a reader can hold at once,
 * and because the choice is one sentence: watching wants the result, editing wants the start.
 */
private fun feltAt(film: Rehearsal?, at: Int, there: PlayerView, building: Boolean): PlayerView =
    if (building) there else film?.tables?.getOrNull(at) ?: there

/**
 * The parts of one turn, as the belt draws them. See [TurnParts].
 *
 * @param editable false for the caller, who reads and taps nothing, and while the film runs,
 *   where a card halfway between two seats is at no position at all (design D14).
 */
private fun partsFor(
    view: PlayerView,
    plan: CoalitionPlan?,
    seat: String,
    line: LaneLine,
    at: Int,
    editable: Boolean,
): TurnParts {
    val lane = plan?.laneOf(seat)
    val step = lane?.step
    val top = view.discardTop
    val takeable = top != null && top.actionText != null && !top.played

    fun quiet(move: Move.Quiet): Move.Quiet? = move.takeIf { editable }

    return TurnParts(
        who = speakerFor(view, seat),
        opening = lane?.opening,
        changeOpening = otherPile(lane?.opening, takeable)
            ?.let { quiet(Move.Plan(PlanEdit.OpenLane(seat, it))) },
        play = step?.let { playKindOf(it) },
        changePlay = quiet(Move.Ask(Question.Doing(seat, at))),
        detail = step?.let { stepLine(view, it) }?.takeIf { it.namesSomething },
        changeDetail = step?.let { detailMove(seat, it, at) }?.let { quiet(it) },
        // Every shed the plan holds for this seat: what they have said they will throw in when
        // this turn puts something on the pile.
        tossers = plan?.sheds.orEmpty().filter { it.seat == seat }.map { shed ->
            ShedLine(
                who = speakerFor(view, shed.seat),
                rank = shed.rank,
                move = Move.Plan(PlanEdit.RemoveShed(shed)).takeIf { editable },
            )
        },
        addToss = quiet(Move.Ask(Question.Shedding(seat, at))),
        // Read off the line the board already built, so the row and the felt cannot come to
        // disagree about whether a step is still standing.
        health = line.health,
        playable = line.playable,
        suggestion = line.suggestion,
        useSuggestion = line.useSuggestion,
    )
}

/**
 * The pile a tap on the opening would move it to, or null when there is nowhere to move it.
 *
 * **Nothing chosen answers DRAW**, and it did not: the tap offered "whichever of the two is not
 * already chosen", which with nothing chosen is *take the discard* — legal only while the pile
 * has an unused action card on it. Over a played card the part was therefore dead, and drawing —
 * the one opening that is always legal — could not be chosen at all. Reported from a phone as
 * nothing happening when the belt was tapped.
 */
private fun otherPile(opening: Opening?, takeable: Boolean): Opening? = when {
    // Every turn can draw, so an undecided opening always has an answer.
    opening == null -> Opening.DRAW
    opening == Opening.TAKE_THE_DISCARD -> Opening.DRAW
    // Taking is a move only while there is an unused action card to take — the same rule the
    // turn itself applies. Where there is not, the opening is already the only one there is.
    takeable -> Opening.TAKE_THE_DISCARD
    else -> null
}

/** Which of the three things a step does with the card it takes. */
private fun playKindOf(step: Step): PlayKind = when (step) {
    // A Jack's or a Queen's action is itself a swap, which is why "play it" and "swap" are not
    // the same word: one is what you do with the card, the other is what the card does.
    is Step.Swap, is Step.Declare, Step.TakeTheDiscard, Step.UseIt -> PlayKind.PLAY_IT
    is Step.PutDown -> PlayKind.KEEP_IT
    Step.Bin -> PlayKind.BIN_IT
}

/** Whether a step has a third part to it at all — something it names beyond itself. */
private val StepLine.namesSomething: Boolean
    get() = when (this) {
        is StepLine.Swap, is StepLine.Declare, is StepLine.PutDown -> true
        StepLine.TakeTheDiscard, StepLine.Bin, StepLine.UseIt -> false
    }

/** What touching a step's own part opens: the question that names what it acts on. */
private fun detailMove(seat: String, step: Step, at: Int): Move.Quiet? = when (step) {
    // A King names a rank; so does the guess on a card you put down. Different questions,
    // because they are different moves with different prices.
    is Step.Declare, Step.UseIt, Step.TakeTheDiscard -> Move.Ask(Question.Planning(seat, at))
    is Step.PutDown -> Move.Ask(Question.Planning(seat, at))
    // A swap names two cards, and they are carried on the felt rather than picked off a rail:
    // there is nothing to open.
    is Step.Swap, Step.Bin -> null
}

/**
 * Where the plan has put an unseen draw, by the stop being read. See [Board.fresh].
 *
 * Walked over the coalition's **turns** rather than the plan's stored lanes, for the same reason
 * the film is: a lane exists only once somebody has set it, so its index is a count of decisions
 * and not of turns.
 */
private fun freshDraws(coalition: List<String>, plan: CoalitionPlan?, at: Int): Set<CardRef> {
    if (plan == null) return emptySet()
    return coalition.take(at)
        .mapNotNull { seat -> plan.laneOf(seat)?.step as? Step.PutDown }
        .map { CardRef(it.card.seat, it.card.position) }
        .toSet()
}

/**
 * Everything one lane needs to know that is not the plan itself.
 *
 * A parameter object rather than eight arguments, because they are one subject — *which* turn
 * this is, seen from where — and because [boardFor] was the shape detekt names when a function
 * has grown a second function inside it.
 */
private data class Lanes(
    val seat: String,
    /** Which turn this is, zero-based. */
    val turn: Int,
    /** Where the transport is parked. The turn at this index is the one being composed. */
    val at: Int,
    /** The table the transport is parked on, which is what an edit is aimed at. */
    val there: PlayerView,
    val onPlay: String?,
    val member: Boolean,
    val running: Boolean,
)

/** One coalition turn, as the plan draws it and as a person edits it. */
private fun laneLine(
    view: PlayerView,
    plan: CoalitionPlan?,
    reading: PlanReading?,
    of: Lanes,
): LaneLine {
    val lane = plan?.laneOf(of.seat)
    val index = reading?.plan?.lanes?.indexOfFirst { it.seat == of.seat } ?: -1
    val followed = reading?.plan?.lanes?.getOrNull(index) ?: lane
    val locked = lane?.locked == true
    val editable = of.member && !locked && of.seat != of.onPlay
    val selected = of.turn == of.at

    return LaneLine(
        who = speakerFor(view, of.seat),
        step = followed?.step?.let { stepLine(view, it) },
        locked = locked,
        health = reading?.health?.getOrNull(index) ?: StepHealth.LIVE,
        // Only on the turn being read (design D8). A bot's "would rather" is a second, dimmer
        // ghost, and three of them at once is three alternative futures on one felt; scoped to
        // the selected turn it is a legible "or this".
        suggestion = lane?.suggestion?.takeIf { selected }?.let { stepLine(view, it) },
        useSuggestion = lane?.suggestion
            ?.takeIf { editable && selected }
            ?.let { Move.Plan(PlanEdit.SetLane(of.seat, it)) },
        number = of.turn + 1,
        playable = followed?.step?.let { drawable(view, it) } ?: true,
        composer = composerFor(of.there, of.seat).takeIf { editable && selected && !of.running },
    )
}

/** The one line every final-round table carries, or null where there is no board to open. */
internal fun summaryFor(view: PlayerView, plan: CoalitionPlan?): PlanSummary? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val member = view.viewerId != caller
    if (!member && (plan == null || plan.isEmpty)) return null

    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    return PlanSummary(
        lanesSet = plan?.lanes.orEmpty().count { it.step != null },
        lanes = coalition.size,
        agreed = plan?.agreed.orEmpty().count { it in coalition },
        mine = view.viewerId in plan?.agreed.orEmpty(),
        open = Move.Ask(Question.ThePlan()),
        outcome = plan?.takeUnless { it.isEmpty }?.let { planOutcome(view, it) },
        editedBy = plan?.editedBy?.let { speakerFor(view, it) },
        nodded = plan?.agreed.orEmpty().filter { it in coalition }.toSet(),
        shedding = plan?.sheds.orEmpty().map { it.seat }.filter { it in coalition }.toSet(),
    )
}

/**
 * The board, open: every lane, the sheds, the nods — and, for a member with something to say
 * yes to, "Agree". Back closes it. The log is not drawn under this table (the rail treats a
 * board like a rank grid), which is the price of a board a phone can hold.
 */
internal fun boardTable(
    view: PlayerView,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal> = emptyList(),
    focus: Question.ThePlan = Question.ThePlan(),
): Table {
    val board = boardFor(view, plan, away, reveals, focus) ?: return Table(Ask.Watching, waiting = true)
    val member = view.viewerId != view.vintoCallerId
    val agreeable = member && plan != null && !plan.isEmpty && view.viewerId !in plan.agreed
    val lane = board.building
    val composer = lane?.composer

    return Table(
        // The turn being read, named. At the last position there is no turn ahead — the plan
        // has arrived — and the prompt is the plan itself.
        prompt = lane?.let { Ask.WhatShouldTheyDo(it.who) } ?: Ask.ThePlan,
        // A broken step is news, and it is the game working: somebody's memory was wrong.
        detail = if (board.lanes.any { it.health == StepHealth.BROKEN }) {
            Detail.AClaimWasWrong
        } else {
            Detail.APlanIsASuggestion
        },
        // Everything here changes the plan, moves through it, or closes it — and nothing acts
        // on the round (design D9).
        choices = planChoices(view, composer, lane?.step, agreeable, member),
        // The same edits the drag offers, reached by touching one card and then the next
        // (design D5). Not a lesser path and not a copy: the values here come out of the very
        // same `drops` map, so a screen reader, a keyboard and a switch device compose the
        // identical `PlanEdit` — which is what `PlanComposerTest` compares.
        taps = selectThenSelect(board, focus),
        board = board,
    )
}

/**
 * Composing by touching: the first touch picks a card up, the second puts it somewhere.
 *
 * The first touch is a [Move.Ask] that only remembers what was picked; the second is the
 * [Move.Plan] the drag would have produced. Touching the picked card again puts it back down,
 * because a selection with no way out is a mode a player is stuck in.
 */
private fun selectThenSelect(board: Board, focus: Question.ThePlan): Map<CardRef, Move> {
    val composer = board.building?.composer ?: return emptyMap()
    val picked = focus.picked

    if (picked == null) {
        return composer.sources.associateWith { Move.Ask(focus.copy(picked = it)) }
    }

    val destinations = composer.drops[picked].orEmpty()
    return buildMap {
        // Down again, on the card that is up. The way out has to be the thing already under
        // the finger, or the only way out is a control nobody looks for.
        put(picked, Move.Ask(focus.copy(picked = null)))
        destinations.forEach { (target, edit) ->
            if (target is PlanTarget.Card) put(target.ref, edit)
        }
    }
}

/**
 * The plan's own buttons: the two steps a card cannot be *carried* into, and the way out.
 *
 * A King names a rank rather than a place and taking the discard names no card at all, so
 * neither has a gesture — they are buttons on the turn being read. Everything here is a
 * [Move.Quiet]; nothing on this rail can reach the engine.
 */
private fun planChoices(
    view: PlayerView,
    composer: PlanComposer?,
    step: StepLine?,
    agreeable: Boolean,
    member: Boolean,
): List<Choice> = buildList {
    if (composer != null) {
        add(Choice(Label.PlanADeclare, Move.Ask(Question.Planning(composer.seat))))
        // Only while there is an unplayed action card to take — the same rule the turn itself
        // applies. A step that could not be done is not worth a button.
        val top = view.discardTop
        if (top != null && top.actionText != null && !top.played) {
            add(
                Choice(
                    Label.PlanTakeTheDiscard,
                    Move.Plan(PlanEdit.SetLane(composer.seat, Step.TakeTheDiscard)),
                ),
            )
        }
        if (step != null) add(Choice(Label.ClearLane, Move.Plan(PlanEdit.ClearLane(composer.seat))))
    }
    if (agreeable) add(Choice(Label.Agree, Move.Agree(true), Tone.PLAY))
    if (member) add(Choice(Label.PlanAShed, Move.Ask(Question.Shedding(view.viewerId))))
    // **No "Back".** The switch in the header is the way in and the way out, of one standing;
    // a second control that closes the plan is a second thing to learn, and it was the fifth
    // button on a phone's rail — which is how "DECLARE A RANK" came out as "DECL / ARE A".
}

/**
 * "I hold one of these and I will throw it in if one lands" — a shed, said on the board.
 *
 * Shedding is the cheapest way to lower a hand in the game and costs no turn, so it is most of
 * how a coalition plays its window (design D13a); but a wrong throw costs a card and, in the
 * final round, bars the seat for the rest of it — and when the seat is the hand the coalition is
 * pushing, that is the round. The rail says which of the two the viewer is looking at.
 */

/**
 * What should this seat do with the card their turn takes? — the middle part of the row.
 *
 * Three answers, and each is a whole instruction on its own: **play it** for its action, **keep
 * it** and put one of yours out, or **let it go**. Which of the three is set decides what the
 * next part of the row can even ask about, which is why it is a question rather than a button:
 * a turn is built a part at a time and this is the part everything after it hangs off.
 *
 * Letting the card go is the one that looks like a wasted turn and is not — it is how a
 * coalition tells the seat holding its best hand to leave that hand alone.
 */
internal fun doingTable(
    view: PlayerView,
    question: Question.Doing,
    plan: CoalitionPlan? = null,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val seat = view.players.firstOrNull { it.id == question.seat } ?: return Table(Ask.Watching)
    val who = speakerFor(view, seat.id)
    val back = Move.Ask(Question.ThePlan(at = question.at))

    // Keeping the card means putting one of your own out, and which one is the next part of the
    // row — so this sets the lane to a put-down of the first card and the row asks about the
    // rest. A lane with no cards to put down cannot offer it at all.
    val mine = seat.cards.indices.firstOrNull()

    return Table(
        prompt = Ask.WhatShouldTheyDo(who),
        detail = Detail.APlanIsASuggestion,
        choices = buildList {
            add(Choice(Label.PlayTheCard, Move.Plan(PlanEdit.SetLane(seat.id, Step.UseIt)), Tone.PLAY))
            if (mine != null) {
                add(
                    Choice(
                        Label.KeepTheCard,
                        Move.Plan(PlanEdit.SetLane(seat.id, Step.PutDown(CardAt(seat.id, mine)))),
                        Tone.KEEP,
                    ),
                )
            }
            add(Choice(Label.LetTheCardGo, Move.Plan(PlanEdit.SetLane(seat.id, Step.Bin))))
            add(Choice(Label.Back, back))
        },
        // Editing: the felt steps back to the table this turn starts from, because that is what
        // an edit is aimed at — see [Board.felt].
        board = boardFor(view, plan, away, reveals, Question.ThePlan(at = question.at), editing = true),
    )
}

internal fun sheddingTable(
    view: PlayerView,
    question: Question.Shedding = Question.Shedding(view.viewerId),
    plan: CoalitionPlan? = null,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val who = question.seat
    return Table(
        prompt = Ask.WhichRankWillYouThrowIn,
        // The risk is the *speaker's* to run, so it is named only when the seat being spoken
        // for is the one that would pay for a wrong throw.
        detail = Detail.ShedRisk(pushed = who == view.viewerId && isTheHandBeingPushed(view)),
        choices = listOf(Choice(Label.Back, Move.Ask(Question.ThePlan(at = question.at)))),
        ranks = ALL_RANKS.map { rank -> RankChoice(rank, Move.Plan(PlanEdit.AddShed(Shed(who, rank)))) },
        // The plan stays open behind it. Naming a rank to throw in is *part of* planning, and a
        // screen that dropped back to the live table for it would put a draw button under a
        // finger that is in the middle of composing (design D9).
        // Editing: the felt steps back to the table this turn starts from, because that is what
        // an edit is aimed at — see [Board.felt].
        board = boardFor(view, plan, away, reveals, Question.ThePlan(at = question.at), editing = true),
    )
}

/**
 * Whether the viewer's hand is the coalition's lowest as far as the table has been told — the
 * one hand whose cards a wrong throw costs the round, not just a member.
 */
private fun isTheHandBeingPushed(view: PlayerView): Boolean {
    val caller = view.vintoCallerId ?: return false
    val coalition = view.players.filter { it.id != caller }
    val believed = coalition.associate { seat ->
        seat.id to seat.cards.indices.sumOf { believedValueAt(seat, it) }
    }
    val lowest = believed.values.minOrNull() ?: return false
    return believed[view.viewerId] == lowest
}

internal fun stepLine(view: PlayerView, step: Step): StepLine = when (step) {
    is Step.Swap -> StepLine.Swap(
        fromWho = speakerFor(view, step.from.seat),
        fromSlot = step.from.position + 1,
        toWho = speakerFor(view, step.to.seat),
        toSlot = step.to.position + 1,
    )

    is Step.Declare -> StepLine.Declare(step.rank)
    Step.TakeTheDiscard -> StepLine.TakeTheDiscard
    Step.Bin -> StepLine.Bin
    Step.UseIt -> StepLine.UseIt
    is Step.PutDown -> StepLine.PutDown(
        who = speakerFor(view, step.card.seat),
        slot = step.card.position + 1,
        rank = view.players.firstOrNull { it.id == step.card.seat }
            ?.let { knownRankOf(view, it, step.card.position) },
    )
}

// ---------------------------------------------------------------------------- moving a card

/**
 * One turn's edits, as places a card may be carried to (design D5).
 *
 * The palette is the same one the rail's composer had and for the same reason: **what has been
 * said**. A card somebody has claimed, or one of the viewer's own they have read. A card nobody
 * knows anything about is not on offer — a plan that moved it would be moving a guess — and
 * that is what makes declaring worth doing.
 *
 * Two destinations, because there are two steps a card can be carried into. Onto another
 * player's card is a swap, which is why the caller's cards are neither a source nor a target
 * and why a hand cannot swap with itself. Onto the pile is a put-down, offered only from the
 * lane's own seat and only for a card the table knows the rank of — a put-down of a mystery
 * sets nothing up for a teammate to throw in on.
 *
 * @param there the table the transport is parked on, which is what the drag is aimed at.
 */
internal fun composerFor(there: PlayerView, seat: String): PlanComposer {
    val caller = there.vintoCallerId
    val movable = there.players
        .filter { it.id != caller }
        .flatMap { hand ->
            hand.cards.indices
                .filter { spokenFor(there, hand, it) }
                .map { CardRef(hand.id, it) }
        }

    val ownHand = there.players.firstOrNull { it.id == seat }
    val puttable = ownHand
        ?.let { hand -> hand.cards.indices.filter { knownRankOf(there, hand, it) != null } }
        .orEmpty()
        .map { CardRef(seat, it) }

    val drops = (movable + puttable).distinct().associateWith { from ->
        buildMap<PlanTarget, Move.Plan> {
            movable
                .filter { it.playerId != from.playerId }
                .forEach { to ->
                    val swap = Step.Swap(cardAt(there, from), cardAt(there, to))
                    put(PlanTarget.Card(to), Move.Plan(PlanEdit.SetLane(seat, swap)))
                }
            if (from in puttable) {
                put(PlanTarget.Discard, Move.Plan(PlanEdit.SetLane(seat, Step.PutDown(cardAt(there, from)))))
            }
        }
    }

    return PlanComposer(seat = seat, drops = drops)
}

/**
 * Whether a step still names cards that are there, and can therefore be drawn.
 *
 * The same question `rehearse()` asks before building a frame, asked here so the felt can mark
 * the turn as unplayable instead of leaving a numbered gap nobody can account for (design D6).
 */
private fun drawable(view: PlayerView, step: Step): Boolean = when (step) {
    is Step.Swap -> view.holds(step.from) && view.holds(step.to)
    is Step.PutDown -> view.holds(step.card)
    is Step.Declare -> true
    // Nothing moves, so there is nothing that can fail to be there.
    Step.TakeTheDiscard, Step.Bin, Step.UseIt -> true
}

/** The cards a step names, as the felt refers to them. A King names a rank rather than a card. */
private fun cardsMoved(step: Step): Set<CardRef> = when (step) {
    is Step.Swap -> setOf(
        CardRef(step.from.seat, step.from.position),
        CardRef(step.to.seat, step.to.position),
    )

    is Step.PutDown -> setOf(CardRef(step.card.seat, step.card.position))
    is Step.Declare, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> emptySet()
}

private fun PlayerView.holds(at: CardAt): Boolean =
    players.firstOrNull { it.id == at.seat }?.cards?.indices?.contains(at.position) == true

// ---------------------------------------------------------------------------- the composer

/**
 * Naming the rank a King should declare, for one turn of the plan.
 *
 * The only thing left of the rail's old composer, and design D5 is the reason: a plan is
 * changed by **carrying a card** to where it should go, and a King names a *rank* rather than a
 * place — there is nothing to carry. So the two steps with a destination (a swap, a put-down)
 * are gestures on the felt, and this one is a rank off the rail, which is the same rail a King
 * declares on during an ordinary turn.
 *
 * The plan stays open behind it. Naming a rank is *part of* planning, and a screen that dropped
 * back to the live table for it would put a draw button under a finger that is in the middle of
 * composing (design D9).
 */
internal fun planningTable(
    view: PlayerView,
    question: Question.Planning,
    plan: CoalitionPlan?,
    away: Set<String> = emptySet(),
    reveals: List<PublicReveal> = emptyList(),
): Table {
    val seat = view.players.firstOrNull { it.id == question.seat } ?: return Table(Ask.Watching)

    return Table(
        prompt = Ask.WhichRankShouldTheyDeclare(speakerFor(view, seat.id)),
        detail = Detail.APlanIsASuggestion,
        choices = listOf(Choice(Label.Back, Move.Ask(Question.ThePlan(at = question.at)))),
        // The ranks worth naming are the ones the table knows a coalition hand to hold — the
        // rest are on the rail too, muted, as a King's own rail draws them.
        ranks = ALL_RANKS.map { rank ->
            RankChoice(
                rank,
                Move.Plan(PlanEdit.SetLane(seat.id, Step.Declare(rank))),
                muted = rank !in ranksTheCoalitionIsKnownToHold(view),
            )
        },
        // Editing: the felt steps back to the table this turn starts from, because that is what
        // an edit is aimed at — see [Board.felt].
        board = boardFor(view, plan, away, reveals, Question.ThePlan(at = question.at), editing = true),
    )
}

/**
 * The rank of a card as this table can name it: what standing claims settle on, or what the
 * viewer can see of their own hand. Null where nobody could say.
 */
internal fun knownRankOf(view: PlayerView, hand: PlayerSeatView, position: Int): Rank? {
    believedOnView(hand, position).candidates.singleOrNull()?.let { return it }
    if (hand.id != view.viewerId) return null
    return (hand.cards.getOrNull(position) as? CardView.Visible)?.card?.rank
}

/** A card the plan may name: claimed by somebody, or one of the viewer's own they have read. */
private fun spokenFor(view: PlayerView, hand: PlayerSeatView, position: Int): Boolean {
    val claimed = believedOnView(hand, position).sources.isNotEmpty()
    val ownAndRead = hand.id == view.viewerId && position in hand.knownCardPositions
    return claimed || ownAndRead
}

/** Every rank some coalition card is believed to be — the ranks a King on the board could empty. */
private fun ranksTheCoalitionIsKnownToHold(view: PlayerView): Set<Rank> =
    view.players
        .filter { it.id != view.vintoCallerId }
        .flatMap { hand ->
            hand.cards.indices.flatMap { position ->
                val believed = believedOnView(hand, position)
                if (believed.rankKnown) believed.candidates else emptySet()
            }
        }
        .toSet()

private fun cardAt(view: PlayerView, ref: CardRef): CardAt {
    val hand = view.players.firstOrNull { it.id == ref.playerId }
    val anchor = hand?.let { believedOnView(it, ref.position).sources.firstOrNull() }
    return CardAt(ref.playerId, ref.position, anchor)
}

// ---------------------------------------------------------------------------- the viewer's turn

/**
 * The viewer's own lane, surfacing on their turn.
 *
 * Two things, and only these two. The ask is written under the prompt in words, whatever the
 * draw was — the plan is the reason the turn is being played the way it is, and it should be
 * in front of the person playing it. And when the table's *own* controls already offer a move
 * that does the step — the unused action card is on the pile, a King is in hand, a Jack is
 * aimed — that move is put first as "do as planned". It is found among the moves the table
 * built rather than composed here, so it is legal by construction, and every other move stays
 * exactly where it was: pre-arming aims a turn and never narrows it (design D5).
 */
internal fun Table.planned(view: PlayerView, plan: CoalitionPlan?): Table {
    val step = plan?.laneOf(view.viewerId)?.step ?: return this
    val armed = armedMove(view, step)
    val planned = armed?.let { listOf(Choice(Label.DoAsPlanned, it, Tone.PLAY)) }.orEmpty()

    // A draw that beats the plan offers the re-plan rather than insisting (3.11): keeping the
    // card comes first, the plan's step stays where it was, and the line under the prompt says
    // which card the draw is worth more than.
    val better = betterDraw(view, plan)
    val keep = better?.let {
        listOf(Choice(Label.KeepItInstead, Move.Ask(Question.WhichSlot), Tone.PLAY))
    }.orEmpty()
    return copy(
        detail = better?.let { Detail.YourDrawBeatsThePlan(it.rank, it.position) }
            ?: Detail.ThePlanAsksYouTo(stepLine(view, step)),
        choices = keep + planned + choices,
    )
}

/** The card the viewer has just drawn and is choosing about, when keeping it beats the plan. */
private fun betterDraw(view: PlayerView, plan: CoalitionPlan): KeepInstead? {
    if (view.subPhase != GameSubPhase.CHOOSING) return null
    val pending = view.pendingAction?.takeIf { it.playerId == view.viewerId && it.canGoToHand } ?: return null
    val rank = (pending.card as? CardView.Visible)?.card?.rank ?: return null
    return keepingBeatsThePlan(view, plan, rank)
}

private fun Table.armedMove(view: PlayerView, step: Step): Move? = when (step) {
    // Letting the card go is the ordinary discard, which the rail already offers plainly; an
    // arm that duplicated it would put a second button under the same finger.
    // Neither is a specific move the rail could arm: the card is not known yet, and letting
    // it go is the ordinary discard the rail already offers plainly.
    Step.Bin, Step.UseIt -> {
        null
    }

    Step.TakeTheDiscard -> {
        choices.firstOrNull { it.label is Label.UseFromPile }?.move
    }

    // Once the draw is in hand, putting the card down is the same tap as swapping into its
    // place; before the draw there is nothing to arm but drawing, which needs no help.
    is Step.PutDown -> {
        val drawn = view.pendingAction?.takeIf { it.playerId == view.viewerId && it.canGoToHand }
        if (drawn != null && step.card.seat == view.viewerId && view.subPhase == GameSubPhase.CHOOSING) {
            Move.Ask(Question.CallRank(step.card.position))
        } else {
            null
        }
    }

    is Step.Declare -> {
        ranks.firstOrNull { rank ->
            rank.rank == step.rank && (rank.move as? Move.Send)?.action is GameAction.DeclareKingAction
        }?.move
    }

    is Step.Swap -> {
        val pending = view.pendingAction?.takeIf { it.playerId == view.viewerId }
        val twoCards = pending?.targetType == TargetType.SWAP_CARDS ||
            pending?.targetType == TargetType.PEEK_THEN_SWAP
        val targets = pending?.targets.orEmpty()
        val firstAimed = targets.size == 1 &&
            targets[0].playerId == step.from.seat &&
            targets[0].position == step.from.position
        when {
            !twoCards -> null
            targets.isEmpty() -> taps[CardRef(step.from.seat, step.from.position)]
            firstAimed -> taps[CardRef(step.to.seat, step.to.position)]
            else -> choices.firstOrNull { it.label == Label.SwapCards }?.move
        }
    }
}
