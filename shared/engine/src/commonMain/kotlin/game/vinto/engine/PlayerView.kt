package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
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
    /**
     * Position → rank this seat has *claimed* its card to be during the final round. Table
     * talk: public to every viewer, the caller included, and only as reliable as the
     * claimant's memory. The real card stays hidden.
     */
    val declaredCards: Map<Int, Rank> = emptyMap(),
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
    /**
     * The card face up on the discard pile, and how many are under it.
     *
     * The **top card only**, because the top card is the only one anybody can see: the rest
     * of the pile is a stack of covered cards, and what went into it is something players
     * remember rather than something they can read. The whole pile used to be sent, on the
     * reasoning that a discard is public — but "public" is the top of it, and a client
     * holding the order of every card discarded this round holds a perfect record of a game
     * whose difficulty is remembering one.
     *
     * The count is public in the way a stack's thickness is: you can see how many.
     */
    val discardTop: Card?,
    val discardCount: Int,
    val pendingAction: PendingActionView?,
    val activeTossIn: ActiveTossIn?,
    /**
     * Who has already thrown in a wrong card this round, and is barred from trying again.
     *
     * Public by nature — a failed toss-in happens face-up, in front of everyone — and it is
     * the kind of rule a player breaks once and then cannot see they have broken: without it
     * the toss-in window simply refuses you, with no way to tell that it is refusing you
     * *permanently* rather than because you were slow.
     */
    val barredFromTossIn: List<String> = emptyList(),
    val difficulty: Difficulty,
    /** Only once the game is over; before then a score is a hand nobody has revealed. */
    val scores: Map<String, Int>? = null,
    /**
     * Milliseconds left in the session, or null outside one.
     *
     * **Passed in, never read.** The engine has no clock and the purity guard fails the build
     * on one; a session length is the room's business. It is here rather than beside the view
     * because of what the rule does: past the buzzer an undeclared round is discarded, so the
     * only way to bank one near the end is to call Vinto first — a real decision, and only if
     * the player can see the deadline they are playing against.
     */
    val sessionMsRemaining: Long? = null,
)

/**
 * The viewer's own seat, or null when this view is not of a table they are sitting at.
 *
 * **Nullable on purpose, and the nullability is the point.** A view is wire data: it arrives
 * from a room that decides who is seated, and a client cannot assume the answer. `tableFor`
 * has always known that — it opens with a `firstOrNull` and answers `Ask.Watching` — and the
 * screen beside it used `players.first { it.id == viewerId }`, which throws. So the model
 * politely handled the case and the felt crashed on it, one function later, with nothing
 * between the exception and the launcher.
 *
 * A solo game always seats you, which is why this was invisible for the life of the branch and
 * why it is an *online* crash. Reaching the seat through here makes the compiler ask the
 * question at every call site, which is the only mechanism Kotlin offers against a partial
 * function: `first {}` is total in the type system and a crash in the world.
 */
val PlayerView.mySeat: PlayerSeatView?
    get() = players.firstOrNull { it.id == viewerId }

/**
 * Redacts [state] down to what [playerId] may see. Pure, and the only thing the server sends
 * to a client.
 *
 * The rules, in the order they apply:
 *
 *  - **your own two cards during setup**, which the rules tell you to look at — and *only*
 *    during setup. What a seat has since remembered is not sent: the engine knows it, the
 *    player is supposed to;
 *  - **cards the current action has revealed to you**, and only to you: a Queen's peek is
 *    private to the player making it, so target cards are visible to the acting seat alone,
 *    and only while the action is running;
 *  - **everything, in `scoring`**, when the hands are turned over anyway;
 *  - **scores**, likewise only in `scoring`.
 *
 * Two earlier rules are deliberately **not** here. The web app let the Vinto caller see any
 * bot card that any bot knew — a display affordance for a game whose opponents are all bots;
 * online the other seats are people, and it would hand the caller their cards. And an earlier
 * Kotlin rule showed the coalition *leader* every member's real hand — replaced by
 * `DECLARE_CARDS`: coalition members say what they believe they hold, the claims ride on
 * every seat's view as [PlayerSeatView.declaredCards], and nobody's actual cards turn over.
 * A claim is only as good as the claimant's memory, which is the game staying the game.
 *
 * Never included at all: the draw pile's contents, other seats' `opponentKnowledge`, and
 * `botMemory`. Bots do not use views — they run in the same process as the full state.
 */
fun projectView(state: GameState, playerId: String, sessionMsRemaining: Long? = null): PlayerView {
    val revealedToViewer = revealedByCurrentAction(state, playerId)
    // Every hand is turned over once the game is scored.
    val everythingRevealed = state.phase == GamePhase.SCORING
    val inSetup = state.phase == GamePhase.SETUP

    val seats = state.players.map { seat ->
        val cards = seat.cards.mapIndexed { position, card ->
            val visible = when {
                everythingRevealed -> true
                // A card the action in progress has shown the viewer is visible whoever owns
                // it — including the viewer's own. Ordering this after the self rule below is
                // how the two halves of a view came to disagree: a Queen peeking at its own
                // player's card showed it under `targets` and hid it in the hand.
                revealedToViewer.contains(seat.id to position) -> true
                // The setup peek, and only during setup: the rules say look at two of your
                // own, so for that phase they are yours to see.
                //
                // What is *not* here is the rest of the round. The engine remembers what
                // every seat has learned — it must, for the bots and for scoring — and this
                // used to send a seat its own remembered cards face-up for the whole round,
                // with the screen politely declining to draw them. That is the wrong place
                // for the rule to live: the client holds the answer and is trusted not to
                // look, and a client we did not write is not trustworthy at all. Remembering
                // your hand is the game; a client that cannot forget has already won it.
                inSetup && seat.id == playerId -> position in seat.knownCardPositions
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
            declaredCards = seat.declaredCards ?: emptyMap(),
        )
    }

    val pending = state.pendingAction?.let { pendingAction ->
        // **The card in play is public.** The rules are explicit about it — a player "draws
        // the top card from the draw pile and *reveals it publicly*" — and every other way a
        // card becomes pending is public too: taken off the discard pile, thrown in face-up,
        // or borrowed by a King that is itself on the table. There is no pending card that
        // arrived face-down.
        //
        // It was hidden here at first, on the reasonable-sounding principle that a card in
        // somebody's hand is theirs. The cost was a game nobody could learn or play well:
        // watching what an opponent drew and what they then did with it is most of the
        // information in Vinto, and without it three bots take their turns behind a screen.
        //
        // What stays secret is what the action has *looked at* — a Queen peeking at two cards
        // shows them to the player holding the Queen and to nobody else.
        // The same rule as the seats above: a target carries its card only when the action
        // aimed at it is one that looks. Anything else and a Jack's two cards arrive here
        // instead of in the hand, which is the same leak through a different field.
        val actorSeesCard = pendingAction.playerId == playerId && pendingAction.looks()
        PendingActionView(
            playerId = pendingAction.playerId,
            actionPhase = pendingAction.actionPhase,
            from = pendingAction.from,
            targetType = pendingAction.targetType,
            declaredRank = pendingAction.declaredRank,
            card = CardView.Visible(pendingAction.card),
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
        discardTop = state.discardPile.peekTop(),
        discardCount = state.discardPile.size,
        pendingAction = pending,
        activeTossIn = state.activeTossIn,
        // Who is actually barred *now*, which outside the final round is only whoever got
        // this window wrong — not everybody who has missed once this round.
        barredFromTossIn = state.players
            .map { it.id }
            .filter { isBarredFromTossIn(state, it) },
        difficulty = state.difficulty,
        scores = if (state.phase == GamePhase.SCORING) {
            calculateFinalScores(state.players, state.vintoCallerId)
        } else {
            null
        },
        sessionMsRemaining = sessionMsRemaining,
    )
}

/**
 * Cards the action in progress has shown to [playerId] — which is only ever the player making
 * it. A Queen peeks at two cards; the players who own them do not get to watch.
 */
private fun revealedByCurrentAction(state: GameState, playerId: String): Set<Pair<String, Int>> {
    val pending = state.pendingAction ?: return emptySet()
    if (pending.playerId != playerId) return emptySet()
    if (!pending.looks()) return emptySet()

    return pending.targets.map { it.playerId to it.position }.toSet()
}

/**
 * Whether the action in progress is one that *looks* at what it is aimed at.
 *
 * This is the whole of the rule, and it is decided by the card rather than by what the player
 * happens to remember. Three of them look — a Seven or Eight at one of your own, a Nine or Ten
 * at somebody else's, a Queen at two before deciding whether to swap them — and the rest do
 * not:
 *
 *  * a **Jack** swaps two cards with nobody looking. The engine records the cards on the
 *    action because it has to move them, and the view used to pass them straight to the player
 *    who played it: two hidden cards read, every time, for free. That is the entire difference
 *    between a Jack and a Queen, and it was worth ten points.
 *  * a **King** is aimed *before* it is declared — you point at a card and say what it is — so
 *    revealing what it is aimed at hands the player the answer to the question the card asks.
 *
 * Earlier this asked whether the viewer *knew* the card, which is not the same question and
 * cannot be made into it: a card you peeked in setup is one you know, so a King aimed at it
 * came back face-up and the declaration was free. What you remember is yours to keep track
 * of; what the engine sends is what you are being shown right now.
 */
private fun PendingAction.looks(): Boolean = when (targetType) {
    TargetType.OWN_CARD, TargetType.OPPONENT_CARD, TargetType.PEEK_THEN_SWAP -> true
    TargetType.SWAP_CARDS, TargetType.FORCE_DRAW, TargetType.DECLARE_ACTION, null -> false
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

/**
 * The card whose action is being played, which is lying face up on the discard pile.
 *
 * Not the same thing as "a card is pending". A card that has just been drawn is pending and
 * is *not* on the pile — it is in front of its player, being decided about. The moment its
 * action is played it goes down on the pile, and everything that follows — the declaration a
 * King asks for, the two cards a Queen looks at, the toss-in window that opens for its rank —
 * happens with the card lying there for everyone to see.
 *
 * The engine models this by opening the toss-in window at that moment while keeping the card
 * in `pendingAction` until the action resolves, which left the table with a window open for
 * Kings and an empty discard pile under it. This is the same fact read from the view: it is
 * on the pile, because that is where a card whose action you are watching actually is.
 *
 * A card that came off the *table* rather than off the deck — thrown in, or taken from the
 * discard — is on the pile from the moment it is played, whatever phase its action is in.
 * Only a freshly drawn card has a phase in which it is somewhere else: in front of its
 * player, being decided about.
 */
val PlayerView.cardInPlay: Card?
    get() = pendingAction
        ?.takeIf { it.from != PendingCardOrigin.DRAWING || it.actionPhase != ActionPhase.CHOOSING_ACTION }
        ?.let { (it.card as? CardView.Visible)?.card }

/**
 * Whose turn it is — which, during a toss-in window, is not who is acting.
 *
 * A toss-in happens *inside* somebody's turn: the window opens on the card they put down,
 * anybody holding a match throws it in, and each thrown card's action is resolved for the
 * player who threw it. The engine points `currentPlayerIndex` at that player while their
 * action runs, because it is their action to aim — and the turn returns to the table's order
 * the moment the window closes.
 *
 * Read literally, that made the table say somebody else's turn had begun: a player put down a
 * King, the seat two along threw one in, and the ring moved to them while the window was still
 * open on the first player's turn. The turn is where the window says it started.
 */
val PlayerView.turnHolderId: String?
    get() = players.getOrNull(activeTossIn?.originalPlayerIndex ?: currentPlayerIndex)?.id
