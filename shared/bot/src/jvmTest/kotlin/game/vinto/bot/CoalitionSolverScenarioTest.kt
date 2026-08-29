package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A position the coalition can only win by *cooperating across turns*, played to the end.
 *
 * The caller holds a lone King — total **zero** — so the coalition needs a hand strictly
 * below zero, and the only negative card on the table is p1's Joker. No single member can
 * get there alone: the win takes the Joker moved into p2's small hand (a Queen or Jack line)
 * and p2's six shed through a toss-in someone else opens. If the solver finds that, it has
 * planned a multi-turn, multi-member line from declared knowledge alone — p1's fourth card
 * and two of p3's stay undeclared and unknown throughout.
 *
 * The assertion is the outcome, not the move list: SCORING reached, best coalition hand
 * below zero, the caller's King untouched. Any line that gets there is a win; with this
 * deck only Joker lines do.
 */
class CoalitionSolverScenarioTest {

    private val callerId = "caller"

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun scenario(): GameState {
        val callerKing = card(Rank.KING, "caller-king")

        fun seat(
            id: String,
            isHuman: Boolean,
            cards: List<Card>,
            known: List<Int>,
            declared: Map<Int, Rank>?,
        ) = PlayerState(
            id = id,
            name = id,
            nickname = id,
            isHuman = isHuman,
            isBot = !isHuman,
            cards = cards,
            knownCardPositions = known,
            isVintoCaller = id == callerId,
            coalitionWith = if (id == callerId) emptyList() else listOf("p1", "p2", "p3"),
            declaredCards = declared,
        )

        val p1 = seat(
            "p1",
            isHuman = false,
            cards = listOf(
                card(Rank.QUEEN, "p1-queen"),
                card(Rank.EIGHT, "p1-eight"),
                card(Rank.JOKER, "p1-joker"),
                card(Rank.TWO, "p1-mystery"),
            ),
            known = listOf(0, 1, 2),
            declared = mapOf(0 to Rank.QUEEN, 1 to Rank.EIGHT, 2 to Rank.JOKER),
        ).copy(
            // p1 once saw the caller's card; pooled, it tells the whole coalition the
            // target is zero.
            opponentKnowledge = mapOf(
                callerId to SerializedOpponentKnowledge(mapOf(0 to callerKing)),
            ),
        )

        return GameState(
            gameId = "solver-scenario",
            roundNumber = 1,
            turnNumber = 20,
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            players = listOf(
                seat(callerId, isHuman = true, listOf(callerKing), known = emptyList(), declared = null),
                p1,
                seat(
                    "p2",
                    isHuman = false,
                    cards = listOf(card(Rank.TEN, "p2-ten"), card(Rank.SIX, "p2-six")),
                    known = listOf(0, 1),
                    declared = mapOf(0 to Rank.TEN, 1 to Rank.SIX),
                ),
                seat(
                    "p3",
                    isHuman = false,
                    cards = listOf(
                        card(Rank.SIX, "p3-six"),
                        card(Rank.SEVEN, "p3-seven"),
                        card(Rank.FIVE, "p3-mystery1"),
                        card(Rank.FOUR, "p3-mystery2"),
                    ),
                    known = listOf(0, 1),
                    declared = mapOf(0 to Rank.SIX, 1 to Rank.SEVEN),
                ),
            ),
            currentPlayerIndex = 1,
            vintoCallerId = callerId,
            coalitionLeaderId = "p1",
            drawPile = Pile(
                listOf(
                    card(Rank.FOUR, "draw-0"),
                    card(Rank.SIX, "draw-1"),
                    card(Rank.FIVE, "draw-2"),
                    card(Rank.EIGHT, "draw-3"),
                    card(Rank.EIGHT, "draw-4"),
                    card(Rank.EIGHT, "draw-5"),
                ),
            ),
            discardPile = Pile(listOf(card(Rank.FOUR, "discard-seed"))),
            pendingAction = null,
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
    }

    private fun playOut(seed: Long): GameState {
        val runner = BotRunner(Difficulty.MODERATE, Random(seed))
        var state = scenario()
        var actions = 0

        while (actions < 300 && state.phase != GamePhase.SCORING) {
            val action = runner.nextAction(state)
                ?: fail("stalled after $actions actions: subPhase=${state.subPhase.serialName}")

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
        return state
    }

    private fun assertJokerLineWon(state: GameState) {
        assertEquals(GamePhase.SCORING, state.phase, "the round never scored")

        val callerTotal = state.players.first { it.id == callerId }.cards.sumOf { it.value }
        val bestCoalition = state.players
            .filter { it.id != callerId }
            .minOf { seat -> seat.cards.sumOf { it.value } }

        assertEquals(
            listOf(Rank.KING),
            state.players.first { it.id == callerId }.cards.map { it.rank },
            "the caller's hand was touched",
        )
        assertTrue(
            bestCoalition < callerTotal && bestCoalition < 0,
            "the solver did not find the Joker line: best coalition hand was " +
                "$bestCoalition against the caller's $callerTotal",
        )
    }

    @Test
    fun theSolverFindsTheJokerLineAndBeatsAKingCaller() {
        assertJokerLineWon(playOut(seed = 4))
    }

    @Test
    fun theWinIsPlannedNotLuckyASecondSeedFindsItToo() {
        // The coalition path is a deterministic search; the seeded Random feeds only the
        // solo services around it. A different seed must land the same win, or the first
        // one was fortune rather than planning.
        assertJokerLineWon(playOut(seed = 99))
    }
}
