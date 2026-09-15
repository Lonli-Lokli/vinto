package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PendingActionView
import game.vinto.engine.PendingTargetView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.GameAction
import game.vinto.shapes.Lane
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.Step
import game.vinto.shapes.SwapCardPayload
import game.vinto.shapes.TargetType
import game.vinto.shapes.TossIn
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import game.vinto.shapes.laneOf

/**
 * A plan, played back as an animation of what it would do.
 *
 * Your example of a plan is four sentences that take a paragraph to write and are hard to read
 * in any language; the same plan is a few taps and one animation. So the coalition **watches**
 * a plan rather than reading it — cards actually moving, in order, settling on the hands the
 * plan produces. Two players with no language in common can agree a four-step line that way.
 *
 * It reuses the real choreography: `choreograph(action, before, after)` is a pure function of
 * an action and two views, and does not care whether the views it is given ever happened.
 *
 * ## The correction
 *
 * The intent was to build each ghost table with `GameEngine.reduce` on a hypothetical
 * `GameState`. **A client has no `GameState` and must not acquire one.** It only ever holds a
 * redacted `PlayerView` — that is the whole of design R1, and it is what makes a solo game and
 * an online one the same screens. So a ghost table is built by **transforming the view**, which
 * is all a swap is anyway. Nothing here needs the engine, and nothing here can see a card the
 * seat was not already shown.
 *
 * ## What the film will and will not show
 *
 * A plan can only speak about cards the table can see or has been told about. So an action as
 * a turn's own step — a trade, a look, a King, an Ace — plays only with a card face up to play
 * it with: the pile's unused action card the turn takes, or the card the seat has drawn and is
 * holding right now. *"Draws, plays it, trades…"* is a sentence no table can say, and the film
 * refuses to draw it rather than inventing a Jack.
 *
 * A card the plan **draws or deals** is not on the table yet, and the film says so: every such
 * card is tagged with the turn it arrives on ([Rehearsal.fresh]) so the felt can draw it rose,
 * from the turn it lands to the last page. A card let go or put down that nobody knows leaves
 * the pile's top unknown ([Rehearsal.pileUnknown]) — and nothing unknown can be taken.
 *
 * ## A turn is everybody's actions, in order
 *
 * A turn is one member's move **and** the throw-ins it sets off, each playing its card's action
 * from the table the throw leaves ([Lane.tossIns]). A **blind** throw — a card nobody has named,
 * thrown to see it — is drawn as its likely outcome: the card comes back and a penalty card
 * lands beside it, rose and tagged. A turn already played is history and plays nothing.
 *
 * ## What was said follows the card
 *
 * A ghost table keeps its claims on the cards they are about: a trade moves the two cards'
 * claims with them, a card leaving a hand takes its claim with it and the claims after it slide
 * down a place, and a card put down arrives with what the table has seen of it — the drawn
 * card's rank, when it is face up — or nothing.
 */
fun rehearse(view: PlayerView, plan: CoalitionPlan): List<Frame> =
    rehearsal(view, plan).frames.filterNotNull()

/**
 * A plan as the **transport** reads it (design D14): the positions it stops at, and the
 * pictures between them.
 *
 * One position per turn boundary — the table now, and the table after each of at most three
 * turns — so [tables] is one longer than [frames], and position *k* renders the table after
 * turns 1..*k* and no further. A turn with nothing to draw keeps its place with a null frame
 * rather than being dropped, so ② means the same turn to every member reading it (design D6).
 */
data class Rehearsal(
    /** One per position, 0..*n*: the table now, then the table after each turn. */
    val tables: List<PlayerView>,
    /** One per turn, in order: what to play on the way to that position, or null if nothing. */
    val frames: List<Frame?>,
    /**
     * One per position: the cards the plan has drawn or dealt by then — a put-down's
     * replacement, an Ace's forced draw, a blind throw's penalty card — at the places they lie
     * in that position's table, each tagged with the turn it arrives on.
     */
    val fresh: List<Map<CardRef, Int>> = tables.map { emptyMap() },
    /** One per position: the turn since which the pile's top is a card nobody knows, or null. */
    val pileUnknown: List<Int?> = tables.map { null },
) {
    /** The last position: the table the plan arrives at (design D10). */
    val arrival: PlayerView get() = tables.last()

    /** How many positions the transport has beyond the first. */
    val turns: Int get() = frames.size

    /** The film from position [at] onward. */
    fun from(at: Int): List<Frame> = between(at, turns)

    /**
     * The film between two positions — what plays on the way from [from] to [to].
     *
     * **The drop comes before the filter.** [frames] is one per turn *including the turns with
     * nothing to draw*, so a turn's index is its position; filtered first, the list is shorter
     * than the plan and dropping by a position number takes the wrong frames off the front.
     */
    fun between(from: Int, to: Int): List<Frame> =
        frames.drop(from.coerceIn(0, turns)).take((to - from).coerceAtLeast(0)).filterNotNull()
}

/** The plan, resolved into the transport's positions. See [Rehearsal]. */
fun rehearsal(view: PlayerView, plan: CoalitionPlan): Rehearsal {
    val caller = view.vintoCallerId
    val coalition = caller?.let { coalitionInTurnOrder(view.players.map { it.id }, it) }.orEmpty()
    val playing = coalition.indexOf(view.players.getOrNull(view.currentPlayerIndex)?.id)

    var table = Ghost(view)
    val tables = mutableListOf(table)
    val frames = mutableListOf<Frame?>()

    for ((index, lane) in plan.turnsOf(view).withIndex()) {
        val turn = index + 1
        // A turn already played is history: nothing to draw, and the table is as it is. A turn
        // still to come — the one on play included — is played from here.
        val start = startOf(table.view, lane.seat, table.pileUnknown, table.fresh)
        val played = if (index < playing) null else table.playTurn(lane, turn, start)
        if (played == null) {
            // The turn still exists — it is somebody's turn either way — so it keeps its
            // position and the transport still stops on it. There is simply nothing to draw.
            frames += null
            tables += table
            continue
        }

        frames += Frame(
            action = played.action,
            scenes = played.scenes,
            view = played.after.view,
            ghost = true,
            turn = turn,
            fresh = played.after.fresh,
            pileUnknown = played.after.pileUnknown,
        )
        table = played.after
        tables += played.after
    }

    return Rehearsal(
        tables = tables.map { it.view },
        frames = frames,
        fresh = tables.map { it.fresh },
        pileUnknown = tables.map { it.pileUnknown },
    )
}

/**
 * A ghost table: the view as the plan leaves it, which of its cards the plan dealt and when,
 * and whether its pile's top is a card nobody knows.
 *
 * The dealt cards ride beside the view rather than in it because a `PlayerView` has no word
 * for "a card nobody has seen *yet*" — every hidden card is the same hidden card — and the felt
 * needs one. They follow the cards: a dealt card traded away is still a dealt card, and one
 * thrown in is gone.
 */
private data class Ghost(
    val view: PlayerView,
    val fresh: Map<CardRef, Int> = emptyMap(),
    val pileUnknown: Int? = null,
)

/** One step played out: the table it leaves, and everything it gave the table to watch. */
private class Played(val after: Ghost, val scenes: List<Scene>, val action: GameAction)

/**
 * A whole turn from this table: the seat's own step, then the throw-ins in the order the plan
 * says, each from the table the one before it leaves. Null where there is nothing to draw —
 * no step and no throw-in, or a step naming a card that is not there, or an action with no card
 * to play it with.
 *
 * A throw-in that cannot be drawn is left out rather than failing the turn: the step still
 * happens, and the reading marks the throw as broken where the news belongs (`PlanHealth`).
 */
private fun Ghost.playTurn(lane: Lane, turn: Int, start: Start): Played? {
    var table = this
    val scenes = mutableListOf<Scene>()
    var action: GameAction? = null

    val step = lane.step
    if (step == null) {
        val blind = table.blindly(lane.seat, turn, start.drawn)
        scenes += blind.scenes
        table = blind.after
        action = blind.action
    } else {
        val played = table.play(lane.seat, step, turn, start.drawn, lane.rankAt(Part.Own, start))
            ?: return null
        scenes += played.scenes
        table = played.after
        action = played.action
    }
    for (tossIn in lane.tossIns) {
        val thrown = table.throwIn(tossIn, turn) ?: continue
        scenes += thrown.scenes
        table = thrown.after
        if (action == null) action = thrown.action
    }
    return Played(table, scenes, action ?: return null)
}

/**
 * A turn nobody has decided: the seat draws, and the card goes on the pile without ever being
 * named.
 *
 * **Not a guess about what they will do — a statement of what the plan knows.** Whether the
 * card is kept or let go, the hand is the same length either way and no card the table can
 * name has moved, so the honest picture is every hand exactly as it was. What it *does* say is
 * that the pile's top is now a card nobody can name, which is the one thing an undecided turn
 * tells the turn after it: there is nothing there to take.
 *
 * Where the seat has already drawn for real — the turn on play, its card face up — that card
 * is what lands, because the table has seen it and pretending otherwise would draw a worse
 * picture than the truth.
 */
private fun Ghost.blindly(seat: String, turn: Int, drawn: Rank?): Played {
    val held = view.pendingAction?.playerId == seat
    val staged = if (held) view else view.drawing(seat)
    val after = if (drawn != null) landing(drawn, played = false) else unknownLanding(turn)
    val binned = GameAction.DiscardCard(PlayerIdPayload(seat))
    // The draw itself is only drawn where the card is not already in front of them.
    val drawing = if (held) {
        emptyList()
    } else {
        choreograph(GameAction.DrawCard(PlayerIdPayload(seat)), view, staged)
    }
    return Played(after, drawing + choreograph(binned, staged, after.view), binned)
}

/**
 * A step played from this table, or null where it names a card that is not there — or is an
 * action with nothing face up to play it with.
 *
 * A called card's action — and a pointed-at card's — is the same turn, played straight after
 * from the table the put-down or the King leaves: one frame, so the transport stops once for
 * the turn and the film shows the call and what it did as one motion.
 *
 * @param drawn the card face up in the seat's hand, where this is the turn being played.
 * @param rank the rank of the card the turn's own action plays with, or null for none.
 */
private fun Ghost.play(seat: String, step: Step, turn: Int, drawn: Rank?, rank: Rank?): Played? {
    if (step.isAnAction() && rank == null) return null
    val located = view.locate(step) ?: return null
    val after = after(seat, located, turn, drawn, rank) ?: return null
    val action = miming(seat, located)

    // The choreography draws a swap from the *pending action's* targets — that is where a real
    // Jack keeps the two cards it is about — so the ghost table has to stage the card as if it
    // were in play. Staged on the `before` view only, and never dispatched.
    val staged = view.staging(seat, located) ?: view
    val scenes = choreograph(action, staged, after.view) + view.secondLook(seat, located, after.view)

    val then = located.follows() ?: return Played(after, scenes, action)
    val followed = after.play(seat, then, turn, drawn = null, rank = located.calls()) ?: return null
    return Played(followed.after, scenes + followed.scenes, action)
}

/** The steps that are a card's action, which need a card face up to be played with. */
private fun Step.isAnAction(): Boolean = when (this) {
    is Step.Swap, is Step.Peek, is Step.Declare, is Step.ForceDraw -> true
    is Step.PutDown, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> false
}

/** What a step goes on to do once its card has acted: a called card's action, a pointed-at card's. */
private fun Step.follows(): Step? = when (this) {
    is Step.PutDown -> then
    is Step.Declare -> then
    is Step.Swap, is Step.Peek, is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> null
}

/** The rank whose action [follows] plays: the rank called, or the rank the King named. */
private fun Step.calls(): Rank? = when (this) {
    is Step.PutDown -> guess
    is Step.Declare -> rank
    is Step.Swap, is Step.Peek, is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> null
}

/** A Queen's second look, which is a second aim the choreography draws on its own. */
private fun PlayerView.secondLook(seat: String, step: Step, after: PlayerView): List<Scene> {
    val also = (step as? Step.Peek)?.also ?: return emptyList()
    return choreograph(
        GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(seat, also.seat, also.position)),
        this,
        after,
    )
}

/**
 * One throw-in played from this table. A vouched throw: the card leaves the thrower's hand for
 * the pile, and what it does is played from the table that leaves. A blind throw: its likely
 * outcome — the card stays, and a penalty card lands beside it, rose and tagged with the turn.
 * Null where the card is not there.
 */
private fun Ghost.throwIn(tossIn: TossIn, turn: Int): Played? {
    val hand = view.players.firstOrNull { it.id == tossIn.seat } ?: return null
    val position = tossIn.card?.position?.takeIf { it in hand.cards.indices }
        ?: tossIn.rank?.let { rank -> hand.cards.indices.firstOrNull { knownRankOf(view, hand, it) == rank } }
        ?: return null
    val action = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(tossIn.seat, listOf(position)))

    val rank = tossIn.rank
    if (rank == null) {
        val back = dealt(tossIn.seat, turn)
        return Played(back, choreograph(action, view, back.view), action)
    }
    val thrown = removedAt(tossIn.seat, position)?.landing(rank, played = false) ?: return null
    val scenes = choreograph(action, view, thrown.view)

    val then = tossIn.then ?: return Played(thrown, scenes, action)
    val followed = thrown.play(
        tossIn.seat,
        then,
        turn,
        drawn = null,
        rank = rank,
    ) ?: return Played(thrown, scenes, action)
    return Played(followed.after, scenes + followed.scenes, action)
}

/**
 * The plan as **one lane per coalition turn**, in turn order, decided or not.
 *
 * `CoalitionPlan.lanes` holds only the turns somebody has set, so its length is a count of
 * *decisions* and not of turns; walked as it stands, ② would mean "after the plan's second
 * decision". Read off the view rather than passed in, so every caller is fixed at once.
 */
private fun CoalitionPlan.turnsOf(view: PlayerView): List<Lane> {
    val caller = view.vintoCallerId ?: return lanes
    return coalitionInTurnOrder(view.players.map { it.id }, caller)
        .map { seat -> laneOf(seat) ?: Lane(seat) }
}

// ---------------------------------------------------------------------------- finding the cards

/**
 * The step with every card it names found on *this* table, or null where one is gone.
 *
 * A card is found by what was said about it first — the claim travelled with the card, so the
 * claim is where the card is now — and by its position only where nothing was said. That is
 * what lets turn two name a card turn one has moved.
 */
private fun PlayerView.locate(step: Step): Step? = when (step) {
    is Step.Swap -> {
        Step.Swap(locate(step.from) ?: return null, locate(step.to) ?: return null)
    }

    is Step.Peek -> {
        Step.Peek(locate(step.card) ?: return null, step.also?.let { locate(it) ?: return null })
    }

    is Step.PutDown -> {
        step.copy(card = locate(step.card) ?: return null)
    }

    is Step.Declare -> {
        step.copy(card = step.card?.let { locate(it) ?: return null })
    }

    is Step.ForceDraw -> {
        step.takeIf { players.any { seat -> seat.id == step.seat } }
    }

    Step.TakeTheDiscard, Step.Bin, Step.UseIt -> {
        step
    }
}

private fun PlayerView.locate(at: CardAt): CardAt? {
    val anchor = at.anchor
    if (anchor != null) {
        for (seat in players) {
            val position = seat.claims
                .firstOrNull { it.positions.size == 1 && sameClaim(it, anchor) }
                ?.positions
                ?.single()
            if (position != null && position in seat.cards.indices) return CardAt(seat.id, position, anchor)
        }
    }
    return at.takeIf { cardAt(it) != null }
}

private fun sameClaim(claim: Claim, other: Claim): Boolean =
    claim.by == other.by && claim.ranks == other.ranks && claim.covering == other.covering

// ---------------------------------------------------------------------------- the table after

/**
 * The table a step would leave behind.
 *
 * Null where the step names a card that is not there — a rehearsal of a broken plan would be a
 * picture of something that cannot happen, which is worse than no picture.
 *
 * Every step ends with a card on the pile. An action plays its card and spends it; a put-down
 * lands the card put down, known or not; letting go lands the drawn card, known or not.
 */
@Suppress("ReturnCount")
private fun Ghost.after(
    seat: String,
    step: Step,
    turn: Int,
    drawn: Rank?,
    rank: Rank?,
): Ghost? = when (step) {
    is Step.Swap -> {
        swapped(step.from, step.to)?.spent(drawn, rank)
    }

    // A King takes the card it points at out of its hand; where the plan names no card, the
    // first coalition card believed to be the rank. The King lands first, then the card.
    is Step.Declare -> {
        val pointed = step.card ?: step.rank?.let { view.firstBelieved(it) }
        val played = spent(drawn, rank)
        if (pointed == null) {
            played
        } else {
            val known = step.rank ?: knownRankOf(view, pointed)
            played.removedAt(
                pointed.seat,
                pointed.position,
            )?.let { if (known == null) it.unknownLanding(turn) else it.landing(known) }
        }
    }

    Step.TakeTheDiscard, Step.UseIt, is Step.Peek -> {
        spent(drawn, rank)
    }

    is Step.ForceDraw -> {
        dealt(step.seat, turn).spent(drawn, rank)
    }

    is Step.PutDown -> {
        putDown(step.card, drawn, turn)
    }

    // Nothing in the hand changes: the drawn card goes straight to the pile, action unused, a
    // card the table has seen where it is face up and a card nobody knows where it is not.
    Step.Bin -> {
        if (drawn != null) landing(drawn, played = false) else unknownLanding(turn)
    }
}

// The ghost's transforms: the view's, with the dealt cards following the cards.

/**
 * The card the turn played with, spent: a drawn card lands used; the pile's card is marked used
 * where it lies.
 */
private fun Ghost.spent(drawn: Rank?, rank: Rank?): Ghost = when {
    drawn != null -> landing(drawn, played = true)
    rank != null -> copy(view = view.copy(discardTop = view.discardTop?.copy(played = true)))
    else -> this
}

private fun Ghost.landing(rank: Rank, played: Boolean = false): Ghost =
    copy(view = view.landing(rank, played), pileUnknown = null)

/**
 * A card nobody knows lands on the pile: the top is unknown from this turn on, and nothing
 * unknown can be taken.
 */
private fun Ghost.unknownLanding(turn: Int): Ghost =
    copy(view = view.copy(discardTop = null, discardCount = view.discardCount + 1), pileUnknown = turn)

/**
 * The card goes to the pile and the drawn card takes its place — with what the table has seen
 * of it: the rank, where the card is face up right now; nothing, where it is still to be drawn.
 * Either way it is a card that arrives this turn, tagged so the felt can say so.
 */
private fun Ghost.putDown(at: CardAt, drawn: Rank?, turn: Int): Ghost? {
    val landing = knownRankOf(view, at)
    val replaced = view.putDown(at, drawn) ?: return null
    val tagged = Ghost(replaced, fresh + (CardRef(at.seat, at.position) to turn), pileUnknown)
    return if (landing == null) tagged.unknownLanding(turn) else tagged.landing(landing)
}

private fun Ghost.dealt(seat: String, turn: Int): Ghost {
    val hand = view.players.firstOrNull { it.id == seat }?.cards?.size ?: return this
    return copy(view = view.dealt(seat), fresh = fresh + (CardRef(seat, hand) to turn))
}

private fun Ghost.removedAt(seat: String, position: Int): Ghost? {
    val after = view.removedAt(seat, position) ?: return null
    val kept = fresh
        .filterKeys { !(it.playerId == seat && it.position == position) }
        .mapKeys { (ref, _) ->
            if (ref.playerId == seat && ref.position > position) {
                CardRef(
                    seat,
                    ref.position - 1,
                )
            } else {
                ref
            }
        }
    return copy(view = after, fresh = kept)
}

private fun Ghost.swapped(from: CardAt, to: CardAt): Ghost? {
    val after = view.swapped(from, to) ?: return null
    val here = CardRef(from.seat, from.position)
    val there = CardRef(to.seat, to.position)
    val moved = fresh - here - there +
        listOfNotNull(fresh[here]?.let { there to it }, fresh[there]?.let { here to it })
    return copy(view = after, fresh = moved)
}

/** The first coalition card believed to be [rank], for the loose King that names no card. */
private fun PlayerView.firstBelieved(rank: Rank): CardAt? = players
    .filter { it.id != vintoCallerId }
    .firstNotNullOfOrNull { seat ->
        seat.cards.indices.firstOrNull { believedOnView(seat, it).candidates.singleOrNull() == rank }
            ?.let { CardAt(seat.id, it) }
    }

/**
 * The card at [at] leaves for the pile and the drawn card takes its place: nothing said about
 * it, unless the whole table has seen it face up — then that is what it is said to be, by the
 * seat holding it.
 */
private fun PlayerView.putDown(at: CardAt, drawn: Rank?): PlayerView? {
    cardAt(at) ?: return null
    return copy(
        players = players.map { seat ->
            if (seat.id != at.seat) {
                seat
            } else {
                seat.copy(
                    cards = seat.cards.mapIndexed { position, card ->
                        if (position == at.position) CardView.Hidden else card
                    },
                    claims = seat.claims.filterNot { at.position in it.positions } +
                        listOfNotNull(drawn?.let { Claim(seat.id, listOf(at.position), listOf(it)) }),
                    knownCardPositions = seat.knownCardPositions - at.position,
                )
            }
        },
        pendingAction = pendingAction?.takeIf { it.playerId != at.seat },
    )
}

/** A card nobody has seen, dealt to the end of [seat]'s hand: an Ace's victim, a penalty. */
private fun PlayerView.dealt(seat: String): PlayerView = copy(
    players = players.map { hand ->
        if (hand.id == seat) hand.copy(cards = hand.cards + CardView.Hidden) else hand
    },
    drawPileSize = (drawPileSize - 1).coerceAtLeast(0),
)

/**
 * A card leaving [seat]'s hand at [position]: the hand closes up, what was said about the card
 * goes with it, and what was said about the cards after it slides down a place — exactly as
 * the engine does it when a card is thrown in or a King takes it.
 */
private fun PlayerView.removedAt(seat: String, position: Int): PlayerView? {
    val hand = players.firstOrNull { it.id == seat } ?: return null
    if (position !in hand.cards.indices) return null
    fun shifted(at: Int): Int = if (at > position) at - 1 else at
    return copy(
        players = players.map { other ->
            if (other.id != seat) {
                other
            } else {
                other.copy(
                    cards = other.cards.filterIndexed { at, _ -> at != position },
                    claims = other.claims
                        .filterNot { position in it.positions }
                        .map { claim -> claim.copy(positions = claim.positions.map(::shifted)) },
                    knownCardPositions = other.knownCardPositions.filter { it != position }.map(::shifted),
                )
            }
        },
    )
}

/** A card of [rank] landing face up on the pile, as the window it opens would see it. */
private fun PlayerView.landing(rank: Rank, played: Boolean): PlayerView = copy(
    discardTop = Card(
        id = "ghost-${rank.serialName}-$discardCount",
        rank = rank,
        value = getCardValue(rank),
        played = played,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    ),
    discardCount = discardCount + 1,
    pendingAction = null,
)

/**
 * Two cards changing places, and what was said about each going with it — a single claim about
 * either card moves to the other hand at its new place, and a pair claim either card was half
 * of is broken, as the engine breaks it (`swapDeclarationsBetween`).
 */
private fun PlayerView.swapped(from: CardAt, to: CardAt): PlayerView? {
    val here = cardAt(from) ?: return null
    val there = cardAt(to) ?: return null
    val fromHand = players.first { it.id == from.seat }
    val toHand = players.first { it.id == to.seat }
    val moving = Moving(
        from = from,
        to = to,
        fromClaims = fromHand.claims.filter { it.positions == listOf(from.position) },
        toClaims = toHand.claims.filter { it.positions == listOf(to.position) },
    )

    return copy(
        players = players.map { seat ->
            seat.copy(
                cards = seat.cards.mapIndexed { position, card ->
                    when {
                        seat.id == from.seat && position == from.position -> there
                        seat.id == to.seat && position == to.position -> here
                        else -> card
                    }
                },
                claims = moving.claimsAfter(seat),
            )
        },
    )
}

/** The two cards of a trade and the single claims that ride on each, for the hands they cross. */
private class Moving(
    val from: CardAt,
    val to: CardAt,
    val fromClaims: List<Claim>,
    val toClaims: List<Claim>,
) {
    private fun touches(seat: PlayerSeatView, claim: Claim): Boolean {
        val here = seat.id == from.seat && from.position in claim.positions
        val there = seat.id == to.seat && to.position in claim.positions
        return here || there
    }

    /** [seat]'s claims once the trade has happened: what was on the moved cards goes with them. */
    fun claimsAfter(seat: PlayerSeatView): List<Claim> {
        if (seat.id != from.seat && seat.id != to.seat) return seat.claims
        val kept = seat.claims.filterNot { touches(seat, it) }
        val arriving = buildList {
            if (seat.id == from.seat) toClaims.forEach { add(it.copy(positions = listOf(from.position))) }
            if (seat.id == to.seat) fromClaims.forEach { add(it.copy(positions = listOf(to.position))) }
        }
        return kept + arriving
    }
}

private fun PlayerView.cardAt(at: CardAt): CardView? =
    players.firstOrNull { it.id == at.seat }?.cards?.getOrNull(at.position)

// ---------------------------------------------------------------------------- staging the picture

/**
 * The card a step would be played with, put in front of its seat so the animation has
 * somewhere to draw from.
 *
 * A trade stages the two cards as a Jack's targets; a put-down stages the draw; a King stages
 * the card it points at, which is where the choreography reads it from. The rest animate from
 * what is already on the table.
 */
private fun PlayerView.staging(seat: String, step: Step): PlayerView? = when (step) {
    is Step.PutDown -> {
        drawing(seat)
    }

    is Step.Swap -> {
        val here = cardAt(step.from)
        val there = cardAt(step.to)
        if (here == null || there == null) {
            null
        } else {
            pending(seat, TargetType.SWAP_CARDS, PendingCardOrigin.DRAWING) {
                listOf(
                    PendingTargetView(step.from.seat, step.from.position, here),
                    PendingTargetView(step.to.seat, step.to.position, there),
                )
            }
        }
    }

    is Step.Declare -> {
        val pointed = step.card ?: step.rank?.let { firstBelieved(it) }
        val card = pointed?.let { cardAt(it) }
        if (pointed == null || card == null) {
            null
        } else {
            pending(seat, TargetType.DECLARE_ACTION, PendingCardOrigin.HAND) {
                listOf(PendingTargetView(pointed.seat, pointed.position, card))
            }
        }
    }

    is Step.Peek, is Step.ForceDraw, Step.TakeTheDiscard, Step.Bin, Step.UseIt -> {
        null
    }
}

private fun PlayerView.pending(
    seat: String,
    targetType: TargetType,
    from: PendingCardOrigin,
    targets: () -> List<PendingTargetView>,
): PlayerView = copy(
    pendingAction = PendingActionView(
        playerId = seat,
        actionPhase = ActionPhase.SELECTING_TARGET,
        from = from,
        targetType = targetType,
        card = CardView.Hidden,
        targets = targets(),
    ),
)

/**
 * The seat with a draw in front of it: where a put-down starts, and what a turn nobody has
 * decided has on the table. Hidden, because nobody has seen it — including the seat holding it.
 */
internal fun PlayerView.drawing(seat: String): PlayerView = copy(
    pendingAction = PendingActionView(
        playerId = seat,
        actionPhase = ActionPhase.CHOOSING_ACTION,
        from = PendingCardOrigin.DRAWING,
        card = CardView.Hidden,
        targets = emptyList(),
    ),
)

/**
 * The action a step is *mimed* as, so the existing choreography can draw it.
 *
 * A rehearsal is not a move and this action is never dispatched, validated or reduced — it
 * exists only to tell `choreograph` which picture to draw.
 */
private fun miming(seat: String, step: Step): GameAction = when (step) {
    is Step.Swap -> GameAction.ExecuteJackSwap(PlayerIdPayload(seat))
    is Step.PutDown -> GameAction.SwapCard(SwapCardPayload(seat, step.card.position))
    is Step.Declare -> GameAction.DeclareKingAction(DeclareKingActionPayload(seat, step.rank ?: Rank.KING))
    is Step.Peek -> GameAction.SelectActionTarget(
        SelectActionTargetPayload.Positional(seat, step.card.seat, step.card.position),
    )

    is Step.ForceDraw -> GameAction.SelectActionTarget(SelectActionTargetPayload.Ace(seat, step.seat))
    Step.TakeTheDiscard -> GameAction.PlayDiscard(PlayerIdPayload(seat))
    Step.Bin -> GameAction.DiscardCard(PlayerIdPayload(seat))
    Step.UseIt -> GameAction.UseCardAction(PlayerIdPayload(seat))
}
