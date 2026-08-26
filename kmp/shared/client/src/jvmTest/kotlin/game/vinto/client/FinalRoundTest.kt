package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Calling Vinto has to end the round.
 *
 * The rules put the call at the end of your own turn, which is the toss-in window — so that is
 * where the table offers it, and it is the path a player actually takes. It is also the path
 * that was never tested: the earlier tests called Vinto at the start of a turn, which the
 * engine treats differently (you still owe the turn you just declared the end of).
 *
 * JVM only, for the same reason as `FinishesTest`: a sweep over sixty deals is fast here and
 * slow on a simulator, and what it proves is about the rules rather than about the platform.
 */
class FinalRoundTest {

    private suspend fun callVintoAt(seed: Long): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        val window = tableFor(session.view.value)
        window.choices.firstOrNull { it.label == "Call Vinto" }?.let {
            session.dispatch((it.move as Move.Send).action)
        }
        return session
    }

    @Test
    fun callingVintoInTheTossInWindowPlaysTheRoundOut() = runTest {
        val stalled = mutableListOf<String>()

        for (seed in 1L..60L) {
            val session = callVintoAt(seed)
            if (session.isOver) continue

            val view = session.view.value
            stalled += "seed $seed: phase=${view.phase} sub=${view.subPhase} " +
                "current=${view.players[view.currentPlayerIndex].nickname} " +
                "leader=${view.coalitionLeaderId} pending=${view.pendingAction?.playerId}"
        }

        assertTrue(stalled.isEmpty(), "rounds that never finished:\n${stalled.joinToString("\n")}")
    }

    /**
     * The final round counts itself down: three coalition turns with four seats, read from
     * the frames the round plays out through — the same views the table draws — so the
     * count a player sees is the one asserted here. 3, 2, 1, and then the hands go over.
     */
    @Test
    fun theFinalRoundCountsItsTurnsDown() = runTest {
        var checked = 0

        for (seed in 1L..20L) {
            val session = callVintoAt(seed)
            if (session.view.value.vintoCallerId == null) continue

            // One emission per dispatch, carrying every frame the call played out through.
            val frames = session.frames.replayCache.lastOrNull().orEmpty()
            val counts = frames.mapNotNull { finalRoundTurnsLeft(it.view) }

            assertTrue(counts.isNotEmpty(), "seed $seed: a final round with no counted frame")
            assertEquals(
                listOf(3, 2, 1),
                counts.distinct(),
                "seed $seed: the countdown was $counts",
            )
            counts.zipWithNext { a, b ->
                assertTrue(a >= b, "seed $seed: the countdown went back up in $counts")
            }
            assertTrue(
                finalRoundTurnsLeft(session.view.value) == null,
                "seed $seed: the round is over; there is nothing left to count",
            )
            checked++
        }

        assertTrue(checked >= 5, "only $checked of 20 seeds produced a callable window")
    }

    /**
     * The scoring screen says why the hands went face-up. A round that reaches scoring with
     * no caller can only have ended on the deck — the mapping is checked against a real
     * ended round, and the no-caller reading against the same view with the call erased,
     * which is the one part the seeds cannot be relied on to produce on demand.
     */
    @Test
    fun theScoringScreenKnowsWhyTheRoundEnded() = runTest {
        var session: LocalGameSession? = null
        for (seed in 1L..20L) {
            val played = callVintoAt(seed)
            if (played.isOver && played.view.value.vintoCallerId != null) {
                session = played
                break
            }
        }
        val ended = (session ?: error("no seed in 20 produced a completed called round")).view.value

        assertEquals(RoundEndReason.VINTO_CALLED, roundEndReason(ended))
        // The no-caller reading, against the same real ended view with the call erased —
        // the one state the seeds cannot be relied on to produce on demand.
        assertEquals(RoundEndReason.DECK_EXHAUSTED, roundEndReason(ended.copy(vintoCallerId = null)))

        val panel = tableFor(ended)
        assertTrue(
            panel.detail.orEmpty().contains("called Vinto"),
            "the panel does not say who ended the round: ${panel.detail}",
        )
        assertTrue(
            tableFor(ended.copy(vintoCallerId = null)).detail.orEmpty().contains("deck ran out"),
            "the deck ending goes unexplained",
        )

        // And before anything has ended, there is no reason to give.
        assertNull(roundEndReason(LocalGameSession(seed = 1L, difficulty = Difficulty.EASY).view.value))
    }
}
