package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Believed
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Lane
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TossIn

/**
 * How a touch on the felt becomes one `PlanEdit` (design D5).
 *
 * What is asked decides what is offered. A put-down: any of the seat's own cards, carried to
 * the pile or touched. A trade: onto another player's card, which is why the caller's cards are
 * neither a source nor a target and why a hand cannot swap with itself. A look, or the card a
 * King points at: one touch. A throw-in: one touch on a coalition card — a card known to match
 * the landing rank, or one nobody has named, which throws blind.
 *
 * The palette for a trade is **what has been said**: a card somebody has claimed, or one of
 * the viewer's own they have read. A card nobody knows anything about is not on offer for
 * moving — a plan that moved it would be moving a guess — and that is what makes declaring
 * worth doing. Only what `CoalitionPlan.edited` would accept is here: a lane the door refuses
 * gets no composer at all rather than one whose drops are refused.
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
 * own `Table.taps`, so the two cannot diverge.
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
    /**
     * Cards that answer a question with **one** touch — the card a 9 looks at, the card a King
     * points at, the card thrown in — beside the two-touch trades in [drops].
     */
    val touches: Map<CardRef, Move.Plan> = emptyMap(),
    /**
     * Of the touchable cards, the ones the table can vouch for: a throw of one of these is a
     * match, and the felt marks them in gold; the rest throw blind and are merely lit.
     */
    val wanted: Set<CardRef> = emptySet(),
) {
    /** The cards a drag may begin on: the ones that can reach somewhere. */
    val sources: Set<CardRef> get() = drops.filterValues { it.isNotEmpty() }.keys
}

/**
 * What the felt is answering, when it is answering anything: which cards it lights and what a
 * touch writes.
 */
internal sealed interface Asking {
    /** Which card of the turn's own seat goes out — carried to the pile, or touched. */
    data object WhichCard : Asking

    /** Which two cards the Jack or Queen at [part] trades. */
    data class Trade(val part: Part) : Asking

    /** Which card the 7 to 10 at [part] looks at — [own] for a 7 or an 8. */
    data class Look(val part: Part, val own: Boolean) : Asking

    /** Which card the King at [part] points at: any coalition card, named or not. */
    data class Point(val part: Part) : Asking

    /** Which card is thrown in as the [index]th throw of the turn. */
    data class Throw(val index: Int) : Asking
}

/**
 * The composer for [seat]'s turn from [start], answering [asking] — or the put-down alone when
 * nothing is asked.
 */
internal fun composerFor(
    start: Start,
    seat: String,
    lane: Lane?,
    asking: Asking?,
): PlanComposer = when (asking) {
    Asking.WhichCard, null -> whichCardComposer(start.view, seat)
    is Asking.Look -> touchComposer(start.view, seat, lane, asking)
    is Asking.Point -> touchComposer(start.view, seat, lane, asking)
    is Asking.Trade -> tradeComposer(start.view, seat, lane, asking.part)
    is Asking.Throw -> throwComposer(start, seat, lane, asking.index)
}

/** A put-down of one of the seat's own cards, as an edit of the turn's own step. */
private fun putDownEdit(there: PlayerView, seat: String, ref: CardRef): Move.Plan =
    Move.Plan(game.vinto.shapes.PlanEdit.SetLane(seat, Step.PutDown(cardAt(there, ref))))

/**
 * Which card goes out: one answer per card of the seat's own, known or not — any of them may
 * go out, and one nobody knows lands a card nobody knows.
 */
private fun whichCardComposer(there: PlayerView, seat: String): PlanComposer {
    val own = there.players.firstOrNull { it.id == seat }?.cards?.indices?.map { CardRef(seat, it) }.orEmpty()
    return PlanComposer(
        seat,
        own.associateWith { ref ->
            mapOf<PlanTarget, Move.Plan>(PlanTarget.Discard to putDownEdit(there, seat, ref))
        },
    )
}

/** Every coalition card the table has been told about: what a trade may name. */
private fun spokenCards(there: PlayerView): List<CardRef> = there.players
    .filter { it.id != there.vintoCallerId }
    .flatMap { hand ->
        hand.cards.indices
            .filter { spokenFor(there, hand, it) }
            .map { CardRef(hand.id, it) }
    }

/** Every coalition card, named or not. */
private fun coalitionCards(there: PlayerView): List<CardRef> = there.players
    .filter { it.id != there.vintoCallerId }
    .flatMap { hand -> hand.cards.indices.map { CardRef(hand.id, it) } }

/** A look, or a King's pointed card: one touch. */
private fun touchComposer(there: PlayerView, seat: String, lane: Lane?, asking: Asking): PlanComposer {
    val part = asking.partOrNull() ?: Part.Own
    val actor = lane.actorAt(part, seat)
    val touchable = when (asking) {
        is Asking.Look ->
            coalitionCards(there).filter { ref ->
                if (asking.own) ref.playerId == actor else ref.playerId != actor
            }

        // Any card at all: the King may name a card nobody has spoken about, which is a guess,
        // and the rank comes afterwards. Never the caller's.
        is Asking.Point -> coalitionCards(there)
        Asking.WhichCard, is Asking.Trade, is Asking.Throw -> emptyList()
    }
    val touches = touchable.mapNotNull { ref ->
        val step = if (asking is Asking.Point) {
            Step.Declare(rank = null, card = cardAt(there, ref))
        } else {
            Step.Peek(cardAt(there, ref))
        }
        lane.edit(seat, part, step)?.let { ref to Move.Plan(it) }
    }.toMap()
    return PlanComposer(seat, emptyMap(), touches)
}

/**
 * A trade: the one at [part], between two hands, of any coalition cards.
 *
 * **Any of them, named or not.** This read `spokenCards` — only what the table had been told
 * about — which is the right rule for a step that *claims* something and the wrong one for a
 * Jack, because a Jack is blind: what it moves is decided by where the cards are and not by
 * what anybody has said about them. A coalition that may only trade cards it has already
 * described cannot plan the commonest Jack there is, and the felt simply did not respond to
 * the cards a person tried to pick — reported from a phone as not being able to select two
 * cards at all.
 *
 * What a card is *worth* to the plan is a separate question, and one the board already answers
 * honestly: an unnamed card prices at the deck's mean and says nothing about its rank.
 */
private fun tradeComposer(there: PlayerView, seat: String, lane: Lane?, part: Part): PlanComposer {
    // The card put down is on the pile by the time a called Jack acts, so it is not one the
    // Jack can move.
    val gone = (lane?.step as? Step.PutDown)
        ?.takeIf { part == Part.Called }
        ?.let { CardRef(it.card.seat, it.card.position) }
    val tradable = coalitionCards(there).filter { it != gone }

    fun trade(from: CardRef, to: CardRef): Move.Plan? =
        lane.edit(seat, part, Step.Swap(cardAt(there, from), cardAt(there, to)))?.let { Move.Plan(it) }

    val drops = tradable.associateWith { from ->
        buildMap<PlanTarget, Move.Plan> {
            tradable
                .filter { it.playerId != from.playerId }
                .forEach { to -> trade(from, to)?.let { put(PlanTarget.Card(to), it) } }
        }
    }
    return PlanComposer(seat = seat, drops = drops)
}

/**
 * A throw-in: one touch on a coalition card. A card known to be the rank that lands throws as
 * that rank and is wanted; a card nobody has named throws blind; a card known to be some other
 * rank can never match and is not offered. Where the landing card is unknown, every throw is
 * blind. The thrower is the card's owner — you can only throw your own card.
 */
private fun throwComposer(start: Start, seat: String, lane: Lane?, index: Int): PlanComposer {
    val there = start.view
    val landing = lane.landing(start)
    val standing = lane?.tossIns.orEmpty()
    // The card put down is on the pile: not there to throw.
    val gone = (lane?.step as? Step.PutDown)?.let { CardRef(it.card.seat, it.card.position) }

    fun withThrow(tossIn: TossIn): List<TossIn> =
        if (index <
            standing.size
        ) {
            standing.mapIndexed { at, was -> if (at == index) tossIn else was }
        } else {
            standing + tossIn
        }

    val offered = coalitionCards(there).filter { it != gone }.mapNotNull { ref ->
        val known = knownRankOf(there, CardAt(ref.playerId, ref.position))
        when {
            known == null -> ref to null
            landing != null && known == landing -> ref to known
            else -> null
        }
    }
    val touches = offered.associate { (ref, rank) ->
        val tossIn = TossIn(ref.playerId, rank, card = cardAt(there, ref))
        ref to Move.Plan(game.vinto.shapes.PlanEdit.SetTossIns(seat, withThrow(tossIn)))
    }
    return PlanComposer(
        seat,
        emptyMap(),
        touches,
        wanted = offered.filter { it.second != null }.map { it.first }.toSet(),
    )
}

/**
 * The full set of ranks a King at [part] may name: lit for what the pointed card is said to be,
 * or — for the older King that points at nobody — for the ranks the coalition is known to hold.
 */
internal fun declareRanks(view: PlayerView, seat: String, lane: Lane?, part: Part): List<RankChoice> {
    val standing = lane.stepAt(part) as? Step.Declare
    val known = standing?.card?.let { believedOnView(view, it).candidates }
        ?: ranksTheCoalitionIsKnownToHold(view)
    return ALL_RANKS.mapNotNull { rank ->
        val declared = standing?.copy(rank = rank) ?: Step.Declare(rank)
        val edit = lane.edit(seat, part, declared) ?: return@mapNotNull null
        RankChoice(rank, Move.Plan(edit), muted = rank !in known)
    }
}

/**
 * Whether a turn still names cards that are there, and names nothing it cannot: an action as
 * the turn's own step needs a card face up to play it with — the pile's, or the one drawn.
 *
 * The same question `rehearse()` asks before building a frame, asked here so the felt can mark
 * the turn as unplayable instead of leaving a numbered gap nobody can account for (design D6).
 */
internal fun drawable(start: Start, lane: Lane): Boolean {
    val view = start.view
    val own = when (val step = lane.step) {
        null -> true
        is Step.Swap, is Step.Peek, is Step.Declare, is Step.ForceDraw ->
            lane.rankAt(Part.Own, start) != null && drawable(view, step)
        is Step.PutDown, Step.Bin, Step.UseIt, Step.TakeTheDiscard -> drawable(view, step)
    }
    return own && lane.tossIns.all { tossIn ->
        val card = tossIn.card
        (card == null || view.holds(card)) && tossIn.then?.let { drawable(view, it) } != false
    }
}

private fun drawable(view: PlayerView, step: Step): Boolean = when (step) {
    is Step.Swap -> {
        view.holds(step.from) && view.holds(step.to)
    }

    is Step.PutDown -> {
        view.holds(step.card) && step.then?.let { drawable(view, it) } != false
    }

    is Step.Declare -> {
        val pointed = step.card?.let { view.holds(it) } != false
        pointed && step.then?.let { drawable(view, it) } != false
    }

    is Step.Peek -> {
        view.holds(step.card) && step.also?.let { view.holds(it) } != false
    }

    // Nothing moves, or a seat is named rather than a card, so nothing can fail to be there.
    is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> {
        true
    }
}

/** The cards a turn names, as the felt refers to them: its step's, and every throw's, thrown or acted on. */
internal fun cardsNamedBy(there: PlayerView, lane: Lane): Set<CardRef> {
    val named = lane.step?.let { cardsMoved(it) }.orEmpty() +
        lane.tossIns.flatMap { tossIn ->
            listOfNotNull(
                tossIn.card?.let { CardRef(it.seat, it.position) },
            ) + tossIn.then?.let { cardsMoved(it) }.orEmpty()
        }
    return named
        .filter { ref -> there.holds(CardAt(ref.playerId, ref.position)) }
        .toSet()
}

private fun cardsMoved(step: Step): Set<CardRef> = when (step) {
    is Step.Swap -> setOf(
        CardRef(step.from.seat, step.from.position),
        CardRef(step.to.seat, step.to.position),
    )

    is Step.PutDown ->
        setOf(CardRef(step.card.seat, step.card.position)) + step.then?.let { cardsMoved(it) }.orEmpty()

    is Step.Declare ->
        step.card?.let { setOf(CardRef(it.seat, it.position)) }.orEmpty() +
            step.then?.let { cardsMoved(it) }.orEmpty()

    is Step.Peek -> listOfNotNull(step.card, step.also).map { CardRef(it.seat, it.position) }.toSet()
    is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> emptySet()
}

internal fun PlayerView.holds(at: CardAt): Boolean =
    players.firstOrNull { it.id == at.seat }?.cards?.indices?.contains(at.position) == true

/** The unplayed action card on the pile — the one card a plan can name in advance — or null. */
internal fun PlayerView.takeableTop(): Card? =
    discardTop?.takeIf { it.actionText != null && !it.played }

/**
 * The rank of a card as this table can name it: what standing claims settle on, or what the
 * viewer can see of their own hand. Null where nobody could say.
 */
internal fun knownRankOf(view: PlayerView, hand: PlayerSeatView, position: Int): Rank? {
    believedOnView(hand, position).candidates.singleOrNull()?.let { return it }
    if (hand.id != view.viewerId) return null
    return (hand.cards.getOrNull(position) as? CardView.Visible)?.card?.rank
}

internal fun knownRankOf(view: PlayerView, at: CardAt): Rank? =
    view.players.firstOrNull { it.id == at.seat }?.let { knownRankOf(view, it, at.position) }

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

/** What the table believes about [at]. */
internal fun believedOnView(view: PlayerView, at: CardAt): Believed =
    view.players.firstOrNull { it.id == at.seat }?.let { believedOnView(it, at.position) }
        ?: Believed(ALL_RANKS.toSet(), disputed = false, sources = emptyList())

/** A card the plan may name in a trade: claimed by somebody, or one of the viewer's own they have read. */
private fun spokenFor(view: PlayerView, hand: PlayerSeatView, position: Int): Boolean {
    val claimed = believedOnView(hand, position).sources.isNotEmpty()
    val ownAndRead = hand.id == view.viewerId && position in hand.knownCardPositions
    return claimed || ownAndRead
}

internal fun cardAt(view: PlayerView, ref: CardRef): CardAt {
    val hand = view.players.firstOrNull { it.id == ref.playerId }
    val anchor = hand?.let { believedOnView(it, ref.position).sources.firstOrNull() }
    return CardAt(ref.playerId, ref.position, anchor)
}
