package game.vinto.bot

import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the bot joins a toss-in window, through the decision service. Ported from
 * `legacy-web/packages/bot/src/lib/__tests__/mcts-bot-tossin.test.ts`.
 *
 * [BotHeuristicsTest] covers the same rule as a pure function; this covers it through the
 * service, which is what the engine actually calls — and which has to build a context and
 * fold the table into memory first. Both are worth having: the function can be right while
 * the wiring around it drops the hand.
 */
class TossInDecisionTest {

    private val botId = "bot1"

    private fun service() = MctsBotDecisionService(Difficulty.HARD, Random(3))

    private fun contextWith(botHand: List<Rank>, openRanks: List<Rank>): BotDecisionContext {
        val botPlayer = testPlayer(
            botId,
            "Bot 1",
            isHuman = false,
            cards = botHand.mapIndexed { index, rank -> testCard(rank, "bot-card-$index") },
        )
        val human = testPlayer(
            "p2",
            "Player 2",
            isHuman = true,
            cards = listOf(testCard(Rank.TWO, "p2-card-1"), testCard(Rank.FIVE, "p2-card-2")),
        )

        val state = testState(
            players = listOf(botPlayer, human),
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            discardPile = Pile(listOf(testCard(openRanks.first(), "discard-top"))),
        ).copy(
            activeTossIn = ActiveTossIn(
                ranks = openRanks,
                initiatorId = "p1",
                originalPlayerIndex = 0,
                participants = emptyList(),
                queuedActions = emptyList(),
                waitingForInput = false,
                playersReadyForNextTurn = emptyList(),
            ),
        )

        return botContext(botId, state).copy(discardPile = state.discardPile)
    }

    @Test
    fun oneMatchingCardIsEnough() {
        val context = contextWith(listOf(Rank.SEVEN, Rank.THREE, Rank.KING), listOf(Rank.SEVEN))
        assertTrue(service().shouldParticipateInTossIn(listOf(Rank.SEVEN), context))
    }

    @Test
    fun aMatchAgainstAnyOfSeveralOpenRanksIsEnough() {
        val context = contextWith(
            listOf(Rank.SEVEN, Rank.THREE, Rank.KING),
            listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE),
        )
        assertTrue(service().shouldParticipateInTossIn(listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE), context))
    }

    @Test
    fun noMatchMeansNoTossIn() {
        val context = contextWith(listOf(Rank.TWO, Rank.THREE, Rank.KING), listOf(Rank.SEVEN))
        assertFalse(service().shouldParticipateInTossIn(listOf(Rank.SEVEN), context))
    }

    @Test
    fun severalCopiesOfTheOpenRankStillMeansYes() {
        val context = contextWith(listOf(Rank.SEVEN, Rank.SEVEN, Rank.KING), listOf(Rank.SEVEN))
        assertTrue(service().shouldParticipateInTossIn(listOf(Rank.SEVEN), context))
    }

    @Test
    fun oneMatchAmongManyOpenRanksIsEnough() {
        val context = contextWith(
            listOf(Rank.QUEEN, Rank.THREE, Rank.FOUR),
            listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN),
        )
        assertTrue(
            service().shouldParticipateInTossIn(
                listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN),
                context,
            ),
        )
    }

    @Test
    fun noMatchAmongManyOpenRanksStillMeansNo() {
        val context = contextWith(
            listOf(Rank.TWO, Rank.THREE, Rank.FOUR),
            listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN),
        )
        assertFalse(
            service().shouldParticipateInTossIn(
                listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN),
                context,
            ),
        )
    }

    @Test
    fun aHandThatIsAllMatchesIsStillJustYes() {
        val context = contextWith(listOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN), listOf(Rank.SEVEN))
        assertTrue(service().shouldParticipateInTossIn(listOf(Rank.SEVEN), context))
    }

    @Test
    fun anEmptyHandDeclinesWithoutFallingOver() {
        val context = contextWith(emptyList(), listOf(Rank.SEVEN))
        assertFalse(service().shouldParticipateInTossIn(listOf(Rank.SEVEN), context))
    }
}
