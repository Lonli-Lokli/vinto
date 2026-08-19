package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Vinto call rule, ported from `packages/bot/src/lib/__tests__/vinto-call.test.ts`.
 *
 * The rule is deliberately conservative, and each of these cases is one of the ways a bot
 * could talk itself into a losing call.
 */
class VintoCallRuleTest {

    private fun contextWith(
        cards: List<Rank>,
        turnNumber: Int = 20,
        known: List<Int>? = null,
        vintoCallerId: String? = null,
    ): BotDecisionContext {
        val hand = cards.mapIndexed { index, rank -> testCard(rank, "${rank.serialName}_$index") }
        val bot = testPlayer(
            id = "p1",
            name = "Bot",
            isHuman = false,
            cards = hand,
            knownCardPositions = known ?: hand.indices.toList(),
        )
        val others = (2..4).map { testPlayer("p$it", "Player $it", isHuman = false) }
        return botContext("p1", testState(listOf(bot) + others, turnNumber, vintoCallerId = vintoCallerId))
    }

    @Test
    fun callsOnAHandWorthZeroOrLess() {
        // King is 0 and Joker is -1, so this hand is -1: a winning call, because the coalition
        // needs to be strictly lower to beat it.
        assertTrue(shouldCallVintoByScore(contextWith(listOf(Rank.KING, Rank.JOKER))))
        assertTrue(shouldCallVintoByScore(contextWith(listOf(Rank.KING, Rank.KING))))
    }

    @Test
    fun callsOnExactlyZeroBecauseTheCoalitionMustBeatTheCallerStrictly() {
        // A tie goes to the caller (+3 / 0), so zero is worth calling on.
        assertTrue(shouldCallVintoByScore(contextWith(listOf(Rank.KING))))
    }

    @Test
    fun theServiceMakesTheSameCallAtEveryDifficulty() {
        // Difficulty tunes memory accuracy and search budget; it must not turn the Vinto
        // rule into a different rule, or an "easy" bot would end games a "hard" one would not.
        val winning = contextWith(listOf(Rank.JOKER, Rank.KING))
        val losing = contextWith(listOf(Rank.TEN))

        for (difficulty in listOf(Difficulty.EASY, Difficulty.MODERATE, Difficulty.HARD)) {
            val service = MctsBotDecisionService(difficulty, Random(5))
            assertTrue(service.shouldCallVinto(winning), "${difficulty.serialName} refused a winning hand")
            assertFalse(service.shouldCallVinto(losing), "${difficulty.serialName} called on a losing hand")
        }
    }

    @Test
    fun doesNotCallOnAPositiveHand() {
        assertFalse(shouldCallVintoByScore(contextWith(listOf(Rank.KING, Rank.TWO))))
        assertFalse(shouldCallVintoByScore(contextWith(listOf(Rank.QUEEN, Rank.JOKER))))
    }

    @Test
    fun doesNotCallWithAnyUnknownCard() {
        // A known Joker beside three unseen cards has a *known* score of -1 and a real
        // expected score far above it. This is the case the rule exists to refuse.
        assertFalse(
            shouldCallVintoByScore(
                contextWith(listOf(Rank.JOKER, Rank.TWO, Rank.THREE, Rank.FOUR), known = listOf(0)),
            ),
        )
    }

    @Test
    fun doesNotCallInTheOpening() {
        // Calling before everyone has had a couple of turns is a coin flip whatever the hand.
        assertFalse(shouldCallVintoByScore(contextWith(listOf(Rank.KING, Rank.JOKER), turnNumber = 3)))
        assertTrue(shouldCallVintoByScore(contextWith(listOf(Rank.KING, Rank.JOKER), turnNumber = 8)))
    }

    @Test
    fun doesNotCallAfterSomeoneElseHas() {
        assertFalse(
            shouldCallVintoByScore(
                contextWith(listOf(Rank.KING, Rank.JOKER), vintoCallerId = "p2"),
            ),
        )
    }

    @Test
    fun respectsAnExplicitThreshold() {
        val hand = contextWith(listOf(Rank.TWO, Rank.THREE))
        assertFalse(shouldCallVintoByScore(hand))
        assertTrue(shouldCallVintoByScore(hand, threshold = 5))
    }
}
