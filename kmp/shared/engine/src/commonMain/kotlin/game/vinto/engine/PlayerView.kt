package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What one seat is allowed to know.
 *
 * The server holds the whole `GameState` and sends each client only its own [PlayerView].
 * That is the entire anti-cheat model of design D9: hidden information exists in exactly one
 * process, and a client cannot ask for what it should not see because it is never sent.
 *
 * **A hidden card carries no id.** Card ids are `"7_0"`, `"K_2"`, `"Joker1"` — the id
 * *contains the rank*, so shipping ids for face-down cards would leak every hand while
 * looking perfectly redacted. This is why [CardView.Hidden] holds nothing at all.
 */
@Serializable
sealed interface CardView {

    /** A card this seat is entitled to see, in full. */
    @Serializable
    @SerialName("visible")
    data class Visible(val card: Card) : CardView

    /** A card that exists and nothing more. Deliberately empty — see the note above on ids. */
    @Serializable
    @SerialName("hidden")
    data object Hidden : CardView
}

@Serializable
data class PlayerSeatView(
    val id: String,
    val name: String,
    val nickname: String,
    val isHuman: Boolean,
    val isBot: Boolean,
    val cards: List<CardView>,
    /**
     * Which of their own cards this seat has seen. Public by nature — everyone watches a
     * player peek — and it says nothing about *what* the card is.
     */
    val knownCardPositions: List<Int>,
    val isVintoCaller: Boolean,
    val coalitionWith: List<String>,
)

/** Pending-action metadata without the card, unless the viewer is entitled to it. */
@Serializable
data class PendingActionView(
    val playerId: String,
    val actionPhase: ActionPhase,
    val from: PendingCardOrigin,
    val targetType: TargetType? = null,
    val declaredRank: Rank? = null,
    val card: CardView,
    val targets: List<PendingTargetView>,
)

@Serializable
data class PendingTargetView(
    val playerId: String,
    val position: Int,
    val card: CardView,
)

@Serializable
data class PlayerView(
    /** The seat this view was built for. */
    val viewerId: String,
    val gameId: String,
    val roundNumber: Int,
    val turnNumber: Int,
    val phase: GamePhase,
    val subPhase: GameSubPhase,
    val finalTurnTriggered: Boolean,
    val players: List<PlayerSeatView>,
    val currentPlayerIndex: Int,
    val vintoCallerId: String?,
    val coalitionLeaderId: String?,
    /** A count, not the cards. Knowing the order of the draw pile would decide the game. */
    val drawPileSize: Int,
    /** Face up on the table, so it is sent in full. */
    val discardPile: List<Card>,
    val pendingAction: PendingActionView?,
    val activeTossIn: ActiveTossIn?,
    val difficulty: Difficulty,
    /** Only once the game is over; before then a score is a hand nobody has revealed. */
    val scores: Map<String, Int>? = null,
)

/**
 * Redacts [state] down to what [playerId] may see. Pure, and the only thing the server sends
 * to a client.
 *
 * The rules, in the order they apply:
 *
 *  - **your own cards** only where `knownCardPositions` says you have looked;
 *  - **an opponent's card** only where your own `opponentKnowledge` records it — that is your
 *    memory of a peek, not a live window into their hand;
 *  - **cards the current action has revealed to you**, and only to you: a Queen's peek is
 *    private to the player making it, so target cards are visible to the acting seat alone;
 *  - **the coalition leader** sees every coalition member's hand — their own included, since
 *    the leader is a member — while the Vinto caller's stays hidden, which is the point of the
 *    coalition rule;
 *  - **everything, in `scoring`**, when the hands are turned over anyway;
 *  - **scores**, likewise only in `scoring`.
 *
 * One rule from the web app is deliberately **not** carried over: there, the Vinto caller can
 * see any bot card that any bot knows, on the reasoning that the bot coalition shares its
 * memory. That is a display affordance for a game whose opponents are all bots. Online, the
 * other seats are people, and it would hand the caller their cards.
 *
 * Never included at all: the draw pile's contents, other seats' `opponentKnowledge`, and
 * `botMemory`. Bots do not use views — they run in the same process as the full state.
 */
fun projectView(state: GameState, playerId: String): PlayerView {
    val viewer = state.players.firstOrNull { it.id == playerId }

    val revealedToViewer = revealedByCurrentAction(state, playerId)
    val viewerLeadsCoalition = state.vintoCallerId != null && state.coalitionLeaderId == playerId
    // Every hand is turned over once the game is scored.
    val everythingRevealed = state.phase == GamePhase.SCORING

    val seats = state.players.map { seat ->
        val ownKnowledge = viewer?.opponentKnowledge?.get(seat.id)?.knownCards.orEmpty()

        // The coalition condition matches the web app's: a member is anyone with a coalition
        // list who is not the caller — which includes the leader's own hand.
        val seatIsCoalitionMember = seat.coalitionWith.isNotEmpty() && !seat.isVintoCaller

        val cards = seat.cards.mapIndexed { position, card ->
            val visible = when {
                everythingRevealed -> true
                // A card the action in progress has shown the viewer is visible whoever owns
                // it — including the viewer's own. Ordering this after the self rule below is
                // how the two halves of a view came to disagree: a Queen peeking at its own
                // player's card showed it under `targets` and hid it in the hand.
                revealedToViewer.contains(seat.id to position) -> true
                viewerLeadsCoalition && seatIsCoalitionMember -> true
                seat.id == playerId -> position in seat.knownCardPositions
                ownKnowledge.containsKey(position) -> true
                else -> false
            }
            if (visible) CardView.Visible(card) else CardView.Hidden
        }

        PlayerSeatView(
            id = seat.id,
            name = seat.name,
            nickname = seat.nickname,
            isHuman = seat.isHuman,
            isBot = seat.isBot,
            cards = cards,
            knownCardPositions = seat.knownCardPositions,
            isVintoCaller = seat.isVintoCaller,
            coalitionWith = seat.coalitionWith,
        )
    }

    val pending = state.pendingAction?.let { pendingAction ->
        // The card in hand being played is known to the player playing it. Everyone else sees
        // that an action is happening, not what it is — until it reaches the discard pile.
        val actorSeesCard = pendingAction.playerId == playerId
        PendingActionView(
            playerId = pendingAction.playerId,
            actionPhase = pendingAction.actionPhase,
            from = pendingAction.from,
            targetType = pendingAction.targetType,
            declaredRank = pendingAction.declaredRank,
            card = if (actorSeesCard) CardView.Visible(pendingAction.card) else CardView.Hidden,
            targets = pendingAction.targets.map { target ->
                PendingTargetView(
                    playerId = target.playerId,
                    position = target.position,
                    card = target.card
                        ?.takeIf { actorSeesCard }
                        ?.let { CardView.Visible(it) }
                        ?: CardView.Hidden,
                )
            },
        )
    }

    return PlayerView(
        viewerId = playerId,
        gameId = state.gameId,
        roundNumber = state.roundNumber,
        turnNumber = state.turnNumber,
        phase = state.phase,
        subPhase = state.subPhase,
        finalTurnTriggered = state.finalTurnTriggered,
        players = seats,
        currentPlayerIndex = state.currentPlayerIndex,
        vintoCallerId = state.vintoCallerId,
        coalitionLeaderId = state.coalitionLeaderId,
        drawPileSize = state.drawPile.size,
        discardPile = state.discardPile.toList(),
        pendingAction = pending,
        activeTossIn = state.activeTossIn,
        difficulty = state.difficulty,
        scores = if (state.phase == GamePhase.SCORING) {
            calculateFinalScores(state.players, state.vintoCallerId)
        } else {
            null
        },
    )
}

/**
 * Cards the action in progress has shown to [playerId] — which is only ever the player making
 * it. A Queen peeks at two cards; the players who own them do not get to watch.
 */
private fun revealedByCurrentAction(state: GameState, playerId: String): Set<Pair<String, Int>> {
    val pending = state.pendingAction ?: return emptySet()
    if (pending.playerId != playerId) return emptySet()

    return pending.targets
        .filter { it.card != null }
        .map { it.playerId to it.position }
        .toSet()
}

/**
 * Every card in [state] that [playerId] is NOT entitled to see. Used by the leak test.
 *
 * Defined as "not [CardView.Visible]" rather than "is [CardView.Hidden]", and the difference
 * is not pedantry. Written the other way round, adding any third variant — say a stub keeping
 * the id "so the client can track animations" — makes this return nothing, and the leak test
 * passes by having no cards left to check. That exact substitution was tried against this
 * file and slipped through until the definition was inverted.
 */
internal fun hiddenFrom(state: GameState, playerId: String): List<Card> {
    val view = projectView(state, playerId)
    return state.players.flatMapIndexed { seatIndex, seat ->
        seat.cards.filterIndexed { position, _ ->
            view.players[seatIndex].cards[position] !is CardView.Visible
        }
    }
}
