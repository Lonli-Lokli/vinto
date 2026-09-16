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
 *  - **Orderings**, which are a prior. An aimed card could pair any of its owner's cards with
 *    any card at the table, and the search cannot afford to treat all of those as equals. So
 *    the aims come out **best first**, priced in the one currency the mover actually has: the
 *    value of a card it remembers, and the deck's mean for a slot it has not read.
 *
 * The ordering is a prior, not a decision — [generateMoves] takes an `aims` budget and the
 * search raises it as it visits a node (`MctsBotDecisionService.aimsAt`), so a node the
 * search keeps coming back to ends up looking at every aim. What the prior buys is the first
 * few visits, which at 2,000 iterations across a table of four is most of them.
 *
 * **Why this is not a detail.** A node carries the *mean* of what it offers, not the best of
 * it, so an aim list padded with trades between two slots nobody has read prices its own
 * parent down. That is how a free Jack was left on the pile with a Joker face-up in
 * somebody's row, and how a Queen was aimed anywhere but at the Joker it had watched land:
 * both reported from a phone, both in `ReportedGamesTest`. The Queen's was the blunter of
 * the two — its targets were "every unread slot, then the cards I know", cut to three, and a
 * five-card hand has four unread slots, so a card the bot *had* read could not be aimed at
 * at all.
 */
object MoveGenerator {

    /** Vinto is not considered before everyone has had two turns. A pacing rule, not a tactic. */
    private const val OPENING_TURNS_PER_PLAYER = 2

    /** Own and opposing positions kept per Jack or Queen, each side. */
    private const val SHORTLIST = 3

    /**
     * @param aims how many of an action's targets to offer, best first. Unbounded for a
     *   caller that wants the whole set; the search raises it with a node's visit count.
     */
    fun generateMoves(state: MctsGameState, aims: Int = Int.MAX_VALUE): List<MctsMove> {
        val currentPlayer = state.currentPlayer ?: return emptyList()

        if (state.awaitingVintoDecision) return endOfTurnMoves(state, currentPlayer)

        // A toss-in window is not a turn: the only choices are to throw matching cards in or
        // to sit it out.
        if (state.isTossInPhase) return tossInMoves(state, currentPlayer)

        state.pendingCard?.let { return pendingCardMoves(state, currentPlayer, it, aims) }

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
        aims: Int,
    ): List<MctsMove> {
        val moves = mutableListOf<MctsMove>()
        val action = getCardAction(pending.rank).takeIf { pending.rank.isActionable() && !pending.played }
        if (action != null) moves += generateActionMoves(state, action, aims)

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

    fun generateActionMoves(
        state: MctsGameState,
        actionType: CardAction,
        aims: Int = Int.MAX_VALUE,
    ): List<MctsMove> {
        val currentPlayer = state.currentPlayer ?: return emptyList()
        val rank = state.pendingCard?.rank

        return aimsFor(state, currentPlayer, rank, actionType).take(aims)
    }

    /** Every aim this action could take, best first. The budget is applied by the caller. */
    private fun aimsFor(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
        rank: Rank?,
        actionType: CardAction,
    ): List<MctsMove> = when (actionType) {
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
     * preference — and both are aimed by the same question: **what would this trade be
     * worth?** Every slot is priced at the card the mover remembers there, or at the deck's
     * mean where it has read nothing, and the pairs come out by what they gain. So the
     * cheapest card the mover has seen in somebody's row is the first target on the table,
     * and the dearest card it can name is the first thing it offers for it.
     *
     * The two cards are alike enough to share the ordering and differ in one place only. A
     * Jack is blind: it takes what it aimed at, so its gain is the price difference, and it
     * gets the one legitimate "no" — aim it and leave both cards where they are, ranked at
     * the nothing it changes, which is what a search picks when every trade on the table
     * loses. A Queen looks first and only trades when the trade sheds points, so it cannot
     * lose by aiming and its downside is floored at zero.
     *
     * [SHORTLIST] bounds each *side* rather than the pairs, and with the sides ordered it
     * cannot cut the pair that matters: the dearest card the mover holds against the
     * cheapest it has seen is pair one by construction. What it drops is the middle.
     */
    private fun twoPlayerMoves(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
        peekFirst: Boolean,
    ): List<MctsMove> {
        val rank = state.pendingCard?.rank
        val unread = averageRemainingCardValue(state.botMemory)
        fun priceOf(player: MctsPlayerState, position: Int) =
            price(state, currentPlayer, player, position, unread)

        // Dearest first on the mover's side, cheapest first on the other: the two ends of
        // the same ordering, which is what a trade is.
        val ownPositions = (0 until currentPlayer.cardCount)
            .sortedByDescending { priceOf(currentPlayer, it) }
            .take(SHORTLIST)

        val aims = mutableListOf<Pair<MctsMove, Double>>()
        for (opponent in targetableOpponents(state, currentPlayer)) {
            val theirPositions = (0 until opponent.cardCount)
                .sortedBy { priceOf(opponent, it) }
                .take(SHORTLIST)

            for (theirs in theirPositions) {
                for (own in ownPositions) {
                    val targets = listOf(
                        MctsActionTarget(currentPlayer.id, own),
                        MctsActionTarget(opponent.id, theirs),
                    )
                    val gain = priceOf(currentPlayer, own) - priceOf(opponent, theirs)
                    aims += aimed(currentPlayer, rank, targets, shouldSwap = true) to
                        if (peekFirst) maxOf(0.0, gain) else gain
                }
            }
        }

        // The Jack's "no", priced at what it does: nothing. It outranks every trade that
        // loses and is outranked by every trade that gains, which is the whole of the rule.
        if (!peekFirst) {
            aims.firstOrNull()?.let { (best, _) -> aims += best.copy(shouldSwap = false) to 0.0 }
        }

        // Stable, so equal trades keep the order the sides put them in and one position
        // always produces one list.
        return aims.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * What [mover] can price [player]'s slot at — a card it can actually see, or the average
     * of what is left in the deck, which is the number an unread card is given in
     * [StateTransition.handTotal] and in the rollout alike.
     *
     * **Who is looking decides what can be seen, and getting that wrong costs the bot the
     * board.** A move is generated for whoever is to move in the sampled world, and
     * `MctsPlayerState.knownCards` means one thing only: what the *searching* bot knows. Read
     * as though it were the mover's own knowledge it hands every rival a window onto the bot's
     * row — so in the search the whole table could see the bot's cards, and every Jack and
     * Queen dealt to an opponent came straight for the best of them. A Joker the bot had just
     * won was gone again within a turn in 29% of rollouts, and the branch that won it priced
     * out below drawing a card. That is why *more* search made it worse: the deeper the tree
     * went, the more clairvoyant opponents it played against.
     *
     * So each seat sees what the rest of the model already grants it — `MutableMctsState.knows`
     * and the rollout's `seenPositions` draw this same line. The bot
     * sees by memory, of anyone. Everyone else sees the cards the table has watched them look
     * at, in their own hand, and nothing whatever of anybody else's.
     */
    private fun price(
        state: MctsGameState,
        mover: MctsPlayerState,
        player: MctsPlayerState,
        position: Int,
        unread: Double,
    ): Double {
        val seen = when {
            mover.id == state.botPlayerId -> knownCards(player)[position]
            mover.id != player.id -> null
            position in player.ownerKnows ->
                state.hiddenCards[state.hiddenCardKey(player.id, position)]
            else -> null
        }
        return seen?.value?.toDouble() ?: unread
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
}
