package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardAction
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * The bot, ported from `legacy-web/packages/bot/src/lib/mcts-bot-decision.ts`.
 *
 * Every question the engine asks a bot arrives here. Most are answered by MCTS over
 * [MctsGameState] — the bot's *beliefs*, never the real hands — but a few are answered by a
 * rule instead, and those are deliberate rather than shortcuts:
 *
 * - **Vinto** is decided by the score rule steered by [VintoRoundSolver] (see
 *   [VintoCallWiring]), not by the search. The search cannot answer it: the engine only
 *   asks during the toss-in window, and in that window the move generator legitimately
 *   offers nothing but toss-in and pass, so `CALL_VINTO` is unreachable. The TypeScript
 *   carried that bug long enough for games to simply never end.
 * - **Peeks and high-value discards** are taken by heuristic, because information is worth
 *   more than any single position the search would evaluate.
 * - **Which card to swap** is [OutcomeSimulator]'s one-ply question, not a tree search.
 *
 * Two departures from the TypeScript, both required rather than cosmetic:
 *
 * - [random] is injected and threaded through determinization, rollouts and expansion, so a
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
     * Plans made at one decision point and spent at the next, keyed by bot.
     *
     * Keyed rather than a single field because a coalition leader plans for more than itself.
     */
    private val cachedActionPlans = mutableMapOf<String, BotActionDecision>()

    // ---------------------------------------------------------------- the interface

    override fun decideTurnAction(context: BotDecisionContext): BotTurnDecision {
        initializeIfNeeded(context)

        // A powerful action on the discard is worth more than anything a search would find,
        // and taking it commits the bot to playing it — which is the rule, not a choice.
        if (shouldAlwaysTakeDiscardPeekCard(context.discardTop, context.botPlayer)) {
            return BotTurnDecision(TurnAction.TAKE_DISCARD)
        }

        val result = runMctsWithPlan(constructGameState(context))

        return if (result.move.type == MctsMoveType.TAKE_DISCARD) {
            BotTurnDecision(TurnAction.TAKE_DISCARD, actionDecision = result.actionPlan)
        } else {
            BotTurnDecision(TurnAction.DRAW)
        }
    }

    override fun shouldUseAction(drawnCard: Card, context: BotDecisionContext): Boolean {
        initializeIfNeeded(context)
        if (drawnCard.actionText == null || drawnCard.played) return false

        // A peek is information, and information compounds; take it.
        if (shouldAlwaysUsePeekAction(drawnCard, context.botPlayer)) return true

        // An Ace only earns its keep defensively, against somebody close to calling Vinto.
        if (drawnCard.rank == Rank.ACE) {
            cachedActionPlans.remove(context.botId)
            return shouldUseAceAction(context.botPlayer, context.allPlayers, context.botId, botMemory)
        }

        val gameState = constructGameState(context)

        // Committing to an action the bot then cannot aim leaves it stuck mid-turn, so the
        // targets are checked to exist before the search is asked whether to want them.
        val actionType = getCardAction(drawnCard.rank)
        if (actionType == null || MoveGenerator.generateActionMoves(gameState, actionType).isEmpty()) {
            cachedActionPlans.remove(context.botId)
            return false
        }

        val result = runMctsWithPlan(gameState)
        if (result.move.type != MctsMoveType.USE_ACTION) {
            cachedActionPlans.remove(context.botId)
            return false
        }

        rememberOrForgetPlan(context.botId, result.actionPlan)
        return true
    }

    override fun selectActionTargets(context: BotDecisionContext): BotActionDecision {
        initializeIfNeeded(context)

        // A plan made when the action was chosen. Spending it here is what keeps the bot's
        // reason for taking the card and its use of the card the same decision.
        //
        // It is checked against the table first. A plan is read out of a node one ply deep in
        // the search, and a toss-in between then and now renumbers whatever it survived —
        // so a stale plan names a position that no longer exists. It is a hint, and a hint
        // that no longer fits is dropped rather than played.
        cachedActionPlans.remove(context.botId)
            ?.takeIf { it.stillFits(context) }
            ?.let { return it }

        val gameState = constructGameState(context)
        val bestMove = runMcts(gameState)
        if (bestMove.targets.isNotEmpty()) return bestMove.toDecision()

        // By the time this is asked, the engine has already committed the card — a card
        // taken from the discard, or a tossed-in action being resolved. "Would I rather
        // swap?" is no longer a question the bot gets to answer, so a search that came back
        // with a swap has answered the wrong one. Take the best *aimed* move instead.
        return firstAimedActionMove(gameState, context)?.toDecision() ?: BotActionDecision()
    }

    /**
     * The generator's first choice among moves that actually name a target.
     *
     * It orders by its own judgement, so first is best; and it only offers legal targets, so
     * this cannot produce an action the engine will refuse. `null` means there is genuinely
     * nowhere to point the card — every own card already read, for a peek — which the caller
     * has to handle by abandoning the action rather than by inventing a target.
     */
    private fun firstAimedActionMove(state: MctsGameState, context: BotDecisionContext): MctsMove? {
        val card = context.activeActionCard ?: context.pendingCard ?: return null
        val actionType = getCardAction(card.rank) ?: return null

        return MoveGenerator.generateActionMoves(state, actionType)
            .firstOrNull { it.targets.isNotEmpty() }
    }

    /** Every target still names a card that exists, in a hand that still exists. */
    private fun BotActionDecision.stillFits(context: BotDecisionContext): Boolean =
        targets.isNotEmpty() && targets.all { target ->
            val player = context.allPlayers.firstOrNull { it.id == target.playerId }
            player != null && target.position in player.cards.indices
        }

    private fun MctsMove.toDecision() = BotActionDecision(
        targets = targets.map { BotActionTarget(it.playerId, it.position) },
        // A Queen carries the answer on the move; a Jack's swap is the move type itself.
        shouldSwap = shouldSwap ?: (type == MctsMoveType.SWAP),
        declaredRank = declaredRank,
    )

    override fun shouldSwapAfterPeek(peekedCards: List<Card>, context: BotDecisionContext): Boolean {
        initializeIfNeeded(context)

        // The peek already happened; the bot knows these cards now whatever it decides next.
        val targets = context.currentAction?.peekTargets.orEmpty()
        targets.forEachIndexed { index, target ->
            peekedCards.getOrNull(index)?.let { botMemory.observeCard(it, target.playerId, target.position) }
        }

        // The two targets are committed — the only question left is swap or walk away, and
        // the peek has answered it. When one of the cards is the bot's own, swap exactly
        // when it sheds points. (Re-running the search here was wrong twice over: it
        // re-planned targets that are no longer up for choice, and its answer was read
        // against a move type that node can never produce — so the Queen always skipped.)
        if (targets.size == 2 && peekedCards.size == 2) {
            val ownIndex = targets.indexOfFirst { it.playerId == context.botId }
            if (ownIndex >= 0) {
                return peekedCards[ownIndex].value > peekedCards[1 - ownIndex].value
            }
            // Both cards belong to rivals: no modelled upside in shuffling their hands.
            return false
        }

        // No committed targets reached us (a hand-built context): fall back to the search.
        val bestMove = runMcts(constructGameState(context))
        return bestMove.type == MctsMoveType.SWAP && bestMove.shouldSwap == true
    }

    override fun selectKingDeclaration(context: BotDecisionContext): Rank {
        initializeIfNeeded(context)

        val result = runMctsWithPlan(constructGameState(context))
        val declared = result.move.declaredRank

        if (declared == null) {
            cachedActionPlans.remove(context.botId)
            // A Queen is the most useful thing to be wrong about: it sees two cards and may
            // swap them.
            return Rank.QUEEN
        }

        rememberOrForgetPlan(context.botId, result.actionPlan)
        return declared
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

        // The hand as this bot remembers it: real values only where a trusted memory says
        // so, the expected unseen value everywhere else. A position the bot never read is
        // priced as an average card, never as what actually sits there — so the Joker
        // nobody saw earns no protection, and a weak memory misprices honestly.
        val believed = OutcomeSimulator.BelievedHand(
            cards = botMemory.getPlayerMemory(botId)
                .filterKeys { it in context.botPlayer.cards.indices }
                .filterValues { it.confidence > TRUSTED_CONFIDENCE }
                .mapValues { (_, memory) -> memory.card },
            handSize = context.botPlayer.cards.size,
            expectedUnseenValue = averageRemainingCardValue(botMemory),
        )

        var bestScore = OutcomeSimulator.calculateOutcomeScore(
            OutcomeSimulator.simulateDiscardOutcome(drawnCard, context.botPlayer, believed),
        )
        var bestPosition: Int? = null

        for (position in context.botPlayer.cards.indices) {
            val outcome = OutcomeSimulator.simulateTurnOutcome(
                drawnCard, position, context.botPlayer, context, believed,
            )
            val score = OutcomeSimulator.calculateStrategicOutcomeScore(
                outcome,
                drawnCard,
                believed.cards[position],
            )

            if (score > bestScore) {
                bestScore = score
                bestPosition = position
            }
        }

        return bestPosition
    }

    override fun shouldCallVinto(context: BotDecisionContext): Boolean {
        initializeIfNeeded(context)
        // Judged on what the bot remembers of its own hand, not the engine's record — the
        // same beliefs it would declare to a coalition. A weak memory can misjudge a call,
        // which is the difficulty model doing its job.
        val believed = botMemory.believedOwnCards()
        if (!vintoCallGatesOpen(context, believed)) return false

        val believedScore = believed.values.sumOf { getCardValue(it) }
        val lateGame =
            context.gameState.turnNumber >= context.allPlayers.size * VintoCallWiring.LATE_GAME_LAPS
        if (believedScore > VintoCallWiring.ENABLER_MAX_SCORE && !lateGame) return false

        // The solver's worst case is what the best-placed opponent could still reach with
        // everything going their way. Its own verdict uses a strict `<`, but a Vinto tie
        // goes to the caller, so the comparisons here are tie-aware: the caller is beaten
        // only when an opponent can get *strictly* below it.
        val result = VintoRoundSolver(botMemory).validateVintoCall(
            botCards = believed.entries.sortedBy { it.key }.map { (position, rank) ->
                Card(
                    id = "believed_$position",
                    rank = rank,
                    value = getCardValue(rank),
                    played = false,
                )
            },
            opponents = context.allPlayers
                .filter { it.id != context.botId }
                .map { VintoRoundSolver.OpponentHand(it.id, it.cards.size) },
        )
        val beatenInWorstCase = result.worstCaseOpponentScore < believedScore

        return when {
            // A zero hand calls by right; the solver may only veto it with real knowledge.
            believedScore <= 0 ->
                !(beatenInWorstCase && result.confidence >= VintoCallWiring.VETO_CONFIDENCE)

            // A small positive hand calls when the solver approves at higher confidence —
            // the path that ends games where nobody ever assembles a zero.
            believedScore <= VintoCallWiring.ENABLER_MAX_SCORE &&
                !beatenInWorstCase && result.confidence >= VintoCallWiring.ENABLER_CONFIDENCE -> true

            // Deep into a stalemated game, provable safety gives way to relative judgement:
            // call when no opponent is *expected* to do better (a tie goes to the caller).
            // The bot holding the lowest believed hand always clears this bar, which is what
            // guarantees the game ends. See [VintoCallWiring.LATE_GAME_LAPS].
            lateGame -> {
                val bestExpectedOpponent = context.allPlayers
                    .filter { it.id != context.botId }
                    .minOfOrNull { estimatePlayerScore(it.cards.size, botMemory, it.id) }
                    ?: Double.MAX_VALUE
                believedScore <= bestExpectedOpponent
            }

            else -> false
        }
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

    private data class SearchResult(val move: MctsMove, val actionPlan: BotActionDecision? = null)

    /**
     * One search, plus the follow-up plan for moves that will be asked a second question.
     *
     * See [extractActionPlan] for why the plan is taken now rather than re-derived later.
     */
    private fun runMctsWithPlan(rootState: MctsGameState): SearchResult {
        val root = buildRoot(rootState) ?: return SearchResult(passMove())
        search(root)

        val bestChild = root.selectMostVisitedChild()
        val move = bestChild?.move ?: return SearchResult(passMove())

        return SearchResult(move, planFor(bestChild, move))
    }

    private fun runMcts(rootState: MctsGameState): MctsMove {
        val root = buildRoot(rootState) ?: return passMove()
        search(root)
        return root.selectMostVisitedChild()?.move ?: passMove()
    }

    /** Null when the position offers nothing — an empty deck, or a dead discard pile. */
    private fun buildRoot(rootState: MctsGameState): MctsNode? {
        val root = MctsNode(rootState, move = null, parent = null)
        root.untriedMoves = MoveGenerator.generateMoves(rootState).toMutableList()
        return root.takeIf { it.untriedMoves.isNotEmpty() }
    }

    /** Select, expand, simulate, backpropagate — until the budget runs out. */
    private fun search(root: MctsNode) {
        val deadline = config.timeLimitMillis?.let { TimeSource.Monotonic.markNow() }
        var iterations = 0

        while (iterations < config.iterations) {
            if (deadline != null && deadline.elapsedNow().inWholeMilliseconds >= config.timeLimitMillis) break

            var node = select(root)
            if (!node.isTerminal && node.hasUntriedMoves()) node = expand(node)
            node.backpropagate(simulate(node.state))

            iterations++
        }
    }

    private fun planFor(bestChild: MctsNode, move: MctsMove): BotActionDecision? = when {
        move.type == MctsMoveType.TAKE_DISCARD && move.actionCard?.actionText != null ->
            extractActionPlan(bestChild)

        // A King that declared an action card will be asked where to point it.
        move.type == MctsMoveType.USE_ACTION && move.declaredRank?.let { getCardAction(it) } != null ->
            extractActionPlan(bestChild)

        // A Jack, Queen or Ace played straight from hand already names its targets.
        move.type == MctsMoveType.USE_ACTION && move.targets.isNotEmpty() ->
            BotActionDecision(
                targets = move.targets.map { BotActionTarget(it.playerId, it.position) },
                shouldSwap = move.shouldSwap,
                declaredRank = move.declaredRank,
            )

        else -> null
    }

    /** Descend by UCB1 until a node has something new to try. */
    private fun select(root: MctsNode): MctsNode {
        var node = root
        while (!node.isTerminal) {
            if (node.hasUntriedMoves() || !node.isFullyExpanded) return node
            node = node.selectBestChildUcb1(config.explorationConstant) ?: break
        }
        return node
    }

    private fun expand(node: MctsNode): MctsNode {
        val move = node.takeRandomUntriedMove(random) ?: return node

        val child = MctsNode(StateTransition.applyMove(node.state, move), move, node)
        child.untriedMoves = MoveGenerator.generateMoves(child.state).toMutableList()
        node.addChild(child)

        return child
    }

    /**
     * Play the position out and score where it ends up.
     *
     * Determinization happens per simulation, not once per search: each rollout deals the
     * hidden cards differently, so a move that only works against one arrangement is found
     * out rather than rewarded.
     */
    private fun simulate(state: MctsGameState): Double {
        var current = determinize(state, random)
        var depth = 0

        while (!StateTransition.isTerminal(current) && depth < config.rolloutDepth) {
            val moves = MoveGenerator.generateMoves(current)
            val move = selectRolloutMove(current, moves, random) ?: break
            current = StateTransition.applyMove(current, move)
            depth++
        }

        return evaluateState(current, botId)
    }

    private fun passMove() = MctsMove(MctsMoveType.PASS, playerId = botId)

    // ---------------------------------------------------------------- context

    private fun rememberOrForgetPlan(botId: String, plan: BotActionDecision?) {
        if (plan != null) cachedActionPlans[botId] = plan else cachedActionPlans.remove(botId)
    }

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
            cachedActionPlans.clear()
            botMemory = BotMemory(context.botId, difficulty, random)
        }

        // Time passes at turn boundaries, never off a clock. One tick per *table lap*, not
        // per seat: the forget-chance and decay constants were calibrated as per-own-turn
        // rates, and four seats' turns are one of this bot's. Ticking per seat quadrupled
        // the forgetting, full-hand belief became rare, and — since only a Vinto call ends
        // a Vinto game — self-play stopped terminating. HARD draws nothing from Random
        // here (forget chance and decay rate are both zero), so perfect-memory fixtures
        // are bit-identical.
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
     * Forgetting first matters. A toss-in removes cards from the middle of a hand and
     * renumbers everything after them, but a memory keeps the index it was written with — so
     * a hand that has shrunk still "remembers" a card past its own end. Left alone that
     * distorts every ratio computed against hand size, and it used to reach the move
     * generator as a target the engine rejects outright.
     */
    private fun updateMemoryFromContext(context: BotDecisionContext) {
        // The table's public cards first: everything on the discard pile plus the card in
        // play is provably out of the deck, whoever remembers what. After a reshuffle the
        // pile is one card again and the pool recovers by the same sync.
        botMemory.syncVisibleCards(
            context.discardPile.toList().map { it.rank } +
                listOfNotNull(context.pendingCard?.rank),
        )

        forgetVanishedPositions(context)
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

    /** Positions that no longer exist in a hand this bot has an opinion about. */
    private fun forgetVanishedPositions(context: BotDecisionContext) {
        for (player in context.allPlayers) {
            botMemory.getPlayerMemory(player.id).keys
                .filter { it !in player.cards.indices }
                .forEach { botMemory.forgetCard(player.id, it) }
        }
    }

    /**
     * The position the search works from — built out of memory, not out of the real hands.
     *
     * Opponents are a card count plus whatever this bot has managed to remember. Their actual
     * cards are in [BotDecisionContext] and are never read here; that omission is the whole
     * discipline (`docs/bot/BOT-ENGINE-DECISION.md`).
     */
    private fun constructGameState(context: BotDecisionContext): MctsGameState {
        val players = context.allPlayers.map { player ->
            MctsPlayerState(
                id = player.id,
                cardCount = player.cards.size,
                knownCards = botMemory.getPlayerMemory(player.id),
                score = estimatePlayerScore(player.cards.size, botMemory, player.id),
            )
        }

        val activeTossIn = context.gameState.activeTossIn
        val isTossInPhase =
            context.gameState.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE && activeTossIn != null

        return MctsGameState(
            players = players,
            currentPlayerIndex = context.allPlayers.indexOfFirst { it.id == context.botId },
            botPlayerId = context.botId,
            discardPileTop = simulationDiscardTop(context, isTossInPhase),
            discardPile = context.discardPile,
            // The real count, not a constant. The TypeScript hardcodes a full deck here, which
            // hides the endgame from the search entirely — a rollout can never run the deck
            // out, and the bot will happily plan a draw that the engine has no card for.
            deckSize = context.gameState.drawPile.size,
            discardCount = context.discardPile.size,
            botMemory = botMemory,
            // Left empty on purpose: determinization deals them, once per simulation.
            hiddenCards = emptyMap(),
            pendingCard = context.activeActionCard ?: context.pendingCard,
            isTossInPhase = isTossInPhase,
            tossInRanks = if (isTossInPhase) activeTossIn?.ranks.orEmpty() else emptyList(),
            turnCount = context.gameState.turnNumber,
            finalTurnTriggered = context.gameState.finalTurnTriggered,
            vintoCallerId = context.gameState.vintoCallerId,
            coalitionLeaderId = context.coalitionLeaderId,
            opponentModeler = context.opponentModeler,
            isTerminal = false,
            winner = null,
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
