package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import game.vinto.shapes.isActionable

/**
 * How the search moves a sampled world forward.
 *
 * **This is a forward model, not the game.** The engine decides what actually happens; this
 * only has to be faithful enough that the outcome of a rollout means something. Legality of
 * what the bot finally *proposes* is settled elsewhere — [MoveGenerator] offers only legal
 * moves and `ActionValidator` rejects anything that slipped through — so a simplification
 * here costs playing strength, never a rule violation.
 *
 * It works on a determinized state: every hidden card has a value and the deck has an order,
 * so a draw deals a real card, a swap moves real points, and a toss-in window sheds the cards
 * that actually match. What it simplifies, deliberately:
 *
 * - **Opponents know their own hands.** They toss in whatever matches and declare whatever
 *   they swap out. The bot itself acts only on what it remembers, which is what gives a peek
 *   its value in the search: a card the bot has not read cannot be tossed, declared, or
 *   counted towards a Vinto call.
 * - **A tossed-in action card is not played.** The window sheds it and stops there.
 * - **Nobody can be wrong.** A King names only a card its declarer knows, so a declaration
 *   is right by construction; a toss-in is only ever of a matching card.
 */
object StateTransition {

    /** A rollout this long has stopped telling the search anything; cut it off. */
    private const val MAX_SEARCH_TURNS = 200

    /** Stands in for a card the world never dealt — a deck run dry beyond what memory can pad. */
    private val FALLBACK_RANK = Rank.SIX

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
     * The round is scored, or the search has run far enough. The last two are guards rather
     * than rules: a deck that cannot be refilled leaves no legal draw, and the real engine
     * ends such a round through `END_ROUND`.
     */
    fun isTerminal(state: MctsGameState): Boolean =
        state.isTerminal || state.turnCount > MAX_SEARCH_TURNS || isStarved(state)

    /** No deck, nothing to fold back into it, and no card in play: nobody can draw. */
    private fun isStarved(state: MctsGameState): Boolean =
        state.deckSize <= 0 && state.discardCount <= 1 && state.pendingCard == null

    /**
     * What a hand is worth in this world. Before determinization a position the bot has not
     * read is priced at the average of what is still unaccounted for, which is the only
     * honest number for it.
     */
    fun handTotal(state: MctsGameState, playerId: String): Int {
        val player = state.players.firstOrNull { it.id == playerId } ?: return 0
        var total = 0
        var unread = 0
        for (position in 0 until player.cardCount) {
            val dealt = state.hiddenCards[state.hiddenCardKey(playerId, position)]
            val remembered = player.knownCards[position]?.takeIf { it.confidence > TRUSTED_CONFIDENCE }?.card
            when {
                dealt != null -> total += dealt.value
                remembered != null -> total += remembered.value
                else -> unread++
            }
        }
        if (unread > 0) total += (unread * averageRemainingCardValue(state.botMemory)).toInt()
        return total
    }

    // --- moves ---------------------------------------------------------------------------

    /** Drawing deals the top of the sampled deck, face up to the table, and waits for a reply. */
    private fun applyDraw(state: MutableMctsState) {
        state.pendingCard = state.takeFromDeck("drawn")
        state.pendingOrigin = PendingOrigin.DRAWN
    }

    /** Taking the pile's top card commits its taker to playing it. */
    private fun applyTakeDiscard(state: MutableMctsState) {
        val top = state.discardPileTop
        if (top == null) {
            state.advanceTurn()
            return
        }
        state.pendingCard = top.copy(played = false)
        state.pendingOrigin = PendingOrigin.COMMITTED
        state.discardPileTop = null
        state.discarded.removeLastOrNull()
        if (state.discardCount > 0) state.discardCount--
    }

    private fun applyUseAction(state: MutableMctsState, move: MctsMove) {
        val card = state.pendingCard ?: return
        state.pendingCard = null
        state.pendingOrigin = null

        applyActionEffect(state, move, card)

        state.putOnPile(card.copy(played = true))
        state.finishPlay(card.rank)
    }

    /**
     * Swapping the drawn card into hand and discarding what it displaced.
     *
     * A displaced action card its owner knows is declared on the way out, so its action is the
     * owner's to play before the turn ends — the same rule the runner plays by.
     */
    private fun applySwap(state: MutableMctsState, move: MctsMove) {
        val position = move.swapPosition ?: return
        val player = state.currentPlayer() ?: return
        val drawn = state.pendingCard ?: return
        val key = state.key(player.id, position)

        val displaced = state.hiddenCards[key] ?: state.fallbackCard("$key-unknown")
        val knewIt = state.knows(player, position)

        state.hiddenCards[key] = drawn
        player.knownCards[position] = certainMemory(drawn)
        state.pendingCard = null
        state.pendingOrigin = null

        if (knewIt && displaced.rank.isActionable()) {
            state.pendingCard = displaced.copy(played = false)
            state.pendingOrigin = PendingOrigin.BORROWED
        } else {
            state.putOnPile(displaced.copy(played = false))
        }
        state.finishPlay(displaced.rank)
    }

    /** Putting the card in play down. A taken or borrowed card put down unaimed is spent. */
    private fun applyDiscard(state: MutableMctsState) {
        val card = state.pendingCard ?: return
        state.pendingCard = null
        state.putOnPile(card.copy(played = state.pendingOrigin != PendingOrigin.DRAWN))
        state.pendingOrigin = null
        state.finishPlay(card.rank)
    }

    /** A player throwing matching cards of their own into the open window. */
    private fun applyTossIn(state: MutableMctsState, move: MctsMove) {
        if (move.tossInPositions.isEmpty()) return
        val player = state.findPlayer(move.playerId) ?: return
        state.shed(player, move.tossInPositions)
    }

    private fun applyPass(state: MutableMctsState) {
        state.awaitingVintoDecision = false
        state.isTossInPhase = false
        state.tossInRanks = emptyList()
        state.advanceTurn()
    }

    /** Calling Vinto: the final round starts, and everybody else gets one more turn. */
    private fun applyCallVinto(state: MutableMctsState) {
        val caller = state.currentPlayer() ?: return
        state.vintoCallerId = caller.id
        state.finalTurnTriggered = true
        state.awaitingVintoDecision = false
        state.isTossInPhase = false
        state.tossInRanks = emptyList()
        state.advanceTurn()
    }

    // --- action effects ------------------------------------------------------------------

    private fun applyActionEffect(state: MutableMctsState, move: MctsMove, actionCard: Card) {
        if (move.targets.isEmpty()) return
        val mover = state.currentPlayer() ?: return

        when (actionCard.rank) {
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN -> state.peek(mover, move.targets.first())
            Rank.JACK -> if (move.shouldSwap != false) state.exchange(move.targets)
            Rank.QUEEN -> applyPeekAndSwap(state, mover, move.targets)
            Rank.ACE -> applyForcedDraw(state, move.targets.first())
            Rank.KING -> applyKing(state, mover, move)
            else -> Unit
        }
    }

    /**
     * Queen: see both, then trade exactly when it sheds the mover's points. The move's own
     * `shouldSwap` is an intention formed before the peek; what the runner actually does is
     * decided on the cards seen, and the model follows the same rule.
     */
    private fun applyPeekAndSwap(
        state: MutableMctsState,
        mover: MutableMctsPlayer,
        targets: List<MctsActionTarget>,
    ) {
        if (targets.size < 2) return
        targets.forEach { state.peek(mover, it) }

        val own = targets.firstOrNull { it.playerId == mover.id } ?: return
        val theirs = targets.first { it != own }
        val ownValue = state.hiddenCards[state.key(own.playerId, own.position)]?.value ?: return
        val theirValue = state.hiddenCards[state.key(theirs.playerId, theirs.position)]?.value ?: return
        if (ownValue > theirValue) state.exchange(targets)
    }

    /** Ace: the victim draws a card nobody has seen. */
    private fun applyForcedDraw(state: MutableMctsState, target: MctsActionTarget) {
        val victim = state.findPlayer(target.playerId) ?: return
        val position = victim.cardCount
        victim.cardCount++
        val drawn = state.takeFromDeck("forced-${victim.id}-$position")
        state.hiddenCards[state.key(victim.id, position)] = drawn
    }

    /**
     * King: name a card and declare its rank. Right, and the card leaves its hand — to be
     * played by the declarer if it has an action — and both King and the declared rank open
     * the toss-in window. Wrong, and the declarer draws a penalty card while the table learns
     * what the card really was. The generator only names cards the mover knows, so the wrong
     * branch is reachable only from a hand-built move.
     */
    private fun applyKing(state: MutableMctsState, mover: MutableMctsPlayer, move: MctsMove) {
        val declared = move.declaredRank ?: return
        val target = move.targets.first()
        val owner = state.findPlayer(target.playerId) ?: return
        val key = state.key(owner.id, target.position)
        val actual = state.hiddenCards[key] ?: return

        if (actual.rank != declared) {
            state.peek(mover, target)
            val penaltyPosition = mover.cardCount
            mover.cardCount++
            val penalty = state.takeFromDeck("penalty-$penaltyPosition")
            state.hiddenCards[state.key(mover.id, penaltyPosition)] = penalty
            return
        }

        val removed = state.removeCardsAt(owner, listOf(target.position)).firstOrNull() ?: return
        state.queuedTossRanks += declared
        if (removed.rank.isActionable()) {
            state.pendingCard = removed.copy(played = false)
            state.pendingOrigin = PendingOrigin.BORROWED
        } else {
            state.discarded += removed
            state.discardCount++
        }
    }

    internal fun certainMemory(card: Card) =
        CardMemory(card = card, confidence = 1.0, lastSeen = 0L, observations = 1)

    internal fun fallbackCard(id: String) = Card(
        id = id,
        rank = FALLBACK_RANK,
        value = getCardValue(FALLBACK_RANK),
        actionText = getCardShortDescription(FALLBACK_RANK).takeIf { it.isNotEmpty() },
        played = false,
    )
}

/**
 * A mutable working copy of a position, for the duration of one transition.
 *
 * [MctsGameState] is immutable; the handlers above mutate this and [freeze] hands the result
 * back. The mutation never escapes [StateTransition.applyMove]. `botMemory` and
 * `opponentModeler` are carried by reference on purpose: they are the bot's own long-lived
 * knowledge, shared across every node of the tree rather than owned by a position.
 */
private class MutableMctsPlayer(source: MctsPlayerState) {
    val id: String = source.id
    var cardCount: Int = source.cardCount
    var knownCards: MutableMap<Int, CardMemory> = source.knownCards.toMutableMap()

    fun freeze() = MctsPlayerState(id = id, cardCount = cardCount, knownCards = knownCards.toMap())
}

private class MutableMctsState(private val source: MctsGameState) {
    val players: List<MutableMctsPlayer> = source.players.map { MutableMctsPlayer(it) }
    val hiddenCards: MutableMap<String, Card> = source.hiddenCards.toMutableMap()
    val deck: MutableList<Card> = source.deckOrder.toMutableList()
    val discarded: MutableList<Card> = source.discarded.toMutableList()
    val queuedTossRanks: MutableList<Rank> = source.queuedTossRanks.toMutableList()

    var discardPileTop: Card? = source.discardPileTop
    var pendingCard: Card? = source.pendingCard
    var pendingOrigin: PendingOrigin? = source.pendingOrigin
    var deckSize: Int = source.deckSize
    var discardCount: Int = source.discardCount
    var currentPlayerIndex: Int = source.currentPlayerIndex
    var turnCount: Int = source.turnCount
    var isTossInPhase: Boolean = source.isTossInPhase
    var tossInRanks: List<Rank> = source.tossInRanks
    var awaitingVintoDecision: Boolean = source.awaitingVintoDecision
    var isTerminal: Boolean = source.isTerminal
    var finalTurnTriggered: Boolean = source.finalTurnTriggered
    var vintoCallerId: String? = source.vintoCallerId

    fun key(playerId: String, position: Int) = source.hiddenCardKey(playerId, position)

    fun currentPlayer(): MutableMctsPlayer? = players.getOrNull(currentPlayerIndex)

    fun findPlayer(playerId: String): MutableMctsPlayer? = players.firstOrNull { it.id == playerId }

    fun fallbackCard(id: String) = StateTransition.fallbackCard(id)

    /** The next card off the sampled deck. A deck the sample could not fill deals a stand-in. */
    fun takeFromDeck(id: String): Card {
        if (deckSize > 0) deckSize--
        return deck.removeFirstOrNull() ?: fallbackCard(id)
    }

    /**
     * Whether this player can act on the card at [position]. The searching bot only knows
     * what it remembers; everyone else is assumed to know their own hand.
     */
    fun knows(player: MutableMctsPlayer, position: Int): Boolean {
        if (player.id != source.botPlayerId) return true
        val memory = player.knownCards[position] ?: return false
        return memory.confidence > TRUSTED_CONFIDENCE
    }

    fun putOnPile(card: Card) {
        discardPileTop = card
        discarded += card
        discardCount++
    }

    /**
     * A card was played or put down. If its play borrowed another card (a King's declared
     * card, a declared swap-out), that card is aimed on the next ply and the window waits;
     * otherwise every rank that has been queued opens the toss-in window at once, and the
     * turn ends with the Vinto question if it may be asked.
     */
    fun finishPlay(rank: Rank) {
        queuedTossRanks += rank
        if (pendingCard != null) return

        resolveTossIn(queuedTossRanks.toList())
        queuedTossRanks.clear()

        if (MoveGenerator.mayCallVinto(vintoCallerId, turnCount, players.size)) {
            awaitingVintoDecision = true
        } else {
            advanceTurn()
        }
    }

    fun advanceTurn() {
        if (players.isEmpty()) return
        val next = (currentPlayerIndex + 1) % players.size
        if (vintoCallerId != null && players[next].id == vintoCallerId) {
            isTerminal = true
            return
        }
        currentPlayerIndex = next
        turnCount++

        // The reshuffle, as the real game plays it: a deck about to run dry folds the pile
        // back in, keeping only the top card.
        if (deckSize <= 1 && discardCount > 1) reshuffle()
    }

    private fun reshuffle() {
        val top = discarded.removeLastOrNull()
        deck += discarded
        deckSize += discarded.size
        discarded.clear()
        top?.let { discarded += it }
        discardCount = 1
    }

    /**
     * Everyone who holds the window's ranks — and knows it — sheds them. The caller never
     * tosses; the rules take that away with the call.
     */
    private fun resolveTossIn(ranks: List<Rank>) {
        for (player in players) {
            if (player.id == vintoCallerId) continue
            val matching = (0 until player.cardCount).filter { position ->
                val card = hiddenCards[key(player.id, position)]
                    ?: player.knownCards[position]?.takeIf { it.confidence > TRUSTED_CONFIDENCE }?.card
                card != null && card.rank in ranks && card.value >= 0 && knows(player, position)
            }
            if (matching.isNotEmpty()) shed(player, matching)
        }
    }

    /** Tossed cards leave the hand and go under the pile's top card. */
    fun shed(player: MutableMctsPlayer, positions: List<Int>) {
        val removed = removeCardsAt(player, positions)
        val top = discarded.removeLastOrNull()
        discarded += removed
        top?.let { discarded += it }
        discardCount += removed.size
    }

    /** The bot learns a card; anybody else peeking changes nothing the bot can see. */
    fun peek(mover: MutableMctsPlayer, target: MctsActionTarget) {
        if (mover.id != source.botPlayerId) return
        val card = hiddenCards[key(target.playerId, target.position)] ?: return
        findPlayer(target.playerId)?.knownCards?.set(target.position, StateTransition.certainMemory(card))
    }

    /** Two cards change hands, and what is known about each travels with it. */
    fun exchange(targets: List<MctsActionTarget>) {
        if (targets.size < 2) return
        val (first, second) = targets
        val firstKey = key(first.playerId, first.position)
        val secondKey = key(second.playerId, second.position)
        val firstCard = hiddenCards[firstKey] ?: return
        val secondCard = hiddenCards[secondKey] ?: return

        hiddenCards[firstKey] = secondCard
        hiddenCards[secondKey] = firstCard

        val firstOwner = findPlayer(first.playerId)
        val secondOwner = findPlayer(second.playerId)
        val firstMemory = firstOwner?.knownCards?.remove(first.position)
        val secondMemory = secondOwner?.knownCards?.remove(second.position)
        secondMemory?.let { firstOwner?.knownCards?.set(first.position, it) }
        firstMemory?.let { secondOwner?.knownCards?.set(second.position, it) }
    }

    /**
     * Takes cards out of a hand and closes the gaps.
     *
     * Positions are an index into the hand, so removing one renumbers everything after it —
     * in the dealt cards and in the memories alike. Renumbering only one of the two would
     * silently attach a memory to a different card.
     */
    fun removeCardsAt(player: MutableMctsPlayer, positions: List<Int>): List<Card> {
        val removed = positions.toSet()
        val originalCount = player.cardCount
        val taken = mutableListOf<Card>()

        val survivingCards = mutableMapOf<Int, Card>()
        val survivingMemories = mutableMapOf<Int, CardMemory>()
        var newPosition = 0

        for (oldPosition in 0 until originalCount) {
            val card = hiddenCards.remove(key(player.id, oldPosition))
            if (oldPosition in removed) {
                card?.let { taken += it }
                continue
            }
            card?.let { survivingCards[newPosition] = it }
            player.knownCards[oldPosition]?.let { survivingMemories[newPosition] = it }
            newPosition++
        }

        survivingCards.forEach { (position, card) -> hiddenCards[key(player.id, position)] = card }
        player.knownCards = survivingMemories
        player.cardCount = originalCount - removed.count { it in 0 until originalCount }
        return taken
    }

    fun freeze(): MctsGameState = source.copy(
        players = players.map { it.freeze() },
        hiddenCards = hiddenCards.toMap(),
        deckOrder = deck.toList(),
        discarded = discarded.toList(),
        queuedTossRanks = queuedTossRanks.toList(),
        discardPileTop = discardPileTop,
        pendingCard = pendingCard,
        pendingOrigin = pendingOrigin,
        deckSize = deckSize,
        discardCount = discardCount,
        currentPlayerIndex = currentPlayerIndex,
        turnCount = turnCount,
        isTossInPhase = isTossInPhase,
        tossInRanks = tossInRanks,
        awaitingVintoDecision = awaitingVintoDecision,
        isTerminal = isTerminal,
        finalTurnTriggered = finalTurnTriggered,
        vintoCallerId = vintoCallerId,
    )
}
