package game.vinto.bot

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SwapCardPayload
import kotlin.random.Random

/**
 * Turns a bot's *decisions* into the engine's *actions*.
 *
 * [BotDecisionService] answers questions — draw or take, use the action or swap, which
 * targets. The engine speaks in `GameAction`s, and one decision is often several of them: a
 * Jack is two target selections and then a swap-or-skip. Something has to sit between, and in
 * the TypeScript that something is `BotAIAdapter` in local-client — 1,500 lines wound around
 * animations, MobX reactions and `await delay(...)`, because it drives a UI.
 *
 * The server needs none of that. A Durable Object holds the state, and when it is a bot's
 * move it needs one question answered: *what is the next action?* So this is a pure function
 * of the state — [nextAction] — with no clock, no queue and nothing remembered between calls
 * beyond each bot's own memory. Everything the adapter tracked in fields is re-read from the
 * state instead, which is also what makes it safe to call after a reconnect or a hibernation.
 *
 * One deliberate departure: a service is kept **per bot**. The TypeScript shares one service
 * across all four seats, and its memory is rebuilt whenever the acting bot changes — so with
 * four bots alternating, a bot's memory is wiped every single turn. Nothing about the
 * decisions changes here; the memories simply survive, which is what the memory model was
 * written for.
 */
class BotRunner(
    difficulty: Difficulty = Difficulty.MODERATE,
    private val random: Random = Random.Default,
    private val serviceFactory: (Difficulty, Random) -> BotDecisionService = { d, r ->
        BotDecisionServiceFactory.create(d, r)
    },
) {
    private val difficultyInUse = difficulty
    private val services = mutableMapOf<String, BotDecisionService>()

    private fun serviceFor(botId: String): BotDecisionService =
        services.getOrPut(botId) { serviceFactory(difficultyInUse, random) }

    /**
     * The single next action the bots owe this state, or `null` when it is a human's move or
     * the game is over.
     *
     * One action at a time on purpose: every one goes back through `ActionValidator` and
     * `GameEngine.reduce`, so the bot never gets to assume the engine agreed with it.
     */
    fun nextAction(state: GameState): GameAction? = when {
        state.phase == GamePhase.SCORING -> null
        state.phase == GamePhase.SETUP -> setupAction(state)
        state.vintoCallerId != null && state.coalitionLeaderId == null -> coalitionLeaderAction(state)
        state.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE && state.activeTossIn != null ->
            tossInAction(state, state.activeTossIn!!)

        else -> turnAction(state)
    }

    // ---------------------------------------------------------------- setup

    /**
     * Setup peeks. The engine deals bots two known cards already, so this only ever runs for
     * a seat that was dealt none — and finishing setup moves the whole table into play.
     */
    private fun setupAction(state: GameState): GameAction? {
        val player = state.players.firstOrNull { it.knownCardPositions.size < SETUP_PEEKS } 
            ?: return GameAction.FinishSetup(PlayerIdPayload(state.players.first().id))

        val position = player.cards.indices.firstOrNull { it !in player.knownCardPositions }
            ?: return GameAction.FinishSetup(PlayerIdPayload(player.id))

        return GameAction.PeekSetupCard(PositionPayload(player.id, position))
    }

    /** Somebody called Vinto; the coalition needs a nominal leader before it can act. */
    private fun coalitionLeaderAction(state: GameState): GameAction? {
        val leader = state.players.firstOrNull { it.isBot && it.id != state.vintoCallerId }
            ?: return null
        return GameAction.SetCoalitionLeader(game.vinto.shapes.LeaderIdPayload(leader.id))
    }

    // ---------------------------------------------------------------- toss-in

    /**
     * The toss-in window, which interrupts whoever's turn it is.
     *
     * Every bot gets asked once, in seat order, and the window closes when all of them have
     * said they are done. A bot only ever tosses cards it has *read* — guessing costs a
     * penalty card and bars it from the rest of the window, so a guess is never worth it.
     *
     * This is also where Vinto is called, which looks odd until you notice the rule: Vinto is
     * declared at the end of a turn, and the toss-in window is the end of a turn.
     */
    private fun tossInAction(state: GameState, tossIn: ActiveTossIn): GameAction? {
        for (player in state.players) {
            if (!player.isBot) continue
            if (player.id in tossIn.playersReadyForNextTurn) continue

            val context = buildContext(state, player)

            // In the final round the coalition plans its toss-ins together: shed everything
            // the coalition can afford to lose, which is not the same as what this seat would
            // shed for itself.
            val coalition = buildCoalitionPlanInput(state, player.id)
            val positions =
                if (coalition != null) planCoalitionTossIn(coalition, tossIn.ranks)
                else tossInPositions(player, tossIn.ranks)

            if (positions.isNotEmpty() &&
                (coalition != null || serviceFor(player.id).shouldParticipateInTossIn(tossIn.ranks, context))
            ) {
                return GameAction.ParticipateInTossIn(
                    ParticipateInTossInPayload(player.id, positions),
                )
            }

            val ownsTheTurn = state.players.indexOfFirst { it.id == player.id } == tossIn.originalPlayerIndex
            if (ownsTheTurn && state.vintoCallerId == null &&
                serviceFor(player.id).shouldCallVinto(context)
            ) {
                return GameAction.CallVinto(PlayerIdPayload(player.id))
            }

            return GameAction.PlayerTossInFinished(PlayerIdPayload(player.id))
        }

        return null
    }

    /** Only cards the bot has read, and only ranks that are worth shedding. */
    private fun tossInPositions(player: PlayerState, ranks: List<Rank>): List<Int> =
        player.cards.indices.filter { position ->
            position in player.knownCardPositions && player.cards[position].rank in ranks
        }

    // ---------------------------------------------------------------- a turn

    private fun turnAction(state: GameState): GameAction? {
        val player = state.players.getOrNull(state.currentPlayerIndex) ?: return null
        if (!player.isBot) return null

        val pending = state.pendingAction
        return when {
            pending == null -> turnStartAction(state, player)

            state.subPhase == GameSubPhase.CHOOSING -> drawnCardAction(state, player, pending)

            // A card drawn *before* a toss-in window opened comes back as `awaiting_action`
            // while its own phase is still `choosing-action`: `advanceTurnAfterTossIn` moves
            // the sub-phase and leaves the action phase alone. Nothing can be done with it
            // from there — use, swap and discard all need `choosing`, and declaring needs
            // `selecting-target` — so the card is put down rather than played. Aiming it
            // instead is how a bot ends up declaring a King the engine will not accept.
            pending.actionPhase == ActionPhase.CHOOSING_ACTION -> abandonAction(player)

            else -> actionTargetAction(state, player, pending)
        }
    }

    /**
     * Draw from the deck, or take an action card off the discard and commit to playing it.
     *
     * The deck can genuinely run out: it is refilled from the discard pile when a turn ends
     * with one card left, but a forced draw or a penalty card can empty it between those
     * checks. Drawing then is not a bad move, it is an impossible one — so when there is no
     * deck the bot takes the discard if the rules allow, and otherwise ends the round, which
     * is the only thing left that moves the game forward.
     */
    private fun turnStartAction(state: GameState, player: PlayerState): GameAction? {
        val coalition = buildCoalitionPlanInput(state, player.id)
        val wantsDiscard = if (coalition != null) {
            planCoalitionTurnStart(coalition) == CoalitionTurnStart.TAKE_DISCARD
        } else {
            serviceFor(player.id).decideTurnAction(buildContext(state, player)).action ==
                TurnAction.TAKE_DISCARD
        }

        if (state.drawPile.isEmpty()) {
            if (canTakeDiscard(state)) return GameAction.PlayDiscard(PlayerIdPayload(player.id))

            // Nothing to draw and nothing to take. Calling Vinto is the move that is both
            // legal and forward — unless somebody already has, which is the final round, and
            // then there is no move at all. `END_ROUND` is the engine's exit from a position
            // it can otherwise only sit in; without it the game stops with every seat waiting
            // for another to act.
            if (state.vintoCallerId == null) return GameAction.CallVinto(PlayerIdPayload(player.id))
            return GameAction.EndRound(PlayerIdPayload(player.id))
        }

        return if (wantsDiscard && canTakeDiscard(state)) {
            GameAction.PlayDiscard(PlayerIdPayload(player.id))
        } else {
            GameAction.DrawCard(PlayerIdPayload(player.id))
        }
    }

    /** The rule: only an action card nobody has played yet can be taken off the pile. */
    private fun canTakeDiscard(state: GameState): Boolean {
        val top = state.discardPile.peekTop() ?: return false
        return top.actionText != null && !top.played
    }

    /**
     * What to do with the card just drawn: play its action, swap it into hand, or discard it.
     *
     * A swap may declare the rank of the card it displaces, which plays that card's action if
     * the declaration is right and costs a penalty card if it is wrong. The bot only declares
     * a card it has actually read, so the gamble is not one.
     */
    private fun drawnCardAction(
        state: GameState,
        player: PlayerState,
        pending: PendingAction,
    ): GameAction {
        val service = serviceFor(player.id)
        val context = buildContext(state, player)
        val drawnCard = pending.card

        buildCoalitionPlanInput(state, player.id)?.let { coalition ->
            return coalitionDrawnCardAction(coalition, player, drawnCard)
        }

        if (service.shouldUseAction(drawnCard, context)) {
            return GameAction.UseCardAction(PlayerIdPayload(player.id))
        }

        val position = service.selectBestSwapPosition(drawnCard, context)
            ?: return GameAction.DiscardCard(PlayerIdPayload(player.id))

        val displaced = player.cards.getOrNull(position)
        val declaredRank = displaced
            ?.takeIf { it.actionText != null && position in player.knownCardPositions }
            ?.rank

        return GameAction.SwapCard(SwapCardPayload(player.id, position, declaredRank))
    }

    /**
     * The same decision, made for the coalition rather than for this seat.
     *
     * The planner searches every coalition hand together, so it will have one member take on
     * points to shorten another's — the right play when only the lowest hand counts, and one
     * that self-interested search does not look for.
     */
    private fun coalitionDrawnCardAction(
        coalition: CoalitionPlanInput,
        player: PlayerState,
        drawnCard: Card,
    ): GameAction = when (val plan = planCoalitionDrawnCard(coalition, drawnCard)) {
        is CoalitionDrawnCardDecision.UseAction ->
            GameAction.UseCardAction(PlayerIdPayload(player.id))

        is CoalitionDrawnCardDecision.Swap ->
            GameAction.SwapCard(SwapCardPayload(player.id, plan.position, plan.declaredRank))

        CoalitionDrawnCardDecision.Discard ->
            GameAction.DiscardCard(PlayerIdPayload(player.id))
    }

    /**
     * Aiming an action that is already in play.
     *
     * How many targets a card takes, and what closes it off, is the rule for that rank — so
     * the state machine is keyed on the rank and on how many targets the engine has already
     * accepted, both read from the pending action rather than remembered.
     */
    private fun actionTargetAction(
        state: GameState,
        player: PlayerState,
        pending: PendingAction,
    ): GameAction? {
        val selected = pending.targets.size
        val coalition = buildCoalitionPlanInput(state, player.id)
        val plan = if (coalition != null) {
            planCoalitionActionTargets(coalition, pending.card)
        } else {
            serviceFor(player.id).selectActionTargets(buildContext(state, player))
        }

        return when (pending.card.rank) {
            // Peek one card, then acknowledge it.
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN ->
                if (selected == 0) selectTarget(state, player, plan, index = 0) else abandonAction(player)

            // Two cards from two different players, then swap or walk away. A skip is only
            // legal once both targets exist, so before that the exit is to abandon the card.
            Rank.JACK -> when (selected) {
                0, 1 -> selectTarget(state, player, plan, index = selected)
                else -> if (plan.shouldSwap != false) {
                    GameAction.ExecuteJackSwap(PlayerIdPayload(player.id))
                } else {
                    GameAction.SkipJackSwap(PlayerIdPayload(player.id))
                }
            }

            // Same shape, except the peek happens first and the swap is genuinely optional.
            Rank.QUEEN -> when (selected) {
                0, 1 -> selectTarget(state, player, plan, index = selected)
                else -> queenSwapDecision(state, player, pending)
            }

            // Name a card, then declare what it is and play that rank's action.
            Rank.KING ->
                if (selected == 0) {
                    selectTarget(state, player, plan, index = 0)
                } else {
                    declareKing(state, player, pending, plan)
                }

            // The victim draws; there is no position to name.
            Rank.ACE -> aceTarget(state, player, plan)

            else -> abandonAction(player)
        }
    }

    private fun selectTarget(
        state: GameState,
        player: PlayerState,
        plan: BotActionDecision,
        index: Int,
    ): GameAction {
        val target = plan.targets
            .filter { state.mayTarget(player, it.playerId) }
            .getOrNull(index)
            ?: return abandonAction(player)

        return GameAction.SelectActionTarget(
            SelectActionTargetPayload.Positional(player.id, target.playerId, target.position),
        )
    }

    /**
     * Whether [actor] is allowed to aim an action at [targetId] right now.
     *
     * The one case where it is not is the final round: everybody except the caller is a
     * coalition against them, and a coalition may not touch the caller's cards. The validator
     * enforces it — the bot has to *know* it, because an action the engine refuses is not a
     * bad move, it is no move, and a bot with no move stops the game for everybody at the
     * table. That is exactly how a full round used to hang: a coalition bot drew an Ace and
     * aimed it at the caller, the engine said no, and nothing further happened.
     */
    private fun GameState.mayTarget(actor: PlayerState, targetId: String): Boolean {
        if (phase != GamePhase.FINAL) return true
        if (vintoCallerId == null || actor.id == vintoCallerId) return true

        return targetId != vintoCallerId
    }

    /**
     * Put the card down unplayed and move on.
     *
     * There is no "cancel"; `CONFIRM_PEEK` is what the engine offers, and it does the right
     * thing for any pending card — marks it played, discards it, opens the toss-in window.
     * The bot needs this because an action can be legal to start and impossible to aim: a
     * peek-own by a bot that has already read every card of its own has nowhere to look.
     * Without an exit that state has no legal move at all, and the game simply stops.
     */
    private fun abandonAction(player: PlayerState) = GameAction.ConfirmPeek(PlayerIdPayload(player.id))

    /** A Queen has seen both cards by now, so the swap is decided on what it saw. */
    private fun queenSwapDecision(
        state: GameState,
        player: PlayerState,
        pending: PendingAction,
    ): GameAction {
        val peeked = pending.targets.mapNotNull { target ->
            state.players.firstOrNull { it.id == target.playerId }?.cards?.getOrNull(target.position)
        }

        return if (serviceFor(player.id).shouldSwapAfterPeek(peeked, buildContext(state, player))) {
            GameAction.ExecuteQueenSwap(PlayerIdPayload(player.id))
        } else {
            GameAction.SkipQueenSwap(PlayerIdPayload(player.id))
        }
    }

    /**
     * The declaration a King makes.
     *
     * The plan may name a rank; if it does not, the bot declares what is actually at the
     * position it chose, which is the safe answer — a wrong declaration costs a penalty card.
     */
    private fun declareKing(
        state: GameState,
        player: PlayerState,
        pending: PendingAction,
        plan: BotActionDecision,
    ): GameAction {
        val target = pending.targets.lastOrNull()
        val cardAtTarget = target?.let { chosen ->
            state.players.firstOrNull { it.id == chosen.playerId }?.cards?.getOrNull(chosen.position)
        }
        val declared = plan.declaredRank
            ?: serviceFor(player.id).selectKingDeclaration(buildContext(state, player))

        return GameAction.DeclareKingAction(
            game.vinto.shapes.DeclareKingActionPayload(
                player.id,
                cardAtTarget?.rank ?: declared,
            ),
        )
    }

    /** An Ace names a player rather than a card, so it carries the Ace-shaped payload. */
    private fun aceTarget(state: GameState, player: PlayerState, plan: BotActionDecision): GameAction {
        val targetId = plan.targets.map { it.playerId }
            .plus(state.players.map { it.id })
            .firstOrNull { it != player.id && state.mayTarget(player, it) }
            ?: return abandonAction(player)

        return GameAction.SelectActionTarget(SelectActionTargetPayload.Ace(player.id, targetId))
    }

    // ---------------------------------------------------------------- context

    /**
     * What the bot is allowed to see.
     *
     * `opponentKnowledge` carries only what this bot has been shown — its own read cards, and
     * nothing of anyone else's. The full hands are in [GameState] because the server owns it;
     * they are not put in the context, and that omission is the whole discipline.
     */
    private fun buildContext(state: GameState, player: PlayerState): BotDecisionContext {
        val ownKnowledge: Map<Int, Card> = player.cards.withIndex()
            .filter { (position, _) -> position in player.knownCardPositions }
            .associate { (position, card) -> position to card }

        return BotDecisionContext(
            botId = player.id,
            botPlayer = player,
            allPlayers = state.players,
            gameState = state,
            discardTop = state.discardPile.peekTop(),
            discardPile = state.discardPile,
            pendingCard = state.pendingAction?.card,
            activeActionCard = state.pendingAction?.card,
            opponentKnowledge = mapOf(player.id to ownKnowledge),
            coalitionLeaderId = state.coalitionLeaderId,
            isCoalitionMember = state.vintoCallerId != null && state.vintoCallerId != player.id,
        )
    }

    private companion object {
        const val SETUP_PEEKS = 2
    }
}
