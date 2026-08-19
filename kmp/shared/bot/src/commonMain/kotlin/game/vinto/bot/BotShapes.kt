package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.GameState
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank

/**
 * Everything a bot is allowed to reason from, ported from `packages/bot/src/lib/shapes.ts`.
 *
 * Note what this deliberately is *not*: a window onto other players' hands. The bot gets its
 * own cards, the public piles, and its own *beliefs* about opponents — never their actual
 * cards. That is the whole reason the previous bot engine was deleted
 * (`docs/bot/BOT-ENGINE-DECISION.md`): it read hidden hands, and a bot that cheats is not a
 * difficulty setting.
 *
 * Bots run server-side and are given the full `GameState` because they run in the same
 * process that owns it (design D9). The discipline is in this context type, not in access.
 */
data class BotDecisionContext(
    val botId: String,
    val botPlayer: PlayerState,
    val allPlayers: List<PlayerState>,
    val gameState: GameState,
    val discardTop: Card? = null,
    /** The whole pile, so cards already gone can be discounted when estimating. */
    val discardPile: Pile = Pile(),
    val pendingCard: Card? = null,
    val activeActionCard: Card? = null,
    val currentAction: CurrentActionContext? = null,
    /** What this bot believes about opponents: opponent id -> position -> card. */
    val opponentKnowledge: Map<String, Map<Int, Card>> = emptyMap(),
    val coalitionLeaderId: String? = null,
    /** True when the bot is part of the coalition against the Vinto caller. */
    val isCoalitionMember: Boolean = false,
    val opponentModeler: OpponentModeler? = null,
)

data class CurrentActionContext(
    val targetType: String,
    val card: Card,
    val peekTargets: List<PeekTarget> = emptyList(),
)

data class PeekTarget(val playerId: String, val position: Int, val card: Card?)

/** `-1` for a player-level target, such as an Ace naming who must draw. */
data class BotActionTarget(val playerId: String, val position: Int)

data class BotActionDecision(
    val targets: List<BotActionTarget> = emptyList(),
    /** Queen: whether to swap after peeking. */
    val shouldSwap: Boolean? = null,
    /** King: the rank being declared. */
    val declaredRank: Rank? = null,
)

enum class TurnAction { DRAW, TAKE_DISCARD }

enum class CardChoice { USE_ACTION, SWAP, DISCARD }

data class BotTurnDecision(
    val action: TurnAction,
    val cardChoice: CardChoice? = null,
    val swapPosition: Int? = null,
    /** Pre-computed plan when taking from the discard pile, where the action is compulsory. */
    val actionDecision: BotActionDecision? = null,
)

/** Predicted end-of-turn position, used by the turn consequence simulator. */
data class TurnOutcome(
    val finalHandSize: Int,
    val finalKnownCards: Int,
    val finalScore: Int,
)

/**
 * The decisions a bot must be able to make.
 *
 * Synchronous and pure by design. Design D4 puts a `suspend` wrapper on
 * `Dispatchers.Default` around this so a thinking bot never blocks composition, but the
 * thinking itself stays a function of its inputs — which is what makes it testable and
 * replayable.
 */
interface BotDecisionService {
    fun decideTurnAction(context: BotDecisionContext): BotTurnDecision

    fun shouldUseAction(drawnCard: Card, context: BotDecisionContext): Boolean

    fun selectActionTargets(context: BotDecisionContext): BotActionDecision

    fun shouldSwapAfterPeek(peekedCards: List<Card>, context: BotDecisionContext): Boolean

    fun selectKingDeclaration(context: BotDecisionContext): Rank

    fun shouldParticipateInTossIn(
        discardedRanks: List<Rank>,
        context: BotDecisionContext,
    ): Boolean

    fun selectBestSwapPosition(drawnCard: Card, context: BotDecisionContext): Int?

    fun shouldCallVinto(context: BotDecisionContext): Boolean
}
