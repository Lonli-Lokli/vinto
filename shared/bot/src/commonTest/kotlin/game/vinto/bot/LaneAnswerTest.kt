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
import game.vinto.shapes.TossIn
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
    fun aCalledCardsSwapIsPricedTogetherWithThePutDown() {
        // The bot puts its ten down, calls it, and the call's swap is part of the same step. The
        // two into the teammate's hand for the three leaves the lowest hand on two: yes. A swap
        // naming a card that is not there cannot be read, whatever the put-down was worth.
        fun called(then: Step) = Step.PutDown(CardAt(bot, 0), guess = Rank.JACK, then = then)

        assertEquals(TableTalk.Answer.Says.YES, says(called(Step.Swap(CardAt(bot, 1), CardAt(mate, 0)))))
        assertEquals(TableTalk.Answer.Says.NO, says(called(Step.Swap(CardAt(bot, 7), CardAt(mate, 0)))))
        // A King's declare after the call empties the teammate's three: yes.
        assertEquals(
            TableTalk.Answer.Says.YES,
            says(Step.PutDown(CardAt(bot, 0), guess = Rank.KING, then = Step.Declare(Rank.THREE))),
        )
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
        // The bot holds a Jack: the one way it can trade is to put the Jack down, call it, and
        // let it move two cards — which is the shape its own idea takes.
        val withJack = table().let { state ->
            state.copy(
                players = state.players.map { seat ->
                    if (seat.id == bot) seat.copy(cards = listOf(card(Rank.JACK), card(Rank.TWO))) else seat
                },
            )
        }
        val worse = Step.Swap(CardAt(bot, 0), CardAt(mate, 0))
        val plan = CoalitionPlan(lanes = listOf(Lane(bot, worse)), agreed = listOf(mate), editedBy = mate)
        val asked = botsAnswering(
            withJack,
            plan,
            PlanEdit.SetLane(bot, worse),
            editor = mate,
            bots = listOf(bot, third),
        )

        assertEquals(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE, (asked.said as TableTalk.Answer).says)
        val offered = asked.plan.laneOf(bot)?.suggestion as? Step.PutDown
        assertNotNull(offered, "the bot said no and offered nothing in its place")
        assertEquals(CardAt(bot, 0), offered.card.copy(anchor = null), "the bot did not put its Jack down")
        assertEquals(Rank.JACK, offered.guess, "the bot did not call its Jack")
        val trade = assertNotNull(offered.then as? Step.Swap, "the called Jack trades nothing")
        assertEquals(
            CardAt(bot, 1) to CardAt(mate, 0),
            trade.from.copy(anchor = null) to trade.to.copy(anchor = null),
        )
        assertEquals(worse, asked.plan.laneOf(bot)?.step, "a bot wrote over a person's edit")

        // A step it agrees with needs no alternative.
        val good = Step.Swap(CardAt(bot, 1), CardAt(mate, 0))
        val fine = botsAnswering(
            withJack,
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

    // ------------------------------------------------------------------ the whole of the rules

    private fun turn(lane: Lane?, state: GameState = table()) = answerForTurn(state, bot, lane, askedBy = mate).says

    @Test
    fun aThrowInOnTheBotsTurnIsPricedAsTheCardLeavingTheThrowersHand() {
        // The bot puts its ten down and the third member, who has said they hold a five, throws
        // it in: their hand empties, and the lowest hand is nothing at all.
        val thrown = Lane(bot, Step.PutDown(CardAt(bot, 0)), tossIns = listOf(TossIn(third, Rank.FIVE)))
        assertEquals(TableTalk.Answer.Says.YES, turn(thrown))

        // A throw the table has no grounds for — the teammate never said they hold a ten — is a
        // turn built on nothing, and a no.
        val unfounded = Lane(bot, Step.PutDown(CardAt(bot, 0)), tossIns = listOf(TossIn(mate, Rank.TEN)))
        assertEquals(TableTalk.Answer.Says.NO, turn(unfounded))

        // And what the thrown card does counts: the bot's own Jack — its hand is ground truth
        // to it — thrown in on a Jack, trading its two for the teammate's three, is a yes.
        val jacks = table().copy(
            players = table().players.map { seat ->
                if (seat.id == bot) seat.copy(cards = listOf(card(Rank.JACK), card(Rank.TWO))) else seat
            },
        )
        val traded = Lane(
            mate,
            Step.PutDown(CardAt(mate, 0), guess = Rank.THREE),
            tossIns = listOf(TossIn(bot, Rank.JACK, then = Step.Swap(CardAt(bot, 1), CardAt(third, 0)))),
        )
        assertEquals(TableTalk.Answer.Says.YES, answerForTurn(jacks, bot, traded, askedBy = mate).says)
    }

    @Test
    fun aLookIsAYesAndAForcedDrawIsPricedOnTheHandItLengthens() {
        assertEquals(TableTalk.Answer.Says.YES, says(Step.Peek(CardAt(mate, 0))))
        // The teammate's three is the lowest hand; a card on top of it leaves us worse.
        assertEquals(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE, says(Step.ForceDraw(mate)))
        // The bot's own hand is not the lowest, so lengthening it changes nothing that is scored.
        assertEquals(TableTalk.Answer.Says.YES, says(Step.ForceDraw(bot)))
    }

    @Test
    fun aKingsPointedCardLeavesAndNothingElseIsSwept() {
        // Pointing at the teammate's three empties their hand: a yes. The loose shape — a rank
        // at whoever holds one — reads the same way it always has.
        assertEquals(TableTalk.Answer.Says.YES, says(Step.Declare(Rank.THREE, CardAt(mate, 0))))
        assertEquals(TableTalk.Answer.Says.YES, says(Step.Declare(Rank.THREE)))
        // A pointed-at card that is not there is a step nobody can read.
        assertEquals(TableTalk.Answer.Says.NO, says(Step.Declare(Rank.THREE, CardAt(mate, 4))))
    }

    @Test
    fun aPutDownAloneSweepsNothingNow() {
        // The bot's ten goes down and an unseen draw takes its place; the teammate's three stays
        // where it is, because nobody has said they will throw in on a ten — and the lowest
        // hand is still the three, which is no worse.
        assertEquals(TableTalk.Answer.Says.YES, says(Step.PutDown(CardAt(bot, 0))))
    }
}
