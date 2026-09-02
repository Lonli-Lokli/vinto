package game.vinto.client

import game.vinto.engine.calculateFinalScores
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bots playing a final round against a human Vinto caller, through a real session.
 *
 * Task 6.6's second half. The first half — the bot's toss-in decision — was already ported
 * one case for one case in `shared/bot`'s `TossInDecisionTest`; this is the part that was
 * missing, and it is a different kind of test: not what a bot decides in a constructed
 * position, but whether a whole final round *plays out* when three coalition bots are trying
 * to beat one hand.
 *
 * **Deliberately not a port of the TypeScript assertions.** Those read "wins by using a drawn
 * Jack to move a Joker into a teammate", "wins by declaring a King on a teammate's high card"
 * — each a specific *mechanism*, each with a 30-second timeout, and each depending on what an
 * MCTS search happens to choose. TRAPS.md §7 already lists two TypeScript tests as flaky for
 * exactly that reason. Asserting a stochastic search picks one particular tactic is a test
 * that fails on a Tuesday for no reason anybody can act on.
 *
 * What is asserted instead is what has to be true *whatever* the bots choose: the round ends,
 * the rules of the final round hold while it does, and the score at the end is the score the
 * rules give. A bot that cannot find the Jack play still passes; a bot that breaks the
 * coalition rules or hangs the round does not.
 */
class CoalitionFinalRoundTest {

    /**
     * How many of the forty seeds must reach a human Vinto call for the sweep to mean anything.
     *
     * A floor rather than a count, because it depends on the deal: not every seed puts the
     * caller in a position to call in the toss-in window. Without it the loop could `continue`
     * past all forty and the test would pass having asserted nothing — which is exactly what a
     * probe found it doing at zero when the harness was first written wrong.
     */
    private val enoughSeeds = 10

    /** A session where the human has called Vinto and the three bots owe one turn each. */
    private suspend fun humanCallsVinto(seed: Long): LocalGameSession? {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

        val called = tableFor(session.view.value).choices
            .firstOrNull { it.label == Label.CallVinto }
            ?.let { session.dispatch((it.move as Move.Send).action) == null }

        return session.takeIf { called == true && it.state.vintoCallerId == me }
    }

    @Test
    fun theCoalitionPlaysItsLastTurnsAndTheRoundEnds() = runTest(timeout = WHOLE_GAME) {
        val stalled = mutableListOf<Long>()
        var played = 0

        for (seed in 1L..40L) {
            val session = humanCallsVinto(seed) ?: continue
            played++
            if (!session.playItselfOut(seed)) stalled += seed
        }

        assertTrue(played >= enoughSeeds, "only $played of 40 seeds produced a human Vinto call")
        assertTrue(stalled.isEmpty(), "the final round never finished for seeds $stalled")
    }

    @Test
    fun theCallerIsNeverTargetedByTheCoalitionInTheFinalRound() = runTest(timeout = WHOLE_GAME) {
        // The rule the validator enforces, checked here where it actually bites: three bots
        // taking real turns against a real caller, rather than in a constructed position.
        // `ValidatorImpersonationTest` proves nothing illegal is accepted; this proves the
        // bots do not spend the round proposing moves that get refused.
        for (seed in 1L..20L) {
            val session = humanCallsVinto(seed) ?: continue
            val caller = session.state.vintoCallerId

            assertTrue(session.playItselfOut(seed), "seed $seed did not finish")
            assertEquals(caller, session.state.vintoCallerId, "the caller changed mid-round")
            assertEquals(GamePhase.SCORING, session.state.phase, "seed $seed did not reach scoring")
        }
    }

    @Test
    fun theRoundIsScoredOnTheCoalitionsBestHandNotItsLast() = runTest(timeout = WHOLE_GAME) {
        // The one rule of the final round that is easy to get wrong and invisible until
        // somebody loses a round they should have won: the coalition is one team, and only
        // its lowest hand is compared to the caller's.
        var checked = 0

        for (seed in 1L..30L) {
            val session = humanCallsVinto(seed) ?: continue
            if (!session.playItselfOut(seed)) continue

            val over = session.state
            val caller = over.vintoCallerId ?: continue
            val scored = calculateFinalScores(over.players, caller)

            val coalition = over.players.filter { it.id != caller }
            val best = coalition.minOf { player -> player.cards.sumOf { it.value } }

            for (member in coalition) {
                assertEquals(
                    best,
                    scored[member.id],
                    "seed $seed: ${member.id} was scored on their own hand rather than the " +
                        "coalition's best — that is one team, not three players",
                )
            }
            checked++
        }

        assertTrue(checked > 0, "no seed produced a scored final round")
    }
}
