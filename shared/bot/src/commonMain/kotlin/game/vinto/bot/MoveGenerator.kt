package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.CardAction
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardAction
import game.vinto.shapes.isActionable

/**
 * Every move the search may consider from a position.
 *
 * Two kinds of thing live in here and they are not equally negotiable:
 *
 *  - **Rules**, which must be exact. Only toss-in or pass during a toss-in; the discard pile
 *    is only takeable when its top card is an unused action; Jack and Queen need two cards
 *    from two *different* players; the coalition may not touch the Vinto caller's cards in
 *    the final round; and Vinto is called at the end of a turn, not the start. A generator
 *    that proposes an illegal move produces a bot the engine rejects mid-game.
 *  - **Orderings**, which are a search budget. A Jack could pair any of its owner's cards
 *    with any card at the table; the shortlist below keeps the pairs a player would actually
 *    consider — the dearest card it holds against the cheapest it has seen, and a blind slot
 *    against a blind slot — so the iterations go on comparing them rather than enumerating
 *    them. The shortlist orders candidates; the search chooses among them.
 */
object MoveGenerator {

    /** Vinto is not considered before everyone has had two turns. A pacing rule, not a tactic. */
    private const val OPENING_TURNS_PER_PLAYER = 2

    /** Own and opposing positions kept per Jack or Queen, each side. */
    private const val SHORTLIST = 3

    fun generateMoves(state: MctsGameState): List<MctsMove> {
        val currentPlayer = state.currentPlayer ?: return emptyList()

        if (state.awaitingVintoDecision) return endOfTurnMoves(state, currentPlayer)

        // A toss-in window is not a turn: the only choices are to throw matching cards in or
        // to sit it out.
        if (state.isTossInPhase) return tossInMoves(state, currentPlayer)

        state.pendingCard?.let { return pendingCardMoves(state, currentPlayer, it) }

        val moves = mutableListOf<MctsMove>()
        if (state.deckSize > 0) moves += MctsMove(MctsMoveType.DRAW, currentPlayer.id)

        // Taking from the discard commits you to playing the action, so it is only offered
        // when there is an unused one to play.
        val discardTop = state.discardPileTop
        if (discardTop != null && !discardTop.actionText.isNullOrEmpty() && !discardTop.played) {
            moves += MctsMove(MctsMoveType.TAKE_DISCARD, currentPlayer.id)
        }
        return moves
    }

    /** The turn is over: call Vinto, or let play move on. */
    private fun endOfTurnMoves(state: MctsGameState, currentPlayer: MctsPlayerState): List<MctsMove> {
        val moves = mutableListOf(MctsMove(MctsMoveType.PASS, currentPlayer.id))
        if (mayCallVinto(state)) moves += MctsMove(MctsMoveType.CALL_VINTO, currentPlayer.id)
        return moves
    }

    /** Nobody has called yet, and the opening is over. */
    fun mayCallVinto(state: MctsGameState): Boolean =
        mayCallVinto(state.vintoCallerId, state.turnCount, state.players.size)

    fun mayCallVinto(vintoCallerId: String?, turnCount: Int, seats: Int): Boolean =
        vintoCallerId == null && turnCount >= seats * OPENING_TURNS_PER_PLAYER

    /**
     * What may be done with the card in play. Drawn, it may be played, swapped in at any
     * position, or discarded; taken or borrowed, it must be aimed — or put down unplayed when
     * there is nowhere to aim it, which is the exit the engine offers for a peek with nothing
     * left to look at.
     */
    private fun pendingCardMoves(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
        pending: Card,
    ): List<MctsMove> {
        val moves = mutableListOf<MctsMove>()
        val action = getCardAction(pending.rank).takeIf { pending.rank.isActionable() && !pending.played }
        if (action != null) moves += generateActionMoves(state, action)

        if (state.pendingOrigin == PendingOrigin.DRAWN) {
            for (position in 0 until currentPlayer.cardCount) {
                moves += MctsMove(
                    MctsMoveType.SWAP,
                    currentPlayer.id,
                    swapPosition = position,
                    cardInPlay = pending.rank,
                )
            }
            moves += MctsMove(MctsMoveType.DISCARD, currentPlayer.id, cardInPlay = pending.rank)
        } else if (moves.isEmpty()) {
            moves += MctsMove(MctsMoveType.DISCARD, currentPlayer.id, cardInPlay = pending.rank)
        }
        return moves
    }

    /**
     * All matching cards go in as one move, not several.
     *
     * The rules resolve a toss-in as a single act, and splitting it would let the search
     * explore throwing one of a pair and keeping the other — which is never what a player
     * would choose and doubles the branching for nothing.
     */
    private fun tossInMoves(state: MctsGameState, currentPlayer: MctsPlayerState): List<MctsMove> {
        val moves = mutableListOf(MctsMove(MctsMoveType.PASS, currentPlayer.id))

        val validRanks = state.tossInRanks.ifEmpty { listOfNotNull(state.discardPileTop?.rank) }
        if (validRanks.isEmpty()) return moves

        // The searching bot may only throw what it remembers; anyone else, in a sampled
        // world, throws what the world dealt them.
        val matching = (0 until currentPlayer.cardCount).filter { position ->
            val remembered = currentPlayer.knownCards[position]
                ?.takeIf { it.confidence > TRUSTED_CONFIDENCE }
                ?.card
            val card = remembered
                ?: state.hiddenCards[state.hiddenCardKey(currentPlayer.id, position)]
                    ?.takeIf { currentPlayer.id != state.botPlayerId }
            card != null && card.rank in validRanks && card.value >= 0
        }

        if (matching.isNotEmpty()) {
            moves += MctsMove(MctsMoveType.TOSS_IN, currentPlayer.id, tossInPositions = matching)
        }
        return moves
    }

    fun generateActionMoves(state: MctsGameState, actionType: CardAction): List<MctsMove> {
        val currentPlayer = state.currentPlayer ?: return emptyList()
        val rank = state.pendingCard?.rank

        return when (actionType) {
            CardAction.PEEK_OWN -> unknownPositions(currentPlayer).map { position ->
                aimed(currentPlayer, rank, listOf(MctsActionTarget(currentPlayer.id, position)))
            }

            CardAction.PEEK_OPPONENT -> targetableOpponents(state, currentPlayer).flatMap { opponent ->
                unknownPositions(opponent).map { position ->
                    aimed(currentPlayer, rank, listOf(MctsActionTarget(opponent.id, position)))
                }
            }

            CardAction.SWAP_CARDS -> twoPlayerMoves(state, currentPlayer, peekFirst = false)
            CardAction.PEEK_AND_SWAP -> twoPlayerMoves(state, currentPlayer, peekFirst = true)
            CardAction.FORCE_DRAW -> forceDrawMoves(state, currentPlayer)
            CardAction.DECLARE_ACTION -> kingMoves(state, currentPlayer)
        }
    }

    private fun aimed(
        player: MctsPlayerState,
        rank: Rank?,
        targets: List<MctsActionTarget>,
        shouldSwap: Boolean? = null,
        declaredRank: Rank? = null,
    ) = MctsMove(
        MctsMoveType.USE_ACTION,
        player.id,
        targets = targets,
        shouldSwap = shouldSwap,
        declaredRank = declaredRank,
        cardInPlay = rank,
    )

    /**
     * Jack and Queen both take two cards from two *different* players — a rule, not a
     * preference. The shortlist pairs the mover's dearest known cards (or a blind slot, which
     * the search prices by sampling it) against the cheapest cards it has seen in each
     * opponent's hand, plus that opponent's blind slots; the Queen, which looks before it
     * trades, prefers blind slots on both sides.
     *
     * The Jack also gets the one legitimate "no": aim it and leave both cards where they
     * are. A Jack that was tossed in has to be aimed, and a search never offered the skip
     * could not choose it when every trade on the table loses points.
     */
    private fun twoPlayerMoves(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
        peekFirst: Boolean,
    ): List<MctsMove> {
        val rank = state.pendingCard?.rank
        val ownPositions = if (peekFirst) {
            unknownPositions(currentPlayer) + knownPositionsByValue(currentPlayer).reversed()
        } else {
            knownPositionsByValue(currentPlayer).reversed() + unknownPositions(currentPlayer)
        }.take(SHORTLIST)

        val moves = mutableListOf<MctsMove>()
        for (opponent in targetableOpponents(state, currentPlayer)) {
            val theirPositions = if (peekFirst) {
                unknownPositions(opponent) + knownPositionsByValue(opponent)
            } else {
                // Cheapest first: a blind swap *receives* this card, so the Joker the bot has
                // seen in an opponent's hand is the whole point of playing the Jack.
                knownPositionsByValue(opponent) + unknownPositions(opponent)
            }.take(SHORTLIST)

            for (own in ownPositions) {
                for (theirs in theirPositions) {
                    val targets = listOf(
                        MctsActionTarget(currentPlayer.id, own),
                        MctsActionTarget(opponent.id, theirs),
                    )
                    moves += aimed(currentPlayer, rank, targets, shouldSwap = true)
                }
            }
        }

        if (!peekFirst) moves.firstOrNull()?.let { moves += it.copy(shouldSwap = false) }
        return moves
    }

    /**
     * An Ace makes someone draw. The coalition must not aim it at the Vinto caller (the rule
     * forbidding interaction with their cards) and should not aim it at its own champion
     * either — handing a card to the one member who can still win is friendly fire.
     */
    private fun forceDrawMoves(state: MctsGameState, currentPlayer: MctsPlayerState): List<MctsMove> {
        val championId = coalitionChampion(state)?.id
        val inCoalition = isCoalitionMember(state, currentPlayer.id)

        return targetableOpponents(state, currentPlayer)
            .filterNot { inCoalition && it.id == championId }
            // An Ace names a player, not a card; position 0 is a placeholder.
            .map { aimed(currentPlayer, state.pendingCard?.rank, listOf(MctsActionTarget(it.id, 0))) }
    }

    /**
     * A King names a card and declares its rank; if right, that card leaves its hand and its
     * action is the declarer's to play, and every matching card at the table may be tossed
     * in. Only a card the mover *knows* is a candidate — naming a card blind costs a penalty
     * card, and the search has nothing to learn from a guess. Own cards come first, then each
     * opponent's; in coalition the caller's cards are off limits, as everywhere.
     */
    private fun kingMoves(state: MctsGameState, currentPlayer: MctsPlayerState): List<MctsMove> {
        val rank = state.pendingCard?.rank
        val holders = listOf(currentPlayer) + targetableOpponents(state, currentPlayer)

        return holders.flatMap { holder ->
            knownCards(holder).entries
                .sortedByDescending { it.value.value }
                .map { (position, card) ->
                    aimed(
                        currentPlayer,
                        rank,
                        listOf(MctsActionTarget(holder.id, position)),
                        declaredRank = card.rank,
                    )
                }
        }
    }

    /**
     * A second check that a move is playable, used where moves are carried between states.
     * Generation already respects these; this catches a stale move applied to a state that
     * has moved on.
     */
    fun isLegalMove(state: MctsGameState, move: MctsMove): Boolean {
        val currentPlayer = state.currentPlayer ?: return false
        if (move.playerId != currentPlayer.id) return false

        if (state.isTossInPhase) {
            return move.type == MctsMoveType.TOSS_IN || move.type == MctsMoveType.PASS
        }

        move.swapPosition?.let { position ->
            if (position !in 0 until currentPlayer.cardCount) return false
        }

        return move.targets.all { target ->
            val targetPlayer = state.players.firstOrNull { it.id == target.playerId }
            targetPlayer != null && target.position < targetPlayer.cardCount
        }
    }

    // --- shared helpers ----------------------------------------------------------------

    /**
     * Opponents this player may act on.
     *
     * The filter is the final-round rule that nobody may interact with the Vinto caller's
     * cards — a rule of the game, not a tactic, which is why it lives at the point where
     * moves are created rather than being left to a later check.
     */
    private fun targetableOpponents(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
    ): List<MctsPlayerState> {
        val inCoalition = isCoalitionMember(state, currentPlayer.id)
        return state.players.filter { opponent ->
            opponent.id != currentPlayer.id &&
                !(inCoalition && opponent.id == state.vintoCallerId)
        }
    }

    /** Everybody but the caller, from the moment of the call: the protection does not wait for a leader. */
    fun isCoalitionMember(state: MctsGameState, playerId: String): Boolean =
        state.vintoCallerId != null && playerId != state.vintoCallerId

    /** The coalition member with the lowest hand in this world; null outside the final round. */
    fun coalitionChampion(state: MctsGameState): MctsPlayerState? {
        if (state.vintoCallerId == null) return null
        return state.players
            .filter { it.id != state.vintoCallerId }
            .minByOrNull { StateTransition.handTotal(state, it.id) }
    }

    /**
     * What the bot is sure of, bounded by the hand that actually exists.
     *
     * The bound is not defensive tidying. Memories outlive the hands they describe — cards
     * are tossed in and everything after them renumbers, but the memory keeps its old
     * position — so a hand that has shrunk still remembers a card at an index past its end.
     * Without the bound the generator offers a target the engine rejects outright, and the
     * bot is left holding an action it cannot aim.
     */
    fun knownCards(player: MctsPlayerState): Map<Int, Card> =
        player.knownCards
            .filterKeys { it in 0 until player.cardCount }
            .filterValues { it.confidence > TRUSTED_CONFIDENCE }
            .mapValues { it.value.card }

    fun unknownPositions(player: MctsPlayerState): List<Int> =
        (0 until player.cardCount).filter { position ->
            val memory = player.knownCards[position]
            memory == null || memory.confidence <= TRUSTED_CONFIDENCE
        }

    /** Known positions, cheapest first. */
    fun knownPositionsByValue(player: MctsPlayerState): List<Int> =
        knownCards(player).entries.sortedBy { it.value.value }.map { it.key }
}
