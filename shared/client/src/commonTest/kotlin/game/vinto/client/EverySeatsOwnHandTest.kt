package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The hand a seat is shown is the hand that seat held.
 *
 * Reported 2026-09-16, off a score sheet where three coalition rows read 2 / −1 / −1 and were
 * indistinguishable: *"scoring should be updated visually leaderboard, look identical"*. They
 * were identical because `calculateFinalScores` gives **every coalition member the best
 * coalition total** — which is exactly right for what the round *pays*, since the coalition
 * wins or loses together, and wrong for the column headed "hand". In that round Ember held 36
 * and Dune 24; both rows said 2, and both wore "best of the others" when only Tide was.
 *
 * Two things read this map and both were wrong in the same way. The sheet marks the decisive
 * row with [bestCoalitionHands], which returns *every* member when they all carry the best; and
 * `Stats` records the viewer's own best hand and whether they finished lowest, so every
 * coalition member banked the coalition's best hand as their own and a win they had not had.
 * The rule `Stats` is written to — won means **finished lowest** — could not hold.
 *
 * So [RoundResult.hands] is what each seat actually held. What the round paid is
 * [RoundResult.points], which is unchanged and is where the coalition's togetherness lives.
 */
class EverySeatsOwnHandTest {

    private suspend fun playedOut(seed: Long = 20_260_916L): LocalGame {
        val game = LocalGame.start(MemoryVault(), seed = seed, difficulty = Difficulty.EASY)
        val me = game.session.playerId
        game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        game.session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        game.session.playItselfOut(seed = seed)
        return game
    }

    @Test
    fun aCoalitionMemberIsShownItsOwnTotalAndNotTheCoalitionsBest() = runTest(timeout = WHOLE_GAME) {
        val game = playedOut()
        val result = assertNotNull(game.result, "the game did not finish")
        val caller = assertNotNull(result.callerId, "the round ended with nobody calling")
        val state = game.session.state

        for ((id, _) in result.seats) {
            val cards = state.players.first { it.id == id }.cards
            assertEquals(
                cards.sumOf { it.value },
                result.hands[id],
                "$id was shown a hand it did not hold",
            )
        }

        // And the mark lands on the one row it is about: the hand the +3 and the −1 were both
        // worked out from, which is one seat unless two of them really tied.
        val decided = bestCoalitionHands(result.hands, caller)
        val best = result.hands.filterKeys { it != caller }.values.min()
        assertEquals(
            result.hands.filterKeys { it != caller }.filterValues { it == best }.keys,
            decided,
            "the decisive row is not the one the round was decided against",
        )
    }

    /**
     * Won means **finished lowest**, which is the rule `Stats` is written to and the one the
     * flattening broke: with every coalition member carrying the coalition's best, a member who
     * finished on 36 banked both that best hand and a win.
     *
     * Swept over seeds rather than pinned to one, because the case only arises in a round a
     * coalition member actually wins — and a test that silently never reaches it is worse than
     * no test. The sweep asserts it reached one.
     */
    // Six whole MCTS games, which is two hundred seconds on an iOS simulator against seventeen
    // for the single game above it — so the budget is [WHOLE_GAME] and not `runTest`'s own
    // minute. `TestBudget` says in as many words that this rule had been written down and then
    // not applied to the next test written in the same session; this was that next test, and
    // `kmp-ios` said so on the nightly before anybody noticed.
    @Test
    fun onlyTheSeatThatFinishedLowestBanksAWin() = runTest(timeout = WHOLE_GAME) {
        var coalitionWins = 0

        for (seed in 1L..6L) {
            val game = playedOut(seed)
            val result = assertNotNull(game.result, "the game on seed $seed did not finish")

            // Read off the table rather than off the result, which is the thing under test: a
            // map that has flattened the coalition agrees with itself about who was lowest.
            val held = game.session.state.players.associate { it.id to it.cards.sumOf { c -> c.value } }
            val lowest = held.values.min()
            val lowestSeats = held.filterValues { it == lowest }.keys
            if (lowestSeats.any { it != result.callerId }) coalitionWins++

            val winners = result.seats.map { it.first }
                .filter { Stats().plus(result, it)?.roundsWon == 1 }
                .toSet()
            assertEquals(
                lowestSeats,
                winners,
                "on seed $seed a seat banked a win for a hand that was not the lowest",
            )

            // The sweep is a **search**, not a sample: it is here to reach a round a coalition
            // member actually wins, and every game it plays is checked on the way. So it stops
            // at the first one. A whole game is about a minute on an iOS simulator, and six of
            // them is 346 s against the five-minute budget — `kmp-ios` failed on that and
            // nothing else did.
            if (coalitionWins > 0) break
        }

        assertTrue(coalitionWins > 0, "no coalition member ever finished lowest, so this proves nothing")
    }
}
