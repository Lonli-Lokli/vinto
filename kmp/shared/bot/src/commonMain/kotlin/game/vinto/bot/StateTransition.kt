package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Rank

/**
 * How the search moves a position forward, ported from
 * `packages/bot/src/lib/mcts-state-transition.ts`.
 *
 * **This is a forward model, not the game.** The engine decides what actually happens; this
 * only has to be plausible enough that the score at the end of a rollout means something.
 * Legality of what the bot finally *proposes* is settled elsewhere — [MoveGenerator] offers
 * only legal moves and `ActionValidator` rejects anything that slipped through — so a
 * simplification here costs playing strength, never a rule violation.
 *
 * Three simplifications are inherited from the TypeScript deliberately, and are worth knowing
 * before reading a search result too literally:
 *
 * - **A draw is just a turn.** [applyDraw] shrinks the deck and passes play on without
 *   dealing a card, because dealing one would mean sampling inside the transition. The
 *   sampling the bot does do is [determinize], once per simulation.
 * - **Only known cards are tossed in.** A player sheds a matching card when the searching bot
 *   believes they hold one, so the model never invents a wrong toss-in or its penalty.
 * - **Vinto ends the game on lowest hand.** Round scoring — the caller against the coalition,
 *   +3/-1 — belongs to the coalition planner, not to a rollout.
 */
object StateTransition {

    /** A memory below this is a hunch; the model only acts on what the bot is sure of. */

    /** Stands in for a card nobody has seen — roughly a mid-deck value. */
    private const val UNKNOWN_CARD_ESTIMATE = 6.0

    /** A player who is not in the state at all: assume the worst rather than zero. */
    private const val MISSING_PLAYER_SCORE = 50.0

    /** What an Ace is assumed to cost its victim, averaged over the deck. */
    private const val ACE_PENALTY_ESTIMATE = 5.0

    /** A rollout this long has stopped telling the search anything; cut it off. */
    private const val MAX_SEARCH_TURNS = 200

    fun applyMove(state: MctsGameState, move: MctsMove): MctsGameState {
        val working = MutableMctsState(state)

        when (move.type) {
            MctsMoveType.DRAW -> applyDraw(working)
            MctsMoveType.TAKE_DISCARD -> applyTakeDiscard(working)
            MctsMoveType.USE_ACTION -> applyUseAction(working, move)
            MctsMoveType.SWAP -> applySwap(working, move)
            MctsMoveType.DISCARD -> applyDiscard(working)
            MctsMoveType.TOSS_IN -> applyTossIn(working, move)
            MctsMoveType.CALL_VINTO -> applyCallVinto(working)
            MctsMoveType.PASS -> applyPass(working)
        }

        return working.freeze()
    }

    /**
     * The game is over, or far enough gone that searching on is wasted effort.
     *
     * The last two are not rules — an empty deck reshuffles and a long game is still a game —
     * they are the guards that stop a rollout running forever.
     */
    fun isTerminal(state: MctsGameState): Boolean =
        state.isTerminal ||
            state.players.any { it.cardCount == 0 } ||
            state.turnCount > MAX_SEARCH_TURNS ||
            state.deckSize <= 0

    data class TerminalOutcome(val winner: String, val scores: Map<String, Double>)

    /** Lowest hand wins, which is the objective of a round. */
    fun evaluateTerminal(state: MctsGameState): TerminalOutcome {
        val scores = state.players.associate { it.id to it.score }
        return TerminalOutcome(winner = lowestScoringPlayerId(state), scores = scores)
    }

    /** What a hand is really worth given the cards determinization dealt it. */
    fun calculatePlayerScore(state: MctsGameState, playerId: String): Double {
        val player = state.players.firstOrNull { it.id == playerId } ?: return MISSING_PLAYER_SCORE

        return (0 until player.cardCount).sumOf { position ->
            state.hiddenCards[state.hiddenCardKey(playerId, position)]?.value?.toDouble()
                ?: UNKNOWN_CARD_ESTIMATE
        }
    }

    /** Re-derives every estimate from the dealt cards, after determinization has run. */
    fun updateScoreEstimates(state: MctsGameState): MctsGameState =
        state.copy(
            players = state.players.map { it.copy(score = calculatePlayerScore(state, it.id)) },
        )

    fun advanceToNextPlayer(state: MctsGameState): MctsGameState =
        state.copy(
            currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size,
            turnCount = state.turnCount + 1,
        )

    /** Used to look one ply ahead without paying for a full transition. */
    fun wouldMoveEndGame(state: MctsGameState, move: MctsMove): Boolean = when (move.type) {
        MctsMoveType.CALL_VINTO -> true
        MctsMoveType.TOSS_IN ->
            state.players.firstOrNull { it.id == move.playerId }?.cardCount == 1
        else -> false
    }

    private fun lowestScoringPlayerId(state: MctsGameState): String =
        state.players.minByOrNull { it.score }?.id ?: ""

    // --- moves ---------------------------------------------------------------------------

    /**
     * Drawing costs a card from the deck and the turn, and nothing else.
     *
     * No card is dealt: see the class comment. The deck count still matters because
     * [isTerminal] ends a rollout when it runs out.
     */
    private fun applyDraw(state: MutableMctsState) {
        if (state.deckSize > 0) state.deckSize--
        state.advanceTurn()
    }

    /** The taken card is played immediately by rule, so the pile top is simply gone. */
    private fun applyTakeDiscard(state: MutableMctsState) {
        state.discardPileTop = null
        state.advanceTurn()
    }

    private fun applyUseAction(state: MutableMctsState, move: MctsMove) {
        val actionCard = state.pendingCard ?: return
        if (state.currentPlayer() == null) return

        applyActionEffect(state, move, actionCard)

        state.discardPileTop = actionCard.copy(played = true)
        state.pendingCard = null

        finishTurnWithTossIn(state, actionCard.rank)
    }

    /**
     * Swapping the drawn card into hand, and discarding what it displaced.
     *
     * The discarded card opens a toss-in window on its rank, which is where most of a hand
     * actually disappears — modelling the swap without the cascade would systematically
     * undervalue it.
     */
    private fun applySwap(state: MutableMctsState, move: MctsMove) {
        val position = move.swapPosition ?: return
        val player = state.currentPlayer() ?: return
        val drawnCard = state.pendingCard ?: return
        val displaced = state.hiddenCards[state.key(player.id, position)] ?: return

        state.hiddenCards[state.key(player.id, position)] = drawnCard
        player.knownCards[position] = certainMemory(drawnCard)

        state.discardPileTop = displaced.copy(played = false)
        state.pendingCard = null

        finishTurnWithTossIn(state, displaced.rank)
    }

    private fun applyDiscard(state: MutableMctsState) {
        val discarded = state.pendingCard ?: return
        if (state.currentPlayer() == null) return

        state.discardPileTop = discarded.copy(played = false)
        state.pendingCard = null

        finishTurnWithTossIn(state, discarded.rank)
    }

    /**
     * A player throwing in matching cards of their own.
     *
     * The turn does *not* advance: a toss-in window stays open for further toss-ins until
     * someone passes, which is what [applyPass] is for.
     */
    private fun applyTossIn(state: MutableMctsState, move: MctsMove) {
        if (move.tossInPositions.isEmpty()) return
        val player = state.findPlayer(move.playerId) ?: return

        removeCardsAt(state, player, move.tossInPositions)
    }

    private fun applyPass(state: MutableMctsState) {
        state.isTossInPhase = false
        state.advanceTurn()
    }

    /**
     * Calling Vinto.
     *
     * The rules give the caller one more round and then score the caller against the
     * coalition; the search stops here and awards the round to the lowest hand. That is a
     * cruder rule than the game's, and it is the reason the coalition planner exists.
     */
    private fun applyCallVinto(state: MutableMctsState) {
        state.isTerminal = true
        state.finalTurnTriggered = true
        state.winner = state.players.minByOrNull { it.score }?.id ?: ""
    }

    /** Every discard opens a toss-in window; resolve it, then pass play on. */
    private fun finishTurnWithTossIn(state: MutableMctsState, discardedRank: Rank) {
        simulateTossInCascade(state, discardedRank)
        state.advanceTurn()
        state.isTossInPhase = false
        state.tossInRanks = emptyList()
    }

    // --- toss-in -------------------------------------------------------------------------

    /**
     * Everyone who knows they hold the discarded rank sheds it.
     *
     * This is one pass, not a recursion: a King tossed into the window would declare a rank
     * of its own and open a second window, and the TypeScript never modelled that either.
     * Worth revisiting when the bot is tuned — it undervalues Kings — but it undervalues them
     * equally in every branch, so it does not bias the comparison the search is making.
     */
    private fun simulateTossInCascade(state: MutableMctsState, discardedRank: Rank): List<Card> {
        val tossed = mutableListOf<Card>()

        for (player in state.players) {
            val matching = (0 until player.cardCount).filter { position ->
                val memory = player.knownCards[position]
                memory != null &&
                    memory.confidence > TRUSTED_CONFIDENCE &&
                    memory.card.rank == discardedRank
            }
            if (matching.isEmpty()) continue

            matching.forEach { tossed += player.knownCards.getValue(it).card }
            removeCardsAt(state, player, matching)
        }

        return tossed
    }

    /**
     * Takes cards out of a hand and closes the gaps.
     *
     * Positions are an index into the hand, so removing one renumbers everything after it —
     * in the dealt cards and in the memories alike. Renumbering only one of the two would
     * silently attach a memory to a different card, which is the kind of bug that shows up
     * as the bot "misremembering" much later.
     */
    private fun removeCardsAt(
        state: MutableMctsState,
        player: MutableMctsPlayer,
        positions: List<Int>,
    ) {
        val removed = positions.toSet()
        val originalCount = player.cardCount

        player.score -= removed.sumOf { position ->
            state.hiddenCards[state.key(player.id, position)]?.value?.toDouble() ?: 0.0
        }
        player.cardCount = originalCount - removed.size

        val survivingCards = mutableMapOf<Int, Card>()
        val survivingMemories = mutableMapOf<Int, CardMemory>()
        var newPosition = 0

        for (oldPosition in 0 until originalCount) {
            if (oldPosition in removed) continue
            state.hiddenCards[state.key(player.id, oldPosition)]?.let {
                survivingCards[newPosition] = it
            }
            player.knownCards[oldPosition]?.let { survivingMemories[newPosition] = it }
            newPosition++
        }

        for (position in 0 until originalCount) {
            state.hiddenCards.remove(state.key(player.id, position))
        }
        survivingCards.forEach { (position, card) ->
            state.hiddenCards[state.key(player.id, position)] = card
        }
        player.knownCards = survivingMemories
    }

    // --- action effects ------------------------------------------------------------------

    /**
     * What each action card does to the model.
     *
     * `knownCards` here means *what the searching bot knows about that player's card*, which
     * is why peeking an opponent writes to the opponent's map rather than the bot's.
     */
    private fun applyActionEffect(state: MutableMctsState, move: MctsMove, actionCard: Card) {
        if (move.targets.isEmpty()) return
        if (state.currentPlayer() == null) return

        when (actionCard.rank) {
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN -> applyPeek(state, move.targets.first())
            Rank.JACK -> applySwapAction(state, move.targets)
            Rank.QUEEN -> applyPeekAndSwap(state, move.targets, move.shouldSwap == true)
            Rank.ACE -> applyForcedDraw(state, move.targets.first())
            // A King declares a rank and plays that rank's action, which the model does not
            // carry out — see [simulateTossInCascade]. All it records is the card it looked
            // at to choose the declaration.
            Rank.KING -> if (move.declaredRank != null) applyPeek(state, move.targets.first())
            else -> Unit
        }
    }

    /** 7/8 look at one of your own cards, 9/10 at somebody else's. Both are the same write. */
    private fun applyPeek(state: MutableMctsState, target: MctsActionTarget) {
        val card = state.hiddenCards[state.key(target.playerId, target.position)] ?: return
        state.findPlayer(target.playerId)?.knownCards?.set(target.position, certainMemory(card))
    }

    /** Jack: swap two cards outright. Both hands change value, so both estimates move. */
    private fun applySwapAction(state: MutableMctsState, targets: List<MctsActionTarget>) {
        if (targets.size < 2) return
        val (first, second) = targets
        val firstCard = state.hiddenCards[state.key(first.playerId, first.position)] ?: return
        val secondCard = state.hiddenCards[state.key(second.playerId, second.position)] ?: return

        exchange(state, first, firstCard, second, secondCard)

        state.findPlayer(first.playerId)?.knownCards?.set(first.position, certainMemory(secondCard))
        state.findPlayer(second.playerId)?.knownCards?.set(second.position, certainMemory(firstCard))
    }

    /** Queen: see both, then decide. The knowledge is kept whether or not the swap happens. */
    private fun applyPeekAndSwap(
        state: MutableMctsState,
        targets: List<MctsActionTarget>,
        shouldSwap: Boolean,
    ) {
        if (targets.size < 2) return
        val (first, second) = targets
        val firstCard = state.hiddenCards[state.key(first.playerId, first.position)]
        val secondCard = state.hiddenCards[state.key(second.playerId, second.position)]

        firstCard?.let {
            state.findPlayer(first.playerId)?.knownCards?.set(first.position, certainMemory(it))
        }
        secondCard?.let {
            state.findPlayer(second.playerId)?.knownCards?.set(second.position, certainMemory(it))
        }

        if (!shouldSwap || firstCard == null || secondCard == null) return
        exchange(state, first, firstCard, second, secondCard)
    }

    private fun exchange(
        state: MutableMctsState,
        first: MctsActionTarget,
        firstCard: Card,
        second: MctsActionTarget,
        secondCard: Card,
    ) {
        state.hiddenCards[state.key(first.playerId, first.position)] = secondCard
        state.hiddenCards[state.key(second.playerId, second.position)] = firstCard

        state.findPlayer(first.playerId)?.let {
            it.score = it.score - firstCard.value + secondCard.value
        }
        state.findPlayer(second.playerId)?.let {
            it.score = it.score - secondCard.value + firstCard.value
        }
    }

    /** Ace: the victim gains a card the bot cannot see, so it gains an estimate. */
    private fun applyForcedDraw(state: MutableMctsState, target: MctsActionTarget) {
        val victim = state.findPlayer(target.playerId) ?: return
        victim.cardCount++
        victim.score += ACE_PENALTY_ESTIMATE
    }

    private fun certainMemory(card: Card) =
        CardMemory(card = card, confidence = 1.0, lastSeen = 0L, observations = 1)
}

/**
 * A mutable working copy of a position, for the duration of one transition.
 *
 * [MctsGameState] is immutable and the handlers below are a port of code that mutated freely;
 * threading `copy()` through them would obscure the correspondence for no gain. The mutation
 * never escapes [StateTransition.applyMove] — the working copy is frozen back on the way out
 * — so the search still sees only immutable states.
 *
 * `botMemory` and `opponentModeler` are carried by reference on purpose: they are the bot's
 * own long-lived knowledge, shared across every node of the tree rather than owned by a
 * position.
 */
private class MutableMctsPlayer(source: MctsPlayerState) {
    val id: String = source.id
    var cardCount: Int = source.cardCount
    var knownCards: MutableMap<Int, CardMemory> = source.knownCards.toMutableMap()
    var score: Double = source.score

    fun freeze() = MctsPlayerState(
        id = id,
        cardCount = cardCount,
        knownCards = knownCards.toMap(),
        score = score,
    )
}

private class MutableMctsState(private val source: MctsGameState) {
    val players: List<MutableMctsPlayer> = source.players.map { MutableMctsPlayer(it) }
    val hiddenCards: MutableMap<String, Card> = source.hiddenCards.toMutableMap()

    var discardPileTop: Card? = source.discardPileTop
    var pendingCard: Card? = source.pendingCard
    var deckSize: Int = source.deckSize
    var currentPlayerIndex: Int = source.currentPlayerIndex
    var turnCount: Int = source.turnCount
    var isTossInPhase: Boolean = source.isTossInPhase
    var tossInRanks: List<Rank> = source.tossInRanks
    var isTerminal: Boolean = source.isTerminal
    var finalTurnTriggered: Boolean = source.finalTurnTriggered
    var winner: String? = source.winner

    fun key(playerId: String, position: Int) = source.hiddenCardKey(playerId, position)

    fun currentPlayer(): MutableMctsPlayer? = players.getOrNull(currentPlayerIndex)

    fun findPlayer(playerId: String): MutableMctsPlayer? = players.firstOrNull { it.id == playerId }

    fun advanceTurn() {
        if (players.isNotEmpty()) currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        turnCount++
    }

    fun freeze(): MctsGameState = source.copy(
        players = players.map { it.freeze() },
        hiddenCards = hiddenCards.toMap(),
        discardPileTop = discardPileTop,
        pendingCard = pendingCard,
        deckSize = deckSize,
        currentPlayerIndex = currentPlayerIndex,
        turnCount = turnCount,
        isTossInPhase = isTossInPhase,
        tossInRanks = tossInRanks,
        isTerminal = isTerminal,
        finalTurnTriggered = finalTurnTriggered,
        winner = winner,
    )
}
