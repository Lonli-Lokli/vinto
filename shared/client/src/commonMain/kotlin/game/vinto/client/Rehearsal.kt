package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PendingActionView
import game.vinto.engine.PendingTargetView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.Lane
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Step
import game.vinto.shapes.SwapCardPayload
import game.vinto.shapes.TargetType
import game.vinto.shapes.coalitionInTurnOrder
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
 * an online one the same screens. A composer that reduced would work locally, where
 * `LocalGameSession` happens to hold the state, and break the first time somebody played
 * online: exactly the class of bug the `GameSession` seam exists to prevent.
 *
 * So a ghost table is built by **transforming the view**, which is all a swap is anyway — two
 * cards changing places. Nothing here needs the engine, and nothing here can see a card the
 * seat was not already shown.
 */
fun rehearse(view: PlayerView, plan: CoalitionPlan): List<Frame> =
    rehearsal(view, plan).frames.filterNotNull()

/**
 * A plan as the **transport** reads it (design D14): the positions it stops at, and the
 * pictures between them.
 *
 * The film has one stop per turn boundary — the table now, and the table after each of at most
 * three turns — because those are the only positions worth resting on: every other moment has
 * cards in mid-air, which is the one state a table can be neither read nor edited in. So
 * [tables] is one longer than [frames], and position *k* renders the table after turns 1..*k*
 * and no further.
 *
 * A turn with nothing to draw — no step yet, or a step naming a card that is not there — keeps
 * its place with a null frame rather than being dropped. That is what makes ② mean the same
 * turn to every member reading it (design D6), and it is why [rehearse] filters rather than
 * this skipping.
 */
data class Rehearsal(
    /** One per position, 0..*n*: the table now, then the table after each turn. */
    val tables: List<PlayerView>,
    /** One per turn, in order: what to play on the way to that position, or null if nothing. */
    val frames: List<Frame?>,
) {
    /** The last position: the table the plan arrives at (design D10). */
    val arrival: PlayerView get() = tables.last()

    /** How many positions the transport has beyond the first. */
    val turns: Int get() = frames.size

    /**
     * The film from position [at] onward — what is left to play from where the head is parked.
     *
     * **The drop comes before the filter**, and that order is the whole of it. [frames] is one
     * per turn *including the turns with nothing to draw*, so a turn's index is its position;
     * filtered first, the list is shorter than the plan and dropping by a position number takes
     * the wrong frames off the front. On the plan a phone reported — turn 1 undecided, turn 2 a
     * swap, turn 3 undecided — the filtered film is one frame long, so a head parked on turn 1
     * dropped the only move there was and Play ran an empty film: the transport travelled to the
     * end and not one card moved.
     */
    fun from(at: Int): List<Frame> = between(at, turns)

    /**
     * The film between two positions — what plays on the way from [from] to [to].
     *
     * The transport sends the head to a *named* stop now rather than always to the end, so a
     * member who presses ② watches the first two turns and stops there.
     */
    fun between(from: Int, to: Int): List<Frame> =
        frames.drop(from.coerceIn(0, turns)).take((to - from).coerceAtLeast(0)).filterNotNull()
}

/** The plan, resolved into the transport's positions. See [Rehearsal]. */
fun rehearsal(view: PlayerView, plan: CoalitionPlan): Rehearsal {
    var table = view
    val tables = mutableListOf(view)
    val frames = mutableListOf<Frame?>()

    for (lane in plan.turnsOf(view)) {
        val step = lane.step
        val after = step?.let { table.after(it) }
        if (step == null || after == null) {
            // The turn still exists — it is somebody's turn either way — so it keeps its
            // position and the transport still stops on it. There is simply nothing to draw.
            frames += null
            tables += table
            continue
        }

        val action = miming(lane.seat, step)

        // The choreography draws a swap from the *pending action's* targets — that is where a
        // real Jack keeps the two cards it is about — so the ghost table has to stage the card
        // as if it were in play. Staged on the `before` view only, and never dispatched.
        val staged = table.staging(lane.seat, step) ?: table

        frames += Frame(
            action = action,
            scenes = choreograph(action, staged, after),
            view = after,
            ghost = true,
        )
        table = after
        tables += after
    }

    return Rehearsal(tables = tables, frames = frames)
}

/**
 * The plan as **one lane per coalition turn**, in turn order, decided or not.
 *
 * `CoalitionPlan.lanes` holds only the turns somebody has set, so its length is a count of
 * *decisions* and not of turns. Walking it made position ② mean "after the plan's second
 * decision", which on a board where only the middle turn was decided put that turn's swap on
 * the felt under the name of the seat before it — and made every position past the last decided
 * lane fall off the end, so the arrival, which is the one picture the transport exists for,
 * quietly showed the table as it is now.
 *
 * Read off the view rather than passed in, so every caller is fixed at once and none of them can
 * pass a coalition that disagrees with the one the board draws.
 */
private fun CoalitionPlan.turnsOf(view: PlayerView): List<Lane> {
    val caller = view.vintoCallerId ?: return lanes
    return coalitionInTurnOrder(view.players.map { it.id }, caller)
        .map { seat -> laneOf(seat) ?: Lane(seat) }
}

/**
 * The table a step would leave behind.
 *
 * Null where the step names a card that is not there — a rehearsal of a broken plan would be a
 * picture of something that cannot happen, which is worse than no picture.
 */
private fun PlayerView.after(step: Step): PlayerView? = when (step) {
    is Step.Swap -> swapped(step.from, step.to)

    // A King empties a rank out of every coalition hand. Only theirs: the caller may not toss
    // in once they have called, so this can never help them shed.
    is Step.Declare -> copy(
        players = players.map { seat ->
            if (seat.id == vintoCallerId) {
                seat
            } else {
                seat.copy(cards = seat.cards.filterIndexed { position, _ -> !claims(seat, position, step) })
            }
        },
    )

    // Nothing to show: what comes off the pile is a card the plan already knows about, and the
    // interesting part is what its action then does, which the next step describes. Binning
    // shows nothing for the opposite reason — the hand is deliberately left alone.
    Step.TakeTheDiscard, Step.Bin, Step.UseIt -> this

    is Step.PutDown -> putDown(step.card)
}

/**
 * The card goes to the pile and an unseen draw takes its place. What teammates then throw in
 * on it is theirs to do and the readout's to price; the rehearsal shows the step itself.
 */
private fun PlayerView.putDown(at: CardAt): PlayerView? {
    val here = cardAt(at) ?: return null
    return copy(
        players = players.map { seat ->
            if (seat.id != at.seat) {
                seat
            } else {
                seat.copy(
                    cards = seat.cards.mapIndexed { position, card ->
                        if (position ==
                            at.position
                        ) {
                            CardView.Hidden
                        } else {
                            card
                        }
                    },
                )
            }
        },
        discardTop = (here as? CardView.Visible)?.card ?: discardTop,
    )
}

private fun claims(seat: PlayerSeatView, position: Int, step: Step.Declare): Boolean =
    believedOnView(seat, position).candidates.singleOrNull() == step.rank

private fun PlayerView.swapped(from: CardAt, to: CardAt): PlayerView? {
    val here = cardAt(from) ?: return null
    val there = cardAt(to) ?: return null

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
            )
        },
    )
}

private fun PlayerView.cardAt(at: CardAt): CardView? =
    players.firstOrNull { it.id == at.seat }?.cards?.getOrNull(at.position)

/**
 * The card a step would be played with, put in front of its seat so the animation has
 * somewhere to draw from.
 *
 * Only a swap needs it: the other two steps animate from what is already on the table.
 */
private fun PlayerView.staging(seat: String, step: Step): PlayerView? {
    if (step is Step.PutDown) return drawing(seat)
    if (step !is Step.Swap) return null
    val here = cardAt(step.from) ?: return null
    val there = cardAt(step.to) ?: return null

    return copy(
        pendingAction = PendingActionView(
            playerId = seat,
            actionPhase = ActionPhase.SELECTING_TARGET,
            from = PendingCardOrigin.DRAWING,
            targetType = TargetType.SWAP_CARDS,
            card = CardView.Hidden,
            targets = listOf(
                PendingTargetView(step.from.seat, step.from.position, here),
                PendingTargetView(step.to.seat, step.to.position, there),
            ),
        ),
    )
}

/** The seat with an unseen draw in front of it, which is where a put-down starts. */
private fun PlayerView.drawing(seat: String): PlayerView = copy(
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
 * exists only to tell `choreograph` which picture to draw. Naming the seat whose turn the step
 * belongs to is what makes the animation play from the right chair.
 */
private fun miming(seat: String, step: Step): GameAction = when (step) {
    is Step.Swap -> GameAction.ExecuteJackSwap(PlayerIdPayload(seat))
    is Step.PutDown -> GameAction.SwapCard(SwapCardPayload(seat, step.card.position))
    is Step.Declare -> GameAction.DeclareKingAction(
        game.vinto.shapes.DeclareKingActionPayload(seat, step.rank),
    )

    Step.TakeTheDiscard -> GameAction.PlayDiscard(PlayerIdPayload(seat))
    Step.Bin -> GameAction.DiscardCard(PlayerIdPayload(seat))
    Step.UseIt -> GameAction.UseCardAction(PlayerIdPayload(seat))
}
