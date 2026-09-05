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
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Step
import game.vinto.shapes.TargetType

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
fun rehearse(view: PlayerView, plan: CoalitionPlan): List<Frame> {
    var table = view
    val frames = mutableListOf<Frame>()

    for (lane in plan.lanes) {
        val step = lane.step ?: continue
        val after = table.after(step) ?: continue
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
    }

    return frames
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
    // interesting part is what its action then does, which the next step describes.
    Step.TakeTheDiscard -> this
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

/**
 * The action a step is *mimed* as, so the existing choreography can draw it.
 *
 * A rehearsal is not a move and this action is never dispatched, validated or reduced — it
 * exists only to tell `choreograph` which picture to draw. Naming the seat whose turn the step
 * belongs to is what makes the animation play from the right chair.
 */
private fun miming(seat: String, step: Step): GameAction = when (step) {
    is Step.Swap -> GameAction.ExecuteJackSwap(PlayerIdPayload(seat))
    is Step.Declare -> GameAction.DeclareKingAction(
        game.vinto.shapes.DeclareKingActionPayload(seat, step.rank),
    )

    Step.TakeTheDiscard -> GameAction.PlayDiscard(PlayerIdPayload(seat))
}
