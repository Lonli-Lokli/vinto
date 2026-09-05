package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Believed
import game.vinto.shapes.Card
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Rank
import game.vinto.shapes.believedAt
import game.vinto.shapes.getCardValue
import kotlin.math.roundToInt

/**
 * The final round, once somebody has called Vinto. Ported from
 * `legacy-web/packages/bot/src/lib/coalition-planner.ts`.
 *
 * This is the one part of the game the bot does not search with MCTS, because it does not
 * have to. The coalition wins if the **lowest** coalition hand beats the caller's total, and
 * coalition members pool their hands — so from the coalition's side the final round is very
 * nearly a full-information, single-agent problem. The only things still hidden are the
 * caller's un-peeked cards and the order of the draw pile, and both are distributions rather
 * than opponents. Expectimax over those beats sampling them.
 *
 * The rule that shapes the whole module: **the coalition may not interact with the caller's
 * cards.** That is enforced structurally rather than by a check — the caller is not among
 * [CoalitionSearch.rootHands] at all, so there is no index that could name one of their
 * cards. A missed `if` cannot reintroduce it.
 *
 * The plan is recomputed at every decision point rather than stored, so it adapts as cards
 * are drawn instead of committing to a line that the draw has already invalidated.
 */

data class PlanCard(
    val id: String,
    val rank: Rank,
    val value: Int,
    val played: Boolean,
    /**
     * Whether [rank] may be *named* — declared with a King, matched in a toss-in, used as a
     * memo key. False for a card the plan holds only as an expectation: an undeclared
     * teammate's card, the acting member's own unread one, and a pair whose speaker has lost
     * its order.
     *
     * [value] is a separate matter and is always usable. An unassigned King-and-Ace pair has
     * no nameable rank and a perfectly good price; a Jack or a Queen is worth ten either way.
     * That is why this is `rankKnown` and not `known`: every guard in [CoalitionSearch] is
     * guarding a *rank*, and the shorter name invited a usable value to be thrown away with
     * an unusable rank.
     */
    val rankKnown: Boolean = true,
)

data class CoalitionMember(val id: String, val isBot: Boolean, val cards: List<PlanCard>)

data class CoalitionPlanInput(
    val vintoCallerId: String,
    val actingPlayerId: String,
    /** Every non-caller, in table order. */
    val members: List<CoalitionMember>,
    /** Members whose full turns are still to come *after* this one, in order. */
    val turnQueue: List<String>,
    /** Values of the caller's cards the coalition has actually seen. */
    val callerKnownValues: List<Int>,
    /** How many of the caller's cards nobody has seen. */
    val callerUnknownCount: Int,
    /** What is left in the deck as far as the coalition can tell. */
    val unseenCounts: Map<Rank, Int>,
    val discardTop: PlanCard?,
)

data class CoalitionActionTarget(val playerId: String, val position: Int)

data class CoalitionActionPlan(
    val targets: List<CoalitionActionTarget> = emptyList(),
    val shouldSwap: Boolean? = null,
    val declaredRank: Rank? = null,
)

sealed interface CoalitionDrawnCardDecision {
    data object Discard : CoalitionDrawnCardDecision

    data class UseAction(val action: CoalitionActionPlan) : CoalitionDrawnCardDecision

    /** [declaredRank] names the swapped-out card, so its action plays immediately. */
    data class Swap(val position: Int, val declaredRank: Rank? = null) : CoalitionDrawnCardDecision
}

/** The three that move points between hands. */
internal val COALITION_ACTION_RANKS = setOf(Rank.JACK, Rank.QUEEN, Rank.KING)

/**
 * The four that reveal a card. They move nothing, but the plan carries placeholders for the
 * cards nobody has read — the acting member's own unread positions, a teammate's undeclared
 * ones — and a card the plan can name is a card it can toss in or declare. A peek turns a
 * placeholder into a card, which is worth exactly what the lookahead then finds to do with it.
 */
internal val COALITION_PEEK_RANKS = setOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN)

internal fun Rank.helpsTheCoalition(): Boolean =
    this in COALITION_ACTION_RANKS || this in COALITION_PEEK_RANKS

internal val DECK_COUNTS: Map<Rank, Int> =
    ALL_RANKS.associateWith { if (it == Rank.JOKER) JOKER_COPIES else COPIES_PER_RANK }

private const val COPIES_PER_RANK = 4
private const val JOKER_COPIES = 2
internal const val FULL_DECK_SIZE = 54

/** Placeholder value for an unseen card when nothing is left to average over. */
internal const val NEUTRAL_UNSEEN_VALUE = 6

/** How many coalition turns after the current one are searched. */
internal const val MAX_LOOKAHEAD_TURNS = 2

/** Options kept per drawn card, indexed by lookahead depth. */
internal val PRUNE_WIDTH = listOf(Int.MAX_VALUE, 2, 1)

/** Root options that earn a full lookahead, pre-ranked by immediate value. */
internal const val ROOT_WIDTH = 16

/** Prefer a lower champion score when the win probability is otherwise equal. */
internal const val SCORE_TIE_EPS = 0.001

/** A toss-in cascade this deep has stopped converging; stop rather than loop. */
internal const val MAX_TOSS_IN_ROUNDS = 6

internal enum class SearchMode { FULL, GREEDY }

internal fun Card.toPlanCard() = PlanCard(id = id, rank = rank, value = value, played = played)

/**
 * Builds the planner's input from the authoritative state, or `null` when this is not a
 * coalition final round.
 *
 * What the plan may treat as known is exactly what the table has been **told**, never the real
 * hands: the acting member's own cards where it has actually read them, and what anybody has
 * *said* — about a teammate's hand or the caller's — through `DECLARE_CARDS`. Claims are taken
 * at face value and are only as reliable as the claimant's memory. Everything else rides as a
 * `rankKnown = false` placeholder carrying the expected value of an unseen card.
 *
 * The caller's cards used to be the exception: the plan pooled every coalition seat's private
 * `opponentKnowledge` of them. That was the one thing the bots shared that a person had no way
 * to say, and it is gone — a bot that has seen one of the caller's cards declares it, and the
 * plan reads the claim like any other. One channel, the same for people and for bots.
 *
 * A claim need not be exact. An unassigned pair — "these two are a King and an Ace, and I have
 * lost which is which" — prices both positions from its candidates, which is a far tighter
 * distribution than an unseen card, while naming no rank the search may declare or match.
 *
 * A wrong claim makes the plan wrong, not the engine: every planner output is position-based,
 * so the real cards move and the line simply fails.
 */
fun buildCoalitionPlanInput(state: GameState, actingPlayerId: String): CoalitionPlanInput? {
    val callerId = state.vintoCallerId ?: return null
    if (state.phase != GamePhase.FINAL || actingPlayerId == callerId) return null
    val caller = state.players.firstOrNull { it.id == callerId } ?: return null

    val coalitionSeats = state.players.filter { it.id != callerId }

    // What the **coalition** has said about the caller's hand, and nothing else.
    //
    // The speaker filter is load-bearing and was missing: the validator lets the caller claim
    // their own cards — bluffing is legitimate, and the reveal is what settles it — so without
    // this a caller could tell the coalition their hand was thirty, and pull ranks out of the
    // draw distribution by naming them. A bluff is for the other players to weigh, never an
    // input to their planner.
    val callerBelief = caller.cards.indices.map { position ->
        believedAt(caller, position).let { believed ->
            believed.copy(sources = believed.sources.filter { it.by != callerId })
        }
    }.map { believed ->
        if (believed.sources.isEmpty()) {
            Believed(ALL_RANKS.toSet(), disputed = false, sources = emptyList())
        } else {
            believed
        }
    }
    val callerKnownValues = callerBelief.filter { it.sources.isNotEmpty() }.map { it.value }
    val callerUnknownCount = caller.cards.size - callerKnownValues.size

    // Anything the plan treats as seen is no longer a possible draw — and only that. Counting
    // the real hands here would be the ground-truth leak this input exists to avoid; a wrong
    // claim skews the distribution slightly, which is the honest cost of trusting table talk.
    val unseenCounts = DECK_COUNTS.toMutableMap()
    fun consume(rank: Rank) {
        unseenCounts[rank] = maxOf(0, (unseenCounts[rank] ?: 0) - 1)
    }
    for (seat in coalitionSeats) {
        seat.cards.indices.forEach { position ->
            val believed = believedAt(seat, position)
            when {
                // Its own read card is ground truth to the acting member alone.
                seat.id == actingPlayerId && position in seat.knownCardPositions ->
                    consume(seat.cards[position].rank)

                // A claim narrow enough to name takes that rank out of the deck. A pair whose
                // order is lost still names both cards between them, so both come out.
                believed.rankKnown -> consume(believed.candidates.single())
                believed.sources.any { it.covering && it.positions.first() == position } ->
                    believed.candidates.forEach(::consume)
            }
        }
    }
    callerBelief.forEach { believed ->
        if (believed.rankKnown) consume(believed.candidates.single())
    }
    state.discardPile.cards.forEach { consume(it.rank) }
    state.pendingAction?.card?.let { consume(it.rank) }

    // Deterministic integer expectation of one unseen card, for the placeholders.
    val totalUnseen = unseenCounts.values.sum()
    val expectedUnseenValue =
        if (totalUnseen > 0) {
            val weighted = unseenCounts.entries.sumOf { getCardValue(it.key) * it.value }
            (weighted.toDouble() / totalUnseen).roundToInt()
        } else {
            NEUTRAL_UNSEEN_VALUE
        }

    fun unknownCard(seatId: String, position: Int) = PlanCard(
        id = "unknown-$seatId-$position",
        // The rank is never read: `rankKnown = false` guards every rank-consuming site.
        rank = Rank.SIX,
        value = expectedUnseenValue,
        played = false,
        rankKnown = false,
    )

    /**
     * A card the table has spoken about. Priced from what is left on the table's account of
     * it, and nameable only where one rank survives — so an unassigned King-and-Ace pair
     * plans at its value and declares nothing.
     */
    fun claimedCard(seatId: String, position: Int, believed: Believed) = PlanCard(
        id = "claimed-$seatId-$position",
        rank = believed.candidates.first(),
        value = believed.value,
        played = false,
        rankKnown = believed.rankKnown,
    )

    val members = coalitionSeats.map { seat ->
        val cards = seat.cards.mapIndexed { position, card ->
            val believed = believedAt(seat, position)
            when {
                // The acting member's own read cards are ground truth — to it alone.
                seat.id == actingPlayerId && position in seat.knownCardPositions ->
                    card.toPlanCard()

                // A standing public claim counts wherever it has not read the card itself: a
                // Queen swap carries a teammate's claim onto a card its new owner never saw.
                believed.sources.isNotEmpty() -> claimedCard(seat.id, position, believed)

                else -> unknownCard(seat.id, position)
            }
        }
        CoalitionMember(seat.id, seat.isBot, cards)
    }

    // Whoever is still to play between here and the caller. A toss-in window suspends the
    // turn, so the turn owner is the player the window interrupted.
    val turnOwnerIndex = state.activeTossIn?.originalPlayerIndex ?: state.currentPlayerIndex
    val turnQueue = mutableListOf<String>()
    for (step in 1 until state.players.size) {
        val player = state.players[(turnOwnerIndex + step) % state.players.size]
        if (player.id == callerId) break
        turnQueue += player.id
    }

    return CoalitionPlanInput(
        vintoCallerId = callerId,
        actingPlayerId = actingPlayerId,
        members = members,
        turnQueue = turnQueue,
        callerKnownValues = callerKnownValues,
        callerUnknownCount = callerUnknownCount,
        unseenCounts = unseenCounts,
        discardTop = state.discardPile.peekTop()?.toPlanCard(),
    )
}

// ============================================================================
// Decision API — called at each decision point, each call a fresh search
// ============================================================================

enum class CoalitionTurnStart { DRAW, TAKE_DISCARD }

/**
 * Draw from the deck, or take an unplayed action card off the discard?
 *
 * Two expectations compared, and both are pruned to the same width — the lookahead's, because
 * neither card is in hand yet: what to *do* with the card is decided again, at the root's full
 * width, once it is. Searching every reply to every possible draw in full cost thirteen root
 * searches per turn start, and the answer is the same.
 */
fun planCoalitionTurnStart(input: CoalitionPlanInput): CoalitionTurnStart {
    val search = CoalitionSearch(input)
    if (!search.hasActor) return CoalitionTurnStart.DRAW
    val width = pruneWidthAt(1)

    val take = search.pickBest(
        search.enumerateTakeDiscard(search.rootHands, search.rootDiscardTop, SearchMode.FULL),
        width,
    ) ?: return CoalitionTurnStart.DRAW

    // What drawing is worth: the value of the best reply to each possible card, weighted by
    // how likely that card is. Searched as widely as the take, or the comparison leans
    // towards whichever side was allowed more options.
    var drawValue = 0.0
    for (option in search.drawDistribution) {
        val best = search.pickBest(
            search.enumerateDrawnOptions(
                search.rootHands,
                search.actorIndex,
                option.card,
                SearchMode.FULL,
            ),
            width,
        )
        drawValue += option.probability * (best?.value ?: search.evaluate(search.rootHands))
    }

    return if (take.value > drawValue) CoalitionTurnStart.TAKE_DISCARD else CoalitionTurnStart.DRAW
}

/** After drawing: play the action, swap it in (optionally declaring), or discard. */
fun planCoalitionDrawnCard(
    input: CoalitionPlanInput,
    drawnCard: Card,
): CoalitionDrawnCardDecision {
    val search = CoalitionSearch(input)
    if (!search.hasActor) return CoalitionDrawnCardDecision.Discard

    val best = search.pickBest(
        search.enumerateDrawnOptions(
            search.rootHands,
            search.actorIndex,
            drawnCard.toPlanCard(),
            SearchMode.FULL,
        ),
    )
    return best?.option?.decision ?: CoalitionDrawnCardDecision.Discard
}

/**
 * Whether a pending action card is worth playing at all.
 *
 * A swap or a King can move points; a peek can turn a placeholder into a card the plan can
 * use, and is worth playing exactly when the lookahead finds something to do with it. An Ace
 * never is: a forced draw can only land on a teammate.
 */
fun shouldCoalitionUseAction(input: CoalitionPlanInput, card: Card): Boolean {
    if (!card.rank.helpsTheCoalition()) return false
    val search = CoalitionSearch(input)
    if (!search.hasActor) return false

    val best = search.pickBest(
        search.enumerateActionUse(search.rootHands, card.toPlanCard(), SearchMode.FULL),
    ) ?: return false

    val skipValue = search.valueOfOutcome(SimpleOutcome(search.rootHands, discardTop = null))
    return best.value > skipValue
}

/**
 * Where to point the acting bot's action card.
 *
 * King takes one target and the rank to declare; Jack and Queen take two, from two different
 * coalition members; a peek takes one placeholder. Anything else returns nothing, and the
 * caller is unreachable by construction.
 */
fun planCoalitionActionTargets(input: CoalitionPlanInput, actionCard: Card): BotActionDecision {
    if (!actionCard.rank.helpsTheCoalition()) return BotActionDecision()
    val search = CoalitionSearch(input)
    if (!search.hasActor) return BotActionDecision()

    val best = search.pickBest(
        search.enumerateActionUse(search.rootHands, actionCard.toPlanCard(), SearchMode.FULL),
    ) ?: return BotActionDecision()

    val plan = best.option.plan
    return BotActionDecision(
        targets = plan.targets.map { BotActionTarget(it.playerId, it.position) },
        shouldSwap = plan.shouldSwap,
        declaredRank = plan.declaredRank,
    )
}

/** Which of the acting bot's cards to throw into a toss-in window on these ranks. */
fun planCoalitionTossIn(input: CoalitionPlanInput, ranks: List<Rank>): List<Int> {
    val me = input.members.firstOrNull { it.id == input.actingPlayerId } ?: return emptyList()
    val wanted = ranks.toSet()

    return me.cards.mapIndexedNotNull { position, card ->
        position.takeIf { card.rank in wanted && shouldTossCard(card) }
    }
}

/**
 * Shedding a card is worth it when it carries points — or when it is a King, which is worth
 * nothing to hold and buys a declaration on the way out. Only a card the plan actually
 * *knows* qualifies: tossing on a placeholder's rank would be guessing, and a wrong guess
 * costs a penalty card and bars the seat for the round.
 */
internal fun shouldTossCard(card: PlanCard): Boolean =
    card.rankKnown && (card.value > 0 || card.rank == Rank.KING)

internal fun handScore(hand: List<PlanCard>): Int = hand.sumOf { it.value }

/** The coalition is judged on its best hand, not its average one. */
internal fun minScore(hands: List<List<PlanCard>>): Int =
    hands.minOfOrNull { handScore(it) } ?: 0

internal fun isTakeableAction(card: PlanCard?): Boolean =
    card != null && !card.played && card.rank.helpsTheCoalition()

internal fun pruneWidthAt(depth: Int) = PRUNE_WIDTH[minOf(depth, PRUNE_WIDTH.size - 1)]
