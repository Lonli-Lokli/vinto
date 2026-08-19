package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.Rank
import game.vinto.shapes.actorId

/**
 * A place on the table a card can be.
 *
 * Named rather than measured, because where these are on screen is the screen's business and
 * changes with the layout, while *which* of them a card moved between is a fact about the
 * game. Keeping them apart is what lets the movement be worked out here, where it can be
 * tested, and drawn there, where it can be seen (design C5).
 */
sealed interface Anchor {
    data object Deck : Anchor
    data object Discard : Anchor

    /** The card in hand awaiting a decision — it belongs to no seat yet. */
    data object Pending : Anchor

    data class Seat(val playerId: String, val position: Int) : Anchor
}

/**
 * One thing the player should see.
 *
 * Movement is the common case and not the only one: a card turning over, a hand flinching at a
 * penalty and a seat lighting up are all things the game does that a still picture cannot say.
 */
sealed interface Beat {

    /**
     * A card travelling.
     *
     * @param card what to draw in flight, or null to draw a back. A card that arrives
     *   face-down flies face-down: showing a rank in mid-air and hiding it on landing tells
     *   the player something the game did not.
     */
    data class Move(val from: Anchor, val to: Anchor, val card: Card? = null) : Beat

    /** A card turning over where it lies — a peek revealing it, or a round ending. */
    data class Turn(val at: Anchor, val card: Card?) : Beat

    /** A hand flinching. Used for the penalty card a wrong guess or a wrong toss-in costs. */
    data class Flinch(val at: Anchor) : Beat

    /** A seat drawing attention to itself, without anything moving. */
    data class Attend(val playerId: String, val kind: Attention) : Beat

    /**
     * Somebody reacting.
     *
     * Bots that only ever move cards are furniture. A line at the right moment — a bot
     * announcing a Vinto call, or wincing at a penalty — is what makes the other three seats
     * feel like opponents, and it costs one string.
     */
    data class Say(val playerId: String, val line: String) : Beat
}

/** Why a seat is being pointed at. */
enum class Attention { TURN, VINTO, PENALTY, COALITION }

/**
 * Beats that happen together.
 *
 * Sequencing is a property of the grouping rather than of each beat (design C2): a scene is
 * the things that happen at once, and scenes happen in order. A swap is one scene of two
 * moves, because the cards cross; a King is three scenes, because it is three things one
 * after another.
 */
typealias Scene = List<Beat>

/**
 * What the player should see, given what happened.
 *
 * Takes two **views** rather than two states, and that is the whole design (C1). A client
 * never holds `GameState` — redaction is the point — so animation derived from state works in
 * a solo game and cannot be carried to a room, which is the mode it is for. Derived from the
 * view it works in both, and it gains a property worth as much: it *cannot* animate a card the
 * player is not entitled to see, because it was never given one.
 *
 * Takes the action rather than diffing the two views, because a diff cannot tell which card
 * went where when two move at once. A swap moves one in and another out; animating them in the
 * wrong order is worse than not animating them.
 *
 * Returns nothing for the many actions that move nothing — aiming a Jack, agreeing that a
 * toss-in window is over. Silence is the right answer there, not a guess.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
fun choreograph(action: GameAction, before: PlayerView, after: PlayerView): List<Scene> {
    val mine = action.actorId == before.viewerId
    val penalty = penaltyScene(action, before, after)

    val main: Scene = when (action) {
        // The deck to the space in front of you. Face-up only for the player who drew it —
        // everyone else is watching a back move.
        is GameAction.DrawCard ->
            listOf(Beat.Move(Anchor.Deck, Anchor.Pending, after.pendingCard().takeIf { mine }))

        // Taking an action card off the pile, which everybody has already seen.
        is GameAction.PlayDiscard ->
            listOf(Beat.Move(Anchor.Discard, Anchor.Pending, before.discardPile.lastOrNull()))

        is GameAction.DiscardCard ->
            listOf(Beat.Move(Anchor.Pending, Anchor.Discard, after.discardPile.lastOrNull()))

        // Two cards at once, and the order is the point: the new one goes in, the old one
        // comes out and lands face-up where everybody can read it.
        is GameAction.SwapCard -> {
            val seat = Anchor.Seat(action.payload.playerId, action.payload.position)
            listOfNotNull(
                Beat.Move(Anchor.Pending, seat, before.pendingCard().takeIf { mine }),
                after.discardPile.lastOrNull()?.let { Beat.Move(seat, Anchor.Discard, it) },
            )
        }

        // A peek does not move anything; it turns a card over where it lies, and only for the
        // player doing the peeking.
        is GameAction.SelectActionTarget -> turnScene(after, mine)

        // A played action ends with its card on the pile — but for a King or a Queen that is
        // several moves later, so it is driven by the pile growing rather than by the action.
        is GameAction.UseCardAction,
        is GameAction.ConfirmPeek,
        is GameAction.SkipPeek,
        is GameAction.SkipJackSwap,
        is GameAction.SkipQueenSwap,
        is GameAction.DeclareKingAction,
        -> discardScene(before, after)

        // The two swaps that happen inside an action rather than as a move of their own, so
        // their endpoints come from the targets the action was aimed at.
        is GameAction.ExecuteJackSwap, is GameAction.ExecuteQueenSwap -> crossScene(before)

        is GameAction.ParticipateInTossIn -> tossScene(action, before, after)

        is GameAction.CallVinto -> listOf(
            Beat.Attend(action.payload.playerId, Attention.VINTO),
            Beat.Say(action.payload.playerId, "Vinto!"),
        )

        is GameAction.SetCoalitionLeader ->
            listOf(Beat.Attend(action.payload.leaderId, Attention.COALITION))

        else -> emptyList()
    }

    return listOfNotNull(main.takeIf { it.isNotEmpty() }, penalty)
}

/**
 * The card a penalty adds to a hand.
 *
 * Not attached to any one action, because several can cause one: a wrong declaration, a wrong
 * toss-in, an Ace aimed at you. What they have in common is a hand that grew, which is
 * exactly what is checked — and it means a penalty introduced later animates without anybody
 * remembering to teach this function about it.
 */
private fun penaltyScene(action: GameAction, before: PlayerView, after: PlayerView): Scene? {
    val grew = after.players.mapNotNull { seat ->
        val was = before.players.firstOrNull { it.id == seat.id }?.cards?.size ?: return@mapNotNull null
        if (seat.cards.size > was) seat to was else null
    }
    if (grew.isEmpty()) return null

    return grew.flatMap { (seat, was) ->
        val landing = Anchor.Seat(seat.id, was)
        listOf(
            Beat.Move(Anchor.Deck, landing),
            Beat.Flinch(landing),
            Beat.Attend(seat.id, Attention.PENALTY),
        )
    } + reactionTo(action, grew.first().first.id)
}

/** A line for the seat that just took a penalty, if the action explains why. */
private fun reactionTo(action: GameAction, playerId: String): List<Beat> = when (action) {
    is GameAction.SwapCard -> if (action.payload.declaredRank != null) {
        listOf(Beat.Say(playerId, "Wrong call."))
    } else {
        emptyList()
    }

    is GameAction.ParticipateInTossIn -> listOf(Beat.Say(playerId, "Wrong rank."))
    else -> emptyList()
}

/** The card an action just revealed, turned over where it lies. */
private fun turnScene(after: PlayerView, mine: Boolean): Scene {
    if (!mine) return emptyList()
    val target = after.pendingAction?.targets?.lastOrNull() ?: return emptyList()
    val card = (target.card as? CardView.Visible)?.card

    return listOf(Beat.Turn(Anchor.Seat(target.playerId, target.position), card))
}

private fun discardScene(before: PlayerView, after: PlayerView): Scene =
    if (after.discardPile.size > before.discardPile.size) {
        listOf(Beat.Move(Anchor.Pending, Anchor.Discard, after.discardPile.lastOrNull()))
    } else {
        emptyList()
    }

/** A Jack or a Queen exchanging two cards, which cross. */
private fun crossScene(before: PlayerView): Scene {
    val targets = before.pendingAction?.targets.orEmpty()
    if (targets.size < TWO) return emptyList()

    val (first, second) = targets
    val firstSeat = Anchor.Seat(first.playerId, first.position)
    val secondSeat = Anchor.Seat(second.playerId, second.position)
    val seen = before.pendingAction?.playerId == before.viewerId

    return listOf(
        Beat.Move(firstSeat, secondSeat, (first.card as? CardView.Visible)?.card.takeIf { seen }),
        Beat.Move(secondSeat, firstSeat, (second.card as? CardView.Visible)?.card.takeIf { seen }),
    )
}

private fun tossScene(
    action: GameAction.ParticipateInTossIn,
    before: PlayerView,
    after: PlayerView,
): Scene {
    val landed = after.discardPile.size - before.discardPile.size
    if (landed <= 0) return emptyList()

    return action.payload.positions.take(landed).map { position ->
        Beat.Move(
            Anchor.Seat(action.payload.playerId, position),
            Anchor.Discard,
            after.discardPile.lastOrNull(),
        )
    }
}

private fun PlayerView.pendingCard(): Card? = (pendingAction?.card as? CardView.Visible)?.card

private const val TWO = 2

/** Every rank the choreography might need to draw, for a client that preloads its art. */
internal val EVERY_RANK: List<Rank> = Rank.entries
