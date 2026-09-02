package game.vinto.bot

import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.Card
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Rank
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
     * False for a card the plan holds only as an expectation — an undeclared teammate card
     * or the acting member's own unread one. Its [rank] is a placeholder and must never
     * drive a decision; every rank-consuming site in [CoalitionSearch] checks this flag.
     */
    val known: Boolean = true,
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
 * What the plan may treat as known is exactly what the table has been told, never the real
 * hands: the acting member's own cards where it has actually read them, the ranks the other
 * members have *declared* out loud (`DECLARE_CARDS` — trusted at face value, and only as
 * reliable as the claimant's memory), and whatever any member has seen of the caller's hand,
 * pooled. Everything else rides as a `known = false` placeholder carrying the expected value
 * of an unseen card. A wrong claim makes the plan wrong, not the engine: every planner
 * output is position-based, so the real cards move and the line simply fails.
 */
fun buildCoalitionPlanInput(state: GameState, actingPlayerId: String): CoalitionPlanInput? {
    val callerId = state.vintoCallerId ?: return null
    if (state.phase != GamePhase.FINAL || actingPlayerId == callerId) return null
    val caller = state.players.firstOrNull { it.id == callerId } ?: return null

    val coalitionSeats = state.players.filter { it.id != callerId }

    // Everything any member has seen of the caller's hand, pooled.
    val knownCallerCardIds = coalitionSeats
        .flatMap { it.opponentKnowledge?.get(callerId)?.knownCards?.values.orEmpty() }
        .map { it.id }
        .toSet()

    val callerKnownValues = caller.cards.filter { it.id in knownCallerCardIds }.map { it.value }
    val callerUnknownCount = caller.cards.size - callerKnownValues.size

    // Anything the plan treats as seen is no longer a possible draw — and only that. Counting
    // the real hands here would be the ground-truth leak this input exists to avoid; a wrong
    // claim skews the distribution slightly, which is the honest cost of trusting table talk.
    val unseenCounts = DECK_COUNTS.toMutableMap()
    fun consume(rank: Rank) {
        unseenCounts[rank] = maxOf(0, (unseenCounts[rank] ?: 0) - 1)
    }
    for (seat in coalitionSeats) {
        if (seat.id == actingPlayerId) {
            val declared = seat.declaredCards ?: emptyMap()
            seat.cards.forEachIndexed { position, card ->
                when {
                    position in seat.knownCardPositions -> consume(card.rank)
                    declared[position] != null -> consume(declared.getValue(position))
                }
            }
        } else {
            seat.declaredCards?.values?.forEach { consume(it) }
        }
    }
    caller.cards.filter { it.id in knownCallerCardIds }.forEach { consume(it.rank) }
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
        // The rank is never read: `known = false` guards every rank-consuming site.
        rank = Rank.SIX,
        value = expectedUnseenValue,
        played = false,
        known = false,
    )

    fun declaredCard(seatId: String, position: Int, rank: Rank) = PlanCard(
        id = "declared-$seatId-$position",
        rank = rank,
        value = getCardValue(rank),
        played = false,
    )

    val members = coalitionSeats.map { seat ->
        val declared = seat.declaredCards ?: emptyMap()
        val cards =
            if (seat.id == actingPlayerId) {
                // The acting member's own read cards are ground truth; where it has *not*
                // read a card, a standing public claim about it still counts — a Queen swap
                // carries a teammate's declaration onto a card its new owner never saw.
                seat.cards.mapIndexed { position, card ->
                    when {
                        position in seat.knownCardPositions -> card.toPlanCard()
                        declared[position] != null ->
                            declaredCard(seat.id, position, declared.getValue(position))

                        else -> unknownCard(seat.id, position)
                    }
                }
            } else {
                seat.cards.mapIndexed { position, _ ->
                    declared[position]?.let { rank -> declaredCard(seat.id, position, rank) }
                        ?: unknownCard(seat.id, position)
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

/** Draw from the deck, or take an unplayed action card off the discard? */
fun planCoalitionTurnStart(input: CoalitionPlanInput): CoalitionTurnStart {
    val search = CoalitionSearch(input)
    if (!search.hasActor) return CoalitionTurnStart.DRAW

    val take = search.pickBest(
        search.enumerateTakeDiscard(search.rootHands, search.rootDiscardTop, SearchMode.FULL),
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
    card.known && (card.value > 0 || card.rank == Rank.KING)

internal fun handScore(hand: List<PlanCard>): Int = hand.sumOf { it.value }

/** The coalition is judged on its best hand, not its average one. */
internal fun minScore(hands: List<List<PlanCard>>): Int =
    hands.minOfOrNull { handScore(it) } ?: 0

internal fun isTakeableAction(card: PlanCard?): Boolean =
    card != null && !card.played && card.rank.helpsTheCoalition()

internal fun pruneWidthAt(depth: Int) = PRUNE_WIDTH[minOf(depth, PRUNE_WIDTH.size - 1)]
