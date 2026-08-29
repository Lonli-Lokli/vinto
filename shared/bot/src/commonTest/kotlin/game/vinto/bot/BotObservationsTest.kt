package game.vinto.bot

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.SwapCardPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The table of inferences behind [BotRunner.observe]: which engine actions teach the
 * [OpponentModeler] what. The modeler's own arithmetic is [OpponentModelerTest]'s job; this
 * checks the *mapping* — the soft evidence is read, the hard facts (which the engine already
 * publishes through `opponentKnowledge`) are left alone, and removals renumber safely.
 */
class BotObservationsTest {

    private fun pending(playerId: String, rank: Rank, from: PendingCardOrigin = PendingCardOrigin.DRAWING) =
        PendingAction(
            card = testCard(rank, "${rank.serialName}_pending"),
            playerId = playerId,
            actionPhase = ActionPhase.CHOOSING_ACTION,
            from = from,
            targets = emptyList(),
        )

    private fun table(handRanks: Map<String, List<Rank>>): GameState = testState(
        players = handRanks.map { (id, ranks) ->
            testPlayer(
                id,
                id,
                isHuman = false,
                cards = ranks.mapIndexed { i, r -> testCard(r, "${r.serialName}_$id$i") }
            )
        },
    )

    @Test
    fun discardingADrawnCardSaysTheHandBehindItIsBetter() {
        val before = table(mapOf("p1" to listOf(Rank.TWO), "p2" to listOf(Rank.SIX)))
            .copy(pendingAction = pending("p1", Rank.NINE))

        val events = observationsFor(GameAction.DiscardCard(PlayerIdPayload("p1")), before, before)

        val acted = events.filterIsInstance<TableObservation.Acted>().single()
        val discard = acted.observed as ObservedAction.DiscardDrawn
        assertEquals("p1", discard.playerId)
        assertEquals(Rank.NINE, discard.card.rank)
    }

    @Test
    fun swappingInInvalidatesTheBeliefAtThatPositionAndSignalsTidyingUp() {
        val before = table(mapOf("p1" to listOf(Rank.TWO, Rank.SIX)))
            .copy(pendingAction = pending("p1", Rank.THREE))

        val events = observationsFor(
            GameAction.SwapCard(SwapCardPayload("p1", position = 1)),
            before,
            before,
        )

        assertTrue(events.any { it == TableObservation.BeliefInvalidated("p1", 1) })
        assertTrue(
            events.filterIsInstance<TableObservation.Acted>()
                .any { it.observed is ObservedAction.SwapOwn },
        )
    }

    @Test
    fun aSuccessfulTossInRenumbersBeliefsInDescendingOrder() {
        val before = table(mapOf("p1" to listOf(Rank.SIX, Rank.SIX, Rank.TWO, Rank.SIX)))
        val after = table(mapOf("p1" to listOf(Rank.TWO)))

        val events = observationsFor(
            GameAction.ParticipateInTossIn(ParticipateInTossInPayload("p1", listOf(0, 1, 3))),
            before,
            after,
        )

        // Descending, so shifting one at a time cannot move a belief twice.
        assertEquals(
            listOf(
                TableObservation.CardRemoved("p1", 3),
                TableObservation.CardRemoved("p1", 1),
                TableObservation.CardRemoved("p1", 0),
            ),
            events,
        )
    }

    @Test
    fun aFailedTossInRemovesNothing() {
        // The card came back and a penalty card arrived: the hand grew, nothing left it.
        val before = table(mapOf("p1" to listOf(Rank.SIX, Rank.TWO)))
        val after = table(mapOf("p1" to listOf(Rank.SIX, Rank.TWO, Rank.NINE)))

        val events = observationsFor(
            GameAction.ParticipateInTossIn(ParticipateInTossInPayload("p1", listOf(0))),
            before,
            after,
        )

        assertTrue(events.isEmpty(), "a failed toss-in produced $events")
    }

    @Test
    fun aWatchedSwapActionResetsBothTouchedPositions() {
        val before = table(mapOf("p1" to listOf(Rank.TWO), "p2" to listOf(Rank.SIX), "p3" to listOf(Rank.NINE)))
            .copy(
                pendingAction = pending("p1", Rank.JACK).copy(
                    actionPhase = ActionPhase.SELECTING_TARGET,
                    targets = listOf(ActionTarget("p2", 0), ActionTarget("p3", 0)),
                ),
            )

        val events = observationsFor(GameAction.ExecuteJackSwap(PlayerIdPayload("p1")), before, before)

        assertEquals(
            setOf(
                TableObservation.BeliefInvalidated("p2", 0),
                TableObservation.BeliefInvalidated("p3", 0),
            ),
            events.toSet(),
        )
    }

    @Test
    fun theRunnerFormsABeliefFromWatchingTheTable() {
        // End to end through BotRunner.observe: a player who draws and throws away a Queen
        // is telling the table their hand is better than ten points, and the modeler's
        // estimate of them should drop below its neutral starting point.
        val runner = BotRunner()
        val before = table(mapOf("p1" to listOf(Rank.TWO), "p2" to listOf(Rank.SIX)))
            .copy(pendingAction = pending("p1", Rank.QUEEN))

        runner.observe(GameAction.DiscardCard(PlayerIdPayload("p1")), before, before)

        val modeler = runner.tableModelForTesting()
        val beliefs = modeler.getPlayerBeliefs("p1")
        assertTrue(
            beliefs != null && beliefs.estimatedScore < 25,
            "watching a Queen thrown away did not lower the estimate: ${beliefs?.estimatedScore}",
        )
    }
}
