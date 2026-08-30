package game.vinto.engine

import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue

/**
 * Toss-in phase management, ported from
 * `legacy-web/packages/engine/src/lib/utils/toss-in-utils.ts`. These mutate the working state exactly
 * as their TypeScript counterparts do — see [MutableGameState] for why.
 */

/**
 * Players automatically marked ready: only those who have called Vinto, who may not
 * participate in toss-in afterwards.
 *
 * Coalition members keep their toss-in rights during the final round — shedding matching
 * cards is one of the coalition's main tools against the caller.
 */
fun getAutomaticallyReadyPlayers(state: MutableGameState): MutableList<String> =
    state.players.filter { it.isVintoCaller }.map { it.id }.toMutableList()

/**
 * Resets the ready list so players can confirm again for a new toss-in round. Called when a
 * queued action completes or the flow returns to toss-in.
 */
fun clearTossInReadyList(state: MutableGameState) {
    state.activeTossIn?.playersReadyForNextTurn = getAutomaticallyReadyPlayers(state)
}

/**
 * Deterministic id for the materialised card of a queued toss-in action.
 *
 * Derived entirely from state so replays reproduce it exactly. The queue only ever shrinks
 * while a turn's queued actions are processed, so `remaining` distinguishes successive
 * materialisations within the same turn.
 */
fun queuedTossInCardId(turnNumber: Int, playerId: String, rank: Rank, remaining: Int): String =
    "tossin_queued_${turnNumber}_${playerId}_${rank.serialName}_$remaining"

fun areAllPlayersReady(state: MutableGameState): Boolean {
    val tossIn = state.activeTossIn ?: return false
    return state.players.size == tossIn.playersReadyForNextTurn.size
}

/**
 * Advances the turn once every toss-in action has been processed.
 *
 * The ranks are deliberately NOT reset: they were set correctly when the cards were
 * discarded. If a King declared an Ace correctly the ranks are `[K, A]`, and both must
 * survive into the next turn.
 */
/**
 * A failed toss-in bars that player for the rest of the **round**, and the round is the deal —
 * not one lap of the table.
 *
 * `roundNumber` here counts laps: it goes up every time the turn comes back to the first seat.
 * Clearing `roundFailedAttempts` alongside it made the bar last one lap, so a player who threw
 * a wrong card in could be throwing again three turns later, and was free again for the whole
 * final round — which is exactly when a barred player would most like to dump a card. The bar
 * now ends where the deal does, because that is the only place it is set back to empty
 * (`initializeGame`).
 *
 * Hash-neutral over the parity corpus: fifty recordings contain a single failed toss-in, and
 * that game ends before the turn wraps again, so no recorded state ever depended on the
 * clearing. `CorpusReplayTest` is what says so rather than this comment.
 */
fun advanceTurnAfterTossIn(state: MutableGameState) {
    val tossIn = state.activeTossIn ?: return

    val originalPlayerIndex = tossIn.originalPlayerIndex
    val nextPlayerIndex = (originalPlayerIndex + 1) % state.players.size

    // The game ends when the final round comes back round to the Vinto caller.
    if (state.phase == GamePhase.FINAL && state.players[nextPlayerIndex].id == state.vintoCallerId) {
        state.phase = GamePhase.SCORING
        state.subPhase = GameSubPhase.IDLE
        state.activeTossIn = null
        return
    }

    state.currentPlayerIndex = nextPlayerIndex
    state.turnNumber++

    if (state.currentPlayerIndex == 0) {
        state.roundNumber++
    }

    if (state.drawPile.length == 1) {
        state.rngState = state.drawPile.reshuffleFrom(state.discardPile, state.rngState)
    }

    if (state.pendingAction != null) {
        state.subPhase = GameSubPhase.AWAITING_ACTION
        tossIn.waitingForInput = false
    }

    // Participation data is cleared, but the ranks persist — they describe the cards
    // currently on top of the discard pile.
    tossIn.participants = mutableListOf()
    tossIn.queuedActions = mutableListOf()
    tossIn.waitingForInput = false
    tossIn.playersReadyForNextTurn = getAutomaticallyReadyPlayers(state)
    tossIn.failedAttempts = mutableListOf()
    tossIn.originalPlayerIndex = state.currentPlayerIndex

    state.subPhase =
        if (state.players[state.currentPlayerIndex].isBot) {
            GameSubPhase.AI_THINKING
        } else {
            GameSubPhase.IDLE
        }
}

/**
 * A King in play widens the toss-in to its declared rank as well; any other card replaces
 * the set outright.
 */
fun addTossInCard(currentRanks: MutableList<Rank>, rank: Rank?): MutableList<Rank> {
    if (rank == null || currentRanks.contains(rank)) return currentRanks
    return if (currentRanks.contains(Rank.KING)) {
        (currentRanks + rank).toMutableList()
    } else {
        mutableListOf(rank)
    }
}

/**
 * Discards the card that finished an action and moves the flow on: opening a fresh toss-in,
 * widening the current one, or pulling the next queued action off the front.
 */
fun clearTossInAfterActionableCard(
    pendingCard: MutableCard?,
    state: MutableGameState,
    playerId: String,
) {
    val tossIn = state.activeTossIn

    if (pendingCard != null) {
        // A card from the toss-in queue goes beneath the top, so an unplayed action card
        // stays available on top of the discard pile.
        if (tossIn != null && tossIn.queuedActions.isNotEmpty()) {
            state.discardPile.addBeforeTop(pendingCard)
        } else {
            state.discardPile.addToTop(pendingCard)
        }
    }

    if (tossIn == null) {
        if (pendingCard != null) {
            state.activeTossIn = MutableActiveTossIn(
                ranks = mutableListOf(pendingCard.rank),
                initiatorId = playerId,
                originalPlayerIndex = state.currentPlayerIndex,
                participants = mutableListOf(),
                queuedActions = mutableListOf(),
                waitingForInput = true,
                playersReadyForNextTurn = getAutomaticallyReadyPlayers(state),
            )
        }
        state.pendingAction = null
        state.subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE
        return
    }

    if (tossIn.queuedActions.isEmpty()) {
        val pending = state.pendingAction
        tossIn.ranks =
            if (pending?.from == PendingCardOrigin.DRAWING) {
                mutableListOf(pending.card.rank)
            } else {
                addTossInCard(tossIn.ranks, pending?.card?.rank)
            }

        state.pendingAction = null
        clearTossInReadyList(state)
        state.subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE
        tossIn.waitingForInput = true
        return
    }

    state.pendingAction = null
    tossIn.queuedActions.removeAt(0)

    if (tossIn.queuedActions.isNotEmpty()) {
        val nextAction = tossIn.queuedActions[0]
        state.pendingAction = MutablePendingAction(
            card = MutableCard(
                id = queuedTossInCardId(
                    state.turnNumber,
                    nextAction.playerId,
                    nextAction.rank,
                    tossIn.queuedActions.size,
                ),
                rank = nextAction.rank,
                value = getCardValue(nextAction.rank),
                actionText = getCardShortDescription(nextAction.rank),
                played = false,
            ),
            playerId = nextAction.playerId,
            actionPhase = game.vinto.shapes.ActionPhase.CHOOSING_ACTION,
            from = PendingCardOrigin.HAND,
            targetType = getTargetTypeFromRank(nextAction.rank),
            targets = mutableListOf(),
        )

        val actionPlayerIndex = state.players.indexOfFirst { it.id == nextAction.playerId }
        if (actionPlayerIndex != -1) state.currentPlayerIndex = actionPlayerIndex

        state.subPhase = GameSubPhase.AWAITING_ACTION
    } else {
        clearTossInReadyList(state)
        // Back to toss-in so a human can still call Vinto.
        state.subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE
    }
}

/**
 * Whether a wrong toss-in still bars this player.
 *
 * A failed throw costs a penalty card and shuts the player out — but out of *what*, and for
 * how long, is the part the rules text never says and the part players feel. It used to be
 * the whole round: miss once on Raph's discard and you sat out every window until the deal
 * ended, including windows opened by cards you could not have known about when you guessed.
 * That is a very long punishment for one wrong read, and it makes the toss-in — the one
 * moment that belongs to the whole table at once — something most players stop touching.
 *
 * So the bar is now **the window you got wrong**, and the round-long version is kept for the
 * **final round** alone, where the coalition is playing one hand against the caller and a
 * second guess is a second chance at a shared prize rather than at your own.
 *
 * Decided by the product owner, and it reverses the previous decision recorded in
 * `VINTO_RULES.md`; that table says so.
 *
 * **This is a validator rule, not state.** `roundFailedAttempts` still records every failure
 * for the whole round — it is history, and it is inside the canonical hash — so the frozen
 * parity corpus is untouched by this. What changed is only which of the two lists is
 * *consulted*, and a validator that refuses less can never reject a recorded action.
 */
fun isBarredFromTossIn(state: GameState, playerId: String): Boolean =
    if (state.phase == GamePhase.FINAL) {
        state.roundFailedAttempts.any { it.playerId == playerId }
    } else {
        state.activeTossIn?.failedAttempts.orEmpty().any { it.playerId == playerId }
    }
