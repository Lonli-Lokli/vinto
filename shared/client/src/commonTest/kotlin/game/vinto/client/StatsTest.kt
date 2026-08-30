package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The four numbers a player comes back for.
 *
 * `Stats.plus` is pure, so every rule here is asked directly rather than by playing rounds
 * through a screen — which matters most for the streak, where the interesting cases are about
 * *breaking* and a test that has to lose a real game to check one would be a slow test that
 * rarely runs.
 */
class StatsTest {

    @Test
    fun nothingIsClaimedBeforeAnythingIsPlayed() {
        val fresh = Stats()
        assertEquals(0, fresh.roundsPlayed)
        assertNull(fresh.winRate, "a win rate out of nothing is a division by zero, not 0%")
        assertNull(fresh.bestHand, "there is no best hand before there is a hand")
    }

    @Test
    fun aWinIsCountedAndTheStreakGrows() {
        val after = Stats().plus(hand = 4, won = true).plus(hand = 7, won = true)
        assertEquals(2, after.roundsPlayed)
        assertEquals(2, after.roundsWon)
        assertEquals(2, after.streak)
        assertEquals(2, after.bestStreak)
        assertEquals(100, after.winRate)
    }

    @Test
    fun aLossBreaksTheStreakButNotTheRecordOfIt() {
        val after = Stats()
            .plus(hand = 4, won = true)
            .plus(hand = 3, won = true)
            .plus(hand = 20, won = false)

        assertEquals(0, after.streak, "the streak is over")
        assertEquals(2, after.bestStreak, "but it happened, and that is the number worth keeping")
        assertEquals(3, after.roundsPlayed)
        assertEquals(2, after.roundsWon, "the two wins still happened")
    }

    @Test
    fun theBestHandIsTheLowestEverSeenIncludingANegativeOne() {
        // A Joker is worth -1, so a hand can go below zero and a naive `maxOf` would miss it.
        val after = Stats()
            .plus(hand = 9, won = false)
            .plus(hand = 2, won = true)
            .plus(hand = -1, won = true)
            .plus(hand = 14, won = false)

        assertEquals(-1, after.bestHand)
    }

    @Test
    fun theWinRateRoundsTowardsHonesty() {
        // One in three is 33%, not 34%. A rate that rounds up flatters the player, which is
        // the wrong direction for a number they are being shown about themselves.
        val after = Stats().plus(1, true).plus(1, false).plus(1, false)
        assertEquals(33, after.winRate)
    }

    @Test
    fun aRecordSurvivesBeingWrittenAndReadBack() {
        val vault = MemoryVault()
        val stats = Stats().plus(hand = 3, won = true).plus(hand = 8, won = false)
        vault.saveStats(stats)
        assertEquals(stats, vault.loadStats())
    }

    @Test
    fun aBrokenFileCostsTheStreakAndNotTheApp() {
        val vault = MemoryVault()
        vault.write("vinto.stats", "{ this is not json")
        assertEquals(Stats(), vault.loadStats(), "a bad file must not stop the app starting")
    }

    @Test
    fun anOlderFormatIsReplacedRatherThanMisread() {
        val vault = MemoryVault()
        vault.write("vinto.stats", """{"version":0,"roundsPlayed":99,"roundsWon":99}""")
        assertEquals(Stats(), vault.loadStats(), "a record from another shape is not this one")
    }

    @Test
    fun forgettingLeavesNothingBehind() {
        val vault = MemoryVault()
        vault.saveStats(Stats().plus(hand = 3, won = true))
        vault.forgetStats()
        assertEquals(Stats(), vault.loadStats())
    }
}
