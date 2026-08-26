package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Time passes for a bot at turn boundaries, and a new deal wipes the slate.
 *
 * The difficulty model always promised this — memory that decays and randomly drops as
 * turns go by, weaker the easier the bot — but nothing ever advanced the clock, so the
 * promise was inert: difficulty collapsed to the observation-accuracy roll alone. The
 * service now ticks the memory once per elapsed game turn, deterministically from the
 * state's own turn counter, and starts a fresh memory whenever the gameId changes.
 */
class MemoryDecayTest {

    private fun handOfFive() = listOf(
        testCard(Rank.FIVE, "c0"), testCard(Rank.NINE, "c1"), testCard(Rank.QUEEN, "c2"),
        testCard(Rank.TWO, "c3"), testCard(Rank.KING, "c4"),
    )

    private fun contextAtTurn(turn: Int, gameId: String = "test-game"): BotDecisionContext {
        val bot = testPlayer(
            "bot", "Bot", isHuman = false,
            cards = handOfFive(), knownCardPositions = (0..4).toList(),
        )
        val state = testState(
            players = listOf(bot, testPlayer("p2", "P2", isHuman = true)),
            turnNumber = turn,
        ).copy(gameId = gameId)
        return botContext("bot", state)
    }

    @Test
    fun anEasyBotsRecallShiftsAsTurnsPassAndReplaysTheSame() {
        fun believedOverTime(seed: Int): Pair<Map<Int, Rank>, Map<Int, Rank>> {
            val service = MctsBotDecisionService(Difficulty.EASY, Random(seed))
            val early = service.believedOwnCards(contextAtTurn(1))
            val late = service.believedOwnCards(contextAtTurn(30))
            return early to late
        }

        // Somewhere in twenty seeds, twenty-nine turns of forgetting must show: what the
        // bot would claim about its own hand changes between the two askings.
        assertTrue(
            (1..20).any { seed ->
                val (early, late) = believedOverTime(seed)
                early != late
            },
            "twenty-nine turns passed and no easy bot's recall ever moved",
        )

        // And wrongness is reproducible: the same seed tells the same story twice.
        assertEquals(believedOverTime(7), believedOverTime(7))
    }

    @Test
    fun aHardBotForgetsNothingHoweverManyTurnsPass() {
        for (seed in 1..5) {
            val service = MctsBotDecisionService(Difficulty.HARD, Random(seed))
            val early = service.believedOwnCards(contextAtTurn(1))
            val late = service.believedOwnCards(contextAtTurn(60))
            assertEquals(early, late, "a hard bot's memory moved (seed $seed)")
            assertEquals(5, late.size, "a hard bot failed to record its own read hand")
        }
    }

    @Test
    fun aNewDealStartsANewMemory() {
        val service = MctsBotDecisionService(Difficulty.HARD, Random(3))
        assertEquals(5, service.believedOwnCards(contextAtTurn(5, gameId = "deal-A")).size)

        // Same seat, next deal: the engine says this hand has never been read, and the
        // memory of the previous one must not leak into it.
        val freshDeal = testState(
            players = listOf(
                testPlayer(
                    "bot", "Bot", isHuman = false,
                    cards = handOfFive(), knownCardPositions = emptyList(),
                ),
                testPlayer("p2", "P2", isHuman = true),
            ),
            turnNumber = 0,
        ).copy(gameId = "deal-B")

        assertTrue(
            service.believedOwnCards(botContext("bot", freshDeal)).isEmpty(),
            "the previous deal's memories survived into the new one",
        )
    }
}
