package game.vinto.bot

import game.vinto.engine.isBarredFromTossIn
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.DeclareCardsPayload
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

    /**
     * Beliefs inferred from watching everybody play — shared across the seats this runner
     * drives, because everything in it is public information: anyone at the table saw the
     * same discard, the same swap, the same toss-in. Fed by [observe], reset when the deal
     * changes, and handed to every decision through the context.
     */
    private val opponentModeler = OpponentModeler()
    private var modelerGameId: String? = null

    private fun serviceFor(botId: String): BotDecisionService =
        services.getOrPut(botId) { serviceFactory(difficultyInUse, random) }

    /** The table model, exposed so a test can check what [observe] taught it. */
    internal fun tableModelForTesting(): OpponentModeler = opponentModeler

    /**
     * Tells the runner what just happened at the table, so [OpponentModeler] can learn from
     * it. Call after every *accepted* action — anyone's, human or bot — with the states
     * around it. Idle to skip: nothing else depends on it, the bots just read the table less
     * well. See [observationsFor] for what is actually inferred.
     */
    fun observe(action: GameAction, before: GameState, after: GameState) {
        if (modelerGameId != before.gameId) {
            opponentModeler.reset()
            modelerGameId = before.gameId
        }

        for (observation in observationsFor(action, before, after)) {
            when (observation) {
                is TableObservation.Acted ->
                    opponentModeler.handleObservedAction(observation.observed)

                is TableObservation.BeliefInvalidated ->
                    opponentModeler.removeCardBelief(observation.playerId, observation.position)

                is TableObservation.CardRemoved ->
                    opponentModeler.shiftCardBeliefs(observation.playerId, observation.position)
            }
        }
    }

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
        else -> coalitionDeclarationAction(state)
            ?: if (state.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE && state.activeTossIn != null) {
                tossInAction(state, state.activeTossIn!!)
            } else {
                turnAction(state)
            }
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

    /**
     * Somebody called Vinto; the coalition needs a nominal leader before it can act.
     *
     * With a human in the coalition the choice is theirs: returning null here holds *all*
     * bot play — the leader branch precedes every other in [nextAction] — until the human's
     * `SET_COALITION_LEADER` arrives through the client. Safe to wait on: the caller's
     * protection no longer depends on a leader being set. A bots-only coalition (the human
     * called Vinto, or an all-bot table) still auto-picks.
     */
    private fun coalitionLeaderAction(state: GameState): GameAction? {
        if (state.players.any { !it.isBot && it.id != state.vintoCallerId }) return null
        val leader = state.players.firstOrNull { it.isBot && it.id != state.vintoCallerId }
            ?: return null
        return GameAction.SetCoalitionLeader(game.vinto.shapes.LeaderIdPayload(leader.id))
    }

    /**
     * Once the leader is chosen, each coalition bot says out loud what it believes its own
     * cards are — one `DECLARE_CARDS` per bot, in seat order, before any final-round play.
     *
     * The claims come from [BotDecisionService.believedOwnCards] — the bot's *memory*, not
     * the engine's record — so on lower difficulties they can be wrong, which is the model:
     * the coalition plans from what was said at the table, not from anyone's real hand. A
     * bot re-declares only if its earlier claims were all invalidated (the field empties
     * back to null when every claimed card has moved).
     */
    private fun coalitionDeclarationAction(state: GameState): GameAction? {
        if (state.phase != GamePhase.FINAL || state.vintoCallerId == null) return null
        if (state.coalitionLeaderId == null) return null

        for (player in state.players) {
            if (!player.isBot || player.id == state.vintoCallerId) continue
            if (player.declaredCards != null) continue
            // Whether a bot speaks at all is decided by *state* — it has read positions and
            // has not declared — never by what its memory happens to hold. Two runners
            // looking at the same table must agree on who still owes a declaration
            // (`FinishesTest` drives the human seat with a second runner and relies on it);
            // only the claims' content is memory's business.
            if (player.knownCardPositions.isEmpty()) continue

            val believed = serviceFor(player.id)
                .believedOwnCards(buildContext(state, player))
                .filterKeys { it in player.cards.indices }

            // Memory came up entirely empty for a hand the table watched this bot read: it
            // still owes the coalition an answer, and the seat's public record is the
            // fallback. Partial memory stays partial — that is where a weak bot's
            // declarations go honestly wrong.
            val claims = believed.ifEmpty {
                player.knownCardPositions
                    .filter { it in player.cards.indices }
                    .associateWith { player.cards[it].rank }
            }
            if (claims.isEmpty()) continue

            return GameAction.DeclareCards(DeclareCardsPayload(player.id, claims))
        }
        return null
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

            // One wrong throw bars a player for the round, and the validator enforces the
            // bar — a second attempt is not a bad move but an *illegal* one, which would
            // stop the whole table. Belief-driven tossing makes the first wrong throw
            // genuinely possible, so the bar is checked here the way the engine checks it.
            // The same rule the validator applies, so a bot never proposes a throw the
            // engine would refuse: this window outside the final round, the whole round in it.
            val barred = isBarredFromTossIn(state, player.id)

            // In the final round the coalition plans its toss-ins together: shed everything
            // the coalition can afford to lose, which is not the same as what this seat would
            // shed for itself.
            val coalition = buildCoalitionPlanInput(state, player.id)
            val positions = when {
                barred -> emptyList()

                coalition != null ->
                    // The planner already restricts itself to cards this seat has read
                    // (unread positions are `known = false` in the plan); the filter is the
                    // belt to that brace — a coalition bot never tosses a card it has not
                    // actually seen.
                    planCoalitionTossIn(coalition, tossIn.ranks)
                        .filter { it in player.knownCardPositions }

                // Solo, the bot throws what it *believes* matches — its memory of its own
                // hand, not the engine's record — and pays the ordinary penalty when a weak
                // memory believed wrongly.
                else -> tossInPositions(
                    player,
                    tossIn.ranks,
                    serviceFor(player.id).believedOwnCards(context),
                )
            }

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

    /** Only cards the bot believes it has read, and only ranks in the window. */
    private fun tossInPositions(
        player: PlayerState,
        ranks: List<Rank>,
        believed: Map<Int, Rank>,
    ): List<Int> =
        believed.entries
            .filter { (position, rank) -> position in player.cards.indices && rank in ranks }
            .map { it.key }
            .sorted()

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
            // In a coalition the swap was chosen by the coalition plan — asking the solo
            // search instead is how a Queen refused to move its own Joker to a teammate.
            Rank.QUEEN -> when (selected) {
                0, 1 -> selectTarget(state, player, plan, index = selected)
                else -> if (coalition != null) {
                    if (plan.shouldSwap != false) {
                        GameAction.ExecuteQueenSwap(PlayerIdPayload(player.id))
                    } else {
                        GameAction.SkipQueenSwap(PlayerIdPayload(player.id))
                    }
                } else {
                    queenSwapDecision(state, player, pending)
                }
            }

            // Name a card, then declare what it is and play that rank's action.
            Rank.KING ->
                if (selected == 0) {
                    selectTarget(state, player, plan, index = 0)
                } else {
                    declareKing(state, player, pending, plan, trustPlan = coalition != null)
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
        val targets = pending.targets.map { target ->
            PeekTarget(
                target.playerId,
                target.position,
                state.players.firstOrNull { it.id == target.playerId }
                    ?.cards?.getOrNull(target.position),
            )
        }
        val peeked = targets.mapNotNull { it.card }

        // The peek rides on the context so the service can fold what was seen into memory —
        // this is the one production construction of [CurrentActionContext].
        val context = buildContext(
            state,
            player,
            currentAction = CurrentActionContext("peek_and_swap", pending.card, targets),
        )

        return if (serviceFor(player.id).shouldSwapAfterPeek(peeked, context)) {
            GameAction.ExecuteQueenSwap(PlayerIdPayload(player.id))
        } else {
            GameAction.SkipQueenSwap(PlayerIdPayload(player.id))
        }
    }

    /**
     * The declaration a King makes.
     *
     * Solo, the plan may name a rank, and if it does not the bot declares what is actually
     * at the position it chose — the safe answer, since a wrong declaration costs a penalty
     * card. In a coalition ([trustPlan]) the plan's rank is the *claimed* one and stands as
     * said: reading the real card there would peek at a teammate's hand through a claim,
     * and a claim that was wrong should fail the way a wrong memory fails.
     */
    private fun declareKing(
        state: GameState,
        player: PlayerState,
        pending: PendingAction,
        plan: BotActionDecision,
        trustPlan: Boolean = false,
    ): GameAction {
        val planned = plan.declaredRank
        if (trustPlan && planned != null) {
            return GameAction.DeclareKingAction(
                game.vinto.shapes.DeclareKingActionPayload(player.id, planned),
            )
        }

        val target = pending.targets.lastOrNull()
        val cardAtTarget = target?.let { chosen ->
            state.players.firstOrNull { it.id == chosen.playerId }?.cards?.getOrNull(chosen.position)
        }
        val declared = planned
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
     * `opponentKnowledge` carries what this bot has been shown, and only that: its own read
     * cards, plus the engine's record of everything *this seat* has legitimately seen of the
     * other seats — its 9/10/Queen peeks, public reveals, watched swap-ins. That record is
     * `PlayerState.opponentKnowledge`, maintained and renumbered by the engine; forwarding
     * it is what lets the memory model actually remember opponents. The full hands are in
     * [GameState] because the server owns it; they are not put in the context, and that
     * omission is the whole discipline.
     */
    private fun buildContext(
        state: GameState,
        player: PlayerState,
        currentAction: CurrentActionContext? = null,
    ): BotDecisionContext {
        val ownKnowledge: Map<Int, Card> = player.cards.withIndex()
            .filter { (position, _) -> position in player.knownCardPositions }
            .associate { (position, card) -> position to card }

        // A hand shrinks and renumbers; a sighting of a position that no longer exists is
        // dropped here as a belt — the memory layer prunes the same way.
        val seenOfOthers: Map<String, Map<Int, Card>> = player.opponentKnowledge.orEmpty()
            .mapValues { (ownerId, knowledge) ->
                val owner = state.players.firstOrNull { it.id == ownerId }
                knowledge.knownCards.filterKeys { it in (owner?.cards?.indices ?: IntRange.EMPTY) }
            }

        return BotDecisionContext(
            botId = player.id,
            botPlayer = player,
            allPlayers = state.players,
            gameState = state,
            discardTop = state.discardPile.peekTop(),
            discardPile = state.discardPile,
            pendingCard = state.pendingAction?.card,
            activeActionCard = state.pendingAction?.card,
            currentAction = currentAction,
            opponentKnowledge = seenOfOthers + (player.id to ownKnowledge),
            coalitionLeaderId = state.coalitionLeaderId,
            isCoalitionMember = state.vintoCallerId != null && state.vintoCallerId != player.id,
            opponentModeler = opponentModeler,
        )
    }

    private companion object {
        const val SETUP_PEEKS = 2
    }
}
