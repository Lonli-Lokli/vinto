package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Joker must never be given away. Ported from
 * `legacy-web/packages/bot/src/lib/__tests__/mcts-bot-joker-swap.test.ts`.
 *
 * A Joker is worth -1, the best card in the game, and swapping one out is the single most
 * expensive mistake a bot can make in one move. [OutcomeSimulator] prices that so far out of
 * reach that no other term can outvote it; this checks the price survives all the way to the
 * decision the engine actually receives.
 *
 * The sweep is exhaustive on purpose — every rank the bot could draw — because a protection
 * rule that holds for twelve ranks and fails for the thirteenth is not a protection rule.
 */
class JokerProtectionTest {

    private val botId = "bot1"

    /** A fixed seed, so a failure here is reproducible rather than a bad afternoon. */
    private fun service() = MctsBotDecisionService(Difficulty.HARD, Random(7))

    private fun contextWith(botCards: List<Card>, knownPositions: List<Int>, drawn: Card): BotDecisionContext {
        val botPlayer = testPlayer(
            botId, "Bot 1", isHuman = false,
            cards = botCards,
            knownCardPositions = knownPositions,
        )
        val human = testPlayer(
            "human1", "Human 1", isHuman = true,
            cards = List(5) { testCard(Rank.SIX, "h$it") },
        )
        val state = testState(
            players = listOf(botPlayer, human),
            turnNumber = 5,
            subPhase = GameSubPhase.CHOOSING,
        )

        return botContext(botId, state).copy(
            pendingCard = drawn,
            discardPile = Pile(),
        )
    }

    private fun handWithJokerFirst(rest: Rank) =
        listOf(testCard(Rank.JOKER, "j1")) + List(4) { testCard(rest, "c${it + 2}") }

    @Test
    fun aKnownJokerIsNeverTheCardSwappedOut() {
        val botCards = handWithJokerFirst(Rank.SIX)

        for (drawn in listOf(
            testCard(Rank.TWO, "d1"),
            testCard(Rank.SIX, "d2"),
            testCard(Rank.TEN, "d3"),
            testCard(Rank.QUEEN, "d4"),
        )) {
            val position = service().selectBestSwapPosition(
                drawn,
                contextWith(botCards, knownPositions = listOf(0), drawn = drawn),
            )

            assertNotEquals(0, position, "the bot swapped away its Joker for a ${drawn.rank.serialName}")
            if (position != null) {
                assertTrue(position in 1..4, "position $position is not in the hand")
            }
        }
    }

    @Test
    fun theJokerSurvivesEveryRankTheBotCouldDraw() {
        // Ten-point cards all round, so every draw is an improvement *somewhere* — the
        // temptation is real and position 0 must still never be it.
        val botCards = handWithJokerFirst(Rank.TEN)

        for (rank in listOf(
            Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX,
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
            Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
        )) {
            val drawn = testCard(rank, "d-${rank.serialName}")
            val position = service().selectBestSwapPosition(
                drawn,
                contextWith(botCards, knownPositions = listOf(0), drawn = drawn),
            )

            assertNotEquals(0, position, "the bot swapped away its Joker for a ${rank.serialName}")
        }
    }

    @Test
    fun aDrawnJackIsAimedSomewhereOtherThanTheJoker() {
        val botCards = handWithJokerFirst(Rank.TEN)
        val jack = testCard(Rank.JACK, "dj")
        val context = contextWith(botCards, knownPositions = listOf(0), drawn = jack)

        // Whether the bot plays the Jack's action or swaps it in is its own call; what is not
        // its call is giving up the Joker either way.
        val service = service()
        service.shouldUseAction(jack, context)
        val position = service.selectBestSwapPosition(jack, context)

        assertNotEquals(0, position)
    }
}
