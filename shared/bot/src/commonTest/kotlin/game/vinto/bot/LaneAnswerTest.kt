package game.vinto.bot

import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Lane
import game.vinto.shapes.Pile
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.laneOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A bot answers for its own lane of the shared plan, and for nobody else's (design D7a).
 *
 * The measure is the one the round is scored on — the lowest coalition hand — read off the
 * shared picture: every claim on the table, plus the bot's own cards. A step that leaves that
 * hand no worse is a yes; one that raises it is "that leaves us worse"; one that cannot be read
 * at all is a no. An empty lane is a yes, because nothing is being asked.
 */
class LaneAnswerTest {

    private val caller = "p1"
    private val bot = "p2"
    private val mate = "p3"
    private val third = "p4"

    private var counter = 0
    private fun card(rank: Rank) = testCard(rank, "${rank.serialName}-${counter++}")

    private fun seat(id: String, ranks: List<Rank>, declared: Boolean): PlayerState =
        testPlayer(id, id, isHuman = false, cards = ranks.map(::card)).let { seat ->
            if (declared) {
                seat.copy(claims = ranks.mapIndexed { position, rank -> Claim(id, listOf(position), listOf(rank)) })
            } else {
                seat
            }
        }

    /**
     * The bot holds a ten and a two (its own cards, ground truth to it); a teammate has claimed
     * a three; the third member has claimed a five. The lowest coalition hand is the three.
     */
    private fun table(discardTop: Rank? = null): GameState = testState(
        players = listOf(
            seat(caller, listOf(Rank.SIX), declared = false).copy(isVintoCaller = true),
            seat(bot, listOf(Rank.TEN, Rank.TWO), declared = false),
            seat(mate, listOf(Rank.THREE), declared = true),
            seat(third, listOf(Rank.FIVE), declared = true),
        ),
        phase = GamePhase.FINAL,
        vintoCallerId = caller,
        discardPile = Pile(listOfNotNull(discardTop?.let(::card))),
    )

    private fun says(step: Step?, state: GameState = table()) = answerForLane(state, bot, step, askedBy = mate).says

    @Test
    fun anEmptyLaneIsAYes() {
        assertEquals(TableTalk.Answer.Says.YES, says(null))
    }

    @Test
    fun aSwapThatLowersTheLowestHandIsAgreedTo() {
        // The two for the three: the teammate's hand drops from three to two.
        val step = Step.Swap(CardAt(bot, 1), CardAt(mate, 0))
        assertEquals(TableTalk.Answer.Says.YES, says(step))
    }

    @Test
    fun aSwapThatRaisesTheLowestHandLeavesUsWorse() {
        // The ten for the three: the teammate's hand becomes ten and the bot's five — nobody is
        // on three any more.
        val step = Step.Swap(CardAt(bot, 0), CardAt(mate, 0))
        assertEquals(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE, says(step))
    }

    @Test
    fun aDeclareThatEmptiesAHandIsAgreedTo() {
        assertEquals(TableTalk.Answer.Says.YES, says(Step.Declare(Rank.THREE)))
    }

    @Test
    fun takingTheDiscardIsAYesOnlyWhenThereIsAnActionToTake() {
        assertEquals(TableTalk.Answer.Says.NO, says(Step.TakeTheDiscard, table(discardTop = Rank.THREE)))
        assertEquals(TableTalk.Answer.Says.YES, says(Step.TakeTheDiscard, table(discardTop = Rank.JACK)))
    }

    @Test
    fun puttingDownACardIsJudgedOnTheHandItLeavesBehind() {
        // 3.14: the bot's ten goes to the pile and an unseen draw takes its place; the lowest
        // hand is still the teammate's three, so yes. Putting the three itself down leaves that
        // hand holding only a draw, which is worse for the coalition than the three was.
        assertEquals(TableTalk.Answer.Says.YES, says(Step.PutDown(CardAt(bot, 0))))
        assertEquals(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE, says(Step.PutDown(CardAt(mate, 0))))
        assertEquals(TableTalk.Answer.Says.NO, says(Step.PutDown(CardAt(bot, 7))))
    }

    @Test
    fun aStepNamingACardThatIsNotThereIsANo() {
        assertEquals(TableTalk.Answer.Says.NO, says(Step.Swap(CardAt(bot, 7), CardAt(mate, 0))))
    }

    @Test
    fun theAnswerIsAddressedToWhoeverAsked() {
        val answer = answerForLane(table(), bot, null, askedBy = third)
        assertEquals(bot, answer.by)
        assertEquals(third, answer.to)
    }

    @Test
    fun aBotOfferedAWorseStepForItsOwnLaneSaysSoAndOffersItsOwnIdeaBeside() {
        // 3.13, as a person would: "that leaves us worse — I'd rather trade my two for your
        // three." The alternative sits beside the step for a person to put on the board; the
        // step a person set is not written over.
        val worse = Step.Swap(CardAt(bot, 0), CardAt(mate, 0))
        val plan = CoalitionPlan(lanes = listOf(Lane(bot, worse)), agreed = listOf(mate), editedBy = mate)
        val asked = botsAnswering(table(), plan, PlanEdit.SetLane(bot, worse), editor = mate, bots = listOf(bot, third))

        assertEquals(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE, (asked.said as TableTalk.Answer).says)
        val offered = asked.plan.laneOf(bot)?.suggestion as? Step.Swap
        assertNotNull(offered, "the bot said no and offered nothing in its place")
        assertEquals(
            CardAt(bot, 1) to CardAt(mate, 0),
            offered.from.copy(anchor = null) to offered.to.copy(anchor = null),
        )
        assertEquals(worse, asked.plan.laneOf(bot)?.step, "a bot wrote over a person's edit")

        // A step it agrees with needs no alternative.
        val good = Step.Swap(CardAt(bot, 1), CardAt(mate, 0))
        val fine = botsAnswering(
            table(),
            plan.copy(lanes = listOf(Lane(bot, good))),
            PlanEdit.SetLane(bot, good),
            editor = mate,
            bots = listOf(bot, third),
        )
        assertNull(fine.plan.laneOf(bot)?.suggestion)
    }

    @Test
    fun onlyTheBotWhoseLaneWasSetSpeaksAndEveryBotsAgreementIsRecorded() {
        val state = table()
        val good = Step.Swap(CardAt(bot, 1), CardAt(mate, 0))
        val plan = CoalitionPlan(lanes = listOf(Lane(bot, good)), agreed = listOf(mate), editedBy = mate)

        val asked = botsAnswering(
            state,
            plan,
            PlanEdit.SetLane(bot, good),
            editor = mate,
            bots = listOf(bot, mate, third),
        )
        assertEquals(bot, (asked.said as? TableTalk.Answer)?.by, "the bot whose lane was set said nothing")
        assertTrue(bot in asked.plan.agreed, "a yes was not recorded")
        assertTrue(third in asked.plan.agreed, "a bot with no lane did not agree")

        val elsewhere = botsAnswering(
            state,
            plan,
            PlanEdit.SetLane(mate, Step.TakeTheDiscard),
            editor = mate,
            bots = listOf(bot, third),
        )
        assertNull(elsewhere.said, "a bot spoke about a lane that is not its own")
        assertTrue(bot in elsewhere.plan.agreed, "silence is still an answer")

        val worse = plan.copy(lanes = listOf(Lane(bot, Step.Swap(CardAt(bot, 0), CardAt(mate, 0)))))
        val refused = botsAnswering(
            state,
            worse,
            PlanEdit.SetLane(bot, worse.lanes.single().step!!),
            editor = mate,
            bots = listOf(bot),
        )
        assertFalse(bot in refused.plan.agreed, "a no was recorded as a yes")
    }
}
