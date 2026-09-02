package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * The bot.
 *
 * Every question the engine asks a bot is answered by one search: an information-set Monte
 * Carlo tree search over the bot's *beliefs*, never the real hands. Each iteration samples a
 * world consistent with what the bot remembers ([determinize]), walks the shared tree
 * applying the chosen moves to that world, plays the rest out with [selectRolloutMove], and
 * scores the end by the round's own rule ([rewards]). The move to play is the one the search
 * spent most of its iterations on.
 *
 * What is *not* a search, and why:
 *
 * - **Tossing in** is a rule: a card the bot believes matches goes in. There is nothing to
 *   weigh — the card leaves the hand and takes its points with it — and what makes a weak bot
 *   weak here is a wrong belief, which is the memory model's business.
 * - **A Queen's swap** is decided on the two cards it has just seen: trade when it sheds
 *   points. Exact, and the search would only rediscover it.
 *
 * Two things hold for every search:
 *
 * - [random] is injected and threaded through determinization, expansion and rollouts, so a
 *   decision is reproducible from a seed (design D4).
 * - The search runs on its iteration budget alone unless a caller opts into
 *   [MctsConfig.timeLimitMillis]. A clock-bounded search returns different moves on different
 *   machines, which would make a recorded game unreplayable.
 */
class MctsBotDecisionService(
    private val difficulty: Difficulty,
    private val random: Random = Random.Default,
) : BotDecisionService {

    private val config: MctsConfig = MCTS_DIFFICULTY_CONFIGS.getValue(difficulty)

    private var botId: String = ""
    private var botMemory: BotMemory = BotMemory(botId = "", difficulty, random)

    /** The deal this memory belongs to; a new gameId starts it over. */
    private var lastGameId: String = ""

    /** The last turn this bot thought about, so elapsed turns become memory ticks. */
    private var lastSeenTurnNumber: Int = -1

    /**
     * The last answer about a card in play, keyed on the position it was given for.
     *
     * The engine asks about one drawn card in pieces — play it? where? failing that, swap it
     * where? — and one search answers all of them. Re-searching each piece could answer the
     * second in a way that contradicts the first; reading the one answer back cannot.
     */
    private var lastAnswer: Pair<String, MctsMove>? = null

    // ---------------------------------------------------------------- the interface

    override fun decideTurnAction(context: BotDecisionContext): BotTurnDecision {
        initializeIfNeeded(context)
        val best = search(constructGameState(context, pending = null))
        return if (best?.type == MctsMoveType.TAKE_DISCARD) {
            BotTurnDecision(TurnAction.TAKE_DISCARD)
        } else {
            BotTurnDecision(TurnAction.DRAW)
        }
    }

    override fun shouldUseAction(drawnCard: Card, context: BotDecisionContext): Boolean {
        initializeIfNeeded(context)
        if (drawnCard.actionText == null || drawnCard.played) return false

        val best = answerAbout(context, drawnCard, PendingOrigin.DRAWN)
        return best?.type == MctsMoveType.USE_ACTION
    }

    override fun selectActionTargets(context: BotDecisionContext): BotActionDecision {
        initializeIfNeeded(context)
        val card = context.activeActionCard ?: context.pendingCard ?: return BotActionDecision()

        val best = answerAbout(context, card, PendingOrigin.COMMITTED)
        if (best?.type != MctsMoveType.USE_ACTION || best.targets.isEmpty()) return BotActionDecision()
        return best.toDecision()
    }

    override fun shouldSwapAfterPeek(peekedCards: List<Card>, context: BotDecisionContext): Boolean {
        initializeIfNeeded(context)

        // The peek already happened; the bot knows these cards now whatever it decides next.
        val targets = context.currentAction?.peekTargets.orEmpty()
        targets.forEachIndexed { index, target ->
            peekedCards.getOrNull(index)?.let { botMemory.observeCard(it, target.playerId, target.position) }
        }

        // The two targets are committed — the only question left is swap or walk away, and
        // the peek has answered it. When one of the cards is the bot's own, swap exactly
        // when it sheds points. Both cards belonging to rivals, there is no upside to model.
        if (targets.size == 2 && peekedCards.size == 2) {
            val ownIndex = targets.indexOfFirst { it.playerId == context.botId }
            return ownIndex >= 0 && peekedCards[ownIndex].value > peekedCards[1 - ownIndex].value
        }

        // No committed targets reached us (a hand-built context): ask the search.
        val card = context.activeActionCard ?: context.pendingCard ?: return false
        val best = answerAbout(context, card, PendingOrigin.COMMITTED)
        return best?.type == MctsMoveType.USE_ACTION && best.shouldSwap == true
    }

    /**
     * The rank to declare for the card the King has already been pointed at. The search
     * names both together, so the answer is read off the move that aims where the engine
     * says the King is pointing; failing that, what the bot remembers being there.
     */
    override fun selectKingDeclaration(context: BotDecisionContext): Rank {
        initializeIfNeeded(context)
        val pointedAt = context.gameState.pendingAction?.targets?.lastOrNull()
        val card = context.activeActionCard ?: context.pendingCard

        val remembered = pointedAt?.let { botMemory.getCardMemory(it.playerId, it.position) }
            ?.takeIf { it.confidence > TRUSTED_CONFIDENCE }
            ?.card
            ?.rank
        if (remembered != null) return remembered

        val best = card?.let { answerAbout(context, it, PendingOrigin.COMMITTED) }
        // A Queen is the most useful thing to be wrong about: it sees two cards and may swap.
        return best?.declaredRank ?: Rank.QUEEN
    }

    /**
     * Tossing in is always worth it when the rank is right: it sheds a card and its points at
     * once. The only question the rule leaves open is whether the bot is sure enough of the
     * rank to risk the penalty, which is what [shouldParticipateInTossIn] weighs.
     */
    override fun shouldParticipateInTossIn(
        discardedRanks: List<Rank>,
        context: BotDecisionContext,
    ): Boolean {
        initializeIfNeeded(context)
        return shouldParticipateInTossIn(
            discardedRanks,
            context.botPlayer,
            believed = botMemory.believedOwnCards(),
        )
    }

    /** Null means discard the drawn card rather than swapping it in. */
    override fun selectBestSwapPosition(drawnCard: Card, context: BotDecisionContext): Int? {
        initializeIfNeeded(context)
        val best = answerAbout(context, drawnCard, PendingOrigin.DRAWN)
        return best?.takeIf { it.type == MctsMoveType.SWAP }?.swapPosition
    }

    /**
     * Vinto is asked at the end of the bot's turn, and the search answers it like any other
     * move: the call's value against the value of letting play go on, over worlds sampled
     * from what the bot remembers. A hand it has not fully read is not a bar to calling — it
     * is a distribution the search prices, as the coalition planner prices the caller's.
     */
    override fun shouldCallVinto(context: BotDecisionContext): Boolean {
        initializeIfNeeded(context)
        val root = constructGameState(context, pending = null, awaitingVintoDecision = true)
        if (!MoveGenerator.mayCallVinto(root)) return false
        return search(root)?.type == MctsMoveType.CALL_VINTO
    }

    /**
     * Declarations come from the memory model, not the engine's record: what this bot
     * *thinks* it holds. On easy and moderate difficulty an observation can have recorded
     * the wrong card, decayed, or been dropped — so a claim can be wrong or missing, and is
     * exactly as wrong as the bot's play already was. Deterministic under the seeded
     * [Random].
     */
    override fun believedOwnCards(context: BotDecisionContext): Map<Int, Rank> {
        initializeIfNeeded(context)
        return botMemory.believedOwnCards()
    }

    // ---------------------------------------------------------------- the search

    /**
     * One search per card in play, whatever is asked about it. The answer is keyed on the
     * deal, the turn, the card and the hand, so a later question about the same card reads
     * the same answer — the targets for an action the search chose to play a moment ago —
     * and a question about a different position runs a fresh search.
     */
    private fun answerAbout(context: BotDecisionContext, card: Card, origin: PendingOrigin): MctsMove? {
        val key = listOf(
            context.gameState.gameId,
            context.gameState.turnNumber,
            card.id,
            context.botPlayer.cards.joinToString(",") { it.id },
        ).joinToString("#")

        lastAnswer?.takeIf { it.first == key }?.let { return it.second }

        val best = search(constructGameState(context, pending = card, origin = origin))
        lastAnswer = best?.let { key to it }
        return best
    }

    private fun MctsMove.toDecision() = BotActionDecision(
        targets = targets.map { BotActionTarget(it.playerId, it.position) },
        shouldSwap = shouldSwap,
        declaredRank = declaredRank,
    )

    /**
     * Information-set MCTS: every iteration samples one world from the root's beliefs and
     * plays it down the shared tree, so a node's statistics average over every world in which
     * its move was legal. Moves are applied to the sampled world, never to the beliefs — the
     * difference between a Jack that trades two real cards and one that trades nothing.
     */
    private fun search(root: MctsGameState): MctsMove? {
        val rootMoves = MoveGenerator.generateMoves(root)
        if (rootMoves.isEmpty()) return null
        if (rootMoves.size == 1) return rootMoves.first()

        return searchTree(root).mostVisitedChild()?.move
    }

    /** The whole tree, for a test that wants to read the root's statistics. */
    internal fun searchTree(root: MctsGameState): MctsNode {
        val tree = MctsNode(move = null, parent = null, seats = root.players.size)
        val deadline = config.timeLimitMillis?.let { TimeSource.Monotonic.markNow() }
        var iterations = 0

        while (iterations < config.iterations) {
            val limit = config.timeLimitMillis
            if (deadline != null && limit != null && deadline.elapsedNow().inWholeMilliseconds >= limit) break

            val world = determinize(root, random)
            val (leaf, state) = descend(tree, world)
            leaf.backpropagate(rewards(rollout(state)))

            iterations++
        }

        return tree
    }

    /** Select by UCB until a node has something untried in this world, then expand it once. */
    private fun descend(tree: MctsNode, world: MctsGameState): Pair<MctsNode, MctsGameState> {
        var node = tree
        var state = world
        var expanded = false

        while (!expanded && !StateTransition.isTerminal(state)) {
            val legal = MoveGenerator.generateMoves(state)
            val untried = node.untried(legal)
            val next = when {
                legal.isEmpty() -> null
                untried.isNotEmpty() -> {
                    expanded = true
                    node.child(untried[random.nextInt(untried.size)])
                }
                else -> node.selectChild(legal, state.currentPlayerIndex, config.explorationConstant)
            } ?: return node to state
            val move = next.move ?: return node to state

            node = next
            state = StateTransition.applyMove(state, move)
        }

        return node to state
    }

    private fun rollout(start: MctsGameState): MctsGameState {
        var state = start
        var depth = 0

        while (!StateTransition.isTerminal(state) && depth < config.rolloutDepth) {
            val moves = MoveGenerator.generateMoves(state)
            val move = selectRolloutMove(state, moves, random) ?: break
            state = StateTransition.applyMove(state, move)
            depth++
        }

        return state
    }

    // ---------------------------------------------------------------- context

    /**
     * A service instance can be reused across bots, so the memory follows whoever is asking.
     * Changing bot means starting from nothing — one bot's memories are not another's.
     */
    private fun initializeIfNeeded(context: BotDecisionContext) {
        // A new deal is a new gameId (never keyed on roundNumber, which counts table laps
        // within one deal): the hands this memory described no longer exist, so it starts
        // over. Client and worker already rebuild their runners per deal; this is the
        // deterministic backstop for any service that survives one.
        if (botId != context.botId || lastGameId != context.gameState.gameId) {
            botId = context.botId
            lastGameId = context.gameState.gameId
            lastSeenTurnNumber = -1
            lastAnswer = null
            botMemory = BotMemory(context.botId, difficulty, random)
        }

        // Time passes at turn boundaries, never off a clock. One tick per *table lap*, not
        // per seat: the forget-chance and decay constants were calibrated as per-own-turn
        // rates, and four seats' turns are one of this bot's. HARD draws nothing from Random
        // here (forget chance and decay rate are both zero), so perfect-memory fixtures are
        // bit-identical.
        val turn = context.gameState.turnNumber
        if (lastSeenTurnNumber in 0 until turn) {
            val seats = maxOf(1, context.allPlayers.size)
            val laps = (turn - 1) / seats - (lastSeenTurnNumber - 1) / seats
            repeat(maxOf(0, laps)) { botMemory.processTurnBoundary() }
        }
        lastSeenTurnNumber = turn

        updateMemoryFromContext(context)
    }

    /**
     * Fold everything the engine says this bot can see into its memory, and drop what the
     * table has since invalidated.
     *
     * Forgetting first matters, and it has to be *forgetting* rather than re-reading. A
     * toss-in removes cards from the middle of a hand and renumbers everything after them,
     * but a memory keeps the index it was written with — so a hand that has shrunk still
     * "remembered" a card past its own end, and, worse, remembered the thrown card at the
     * position the next card slid into. Re-reading that position looked like enough, and
     * was not: on easy and moderate a read silently fails some of the time, and a failed
     * read left the stale belief standing. So anything the engine no longer backs — a
     * position it says is unread, or a different card at it — goes first, and a missed
     * glance leaves an honest gap instead of a wrong card.
     */
    private fun updateMemoryFromContext(context: BotDecisionContext) {
        // The table's public cards first: everything on the discard pile plus the card in
        // play is provably out of the deck, whoever remembers what. After a reshuffle the
        // pile is one card again and the pool recovers by the same sync.
        botMemory.syncVisibleCards(
            context.discardPile.toList().map { it.rank } +
                listOfNotNull(context.pendingCard?.rank),
        )

        forgetStaleSightings(context)
        context.botPlayer.cards.forEachIndexed { position, card ->
            if (position !in context.botPlayer.knownCardPositions) return@forEachIndexed
            if (botMemory.getCardMemory(botId, position)?.card?.id != card.id) {
                botMemory.observeCard(card, botId, position)
            }
        }

        for ((opponentId, knownCards) in context.opponentKnowledge) {
            for ((position, card) in knownCards) {
                if (botMemory.getCardMemory(opponentId, position)?.card?.id != card.id) {
                    botMemory.observeCard(card, opponentId, position)
                }
            }
        }
    }

    /**
     * Every memory the engine does not stand behind: a position that no longer exists, one
     * this bot is not recorded as having read, or one holding a different card from the one
     * remembered there. The engine's record is what the bot has legitimately seen, and it is
     * renumbered on every removal and cleared on every blind swap — so a memory it does not
     * back is a stale index, never a sighting the engine missed.
     */
    private fun forgetStaleSightings(context: BotDecisionContext) {
        for (player in context.allPlayers) {
            val seen = context.opponentKnowledge[player.id].orEmpty()
            botMemory.getPlayerMemory(player.id)
                .filter { (position, memory) ->
                    position !in player.cards.indices || seen[position]?.id != memory.card.id
                }
                .keys
                .forEach { botMemory.forgetCard(player.id, it) }
        }
    }

    /** The root a question would be searched from, for a test that wants to read the tree. */
    internal fun rootFor(context: BotDecisionContext, pending: Card?): MctsGameState {
        initializeIfNeeded(context)
        return constructGameState(context, pending)
    }

    /**
     * The position the search works from — built out of memory, not out of the real hands.
     *
     * Opponents are a card count plus whatever this bot has managed to remember. Their actual
     * cards are in [BotDecisionContext] and are never read here; that omission is the whole
     * discipline (`docs/bot/BOT-ENGINE-DECISION.md`).
     */
    private fun constructGameState(
        context: BotDecisionContext,
        pending: Card?,
        origin: PendingOrigin? = null,
        awaitingVintoDecision: Boolean = false,
    ): MctsGameState {
        val players = context.allPlayers.map { player ->
            MctsPlayerState(
                id = player.id,
                cardCount = player.cards.size,
                knownCards = botMemory.getPlayerMemory(player.id),
            )
        }

        val activeTossIn = context.gameState.activeTossIn
        val isTossInPhase = !awaitingVintoDecision && pending == null &&
            context.gameState.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE && activeTossIn != null

        return MctsGameState(
            players = players,
            currentPlayerIndex = context.allPlayers.indexOfFirst { it.id == context.botId },
            botPlayerId = context.botId,
            discardPileTop = simulationDiscardTop(context, isTossInPhase),
            discardPile = context.discardPile,
            deckSize = context.gameState.drawPile.size,
            discardCount = context.discardPile.size,
            botMemory = botMemory,
            // Left empty on purpose: determinization deals them, once per iteration.
            hiddenCards = emptyMap(),
            pendingCard = pending,
            pendingOrigin = pending?.let { origin ?: PendingOrigin.COMMITTED },
            isTossInPhase = isTossInPhase,
            // No safe call: `isTossInPhase` carries `activeTossIn != null`, and K2 reads that.
            tossInRanks = if (isTossInPhase) activeTossIn.ranks else emptyList(),
            awaitingVintoDecision = awaitingVintoDecision,
            turnCount = context.gameState.turnNumber,
            finalTurnTriggered = context.gameState.finalTurnTriggered,
            vintoCallerId = context.gameState.vintoCallerId,
            coalitionLeaderId = context.coalitionLeaderId,
            opponentModeler = context.opponentModeler,
            isTerminal = false,
        )
    }

    /**
     * During a toss-in window the thing the bot is reacting to is the *window's* rank, which
     * may no longer be the physical top of the pile. Standing in a card for it keeps the
     * move generator offering the right toss-ins.
     */
    private fun simulationDiscardTop(context: BotDecisionContext, isTossInPhase: Boolean): Card? {
        val tossInRank = context.gameState.activeTossIn?.ranks?.firstOrNull()
        if (!isTossInPhase || tossInRank == null) return context.discardTop

        return Card(
            id = "tossin-state-${tossInRank.serialName}",
            rank = tossInRank,
            value = getCardValue(tossInRank),
            actionText = getCardShortDescription(tossInRank).takeIf { it.isNotEmpty() },
            // Already played: a toss-in window's card is not there to be taken.
            played = true,
        )
    }
}

/**
 * There is exactly one bot engine.
 *
 * The former `v2` service was deleted because it read opponents' hidden hands — see
 * `docs/bot/BOT-ENGINE-DECISION.md`. Difficulty tunes memory accuracy and how long the search
 * runs; it does not switch in a different, cheating implementation.
 */
object BotDecisionServiceFactory {
    fun create(difficulty: Difficulty, random: Random = Random.Default): BotDecisionService =
        MctsBotDecisionService(difficulty, random)
}
