package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The final round played out end to end, ported from
 * `legacy-web/packages/local-client/src/lib/__tests__/coalition-final-round.test.ts`.
 *
 * [CoalitionPlannerTest] checks the planner finds the right line from a fixed position. This
 * checks the line actually gets played: three bots take real turns through the real engine,
 * every action validated, until the round scores.
 *
 * What these four cases do **not** prove is that the coalition planner is the thing making
 * the decisions: they still pass with every coalition routing in `BotRunner` disabled,
 * because ordinary MCTS wins these positions too. That was checked rather than assumed. The
 * wiring is pinned by [theRunnerFollowsThePlannerAndNotTheSoloService] below, which compares
 * the runner's action against the planner's own answer.
 */
class CoalitionFinalRoundTest {

    private val callerId = "human-1"
    private val botIds = listOf("bot-1", "bot-2", "bot-3")

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun player(id: String, isHuman: Boolean, ranks: List<Rank>): PlayerState = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = isHuman,
        isBot = !isHuman,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        // Every bot has read its own hand.
        knownCardPositions = if (isHuman) emptyList() else ranks.indices.toList(),
        isVintoCaller = id == callerId,
        coalitionWith = if (isHuman) emptyList() else botIds,
        // And has already declared it truthfully — the scenarios pin the *play*, not the
        // declaration step, which CoalitionHumanMemberTest covers on its own.
        declaredCards = if (isHuman) null else ranks.mapIndexed { i, r -> i to r }.toMap(),
    )

    private fun total(player: PlayerState) = player.cards.sumOf { it.value }

    private fun finalRoundState(
        human: List<Rank>,
        bot1: List<Rank>,
        bot2: List<Rank>,
        bot3: List<Rank>,
        drawPile: List<Rank>,
    ) = GameState(
        gameId = "coalition-test",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            player(callerId, isHuman = true, ranks = human),
            player("bot-1", isHuman = false, ranks = bot1),
            player("bot-2", isHuman = false, ranks = bot2),
            player("bot-3", isHuman = false, ranks = bot3),
        ),
        // The caller has had their turn; the coalition plays from bot-1 onwards.
        currentPlayerIndex = 1,
        vintoCallerId = callerId,
        coalitionLeaderId = "bot-1",
        drawPile = Pile(drawPile.mapIndexed { index, rank -> card(rank, "draw-$index") }),
        discardPile = Pile(listOf(card(Rank.FOUR, "discard-seed"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private data class RoundResult(val state: GameState, val actions: Int)

    /** Plays until the round scores or nothing legal is left, refusing anything invalid. */
    private fun playOut(start: GameState): RoundResult {
        val runner = BotRunner(Difficulty.MODERATE, Random(4))
        var state = start
        var actions = 0

        while (actions < 500 && state.phase != GamePhase.SCORING) {
            val action = runner.nextAction(state) ?: break

            when (val validation = ActionValidator.validate(state, action)) {
                is Validation.Invalid -> fail(
                    "action #$actions: the coalition proposed an illegal ${action.type} — " +
                        "${validation.reason} (subPhase=${state.subPhase.serialName})",
                )

                Validation.Valid -> Unit
            }

            state = when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure -> fail("the engine rejected a validated ${action.type}: ${result.reason}")
            }
            actions++
        }

        return RoundResult(state, actions)
    }

    private fun assertCoalitionBeatsTheCaller(state: GameState) {
        val caller = state.players.first { it.id == callerId }
        val best = state.players.filter { it.id != callerId }.minOf { total(it) }

        assertTrue(
            best < total(caller),
            "coalition best was $best against the caller's ${total(caller)}",
        )
    }

    private fun assertCallersCardsUntouched(state: GameState, expected: List<Rank>) {
        assertEquals(
            expected,
            state.players.first { it.id == callerId }.cards.map { it.rank },
            "the coalition interfered with the Vinto caller's hand",
        )
    }

    @Test
    fun theCoalitionMovesAJokerIntoATeammateWithADrawnJack() {
        // Caller is 6. Bot1 is 8, Bot2 is 8, Bot3 is 12 — nobody wins as things stand, and a
        // Jack moving the Joker into either 8-hand puts it under 6.
        val human = listOf(Rank.TWO, Rank.THREE, Rank.ACE)
        val result = playOut(
            finalRoundState(
                human = human,
                bot1 = listOf(Rank.THREE, Rank.FIVE),
                bot2 = listOf(Rank.JOKER, Rank.NINE),
                bot3 = listOf(Rank.SEVEN, Rank.FIVE),
                drawPile = listOf(Rank.JACK, Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR),
            ),
        )

        assertEquals(GamePhase.SCORING, result.state.phase, "the round did not finish")
        assertCoalitionBeatsTheCaller(result.state)
        assertCallersCardsUntouched(result.state, human)
    }

    @Test
    fun theCoalitionShedsCardsThroughTossIns() {
        // Bot1 holds two 5s. Drawing and discarding a 5 opens a window it can empty its own
        // hand into — a line that costs Bot1 nothing and wins the round outright.
        val human = listOf(Rank.TWO, Rank.THREE, Rank.ACE)
        val result = playOut(
            finalRoundState(
                human = human,
                bot1 = listOf(Rank.FIVE, Rank.FIVE),
                bot2 = listOf(Rank.SEVEN, Rank.NINE),
                bot3 = listOf(Rank.NINE, Rank.SEVEN),
                drawPile = listOf(Rank.FIVE, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT, Rank.EIGHT),
            ),
        )

        assertEquals(GamePhase.SCORING, result.state.phase, "the round did not finish")
        assertCoalitionBeatsTheCaller(result.state)
        assertCallersCardsUntouched(result.state, human)
    }

    @Test
    fun aRoundTheCoalitionCannotWinStillFinishesCleanly() {
        // Two Jokers and an Ace is -1. There is no line; the round must still score, and the
        // bots must not flail at the caller's cards trying to find one.
        val human = listOf(Rank.JOKER, Rank.JOKER, Rank.ACE)
        val result = playOut(
            finalRoundState(
                human = human,
                bot1 = listOf(Rank.NINE, Rank.TEN),
                bot2 = listOf(Rank.EIGHT, Rank.NINE),
                bot3 = listOf(Rank.SEVEN, Rank.NINE),
                drawPile = listOf(Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SIX, Rank.SIX, Rank.SIX),
            ),
        )

        assertEquals(GamePhase.SCORING, result.state.phase, "the round did not finish")
        assertCallersCardsUntouched(result.state, human)
        assertEquals(3, result.state.players.first { it.id == callerId }.cards.size)
    }

    @Test
    fun theCallersHandIsNeverTouchedWhateverTheCoalitionDraws() {
        // The rule, swept rather than spot-checked: the same position with every action card
        // at the top of the deck, since each routes through a different branch of the planner.
        val human = listOf(Rank.TWO, Rank.THREE, Rank.ACE)

        for (drawn in listOf(Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE, Rank.NINE, Rank.SEVEN)) {
            val result = playOut(
                finalRoundState(
                    human = human,
                    bot1 = listOf(Rank.THREE, Rank.TEN),
                    bot2 = listOf(Rank.JOKER, Rank.NINE),
                    bot3 = listOf(Rank.SEVEN, Rank.FIVE),
                    drawPile = listOf(drawn, Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR),
                ),
            )

            assertEquals(
                GamePhase.SCORING,
                result.state.phase,
                "the round did not finish after drawing a ${drawn.serialName}",
            )
            assertCallersCardsUntouched(result.state, human)
        }
    }

    @Test
    fun theRunnerPlaysForTheCoalitionEvenWhenThatHelpsOnlyItsTeammates() {
        // The wiring test, and it took a real position to make it one: the four rounds above
        // pass with every coalition routing removed, because plain MCTS wins them too.
        //
        // Here the planner and the solo service genuinely disagree. Bot-3 is holding a Jack;
        // the planner aims it at bot-1 and bot-2 — **neither target is bot-3's own card** —
        // because moving the Joker into bot-1's hand wins the round for the coalition and
        // nothing bot-3 does for itself can. The solo service includes bot-3, as any
        // self-interested search would.
        val state = finalRoundState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            drawPile = listOf(Rank.FOUR, Rank.FOUR),
        ).let { base ->
            base.copy(
                currentPlayerIndex = 3,
                subPhase = GameSubPhase.AWAITING_ACTION,
                pendingAction = game.vinto.shapes.PendingAction(
                    card = card(Rank.JACK, "pending-jack"),
                    playerId = "bot-3",
                    actionPhase = game.vinto.shapes.ActionPhase.SELECTING_TARGET,
                    from = game.vinto.shapes.PendingCardOrigin.DRAWING,
                    targets = emptyList(),
                ),
            )
        }

        val coalition = buildCoalitionPlanInput(state, "bot-3")
            ?: fail("the position is not a coalition final round")
        val plannerChoice = planCoalitionActionTargets(coalition, state.pendingAction!!.card)
            .targets.firstOrNull() ?: fail("the planner had no opinion, so this proves nothing")

        assertTrue(
            plannerChoice.playerId != "bot-3",
            "the position no longer discriminates: the planner now includes the acting bot",
        )

        val emitted = BotRunner(Difficulty.MODERATE, Random(4)).nextAction(state)
        assertTrue(
            emitted is GameAction.SelectActionTarget,
            "expected a target selection, got ${emitted?.type}",
        )
        val payload = (emitted as GameAction.SelectActionTarget).payload

        assertEquals(
            plannerChoice.playerId to plannerChoice.position,
            payload.targetPlayerId to (payload as SelectActionTargetPayload.Positional).position,
            "the runner aimed where a solo search would, not where the coalition planner chose",
        )
    }

    @Test
    fun aHumanCoalitionMemberPlaysTheirTurnAndTheRoundScores() {
        // Scenario (b): a *bot* called Vinto and a person sits in the coalition. The bots
        // hold until the human names the leader, the human takes exactly one ordinary turn,
        // and the round still scores with the caller's hand untouched. The human here is a
        // minimal script: choose a leader, draw, discard, wave toss-ins through.
        val humanId = "human-1"
        val botCaller = "bot-0"

        fun member(id: String, isHuman: Boolean, ranks: List<Rank>) = PlayerState(
            id = id,
            name = id,
            nickname = id,
            isHuman = isHuman,
            isBot = !isHuman,
            cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
            knownCardPositions = if (isHuman) emptyList() else ranks.indices.toList(),
            isVintoCaller = id == botCaller,
            coalitionWith = if (id == botCaller) emptyList() else listOf(humanId, "bot-1", "bot-2"),
        )

        var state = GameState(
            gameId = "coalition-human-member",
            roundNumber = 1,
            turnNumber = 12,
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            players = listOf(
                member(botCaller, isHuman = false, ranks = listOf(Rank.KING, Rank.TWO)),
                member(humanId, isHuman = true, ranks = listOf(Rank.NINE)),
                member("bot-1", isHuman = false, ranks = listOf(Rank.FIVE, Rank.FIVE)),
                member("bot-2", isHuman = false, ranks = listOf(Rank.SIX)),
            ),
            currentPlayerIndex = 1,
            vintoCallerId = botCaller,
            coalitionLeaderId = null,
            drawPile = Pile((0..5).map { card(Rank.FOUR, "draw-$it") }),
            discardPile = Pile(listOf(card(Rank.THREE, "discard-seed"))),
            pendingAction = null,
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
        val callerRanks = state.players.first().cards.map { it.rank }

        fun humanAction(s: GameState): GameAction? = when {
            s.coalitionLeaderId == null ->
                GameAction.SetCoalitionLeader(game.vinto.shapes.LeaderIdPayload("bot-1"))

            s.activeTossIn != null &&
                humanId !in s.activeTossIn!!.playersReadyForNextTurn ->
                GameAction.PlayerTossInFinished(game.vinto.shapes.PlayerIdPayload(humanId))

            s.players[s.currentPlayerIndex].id == humanId && s.pendingAction == null &&
                s.subPhase == GameSubPhase.IDLE ->
                GameAction.DrawCard(game.vinto.shapes.PlayerIdPayload(humanId))

            s.players[s.currentPlayerIndex].id == humanId &&
                s.subPhase == GameSubPhase.CHOOSING ->
                GameAction.DiscardCard(game.vinto.shapes.PlayerIdPayload(humanId))

            else -> null
        }

        val runner = BotRunner(Difficulty.MODERATE, Random(4))
        var humanDraws = 0
        var actions = 0

        while (actions < 300 && state.phase != GamePhase.SCORING) {
            val action = runner.nextAction(state)
                ?: humanAction(state)
                ?: fail("the round stalled: subPhase=${state.subPhase.serialName}")

            if (action is GameAction.SetCoalitionLeader && state.coalitionLeaderId == null) {
                assertTrue(
                    runner.nextAction(state) == null,
                    "the bots did not wait for the human's leader choice",
                )
            }
            if (action is GameAction.DrawCard && action.payload.playerId == humanId) humanDraws++

            when (val validation = ActionValidator.validate(state, action)) {
                is Validation.Invalid ->
                    fail("action #$actions: illegal ${action.type} — ${validation.reason}")

                Validation.Valid -> Unit
            }
            state = when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure -> fail("engine rejected ${action.type}: ${result.reason}")
            }
            actions++
        }

        assertEquals(GamePhase.SCORING, state.phase, "the round never scored")
        assertEquals(1, humanDraws, "the human should get exactly one final turn")
        assertEquals(
            callerRanks,
            state.players.first().cards.map { it.rank },
            "the coalition interfered with the bot caller's hand",
        )
    }

    @Test
    fun aWrongDeclarationFailsTheLineWithoutBreakingTheRound() {
        // bot-1 has misdeclared its NINE as a QUEEN. Whatever the coalition builds on that
        // claim — a King naming it, a swap-declare — the engine answers with the ordinary
        // wrong-declaration penalty, and the round still finishes lawfully with the caller
        // untouched. Wrongness is a memory problem, never a crash.
        val human = listOf(Rank.TWO, Rank.THREE, Rank.ACE)
        val base = finalRoundState(
            human = human,
            bot1 = listOf(Rank.NINE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.TWO),
            bot3 = listOf(Rank.SEVEN),
            drawPile = listOf(Rank.KING, Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR),
        )
        val misdeclared = base.copy(
            players = base.players.map { player ->
                if (player.id == "bot-1") {
                    player.copy(declaredCards = mapOf(0 to Rank.QUEEN, 1 to Rank.FIVE))
                } else {
                    player
                }
            },
        )

        val result = playOut(misdeclared)

        assertEquals(GamePhase.SCORING, result.state.phase, "the round never scored")
        assertCallersCardsUntouched(result.state, human)
    }
}
