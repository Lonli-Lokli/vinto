package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Opening
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TossIn
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.hasAction

/**
 * The turn as a sentence: the words a person would say it in, and what touching each does.
 *
 * *"Draws, puts down the Jack, trades Ember's 4 for Tide's Ace, then Tide throws in a 5"* is
 * what a coalition says at a real table, and the plan stands in for that talk. So the rail
 * draws exactly that, one word per decision, and the grammar is the rules' own: a plan can
 * only speak about cards the table can see or has been told about, so nothing is aimed on a
 * blind draw; a question with one answer is not asked, so "draws" is a fact where the pile
 * cannot be taken; and the next decision is on offer at the end of the line rather than asked
 * before anybody wants it.
 *
 * **Boxed means touchable.** A word with something to change is a decision and carries what
 * touching it opens ([Slot.open]); a fact — "drew the Queen", "draws" with one pile to draw
 * from, "and we'll see" for nothing decided — carries nothing and is drawn plain. The caller
 * reads every word plain. Every [Slot.open] is a [Move.Quiet]: nothing here reaches the engine.
 */
data class TurnSentence(
    val who: Speaker,
    /** Who the seat is, for the face at the head of the sentence. */
    val nickname: String?,
    /** Which turn this is, as people count. */
    val number: Int,
    /**
     * The turn's own move first, then one clause per throw-in in the order thrown, then the
     * throw on offer.
     */
    val clauses: List<Clause>,
    /** Watch this turn again, from the table it starts on. Null where there is nothing to watch. */
    val replay: Move.Quiet?,
    /**
     * How the turn is bearing up (design D9), and whether it can be drawn at all.
     *
     * News rather than decoration: a claim a reveal has proved wrong, or a card that has gone,
     * has to be *said*, under the sentence, and only when there is something to say.
     */
    val health: StepHealth = StepHealth.LIVE,
    val playable: Boolean = true,
    /** What this turn's own seat would rather do, offered beside the step rather than over it. */
    val suggestion: StepLine? = null,
    val useSuggestion: Move? = null,
) {
    /** The turn's own move: the first row of the rail. */
    val own: Clause get() = clauses.first()

    /** The throw-ins, and the throw on offer: the second row. */
    val throws: List<Clause> get() = clauses.drop(1)

    /** Every word, in order. */
    val says: List<Says> get() = clauses.flatMap { clause -> clause.slots.map { it.says } }

    /** The clause the rail is asking about, if one is. */
    val asked: Clause? get() = clauses.firstOrNull { clause -> clause.slots.any { it.asked } }
}

/**
 * One clause of the sentence: the turn's own move, one throw-in, or the throw on offer.
 *
 * [part] names which part of the turn the clause is about; null for the offer, which is about
 * nothing yet.
 */
data class Clause(val part: Part?, val slots: List<Slot>)

/** One word of the sentence, and what touching it does. */
data class Slot(
    val says: Says,
    /**
     * What touching the word opens or changes. Null where it is only to be read — a fact, or
     * the caller's view.
     */
    val open: Move.Quiet? = null,
    /** Whether this is the word the rail is asking for right now. */
    val asked: Boolean = false,
    /** Whether the word is on offer rather than said — the next decision, taken with one touch. */
    val offer: Boolean = false,
    /** A Queen's arrow: lit, she swaps; dim, she only looks. Touching it flips the two. */
    val toggle: Move.Quiet? = null,
)

/**
 * A card in the sentence, drawn as the card on the felt is: whose, what the table says it is,
 * its place in the row, and — for a card the plan draws or deals — the turn it arrives on.
 */
data class CardWord(
    val who: Speaker,
    val nickname: String?,
    /** One-based, as people count. */
    val slot: Int,
    /** What the table says the card is, or null for a card nobody has named. */
    val rank: Rank?,
    /** The turn this card arrives on, for a card that is not on the table yet; null for one that is. */
    val fresh: Int? = null,
)

/**
 * A word of the sentence, typed so the screen can say it in nineteen languages.
 *
 * The words that decide nothing yet — the asked questions and the offers — are here too, so
 * that the shape of a turn is visible before it is decided.
 */
sealed interface Says {
    /** Whether this word says something that will happen, rather than asking for it or offering it. */
    val said: Boolean
        get() = when (this) {
            Draws, is Takes, is Drew, PlaysIt, is PutsDown, LetsItGo, is Called, is Trade, is Looks,
            is Names, is Forces, is Throws,
            -> true
            is Points -> rank != null
            WellSee, AndThen, WhatWith, WhichCard, is CallIt, WhichTwo, WhichToLookAt, WhichToPointAt,
            WhoDraws, WhichToThrow, AddThrow,
            -> false
        }

    /** Off the deck. A decision while the pile can be taken, a fact otherwise. */
    data object Draws : Says

    /** Takes the pile's card — named where the pile still shows it. */
    data class Takes(val rank: Rank?) : Says

    /** A fact: the card is face up in the player's hand, and the whole table has seen it. */
    data class Drew(val rank: Rank) : Says

    /** Nothing decided about what becomes of the card. Plain: it is not a decision. */
    data object WellSee : Says

    /** The next decision, on offer: what becomes of the card. */
    data object AndThen : Says

    /** Asked: what becomes of the card? */
    data object WhatWith : Says
    data object PlaysIt : Says
    data class PutsDown(val card: CardWord) : Says

    /** Asked: which card goes out? */
    data object WhichCard : Says
    data object LetsItGo : Says

    /**
     * On offer: call the put-down card, which is what lets it act. [rank] is what the table says
     * the card is, and null for a card nobody has read — where the call is a guess off the whole
     * rail rather than one word.
     */
    data class CallIt(val rank: Rank?) : Says

    /**
     * A call made on a card nobody can name.
     *
     * There is no word for a call made on a card the table *can* name: "puts down your Jack,
     * calls it a Jack" is the same word twice, and the action after the put-down is the call.
     * A card nobody has read is the other case — the put-down named no rank, so without this the
     * action below it would belong to nothing anybody can see.
     */
    data class Called(val rank: Rank) : Says

    /**
     * Two cards and the arrow between them: a Jack's trade, or a Queen's look with [swap]
     * saying whether she swaps.
     */
    data class Trade(val from: CardWord, val to: CardWord, val swap: Boolean) : Says
    data object WhichTwo : Says

    /** A 7 to 10's look at one card. */
    data class Looks(val card: CardWord) : Says
    data object WhichToLookAt : Says

    /** A King's pointed card, and what it is said to be — asked while [rank] is null. */
    data class Points(val card: CardWord, val rank: Rank?) : Says
    data object WhichToPointAt : Says

    /** The older, looser King: a rank named at whoever holds one. */
    data class Names(val rank: Rank) : Says
    data class Forces(val who: Speaker) : Says
    data object WhoDraws : Says

    /**
     * A throw-in: who, which card, what they say it is — and [blind] where the table cannot
     * vouch for the match: a card nobody has named, or a landing card nobody knows.
     */
    data class Throws(val who: Speaker, val card: CardWord?, val rank: Rank?, val blind: Boolean) : Says

    /** Asked: which card is thrown in? */
    data object WhichToThrow : Says

    /** On offer: another throw. */
    data object AddThrow : Says
}

/**
 * Which part of a turn a question is about, as a path into the lane.
 *
 * A card's action can sit at three depths — the turn's own step, a called put-down card's
 * action, a thrown card's action — and a King anywhere points at a card whose own action is
 * one deeper. A path names all of them with one shape, so the composer that answers "which two
 * cards does the Jack swap?" writes the answer into the right place without a case per depth.
 */
sealed interface Part {
    /** The turn's own step. */
    data object Own : Part

    /** What the called put-down card does. */
    data object Called : Part

    /** What the [index]th thrown card does. */
    data class Throw(val index: Int) : Part

    /** What the card a King at [of] points at does. */
    data class Pointed(val of: Part) : Part
}

/** The step at [part] of the lane, or null where nothing has been said there. */
internal fun Lane?.stepAt(part: Part): Step? = when (part) {
    Part.Own -> this?.step
    Part.Called -> (this?.step as? Step.PutDown)?.then
    is Part.Throw -> this?.tossIns?.getOrNull(part.index)?.then
    is Part.Pointed -> (stepAt(part.of) as? Step.Declare)?.then
}

/**
 * The lane with [step] written at [part]. A path that leads nowhere — a called action on a
 * lane that puts nothing down — leaves the lane as it was.
 */
internal fun Lane.writing(part: Part, step: Step?): Lane = when (part) {
    Part.Own -> {
        copy(step = step)
    }

    Part.Called -> {
        copy(step = (this.step as? Step.PutDown)?.copy(then = step) ?: this.step)
    }

    is Part.Throw -> {
        copy(
            tossIns = tossIns.mapIndexed { index, tossIn ->
                if (index == part.index) tossIn.copy(then = step) else tossIn
            },
        )
    }

    is Part.Pointed -> {
        val king = stepAt(part.of) as? Step.Declare
        if (king == null) this else writing(part.of, king.copy(then = step))
    }
}

/** The edit that writes [step] at [part] of [seat]'s turn — the lane's step, or its throw-ins. */
internal fun Lane?.edit(seat: String, part: Part, step: Step?): PlanEdit? {
    val lane = (this ?: Lane(seat)).writing(part, step)
    // Bound locally: a smart cast on a property from another module is not allowed.
    val own = lane.step
    return when {
        part.rootIsThrow() -> PlanEdit.SetTossIns(seat, lane.tossIns)
        own != null -> PlanEdit.SetLane(seat, own)
        else -> null
    }
}

/** Whether the part is a thrown card's action, at any depth — which is written into the throw-ins. */
private fun Part.rootIsThrow(): Boolean = when (this) {
    is Part.Throw -> true
    is Part.Pointed -> of.rootIsThrow()
    Part.Own, Part.Called -> false
}

/**
 * The table a turn starts from, and the two things the sentence needs beyond the view: whether
 * the pile's top is a card nobody knows, and the card face up in the seat's hand where the turn
 * is the one being played.
 *
 * @param fresh the cards the plan has dealt by this turn, tagged with the turn they arrive on.
 */
internal data class Start(
    val view: PlayerView,
    val pileUnknown: Int? = null,
    /** The drawn card, once it is face up: a fact the whole table has seen. */
    val drawn: Rank? = null,
    val fresh: Map<CardRef, Int> = emptyMap(),
) {
    /** The unplayed action card on the pile, or null — a card nobody knows can never be taken. */
    val takeable: Rank? get() = if (pileUnknown != null) null else view.takeableTop()?.rank
}

/** The turn [seat] starts from [view]: the drawn card is a fact only for the seat holding it. */
internal fun startOf(
    view: PlayerView,
    seat: String,
    pileUnknown: Int? = null,
    fresh: Map<CardRef, Int> = emptyMap(),
): Start {
    val pending = view.pendingAction?.takeIf { it.playerId == seat && it.from == PendingCardOrigin.DRAWING }
    val drawn = (pending?.card as? CardView.Visible)?.card?.rank
    return Start(view, pileUnknown, drawn, fresh)
}

/** The pile's card, where the turn takes it and there is one to take. */
internal fun Lane?.takenRank(start: Start): Rank? =
    start.takeable.takeIf { openingReads() == Opening.TAKE_THE_DISCARD }

/** Which pile the turn opens from, as said — the old step that takes the pile says it too. */
internal fun Lane?.openingReads(): Opening? =
    this?.opening ?: Opening.TAKE_THE_DISCARD.takeIf { this?.step == Step.TakeTheDiscard }

/**
 * The rank whose action [part] names, as far as the table can tell: the card face up in the
 * hand, the pile's card for a turn that takes it, the rank a put-down is called as, the rank a
 * card is thrown in as, the rank a King names or the pointed card is known to be. Null for a
 * card nobody has seen — a blind draw, which nothing can be aimed with.
 */
internal fun Lane?.rankAt(part: Part, start: Start): Rank? = when (part) {
    Part.Own -> when {
        start.drawn != null -> start.drawn
        openingReads() == Opening.TAKE_THE_DISCARD -> start.takeable
        else -> null
    }

    Part.Called -> (this?.step as? Step.PutDown)?.guess
    is Part.Throw -> this?.tossIns?.getOrNull(part.index)?.rank
    is Part.Pointed -> (stepAt(part.of) as? Step.Declare)?.let { king ->
        king.rank ?: king.card?.let { knownRankOf(start.view, it) }
    }
}

/** Who plays the action at [part]: the thrower for a thrown card, the turn's own seat otherwise. */
internal fun Lane?.actorAt(part: Part, seat: String): String = when (part) {
    is Part.Throw -> this?.tossIns?.getOrNull(part.index)?.seat ?: seat
    is Part.Pointed -> actorAt(part.of, seat)
    Part.Own, Part.Called -> seat
}

/**
 * The rank that lands on the pile at the end of the turn's own move — what a throw-in has to
 * match. Null where nobody can say: nothing decided, a card nobody knows let go or put down.
 */
internal fun Lane?.landing(start: Start): Rank? {
    val step = this?.step ?: return null
    val main = when (step) {
        is Step.PutDown -> knownRankOf(start.view, step.card)
        Step.Bin -> start.drawn
        Step.UseIt, Step.TakeTheDiscard, is Step.Swap, is Step.Peek, is Step.ForceDraw, is Step.Declare ->
            rankAt(Part.Own, start)
    }
    // A King's pointed card lands after the King, and is the last thing down.
    val king = when (step) {
        is Step.Declare -> step
        is Step.PutDown -> step.then as? Step.Declare
        else -> null
    }
    val pointed = king?.let { it.rank ?: it.card?.let { card -> knownRankOf(start.view, card) } }
    return pointed ?: main
}

/**
 * The part of the turn being built that is still open, in the order a turn happens.
 *
 * A turn is a sequence — take a card, do something with it, and if that plays an action the
 * action does something; then each throw-in's card does something — and the rail asks for the
 * first part nobody has answered. **What cannot be answered is not asked**: a card nobody has
 * seen yet has nothing to aim, and what becomes of the card is on offer rather than asked.
 */
internal sealed interface NextPart {
    /** Which two cards the known Jack or Queen at [part] trades. */
    data class Targets(val rank: Rank, val part: Part) : NextPart

    /** Which card the known 7 to 10 at [part] looks at — [own] for a 7 or an 8. */
    data class Look(val rank: Rank, val part: Part, val own: Boolean) : NextPart

    /** Which card the King at [part] points at. */
    data class Point(val part: Part) : NextPart

    /** What the card the King at [part] points at is: the rank the table said, or another. */
    data class KingsRank(val part: Part, val card: CardAt) : NextPart

    /** Who the known Ace at [part] makes draw. */
    data class Victim(val part: Part) : NextPart

    /** Nothing open: the turn reads whole, and can still be changed a part at a time. */
    data object Done : NextPart
}

internal fun nextPart(start: Start, lane: Lane?): NextPart {
    // Taking the pile's card is the whole of the turn: its action is what is asked next.
    val step = lane?.step
        ?: return lane.takenRank(
            start,
        )?.let { open(Part.Own, it, lane ?: return NextPart.Done, start) } ?: NextPart.Done

    // The turn's own card, then what its call goes on to do, then each throw.
    val own = when (step) {
        is Step.PutDown -> step.guess?.let { open(Part.Called, it, lane, start) }
        Step.UseIt, Step.TakeTheDiscard, is Step.Declare, is Step.Swap, is Step.Peek, is Step.ForceDraw ->
            lane.rankAt(Part.Own, start)?.let { open(Part.Own, it, lane, start) }
        Step.Bin -> null
    }
    if (own != null) return own
    lane.tossIns.forEachIndexed { index, tossIn ->
        tossIn.rank?.let { rank -> open(Part.Throw(index), rank, lane, start) }?.let { return it }
    }
    return NextPart.Done
}

/**
 * The question the action of a card of [rank] at [part] still has open, or null where it is
 * answered — or where the rank has nothing to ask.
 */
private fun open(part: Part, rank: Rank, lane: Lane, start: Start): NextPart? {
    val said = lane.stepAt(part)
    return when (rank) {
        Rank.JACK, Rank.QUEEN ->
            NextPart.Targets(rank, part).takeIf { said !is Step.Swap && said !is Step.Peek }
        Rank.SEVEN, Rank.EIGHT -> NextPart.Look(rank, part, own = true).takeIf { said !is Step.Peek }
        Rank.NINE, Rank.TEN -> NextPart.Look(rank, part, own = false).takeIf { said !is Step.Peek }
        Rank.KING -> kingsQuestion(part, said, lane, start)
        Rank.ACE -> NextPart.Victim(part).takeIf { said !is Step.ForceDraw }
        Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.JOKER -> null
    }
}

/** The card first, then its rank, then what the card does — the King's three questions in order. */
private fun kingsQuestion(part: Part, said: Step?, lane: Lane, start: Start): NextPart? {
    if (said !is Step.Declare) return NextPart.Point(part)
    val card = said.card ?: return null
    val rank = said.rank ?: return NextPart.KingsRank(part, card)
    return open(Part.Pointed(part), rank, lane, start)
}

/**
 * The words of one turn, and what touching each one does.
 *
 * @param start the table the turn starts from, which is what its cards are named against.
 * @param question the question open about this turn, if any: it is the word that is lit.
 * @param asking what the felt is answering, which lights the word it answers.
 * @param next what the turn still needs answered, lit where nothing else is asked.
 */
@Suppress("LongParameterList")
internal class Words(
    val view: PlayerView,
    val start: Start,
    val seat: String,
    val lane: Lane?,
    val at: Int,
    val question: Question? = null,
    val asking: Asking? = null,
    val next: NextPart? = null,
    val editable: Boolean = false,
) {
    private val drawn: Rank? = start.drawn
    private val takes: Boolean = lane.openingReads() == Opening.TAKE_THE_DISCARD

    private fun quiet(move: Move.Quiet): Move.Quiet? = move.takeIf { editable }

    /** The way out of an open question: touching the asked word closes it onto the plan. */
    private fun back(): Move.Quiet? = quiet(Move.Ask(Question.ThePlan(at = at)))
    private fun who(id: String): Speaker = speakerFor(view, id)
    private fun nickname(id: String): String? = view.players.firstOrNull { it.id == id }?.nickname

    /**
     * A card as the sentence draws it: whose, what it is said to be, its place, and whether it
     * is not there yet.
     */
    fun cardWord(at: CardAt): CardWord = CardWord(
        who = who(at.seat),
        nickname = nickname(at.seat),
        slot = at.position + 1,
        rank = knownRankOf(start.view, at),
        fresh = start.fresh[CardRef(at.seat, at.position)],
    )

    /** The turn whole: its own row, then the throws. */
    fun clauses(): List<Clause> = buildList {
        add(Clause(Part.Own, ownClause()))
        addAll(throwClauses())
    }

    /** Where the card comes from, what becomes of it, and what it then does. */
    fun ownClause(): List<Slot> = buildList {
        add(openingSlot())
        when (question) {
            is Question.Doing -> add(Slot(Says.WhatWith, back(), asked = true))
            is Question.PuttingDown -> add(Slot(Says.WhichCard, back(), asked = true))
            else -> addAll(stepSlots(lane?.step))
        }
    }

    /** What becomes of the card and what it then does — or, with nothing said yet, what could. */
    private fun stepSlots(step: Step?): List<Slot> = when (step) {
        null -> undecidedSlots()
        is Step.PutDown -> putDownSlots(step)
        Step.Bin -> listOf(Slot(Says.LetsItGo, quiet(Move.Ask(Question.Doing(seat, at)))))
        // "Play whatever it is" is plain before the card is face up: nothing to aim, nothing else
        // to say. Once it is face up, playing it is a decision.
        Step.UseIt, Step.TakeTheDiscard ->
            playsItSlots() + actionSlots(Part.Own, lane.rankAt(Part.Own, start), null)
        // "Plays it" is a decision only once the card is face up; before that it is the sentence
        // no table can say, and it is drawn plain and left unplayed.
        is Step.Swap, is Step.Peek, is Step.Declare, is Step.ForceDraw ->
            playsItSlots() + actionSlots(Part.Own, lane.rankAt(Part.Own, start), step)
    }

    /**
     * Nothing said: the pile's card is the turn where the turn takes it, and what its action
     * needs is the next word; otherwise "we'll see", with the next decision on offer.
     */
    private fun undecidedSlots(): List<Slot> {
        val taken = lane.takenRank(start)
        if (taken != null) return actionSlots(Part.Own, taken, null)
        return buildList {
            if (drawn == null) add(Slot(Says.WellSee))
            val andThen = quiet(Move.Ask(Question.Doing(seat, at)))
            if (andThen != null) add(Slot(Says.AndThen, andThen, offer = true))
        }
    }

    /** The card put down, then its call: offered while it is not made, and what it does once it is. */
    private fun putDownSlots(step: Step.PutDown): List<Slot> = buildList {
        add(putDownSlot(step))
        val call = step.guess
        if (call == null) {
            callOffer(step)?.let { add(it) }
        } else {
            // Only where the put-down itself named no rank: see `Says.Called`.
            if (knownRankOf(start.view, step.card) == null) add(callSaid(call))
            addAll(actionSlots(Part.Called, call, step.then))
        }
    }

    /**
     * "Plays it": a decision once the card is face up, plain before — and nothing at all where
     * the turn takes the pile's card, since taking it is playing it.
     */
    private fun playsItSlots(): List<Slot> {
        if (takes) return emptyList()
        return listOf(Slot(Says.PlaysIt, quiet(Move.Ask(Question.Doing(seat, at))).takeIf { drawn != null }))
    }

    private fun openingSlot(): Slot = when {
        drawn != null -> Slot(Says.Drew(drawn))
        takes -> Slot(Says.Takes(start.takeable), quiet(Move.Plan(PlanEdit.OpenLane(seat, Opening.DRAW))))
        // One pile to draw from is a fact; a pile that can be taken makes the draw a decision.
        start.takeable != null ->
            Slot(Says.Draws, quiet(Move.Plan(PlanEdit.OpenLane(seat, Opening.TAKE_THE_DISCARD))))
        else -> Slot(Says.Draws)
    }

    private fun putDownSlot(step: Step.PutDown): Slot =
        Slot(Says.PutsDown(cardWord(step.card)), quiet(Move.Ask(Question.PuttingDown(seat, at))))

    /**
     * "+ calls it", while no call is made: one word for a card the table knows to be an action
     * card, and the open rail for a card nobody has read.
     *
     * A card known to be a two through a six is offered nothing at all, because there is nothing
     * to win: calling it right plays an action that does not exist, and calling it anything else
     * costs a penalty card.
     */
    private fun callOffer(step: Step.PutDown): Slot? {
        val known = knownRankOf(start.view, step.card)
        if (known == null) {
            val rail = quiet(Move.Ask(Question.Calling(seat, at))) ?: return null
            return Slot(Says.CallIt(null), rail, offer = true)
        }
        if (!hasAction(known)) return null
        val call = quiet(Move.Plan(PlanEdit.SetLane(seat, step.copy(guess = known)))) ?: return null
        return Slot(Says.CallIt(known), call, offer = true)
    }

    /** The call made on a card nobody can name, and the rail back to change it. */
    private fun callSaid(call: Rank): Slot = Slot(
        Says.Called(call),
        quiet(Move.Ask(Question.Calling(seat, at))),
        asked = question is Question.Calling,
    )

    /** What the card of [rank] at [part] does: said, asked for, or nothing for a card that does nothing. */
    fun actionSlots(part: Part, rank: Rank?, said: Step?): List<Slot> {
        val askedHere = if (asking == null) next?.partOrNull() == part else asking.partOrNull() == part
        return if (said == null || said.saysNothingOfItself()) {
            openSlot(part, rank, askedHere)
        } else {
            saidSlots(part, rank, said, askedHere)
        }
    }

    /** The action as it was said, in its own words. */
    private fun saidSlots(part: Part, rank: Rank?, said: Step, lit: Boolean): List<Slot> = buildList {
        val aim = quiet(Move.Ask(Question.Aiming(seat, at, part)))
        when (said) {
            is Step.Swap -> {
                val look = Step.Peek(said.from, said.to).takeIf { rank == Rank.QUEEN }
                val toggle = look?.let { lane.edit(seat, part, it) }?.let { quiet(Move.Plan(it)) }
                add(
                    Slot(
                        Says.Trade(cardWord(said.from), cardWord(said.to), swap = true),
                        aim,
                        asked = lit,
                        toggle = toggle,
                    ),
                )
            }

            is Step.Peek -> {
                val also = said.also
                if (also == null) {
                    add(Slot(Says.Looks(cardWord(said.card)), aim, asked = lit))
                } else {
                    val swap = lane.edit(seat, part, Step.Swap(said.card, also))?.let { quiet(Move.Plan(it)) }
                    add(
                        Slot(
                            Says.Trade(cardWord(said.card), cardWord(also), swap = false),
                            aim,
                            asked = lit,
                            toggle = swap,
                        ),
                    )
                }
            }

            is Step.Declare -> {
                addAll(kingSlots(part, said, lit, aim))
            }

            is Step.ForceDraw -> {
                val forcing = quiet(Move.Ask(Question.Forcing(seat, at, part)))
                add(Slot(Says.Forces(who(said.seat)), forcing, asked = lit))
            }

            is Step.PutDown, Step.Bin, Step.UseIt, Step.TakeTheDiscard -> {
                Unit
            }
        }
    }

    /** "[card] is a 5", asked while the rank is still to be said; then what that card does, one deeper. */
    private fun kingSlots(
        part: Part,
        king: Step.Declare,
        lit: Boolean,
        aim: Move.Quiet?,
    ): List<Slot> = buildList {
        val card = king.card
        val rank = king.rank
        if (card == null) {
            val naming = quiet(Move.Ask(Question.Naming(seat, at, part)))
            if (rank != null) add(Slot(Says.Names(rank), naming, asked = lit))
            return@buildList
        }
        add(Slot(Says.Points(cardWord(card), rank), aim, asked = lit))
        if (rank != null) addAll(actionSlots(Part.Pointed(part), rank, king.then))
    }

    /** Nothing said yet: the open question, in the place its answer will go — or nothing to ask. */
    private fun openSlot(part: Part, rank: Rank?, lit: Boolean): List<Slot> {
        val aim = quiet(Move.Ask(Question.Aiming(seat, at, part)))
        val slot = when (rank) {
            Rank.JACK, Rank.QUEEN -> Slot(Says.WhichTwo, aim, asked = lit)
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN -> Slot(Says.WhichToLookAt, aim, asked = lit)
            Rank.KING -> Slot(Says.WhichToPointAt, aim, asked = lit)
            Rank.ACE -> Slot(Says.WhoDraws, quiet(Move.Ask(Question.Forcing(seat, at, part))), asked = lit)
            Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.JOKER, null -> null
        }
        return listOfNotNull(slot)
    }

    /** "Then Tide throws in a five, and …" — one clause per throw, then the throw on offer. */
    fun throwClauses(): List<Clause> = buildList {
        val throws = lane?.tossIns.orEmpty()
        val landing = lane.landing(start)
        throws.forEachIndexed { index, tossIn ->
            add(Clause(Part.Throw(index), throwClause(index, tossIn, landing)))
        }
        val asking = question is Question.Throwing && question.index >= throws.size
        val another = quiet(Move.Ask(Question.Throwing(seat, at, throws.size)))
        when {
            asking -> add(Clause(null, listOf(Slot(Says.WhichToThrow, back(), asked = true))))
            another != null -> add(Clause(null, listOf(Slot(Says.AddThrow, another, offer = true))))
        }
    }

    private fun throwClause(index: Int, tossIn: TossIn, landing: Rank?): List<Slot> = buildList {
        if (question is Question.Throwing && question.index == index) {
            add(Slot(Says.WhichToThrow, back(), asked = true))
            return@buildList
        }
        val card = tossIn.card ?: heldCard(tossIn.seat, tossIn.rank)
        val rank = tossIn.rank
        val blind = rank == null || landing == null || rank != landing
        add(
            Slot(
                Says.Throws(who(tossIn.seat), card?.let { cardWord(it) }, rank, blind),
                quiet(Move.Ask(Question.Throwing(seat, at, index))),
            ),
        )
        if (rank != null) addAll(actionSlots(Part.Throw(index), rank, tossIn.then))
    }

    /** The card a throw named by rank alone is found at: the first of the seat's known to be that rank. */
    private fun heldCard(thrower: String, rank: Rank?): CardAt? {
        if (rank == null) return null
        val hand = start.view.players.firstOrNull { it.id == thrower } ?: return null
        val position = hand.cards.indices.firstOrNull { knownRankOf(start.view, hand, it) == rank }
            ?: return null
        return CardAt(thrower, position)
    }
}

/** The steps that are not a card's action, and so have no word of their own where an action goes. */
private fun Step.saysNothingOfItself(): Boolean = when (this) {
    is Step.PutDown, Step.Bin, Step.UseIt, Step.TakeTheDiscard -> true
    is Step.Swap, is Step.Peek, is Step.Declare, is Step.ForceDraw -> false
}

internal fun Asking.partOrNull(): Part? = when (this) {
    is Asking.Trade -> part
    is Asking.Look -> part
    is Asking.Point -> part
    is Asking.Throw -> Part.Throw(index)
    Asking.WhichCard -> null
}

internal fun NextPart.partOrNull(): Part? = when (this) {
    is NextPart.Targets -> part
    is NextPart.Look -> part
    is NextPart.Point -> part
    is NextPart.KingsRank -> part
    is NextPart.Victim -> part
    NextPart.Done -> null
}

// ---------------------------------------------------------------------------- the live rail

/**
 * The plan's row on the live rail: what the plan says about the turn on play, as information
 * and nothing else. There for the whole final round, empty when nothing is planned, so that the
 * buttons under it never move.
 */
data class PlanLine(val who: Speaker, val nickname: String?, val says: List<Says>)

/**
 * The row for [view]'s table: the turn on play — or, while play is still parked on the caller,
 * the first turn to come. Null outside a final round.
 */
internal fun planLineFor(view: PlayerView, plan: CoalitionPlan?): PlanLine? {
    val caller = view.vintoCallerId ?: return null
    if (view.phase != GamePhase.FINAL) return null
    val coalition = coalitionInTurnOrder(view.players.map { it.id }, caller)
    val onPlay = view.players.getOrNull(view.currentPlayerIndex)?.id
    val seat = onPlay?.takeIf { it in coalition } ?: coalition.firstOrNull() ?: return null
    val lane = plan?.lanes?.firstOrNull { it.seat == seat }
    val words = Words(view, startOf(view, seat), seat, lane, at = 0)
    val says = if (lane == null) {
        emptyList()
    } else {
        words.clauses().flatMap { clause -> clause.slots.map { it.says } }
    }
    return PlanLine(
        who = speakerFor(view, seat),
        nickname = view.players.firstOrNull { it.id == seat }?.nickname,
        says = says.filter { it.said },
    )
}
