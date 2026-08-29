package game.vinto.engine

import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Round and game points, from `VINTO_RULES.md` — "Scoring a Round" and "Game End".
 *
 * These have no TypeScript counterpart: the web app plays one round and stops, so nothing over
 * there ever needed them. They are written from the rules document, which is why each case
 * names the clause it comes from.
 */
class RoundScoringTest {

    private fun hand(id: String, vararg ranks: Rank) =
        testPlayer(id, id, isHuman = id == "p1", cards = ranks.mapIndexed { i, r -> testCard(r, "$id-$i") })

    @Test
    fun aCallerWhoIsLowerTakesThreeAndTheCoalitionLosesOne() {
        // "If Vinto < Coalition lowest → Vinto +3; each Coalition −1."
        val players = listOf(
            hand("p1", Rank.TWO, Rank.THREE), // 5, the caller
            hand("p2", Rank.SIX, Rank.SIX), // 12
            hand("p3", Rank.SEVEN, Rank.SEVEN), // 14
            hand("p4", Rank.EIGHT, Rank.EIGHT), // 16
        )

        assertEquals(
            mapOf("p1" to 3, "p2" to -1, "p3" to -1, "p4" to -1),
            calculateRoundPoints(players, vintoCallerId = "p1"),
        )
    }

    @Test
    fun aCoalitionThatBeatsTheCallerTakesThreeEach() {
        // "If Coalition lowest < Vinto → Vinto −1; each Coalition +3."
        val players = listOf(
            hand("p1", Rank.TEN, Rank.TEN), // 20, the caller
            hand("p2", Rank.TWO, Rank.THREE), // 5 — the champion
            hand("p3", Rank.SEVEN, Rank.SEVEN), // 14
            hand("p4", Rank.EIGHT, Rank.EIGHT), // 16
        )

        // Every member scores, not only the one who beat them: the coalition wins together.
        assertEquals(
            mapOf("p1" to -1, "p2" to 3, "p3" to 3, "p4" to 3),
            calculateRoundPoints(players, vintoCallerId = "p1"),
        )
    }

    @Test
    fun aTieGoesToTheCaller() {
        // "If tie → Vinto +3; Coalition 0." This is what makes "beat them" mean beat them, and
        // it is why the coalition planner searches for a strictly lower total.
        val players = listOf(
            hand("p1", Rank.FIVE, Rank.FIVE), // 10, the caller
            hand("p2", Rank.FOUR, Rank.SIX), // 10 — level, not lower
            hand("p3", Rank.SEVEN, Rank.SEVEN),
            hand("p4", Rank.EIGHT, Rank.EIGHT),
        )

        assertEquals(
            mapOf("p1" to 3, "p2" to 0, "p3" to 0, "p4" to 0),
            calculateRoundPoints(players, vintoCallerId = "p1"),
        )
    }

    @Test
    fun aRoundWithNoCallerScoresNothing() {
        // A round discarded at the buzzer has no caller, and nobody should be paid for it.
        val players = listOf(hand("p1", Rank.TWO), hand("p2", Rank.TEN))

        assertEquals(mapOf("p1" to 0, "p2" to 0), calculateRoundPoints(players, vintoCallerId = null))
    }

    @Test
    fun gamePointsFollowTheRanking() {
        // "1st = 5, 2nd = 3, 3rd = 2." Fourth gets nothing, which the rules leave unstated and
        // arithmetic settles.
        assertEquals(
            mapOf("p1" to 5, "p2" to 3, "p3" to 2, "p4" to 0),
            calculateGamePoints(mapOf("p1" to 9, "p2" to 6, "p3" to 2, "p4" to -3)),
        )
    }

    @Test
    fun playersWhoTieShareARankAndBothTakeItsAward() {
        // Breaking a tie arbitrarily would hand somebody two points for having a name earlier
        // in the alphabet, so a tie is a tie and the rank below it is skipped.
        assertEquals(
            mapOf("p1" to 5, "p2" to 5, "p3" to 3, "p4" to 2),
            calculateGamePoints(mapOf("p1" to 9, "p2" to 9, "p3" to 6, "p4" to 1)),
        )
    }

    @Test
    fun everybodyLevelIsEverybodyFirst() {
        assertEquals(
            mapOf("p1" to 5, "p2" to 5, "p3" to 5, "p4" to 5),
            calculateGamePoints(mapOf("p1" to 0, "p2" to 0, "p3" to 0, "p4" to 0)),
        )
    }
}
