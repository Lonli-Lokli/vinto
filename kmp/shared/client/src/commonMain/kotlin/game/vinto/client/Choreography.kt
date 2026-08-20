package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
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
     * @param spin a full turn on the way. Marks a card as somebody else's: a bot's draw and
     *   yours are otherwise the same movement, and at a bot's speed the difference between
     *   "I did that" and "that happened to me" is worth one rotation.
     */
    data class Move(
        val from: Anchor,
        val to: Anchor,
        val card: Card? = null,
        val spin: Boolean = false,
    ) : Beat

    /**
     * A card being looked at.
     *
     * **Public.** Everyone sees *which* card was peeked — that is real information, and it is
     * how you know a bot has just learned its own third card. Only the player entitled to it
     * sees the face, which is what [card] being null means: somebody looked at that card, and
     * you do not get to look with them.
     *
     * Drawn as a lift toward the middle of the table with a glow, rather than a flip in place,
     * so that it reads at a glance across three other hands.
     */
    data class Peek(val at: Anchor, val card: Card? = null) : Beat

    /**
     * A card held up in the middle of the table while its action happens.
     *
     * The web app's two-stage "play action": the card leaves the hand, is shown large in the
     * centre, and only then goes to the pile. Without the pause an action card is drawn and
     * discarded in the same breath and nobody reads what it was.
     */
    data class Stage(val card: Card?) : Beat

    /**
     * The discard pile going back into the deck.
     *
     * A real event and currently a silent one in both clients: everything anybody has learned
     * from watching the pile becomes stale at that moment, and the only sign of it is two
     * numbers changing. [cards] is how many went back, which is what makes it read as a
     * sweep rather than as one card moving the wrong way.
     */
    data class Reshuffle(val cards: Int) : Beat

    /**
     * The King's borrowed action.
     *
     * A King declares another rank and performs *that* card's action, which means the next
     * thing the table is asked for belongs to a card nobody played. Naming it is the
     * difference between "why is it asking me to pick two cards" and "ah, it declared a
     * Queen".
     */
    data class Borrowed(val rank: Rank) : Beat

    /**
     * The answer to a declaration: green for right, red for wrong.
     *
     * Declaring a rank is the one move in the game that is a gamble on your own memory, and
     * the outcome is otherwise only visible as a penalty card appearing somewhere.
     */
    data class Verdict(val at: Anchor, val correct: Boolean) : Beat

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
        // everyone else is watching a back move, and watching it turn over as it goes.
        is GameAction.DrawCard ->
            listOf(
                Beat.Move(
                    from = Anchor.Deck,
                    to = Anchor.Pending,
                    card = after.pendingCard().takeIf { mine },
                    spin = !mine,
                ),
            )

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

        // Aiming an action. A peek is shown to the whole table; an Ace names a victim rather
        // than a card, so there is nothing to lift and somebody to point at instead.
        is GameAction.SelectActionTarget -> when (val payload = action.payload) {
            is SelectActionTargetPayload.Ace -> listOf(
                Beat.Attend(payload.targetPlayerId, Attention.PENALTY),
                Beat.Say(payload.targetPlayerId, "Drawing…"),
            )

            is SelectActionTargetPayload.Positional -> peekScene(after, payload)
        }

        // A played action ends with its card on the pile — but for a King or a Queen that is
        // several moves later, so it is driven by the pile growing rather than by the action.
        // Held up in the middle before it goes anywhere, so the table can read it.
        is GameAction.UseCardAction, is GameAction.PlayDiscard ->
            listOf(Beat.Stage(before.pendingCard() ?: after.pendingCard()))

        is GameAction.ConfirmPeek,
        is GameAction.SkipPeek,
        is GameAction.SkipJackSwap,
        is GameAction.SkipQueenSwap,
        -> discardScene(before, after)

        // The King says what it is pretending to be, and then does that.
        is GameAction.DeclareKingAction ->
            listOf(Beat.Borrowed(action.payload.declaredRank)) + discardScene(before, after)

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

    val verdict = verdictScene(action, penalty != null)
    val table = tableScene(before, after)

    return listOfNotNull(main.takeIf { it.isNotEmpty() }, verdict, penalty, table)
}

/**
 * What happened to the table itself, rather than to anybody's cards.
 *
 * Read from the two views rather than from the action, because neither is anybody's *move*:
 * the deck refills and the turn passes inside the engine's own bookkeeping, at the end of
 * whatever action happened to finish a turn. There is no action to attach them to, which is
 * exactly why both went unnoticed in the web app as well.
 */
private fun tableScene(before: PlayerView, after: PlayerView): Scene? {
    val beats = mutableListOf<Beat>()

    // The pile going back into the deck. The draw pile only ever grows this way.
    val refilled = after.drawPileSize - before.drawPileSize
    if (refilled > 0) beats += Beat.Reshuffle(refilled)

    // The turn moving. Three bots take theirs in under a second between one tap and the
    // next, and a ring that is simply *on* the active seat is easy to lose track of; a flash
    // as it arrives is what makes the hand-off followable.
    val was = before.players.getOrNull(before.currentPlayerIndex)?.id
    val now = after.players.getOrNull(after.currentPlayerIndex)?.id
    if (now != null && now != was && after.phase != GamePhase.SCORING) {
        beats += Beat.Attend(now, Attention.TURN)
    }

    return beats.takeIf { it.isNotEmpty() }
}

/**
 * Whether a declaration was right, shown on the card it was about.
 *
 * Derived from whether a penalty followed rather than from comparing ranks: a wrong call costs
 * a card and that is the only thing the rules do about it, so the penalty *is* the verdict.
 * One check covers the swap declaration and the King, and would cover a third if one were
 * added.
 */
private fun verdictScene(action: GameAction, penalised: Boolean): Scene? = when (action) {
    is GameAction.SwapCard -> action.payload.declaredRank?.let {
        listOf(Beat.Verdict(Anchor.Discard, correct = !penalised))
    }

    is GameAction.DeclareKingAction ->
        listOf(Beat.Verdict(Anchor.Discard, correct = !penalised))

    else -> null
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

/**
 * The card an action was just aimed at, lifted for everyone to see.
 *
 * Aimed at the *payload's* target rather than the pending action's last entry, because the
 * pending action is projected per seat and a watcher is not always given the list — but the
 * action itself says what was aimed at, and that a card was looked at is public.
 */
private fun peekScene(after: PlayerView, payload: SelectActionTargetPayload.Positional): Scene {
    val at = Anchor.Seat(payload.targetPlayerId, payload.position)

    // The face comes from the seat, not from the pending action. A peek does not travel on the
    // action — the engine records that the looker now *knows* the card, and the projection
    // turns it face-up for whoever is entitled. Reading the target entry instead returns null
    // even for your own peek, which is how this went out showing you nothing the first time.
    val seen = after.players
        .firstOrNull { it.id == payload.targetPlayerId }
        ?.cards?.getOrNull(payload.position) as? CardView.Visible

    return listOf(Beat.Peek(at, seen?.card))
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
