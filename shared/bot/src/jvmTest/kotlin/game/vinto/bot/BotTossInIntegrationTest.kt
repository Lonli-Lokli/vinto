package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A toss-in window driven to completion, ported from
 * `legacy-web/packages/local-client/src/lib/__tests__/bot-tossin.test.ts`.
 *
 * [TossInDecisionTest] asks the service whether a bot *would* join a window.
 * This runs the window: bots take real actions through the real engine until it closes and
 * the turn moves on. The failure it guards against is a window nobody ever finishes — every
 * individual decision correct while the game sits there, which is invisible to any test that
 * asks one question and stops.
 *
 * The TypeScript version counts adapter callbacks (`bot1TossInCalls.length`) because it is
 * watching an async, animation-driven adapter from outside. `BotRunner` is a pure function of
 * the state, so the actions themselves are collected instead — the same events, observed
 * where they happen rather than inferred from spies.
 */
class BotTossInIntegrationTest {

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun bot(id: String, ranks: List<Rank>): PlayerState = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = false,
        isBot = true,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = ranks.indices.toList(),
        isVintoCaller = false,
        coalitionWith = emptyList(),
    )

    private fun openWindow(
        hands: List<Pair<String, List<Rank>>>,
        openRank: Rank,
        drawPile: List<Rank> = listOf(Rank.FOUR, Rank.FOUR, Rank.FOUR, Rank.FOUR),
    ) = GameState(
        gameId = "tossin-test",
        roundNumber = 1,
        // The opening, so the window's owner cannot end the round by calling Vinto — which a
        // bot on twelve points against an unread hand will otherwise do, and rightly.
        turnNumber = 1,
        phase = GamePhase.PLAYING,
        subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        finalTurnTriggered = false,
        players = hands.map { (id, ranks) -> bot(id, ranks) },
        currentPlayerIndex = 0,
        vintoCallerId = null,
        coalitionLeaderId = null,
        drawPile = Pile(drawPile.mapIndexed { index, rank -> card(rank, "draw-$index") }),
        discardPile = Pile(listOf(card(openRank, "discard-top"))),
        pendingAction = null,
        activeTossIn = ActiveTossIn(
            ranks = listOf(openRank),
            initiatorId = hands.first().first,
            originalPlayerIndex = 0,
            participants = emptyList(),
            queuedActions = emptyList(),
            waitingForInput = false,
            playersReadyForNextTurn = emptyList(),
        ),
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private data class Run(val state: GameState, val actions: List<GameAction>)

    /**
     * Runs until the turn moves on, refusing anything invalid.
     *
     * "The window closed" is exactly "the turn advanced" — the sub-phase leaves
     * `toss_queue_active` and comes back while a queued action is aimed, so watching it
     * would stop the run half way through resolving a tossed-in card.
     */
    private fun closeTheWindow(start: GameState, limit: Int = 200): Run {
        // Perfect memory on purpose: a moderate bot fails to record a card a quarter of the
        // time, and whether it tosses then depends on the seed rather than on the window.
        val runner = BotRunner(Difficulty.HARD, Random(6))
        var state = start
        val taken = mutableListOf<GameAction>()

        while (taken.size < limit && state.currentPlayerIndex == start.currentPlayerIndex) {
            val action = runner.nextAction(state) ?: break

            when (val validation = ActionValidator.validate(state, action)) {
                is Validation.Invalid ->
                    fail("action #${taken.size}: illegal ${action.type} — ${validation.reason}")

                Validation.Valid -> Unit
            }

            state = when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure -> fail("the engine rejected a validated ${action.type}: ${result.reason}")
            }
            taken += action
        }

        return Run(state, taken)
    }

    /** The window is closed once play has moved past whoever opened it. */
    private fun windowClosed(run: Run, start: String): Boolean =
        run.state.players.getOrNull(run.state.currentPlayerIndex)?.id != start

    private fun tossedInBy(run: Run) = run.actions
        .filterIsInstance<GameAction.ParticipateInTossIn>()
        .map { it.payload.playerId }

    @Test
    fun aWindowEveryoneCanJoinClosesAndTheTurnMovesOn() {
        val run = closeTheWindow(
            openWindow(
                hands = listOf(
                    "bot1" to listOf(Rank.SEVEN, Rank.FIVE, Rank.FOUR, Rank.THREE),
                    "bot2" to listOf(Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR),
                ),
                openRank = Rank.SEVEN,
            ),
        )

        assertTrue(tossedInBy(run).contains("bot1"), "bot1 held a 7 and did not toss it")
        assertTrue(tossedInBy(run).contains("bot2"), "bot2 held a 7 and did not toss it")
        assertTrue(windowClosed(run, start = "bot1"), "the window never closed")
    }

    @Test
    fun aWindowNobodyCanJoinStillCloses() {
        // The stall this whole file exists for: with nothing to toss, every bot must still
        // say it is done, or the game sits on an open window forever.
        val run = closeTheWindow(
            openWindow(
                hands = listOf(
                    "bot1" to listOf(Rank.TWO, Rank.THREE, Rank.FOUR),
                    "bot2" to listOf(Rank.FIVE, Rank.SIX, Rank.EIGHT),
                    "bot3" to listOf(Rank.NINE, Rank.TEN, Rank.JACK),
                ),
                openRank = Rank.SEVEN,
            ),
        )

        assertEquals(emptyList(), tossedInBy(run), "somebody tossed a card they did not hold")
        assertTrue(windowClosed(run, start = "bot1"), "the window never closed")
    }

    @Test
    fun onlyTheBotsHoldingTheOpenRankJoin() {
        val run = closeTheWindow(
            openWindow(
                hands = listOf(
                    "bot1" to listOf(Rank.SEVEN, Rank.TWO),
                    "bot2" to listOf(Rank.THREE, Rank.FOUR),
                    "bot3" to listOf(Rank.SEVEN, Rank.FIVE),
                ),
                openRank = Rank.SEVEN,
            ),
        )

        assertTrue(tossedInBy(run).contains("bot1"))
        assertTrue(tossedInBy(run).contains("bot3"))
        assertTrue(!tossedInBy(run).contains("bot2"), "bot2 tossed into a window it could not match")
    }

    @Test
    fun aTossedInActionCardIsPlayedBeforeTheWindowCloses() {
        val run = closeTheWindow(
            openWindow(
                hands = listOf(
                    "bot1" to listOf(Rank.NINE, Rank.TWO, Rank.THREE),
                    "bot2" to listOf(Rank.NINE, Rank.FOUR, Rank.FIVE),
                ),
                openRank = Rank.NINE,
            ),
        )

        assertTrue(tossedInBy(run).isNotEmpty(), "nobody tossed in a 9")
        assertTrue(
            run.actions.any { it is GameAction.SelectActionTarget },
            "a 9 was tossed in and its peek was never aimed",
        )
        assertTrue(windowClosed(run, start = "bot1"), "the window never closed")
    }

    @Test
    fun everyBotSaysItIsDoneExactlyOnce() {
        // The duplicate-ready rejection is a real validator rule, so a runner that marks a
        // bot ready twice would be caught by the harness above. This checks the shape of the
        // sequence directly, since the symptom of getting it wrong is a stuck game.
        val run = closeTheWindow(
            openWindow(
                hands = listOf(
                    "bot1" to listOf(Rank.TWO, Rank.THREE),
                    "bot2" to listOf(Rank.FOUR, Rank.FIVE),
                    "bot3" to listOf(Rank.SIX, Rank.EIGHT),
                ),
                openRank = Rank.SEVEN,
            ),
        )

        val readied = run.actions
            .filterIsInstance<GameAction.PlayerTossInFinished>()
            .map { it.payload.playerId }

        assertEquals(readied.toSet().size, readied.size, "a bot was marked ready twice: $readied")
        assertEquals(setOf("bot1", "bot2", "bot3"), readied.toSet())
    }
}
