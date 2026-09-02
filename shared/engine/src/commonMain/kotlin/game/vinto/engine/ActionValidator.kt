package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload

sealed interface Validation {
    data object Valid : Validation
    data class Invalid(val reason: String) : Validation
}

/**
 * Legality checks, run before any handler.
 *
 * This is the anti-cheat boundary. Design D9 makes the server authoritative precisely so that
 * hidden information lives only here and a client cannot ask for what it should not see — and
 * that argument is worth nothing unless this function is right. It runs on every action the
 * Durable Object accepts.
 *
 * Ported from `legacy-web/packages/engine/src/lib/action-validator.ts`. The corpus cannot check it: every
 * recorded action was legal when it was written, so a validator that returned `Valid`
 * unconditionally would replay all 13,900 of them identically. It is covered by its own tests
 * instead.
 */
object ActionValidator {

    fun validate(state: GameState, action: GameAction): Validation =
        state.requirePhaseAllows(action) ?: validateInPhase(state, action)

    /**
     * The phase gate, which the per-action rules below do not provide.
     *
     * Each rule checks whose turn it is and which *sub*-phase the turn is in, and the
     * TypeScript does the same — so `DRAW_CARD` passes during setup and again after scoring,
     * because in both the sub-phase is `idle` and seat zero is nominally on turn. That was
     * never reachable there: the only caller was a UI that does not draw a card it has no
     * button for. It is reachable here. A Durable Object accepts whatever a socket sends, so
     * a client could deal itself a pending card before anyone had peeked, or keep playing a
     * round that had already been scored.
     *
     * Setup admits only the two setup actions; scoring admits nothing at all. The debug hooks
     * stay open in every phase — a harness uses them to arrange a position before play, which
     * is the one legitimate reason to act outside a turn.
     */
    private fun GameState.requirePhaseAllows(action: GameAction): Validation? = when {
        action is GameAction.SetNextDrawCard || action is GameAction.SwapHandWithDeck -> null

        phase == GamePhase.SETUP && !action.isSetupAction() ->
            Validation.Invalid("Cannot ${action.label()} during setup")

        phase != GamePhase.SETUP && action.isSetupAction() ->
            Validation.Invalid("Not in setup phase")

        phase == GamePhase.SCORING ->
            Validation.Invalid("Cannot ${action.label()} once the round has been scored")

        else -> null
    }

    private fun GameAction.isSetupAction(): Boolean =
        this is GameAction.PeekSetupCard || this is GameAction.FinishSetup

    /** The action's own name, for a refusal a player can read. */
    private fun GameAction.label(): String = this::class.simpleName ?: "act"

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun validateInPhase(state: GameState, action: GameAction): Validation = when (action) {
        is GameAction.DrawCard -> state.requireTurn(action.payload.playerId, "DRAW_CARD")
            ?: state.requireSubPhase(GameSubPhase.IDLE, GameSubPhase.AI_THINKING) {
                "Cannot draw in phase ${state.subPhase.serialName}"
            }
            ?: when {
                state.drawPile.size == 0 -> Validation.Invalid("Draw pile is empty")
                else -> Validation.Valid
            }

        is GameAction.SwapCard -> state.requireTurn(action.payload.playerId, "SWAP_CARD")
            ?: state.requireSubPhase(GameSubPhase.CHOOSING) {
                "Cannot swap in phase ${state.subPhase.serialName}"
            }
            ?: when {
                state.pendingAction == null -> Validation.Invalid("No pending action")
                action.payload.position !in state.currentPlayer().cards.indices ->
                    Validation.Invalid("Invalid position ${action.payload.position}")

                else -> Validation.Valid
            }

        is GameAction.DiscardCard -> state.requireTurn(action.payload.playerId, "DISCARD_CARD")
            ?: state.requireSubPhase(GameSubPhase.SELECTING, GameSubPhase.CHOOSING) {
                "Cannot discard in phase ${state.subPhase.serialName}"
            }
            ?: Validation.Valid

        is GameAction.PlayDiscard -> state.requireTurn(action.payload.playerId, "PLAY_DISCARD")
            ?: state.requireSubPhase(GameSubPhase.IDLE, GameSubPhase.AI_THINKING) {
                "Cannot take discard in phase ${state.subPhase.serialName}"
            }
            ?: run {
                // "Allowed only if the top discard is an unused action card (7-K, A)", and
                // the player must then play it. Neither this nor the TypeScript enforced it,
                // which left a client able to lift a Joker — the best card in the game — off
                // the pile, or to re-play an action somebody had already spent. The
                // TypeScript's own test for it is an empty stub saying the implementation
                // "might not validate this strictly".
                val top = state.discardPile.peekTop()
                when {
                    top == null -> Validation.Invalid("Discard pile is empty")
                    top.actionText.isNullOrEmpty() ->
                        Validation.Invalid("Cannot take ${top.rank.serialName} from discard: no action")

                    top.played ->
                        Validation.Invalid("Cannot take ${top.rank.serialName} from discard: already played")

                    else -> Validation.Valid
                }
            }

        is GameAction.UseCardAction -> state.requireTurn(action.payload.playerId, "USE_CARD_ACTION")
            ?: state.requireSubPhase(GameSubPhase.SELECTING, GameSubPhase.CHOOSING) {
                "Cannot use card action in phase ${state.subPhase.serialName}"
            }
            ?: run {
                val pending = state.pendingAction
                when {
                    pending == null -> Validation.Invalid("No card to use action from")
                    pending.card.played -> Validation.Invalid("Card has already been played")
                    else -> Validation.Valid
                }
            }

        is GameAction.SelectActionTarget -> validateSelectActionTarget(state, action)

        is GameAction.ConfirmPeek -> state.requireActor(action.payload.playerId, "CONFIRM_PEEK")
            ?: state.requireSubPhase(GameSubPhase.AWAITING_ACTION, GameSubPhase.SELECTING) {
                "Cannot confirm peek in phase ${state.subPhase.serialName}"
            }
            ?: Validation.Valid

        is GameAction.SkipPeek -> state.requireActor(action.payload.playerId, "SKIP_PEEK")
            ?: state.requireSubPhase(GameSubPhase.AWAITING_ACTION, GameSubPhase.SELECTING) {
                "Cannot confirm peek in phase ${state.subPhase.serialName}"
            }
            ?: Validation.Valid

        is GameAction.CallVinto -> {
            // Vinto is declared at the end of one's own turn. During a toss-in window
            // `currentPlayerIndex` can rest on a toss-in actor while the queue drains, so
            // there the window's owner — `originalPlayerIndex`'s seat — is the one test,
            // *replacing* the turn check rather than adding to it: requiring both refused
            // everybody, the toss-in actor on ownership and the rightful owner on turn,
            // and a table where a bot had tossed into the owner's window could never call.
            val owner = state.activeTossIn?.let {
                state.players.getOrNull(it.originalPlayerIndex)?.id
            }
            when {
                owner == null -> state.requireTurn(action.payload.playerId, "CALL_VINTO")
                owner != action.payload.playerId -> Validation.Invalid(
                    "Vinto can only be called at the end of your own turn",
                )

                else -> null
            }
                ?: if (state.vintoCallerId != null) {
                    Validation.Invalid("Vinto already called")
                } else {
                    Validation.Valid
                }
        }

        is GameAction.ExecuteJackSwap ->
            validateTwoTargetSwap(state, action.payload.playerId, "EXECUTE_JACK_SWAP", "Jack")

        is GameAction.SkipJackSwap ->
            validateTwoTargetSwap(state, action.payload.playerId, "SKIP_JACK_SWAP", "Jack")

        is GameAction.ExecuteQueenSwap ->
            validateTwoTargetSwap(state, action.payload.playerId, "EXECUTE_QUEEN_SWAP", "Queen")

        is GameAction.SkipQueenSwap ->
            validateTwoTargetSwap(state, action.payload.playerId, "SKIP_QUEEN_SWAP", "Queen")

        is GameAction.DeclareKingAction -> validateDeclareKing(state, action)

        is GameAction.ParticipateInTossIn -> validateParticipateInTossIn(state, action)

        is GameAction.PlayerTossInFinished -> {
            val playerId = action.payload.playerId
            val tossIn = state.activeTossIn
            when {
                tossIn == null -> Validation.Invalid("No active toss-in")
                state.playerById(playerId) == null -> Validation.Invalid("Player not found")
                tossIn.playersReadyForNextTurn.contains(playerId) ->
                    Validation.Invalid("Player already confirmed ready for next turn")

                else -> Validation.Valid
            }
        }

        is GameAction.FinishTossInPeriod ->
            state.requireSubPhase(
                GameSubPhase.TOSS_QUEUE_ACTIVE,
                GameSubPhase.TOSS_QUEUE_PROCESSING,
            ) { "Cannot finish toss-in during phase ${state.subPhase.serialName}" }
                ?: run {
                    val tossIn = state.activeTossIn
                    when {
                        tossIn == null -> Validation.Invalid("No active toss-in")
                        tossIn.initiatorId != action.payload.initiatorId ->
                            Validation.Invalid("Only toss-in initiator can finish the period")

                        else -> Validation.Valid
                    }
                }

        is GameAction.SetCoalitionLeader -> {
            val leader = state.playerById(action.payload.leaderId)
            when {
                state.phase != GamePhase.FINAL ->
                    Validation.Invalid("Coalition leader can only be set in final phase")

                state.vintoCallerId == null ->
                    Validation.Invalid("No Vinto caller to form coalition against")

                leader == null -> Validation.Invalid("Leader player not found")
                leader.id == state.vintoCallerId ->
                    Validation.Invalid("Vinto caller cannot be coalition leader")

                else -> Validation.Valid
            }
        }

        is GameAction.DeclareCards -> {
            // Table talk from a coalition member: legal in any sub-phase of the final round,
            // never on the caller's behalf, and — deliberately — never compared against the
            // real cards. A claim can be wrong; being wrong is a memory problem, not a rules
            // problem.
            val player = state.playerById(action.payload.playerId)
            when {
                state.phase != GamePhase.FINAL ->
                    Validation.Invalid("Cards can only be declared in the final round")

                player == null -> Validation.Invalid("Player not found")
                player.id == state.vintoCallerId ->
                    Validation.Invalid("The Vinto caller has no coalition to talk to")

                action.payload.claims.isEmpty() -> Validation.Invalid("No claims provided")
                else -> action.payload.claims.keys.firstOrNull { it !in player.cards.indices }
                    ?.let { Validation.Invalid("Invalid card position $it") }
                    ?: Validation.Valid
            }
        }

        is GameAction.ProcessAiTurn -> {
            val player = state.playerById(action.payload.playerId)
            when {
                player == null -> Validation.Invalid("Player not found")
                !player.isBot -> Validation.Invalid("Player is not a bot")
                else -> state.requireTurn(action.payload.playerId, "PROCESS_AI_TURN")
                    ?: Validation.Valid
            }
        }

        is GameAction.PeekSetupCard -> {
            val player = state.playerById(action.payload.playerId)
            when {
                state.phase != GamePhase.SETUP -> Validation.Invalid("Not in setup phase")
                player == null -> Validation.Invalid("Player not found")
                action.payload.position !in player.cards.indices ->
                    Validation.Invalid("Invalid card position ${action.payload.position}")

                player.knownCardPositions.contains(action.payload.position) ->
                    Validation.Invalid("Card already peeked")

                else -> Validation.Valid
            }
        }

        // Finishing setup starts the round for the whole table, so it takes the whole table
        // being ready — not merely whoever pressed the button. In TypeScript the two were the
        // same thing, because only one player was ever a person and the bots are dealt their
        // peeks. In a room with four people it is the difference between everyone seeing two
        // of their cards and the quickest player starting the game over the others.
        is GameAction.FinishSetup -> {
            val player = state.playerById(action.payload.playerId)
            val unready = state.players.filter { it.knownCardPositions.size < SETUP_PEEKS_REQUIRED }
            when {
                state.phase != GamePhase.SETUP -> Validation.Invalid("Not in setup phase")
                player == null -> Validation.Invalid("Player not found")
                // A count, not a list of names. The engine's names come from the deal, so in
                // a room the seat a person occupies still carries whichever name that index
                // was dealt — naming them in a refusal would tell a player they are waiting
                // for somebody who is not there.
                unready.isNotEmpty() -> Validation.Invalid(
                    "Every player must peek at $SETUP_PEEKS_REQUIRED cards before the round " +
                        "starts (waiting for ${unready.size})",
                )

                else -> Validation.Valid
            }
        }

        /**
         * Only when the round truly cannot go on.
         *
         * Gated tightly because it is the one action that ends a round without anybody
         * winning it: the final round, an empty deck, nothing takeable on the discard, and no
         * action still in flight. In that position the player on turn has no legal move — no
         * draw, no take, no pass — and without this the game stops with everyone waiting for
         * everyone. Outside the final round a player in the same position calls Vinto, which
         * is legal and finite, so this stays unavailable there.
         */
        is GameAction.EndRound -> when {
            state.phase != GamePhase.FINAL -> Validation.Invalid("The round can still be played")
            state.drawPile.size > 0 -> Validation.Invalid("There are still cards to draw")
            state.pendingAction != null -> Validation.Invalid("There is an action to finish")
            state.discardPile.peekTop()?.let { it.actionText != null && !it.played } == true ->
                Validation.Invalid("The discard can still be taken")

            else -> Validation.Valid
        }

        // Configuration and debug actions carry no legality conditions.
        is GameAction.Empty,
        is GameAction.UpdateDifficulty,
        is GameAction.SetNextDrawCard,
        is GameAction.SwapHandWithDeck,
        -> Validation.Valid
    }

    /** Players peek at exactly two of their own cards before play begins. */
    private const val SETUP_PEEKS_REQUIRED = 2

    private fun validateSelectActionTarget(
        state: GameState,
        action: GameAction.SelectActionTarget,
    ): Validation {
        val payload = action.payload
        state.requireActor(payload.playerId, "SELECT_ACTION_TARGET")?.let { return it }
        state.requireSubPhase(GameSubPhase.AWAITING_ACTION, GameSubPhase.SELECTING) {
            "Cannot select target in phase ${state.subPhase.serialName}"
        }?.let { return it }

        val pending = state.pendingAction
            ?: return Validation.Invalid("No pending action to add target to")
        val targetPlayer = state.playerById(payload.targetPlayerId)
            ?: return Validation.Invalid("Target player not found")

        // Coalition rule: during the final round the coalition may not touch the Vinto
        // caller's cards. This is the rule that makes calling Vinto a commitment rather than
        // a free option, so it is enforced here rather than left to the UI. The guard holds
        // from the moment Vinto is called — choosing a coalition leader is optional and the
        // caller's protection must not wait for it.
        if (state.phase == GamePhase.FINAL && state.vintoCallerId != null) {
            val actor =
                if (state.isProcessingTossInAction()) {
                    state.playerById(pending.playerId)
                } else {
                    state.players.getOrNull(state.currentPlayerIndex)
                }

            if (actor != null && actor.id != state.vintoCallerId &&
                payload.targetPlayerId == state.vintoCallerId
            ) {
                return Validation.Invalid(
                    "Coalition members cannot target Vinto caller with actions",
                )
            }
        }

        if (payload is SelectActionTargetPayload.Positional &&
            payload.position !in targetPlayer.cards.indices
        ) {
            return Validation.Invalid("Invalid position ${payload.position} for target player")
        }

        // Jack and Queen each take two cards, and they must belong to different players.
        if (pending.card.rank == Rank.JACK || pending.card.rank == Rank.QUEEN) {
            val first = pending.targets.singleOrNull()
            if (first != null && first.playerId == payload.targetPlayerId) {
                return Validation.Invalid(
                    "Jack and Queen must target cards from different players",
                )
            }
        }

        return Validation.Valid
    }

    /**
     * Jack and Queen share every condition at execution time; only the name in the message
     * differs. TypeScript duplicated this block, and the copy reported "Jack" from the Queen
     * branch — harmless, but the sort of thing that sends someone debugging the wrong card.
     */
    private fun validateTwoTargetSwap(
        state: GameState,
        playerId: String,
        actionType: String,
        cardName: String,
    ): Validation {
        state.requireActor(playerId, actionType)?.let { return it }
        // `selecting` is where a bot sits while it works through a tossed-in action card —
        // the same allowance CONFIRM_PEEK, DECLARE_KING_ACTION and SELECT_ACTION_TARGET all
        // make. The TypeScript omits it on these two alone, which leaves a bot that tossed in
        // a Jack or Queen with targets chosen and no legal way to finish. See the deviation
        // note in docs/kotlin/GATES.md §6e.
        state.requireSubPhase(GameSubPhase.AWAITING_ACTION, GameSubPhase.SELECTING) {
            "Cannot execute $cardName action in phase ${state.subPhase.serialName}"
        }?.let { return it }

        val pending = state.pendingAction ?: return Validation.Invalid("No pending action")

        if (pending.targets.size != 2) {
            return Validation.Invalid(
                "$cardName action requires 2 targets, got ${pending.targets.size}",
            )
        }
        if (pending.targets[0].playerId == pending.targets[1].playerId) {
            return Validation.Invalid(
                "$cardName action requires 2 different players, " +
                    "got same ${pending.targets[0].playerId}",
            )
        }

        // Coalition validity was settled in SELECT_ACTION_TARGET, when the targets were chosen.
        return Validation.Valid
    }

    private fun validateDeclareKing(
        state: GameState,
        action: GameAction.DeclareKingAction,
    ): Validation {
        state.requireActor(action.payload.playerId, "DECLARE_KING_ACTION")?.let { return it }
        state.requireSubPhase(GameSubPhase.AWAITING_ACTION, GameSubPhase.SELECTING) {
            "Cannot declare King action in phase ${state.subPhase.serialName}"
        }?.let { return it }

        val pending = state.pendingAction ?: return Validation.Invalid("No pending King card")
        pendingIsADeclarableKing(pending)?.let { return it }

        val target = pending.targets.firstOrNull()
            ?: return Validation.Invalid("Target player not found")
        val targetPlayer = state.playerById(target.playerId)
            ?: return Validation.Invalid("Target player not found")

        if (target.position !in targetPlayer.cards.indices) {
            return Validation.Invalid("Invalid position ${target.position} for target player")
        }

        return Validation.Valid
    }

    /** The pending card must be a King that has already chosen its target. */
    private fun pendingIsADeclarableKing(pending: PendingAction): Validation? = when {
        pending.card.rank != Rank.KING -> Validation.Invalid("Pending card is not a King")
        pending.actionPhase != ActionPhase.SELECTING_TARGET -> Validation.Invalid(
            "Cannot declare rank in action phase ${pending.actionPhase.serialName}",
        )

        else -> null
    }

    private fun validateParticipateInTossIn(
        state: GameState,
        action: GameAction.ParticipateInTossIn,
    ): Validation {
        state.requireSubPhase(
            GameSubPhase.TOSS_QUEUE_ACTIVE,
            GameSubPhase.TOSS_QUEUE_PROCESSING,
        ) { "Cannot toss in during phase ${state.subPhase.serialName}" }?.let { return it }

        if (state.activeTossIn == null) return Validation.Invalid("No active toss-in")

        val player = state.playerById(action.payload.playerId)
            ?: return Validation.Invalid("Player not found")

        // The Vinto caller's hand is frozen from the moment of the call: the coalition may
        // not touch it, and the caller may not slim it down through toss-ins either.
        if (state.vintoCallerId == action.payload.playerId) {
            return Validation.Invalid("Vinto caller cannot participate in toss-in")
        }

        val positions = action.payload.positions
        if (positions.isEmpty()) return Validation.Invalid("No positions provided")
        positions.firstOrNull { it !in player.cards.indices }?.let {
            return Validation.Invalid("Invalid card position $it")
        }

        // One wrong toss-in ends a player's participation in **that window** — and, in the
        // final round only, in the rest of the round. See `isBarredFromTossIn`.
        if (isBarredFromTossIn(state, action.payload.playerId)) {
            return Validation.Invalid("Cannot participate in toss-in after failed attempt")
        }

        return Validation.Valid
    }

    // --- shared conditions -------------------------------------------------------------

    private fun GameState.currentPlayer(): PlayerState = players[currentPlayerIndex]

    private fun GameState.playerById(id: String): PlayerState? = players.firstOrNull { it.id == id }

    /** True while queued toss-in actions are being worked through. */
    private fun GameState.isProcessingTossInAction(): Boolean =
        activeTossIn?.queuedActions?.isNotEmpty() == true

    /** Null when the condition holds, so callers can chain with `?:`. */
    private fun GameState.requireTurn(playerId: String, actionType: String): Validation? =
        if (currentPlayer().id == playerId) {
            null
        } else {
            Validation.Invalid("Not player turn for $actionType")
        }

    /**
     * Who is allowed to act right now.
     *
     * While queued toss-in actions are resolving, the actor is whoever owns the pending
     * action rather than whoever's turn it is — a toss-in lets another player act out of
     * turn, which is the whole point of the mechanic.
     */
    private fun GameState.requireActor(playerId: String, actionType: String): Validation? =
        if (isProcessingTossInAction()) {
            if (pendingAction?.playerId == playerId) {
                null
            } else {
                Validation.Invalid("Not your toss-in action")
            }
        } else {
            requireTurn(playerId, actionType)
        }

    private inline fun GameState.requireSubPhase(
        vararg allowed: GameSubPhase,
        reason: () -> String,
    ): Validation? = if (subPhase in allowed) null else Validation.Invalid(reason())
}
