package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardValue

/**
 * The expectimax search behind [buildCoalitionPlanInput], ported from the `CoalitionSearch`
 * class in `packages/bot/src/lib/coalition-planner.ts`.
 *
 * Coalition hands are held as a plain `List<List<PlanCard>>` indexed by member, and **the
 * caller is not in it**. Every target the search can name is an index into that list, so the
 * rule against touching the caller's cards is enforced by there being no way to express it.
 *
 * Two things are integrated rather than sampled: the caller's unseen cards become a
 * distribution over totals, and the deck becomes a distribution over draws. That is what
 * makes this cheaper and sharper than MCTS here — with the coalition's hands shared, almost
 * nothing else is hidden.
 */
internal class CoalitionSearch(input: CoalitionPlanInput) {

    private val memberIds: List<String> = input.members.map { it.id }
    private val memberIsBot: List<Boolean> = input.members.map { it.isBot }
    private val queue: List<Int>

    private val winProbCache = mutableMapOf<Int, Double>()
    private val memo = mutableMapOf<String, Double>()

    private val callerKnownSum: Int = input.callerKnownValues.sum()

    /** Total → probability, over the caller's cards nobody has seen. */
    private val callerUnknownDistribution: Map<Int, Double>

    val drawDistribution: List<DrawOption>
    val rootHands: Hands = input.members.map { it.cards }
    val rootDiscardTop: PlanCard? = input.discardTop
    val actorIndex: Int

    init {
        val indexById = input.members.withIndex().associate { (index, m) -> m.id to index }
        actorIndex = indexById[input.actingPlayerId] ?: -1
        queue = input.turnQueue.mapNotNull { indexById[it] }

        // Fall back to a full deck when the coalition has somehow accounted for everything;
        // a zero-weight distribution would make every expectation NaN.
        val accountedFor = ALL_RANKS.sumOf { input.unseenCounts[it] ?: 0 }
        val counts = if (accountedFor > 0) input.unseenCounts else DECK_COUNTS
        val total = if (accountedFor > 0) accountedFor else FULL_DECK_SIZE

        drawDistribution = ALL_RANKS.mapNotNull { rank ->
            val count = counts[rank] ?: 0
            if (count <= 0) {
                null
            } else {
                DrawOption(
                    card = PlanCard("draw-${rank.serialName}", rank, getCardValue(rank), played = false),
                    probability = count.toDouble() / total,
                )
            }
        }

        callerUnknownDistribution = buildUnknownSumDistribution(input.callerUnknownCount)
    }

    val hasActor: Boolean get() = actorIndex >= 0

    // ---------------------------------------------------------------- the caller

    /**
     * The distribution of the caller's hidden total, by convolving one unseen card at a time.
     *
     * Exact rather than sampled: there are at most a handful of unseen cards and fourteen
     * ranks, so the whole distribution is cheaper than the samples it would take to estimate
     * it — and it does not wobble between two runs of the same position.
     */
    private fun buildUnknownSumDistribution(unknownCount: Int): Map<Int, Double> {
        var distribution = mapOf(0 to 1.0)

        repeat(unknownCount) {
            val next = mutableMapOf<Int, Double>()
            for ((sum, probability) in distribution) {
                for (draw in drawDistribution) {
                    val total = sum + draw.card.value
                    next[total] = (next[total] ?: 0.0) + probability * draw.probability
                }
            }
            distribution = next
        }

        return distribution
    }

    /** P(the caller's total beats [target]) — the coalition needs to be strictly lower. */
    fun winProb(target: Int): Double = winProbCache.getOrPut(target) {
        callerUnknownDistribution.entries
            .filter { callerKnownSum + it.key > target }
            .sumOf { it.value }
    }

    /**
     * How good a coalition position is: the chance its best hand wins, nudged towards lower
     * scores so that two positions with equal odds are separated by the safer one.
     */
    fun evaluate(hands: Hands): Double {
        val best = minScore(hands)
        return winProb(best) - SCORE_TIE_EPS * best
    }

    // ---------------------------------------------------------------- primitives

    private fun swapCards(hands: Hands, memberA: Int, posA: Int, memberB: Int, posB: Int): Hands {
        val next = hands.toMutableList()
        val handA = next[memberA].toMutableList()
        val handB = next[memberB].toMutableList()
        val cardA = handA[posA]
        handA[posA] = handB[posB]
        handB[posB] = cardA
        next[memberA] = handA
        next[memberB] = handB
        return next
    }

    private fun removeCard(hands: Hands, member: Int, position: Int): Hands {
        val next = hands.toMutableList()
        next[member] = next[member].filterIndexed { index, _ -> index != position }
        return next
    }

    private fun replaceCard(hands: Hands, member: Int, position: Int, card: PlanCard): Hands {
        val next = hands.toMutableList()
        next[member] = next[member].mapIndexed { index, existing ->
            if (index == position) card else existing
        }
        return next
    }

    /**
     * Every Jack or Queen swap: two cards from two *different* coalition members, plus the
     * option of not swapping at all.
     *
     * The pairing loop starts at `i + 1`, which is both the rule (two different players) and
     * the reason a member can never be made to swap with themselves.
     */
    private fun enumerateSwaps(hands: Hands, mode: SearchMode): List<ActionOutcome> {
        val noSwap = ActionOutcome(
            hands = hands,
            discardTop = null,
            plan = CoalitionActionPlan(shouldSwap = false),
        )
        val swaps = hands.indices.flatMap { i ->
            (i + 1 until hands.size).flatMap { j -> swapsBetween(hands, i, j) }
        }

        if (mode == SearchMode.FULL) return listOf(noSwap) + swaps

        val best = Best<ActionOutcome>()
        best.offer(noSwap, evaluate(hands))
        swaps.forEach { best.offer(it, evaluate(it.hands)) }
        return listOfNotNull(best.item)
    }

    /** Every swap between these two members' hands, skipping the ones that move no points. */
    private fun swapsBetween(hands: Hands, memberA: Int, memberB: Int): List<ActionOutcome> {
        val outcomes = mutableListOf<ActionOutcome>()

        for (a in hands[memberA].indices) {
            for (b in hands[memberB].indices) {
                if (hands[memberA][a].value == hands[memberB][b].value) continue

                outcomes += ActionOutcome(
                    hands = swapCards(hands, memberA, a, memberB, b),
                    discardTop = null,
                    plan = CoalitionActionPlan(
                        targets = listOf(
                            CoalitionActionTarget(memberIds[memberA], a),
                            CoalitionActionTarget(memberIds[memberB], b),
                        ),
                        shouldSwap = true,
                    ),
                )
            }
        }

        return outcomes
    }

    /**
     * Every King play: name a coalition card, declare its rank, and that card leaves the
     * hand. If what left was a Jack or Queen, its swap is played too.
     *
     * The resulting toss-in window covers both the King and the declared rank, which is where
     * a King's real value in the final round comes from — it can empty two hands at once.
     */
    private fun enumerateKingTargets(hands: Hands, mode: SearchMode): List<KingOutcome> {
        if (mode == SearchMode.GREEDY) return greedyKingTarget(hands)

        val results = mutableListOf<KingOutcome>()
        for (member in hands.indices) {
            for (position in hands[member].indices) {
                val target = hands[member][position]
                val removed = removeCard(hands, member, position)
                val plan = CoalitionActionPlan(
                    targets = listOf(CoalitionActionTarget(memberIds[member], position)),
                    declaredRank = target.rank,
                )
                val tossRanks = listOf(Rank.KING, target.rank)

                val variants =
                    if (target.rank == Rank.JACK || target.rank == Rank.QUEEN) {
                        enumerateSwaps(removed, SearchMode.FULL).map { it.hands }
                    } else {
                        listOf(removed)
                    }

                variants.forEach { results += KingOutcome(it, plan, tossRanks) }
            }
        }
        return results
    }

    /** Two stages: pick the card worth removing, then play its action if it had one. */
    private fun greedyKingTarget(hands: Hands): List<KingOutcome> {
        val best = Best<Triple<Int, Int, Hands>>()

        for (member in hands.indices) {
            for (position in hands[member].indices) {
                val target = hands[member][position]
                val removed = removeCard(hands, member, position)
                val value = evaluate(resolveTossIn(removed, listOf(Rank.KING, target.rank)).hands)
                best.offer(Triple(member, position, removed), value)
            }
        }

        val (member, position, removed) = best.item ?: return emptyList()
        val target = hands[member][position]

        val finalHands =
            if (target.rank == Rank.JACK || target.rank == Rank.QUEEN) {
                enumerateSwaps(removed, SearchMode.GREEDY).firstOrNull()?.hands ?: removed
            } else {
                removed
            }

        return listOf(
            KingOutcome(
                hands = finalHands,
                plan = CoalitionActionPlan(
                    targets = listOf(CoalitionActionTarget(memberIds[member], position)),
                    declaredRank = target.rank,
                ),
                tossRanks = listOf(Rank.KING, target.rank),
            ),
        )
    }

    /**
     * The toss-in window after cards of these ranks hit the pile.
     *
     * Every coalition *bot* sheds what matches, and a tossed Jack, Queen or King plays its
     * action on the way out — which can open a further window. Human members are left alone:
     * the planner does not get to decide their turns for them.
     */
    private fun resolveTossIn(hands: Hands, ranks: List<Rank>): TossInOutcome {
        val active = ranks.toMutableSet()
        var current = hands
        var tossedAny = false

        var round = 0
        while (round < MAX_TOSS_IN_ROUNDS) {
            round++
            val tossedActions = mutableListOf<Rank>()
            val afterRound = current.toMutableList()
            var changed = false

            for (member in current.indices) {
                if (!memberIsBot[member]) continue
                val hand = current[member]
                val tossed = hand.filter { it.rank in active && shouldTossCard(it) }
                if (tossed.isEmpty()) continue

                tossed.filter { it.rank in COALITION_ACTION_RANKS }.forEach { tossedActions += it.rank }
                afterRound[member] = hand - tossed.toSet()
                changed = true
            }

            if (!changed) break
            tossedAny = true
            current = afterRound

            // A tossed action card still gets played, and a King's declaration can widen the
            // window — which is what makes this a cascade rather than a single sweep.
            for (rank in tossedActions) {
                if (rank == Rank.KING) {
                    val king = enumerateKingTargets(current, SearchMode.GREEDY).firstOrNull() ?: continue
                    current = king.hands
                    active += king.tossRanks
                } else {
                    enumerateSwaps(current, SearchMode.GREEDY).firstOrNull()?.let { current = it.hands }
                }
            }
        }

        return TossInOutcome(current, tossedAny)
    }

    /**
     * A card lands on the pile, the window opens, and this is what is left afterwards.
     *
     * If anything was tossed on top, the discarded card is buried and the next member can no
     * longer take it — which is why [SimpleOutcome.discardTop] goes to null.
     */
    private fun afterDiscard(
        hands: Hands,
        discarded: PlanCard,
        played: Boolean,
        tossRanks: List<Rank>,
    ): SimpleOutcome {
        val resolved = resolveTossIn(hands, tossRanks)
        return SimpleOutcome(
            hands = resolved.hands,
            discardTop = if (resolved.tossed) null else discarded.copy(played = played),
        )
    }

    /** Ways to play an action card. Only Jack, Queen and King have any here. */
    fun enumerateActionUse(
        hands: Hands,
        card: PlanCard,
        mode: SearchMode,
    ): List<ActionOutcome> = when (card.rank) {
        Rank.KING -> enumerateKingTargets(hands, mode).map { king ->
            val after = afterDiscard(king.hands, card, played = true, tossRanks = king.tossRanks)
            ActionOutcome(after.hands, after.discardTop, king.plan)
        }

        Rank.JACK, Rank.QUEEN -> enumerateSwaps(hands, mode).map { swap ->
            val after = afterDiscard(swap.hands, card, played = true, tossRanks = listOf(card.rank))
            ActionOutcome(after.hands, after.discardTop, swap.plan)
        }

        else -> emptyList()
    }

    /** Every way the acting member can finish a turn after drawing [card]. */
    fun enumerateDrawnOptions(
        hands: Hands,
        actor: Int,
        card: PlanCard,
        mode: SearchMode,
    ): List<DrawnOutcome> {
        val options = mutableListOf<DrawnOutcome>()

        // 1. Discard it. Unplayed, so the next member could still take it.
        afterDiscard(hands, card, played = false, tossRanks = listOf(card.rank)).let {
            options += DrawnOutcome(it.hands, it.discardTop, CoalitionDrawnCardDecision.Discard)
        }

        // 2. Play its action, if it has one worth playing.
        if (card.rank in COALITION_ACTION_RANKS) {
            enumerateActionUse(hands, card, mode).forEach {
                options += DrawnOutcome(
                    it.hands,
                    it.discardTop,
                    CoalitionDrawnCardDecision.UseAction(it.plan),
                )
            }
        }

        // 3. Swap it in. Whatever it displaces is discarded, and if that was an action card
        //    it can be declared on the way out so its action plays immediately.
        for (position in hands[actor].indices) {
            val displaced = hands[actor][position]
            val swapped = replaceCard(hands, actor, position, card)

            if (displaced.rank in COALITION_ACTION_RANKS) {
                enumerateActionUse(swapped, displaced, mode).forEach {
                    options += DrawnOutcome(
                        it.hands,
                        it.discardTop,
                        CoalitionDrawnCardDecision.Swap(position, displaced.rank),
                    )
                }
            }

            afterDiscard(swapped, displaced, played = false, tossRanks = listOf(displaced.rank)).let {
                options += DrawnOutcome(it.hands, it.discardTop, CoalitionDrawnCardDecision.Swap(position))
            }
        }

        return options
    }

    /** Taking the top of the discard, which by rule must be an unplayed action card. */
    fun enumerateTakeDiscard(
        hands: Hands,
        discardTop: PlanCard?,
        mode: SearchMode,
    ): List<ActionOutcome> =
        if (!isTakeableAction(discardTop) || discardTop == null) {
            emptyList()
        } else {
            enumerateActionUse(hands, discardTop, mode)
        }

    // ---------------------------------------------------------------- lookahead

    /**
     * Positions that differ only in card *identity* play out identically, so the key is the
     * multiset of ranks per hand. Ranks are ordered by the enum rather than by name — any
     * consistent order canonicalises a hand, and this one does not depend on how a rank
     * happens to spell itself.
     */
    private fun memoKey(hands: Hands, discardTop: PlanCard?, queueIndex: Int): String = buildString {
        append(queueIndex)
        append('#')
        for (hand in hands) {
            hand.map { it.rank }.sortedBy { it.ordinal }.joinTo(this, ",") { it.serialName }
            append('|')
        }
        append(if (isTakeableAction(discardTop)) discardTop?.rank?.serialName else "-")
    }

    /** What the coalition position is worth at the start of the queued member's turn. */
    private fun valueAtTurnStart(hands: Hands, discardTop: PlanCard?, queueIndex: Int, depth: Int): Double {
        if (queueIndex >= queue.size || depth > MAX_LOOKAHEAD_TURNS) return evaluate(hands)

        val member = queue[queueIndex]
        // Human members decide for themselves; assuming they improve would be planning with
        // somebody else's hand.
        if (!memberIsBot[member]) return valueAtTurnStart(hands, discardTop, queueIndex + 1, depth)

        val key = memoKey(hands, discardTop, queueIndex)
        memo[key]?.let { return it }

        val width = pruneWidthAt(depth)
        var best = Double.NEGATIVE_INFINITY

        for (outcome in enumerateTakeDiscard(hands, discardTop, SearchMode.GREEDY)) {
            best = maxOf(best, valueAtTurnStart(outcome.hands, outcome.discardTop, queueIndex + 1, depth + 1))
        }

        // Drawing is an expectation over what could come up, not a choice.
        var drawValue = 0.0
        for (draw in drawDistribution) {
            val options = pruneOptions(
                enumerateDrawnOptions(hands, member, draw.card, SearchMode.GREEDY),
                width,
            )
            var bestOption = Double.NEGATIVE_INFINITY
            for (option in options) {
                bestOption = maxOf(
                    bestOption,
                    valueAtTurnStart(option.hands, option.discardTop, queueIndex + 1, depth + 1),
                )
            }
            drawValue += draw.probability * bestOption
        }
        best = maxOf(best, drawValue)

        memo[key] = best
        return best
    }

    fun <T : CoalitionOutcome> pruneOptions(options: List<T>, width: Int): List<T> =
        if (options.size <= width) {
            options
        } else {
            options.sortedByDescending { evaluate(it.hands) }.take(width)
        }

    /** The value of one outcome of the acting bot's own turn; lookahead starts after it. */
    fun valueOfOutcome(outcome: CoalitionOutcome): Double =
        valueAtTurnStart(outcome.hands, outcome.discardTop, queueIndex = 0, depth = 1)

    data class Ranked<T>(val option: T, val value: Double)

    /** Pre-rank cheaply, then spend the full lookahead on the shortlist. */
    fun <T : CoalitionOutcome> pickBest(options: List<T>): Ranked<T>? {
        val best = Best<T>()
        for (option in pruneOptions(options, ROOT_WIDTH)) {
            best.offer(option, valueOfOutcome(option))
        }
        return best.item?.let { Ranked(it, best.value) }
    }
}

internal typealias Hands = List<List<PlanCard>>

internal interface CoalitionOutcome {
    val hands: Hands
    val discardTop: PlanCard?
}

internal data class SimpleOutcome(
    override val hands: Hands,
    override val discardTop: PlanCard?,
) : CoalitionOutcome

internal data class ActionOutcome(
    override val hands: Hands,
    override val discardTop: PlanCard?,
    val plan: CoalitionActionPlan,
) : CoalitionOutcome

internal data class DrawnOutcome(
    override val hands: Hands,
    override val discardTop: PlanCard?,
    val decision: CoalitionDrawnCardDecision,
) : CoalitionOutcome

internal data class KingOutcome(
    val hands: Hands,
    val plan: CoalitionActionPlan,
    val tossRanks: List<Rank>,
)

internal data class TossInOutcome(val hands: Hands, val tossed: Boolean)

internal data class DrawOption(val card: PlanCard, val probability: Double)

/** Keeps the best thing offered so far. First offer wins a tie, which keeps runs stable. */
internal class Best<T> {
    var item: T? = null
        private set
    var value: Double = Double.NEGATIVE_INFINITY
        private set

    fun offer(item: T, value: Double) {
        if (value > this.value) {
            this.value = value
            this.item = item
        }
    }
}
