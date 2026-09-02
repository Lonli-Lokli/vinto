package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.Validation
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A coalition bot's Ace is never forced on a teammate.
 *
 * The planner never plays an Ace from hand, but a tossed-in Ace is queued by the engine and
 * its owner is asked where to point it. Every legal target is a coalition member — the caller
 * is off limits — and the first version of the runner answered by seat order, which could
 * hand a penalty card to the one hand still able to win the round.
 */
class CoalitionAceTest {

    private val human = "human1"
    private val bots = listOf("bot1", "bot2", "bot3")

    @Test
    fun aTossedInAceIsPutDownRatherThanAimedAtATeammate() {
        val players = listOf(
            testPlayer(human, "Human", isHuman = true, cards = listOf(testCard(Rank.TWO, "h0"))),
        ) + bots.map { id ->
            val cards = listOf(testCard(Rank.FIVE, "$id-0"), testCard(Rank.SIX, "$id-1"))
            testPlayer(id, id, isHuman = false, cards = cards).copy(
                coalitionWith = bots,
                declaredCards = cards.indices.associateWith { cards[it].rank },
            )
        }
        val state = testState(
            players = players,
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.SELECTING,
            vintoCallerId = human,
            coalitionLeaderId = "bot1",
            turnNumber = 12,
            drawPile = Pile(List(5) { testCard(Rank.FOUR, "deck-$it") }),
            discardPile = Pile(listOf(testCard(Rank.ACE, "discard-ace"))),
        ).copy(
            currentPlayerIndex = 2,
            pendingAction = PendingAction(
                card = testCard(Rank.ACE, "queued-ace"),
                playerId = "bot2",
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.HAND,
                targetType = TargetType.FORCE_DRAW,
                targets = emptyList(),
            ),
            activeTossIn = ActiveTossIn(
                ranks = listOf(Rank.ACE),
                initiatorId = "bot1",
                originalPlayerIndex = 1,
                participants = listOf("bot2"),
                queuedActions = emptyList(),
                waitingForInput = false,
                playersReadyForNextTurn = bots,
            ),
        )

        val action = BotRunner(Difficulty.HARD, Random(3)).nextAction(state)

        assertTrue(action is GameAction.ConfirmPeek, "the Ace was aimed: $action")
        assertEquals(Validation.Valid, ActionValidator.validate(state, action))
    }
}
