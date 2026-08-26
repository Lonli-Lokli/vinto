package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.CardAction
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardAction
import game.vinto.shapes.isActionable

/**
 * Every move the search may consider from a position.
 *
 * Ported from `packages/bot/src/lib/mcts-move-generator.ts`. Two kinds of thing live in
 * here and they are not equally negotiable:
 *
 *  - **Rules**, which must be exact. Only toss-in or pass during a toss-in; the discard pile
 *    is only takeable when its top card is an unused action; Jack and Queen need two cards
 *    from two *different* players; and the coalition may not touch the Vinto caller's cards
 *    in the final round. A generator that proposes an illegal move produces a bot that
 *    cheats, or one the engine rejects mid-game.
 *  - **Priorities**, which are judgement. Which swaps are worth searching, which King
 *    declaration to try first. These are ported in spirit and in ordering rather than
 *    line-for-line; the search explores what it is given, so a different shortlist makes a
 *    different bot, not a broken one.
 */
object MoveGenerator {


    /** Worth removing from a hand, or worth forcing an opponent to keep. */
    private const val HIGH_VALUE_CARD = 9

    /** Vinto is not considered before everyone has had two turns. */
    private const val OPENING_TURNS_PER_PLAYER = 2

    fun generateMoves(state: MctsGameState): List<MctsMove> {
        val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return emptyList()

        // A toss-in window is not a turn: the only choices are to throw matching cards in or
        // to sit it out.
        if (state.isTossInPhase) return tossInMoves(state, currentPlayer)

        // Holding an action card mid-play means the only decision left is where to aim it.
        val pending = state.pendingCard
        if (pending != null && pending.rank.isActionable()) {
            getCardAction(pending.rank)?.let { return generateActionMoves(state, it) }
        }

        val moves = mutableListOf<MctsMove>()

        if (state.deckSize > 0) {
            moves += MctsMove(MctsMoveType.DRAW, currentPlayer.id)
        }

        // Taking from the discard commits you to playing the action, so it is only offered
        // when there is an unused one to play.
        val discardTop = state.discardPileTop
        if (discardTop != null && !discardTop.actionText.isNullOrEmpty() && !discardTop.played) {
            moves += MctsMove(MctsMoveType.TAKE_DISCARD, currentPlayer.id, actionCard = discardTop)
        }

        if (state.turnCount >= state.players.size * OPENING_TURNS_PER_PLAYER &&
            assessVintoThreat(state)
        ) {
            moves += MctsMove(MctsMoveType.CALL_VINTO, currentPlayer.id)
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

        val validRanks = state.tossInRanks.ifEmpty {
            listOfNotNull(state.discardPileTop?.rank)
        }
        if (validRanks.isEmpty()) return moves

        val matching = (0 until currentPlayer.cardCount).filter { position ->
            val card = currentPlayer.knownCards[position]?.card
                ?: state.hiddenCards[state.hiddenCardKey(currentPlayer.id, position)]
            card != null && card.rank in validRanks
        }

        if (matching.isNotEmpty()) {
            moves += MctsMove(MctsMoveType.TOSS_IN, currentPlayer.id, tossInPositions = matching)
        }
        return moves
    }

    fun generateActionMoves(state: MctsGameState, actionType: CardAction): List<MctsMove> {
        val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return emptyList()

        return when (actionType) {
            CardAction.PEEK_OWN -> unknownPositions(currentPlayer).map { position ->
                MctsMove(
                    MctsMoveType.USE_ACTION,
                    currentPlayer.id,
                    targets = listOf(MctsActionTarget(currentPlayer.id, position)),
                )
            }

            CardAction.PEEK_OPPONENT -> targetableOpponents(state, currentPlayer).flatMap { opponent ->
                unknownPositions(opponent).map { position ->
                    MctsMove(
                        MctsMoveType.USE_ACTION,
                        currentPlayer.id,
                        targets = listOf(MctsActionTarget(opponent.id, position)),
                    )
                }
            }

            CardAction.SWAP_CARDS -> generateTwoPlayerMoves(state, currentPlayer, peekFirst = false)
            CardAction.PEEK_AND_SWAP -> generateTwoPlayerMoves(state, currentPlayer, peekFirst = true)
            CardAction.FORCE_DRAW -> generateForceDrawMoves(state, currentPlayer)
            CardAction.DECLARE_ACTION -> generateKingMoves(state, currentPlayer)
        }
    }

    /** After drawing, the card either goes into a position or straight to the discard. */
    fun generateSwapPositionMoves(state: MctsGameState): List<MctsMove> {
        val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return emptyList()

        return listOf(MctsMove(MctsMoveType.DISCARD, currentPlayer.id)) +
            (0 until currentPlayer.cardCount).map { position ->
                MctsMove(MctsMoveType.SWAP, currentPlayer.id, swapPosition = position)
            }
    }

    /**
     * Jack and Queen both take two cards from two *different* players — a rule, not a
     * preference. The shortlist pairs the bot's worst known card against opponents' best,
     * which is the trade worth searching; Queen additionally prefers cards it has not seen,
     * since it peeks before deciding.
     */
    private fun generateTwoPlayerMoves(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
        peekFirst: Boolean,
    ): List<MctsMove> {
        val ownPositions = if (peekFirst) {
            unknownPositions(currentPlayer).ifEmpty { knownPositionsByValue(currentPlayer) }
        } else {
            knownPositionsByValue(currentPlayer).reversed()
        }.take(SHORTLIST)

        val moves = mutableListOf<MctsMove>()
        for (opponent in targetableOpponents(state, currentPlayer)) {
            val opponentPositions = if (peekFirst) {
                unknownPositions(opponent).ifEmpty { (0 until opponent.cardCount).toList() }
            } else {
                knownPositionsByValue(opponent).reversed()
                    .ifEmpty { (0 until opponent.cardCount).toList() }
            }.take(SHORTLIST)

            for (own in ownPositions) {
                for (theirs in opponentPositions) {
                    moves += MctsMove(
                        MctsMoveType.USE_ACTION,
                        currentPlayer.id,
                        targets = listOf(
                            MctsActionTarget(currentPlayer.id, own),
                            MctsActionTarget(opponent.id, theirs),
                        ),
                        shouldSwap = if (peekFirst) true else null,
                    )
                }
            }
        }
        return moves
    }

    private const val SHORTLIST = 2

    /**
     * An Ace makes someone draw. The coalition must not aim it at the Vinto caller (the rule
     * forbidding interaction with their cards) and should not aim it at its own champion
     * either — handing a card to the one member who can still win is friendly fire.
     */
    private fun generateForceDrawMoves(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
    ): List<MctsMove> {
        val championId = coalitionChampion(state)?.id
        val inCoalition = isCoalitionMember(state, currentPlayer.id)

        return targetableOpponents(state, currentPlayer)
            .filterNot { inCoalition && it.id == championId }
            .map { opponent ->
                // An Ace names a player, not a card; position 0 is a placeholder.
                MctsMove(
                    MctsMoveType.USE_ACTION,
                    currentPlayer.id,
                    targets = listOf(MctsActionTarget(opponent.id, 0)),
                )
            }
    }

    /**
     * A King declares a rank, and every matching card at the table gets tossed in.
     *
     * The priority order is the whole strategy: a rank the bot holds two of sheds two of its
     * own cards at once; then its own expensive single cards; then an opponent's expensive
     * cards, which costs them. In coalition, the caller's cards come first — that is the one
     * time harming a specific player is the goal.
     */
    private fun generateKingMoves(
        state: MctsGameState,
        currentPlayer: MctsPlayerState,
    ): List<MctsMove> {
        val ownKnown = knownCards(currentPlayer)
        val ranks = mutableListOf<Rank>()

        // 1. Own cascades — two or more of a rank leave together.
        ownKnown.values.groupingBy { it.rank }.eachCount()
            .filter { it.value >= 2 }
            .keys
            .let(ranks::addAll)

        // 2. Own expensive singles.
        ownKnown.values.filter { it.value >= HIGH_VALUE_CARD }.map { it.rank }.let(ranks::addAll)

        // 3. Opponents' expensive cards. In coalition the caller is the target of choice,
        //    and that is also the only case where the caller may be named at all.
        val inCoalition = isCoalitionMember(state, currentPlayer.id)
        val opponents = if (inCoalition) {
            state.players.filter { it.id == state.vintoCallerId }
        } else {
            state.players.filter { it.id != currentPlayer.id }
        }
        opponents.flatMap { knownCards(it).values }
            .filter { it.value >= HIGH_VALUE_CARD }
            .map { it.rank }
            .let(ranks::addAll)

        return ranks.distinct().map { rank ->
            MctsMove(MctsMoveType.USE_ACTION, currentPlayer.id, declaredRank = rank)
        }
    }

    /** Rough ordering for progressive widening: decisive moves before filler. */
    @Suppress("MagicNumber")
    fun getMovePriority(move: MctsMove): Int = when (move.type) {
        MctsMoveType.CALL_VINTO -> 100
        MctsMoveType.USE_ACTION -> 80
        MctsMoveType.TAKE_DISCARD -> 70
        MctsMoveType.TOSS_IN -> 60
        MctsMoveType.DRAW -> 50
        MctsMoveType.SWAP -> 40
        MctsMoveType.DISCARD -> 30
        MctsMoveType.PASS -> 10
    }

    /**
     * A second check that a move is playable, used where moves are carried between states.
     * Generation already respects these; this catches a stale move applied to a state that
     * has moved on.
     */
    fun isLegalMove(state: MctsGameState, move: MctsMove): Boolean {
        val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return false
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

    /**
     * Whether calling Vinto is even worth putting in front of the search.
     *
     * Being ahead is not enough: an opponent holding a Jack, Queen or King can rearrange the
     * table before the round ends, so known action cards raise the margin required. Three or
     * more and the bot wants a clear lead over the *best* opponent, not just the average.
     */
    @Suppress("MagicNumber")
    private fun assessVintoThreat(state: MctsGameState): Boolean {
        val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return false
        val opponents = state.players.filter { it.id != currentPlayer.id }
        if (opponents.isEmpty()) return false

        val averageOpponentScore = opponents.sumOf { it.score } / opponents.size
        val minOpponentScore = opponents.minOf { it.score }
        val baseThreshold = 5

        if (currentPlayer.score > averageOpponentScore - baseThreshold) return false

        val dangerous = setOf(
            CardAction.SWAP_CARDS,
            CardAction.PEEK_AND_SWAP,
            CardAction.DECLARE_ACTION,
        )
        var threatLevel = 0
        for (opponent in opponents) {
            for (memory in opponent.knownCards.values) {
                if (memory.confidence <= TRUSTED_CONFIDENCE) continue
                val action = getCardAction(memory.card.rank) ?: continue
                if (action !in dangerous) continue

                threatLevel++
                if (action == CardAction.DECLARE_ACTION) threatLevel += 2
                if (action == CardAction.PEEK_AND_SWAP) threatLevel += 1
            }
        }

        return when {
            threatLevel == 0 -> currentPlayer.score <= averageOpponentScore - baseThreshold
            threatLevel <= 2 -> currentPlayer.score <= averageOpponentScore - (baseThreshold + 3)
            else -> currentPlayer.score <= averageOpponentScore - (baseThreshold + 5) &&
                currentPlayer.score < minOpponentScore - 3
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

    private fun isCoalitionMember(state: MctsGameState, playerId: String): Boolean =
        state.vintoCallerId != null &&
            state.coalitionLeaderId != null &&
            playerId != state.vintoCallerId

    fun coalitionChampion(state: MctsGameState): MctsPlayerState? {
        if (state.vintoCallerId == null || state.coalitionLeaderId == null) return null
        return state.players.filter { it.id != state.vintoCallerId }.minByOrNull { it.score }
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
    private fun knownCards(player: MctsPlayerState): Map<Int, Card> =
        player.knownCards
            .filterKeys { it in 0 until player.cardCount }
            .filterValues { it.confidence > TRUSTED_CONFIDENCE }
            .mapValues { it.value.card }

    private fun unknownPositions(player: MctsPlayerState): List<Int> =
        (0 until player.cardCount).filter { position ->
            val memory = player.knownCards[position]
            memory == null || memory.confidence <= TRUSTED_CONFIDENCE
        }

    /** Known positions, cheapest first. */
    private fun knownPositionsByValue(player: MctsPlayerState): List<Int> =
        knownCards(player).entries.sortedBy { it.value.value }.map { it.key }
}
