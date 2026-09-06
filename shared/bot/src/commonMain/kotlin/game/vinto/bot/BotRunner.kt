package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.isBarredFromTossIn
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
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
import game.vinto.shapes.TableTalk
import game.vinto.shapes.actorId
import game.vinto.shapes.believedAt
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
    /**
     * The difficulty each seat plays at — the one difficulty for every seat unless a caller
     * says otherwise. A mixed table is how the difficulties are ranked against each other
     * (`TournamentTest`); a game only ever has one.
     */
    private val difficultyFor: (String) -> Difficulty = { difficulty },
) {
    private val services = mutableMapOf<String, BotDecisionService>()

    /**
     * Beliefs inferred from watching everybody play — shared across the seats this runner
     * drives, because everything in it is public information: anyone at the table saw the
     * same discard, the same swap, the same toss-in. Fed by [observe], reset when the deal
     * changes, and handed to every decision through the context.
     */
    private val opponentModeler = OpponentModeler()
    private var modelerGameId: String? = null

    /** Turn number a bot last spoke on, so it speaks once per turn and not once per question. */
    private val spokenOn = mutableMapOf<String, Int>()

    private fun serviceFor(botId: String): BotDecisionService =
        services.getOrPut(botId) { serviceFactory(difficultyFor(botId), random) }

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
     * Each coalition bot says out loud what it believes its own cards are — one
     * `DECLARE_CARDS` per bot, in seat order, before any final-round play.
     *
     * This is what coordinates the coalition, and it is why there is no leader to nominate:
     * by the time anybody takes a turn every bot's read cards are public claims, so the three
     * planners reach the same target from the same picture. `nextAction` will not let a bot
     * play while one still owes a declaration, which is what makes that true rather than
     * likely.
     *
     * The claims come from the bot's **memory**, not the engine's record, so on lower
     * difficulties they can be wrong — which is the model: the coalition plans from what was
     * said at the table, not from anyone's real hand.
     *
     * And the memory's *grade* is kept rather than flattened. `CardMemory` has carried a
     * per-card `confidence` all along and `believedOwnCards` threw it away at the moment of
     * speaking, so a bot that was half sure asserted an arrangement anyway. Three bands now:
     *
     *  - **sure** — an exact claim, one per position;
     *  - **half sure of exactly two** — one claim naming both positions and both ranks, with
     *    the order left open. That is the honest reading of two middling confidences: the
     *    ranks are probably right, the pairing is a coin toss;
     *  - **decayed** — nothing. Silence is a better teammate than a confident guess.
     *
     * A bot also says what it has seen of **other** hands, the caller's included. That closes
     * the one thing the bots used to share that a person could not say: the planner pooled
     * every seat's private sighting of the caller, and now it reads claims like everyone else.
     */
    private fun coalitionDeclarationAction(state: GameState): GameAction? {
        if (state.phase != GamePhase.FINAL || state.vintoCallerId == null) return null
        val speakers = state.players.filter { it.isBot && it.id != state.vintoCallerId }
        return speakers.firstNotNullOfOrNull { ownHandAction(state, it) }
            ?: speakers.firstNotNullOfOrNull { seenElsewhereAction(state, it) }
            ?: speakers.firstNotNullOfOrNull { contradictionAction(state, it) }
    }

    /**
     * A bot answers a contradiction from its confidence (design D3b).
     *
     * Somebody has said something about one of this bot's own cards that cannot square with
     * what the bot said. The bot never consults the real card — that would make it an oracle
     * rather than a teammate — it consults its **memory's grade**: where the card is still
     * held above [TRUSTED_CONFIDENCE] it stands, and says nothing more; where the memory has
     * decayed it takes that claim back.
     *
     * **Whether** it answers is a fact about the state; **what** it answers is memory's. A
     * contradiction is new while somebody else's claim on the card stands *after* the bot's
     * own — a declaration is appended, so the order of the standing claims is the order of the
     * conversation — and the bot answers it once, with a declaration either way: standing is
     * saying the same thing again, which moves its word to the end and closes the exchange;
     * letting go is saying the card could be **any rank**, which replaces its earlier word and
     * disputes nothing, since belief reads past it (`Claim.vacuous`). Both leave a trace, so
     * two runners with different memories agree on who still owes an answer, and neither can
     * be asked twice.
     *
     * "My hand minus that card" was the first shape of letting go and it looped: a declaration
     * replaces only the earlier claims that overlap it, so the disputed word stayed standing and
     * this answered it on every call — the stall three whole-game suites found.
     *
     * Only the bot's own hand consults confidence. What it has said of other hands came off
     * the engine's own record of a peek (`opponentKnowledge`), which does not decay, so there
     * it stands.
     */
    private fun contradictionAction(state: GameState, player: PlayerState): GameAction? {
        if (player.claims.orEmpty().none { it.by == player.id }) return null

        val unanswered = player.cards.indices.filter { position ->
            val believed = believedAt(player, position)
            val mine = believed.sources.indexOfLast { it.by == player.id }
            val theirs = believed.sources.indexOfLast { it.by != player.id }
            believed.disputed && mine >= 0 && mine < theirs
        }
        if (unanswered.isEmpty()) return null

        val graded = serviceFor(player.id).gradedOwnCards(buildContext(state, player))
        val answer = unanswered.map { position ->
            val mine = believedAt(player, position).sources.last { it.by == player.id }
            val confidence = graded[position]?.second ?: 0.0
            if (confidence <= TRUSTED_CONFIDENCE) {
                // `covering = false`: one card that is *one of* every rank, not fourteen cards.
                Claim(player.id, listOf(position), ALL_RANKS, covering = false)
            } else {
                mine
            }
        }.distinct()
        return GameAction.DeclareCards(DeclareCardsPayload(player.id, player.id, answer))
    }

    /** A bot's own hand, said once. */
    private fun ownHandAction(state: GameState, player: PlayerState): GameAction? {
        // Whether a bot speaks at all is decided by *state* — it has read positions and has
        // not spoken — never by what its memory happens to hold. Two runners looking at the
        // same table must agree on who still owes a declaration (`FinishesTest` drives the
        // human seat with a second runner and relies on it); only the content is memory's.
        if (player.knownCardPositions.isEmpty()) return null
        if (player.claims.orEmpty().any { it.by == player.id }) return null

        // Always *something*, so that whether this bot still owes a declaration stays a fact
        // about the state. Where memory has nothing to say for a card it read, the bot says the
        // card could be any rank — a claim belief reads past (`Claim.vacuous`) that still marks
        // the hand as spoken for. A silent bot would owe its declaration forever, and a second
        // runner with a different memory would keep proposing it (`FinishesTest`).
        val claims = ownClaims(state, player).ifEmpty {
            player.knownCardPositions
                .filter { it in player.cards.indices }
                .map { Claim(player.id, listOf(it), ALL_RANKS, covering = false) }
        }
        return claims.takeIf { it.isNotEmpty() }?.let {
            GameAction.DeclareCards(DeclareCardsPayload(player.id, player.id, it))
        }
    }

    /** Its own hand said, a bot passes on what it has seen of everybody else's. */
    private fun seenElsewhereAction(state: GameState, player: PlayerState): GameAction? =
        state.players.asSequence()
            .filter { it.id != player.id }
            .filterNot { other -> other.claims.orEmpty().any { it.by == player.id } }
            .mapNotNull { other ->
                seenClaims(player, other).takeIf { it.isNotEmpty() }?.let {
                    GameAction.DeclareCards(DeclareCardsPayload(player.id, other.id, it))
                }
            }
            .firstOrNull()

    /**
     * What a bot makes of a suggestion addressed to it.
     *
     * It runs the move through its **own** planner and answers: performs it if the line is
     * within reach of the one it had, declines with a reason if it is not. Nothing here is
     * obliged — a bot that must obey is not a teammate, and the move it makes when it agrees
     * is its own, seat-bound like any other.
     *
     * "Within reach" rather than "better", deliberately. A teammate who can only be persuaded
     * by a strictly superior idea is one nobody bothers talking to, and the coalition's whole
     * problem is coordination rather than optimisation: three members agreeing on a decent
     * line beat three members each playing their own best one.
     */
    fun answerTo(state: GameState, proposal: TableTalk.Proposal): Pair<GameAction?, TableTalk> {
        val player = state.players.firstOrNull { it.id == proposal.to }
        val no = TableTalk.Answer(proposal.to, proposal.by, TableTalk.Answer.Says.NO)
        if (player == null || !player.isBot) return null to no

        // A suggestion may only ever be a move for the seat it is addressed to. Without this,
        // `Proposal(to = B, move = DrawCard(C))` would have B agree and the room play C's move
        // — one seat driving another through a third's consent, which is the impersonation the
        // whole "propose, never command" rule exists to refuse. The seat check downstream
        // would not catch it, because the room does not seat-check its own bots.
        if (proposal.move.actorId != null && proposal.move.actorId != proposal.to) {
            return null to no
        }

        if (ActionValidator.validate(state, proposal.move) is Validation.Invalid) {
            return null to TableTalk.Answer(
                proposal.to,
                proposal.by,
                TableTalk.Answer.Says.THAT_LEAVES_US_WORSE,
            )
        }

        val mine = nextAction(state)
        val agreeable = mine == null || worthIt(state, proposal.move, mine)
        return if (agreeable) {
            proposal.move to TableTalk.Answer(proposal.to, proposal.by, TableTalk.Answer.Says.YES)
        } else {
            null to TableTalk.Answer(
                proposal.to,
                proposal.by,
                TableTalk.Answer.Says.THAT_LEAVES_US_WORSE,
            )
        }
    }

    /**
     * Whether a suggested move leaves the coalition no worse than the bot's own line.
     *
     * Measured on the thing the round is actually scored on — the lowest coalition hand — so a
     * suggestion that keeps it where it was is agreed to. Anything the plan cannot evaluate is
     * agreed to as well: an unmeasurable difference is not a reason to refuse a teammate.
     */
    private fun worthIt(state: GameState, suggested: GameAction, own: GameAction): Boolean {
        fun bestHandAfter(action: GameAction): Int? {
            val next = (GameEngine.reduce(state, action) as? ReduceResult.Success)?.state ?: return null
            val input = buildCoalitionPlanInput(next, action.actorId ?: return null) ?: return null
            return input.members.minOfOrNull { handScore(it.cards) }
        }

        val theirs = bestHandAfter(suggested) ?: return true
        val ours = bestHandAfter(own) ?: return true
        return theirs <= ours
    }

    /** What [player] will say about its own hand, in the three bands above. */
    private fun ownClaims(state: GameState, player: PlayerState): List<Claim> {
        val graded = serviceFor(player.id)
            .gradedOwnCards(buildContext(state, player))
            .filterKeys { it in player.cards.indices }

        // Memory come up empty for a hand the table watched this bot read is a hand the bot has
        // forgotten, and it says nothing. It used to fall back to the engine's record of the
        // seat's peeks and declare the real cards — an oracle wearing a teammate's face, and
        // the one bot at the table whose claims were always right was the one with no memory
        // at all. Silence is what design D3a asks for past the hazy floor, and it is what a
        // person with the same memory would offer.
        val sure = graded.filterValues { it.second > TRUSTED_CONFIDENCE }
        val hazy = graded.filterValues { it.second in HAZY_CONFIDENCE..TRUSTED_CONFIDENCE }

        val exact = sure.map { (position, belief) ->
            Claim(player.id, listOf(position), listOf(belief.first))
        }
        // Exactly two half-remembered cards is the case a pair claim is for. Three or more and
        // there is no pairing worth stating; one on its own is simply not worth saying.
        val pair = hazy.takeIf { it.size == 2 }?.let { two ->
            val positions = two.keys.sorted()
            listOf(Claim(player.id, positions, positions.map { two.getValue(it).first }))
        }.orEmpty()

        return exact + pair
    }

    /** What [player] has seen of [other]'s hand, said out loud so the plan may use it. */
    private fun seenClaims(player: PlayerState, other: PlayerState): List<Claim> =
        player.opponentKnowledge?.get(other.id)?.knownCards.orEmpty()
            .filterKeys { it in other.cards.indices }
            .map { (position, card) -> Claim(player.id, listOf(position), listOf(card.rank)) }

    /**
     * One thing a bot has to say, or nothing.
     *
     * Separate from [nextAction] because talk is not a move: it changes no state, reaches no
     * recording and touches no hash (design D6). The runner offers it and the session carries
     * it; nothing waits on it.
     *
     * **At most one unprompted sentence per bot per turn.** Three planning bots with nothing
     * holding them back would narrate every turn, and a strip nobody reads is worse than a
     * quiet one. The number is a feel judgement and expected to move once somebody plays it.
     *
     * **A bot that has called Vinto says nothing at all.** It cannot act again, so anything it
     * said would be information or a bluff — and a bot has no model of when to bluff. One
     * claiming a 4 while holding 12 is lying to the player, which is a different contract from
     * its honest-but-fallible declarations to its own coalition. Silence is the only honest
     * default, and it reads correctly: the caller said their piece by calling.
     */
    fun nextTalk(state: GameState): TableTalk? {
        if (state.phase != GamePhase.FINAL) return null
        val callerId = state.vintoCallerId ?: return null

        for (player in state.players) {
            if (!player.isBot || player.id == callerId) continue
            if (spokenOn[player.id] == state.turnNumber) continue
            val sentence = whatItWouldSay(state, player) ?: continue
            spokenOn[player.id] = state.turnNumber
            return sentence
        }
        return null
    }

    /**
     * What a coalition bot thinks worth saying, in the order it is worth saying it.
     *
     * Both sentences come off things the plan already knows, so neither costs a search: where
     * this hand stands against the others, and whether it holds something it could shed into
     * a toss-in — which is the cheapest way to lower a hand in the game and costs no turn.
     */
    private fun whatItWouldSay(state: GameState, player: PlayerState): TableTalk? {
        val input = buildCoalitionPlanInput(state, player.id) ?: return null
        val hands = input.members.map { it.id to handScore(it.cards) }
        val mine = hands.firstOrNull { it.first == player.id }?.second ?: return null
        val best = hands.minOfOrNull { it.second } ?: return null
        val worst = hands.maxOfOrNull { it.second } ?: return null

        val shed = input.members
            .first { it.id == player.id }
            .cards
            .firstOrNull { shouldTossCard(it) }

        return when {
            mine == best && best < worst ->
                askForACard(input, player.id) ?: TableTalk.Standing(player.id, TableTalk.Standing.Where.LOW)

            shed != null -> TableTalk.WillShed(player.id, shed.rank)
            mine == worst && best < worst -> TableTalk.Standing(player.id, TableTalk.Standing.Where.BIN)
            else -> null
        }
    }

    /**
     * The card this bot's plan wants out of a teammate's hand, asked for out loud.
     *
     * Only the lowest coalition hand is compared, so the play is to concentrate the good cards
     * in one hand and dump into the others — and when *this* hand is the low one, the useful
     * thing to say is which card would improve it.
     *
     * **A concrete move for a teammate's turn is not askable, and that is a fact about the
     * game rather than a gap here.** Their turn opens with a draw nobody can predict, so the
     * only move anyone could name for it would be one that ignores what they draw. What the
     * plan does know is *which cards should end up where*, so that is what gets said. The
     * exchange still runs through the same rule as everything else: a request is a request,
     * and the teammate's move remains their own.
     *
     * Computed from the shared picture only — claims and this bot's own read cards — never
     * from a teammate's private knowledge. Building a plan "as" them would read their reads,
     * which is precisely the back channel the claim model exists to close.
     */
    private fun askForACard(input: CoalitionPlanInput, me: String): TableTalk? {
        // The same measure the board's proposals use — the coalition's lowest hand after the
        // trade — restricted to trades that bring a card into this hand.
        val hands = input.members.associate { it.id to it.cards }
        val swap = bestSwap(hands, input.members.map { it.id }) ?: return null
        val theirs = when (me) {
            swap.from.seat -> swap.to
            swap.to.seat -> swap.from
            else -> return null
        }
        return TableTalk.GiveMe(me, theirs.seat, theirs.position)
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

            // The victim draws; there is no position to name. In the final round every
            // possible victim is a teammate — the caller is out of reach — so a coalition
            // bot's Ace (a tossed-in one; the planner never plays one from hand) is put down
            // unaimed rather than forced on the one hand that might still win.
            Rank.ACE -> if (coalition != null) abandonAction(player) else aceTarget(state, player, plan)

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
            isCoalitionMember = state.vintoCallerId != null && state.vintoCallerId != player.id,
            opponentModeler = opponentModeler,
        )
    }

    private companion object {
        const val SETUP_PEEKS = 2
    }
}
