package game.vinto.client

import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.PendingCardOrigin

/**
 * A place on the table a card can be.
 *
 * Named rather than measured, because where these are on the screen is the screen's business
 * and changes with the layout, while *which* of them a card moved between is a fact about the
 * game. Keeping the two apart is what lets the movement be worked out here, where it can be
 * tested, and drawn there, where it can be seen.
 */
sealed interface Anchor {
    data object Deck : Anchor
    data object Discard : Anchor

    /** The card in hand awaiting a decision — it belongs to no seat yet. */
    data object Pending : Anchor

    data class Seat(val playerId: String, val position: Int) : Anchor
}

/**
 * One card moving from one place to another.
 *
 * @param card what to draw in flight, or null to draw a back. A card that arrives face-down
 *   should fly face-down: showing a rank in mid-air and hiding it on landing tells the player
 *   something the game did not.
 */
data class CardFlight(val from: Anchor, val to: Anchor, val card: Card? = null)

/**
 * What visibly moved when [action] was applied to [before], producing [after].
 *
 * Worked out from the action rather than by comparing the two states, and the difference
 * matters: a diff can see that the discard pile grew and a hand shrank, but not which card
 * went where when two moved at once — a swap moves one card in and another out, and drawing
 * them in the wrong order is worse than not drawing them at all. The action says exactly what
 * was asked for; the states supply the cards.
 *
 * Returns nothing for the many actions that move no card — aiming a Jack, agreeing to a
 * toss-in window, calling Vinto. Silence is the right answer there, not a guess.
 *
 * @param viewerId the seat watching. A card drawn by somebody else flies face-down, because
 *   that is what the watcher is entitled to see; the same card drawn by the viewer flies
 *   face-up.
 */
@Suppress("ReturnCount")
fun flightsFor(
    action: GameAction,
    before: GameState,
    after: GameState,
    viewerId: String,
): List<CardFlight> = when (action) {
    // The deck to the space in front of you. Face-up only for the player who drew it.
    is GameAction.DrawCard -> {
        val mine = action.payload.playerId == viewerId
        val drawn = after.pendingAction?.card.takeIf { mine }
        listOf(CardFlight(Anchor.Deck, Anchor.Pending, drawn))
    }

    // Taking an action card off the discard pile, which everyone has already seen.
    is GameAction.PlayDiscard ->
        listOf(CardFlight(Anchor.Discard, Anchor.Pending, after.pendingAction?.card))

    // The pending card goes down unplayed.
    is GameAction.DiscardCard ->
        listOf(CardFlight(Anchor.Pending, Anchor.Discard, after.discardPile.peekTop()))

    // Two cards at once, and the order is the point: the new one goes in, the old one comes
    // out and lands face-up on the pile where everybody can read it.
    is GameAction.SwapCard -> {
        val seat = Anchor.Seat(action.payload.playerId, action.payload.position)
        val mine = action.payload.playerId == viewerId
        val takenIn = before.pendingAction?.card.takeIf { mine }
        listOfNotNull(
            CardFlight(Anchor.Pending, seat, takenIn),
            after.discardPile.peekTop()?.let { CardFlight(seat, Anchor.Discard, it) },
        )
    }

    // A played action ends with its card on the pile — but only once it actually gets there,
    // which for a King or a Queen is several moves later.
    is GameAction.UseCardAction,
    is GameAction.ConfirmPeek,
    is GameAction.SkipPeek,
    is GameAction.ExecuteJackSwap,
    is GameAction.SkipJackSwap,
    is GameAction.ExecuteQueenSwap,
    is GameAction.SkipQueenSwap,
    is GameAction.DeclareKingAction,
    -> if (after.discardPile.size > before.discardPile.size) {
        listOf(CardFlight(Anchor.Pending, Anchor.Discard, after.discardPile.peekTop()))
    } else {
        emptyList()
    }

    // Every card thrown in, from wherever it sat.
    is GameAction.ParticipateInTossIn -> {
        val thrown = after.discardPile.size - before.discardPile.size
        action.payload.positions.take(thrown).map { position ->
            CardFlight(
                Anchor.Seat(action.payload.playerId, position),
                Anchor.Discard,
                after.discardPile.peekTop(),
            )
        }
    }

    else -> emptyList()
}

/**
 * The card a Jack or a Queen swapped, as two flights that cross.
 *
 * Kept separate from [flightsFor] because the engine resolves the swap inside the action
 * rather than as a move of its own, so the endpoints come from the pending action's targets
 * rather than from the action's payload.
 */
fun swapFlights(before: GameState, viewerId: String): List<CardFlight> {
    val targets = before.pendingAction?.targets.orEmpty()
    if (targets.size < 2) return emptyList()

    val (first, second) = targets
    val firstSeat = Anchor.Seat(first.playerId, first.position)
    val secondSeat = Anchor.Seat(second.playerId, second.position)
    val visible = before.pendingAction?.playerId == viewerId

    return listOf(
        CardFlight(firstSeat, secondSeat, first.card.takeIf { visible }),
        CardFlight(secondSeat, firstSeat, second.card.takeIf { visible }),
    )
}

/** Where the pending card came from, for the flight that put it there. */
internal fun PendingCardOrigin.anchor(): Anchor =
    if (this == PendingCardOrigin.DRAWING) Anchor.Deck else Anchor.Discard
